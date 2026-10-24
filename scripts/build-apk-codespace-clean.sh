#!/usr/bin/env bash
set -Eeuo pipefail

# Build the current GitHub main revision in a separate worktree. The user's
# existing Codespaces files, untracked files, logs, and Copilot edits are untouched.
ROOT="${MYN_REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
ROOT="$(cd "$ROOT" && pwd)"
if ! git -C "$ROOT" rev-parse --git-dir >/dev/null 2>&1; then
  echo "ERROR: Run this from the MynAssistant-Android Codespace, or set MYN_REPO_ROOT."
  exit 2
fi

STAMP="$(date -u +%Y%m%d-%H%M%S)"
TMP_PARENT="$(mktemp -d -t mynassistant-clean-0.6.0.XXXXXX)"
BUILD_DIR="$TMP_PARENT/source"
BUILD_STATUS=0

cleanup() {
  local exit_code=$?
  if [[ -n "${BUILD_DIR:-}" ]] && git -C "$ROOT" worktree list --porcelain 2>/dev/null | grep -Fq "worktree $BUILD_DIR"; then
    git -C "$ROOT" worktree remove --force "$BUILD_DIR" >/dev/null 2>&1 || true
  fi
  if [[ -n "${TMP_PARENT:-}" && -d "$TMP_PARENT" ]]; then
    rm -rf "$TMP_PARENT"
  fi
  exit "$exit_code"
}
trap cleanup EXIT

echo "== 1/5: Fetch newest GitHub main without changing your open workspace =="
git -C "$ROOT" fetch --no-tags origin main
SOURCE_COMMIT="$(git -C "$ROOT" rev-parse origin/main)"
echo "Source commit: $SOURCE_COMMIT"

echo
echo "== 2/5: Create isolated clean build worktree =="
git -C "$ROOT" worktree add --detach "$BUILD_DIR" "$SOURCE_COMMIT"
cd "$BUILD_DIR"

echo
echo "== 3/5: Confirm the correct source revision and account fix =="
VERSION_CODE="$(grep -m1 'versionCode =' app/build.gradle.kts | tr -cd '0-9')"
VERSION_NAME="$(grep -m1 'versionName =' app/build.gradle.kts | cut -d '"' -f 2)"
if [[ "$VERSION_CODE" != "37" || "$VERSION_NAME" != "0.6.0" ]]; then
  echo "ERROR: Expected MynAssistant 0.6.0 / versionCode 37; found $VERSION_NAME / $VERSION_CODE."
  exit 3
fi

SOURCE="app/src/main/java/com/mynkralus/mynassistant/MainActivity.kt"
ACCOUNT_BACKEND="app/src/main/java/com/mynkralus/mynassistant/MynAssistantAccountBackend.kt"
if ! grep -Fq 'MynAssistantAccountBackend.sendEmailVerification' "$SOURCE" ||
   ! grep -Fq 'MynAssistantAccountBackend.changeEmailAddress' "$SOURCE" ||
   ! grep -Fq 'MynAssistantAccountBackend.uploadProfilePhoto' "$SOURCE" ||
   ! grep -Fq 'MynAssistant • Android 0.6.0' "$SOURCE" ||
   ! grep -Fq 'changeDisplayName: (String, (String) -> Unit) -> Unit' "$SOURCE" ||
   ! grep -Fq 'pendingPhoneNumber' "$SOURCE" ||
   ! grep -Fq 'pendingEmailAddress' "$SOURCE" ||
   ! grep -Fq 'Ücretsiz başlangıç modunda gerçek SMS gönderimi kapalı' "$SOURCE" ||
   grep -Fq 'PhoneAuthProvider.verifyPhoneNumber' "$SOURCE" ||
   [[ ! -s "$ACCOUNT_BACKEND" ]] ||
   [[ ! -s "app/src/main/java/com/mynkralus/mynassistant/ProfilePhotoCropDialog.kt" ]] ||
   grep -q 'com.google.firebase:firebase-storage' app/build.gradle.kts; then
  echo "ERROR: The fetched source is missing required 0.6.0 account changes."
  exit 4
fi
CALLBACK_PASSES="$(grep -F -c 'changeEmailAddress = changeEmailAddress' "$SOURCE")"
if [[ "$CALLBACK_PASSES" -ne 3 ]]; then
  echo "ERROR: Expected callback forwarding into MainActivity/AssistantScreen/AccountSettings; found $CALLBACK_PASSES pass-throughs."
  echo "The current local build source is not accepted until this check passes."
  exit 4
fi
bash -n scripts/build-apk-codespace.sh
echo "Verified: version $VERSION_NAME (versionCode $VERSION_CODE), free account services, pending contacts and cropper changes present."

echo
echo "== 4/5: Run tests and build from the fetched main revision =="
set +e
bash scripts/build-apk-codespace.sh
BUILD_STATUS=$?
set -e

if [[ "$BUILD_STATUS" -ne 0 ]]; then
  echo
  echo "BUILD FAILED. Your original workspace was not modified."
  if [[ -f "$BUILD_DIR/mynassistant-build-0.6.0.log" ]]; then
    LOG_DEST="$ROOT/mynassistant-build-0.6.0-clean-$STAMP.log"
    cp "$BUILD_DIR/mynassistant-build-0.6.0.log" "$LOG_DEST"
    echo "Diagnostic log copied to: $LOG_DEST"
    echo "===== Root-cause lines ====="
    grep -ni -B 3 -A 8 -E 'What went wrong|Execution failed for task|e: file://|Unresolved reference|Type mismatch|Caused by:|unable to create native thread|OutOfMemoryError|Could not resolve|Could not find|No space left on device|BackendException|Internal compiler error' "$LOG_DEST" | tail -120 || true
  fi
  exit "$BUILD_STATUS"
fi

echo
echo "== 5/5: Copy the verified APK into the open Codespaces workspace =="
APK_SOURCE="$BUILD_DIR/MynAssistant-Android-$VERSION_NAME.apk"
if [[ ! -s "$APK_SOURCE" ]]; then
  echo "ERROR: Build reported success but the expected APK file is missing."
  exit 5
fi
APK_DEST="$ROOT/MynAssistant-Android-$VERSION_NAME-$STAMP.apk"
cp "$APK_SOURCE" "$APK_DEST"
echo "APK: $APK_DEST"
echo "Size: $(du -h "$APK_DEST" | cut -f1)"
echo "SHA-256: $(sha256sum "$APK_DEST" | awk '{print $1}')"
echo
echo "SUCCESS: unit tests, APK build, signing and existing regression checks passed."
echo "In Explorer, download the timestamped APK file. Your previous files were left in place."
