# MynAssistant Android 0.5.9 — Account, email, SMS and profile-photo setup

## What the app does

- A user can edit their Firebase Authentication display name; the new value is also merged into `users/{uid}` in Firestore.
- A picked photo opens an interactive 1:1 crop dialog (zoom, horizontal position, vertical position). The final square JPEG is uploaded to `profilePhotos/{uid}/...`, then its download URL is saved to Firebase Authentication and the Firestore user profile.
- A new phone number is saved first as `pendingPhoneNumber` / `pendingPhoneVerified: false`. It is displayed as unverified until Firebase Phone Authentication sends a code and the code is accepted. Only then is the number attached to Firebase Authentication.
- A changed email address is stored as `pendingEmailAddress` / `pendingEmailVerified: false` after Firebase accepts the verification request. The current email remains active until the Firebase verification action completes.
- The UI exposes the Firebase Authentication error code or an actionable hint if the email/SMS operation fails.

## One-time Firebase Console configuration

The checked-in `app/google-services.json` points at project `myncloud-24` and declares the bucket name `myncloud-24.firebasestorage.app`. That configuration alone does **not** prove that the Storage product/bucket has been initialized or that the checked-in rules have been deployed.

### 1. Enable Firebase Storage

1. Open Firebase Console and select project `myncloud-24`.
2. Open **Build → Storage** and finish **Get started** if the bucket has not been created.
3. Confirm the default bucket is `myncloud-24.firebasestorage.app`.
4. From an authorized Firebase CLI session at the repository root, deploy the checked-in rules:

   ```bash
   firebase login
   firebase deploy --only storage --project myncloud-24
   ```

5. Rules permit authenticated users to read/write only their own `profilePhotos/{uid}/...` path, limit each upload to under 5 MiB, and require `image/jpeg`. The app's cropper produces a JPEG and supplies that content type explicitly.

If the CLI says the bucket does not exist, finish initializing Storage in Firebase Console first. Deploying rules from GitHub cannot create/activate a Firebase product without an authorized Console/project setup.

### 2. Enable and verify email delivery

1. In **Build → Authentication → Sign-in method**, enable the sign-in providers actually used by the app, including **Email/Password** when email/password accounts are supported.
2. In **Authentication → Templates**, inspect the verification-email template and sender details. Ensure the relevant Firebase project's email templates and authorized domains/action URL configuration are valid.
3. If signing in with Google, Firebase normally already considers the Google-provided email verified. The app deliberately does not send another verification email for an already-verified current address. Use **E-posta adresini değiştir** to request a verification link for a new address.
4. A successful client SDK result means Firebase accepted the request; it is not proof that the recipient's mail provider delivered it to the inbox. Check spam/junk, template/action configuration, Firebase limits and the exact in-app error message.

The current `MynAssistant-Server` API has Firebase Auth REST functionality for administrative account setup/password-reset email, but it is not configured as a custom SMTP mail sender for mobile email-change/verification links. No SMTP credentials or third-party mail-provider secrets are present in this Android repository. Do not commit such secrets to GitHub.

### 3. Enable SMS phone verification

1. In **Build → Authentication → Sign-in method**, enable **Phone**.
2. In the Firebase Authentication settings, check SMS region policy and allow the recipient region (for a Turkish number, Turkey / +90).
3. Add the Android app signing SHA-1 and SHA-256 fingerprints under **Project settings → Your apps**. Verify the application ID remains `com.mynkralus.mynassistant`.
4. Check Firebase phone-verification quotas, anti-abuse/app-verification settings, and billing requirements shown by the project. Exact requirements depend on the project's Firebase plan, settings and current quota.
5. Retry the SMS request. The app now surfaces a more specific Firebase code/hint instead of swallowing every failure into one generic message.

No SMS can be sent by changing Kotlin UI code alone if the Firebase Phone provider, project region policy or app-verification setup is disabled. The signed-in user must still confirm the received code before Firebase Authentication marks the number verified.

## Build

The app version is **0.5.9** with **versionCode 36**. From the latest GitHub `main` in Codespaces, run:

```bash
bash scripts/build-apk-codespace-clean.sh
```

That script builds a detached temporary worktree from `origin/main`, keeps local/Copilot changes intact, executes the existing test/build/signature/regression script, and copies the verified APK into the open workspace. It does not deploy Firebase rules; deploy rules separately as described above.
