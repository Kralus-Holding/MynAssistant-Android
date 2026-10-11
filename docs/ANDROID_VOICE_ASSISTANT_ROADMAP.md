# MynAssistant Android voice assistant roadmap

Status: implementation notes for the 0.4.2 candidate. A green build is not evidence of reliable wake-word detection on a physical phone. Real-device acceptance remains required.

## 0.4.2 candidate: reliability fixes

Implemented on `feature/android-command-status-tracking`:
- The command composer uses a single root-level insets path for status bars, navigation bars, and the IME. The composer no longer adds a second conditional navigation-bar inset.
- The always-on wake-listening control is removed from the main toolbar and placed in Settings as an explicit **Arka planda dinle** switch.
- Settings now links to overlay permission and app battery/background settings.
- Successful wake detections store only the canonical wake-phrase label, a timestamp, and a count in local app preferences. No raw microphone audio is written to this telemetry.
- Transcript matching is isolated in `MynWakeWordMatcher` and has unit tests for exact phrases, common transcript variants, punctuation, Turkish characters, aliases, and non-wake speech.
- App version is bumped to 0.4.2 / versionCode 19.

Not yet proven: keyboard placement across different OEM keyboards, wake-word hit-rate in noisy rooms, long-duration background survival, battery impact, and post-reboot behavior. CI and physical-device results must be reported separately.

## Current voice pipeline and its limits

1. The user explicitly enables listening from Settings while MynAssistant is visible and has granted microphone permission.
2. `MynWakeWordService` runs as a microphone foreground service with a persistent status notification.
3. Vosk's Turkish small model performs continuous on-device speech recognition after its one-time model download.
4. `MynWakeWordMatcher` recognizes the intended phrase and common near-spellings in the recognized text. A successful match opens the floating panel.
5. The user reviews the transcription and chooses **MynAssistant'ta aç** before the command is handed to the main app.

The current matcher is a transcript-matching layer, **not a custom acoustic wake-word neural network**. Its fuzzy text handling can compensate for some speech-to-text variations; it cannot recover a phrase that the ASR engine never recognizes. It should not be described as having Gemini-level wake-word accuracy.

## Android permission and lifecycle rules

- Microphone use requires `RECORD_AUDIO` and an appropriate, user-visible foreground-service flow. The microphone foreground service must be started while the app is eligible to start it; Android does not permit an ordinary app to silently start microphone capture from arbitrary background states.
- Keep the ongoing foreground-service notification and microphone privacy indicator visible. Listening is opt-in and must be easy to stop.
- `SYSTEM_ALERT_WINDOW` is separate from microphone permission. The floating panel requires the user to approve “Display over other apps”; if this permission is revoked, the app should stop trying to create the overlay and explain the missing permission.
- Battery/background settings are vendor-specific. Opening Android's app-details/battery settings is a shortcut, not a guarantee that the OS or OEM will never suspend the process. Do not request an unrestricted battery exemption automatically or promise uninterrupted listening.
- The existing Accessibility service is scoped to the explicit WhatsApp automation flow. It is **not** a way to bypass microphone restrictions and must not be used as a hidden always-on microphone path.
- The manifest currently declares the `microphone` foreground-service type. Do not add other service types speculatively; select them only if the service's actual work and Android's required permissions match that type.
- The project minimum is API 26 and target/compile API 36. Run the device matrix on Android 11/API 30 through current stable target devices, and include Android 17/API 37 when a stable SDK/device is available. Record OEM, OS build, keyboard, and power-management settings in bug reports.

## Test gates for release

### Automated
- Run Android debug build and unit tests, including `MynWakeWordMatcherTest`.
- Verify the merged manifest contains the microphone foreground-service permission/type, microphone runtime permission, and overlay permission.
- Test the pure wake phrase matcher against accepted and rejected transcript examples. Keep false-positive cases in the test suite whenever aliases are added.

### Physical-device checks
- Keyboard closed: the composer remains above the three-button navigation bar and does not overlap it.
- Keyboard open/close repeatedly: the composer touches the keyboard with no unexplained extra gap; it never jumps behind the navigation bar after IME dismissal.
- Start listening, see the ongoing notification/privacy indicator, say “Hey Meyene”, and observe the panel opening. Repeat at normal conversation volume, at a distance, with background TV/noise, and with screen locked/unlocked where Android permits the scenario.
- Confirm “Hey Meyene” without a following command opens a ready-to-listen panel; confirm a phrase followed immediately by a command captures the trailing command.
- Deny microphone, notification, and overlay permissions individually. Each failure must show an actionable explanation and a stable diagnostic code.
- Stop from Settings and notification action; verify audio capture ends. Repeated start/stop must not leak overlays, recognizers, or notifications.
- Test battery-optimized and user-exempted states, force-stop, process death, reboot, airplane mode after model download, and storage pressure. Do not assume Android will restart the service automatically after force-stop.
- Measure battery and memory over at least a multi-hour idle run before describing the feature as battery-efficient.

## Recommended phased architecture

### Phase A — stabilize existing behavior (0.4.2)
Keep the current single service and local Vosk model. Fix user-visible layout and settings, expose last successful wake timestamp/count, add matcher unit tests, and collect reproducible device results before tuning recognition logic further.

### Phase B — separate responsibilities (0.5)
Introduce narrow interfaces so the speech backend can change without rewriting the UI:
- `WakeWordEngine`: consumes PCM frames and emits a wake event.
- `CommandSpeechEngine`: records/transcribes a command only after a wake event or explicit mic tap.
- `OverlayController`: owns the floating window, drag state, dismissal, and lifecycle.
- `VoiceSettingsRepository`: persists opt-in, selected wake phrase/backend, and user preferences.
- `PermissionCoordinator`: explains and checks microphone, notification, overlay, and optional battery settings.
- `AssistantCommandRouter`: validates intent/action permissions before dispatching phone, web, or Windows-agent operations.

Keep the microphone owner singular. Do not have the service, Activity, and Accessibility service all compete for the microphone. Use a small bounded audio buffer; do not retain audio after the wake-word engine has processed it.

### Phase C — compare dedicated wake-word engines
Evaluate candidates with the same recorded test set, phone, microphone distance, and noise conditions. Measure false accepts/hour, false rejects, wake latency, CPU, RAM, and battery.

- **Vosk transcript matching (current)**: no additional acoustic wake model; easy to keep offline, but detection quality depends on ASR recognizing an uncommon brand name and continuous ASR may consume more power than a dedicated detector. Grammar restrictions may help a prototype but can harm command transcription if the same recognizer is expected to understand free-form speech.
- **Picovoice Porcupine**: mature on-device wake-word SDK, but proprietary terms, model creation, access-key requirements, and commercial pricing must be checked against current official terms before committing. Do not hard-code a historical price estimate.
- **openWakeWord**: open-source model/tooling, commonly used with ONNX/Python runtimes. Android production use requires validating an Android-compatible runtime, model/license terms, CPU usage, and a repeatable custom-model training pipeline; it is not a drop-in Kotlin replacement.
- **microWakeWord**: designed around embedded/TFLite Micro-style deployments. Treat it as an embedded-model research option, not the default native Android implementation without a working Android integration and benchmarks.
- **Android `SpeechRecognizer`**: suitable for user-triggered utterance recognition where the device/service supports it, but do not assume it provides a continuous offline wake-word engine or that every provider works offline.
- **Snowboy**: legacy/deprecated; avoid adding it to a new maintainable implementation.

Use a provider abstraction so a dedicated detector can be tested as an opt-in experiment without destabilizing the current Vosk fallback. Select an engine only after measuring Turkish “Hey Meyene” accuracy and reviewing its license.

### Phase D — custom wake phrase and personalization
If a trainable custom wake-word model is justified, create a dataset with consented positive examples of “Hey Meyene” from varied voices, distances, phone orientations, and noise conditions, plus hard negatives (similar-sounding phrases, Turkish conversation, TV/audio). Split training/validation/test data by speaker, tune thresholds, and report false accepts and false rejects. Keep samples local by default and require explicit consent for any upload. A wake-word detector is a compact classifier, not a general-purpose conversational model; it does not “train MynAssistant into Gemini.”

### Phase E — richer voice command experience
After the wake trigger is reliable:
- add command timeout, cancel/retry, haptic/audio feedback, and a concise transcription confirmation state;
- support an optional local command model for deterministic phone actions, with allowlisted intents and a confirmation gate for calls/messages or destructive actions;
- consider an optional remote LLM backend only with an explicit connectivity setting, transparent data disclosure, redaction, and a reliable offline fallback;
- add a Room-backed searchable diagnostics screen and opt-in export that excludes raw audio and secrets;
- add selectable wake-word/backend settings only after each backend has a tested installer/model lifecycle;
- consider local text-to-speech responses, language settings, custom phrases, and per-device tuning as separate features.

## Diagnostic code convention

Keep the codes stable and user actionable:
- `MYN-PERM-4002`: overlay permission settings could not be opened.
- `MYN-PERM-4003`: app battery/background settings could not be opened.
- `MYN-WAKE-8010`: recoverable streaming audio issue.
- `MYN-WAKE-8011`: repeated audio stream failures; the user should inspect power-management settings and restart listening.
- Add a new code for a distinct failure instead of reusing unrelated codes. Avoid recording raw audio, full contacts, credentials, or private command contents in diagnostics.

## Definition of done

A release is ready only when automated build/tests pass, the resulting APK artifact is verified, the physical-device matrix is completed, and all limitations are documented. A successful compilation alone is not a release sign-off.
