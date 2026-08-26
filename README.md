# Ruko Voice Agent

A **Kotlin Android app** backed by a **Node/Express server** that enables voice calling via [Twilio Voice SDK](https://www.twilio.com/docs/voice/sdks/android).

---

## Repository structure

```
ruko-voice-agent/
├── server.js           # Node/Express backend
├── package.json
├── android/            # Android Studio / Gradle project
│   ├── app/
│   │   └── src/main/
│   │       ├── java/com/ruko/voice/   # Kotlin sources
│   │       └── res/                   # Layouts, strings, etc.
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   └── gradlew
└── README.md
```

---

## Backend

### Requirements

- Node.js 18+
- A Twilio account with:
  - Account SID
  - API Key SID + Secret
  - TwiML Application SID (points to your `/voice` webhook URL)

### Environment variables

| Variable          | Description                                    |
|-------------------|------------------------------------------------|
| `ACCOUNT_SID`     | Twilio Account SID (`AC…`)                     |
| `API_KEY_SID`     | Twilio API Key SID (`SK…`)                     |
| `API_KEY_SECRET`  | Twilio API Key Secret                          |
| `TWIML_APP_SID`   | TwiML Application SID (`AP…`)                  |
| `PORT`            | HTTP port (default: `3000`)                    |

Create a `.env` file (not committed) and use a tool such as [`dotenv-cli`](https://github.com/entropitor/dotenv-cli) or set them in your hosting environment.

### Run locally

```bash
npm install
ACCOUNT_SID=ACxxx API_KEY_SID=SKxxx API_KEY_SECRET=xxx TWIML_APP_SID=APxxx npm start
```

The server exposes:

| Method | Path     | Description                                        |
|--------|----------|----------------------------------------------------|
| GET    | `/`      | Health check                                       |
| GET    | `/token` | Mints a Twilio Access Token (query: `?identity=…`) |
| POST   | `/voice` | Twilio Voice webhook (TwiML response)              |

### Twilio setup

1. **Create an API Key** in the [Twilio Console](https://console.twilio.com) → Account → API Keys.
2. **Create a TwiML App** → Voice → TwiML Apps → New.
   - Set the **Voice Request URL** to `https://<your-server>/voice` (HTTP POST).
3. Copy the TwiML App SID (`AP…`) into `TWIML_APP_SID`.
4. Deploy the server so it has a public HTTPS URL (e.g. [Render](https://render.com), [Fly.io](https://fly.io), or [Railway](https://railway.app)).

---

## Android app

### Requirements

- Android Studio (Hedgehog or newer) **or** JDK 17+ with the Gradle wrapper.
- A Firebase project with an Android app registered (package name `com.ruko.voice`).
- `google-services.json` downloaded from Firebase Console → Project Settings → Your apps.

### Firebase / FCM setup

1. Go to [Firebase Console](https://console.firebase.google.com) and create a project (or use an existing one).
2. Click **Add app → Android** and enter package name `com.ruko.voice`.
3. Download `google-services.json` and copy it to `android/app/google-services.json`.  
   ⚠️ **Do not commit this file** – it is listed in `.gitignore`.
4. In Twilio Console → Voice → Push Credentials, create a new **FCM credential**:
   - Paste the **Server Key** from Firebase Console → Project Settings → Cloud Messaging → **Legacy server key** (or upload the service account JSON for HTTP v1).
5. Note the Twilio Push Credential SID – the SDK registers it automatically when `Voice.register()` is called.

### Configure the server URL

Edit `android/gradle.properties` (or create `android/local.properties`):

```properties
SERVER_BASE_URL=https://your-server.example.com
TWILIO_IDENTITY=alice
```

`local.properties` is never committed and overrides `gradle.properties`.

### Build the APK

```bash
cd android
./gradlew assembleDebug
```

The debug APK is output to `android/app/build/outputs/apk/debug/app-debug.apk`.

Install on a connected device / emulator:

```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

### Runtime permissions

On first launch the app requests:
- `RECORD_AUDIO` – required for voice calls.
- `POST_NOTIFICATIONS` (Android 13+) – required for incoming call notifications.
- `BLUETOOTH_CONNECT` (Android 12+) – required for Bluetooth headsets.

---

## How it works

1. **App start** → `MainActivity` fetches a Twilio Access Token from `GET /token?identity=<id>`.
2. Token is cached in `SharedPreferences` via `TokenStore`.
3. **Outgoing call** → tap *Place Call* → `Voice.connect(context, token, params)`.
4. **Incoming call** → Twilio sends an FCM push to the registered device → `MyFirebaseMessagingService.onMessageReceived` calls `Voice.handleMessage` → shows `IncomingCallActivity`.
5. The `VoiceCallService` foreground service keeps the call alive while the app is in the background.

