# Ruko Voice Agent

Ruko Voice Agent is a Twilio-powered voice backend built with Node.js and Express.

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
  `401 Unauthorized`. **This value has no default** — if it is not set, the
  server responds to `/token` with `500 Server misconfiguration` instead of
  silently allowing unauthenticated access. Generate a strong random value,
  e.g. `openssl rand -hex 32`, and keep it secret (do not commit it to
  source control).

The remaining `TWILIO_*` variables (`TWILIO_ACCOUNT_SID`,
`TWILIO_API_KEY_SID`, `TWILIO_API_KEY_SECRET`, `TWILIO_TWIML_APP_SID`) are
your Twilio account credentials used to mint Voice Access Tokens; find them
in the [Twilio Console](https://console.twilio.com). `TWILIO_TWIML_APP_SID`
must reference a TwiML Application configured for outgoing calls, otherwise
Twilio returns error 31002.

## Endpoints

| Method | Path     | Auth required | Description |
|--------|----------|---------------|--------------|
| GET    | `/`      | No  | Health check |
| POST   | `/voice` | No (Twilio webhook) | Returns TwiML for the voice call |
| GET    | `/token` | Yes (API key) | Returns a Twilio Voice Access Token (query: `?identity=<id>`) |

Unknown routes return `404 {"error": "Not found"}`. Unhandled errors are
caught by a generic error handler and return `500 {"error": "Internal
Server Error"}` without crashing the process.

## Testing

Run the test suite with:

```bash
npm test
```

Tests use `jest` and `supertest` and cover `/token` authentication, TwiML
generation on `/voice`, and 404 handling. No real Twilio credentials are
required — tests use placeholder values.

## Security notes

- The `/token` endpoint issues Twilio Voice Access Tokens and is protected by
  the `TWILIO_API_KEY` middleware — never disable this check or expose the
  endpoint without authentication, as it would allow toll fraud and identity
  spoofing.
- The `identity` query parameter is sanitized (alphanumeric, `_`/`-` only,
  max 64 chars) before being embedded in the Access Token.
- Request bodies are capped at 10kb to reduce DoS surface.
- The server shuts down gracefully on `SIGTERM`/`SIGINT`, finishing
  in-flight requests (with a 10s timeout) before exiting.
- Any client reaching this server in production must use HTTPS (cleartext
  HTTP should be restricted to local development hosts only).

