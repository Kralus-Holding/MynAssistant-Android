# MynAssistant Android + Desktop Agent

Native Android companion app for MynAssistant, with an isolated Windows companion agent under desktop-agent/. The existing MynAi/Python project is not modified.

## Android development

- Kotlin
- Jetpack Compose
- Firebase Authentication and Cloud Firestore
- GitHub Actions for cloud APK builds

## Two PC connection paths

### Cloud connection (recommended for remote / web-panel use)

- Start the Windows MynAssistant bridge from Windows Settings > Telefon-PC bağlantısı and complete Google sign-in with the same account as Android.
- The bridge registers a `firebase-agent` device in Firestore, sends heartbeats, processes the owner's queued commands, and streams temporary screenshot frames only while a preview lease is active.
- Android discovers this device from Firestore; the web Control Center also sees it and can queue the allowlisted command set. This route works across networks as long as both clients have internet access and Firebase configuration is valid.

### Optional direct local-network connection

- The Windows bridge also exposes authenticated `GET /health` and `POST /command` endpoints on TCP port 8765, bound to the LAN interfaces.
- Windows Settings shows the current private-LAN URL and a random per-user connection key. Enter both in Android Settings > Telefon-PC bağlantısı and tap “Kaydet ve bağlantıyı test et”.
- The phone and PC must be on the same trusted private Wi-Fi. If Windows Firewall asks, allow the bridge only on private networks. Do not create router port forwarding or share the connection key.
- The local bridge accepts bounded text commands that are handled by the existing allowlist; it does not expose a shell or arbitrary script execution.

The cloud route is separate from the optional LAN route. The web panel only queues Firebase commands for devices registered with `connectionMode=firebase-agent`; local-only records are not remote-web command targets.

## Build outputs

- Android APK: published to GitHub Releases only after CI verifies the full-feature APK and its signing certificate. Actions artifact upload is disabled because the repository's Actions storage quota is full.
- Windows companion: built as a versioned prerelease ZIP through the Windows Agent workflow.

## Current scope

Android version 0.6.1 adds the optional LAN URL/token configuration and health-test UI. The Windows bridge must be rebuilt from the same canonical agent source for direct LAN support; the cloud route stays available independently.


The new Windows Agent provides automatic device discovery, online heartbeats, volume/desktop/mouse commands, Unicode text entry, Backspace/Enter input, and an on-demand low-frame-rate JPEG desktop preview. Smooth full-screen remote desktop streaming is a later WebRTC milestone.


## Device registration (Android)

Version 0.2.2 registers the signed-in Android installation in Firestore `devices`. The record uses a per-installation ID that is hashed with the Firebase UID (the raw installation identifier is not used as the document ID). The app writes a heartbeat about once per minute while the activity is in the foreground and marks its record offline when the activity is paused. After a sign-out/session switch, heartbeats stop; the admin server treats records without a recent heartbeat as offline within its three-minute freshness window. Android presence registration does not enable Windows remote commands; those remain restricted to the `firebase-agent` connection mode.


## Full-feature Android APK recovery

The release line is based on the 0.4.9 app (versionCode 26) rather than the reduced 0.2.2 shell. Version 0.5.0 (versionCode 27) restores the Hey Meyene wake-word service, Vosk Turkish recognition, sherpa-onnx keyword spotting, model installers, accessibility integration, diagnostics, and their unit tests while retaining Firebase device registration/heartbeat. CI runs unit tests, verifies the signed APK, rejects unexpectedly small APKs, and publishes a prerelease APK asset on pushes to main. The APK is distributed through GitHub Releases; Actions-artifact upload is intentionally omitted because this repository's Actions storage quota is full.


## Account and auth polish (Android 0.5.9)

The device-name pencil sits beside the device name; Save uses the proper save icon. Online/offline badges adapt to light and dark themes. Touchpad dots avoid the `Touchpad` title. The launcher foreground is reduced into Android's adaptive safe zone so the full original mark remains visible. The shared auth form shows only one Google action according to the selected mode. Email/password registration requires at least eight characters. Email auth feedback is shown once inside the form, not copied into the navigation drawer. CI asserts that only one mode-specific Google action exists, old six-character guidance is absent, and auth warnings do not render in the drawer. The account page retains Google linking, email verification, SMS-verified phone numbers, and profile photo upload. Existing remote-control, keyboard and wake-word paths remain intact.

### Firebase setup required for account extras

The Android app already points to the `myncloud-24` Firebase project. Enable **Authentication → Phone** for SMS verification and publish `storage.rules` to the project's Storage bucket before using profile-photo upload. The Storage rules restrict writes to each signed-in user's own `profilePhotos/{uid}/...` path, images under 5 MiB. From a configured Firebase CLI environment, run `firebase deploy --only storage --project myncloud-24`. SMS delivery also depends on Firebase Phone Authentication, app verification and project quota/billing settings.


The 0.5.8 follow-up makes the account avatar square with rounded corners, keeps the email verification action on one line, and lets users request an email address change through Firebase's verification link before the new address becomes active. The drawer version label matches the Gradle app version. Release APKs still require a successful GitHub Actions build and verified release asset.


## Account contact updates (Android 0.5.9)

The account page supports display-name editing and a square 1:1 profile-photo crop preview with zoom and position controls before JPEG upload. Email changes are recorded as pending until Firebase verifies the new address. Phone numbers are saved as pending/unverified data before the separate SMS verification action; Firebase Authentication only attaches the phone number after code verification. Delivery errors expose Firebase error codes and actionable setup hints.

Firebase Storage must be initialized for the bucket configured in `app/google-services.json` (`myncloud-24.firebasestorage.app`), and the current repository rules must be deployed by an authorized Firebase CLI session. Firebase Phone Authentication must be enabled in the Firebase Console and also needs valid Android app verification, SMS region policy, quota and any required billing settings. Google sign-in marks the provider's email as verified; the app will not send a verification email for an address Firebase already considers verified. Email-address changes use Firebase Authentication's verification action email, not MynAssistant-Server SMTP, because a custom SMTP sender is not configured in that server.


See [docs/ACCOUNT-CONTACT-SETUP-0.5.9.md](docs/ACCOUNT-CONTACT-SETUP-0.5.9.md) for the one-time Firebase Storage deployment and Firebase email/SMS authentication setup required for account verification and profile photos.


## Free-start account services (Android 0.6.0)

The zero-cost startup path keeps Firebase Authentication for Google sign-in and Firestore for profile metadata, sends Firebase-generated verification/change-email links through Brevo Free on the existing Render backend, and stores cropped profile avatars in Cloudinary Free. Firebase Cloud Storage is no longer a dependency in this release, so avatar uploads do not require upgrading Firebase to Blaze. Real SMS verification is deliberately disabled; a saved phone number remains pending/unverified until an explicitly approved SMS service is configured.

Setup and required Render environment variables: [Free account services setup](docs/FREE-ACCOUNT-SERVICES-0.6.0.md). Secrets must be set in the Render dashboard, never embedded in Kotlin or committed to GitHub.
