# Ruko Voice Agent

## Setup

1. Install dependencies: `npm install`
2. Copy `.env.example` to `.env` and fill in the required values (see below).
3. Start the server: `npm start`

## Environment variables

See [`.env.example`](.env.example) for the full list. The most important one
for security is:

- `TWILIO_API_KEY` — a secret application-level API key required to call the
  `/token` endpoint. Requests must include it as an `X-API-Key` header
  (preferred) or a `?api_key=` query parameter (supported as a fallback,
  but avoid it where possible since query strings can end up in access
  logs or browser history); requests without a valid key are rejected with
  `401 Unauthorized`. **Do not use the insecure development default in
  production** — generate a strong random value instead, e.g.
  `openssl rand -hex 32`, and keep it secret (do not commit it to source
  control).

The remaining `TWILIO_*` variables (`TWILIO_ACCOUNT_SID`,
`TWILIO_API_KEY_SID`, `TWILIO_API_KEY_SECRET`, `TWILIO_TWIML_APP_SID`) are
your Twilio account credentials used to mint Voice Access Tokens; find them
in the [Twilio Console](https://console.twilio.com).

## Security notes

- The `/token` endpoint issues Twilio Voice Access Tokens and is protected by
  the `TWILIO_API_KEY` middleware — never disable this check or expose the
  endpoint without authentication, as it would allow toll fraud and identity
  spoofing.
- The Android client must only use HTTPS to reach this server in production
  (cleartext HTTP is restricted to local development hosts only).
