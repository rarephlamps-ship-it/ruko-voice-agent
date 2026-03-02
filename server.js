const express = require("express");
const bodyParser = require("body-parser");

const app = express();
app.use(bodyParser.urlencoded({ extended: false }));

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

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log("Server running on port " + PORT);
});
