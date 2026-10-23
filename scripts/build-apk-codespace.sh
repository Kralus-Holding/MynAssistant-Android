#!/usr/bin/env bash
set -Eeuo pipefail
export LANG=C.UTF-8
ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT"

if [[ ! -f app/build.gradle.kts || ! -s .github/ci-debug-keystore.b64 ]]; then
  echo "ERROR: Run this from the MynAssistant-Android repository root."
  exit 2
fi
VERSION_CODE="$(grep -m1 'versionCode =' app/build.gradle.kts | tr -cd '0-9')"
VERSION_NAME="$(grep -m1 'versionName =' app/build.gradle.kts | cut -d '"' -f 2)"
if [[ "$VERSION_CODE" != "35" || "$VERSION_NAME" != "0.5.8" ]]; then
  echo "ERROR: Expected MynAssistant 0.5.8 / versionCode 35; found $VERSION_NAME / $VERSION_CODE."
  exit 2
fi

echo "== MynAssistant Android $VERSION_NAME (versionCode $VERSION_CODE) =="
echo "== 1/6: Prepare system tools and Java 17 =="
if ! command -v curl >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y curl unzip
fi
JAVA_MAJOR="$(java -version 2>&1 | sed -nE '1s/.*version "([0-9]+).*/\1/p' || true)"
if [[ "$JAVA_MAJOR" != "17" ]]; then
  echo "Java 17 not detected (detected: ${JAVA_MAJOR:-unknown}); installing OpenJDK 17."
  if ! command -v sudo >/dev/null 2>&1; then
    echo "ERROR: Java 17 is required and sudo is unavailable to install it."
    exit 2
  fi
  sudo apt-get update
  sudo apt-get install -y openjdk-17-jdk
fi
if [[ -x /usr/lib/jvm/java-17-openjdk-amd64/bin/java ]]; then
  export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
else
  JAVAC_PATH="$(readlink -f "$(command -v javac)")"
  export JAVA_HOME="$(dirname "$(dirname "$JAVAC_PATH")")"
fi
export PATH="$JAVA_HOME/bin:$PATH"
java -version

TMP_DIR="$(mktemp -d -t mynassistant-build-0.5.8.XXXXXX)"
cleanup_tmp() {
  local exit_code=$?
  if [[ "$exit_code" -eq 0 ]]; then
    rm -rf "$TMP_DIR"
  else
    echo "Build failed; keeping temporary diagnostics at: $TMP_DIR"
    if [[ -f "$TMP_DIR/licenses.log" ]]; then
      echo "--- Android SDK license log (last 40 lines) ---"
      tail -40 "$TMP_DIR/licenses.log" || true
    fi
    if [[ -n "${BUILD_LOG:-}" && -f "$BUILD_LOG" ]]; then
      echo "--- Gradle build log (last 100 lines) ---"
      tail -100 "$BUILD_LOG" || true
    fi
  fi
}
trap cleanup_tmp EXIT

echo "== 2/6: Locate or install Android SDK 36 and Build Tools 36.0.0 =="
SDK_CANDIDATES=(
  "${ANDROID_HOME:-}"
  "${ANDROID_SDK_ROOT:-}"
  "$HOME/android-sdk"
  "$HOME/Android/Sdk"
  "/usr/local/lib/android/sdk"
  "/opt/android-sdk"
  "/usr/lib/android-sdk"
)
SELECTED_SDK=""
for candidate in "${SDK_CANDIDATES[@]}"; do
  [[ -n "$candidate" && -d "$candidate" ]] || continue
  if [[ -x "$candidate/cmdline-tools/latest/bin/sdkmanager" ]] ||
     compgen -G "$candidate/cmdline-tools/*/bin/sdkmanager" >/dev/null ||
     [[ -f "$candidate/platforms/android-36/android.jar" ]]; then
    SELECTED_SDK="$candidate"
    break
  fi
done
export ANDROID_HOME="${SELECTED_SDK:-${ANDROID_HOME:-$HOME/android-sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
mkdir -p "$ANDROID_HOME"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

# Reuse a versioned Command-line Tools install instead of downloading a second copy.
if [[ ! -x "$SDKMANAGER" && -d "$ANDROID_HOME/cmdline-tools" ]]; then
  for candidate in "$ANDROID_HOME"/cmdline-tools/*/bin/sdkmanager; do
    [[ -x "$candidate" ]] || continue
    SDKMANAGER="$candidate"
    break
  done
fi

if [[ ! -x "$SDKMANAGER" ]]; then
  SDK_ZIP="$TMP_DIR/commandlinetools-linux.zip"
  SDK_URL="https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip"
  SDK_SHA256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"
  curl -fL --retry 3 --connect-timeout 20 "$SDK_URL" -o "$SDK_ZIP"
  echo "$SDK_SHA256  $SDK_ZIP" | sha256sum -c -
  mkdir -p "$TMP_DIR/sdk-unpacked" "$ANDROID_HOME/cmdline-tools"
  unzip -q "$SDK_ZIP" -d "$TMP_DIR/sdk-unpacked"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$TMP_DIR/sdk-unpacked/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
set +e
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses > "$TMP_DIR/licenses.log" 2>&1
LICENSE_PIPE_STATUS=${PIPESTATUS[1]:-1}
set -e
if [[ "$LICENSE_PIPE_STATUS" -ne 0 ]] && ! grep -q "All SDK package licenses accepted" "$TMP_DIR/licenses.log"; then
  cat "$TMP_DIR/licenses.log"
  echo "ERROR: Android SDK licenses were not accepted."
  exit 3
fi
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-36" "build-tools;36.0.0"

echo "== 3/6: Prepare Gradle 8.13 =="
INSTALLED_GRADLE_VERSION=""
if command -v gradle >/dev/null 2>&1; then
  INSTALLED_GRADLE_VERSION="$(gradle --version 2>&1 || true)"
fi
if [[ "$INSTALLED_GRADLE_VERSION" == *"Gradle 8.13"* ]]; then
  GRADLE_BIN="$(command -v gradle)"
else
  GRADLE_ZIP="$TMP_DIR/gradle-8.13-bin.zip"
  GRADLE_URL="https://services.gradle.org/distributions/gradle-8.13-bin.zip"
  curl -fL --retry 3 --connect-timeout 20 "$GRADLE_URL" -o "$GRADLE_ZIP"
  EXPECTED_SHA="$(curl -fsSL --retry 3 --connect-timeout 20 "$GRADLE_URL.sha256" | awk '{print $1}')"
  ACTUAL_SHA="$(sha256sum "$GRADLE_ZIP" | awk '{print $1}')"
  if [[ -z "$EXPECTED_SHA" || "$EXPECTED_SHA" != "$ACTUAL_SHA" ]]; then
    echo "ERROR: Gradle distribution SHA-256 verification failed."
    exit 3
  fi
  unzip -q "$GRADLE_ZIP" -d "$TMP_DIR"
  GRADLE_BIN="$TMP_DIR/gradle-8.13/bin/gradle"
fi
"$GRADLE_BIN" --version

echo "== 4/6: Restore stable debug signing key =="
mkdir -p "$HOME/.android"
base64 --decode .github/ci-debug-keystore.b64 > "$HOME/.android/debug.keystore"
chmod 600 "$HOME/.android/debug.keystore"
keytool -list -v -keystore "$HOME/.android/debug.keystore" -storepass android -alias androiddebugkey | grep "SHA1:"

echo "== 5/6: Run unit tests and build APK =="
BUILD_LOG="$ROOT/mynassistant-build-0.5.8.log"
set +e
GRADLE_HEAP="${MYN_GRADLE_HEAP:-2g}"
GRADLE_METASPACE="${MYN_GRADLE_METASPACE:-512m}"
"$GRADLE_BIN" --no-daemon --no-parallel --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx$GRADLE_HEAP -XX:MaxMetaspaceSize=$GRADLE_METASPACE -Dfile.encoding=UTF-8" \
  testDebugUnitTest assembleDebug --stacktrace 2>&1 | tee "$BUILD_LOG"
BUILD_STATUS=${PIPESTATUS[0]}
set -e
if [[ "$BUILD_STATUS" -ne 0 ]]; then
  echo "ERROR: Gradle build/tests failed (exit $BUILD_STATUS). Log: $BUILD_LOG"
  tail -100 "$BUILD_LOG"
  exit "$BUILD_STATUS"
fi

echo "== 6/6: Verify signing, APK size, and regression checks =="
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$APK" ]]; then echo "ERROR: APK missing after Gradle success."; exit 4; fi
KEYSTORE_SHA1="$(keytool -list -v -keystore "$HOME/.android/debug.keystore" -storepass android -alias androiddebugkey | awk '/SHA1:/ { gsub(":", "", $2); print tolower($2) }')"
APK_SHA1="$("$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs "$APK" | awk '/certificate SHA-1 digest/ { print tolower($NF) }' | tr -d ':')"
echo "Keystore SHA-1: $KEYSTORE_SHA1"
echo "APK SHA-1:      $APK_SHA1"
if [[ -z "$KEYSTORE_SHA1" || -z "$APK_SHA1" || "$KEYSTORE_SHA1" != "$APK_SHA1" ]]; then
  echo "ERROR: APK signing certificate does not match the stable key."; exit 5
fi
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose "$APK"
test "$(grep -c 'vosk-android' app/build.gradle.kts)" -eq 1
grep -q 'sherpa-onnx' app/build.gradle.kts
grep -q 'com.google.firebase:firebase-storage' app/build.gradle.kts
grep -q 'verifyBeforeUpdateEmail' app/src/main/java/com/mynkralus/mynassistant/MainActivity.kt
grep -q 'E-posta adresini değiştir' app/src/main/java/com/mynkralus/mynassistant/MainActivity.kt
grep -q 'Modifier.size(76.dp).clip(RoundedCornerShape(12.dp))' app/src/main/java/com/mynkralus/mynassistant/MainActivity.kt
grep -q 'MynAssistant • Android 0.5.8' app/src/main/java/com/mynkralus/mynassistant/MainActivity.kt
grep -q 'password.length < 8' app/src/main/java/com/mynkralus/mynassistant/MainActivity.kt
grep -q 'Text(if (registerMode) "Google ile kayıt ol" else "Google ile giriş yap")' app/src/main/java/com/mynkralus/mynassistant/MainActivity.kt
if grep -q 'Text("Şifre (en az 6 karakter)")' app/src/main/java/com/mynkralus/mynassistant/MainActivity.kt; then echo "ERROR: old password label found."; exit 5; fi
if grep -q 'padding(start = 60.dp, bottom = 4.dp)' app/src/main/java/com/mynkralus/mynassistant/MainActivity.kt; then echo "ERROR: duplicate auth warning in drawer."; exit 5; fi
APK_BYTES="$(stat -c%s "$APK")"
if [[ "$APK_BYTES" -lt 100000000 ]]; then echo "ERROR: APK unexpectedly small ($APK_BYTES bytes)."; exit 5; fi
OUTPUT="$ROOT/MynAssistant-Android-$VERSION_NAME.apk"
cp "$APK" "$OUTPUT"
echo
echo "SUCCESS: unit tests, build, signing, and UI regression checks passed."
echo "APK path: $OUTPUT"
echo "Size: $(du -h "$OUTPUT" | cut -f1) ($APK_BYTES bytes)"
echo "SHA-256: $(sha256sum "$OUTPUT" | awk '{print $1}')"
echo "In VS Code Explorer, right-click $OUTPUT and choose Download. Do NOT commit the APK or build log."
