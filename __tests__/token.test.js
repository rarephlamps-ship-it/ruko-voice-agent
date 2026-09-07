const request = require("supertest");

describe("/token endpoint authentication", () => {
  const ORIGINAL_ENV = process.env;

  beforeEach(() => {
    jest.resetModules();
    process.env = { ...ORIGINAL_ENV, TWILIO_API_KEY: "test-secret-key" };
  });

  afterAll(() => {
    process.env = ORIGINAL_ENV;
  });

  test("rejects requests without an API key", async () => {
    const app = require("../server");
    const res = await request(app).get("/token");
    expect(res.status).toBe(401);
    expect(res.body.error).toMatch(/unauthorized/i);
  });

  test("rejects requests with an incorrect API key header", async () => {
    const app = require("../server");
    const res = await request(app).get("/token").set("X-API-Key", "wrong-key");
    expect(res.status).toBe(401);
  });

  test("rejects requests that pass the API key as a query parameter", async () => {
    // The query-parameter fallback was removed: query strings can leak into
    // access logs, browser history, or proxy logs, so only the header is
    // accepted.
    const app = require("../server");
    const res = await request(app).get("/token?api_key=test-secret-key");
    expect(res.status).toBe(401);
  });

  test("returns 500 when TWILIO_API_KEY is not configured", async () => {
    jest.resetModules();
    process.env = { ...ORIGINAL_ENV };
    delete process.env.TWILIO_API_KEY;
    const app = require("../server");
    const res = await request(app).get("/token").set("X-API-Key", "anything");
    expect(res.status).toBe(500);
  });

  test("accepts a valid API key via X-API-Key header but reports missing Twilio credentials", async () => {
    const app = require("../server");
    const res = await request(app)
      .get("/token")
      .set("X-API-Key", "test-secret-key");
    // Auth passes; without Twilio credentials configured the server should
    // report a configuration error rather than a 401.
    expect(res.status).toBe(500);
  });

  test("issues a token when Twilio credentials are configured", async () => {
    process.env.TWILIO_ACCOUNT_SID = "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    process.env.TWILIO_API_KEY_SID = "SKxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    process.env.TWILIO_API_KEY_SECRET = "secret";
    process.env.TWILIO_TWIML_APP_SID = "APxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

    const app = require("../server");
    const res = await request(app)
      .get("/token?identity=alice")
      .set("X-API-Key", "test-secret-key");
    expect(res.status).toBe(200);
    expect(res.body.identity).toBe("alice");
    expect(typeof res.body.token).toBe("string");
  });

  test("sanitizes identity to strip unsafe characters", async () => {
    process.env.TWILIO_ACCOUNT_SID = "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    process.env.TWILIO_API_KEY_SID = "SKxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    process.env.TWILIO_API_KEY_SECRET = "secret";
    process.env.TWILIO_TWIML_APP_SID = "APxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

    const app = require("../server");
    const res = await request(app)
      .get("/token?identity=" + encodeURIComponent("<script>alert(1)</script>"))
      .set("X-API-Key", "test-secret-key");
    expect(res.status).toBe(200);
    expect(res.body.identity).toBe("scriptalert1script");
  });
});

describe("/voice endpoint", () => {
  test("returns TwiML XML, not JSON", async () => {
    const app = require("../server");
    const res = await request(app).post("/voice");
    expect(res.status).toBe(200);
    expect(res.headers["content-type"]).toMatch(/text\/xml/);
    expect(res.text).toMatch(/<Response>/);
    expect(res.text).toMatch(/<Say/);
  });
});

describe("unknown routes", () => {
  test("returns 404 for unknown routes", async () => {
    const app = require("../server");
    const res = await request(app).get("/nonexistent");
    expect(res.status).toBe(404);
  });
});
