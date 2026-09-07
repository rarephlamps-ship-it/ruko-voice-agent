const express = require("express");
require("dotenv").config();
const twilio = require("twilio");

const app = express();
app.use(express.urlencoded({ extended: false, limit: "10kb" }));
app.use(express.json({ limit: "10kb" }));

// Application-level API key used to authenticate requests to this server's
// /token endpoint. This is NOT the same as Twilio's own Account SID/Auth
// Token or API Key/Secret used below to mint Access Tokens.
const APP_API_KEY = process.env.TWILIO_API_KEY;

// Authentication middleware: without this, anyone could call /token and
// obtain a valid Twilio Voice Access Token, enabling toll fraud and
// identity spoofing via the `identity` query parameter.
//
// Only the `X-API-Key` header is accepted (never a query parameter):
// query strings can end up in server access logs, browser history, or
// proxy logs, which would leak the key.
function requireApiKey(req, res, next) {
  if (!APP_API_KEY) {
    console.error("Server misconfiguration: TWILIO_API_KEY is not set");
    return res
      .status(500)
      .json({ error: "Server misconfiguration: authentication is not configured" });
  }
  const apiKey = req.get("X-API-Key");
  if (!apiKey || apiKey !== APP_API_KEY) {
    return res
      .status(401)
      .json({ error: "Unauthorized: missing or invalid API key" });
  }
  next();
}

// Twilio Voice endpoint
app.post("/voice", (req, res) => {
  const twiml = new twilio.twiml.VoiceResponse();
  twiml.say({ language: "da-DK" }, "Hej. Dette er Rukos AI agent. Systemet virker.");
  res.type("text/xml");
  res.send(twiml.toString());
});

// Health check
app.get("/", (req, res) => {
  res.send("Ruko Voice Agent kører.");
});

// Issues short-lived Twilio Voice Access Tokens for the mobile client.
// Protected by requireApiKey so that only requests presenting the correct
// TWILIO_API_KEY (as `?api_key=` or `X-API-Key` header) can obtain a token.
app.get("/token", requireApiKey, (req, res) => {
  const identity = (req.query.identity || "ruko-user")
    .toString()
    .trim()
    .replace(/[^a-zA-Z0-9_-]/g, "")
    .slice(0, 64) || "ruko-user";

  const accountSid = process.env.TWILIO_ACCOUNT_SID;
  const apiKeySid = process.env.TWILIO_API_KEY_SID;
  const apiKeySecret = process.env.TWILIO_API_KEY_SECRET;
  const twimlAppSid = process.env.TWILIO_TWIML_APP_SID;

  if (!accountSid || !apiKeySid || !apiKeySecret || !twimlAppSid) {
    return res
      .status(500)
      .json({ error: "Server misconfiguration: missing Twilio credentials" });
  }

  const AccessToken = twilio.jwt.AccessToken;
  const VoiceGrant = AccessToken.VoiceGrant;

  const voiceGrant = new VoiceGrant({
    outgoingApplicationSid: twimlAppSid,
    incomingAllow: true,
  });

  const token = new AccessToken(accountSid, apiKeySid, apiKeySecret, {
    identity,
    ttl: 3600,
  });
  token.addGrant(voiceGrant);

  res.json({ identity, token: token.toJwt() });
});

// 404 handler for unknown routes
app.use((req, res) => {
  res.status(404).json({ error: "Not found" });
});

// Generic error handler — prevents unhandled exceptions from crashing the process.
// Fields are passed as separate arguments (not interpolated into one string) so
// that user-controlled values (e.g. req.url) can't be misinterpreted as printf-style
// format specifiers by console.error/util.format.
app.use((err, req, res, next) => {
  console.error("[%s] %s %s", new Date().toISOString(), req.method, req.url, err);
  res.status(500).json({ error: "Internal Server Error" });
});

const PORT = process.env.PORT || 3000;

let server;
if (require.main === module) {
  server = app.listen(PORT, () => {
    console.log("Server running on port " + PORT);
  });

  // Graceful shutdown: finish in-flight requests before exiting.
  // A 10-second timeout ensures the process doesn't hang indefinitely.
  const SHUTDOWN_TIMEOUT_MS = 10_000;

  const shutdown = () => {
    const timer = setTimeout(() => {
      console.error("Shutdown timed out — forcing exit");
      process.exit(1);
    }, SHUTDOWN_TIMEOUT_MS);
    timer.unref();

    server.close((err) => {
      if (err) {
        console.error("Error during server close:", err);
        process.exit(1);
      }
      process.exit(0);
    });
  };

  process.on("SIGTERM", shutdown);
  process.on("SIGINT", shutdown);
}

module.exports = app;
