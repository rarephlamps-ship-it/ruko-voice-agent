const express = require("express");

const app = express();
app.use(express.urlencoded({ extended: false }));

// Pre-built TwiML response — static content, so build once at startup
const VOICE_TWIML =
  '<Response><Say language="da-DK">Hej. Dette er Rukos AI agent. Systemet virker.</Say></Response>';

// Twilio Voice endpoint
app.post("/voice", (req, res) => {
  res.type("text/xml");
  res.send(VOICE_TWIML);
});

// Health check
app.get("/", (req, res) => {
  res.send("Ruko Voice Agent kører.");
});

// Generic error handler — prevents unhandled exceptions from crashing the process
app.use((err, req, res, next) => {
  console.error(`[${new Date().toISOString()}] ${req.method} ${req.url}`, err);
  res.status(500).send("Internal Server Error");
});

const PORT = process.env.PORT || 3000;
const server = app.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});

// Graceful shutdown: finish in-flight requests before exiting.
// A 10-second timeout ensures the process doesn't hang indefinitely.
const SHUTDOWN_TIMEOUT_MS = 10_000;

function shutdown() {
  const timer = setTimeout(() => {
    console.error("Shutdown timed out — forcing exit");
    process.exit(1);
  }, SHUTDOWN_TIMEOUT_MS);
  // Allow the timer to be garbage-collected if close() finishes first
  timer.unref();

  server.close((err) => {
    if (err) {
      console.error("Error during server close:", err);
      process.exit(1);
    }
    process.exit(0);
  });
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
