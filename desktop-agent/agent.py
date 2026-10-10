from __future__ import annotations

import ctypes
import json
import os
import platform
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode

import pyautogui
import requests
import win32crypt
from google_auth_oauthlib.flow import InstalledAppFlow


PROJECT_ID = "myncloud-24"
FIREBASE_API_KEY = "AIzaSyB0z1b5OOMh8Pqn2GPd1ZuWoHEhRoKSrRM"
IDENTITY_TOOLKIT = "https://identitytoolkit.googleapis.com/v1"
SECURE_TOKEN = "https://securetoken.googleapis.com/v1/token"
FIRESTORE = f"https://firestore.googleapis.com/v1/projects/{PROJECT_ID}/databases/(default)/documents"
SCOPES = ["openid", "email", "profile"]
AGENT_VERSION = "0.1.0"
POLL_SECONDS = 2.0
HEARTBEAT_SECONDS = 25.0
DPAPI_ENTROPY = b"MynAssistantDesktopAgent-v1"
APP_DATA = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "MynAssistant" / "DesktopAgent"
OAUTH_FILE_NAME = "client_secret.json"
SESSION_FILE = APP_DATA / "session.bin"
DEVICE_FILE = APP_DATA / "devices.json"

VK_VOLUME_MUTE = 0xAD
VK_VOLUME_DOWN = 0xAE
VK_VOLUME_UP = 0xAF
KEYEVENTF_KEYUP = 0x0002


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def running_directory() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


def encode_string(value: str) -> dict:
    return {"stringValue": value}


def encode_timestamp(value: str | None = None) -> dict:
    return {"timestampValue": value or utc_now()}


def decode_field(value: dict | None):
    if not value:
        return None
    if "stringValue" in value:
        return value["stringValue"]
    if "integerValue" in value:
        return int(value["integerValue"])
    if "doubleValue" in value:
        return float(value["doubleValue"])
    if "booleanValue" in value:
        return bool(value["booleanValue"])
    if "timestampValue" in value:
        return value["timestampValue"]
    if "nullValue" in value:
        return None
    return None


def decode_document(document: dict) -> dict:
    return {key: decode_field(value) for key, value in document.get("fields", {}).items()}


def protect_session(payload: dict) -> None:
    APP_DATA.mkdir(parents=True, exist_ok=True)
    raw = json.dumps(payload).encode("utf-8")
    _description, encrypted = win32crypt.CryptProtectData(
        raw, "MynAssistant Desktop Agent", DPAPI_ENTROPY, None, None, 0
    )
    SESSION_FILE.write_bytes(encrypted)


def load_protected_session() -> dict | None:
    if not SESSION_FILE.exists():
        return None
    try:
        encrypted = SESSION_FILE.read_bytes()
        _description, raw = win32crypt.CryptUnprotectData(encrypted, DPAPI_ENTROPY, None, None, 0)
        payload = json.loads(raw.decode("utf-8"))
        if payload.get("refreshToken") and payload.get("uid"):
            return payload
    except Exception as exc:
        print(f"[Auth] Kaydedilmiş oturum açılamadı: {exc}")
    return None


def delete_protected_session() -> None:
    try:
        SESSION_FILE.unlink(missing_ok=True)
    except OSError:
        pass


def load_device_id(uid: str) -> str:
    APP_DATA.mkdir(parents=True, exist_ok=True)
    try:
        data = json.loads(DEVICE_FILE.read_text("utf-8"))
    except Exception:
        data = {}
    device_id = data.get(uid)
    if not isinstance(device_id, str) or not device_id:
        device_id = "win-" + uuid.uuid4().hex
        data[uid] = device_id
        DEVICE_FILE.write_text(json.dumps(data, indent=2), encoding="utf-8")
    return device_id


def google_oauth_credentials():
    credentials_path = running_directory() / OAUTH_FILE_NAME
    if not credentials_path.is_file():
        raise FileNotFoundError(
            "Google OAuth dosyası bulunamadı.\n"
            f"Google Cloud Console'dan Desktop app türündeki OAuth istemcisinin JSON dosyasını indirip\n"
            f"adı '{OAUTH_FILE_NAME}' olacak şekilde şu klasöre koy:\n{running_directory()}"
        )
    flow = InstalledAppFlow.from_client_secrets_file(str(credentials_path), SCOPES)
    return flow.run_local_server(
        host="127.0.0.1",
        port=0,
        open_browser=True,
        authorization_prompt_message="Google hesabıyla giriş için tarayıcı açılıyor...",
        success_message="Google girişi tamamlandı. Bu sekmeyi kapatıp MynAssistant Agent penceresine dön.",
        access_type="offline",
        prompt="select_account",
    )


def firebase_exchange_google_access_token(access_token: str) -> dict:
    post_body = urlencode({"access_token": access_token, "providerId": "google.com"})
    response = requests.post(
        f"{IDENTITY_TOOLKIT}/accounts:signInWithIdp",
        params={"key": FIREBASE_API_KEY},
        json={
            "postBody": post_body,
            "requestUri": "http://localhost",
            "returnSecureToken": True,
            "returnIdpCredential": False,
        },
        timeout=25,
    )
    if response.status_code >= 400:
        raise RuntimeError("Firebase Google girişi başarısız: " + response.text[:700])
    data = response.json()
    payload = {
        "uid": data["localId"],
        "email": data.get("email", ""),
        "displayName": data.get("displayName", ""),
        "refreshToken": data["refreshToken"],
    }
    protect_session(payload)
    return {
        **payload,
        "idToken": data["idToken"],
        "expiresAt": time.time() + int(data.get("expiresIn", "3600")) - 90,
    }


class FirebaseSession:
    def __init__(self, state: dict):
        self.uid = state["uid"]
        self.email = state.get("email", "")
        self.display_name = state.get("displayName", "")
        self.refresh_token = state["refreshToken"]
        self.id_token = state.get("idToken", "")
        self.expires_at = float(state.get("expiresAt", 0))
        self.ensure_fresh(force=not self.id_token)

    @classmethod
    def sign_in(cls):
        credentials = google_oauth_credentials()
        if not credentials.token:
            raise RuntimeError("Google OAuth sonucunda erişim anahtarı alınamadı.")
        return cls(firebase_exchange_google_access_token(credentials.token))

    def _store(self) -> None:
        protect_session({
            "uid": self.uid,
            "email": self.email,
            "displayName": self.display_name,
            "refreshToken": self.refresh_token,
        })

    def ensure_fresh(self, force: bool = False) -> None:
        if self.id_token and not force and time.time() < self.expires_at:
            return
        response = requests.post(
            SECURE_TOKEN,
            params={"key": FIREBASE_API_KEY},
            data={"grant_type": "refresh_token", "refresh_token": self.refresh_token},
            timeout=20,
        )
        if response.status_code >= 400:
            raise RuntimeError("Firebase oturumu yenilenemedi: " + response.text[:500])
        data = response.json()
        self.id_token = data["id_token"]
        self.refresh_token = data.get("refresh_token", self.refresh_token)
        self.expires_at = time.time() + int(data.get("expires_in", "3600")) - 90
        self._store()

    def headers(self) -> dict:
        self.ensure_fresh()
        return {"Authorization": f"Bearer {self.id_token}", "Content-Type": "application/json"}


def firestore_patch(session: FirebaseSession, document_path: str, fields: dict, update_mask: list[str] | None = None):
    params = []
    if update_mask:
        params.extend(("updateMask.fieldPaths", field) for field in update_mask)
    response = requests.patch(
        f"{FIRESTORE}/{document_path}",
        params=params,
        headers=session.headers(),
        json={"fields": fields},
        timeout=20,
    )
    if response.status_code >= 400:
        raise RuntimeError(
            f"Firestore PATCH {document_path} HTTP {response.status_code}: {response.text[:500]}"
        )
    return response.json()


def register_or_heartbeat(session: FirebaseSession, device_id: str) -> None:
    computer_name = platform.node().strip() or "Windows bilgisayarım"
    fields = {
        "ownerId": encode_string(session.uid),
        "name": encode_string(computer_name),
        "platform": encode_string("Windows"),
        "type": encode_string("Windows"),
        "status": encode_string("online"),
        "connectionMode": encode_string("firebase-agent"),
        "agentVersion": encode_string(AGENT_VERSION),
        "lastSeenAt": encode_timestamp(),
    }
    response = requests.patch(
        f"{FIRESTORE}/devices/{device_id}",
        headers=session.headers(),
        json={"fields": fields},
        timeout=20,
    )
    if response.status_code >= 400:
        raise RuntimeError("Windows cihaz kaydı başarısız: " + response.text[:700])


def fetch_owner_commands(session: FirebaseSession) -> list[dict]:
    response = requests.post(
        f"{FIRESTORE}:runQuery",
        headers=session.headers(),
        json={
            "structuredQuery": {
                "from": [{"collectionId": "commands"}],
                "where": {
                    "fieldFilter": {
                        "field": {"fieldPath": "ownerId"},
                        "op": "EQUAL",
                        "value": {"stringValue": session.uid},
                    }
                },
                "limit": 100,
            }
        },
        timeout=25,
    )
    if response.status_code >= 400:
        raise RuntimeError("Komut kuyruğu okunamadı: " + response.text[:700])
    documents = []
    for result in response.json():
        document = result.get("document")
        if document:
            documents.append(document)
    return documents


def update_command(session: FirebaseSession, document: dict, status: str, result: str) -> None:
    full_name = document.get("name", "")
    marker = "/documents/"
    if marker not in full_name:
        raise RuntimeError("Komut belgesinin yolu geçersiz.")
    document_path = full_name.split(marker, 1)[1]
    fields = {
        "status": encode_string(status),
        "result": encode_string(result[:800]),
        "processedAt": encode_timestamp(),
    }
    firestore_patch(session, document_path, fields, update_mask=["status", "result", "processedAt"])


def press_virtual_key(vk: int, repeat: int = 1) -> None:
    user32 = ctypes.windll.user32
    for _ in range(max(1, min(repeat, 15))):
        user32.keybd_event(vk, 0, 0, 0)
        user32.keybd_event(vk, 0, KEYEVENTF_KEYUP, 0)


def execute_command(command: str, fields: dict) -> str:
    command = command.strip().lower()
    if command == "mute":
        press_virtual_key(VK_VOLUME_MUTE)
        return "Bilgisayarın sesi aç/kapat tuşuyla değiştirildi."
    if command == "volume_up":
        press_virtual_key(VK_VOLUME_UP, 3)
        return "Bilgisayarın sesi artırıldı."
    if command == "volume_down":
        press_virtual_key(VK_VOLUME_DOWN, 3)
        return "Bilgisayarın sesi azaltıldı."
    if command == "home":
        pyautogui.hotkey("win", "d")
        return "Windows masaüstü gösterildi."
    if command == "mouse_move":
        dx = int(fields.get("dx", 0) or 0)
        dy = int(fields.get("dy", 0) or 0)
        pyautogui.moveRel(dx, dy, duration=0)
        return "Fare hareketi uygulandı."
    if command == "mouse_click":
        pyautogui.click()
        return "Sol tıklama uygulandı."
    if command == "mouse_right_click":
        pyautogui.click(button="right")
        return "Sağ tıklama uygulandı."
    if command == "power":
        return "Güç kapatma bu sürümde etkin değil; yanlışlıkla kapanmayı önlemek için işlem uygulanmadı."
    return f"Desteklenmeyen komut: {command}"


def command_is_supported(command: str) -> bool:
    return command in {
        "mute", "volume_up", "volume_down", "home",
        "mouse_move", "mouse_click", "mouse_right_click", "power"
    }


def ask_install_startup() -> None:
    try:
        answer = input("MynAssistant Agent Windows açılışında otomatik başlasın mı? [E/h]: ").strip().lower()
    except (EOFError, KeyboardInterrupt):
        return
    if answer in {"", "e", "evet", "y", "yes"}:
        if not getattr(sys, "frozen", False):
            print("Otomatik başlangıç yalnızca paketlenmiş EXE sürümünde desteklenir.")
            return
        try:
            import winreg
            key = winreg.OpenKey(
                winreg.HKEY_CURRENT_USER,
                r"Software\Microsoft\Windows\CurrentVersion\Run",
                0,
                winreg.KEY_SET_VALUE,
            )
            winreg.SetValueEx(key, "MynAssistantAgent", 0, winreg.REG_SZ, f'"{Path(sys.executable).resolve()}"')
            winreg.CloseKey(key)
            print("Otomatik başlangıç etkinleştirildi.")
        except Exception as exc:
            print(f"Otomatik başlangıç ayarlanamadı: {exc}")


def run_agent() -> int:
    APP_DATA.mkdir(parents=True, exist_ok=True)
    session_state = load_protected_session()
    session = None
    if session_state:
        try:
            session = FirebaseSession(session_state)
            print(f"Kaydedilmiş Firebase oturumu kullanılıyor: {session.email or session.uid}")
        except Exception as exc:
            print(f"[Auth] Kayıtlı oturum yenilenemedi; Google girişi yeniden açılacak. Ayrıntı: {exc}")
            delete_protected_session()

    if session is None:
        session = FirebaseSession.sign_in()
        print(f"Firebase hesabı: {session.email or session.uid}")
        ask_install_startup()

    device_id = load_device_id(session.uid)
    register_or_heartbeat(session, device_id)
    print(f"Cihaz çevrimiçi: {platform.node()} (ID: {device_id})")
    print("Android uygulamasında Cihazlarım bölümünde otomatik görünmesi gerekir.")
    print("Durdurmak için Ctrl+C. Bu ilk sürümde canlı ekran akışı yok.")

    last_heartbeat = time.monotonic()
    pyautogui.FAILSAFE = False
    try:
        while True:
            session.ensure_fresh()
            now = time.monotonic()
            if now - last_heartbeat >= HEARTBEAT_SECONDS:
                register_or_heartbeat(session, device_id)
                last_heartbeat = now

            for document in fetch_owner_commands(session):
                fields = decode_document(document)
                if fields.get("deviceId") != device_id or fields.get("status") != "pending":
                    continue
                command = str(fields.get("command") or "")
                if not command_is_supported(command):
                    update_command(session, document, "failed", "Desteklenmeyen komut.")
                    continue
                update_command(session, document, "running", "Windows Agent komutu çalıştırıyor.")
                try:
                    result = execute_command(command, fields)
                    status = "completed" if not result.startswith("Desteklenmeyen") else "failed"
                    update_command(session, document, status, result)
                    print(f"[Komut] {command}: {result}")
                except Exception as exc:
                    try:
                        update_command(session, document, "failed", str(exc))
                    except Exception as update_exc:
                        print(f"[Hata] Komut durumu güncellenemedi: {update_exc}")
                    print(f"[Hata] {command}: {exc}")
            time.sleep(POLL_SECONDS)
    except KeyboardInterrupt:
        print("\nAgent kapatılıyor...")
    finally:
        try:
            firestore_patch(
                session,
                f"devices/{device_id}",
                {"status": encode_string("offline"), "lastSeenAt": encode_timestamp()},
                update_mask=["status", "lastSeenAt"],
            )
        except Exception as exc:
            print(f"[Uyarı] Çevrimdışı durumu kaydedilemedi: {exc}")
    return 0


def main() -> int:
    if os.name != "nt":
        print("MynAssistant Desktop Agent yalnızca Windows üzerinde çalışır.")
        return 2
    try:
        return run_agent()
    except FileNotFoundError as exc:
        print(f"\n[Kurulum] {exc}")
        input("\nÇıkmak için Enter'a bas...")
        return 2
    except Exception as exc:
        print(f"\n[Hata] {exc}")
        input("\nÇıkmak için Enter'a bas...")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
