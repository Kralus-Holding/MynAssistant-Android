# MynAssistant account profile setup

The Android account screen stores display-name, email-verification, phone-number, and profile-photo state against the signed-in Firebase user. Profile photos are uploaded to Firebase Storage; the app also stores the resulting download URL in the user's Authentication profile and `users/{uid}` Firestore document.

## One-time Firebase setup

1. In Firebase Console for project `myncloud-24`, open **Authentication → Sign-in method** and enable the sign-in providers you use, including **Phone** if SMS verification is required.
2. In **Authentication → Settings**, check the SMS regions/policies for the users you expect to support. Add the Android app's SHA-1 and SHA-256 fingerprints in **Project settings → Your apps** so phone verification can complete on-device.
3. Confirm **Storage** is initialized for `myncloud-24`.
4. From this repository root, deploy only the profile-image rules after reviewing the target project:
   `firebase deploy --only storage --project myncloud-24`
5. Test an upload using a signed-in account, then confirm the app can read the resulting image URL after closing and reopening the app.

The Storage rules allow authenticated users to access only their own `profileImages/{uid}/...` path and restrict uploaded content to images up to 10 MiB.

## Email verification delivery

The Android client currently calls Firebase Authentication's `sendEmailVerification()` and `verifyBeforeUpdateEmail()`. Firebase's managed Authentication service generates and sends those links; the app does not send mail directly.

To send verification mail from MynAssistant-Server instead, configure a mail-delivery provider/SMTP credentials as server-side secrets, generate Firebase email-action links with the Firebase Admin SDK, require a valid Firebase ID token at the API endpoint, and add rate limits and verification-link handling. Those mail-service credentials and an authenticated custom-mail endpoint are not currently configured by this Android repository, so custom-server mail delivery must not be assumed active.
