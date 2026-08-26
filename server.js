const express = require("express");
const { jwt: { AccessToken } } = require("twilio");

const VoiceGrant = AccessToken.VoiceGrant;

const app = express();
app.use(express.urlencoded({ extended: false }));
app.use(express.json());

// --- /token endpoint ---
// Mints a Twilio Access Token with a VoiceGrant for the requesting client.
// Query param: identity (optional, defaults to "alice")
// Required env vars:
//   ACCOUNT_SID      – Twilio Account SID  (AC…)
//   API_KEY_SID      – Twilio API Key SID  (SK…)
//   API_KEY_SECRET   – Twilio API Key Secret
//   TWIML_APP_SID    – TwiML Application SID (AP…)
app.get("/token", (req, res) => {
  const accountSid = process.env.ACCOUNT_SID;
  const apiKeySid = process.env.API_KEY_SID;
  const apiKeySecret = process.env.API_KEY_SECRET;
  const twimlAppSid = process.env.TWIML_APP_SID;

  if (!accountSid || !apiKeySid || !apiKeySecret || !twimlAppSid) {
    return res.status(500).json({
      error: "Server misconfiguration: missing one or more required environment variables " +
             "(ACCOUNT_SID, API_KEY_SID, API_KEY_SECRET, TWIML_APP_SID)."
    });
  }

  const identity = (req.query.identity || "alice").toString().trim().replace(/[^a-zA-Z0-9_]/g, "").slice(0, 64) || "alice";

  const token = new AccessToken(accountSid, apiKeySid, apiKeySecret, {
    identity,
    ttl: 3600
  });

  const voiceGrant = new VoiceGrant({
    outgoingApplicationSid: twimlAppSid,
    incomingAllow: true
  });
  token.addGrant(voiceGrant);

  res.json({ identity, token: token.toJwt() });
});

// --- /voice endpoint ---
// Twilio webhook: called when an outgoing call is initiated via the TwiML App.
// Returns TwiML that connects the call.
app.post("/voice", (req, res) => {
  const to = req.body.To;

  let twiml;
  if (to) {
    // Dial a specific number or client identity
    const isClient = to.startsWith("client:");
    twiml = isClient
      ? `<Response><Dial><Client>${escapeXml(to.replace("client:", ""))}</Client></Dial></Response>`
      : `<Response><Dial><Number>${escapeXml(to)}</Number></Dial></Response>`;
  } else {
    twiml = `
    <Response>
      <Say language="da-DK">
        Hej. Dette er Rukos AI agent. Systemet virker.
      </Say>
    </Response>`;
  }

  res.status(200).type("text/xml").send(twiml);
});

// --- Health check ---
app.get("/", (req, res) => {
  res.send("Ruko Voice Agent kører.");
});

// Escape user-supplied strings before embedding them in TwiML XML.
function escapeXml(unsafe) {
  return String(unsafe)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log("Ruko Voice Agent running on port " + PORT);
});
