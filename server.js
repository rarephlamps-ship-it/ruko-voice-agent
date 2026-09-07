const express = require("express");
const bodyParser = require("body-parser");
const twilio = require("twilio");
require("dotenv").config();

const app = express();
app.use(bodyParser.urlencoded({ extended: false }));

// Application-level API key used to authenticate requests to this server's
// /token endpoint. This is NOT the same as Twilio's own Account SID/Auth
// Token or API Key/Secret used below to mint Access Tokens.
const APP_API_KEY = process.env.TWILIO_API_KEY || "dev-key-insecure";

// Authentication middleware: without this, anyone could call /token and
// obtain a valid Twilio Voice Access Token, enabling toll fraud and
// identity spoofing via the `identity` query parameter.
function requireApiKey(req, res, next) {
  const apiKey = req.query.api_key || req.get("X-API-Key");
  if (!apiKey || apiKey !== APP_API_KEY) {
    return res
      .status(401)
      .json({ error: "Unauthorized: missing or invalid API key" });
  }
  next();
}

// Twilio Voice endpoint
app.post("/voice", (req, res) => {
  const twiml = `
    <Response>
      <Say language="da-DK">
        Hej. Dette er Rukos AI agent. Systemet virker.
      </Say>
    </Response>
  `;

  res.type("text/xml");
  res.send(twiml);
});

// Health check
app.get("/", (req, res) => {
  res.send("Ruko Voice Agent kører.");
});

// Issues short-lived Twilio Voice Access Tokens for the mobile client.
// Protected by requireApiKey so that only requests presenting the correct
// TWILIO_API_KEY (as `?api_key=` or `X-API-Key` header) can obtain a token.
app.get("/token", requireApiKey, (req, res) => {
  const identity = req.query.identity || "ruko-user";

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
  });
  token.addGrant(voiceGrant);

  res.json({ identity, token: token.toJwt() });
});

const PORT = process.env.PORT || 3000;
if (require.main === module) {
  app.listen(PORT, () => {
    console.log("Server running on port " + PORT);
  });
}

module.exports = app;
