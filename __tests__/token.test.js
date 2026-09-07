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

  test("rejects requests with an incorrect API key", async () => {
    const app = require("../server");
    const res = await request(app).get("/token?api_key=wrong-key");
    expect(res.status).toBe(401);
  });

  test("accepts a valid API key via query param but reports missing Twilio credentials", async () => {
    const app = require("../server");
    const res = await request(app).get("/token?api_key=test-secret-key");
    // Auth passes; without Twilio credentials configured the server should
    // report a configuration error rather than a 401.
    expect(res.status).toBe(500);
  });

  test("accepts a valid API key via X-API-Key header", async () => {
    const app = require("../server");
    const res = await request(app)
      .get("/token")
      .set("X-API-Key", "test-secret-key");
    expect(res.status).toBe(500);
  });

  test("issues a token when Twilio credentials are configured", async () => {
    process.env.TWILIO_ACCOUNT_SID = "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    process.env.TWILIO_API_KEY_SID = "SKxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    process.env.TWILIO_API_KEY_SECRET = "secret";
    process.env.TWILIO_TWIML_APP_SID = "APxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

    const app = require("../server");
    const res = await request(app).get(
      "/token?api_key=test-secret-key&identity=alice"
    );
    expect(res.status).toBe(200);
    expect(res.body.identity).toBe("alice");
    expect(typeof res.body.token).toBe("string");
  });
});
