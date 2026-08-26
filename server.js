const express = require("express");
const { jwt: { AccessToken } } = require("twilio");
const VoiceGrant = AccessToken.VoiceGrant;

const app = express();
app.use(express.urlencoded({ extended: false }));
app.use(express.json());

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

  if (!accountSid || !apiKeySid || !apiKeySecret) {
    return res
      .status(500)
      .json({ error: "Twilio credentials not configured. Set TWILIO_ACCOUNT_SID, TWILIO_API_KEY_SID, TWILIO_API_KEY_SECRET." });
  }

  const identity = req.query.identity || "ruko-user";

  const accessToken = new AccessToken(accountSid, apiKeySid, apiKeySecret, {
    identity,
  });

  const twimlAppSid =
    process.env.TWILIO_TWIML_APP_SID ||
    process.env.TWILIO_VOICE_TWIML_APP_SID;

  const voiceGrant = new VoiceGrant({
    outgoingApplicationSid: twimlAppSid,
    incomingAllow: true,
    pushCredentialSid: process.env.TWILIO_PUSH_CREDENTIAL_SID,
  });

  accessToken.addGrant(voiceGrant);

  res.json({ token: accessToken.toJwt(), identity });
});

// ── /voice ───────────────────────────────────────────────────────────────────
// TwiML webhook called by Twilio when an outgoing call is placed via the app.
// Configure this URL as the "Voice Request URL" in your TwiML App.
app.post("/voice", (req, res) => {
  const to = req.body.To || req.query.To;
  let twiml;

  if (to) {
    // Dial another Twilio Client identity or a phone number.
    const isClient = !to.startsWith("+");
    twiml = `<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Dial callerId="${process.env.TWILIO_CALLER_ID || ""}">
    ${isClient ? `<Client>${to}</Client>` : `<Number>${to}</Number>`}
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

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log("Server running on port " + PORT);
});
