# Ruko Voice Agent

A Twilio Voice solution consisting of:

- **Node/Express backend** (`server.js`) — issues Twilio access tokens and handles TwiML webhooks.
- **Kotlin Android app** (`android/`) — places outgoing calls and receives incoming calls via FCM push notifications using the Twilio Voice SDK.

---

## Table of contents

1. [Backend setup](#1-backend-setup)
2. [Twilio configuration](#2-twilio-configuration)
3. [Firebase / FCM setup](#3-firebase--fcm-setup)
4. [Android app — build & run](#4-android-app--build--run)
5. [Changing the package name](#5-changing-the-package-name)
6. [Environment variable reference](#6-environment-variable-reference)

---

## 1. Backend setup

### Prerequisites

- Node.js ≥ 18
- A Twilio account ([twilio.com](https://www.twilio.com))

### Install & run

```bash
# In the repo root
npm install
npm start          # starts on port 3000 (override with PORT env var)
```

### Environment variables

Copy and fill in the required values before starting the server.  
**Never commit secrets to source control.**

```bash
export TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export TWILIO_API_KEY_SID=SKxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export TWILIO_API_KEY_SECRET=your_api_key_secret
export TWILIO_TWIML_APP_SID=APxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export TWILIO_PUSH_CREDENTIAL_SID=CRxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx   # optional
export TWILIO_CALLER_ID=+1xxxxxxxxxx                                   # optional
```

Or use a `.env` file (`.gitignore` already excludes it) with a tool like [`dotenv-cli`](https://github.com/entropitor/dotenv-cli):

```bash
npx dotenv-cli -- npm start
```

### Endpoints

| Method | Path     | Description |
|--------|----------|-------------|
| GET    | `/`      | Health check |
| GET    | `/token` | Returns a Twilio access token (query param `?identity=alice`) |
| POST   | `/voice` | TwiML webhook for Twilio — handles outgoing/incoming call routing |

---

## 2. Twilio configuration

### a. Create an API Key

1. Open [Twilio Console → API Keys](https://console.twilio.com/us1/account/keys-credentials/api-keys).
2. Click **Create API Key**.  Select **Standard** type.
3. Copy the **SID** (`SK…`) and **Secret** — you will not see the secret again.
4. Set `TWILIO_API_KEY_SID` and `TWILIO_API_KEY_SECRET`.

### b. Create a TwiML App

1. Open [Twilio Console → TwiML Apps](https://console.twilio.com/us1/develop/voice/manage/twiml-apps).
2. Click **Create new TwiML App**.
3. Set **Voice Request URL** to your public backend URL + `/voice`  
   e.g. `https://your-server.example.com/voice` (must be HTTPS for production).
4. Save and copy the **App SID** (`AP…`).
5. Set `TWILIO_TWIML_APP_SID`.

### c. Configure a Push Credential (for incoming calls via FCM)

1. Obtain your **Firebase Server Key** from the Firebase Console  
   (Project Settings → Cloud Messaging → Legacy Server Key).
2. Open [Twilio Console → Push Credentials](https://console.twilio.com/us1/develop/voice/manage/push-credentials).
3. Click **Create new Credential**, choose **FCM**, paste the Server Key.
4. Copy the credential SID (`CR…`).
5. Set `TWILIO_PUSH_CREDENTIAL_SID`.

---

## 3. Firebase / FCM setup

The Android app uses Firebase Cloud Messaging (FCM) to receive incoming call notifications.  
The file `google-services.json` **must not be committed** (it is listed in `.gitignore`).

### Step-by-step

1. Go to [Firebase Console](https://console.firebase.google.com) and click **Add project**.
2. Once created, click **Add app** → Android.
3. Enter the package name: `com.ruko.voiceagent`  
   (see [§ 5](#5-changing-the-package-name) if you changed it).
4. Download `google-services.json`.
5. Place the file at:
   ```
   android/app/google-services.json
   ```
   This location is gitignored — it will not be committed.
6. (Optional) Follow the Firebase Console steps to add the SHA-1 fingerprint of your debug keystore if required.

---

## 4. Android app — build & run

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer, **or** JDK 17 + command-line tools.
- `google-services.json` placed at `android/app/google-services.json` (see §3).
- Backend running and reachable from the device/emulator.

### Configure backend URL

By default the app points to `http://10.0.2.2:3000` (Android emulator ↔ host localhost).  
To point to a real server, edit `android/app/build.gradle`:

```groovy
buildConfigField "String", "TOKEN_SERVER_URL", '"https://your-server.example.com"'
```

### Build debug APK

```bash
cd android
./gradlew :app:assembleDebug
```

The output APK will be at:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

### Install on a device

```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

### Open in Android Studio

Open the `android/` folder directly in Android Studio (not the repo root).

---

## 5. Changing the package name

The default package name is `com.ruko.voiceagent`.  To rename it:

1. In `android/app/build.gradle`, change:
   - `namespace`
   - `applicationId`
2. Rename the source directory:
   ```
   android/app/src/main/java/com/ruko/voiceagent/
   ```
   to match your new package, e.g. `com/example/myapp/`.
3. Update the `package` declaration at the top of each `.kt` file.
4. In **Firebase Console**, create a new Android app entry with the updated package name and download a fresh `google-services.json`.

---

## 6. Environment variable reference

| Variable | Required | Description |
|----------|----------|-------------|
| `TWILIO_ACCOUNT_SID` | ✅ | Twilio account SID (`AC…`) |
| `TWILIO_API_KEY_SID` | ✅ | API Key SID (`SK…`) |
| `TWILIO_API_KEY_SECRET` | ✅ | API Key secret |
| `TWILIO_TWIML_APP_SID` | ✅ | TwiML App SID (`AP…`) for outgoing calls |
| `TWILIO_VOICE_TWIML_APP_SID` | — | Alias for `TWILIO_TWIML_APP_SID` |
| `TWILIO_PUSH_CREDENTIAL_SID` | — | FCM Push Credential SID (`CR…`) for incoming calls |
| `TWILIO_CALLER_ID` | — | Caller ID shown to called party (E.164 phone number) |
| `PORT` | — | HTTP port (default: `3000`) |
