const { test, describe, before, after } = require("node:test");
const assert = require("node:assert");

// Set dummy environment variables for testing
process.env.TWILIO_ACCOUNT_SID = ["AC", "1234567890abcdef1234567890abcdef"].join("");
process.env.TWILIO_API_KEY_SID = ["SK", "1234567890abcdef1234567890abcdef"].join("");
process.env.TWILIO_API_KEY_SECRET = "secret1234567890secret1234567890";
process.env.TWILIO_TWIML_APP_SID = "AP1234567890abcdef1234567890abcdef";

let serverInstance;
let baseUrl;

describe("Ruko Voice Agent Server Tests", () => {
  before(() => {
    return new Promise((resolve) => {
      const { app } = require("../server.js");
      serverInstance = app.listen(0, () => {
        const port = serverInstance.address().port;
        baseUrl = `http://localhost:${port}`;
        resolve();
      });
    });
  });

  after(() => {
    return new Promise((resolve) => {
      if (serverInstance) {
        serverInstance.close(resolve);
      } else {
        resolve();
      }
    });
  });

  test("GET / returns 200 health check", async () => {
    const res = await fetch(`${baseUrl}/`);
    assert.strictEqual(res.status, 200);
    const text = await res.text();
    assert.ok(text.includes("Ruko Voice Agent kører."));
  });

  test("GET /token returns JWT token and identity", async () => {
    const res = await fetch(`${baseUrl}/token?identity=test-user`);
    assert.strictEqual(res.status, 200);
    const json = await res.json();
    assert.strictEqual(json.identity, "test-user");
    assert.ok(json.token && typeof json.token === "string");
  });

  test("POST /voice returns valid TwiML XML for client call", async () => {
    const res = await fetch(`${baseUrl}/voice`, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: "To=client:alice",
    });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.headers.get("content-type"), "text/xml; charset=utf-8");
    const text = await res.text();
    assert.ok(text.includes("<Client>alice</Client>"));
    assert.ok(!text.includes("callerId="));
  });

  test("POST /voice escapes special XML characters in To", async () => {
    const res = await fetch(`${baseUrl}/voice`, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: "To=" + encodeURIComponent("client:user<script>&foo"),
    });
    assert.strictEqual(res.status, 200);
    const text = await res.text();
    assert.ok(text.includes("<Client>user&lt;script&gt;&amp;foo</Client>"));
  });
});
