# MynAssistant Android 0.6.0 — strict free-start account services

This release keeps Firebase Authentication for Google sign-in and existing authentication sessions, keeps Firestore for user profile metadata, sends email action links through Brevo Free from the existing Render backend, and stores profile images in Cloudinary Free. It does not use Firebase Cloud Storage and does not send real SMS.

## What is free at the start?

- **Google sign-in / Firebase Authentication:** remains on the existing `myncloud-24` Firebase project.
- **Profile metadata:** remains in Firestore and is subject to Spark free quotas.
- **Verification and email-change messages:** Brevo Free supports 300 sends per day. Email is sent by the Render backend; a Firebase action link is generated securely so Firebase still owns the verification state.
- **Profile photos:** Cloudinary Free provides 25 monthly credits without a credit card. The crop dialog creates a square JPEG, the app sends it via a short-lived signed upload, and the API secret stays on the server.
- **Real SMS verification:** disabled in the strict free-start app. A phone number can be saved as pending/unverified. No Firebase SMS is triggered and the number must never be shown as verified until a future approved verification flow succeeds.
- **Firebase Storage:** not used by this release. Do not upgrade to Blaze just to upload profile photos.

Free tiers can have quotas and service limits; “free” does not mean unlimited.

## 1. Brevo email sender

1. Create a free Brevo account: <https://app.brevo.com/>.
2. In **Settings → Senders, Domains, IPs → Domains**, add a domain/subdomain you own and control. Copy the DNS records from Brevo and publish them at that domain's DNS provider, then wait for successful authentication.
3. Add a transactional sender address on the authenticated domain and verify it.
4. Under **SMTP & API → API Keys**, create a Brevo API key.
5. In Render Dashboard → `mynassistant-server` → **Environment**, set:
   - `BREVO_API_KEY`: Brevo API key (secret)
   - `BREVO_SENDER_EMAIL`: the verified sender address
   - `BREVO_SENDER_NAME`: `MynAssistant`
6. Keep `FIREBASE_WEB_API_KEY` and `FIREBASE_SERVICE_ACCOUNT_JSON` configured on the server. The web API key can be copied from `app/google-services.json`; the service account JSON is sensitive and must remain in Render secrets.
7. Redeploy the Render service. The mobile app will send its current Firebase ID token to `POST /api/v1/account/email/verification` or `POST /api/v1/account/email/change`. The server validates that token, asks Firebase to generate an action link, and sends that link via Brevo. The Brevo key is never bundled into the APK.

**Domain caveat:** Brevo recommends/needs domain authentication for reliable delivery. A free Gmail/Yahoo sender domain cannot be DNS-authenticated. If you don't control a DNS zone, do not invent records; use only an address/domain for which you can publish the exact records Brevo gives you.

## 2. Cloudinary profile-image uploads

1. Create a Cloudinary Free account: <https://cloudinary.com/users/register_free>.
2. In the Cloudinary Console, copy the cloud name and API key and reveal the API secret.
3. In Render Dashboard → `mynassistant-server` → **Environment**, set:
   - `CLOUDINARY_CLOUD_NAME`
   - `CLOUDINARY_API_KEY`
   - `CLOUDINARY_API_SECRET` (secret)
4. Redeploy the Render service. The app gets a signed upload request from `POST /api/v1/account/profile-photo/sign`, authorized with a Firebase ID token.
5. The server signs only the authenticated user's fixed avatar ID. The Android app uploads the cropped JPEG directly to Cloudinary, then writes the secure URL to Firebase Authentication and the user's Firestore profile. The Cloudinary API secret never leaves Render.
6. Cloudinary Free includes 25 monthly credits for storage, bandwidth and transformations combined. Stop uploading if the free allowance is exhausted; don't set up automatic paid upgrades if the requirement is strict zero cost.

Profile URLs are public CDN URLs, which is appropriate for a profile avatar. Do not upload private documents to this path.

## 3. Phone number flow

The app may save a validated phone number in Firestore as `pendingPhoneNumber` with `pendingPhoneVerified: false`. In this release, the SMS-send button is removed and a clear note explains that real SMS is disabled in free mode. No real SMS request is made.

Firebase Phone Authentication's real SMS delivery requires Blaze/pay-as-you-go and may incur a message fee. When you explicitly approve that cost later, a separate change can restore the SMS provider. Development-only Firebase test numbers are not real SMS and should not be mistaken for production delivery.

## 4. Firebase setup that remains

- Keep Google sign-in enabled under Firebase Console → Authentication → Sign-in method.
- Keep the current Firestore rules which allow each signed-in user to manage their own `users/{uid}` document.
- Do not enable Cloud Storage or configure Firebase Storage rules for this app version.
- The Firebase Authentication email-template editor does not need to be changed for the Brevo route; the backend builds a Firebase action link and sends it with a custom template.

## 5. Diagnose delivery

- Render health: <https://mynassistant-server.onrender.com/health>
- Render logs: Dashboard → `mynassistant-server` → Logs.
- Brevo delivery reports: Brevo → Transactional → Logs.
- Cloudinary usage: Cloudinary Console → Usage/Billing.
- The first request to Render Free may be slower after idle time. App calls have finite network timeouts and should show an error instead of spinning forever.

Never place `BREVO_API_KEY`, `CLOUDINARY_API_SECRET`, or Firebase service-account JSON into the APK, chat, `google-services.json`, or GitHub source.
