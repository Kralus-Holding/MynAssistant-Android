from __future__ import annotations

import importlib.util
import json
import sys
import unittest
from pathlib import Path
from unittest import mock

AGENT_PATH = Path(__file__).resolve().parents[1] / "agent.py"
sys.path.insert(0, str(AGENT_PATH.parent))
SPEC = importlib.util.spec_from_file_location("mynassistant_agent", AGENT_PATH)
assert SPEC is not None and SPEC.loader is not None
agent = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(agent)


class FakeResponse:
    def __init__(self, status_code: int, payload: dict | None = None, text: str = ""):
        self.status_code = status_code
        self._payload = payload or {}
        self.text = text

    def json(self) -> dict:
        return self._payload


class FakeSession:
    uid = "test-user"

    def headers(self) -> dict:
        return {"Authorization": "Bearer test-token"}


def firestore_doc(fields: dict, update_time: str = "2026-10-22T10:00:00.000000Z") -> dict:
    encoded = {}
    for key, value in fields.items():
        if isinstance(value, bool):
            encoded[key] = {"booleanValue": value}
        elif isinstance(value, int):
            encoded[key] = {"integerValue": str(value)}
        else:
            encoded[key] = {"stringValue": str(value)}
    return {
        "name": "projects/myncloud-24/databases/(default)/documents/commands/cmd-1",
        "updateTime": update_time,
        "fields": encoded,
    }


class FakeScreenshotImage:
    def __init__(self, sizes_by_quality: dict[int, int]):
        self.sizes_by_quality = sizes_by_quality
        self.qualities_used: list[int] = []

    def thumbnail(self, size: tuple[int, int]) -> None:
        self.thumbnail_size = size

    def save(self, output, format: str, quality: int, optimize: bool) -> None:
        self.qualities_used.append(quality)
        output.write(b"x" * self.sizes_by_quality[quality])


class AtomicCommandClaimTests(unittest.TestCase):
    def setUp(self) -> None:
        self.session = FakeSession()
        self.document = {
            "name": "projects/myncloud-24/databases/(default)/documents/commands/cmd-1",
            "updateTime": "2026-10-22T10:00:00.000000Z",
            "fields": {
                "ownerId": {"stringValue": "test-user"},
                "deviceId": {"stringValue": "win-test"},
                "command": {"stringValue": "volume_up"},
                "status": {"stringValue": "pending"},
            },
        }

    def test_screen_frame_compresses_before_base64_firestore_expansion(self) -> None:
        image = FakeScreenshotImage({45: 600_001, 30: 500_000})
        with mock.patch.object(agent.pyautogui, "screenshot", return_value=image):
            encoded = agent.capture_screen_base64()

        import base64
        self.assertEqual(image.qualities_used, [45, 30])
        self.assertEqual(len(base64.b64decode(encoded)), 500_000)
        self.assertLessEqual(len(encoded), 700_000)

    def test_screen_frame_is_rejected_if_low_quality_still_too_large(self) -> None:
        image = FakeScreenshotImage({45: 800_001, 30: 700_001})
        with mock.patch.object(agent.pyautogui, "screenshot", return_value=image):
            with self.assertRaisesRegex(RuntimeError, "Firestore belge boyutu"):
                agent.capture_screen_base64()
        self.assertEqual(image.qualities_used, [45, 30])

    def test_patch_includes_firestore_update_time_precondition(self) -> None:
        with mock.patch.object(agent.requests, "patch", return_value=FakeResponse(200, {})) as patch:
            agent.firestore_patch(
                self.session,
                "commands/cmd-1",
                {"status": agent.encode_string("running")},
                update_mask=["status"],
                current_document_update_time=self.document["updateTime"],
            )

        params = patch.call_args.kwargs["params"]
        self.assertIn(("currentDocument.updateTime", self.document["updateTime"]), params)
        self.assertIn(("updateMask.fieldPaths", "status"), params)

    def test_claim_refuses_document_without_update_time(self) -> None:
        document = dict(self.document)
        document.pop("updateTime")
        with mock.patch.object(agent.requests, "patch") as patch, mock.patch.object(agent.requests, "get") as get:
            claimed = agent.claim_pending_command(self.session, document, "win-test")
        self.assertFalse(claimed)
        patch.assert_not_called()
        get.assert_not_called()

    def test_claim_wins_using_compare_and_swap(self) -> None:
        with mock.patch.object(agent.requests, "patch", return_value=FakeResponse(200, {})) as patch:
            claimed = agent.claim_pending_command(self.session, self.document, "win-test")

        self.assertTrue(claimed)
        params = patch.call_args.kwargs["params"]
        self.assertIn(("currentDocument.updateTime", self.document["updateTime"]), params)
        fields = patch.call_args.kwargs["json"]["fields"]
        self.assertEqual(fields["status"]["stringValue"], "running")
        self.assertEqual(fields["claimedByDeviceId"]["stringValue"], "win-test")
        self.assertTrue(fields["claimToken"]["stringValue"])

    def test_cancelled_document_wins_race_and_agent_does_not_claim(self) -> None:
        with mock.patch.object(
            agent.requests, "patch", return_value=FakeResponse(
                400, {"error": {"status": "FAILED_PRECONDITION"}}, '{"error":{"status":"FAILED_PRECONDITION"}}'
            )
        ) as patch, mock.patch.object(
            agent.requests, "get", return_value=FakeResponse(
                200, firestore_doc({"status": "cancelled", "result": "Cancelled"})
            )
        ) as get:
            claimed = agent.claim_pending_command(self.session, self.document, "win-test")

        self.assertFalse(claimed)
        patch.assert_called_once()
        get.assert_called_once()
        self.assertIn(("currentDocument.updateTime", self.document["updateTime"]), patch.call_args.kwargs["params"])

    def test_timeout_after_success_is_verified_by_unique_claim_token(self) -> None:
        captured = {}

        def patch_then_timeout(*args, **kwargs):
            captured["body"] = kwargs["json"]["fields"]
            raise agent.requests.Timeout("response was lost after server accepted write")

        def get_claim(*args, **kwargs):
            fields = captured["body"]
            return FakeResponse(
                200,
                firestore_doc({
                    "status": fields["status"]["stringValue"],
                    "claimToken": fields["claimToken"]["stringValue"],
                    "claimedByDeviceId": fields["claimedByDeviceId"]["stringValue"],
                }),
            )

        with mock.patch.object(agent.requests, "patch", side_effect=patch_then_timeout), mock.patch.object(
            agent.requests, "get", side_effect=get_claim
        ):
            claimed = agent.claim_pending_command(self.session, self.document, "win-test")

        self.assertTrue(claimed)


if __name__ == "__main__":
    unittest.main(verbosity=2)
