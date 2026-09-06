require("dotenv").config();
const express = require("express");
const { jwt: { AccessToken } } = require("twilio");
const VoiceGrant = AccessToken.VoiceGrant;

const app = express();
app.use(express.urlencoded({ extended: false }));
app.use(express.json());

// Escape user-supplied strings before embedding them in TwiML XML.
function escapeXml(unsafe) {
  return String(unsafe || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

// ── /token ──────────────────────────────────────────────────────────────────
// Issues a Twilio Voice access token for the Android app.
//
// Required env vars:
//   TWILIO_ACCOUNT_SID      — your Twilio account SID (AC...)
//   TWILIO_API_KEY_SID      — API Key SID (SK...)
//   TWILIO_API_KEY_SECRET   — API Key secret
//   TWILIO_TWIML_APP_SID    — TwiML App SID for outgoing calls (AP...)
//
// Optional env vars:
//   TWILIO_PUSH_CREDENTIAL_SID — FCM Push Credential SID for incoming calls
//   TWILIO_VOICE_TWIML_APP_SID — alias for TWILIO_TWIML_APP_SID
//
// Query params:
//   identity — caller identity (default: "ruko-user")
app.get("/token", (req, res) => {
  const accountSid = process.env.TWILIO_ACCOUNT_SID;
  const apiKeySid = process.env.TWILIO_API_KEY_SID;
  const apiKeySecret = process.env.TWILIO_API_KEY_SECRET;
  const twimlAppSid =
    process.env.TWILIO_TWIML_APP_SID ||
    process.env.TWILIO_VOICE_TWIML_APP_SID;

  if (!accountSid || !apiKeySid || !apiKeySecret || !twimlAppSid) {
    return res.status(500).json({
      error:
        "Twilio credentials not fully configured. Set TWILIO_ACCOUNT_SID, TWILIO_API_KEY_SID, TWILIO_API_KEY_SECRET, and TWILIO_TWIML_APP_SID.",
    });
  }

  const rawIdentity = req.query.identity ? String(req.query.identity).trim() : "ruko-user";
  const identity = rawIdentity.replace(/[^a-zA-Z0-9_\-.:@]/g, "").slice(0, 64) || "ruko-user";

  const accessToken = new AccessToken(accountSid, apiKeySid, apiKeySecret, {
    identity,
    ttl: 3600,
  });

  const voiceGrant = new VoiceGrant({
    outgoingApplicationSid: twimlAppSid,
    incomingAllow: true,
    pushCredentialSid: process.env.TWILIO_PUSH_CREDENTIAL_SID || undefined,
  });

  accessToken.addGrant(voiceGrant);

  res.json({ token: accessToken.toJwt(), identity });
});

// ── /voice ───────────────────────────────────────────────────────────────────
// TwiML webhook called by Twilio when an outgoing call is placed via the app.
// Configure this URL as the "Voice Request URL" in your TwiML App.
app.post("/voice", (req, res) => {
  const rawTo = req.body?.To || req.query?.To;
  let twiml;

  if (rawTo) {
    const toStr = String(rawTo).trim();
    const cleanTo = toStr.startsWith("client:") ? toStr.replace("client:", "") : toStr;
    const isClient = !cleanTo.startsWith("+");
    const escapedTo = escapeXml(cleanTo);
    const callerId = process.env.TWILIO_CALLER_ID ? process.env.TWILIO_CALLER_ID.trim() : "";
    const callerIdAttr = callerId ? ` callerId="${escapeXml(callerId)}"` : "";

    twiml = `<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Dial${callerIdAttr}>
    ${isClient ? `<Client>${escapedTo}</Client>` : `<Number>${escapedTo}</Number>`}
  </Dial>
</Response>`;
  } else {
    twiml = `<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Say language="da-DK">Hej. Dette er Rukos AI agent. Systemet virker.</Say>
</Response>`;
  }

  res.type("text/xml");
  res.send(twiml);
});

// ── Health check ─────────────────────────────────────────────────────────────
app.get("/", (req, res) => {
  res.send("Ruko Voice Agent kører.");
});

// Generic error handling middleware
app.use((err, req, res, next) => {
  console.error("[" + new Date().toISOString() + "]", req.method, req.url, err);
  res.status(500).json({ error: "Internal Server Error" });
});

const PORT = process.env.PORT || 3000;
let server;
if (require.main === module) {
  server = app.listen(PORT, () => {
    console.log("Server running on port " + PORT);
  });
}

module.exports = { app, server };
