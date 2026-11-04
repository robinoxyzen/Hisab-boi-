# হিসাব বই (Hisab Boi)

Digital ledger (খাতা) for small shop owners in Bangladesh. The UI is Bangla, with Bangla numerals, and Material 3.

This is an original app. Reference screenshots of other khata products were used only to understand workflows. The name, colors (teal and indigo), icon, layout, and copy are not taken from those products.

The app **never receives, holds, routes, or processes money**. Customers pay the shopkeeper's own bKash, Nagad, Rocket, bank account, or Bangla QR. There is no payment gateway.

## Features

1. **Google sign-in only.** Firebase Auth with Credential Manager. The auth layer is `AuthGateway`, so another provider can be added later without rewriting screens.
2. **Profile and businesses.** Name, owner, phone, photo, business type. Several businesses per user, with a switcher on the ledger.
3. **Ledger.** Customers and suppliers, মোট পাবো / মোট দেবো, search, filter, PDF and CSV. Per contact: দিলাম / পেলাম, note, date, photo, running balance, history, report.
4. **Cashbox.** Today's sales, current cash, today in/out. Cash sale, cash purchase, expense, owner deposit, owner withdrawal, and a daily report.
5. **QR.** The shopkeeper saves their own receiving details. The app shows a QR, number, and name, with share. A Bangla QR merchant string is encoded as-is. Wallet numbers are encoded as readable text. Money goes to the shopkeeper's account.
6. **Payment received.** "পেমেন্ট পেয়েছি" writes the shopkeeper's ledger row and shows a local notification **পেমেন্ট সফল**. Cloud Functions also send FCM. A webhook stub can later accept an MFS/bank notice. It is **disabled by default** and still does not move money.
7. **SMS and তাগাদা.** Transaction SMS and due reminders. Cloud Functions call a pluggable HTTP gateway (BulkSMSBD-style, SSL Wireless-style, or generic). The API key is an environment secret, never in the app. If no key is set, the phone SMS app opens. Reminders can also go to WhatsApp or a shareable image, now or on a future date (WorkManager).
8. **Offline.** Firestore persistence is on. Data lives under `users/{uid}/businesses/...`. Security rules allow only the owner. Soft-delete and restore. JSON backup and restore (a restore does **not** grant the verified badge).
9. **Menu.** Profile, accounts, statements, reports, settings, logout, plus products, backup, and trash.
10. **Product scan.** CameraX plus on-device ML Kit barcode scan and text recognition (Latin and Devanagari). Unknown barcodes are named once and saved in the business catalog. Several products can be scanned into the note. Works offline after the models are on the device. ML Kit does not have a full Bengali-script model; Bangla packaging text is read only where the Devanagari/Latin models can see it.
11. **Shop page.** Address, hours, photo, map pin or GPS. Public opt-in page with a `hisabboi://shop/{id}` link and directions in Google Maps. Nearby list of opted-in shops. Maps SDK key comes from `local.properties`.
12. **App lock.** 4–6 digit PIN (PBKDF2, salt in EncryptedSharedPreferences) and optional fingerprint. Lock on launch and after a background timeout. PIN reset uses Google re-authentication.
13. **Customer KYC.** Photo, NID front/back, NID number, address, reference, optional credit limit with a warning, verified badge when complete. NID number is encrypted with the Android Keystore before upload. Stored at `users/{uid}/businesses/{bid}/contacts/{cid}/kyc` and Storage under the same owner path. Not on public pages. Not readable by other shops or platform admins. Can be deleted.
14. **Verified shop.** The shopkeeper submits NID, photos, a selfie, and an optional trade license. Status: not submitted → pending → verified or rejected. A platform admin (Firebase custom claim `admin`) reviews them in the app. Only the green badge is public. Documents are readable by the owner and admins. `IdentityVerifier` is the plug-in point for a later provider such as Porichoy; the default is manual review.

## Modules

| Module | Role |
| --- | --- |
| `:core:model` | Models, money/Bangla format, repository interfaces, auth and lock interfaces |
| `:core:data` | Firebase, Google sign-in, Firestore repositories |
| `:core:ui` | Theme, shared controls, PDF/CSV/QR |
| `:core:security` | PIN, fingerprint, NID field encryption |
| `:feature:ledger` | Ledger, contacts, give/receive |
| `:feature:cashbox` | Cashbox |
| `:feature:payments` | Receiving QR. No gateway |
| `:feature:catalog` | Camera scan and product list |
| `:feature:shop` | Business profile, public page, nearby map |
| `:feature:trust` | KYC, shop verification, admin queue |
| `:feature:reminders` | তাগাদা |
| `:feature:account` | Settings, backup, reports, trash |
| `:app` | Navigation, login, FCM |
| `functions/` | TypeScript Cloud Functions |

## Setup

You need Android Studio (JDK 17 or 21), an Android SDK, and a Firebase project on the Blaze plan if you deploy Functions.

### 1. Firebase project

1. Create a project in the [Firebase console](https://console.firebase.google.com).
2. Add an Android app with package name `com.hisabboi.app`.
3. Download `google-services.json` and replace `app/google-services.json`. The file in git is a **placeholder**. The login screen will say so until you replace it. `./gradlew assembleDebug` still compiles.
4. Enable **Authentication → Google**.
5. Enable **Cloud Firestore**, **Storage**, and **Cloud Messaging**.
6. In Authentication → Google, copy the **Web client ID**. It must be the `client_type: 3` OAuth client inside `google-services.json` (the Google Services plugin exposes it as `default_web_client_id`).

### 2. SHA-1

The debug build uses the keystore committed at `app/debug.keystore` (alias `androiddebugkey`, store and key password `android`). That keeps the debug SHA-1 the same on every machine and in CI. It is a debug key only.

```text
SHA-1: 81:59:C2:4B:0F:17:23:CF:56:A2:5C:E1:ED:9E:7D:CF:07:68:BF:9E
```

Confirm with:

```bash
./gradlew :app:signingReport
```

Add that SHA-1 (and later the release key) under the Android app in Firebase, then download `google-services.json` again. The Firebase project id in `.firebaserc` is `hisab--boi`.

### 3. Maps key

Enable **Maps SDK for Android** in Google Cloud for a key restricted to `com.hisabboi.app`.

```bash
cp local.properties.example local.properties
```

Set `sdk.dir` and:

```properties
MAPS_API_KEY=your_key_here
```

Do not commit the key. Directions still open the Google Maps app with a `geo` intent when the key is empty; the in-app map stays hidden until the key is set.

### 4. Rules and indexes

From the repo root, after `npm i -g firebase-tools` and `firebase login`:

```bash
firebase use --add
firebase deploy --only firestore:rules,firestore:indexes,storage
```

Rules:

- `users/{uid}/**` is owner-only, except shop verification files, which admins can also read.
- Customer KYC is owner-only. Admins cannot read it.
- `publicShops` is world-readable and contains only name, address, hours, phone, photo, payment details the shopkeeper chose to show, and the verified flag. The flag must match the business document, which the owner cannot set to verified.
- `verificationRequests` is a short queue (no NID, no images).

### 5. Cloud Functions

```bash
cd functions
cp .env.example .env
npm install
npm test
npm run build
cd ..
firebase deploy --only functions
```

Region is **asia-south1** (Mumbai), same as Firestore. The Android client calls Functions in that region.

`.env` example:

```bash
ADMIN_EMAILS=robinoxyzen@gmail.com
SMS_PROVIDER=bulksmsbd
SMS_API_URL=https://bulksmsbd.net/api/smsapi
SMS_API_KEY=
SMS_SENDER_ID=HISABBOI
PAYMENT_WEBHOOK_ENABLED=false
PAYMENT_WEBHOOK_SECRET=
```

`ADMIN_EMAILS` is the only place the bootstrap admin is configured. On user create, and when the app calls `refreshAdminClaim`, a verified Google email in that list receives the custom claim `admin`. The app shows the admin screen only when `request.auth.token.admin == true`. It does not compare emails on the device.

For production, prefer a secret instead of a plain env value:

```bash
firebase functions:secrets:set SMS_API_KEY
firebase functions:secrets:set PAYMENT_WEBHOOK_SECRET
```

Set the SMS provider to `bulksmsbd`, `sslwireless`, or `generic`. See `functions/src/sms/provider.ts`. If `SMS_API_KEY` is empty, the function records `sms_not_configured` and the app opens the device SMS app when Settings → SMS is "ফোনের এসএমএস".

### 6. Payment webhook (off)

`paymentWebhook` returns HTTP 403 until `PAYMENT_WEBHOOK_ENABLED=true`. A future bank or MFS integration can POST:

`POST https://asia-south1-<project>.cloudfunctions.net/paymentWebhook`

Header: `x-hisab-webhook-secret: <PAYMENT_WEBHOOK_SECRET>`

```json
{ "uid": "", "businessId": "", "amount": 100, "contactId": "", "payerName": "", "note": "", "providerRef": "" }
```

The function writes a ledger row and a cash entry in that shopkeeper's data and the transaction trigger sends **পেমেন্ট সফল**. It does not capture or settle funds.

### 7. Build a debug APK

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Unit tests:

```bash
./gradlew :core:model:test
cd functions && npm test
```

## Privacy

- Ledger, cash, products, and KYC belong to the signed-in shopkeeper.
- KYC images are compressed, stored in that owner's Firestore docs and under `users/{uid}/businesses/{bid}/contacts/{cid}/kyc/` in Storage. Rules allow only that uid. Delete KYC removes those docs and clears the contact badge.
- The NID number is encrypted on the device (AES-GCM, key in Android Keystore) before it is saved. A backup restored on another phone cannot decrypt it.
- Shop verification images live under `users/{uid}/businesses/{bid}/verification/` and are readable by the owner and by accounts whose ID token has `admin: true`.
- Public shop pages and the nearby list show the shop name and, when approved, the badge. They do not show NID, selfies, or KYC.

## What is stubbed

- `google-services.json` in git is a placeholder. Replace it or Google sign-in will not succeed.
- SMS does nothing on the server until `SMS_API_KEY` and `SMS_API_URL` are set. The phone SMS app is the fallback.
- `paymentWebhook` is disabled. There is no payment gateway and there should not be one.
- `PorichoyVerifier` is an unbound stub. Verification is a manual admin queue.
- The in-app Google Map needs `MAPS_API_KEY`. Address, GPS coordinates, and external directions work without it.
- ML Kit's Devanagari model is used for Indic text. A dedicated Bengali OCR model is not bundled.

## বাংলায় সেটআপ

হিসাব বই ছোট দোকানের ডিজিটাল খাতা। টাকা সরাসরি দোকানদারের বিকাশ, নগদ, রকেট, ব্যাংক বা বাংলা QR-এ যায়। অ্যাপ কোনো পেমেন্ট গ্রহণ করে না।

1. Firebase প্রজেক্ট বানান। Android অ্যাপের প্যাকেজ `com.hisabboi.app`।
2. `app/google-services.json` প্লেসহোল্ডার ফাইলটি নতুন ফাইল দিয়ে বদলান।
3. Authentication-এ Google চালু করুন। ডিবাগ SHA-1 হলো `81:59:C2:4B:0F:17:23:CF:56:A2:5C:E1:ED:9E:7D:CF:07:68:BF:9E` (`app/debug.keystore`)। Firebase প্রজেক্ট আইডি `hisab--boi`। ফাংশন রিজিয়ন `asia-south1`।
4. Firestore, Storage, Cloud Messaging চালু করুন।
5. `local.properties`-এ `MAPS_API_KEY` দিন (ম্যাপের জন্য)। কী কমিট করবেন না।
6. `firebase deploy --only firestore:rules,firestore:indexes,storage`
7. `functions/.env`-এ `ADMIN_EMAILS=robinoxyzen@gmail.com` রাখুন বা নিজের অ্যাডমিন ইমেইল দিন। তারপর `firebase deploy --only functions`। অ্যাডমিন চেক শুধু কাস্টম ক্লেইম `admin` দিয়ে হয়।
8. এসএমএস গেটওয়ের `SMS_API_KEY` সেট না করলে ফোনের এসএমএস অ্যাপ খুলবে।
9. `./gradlew assembleDebug` দিয়ে ডিবাগ APK বানান।

গ্রাহকের এনআইডি ও কেওয়াইসি শুধু সেই দোকানদারের। পাবলিক দোকান পেজে শুধু নাম আর ভেরিফাইড ব্যাজ যায়। দোকান যাচাইয়ের কাগজ অ্যাডমিন দেখতে পান; কেওয়াইসি অ্যাডমিনও দেখতে পান না।

## License of the bundled font

Noto Sans Bengali is used for the UI and PDF text under the SIL Open Font License. See `THIRD_PARTY_NOTICES.md`.
