import { exportJWK, generateKeyPair, SignJWT } from "jose";
import { afterEach, describe, expect, it, vi } from "vitest";

import { readBearerToken, UnauthorizedRequest, verifyListener } from "../src/auth";
import type { RakyzuApiEnv } from "../src/types";

const USER_ID = "b1000000-0000-4000-8000-000000000001";
const PROJECT_URL = "https://auth-test.supabase.co";

afterEach(() => vi.unstubAllGlobals());

describe("Rakyzu listener authentication", () => {
  it("accepts exactly one bearer token", () => {
    expect(readBearerToken(new Request("https://api.example", {
      headers: { authorization: "Bearer listener-token" },
    }))).toBe("listener-token");

    for (const value of [null, "Basic value", "Bearer two tokens", "Bearer "]) {
      const headers = value === null ? undefined : { authorization: value };
      expect(() => readBearerToken(new Request("https://api.example", { headers })))
        .toThrow(UnauthorizedRequest);
    }
  });

  it("verifies Supabase ES256 issuer, audience, role, and subject", async () => {
    const { privateKey, publicKey } = await generateKeyPair("ES256");
    const publicJwk = await exportJWK(publicKey);
    vi.stubGlobal("fetch", vi.fn(async () => Response.json({
      keys: [{ ...publicJwk, alg: "ES256", kid: "rakyzu-test-key", use: "sig" }],
    })));

    const validToken = await sign(privateKey, "authenticated");
    await expect(verifyListener(validToken, environment())).resolves.toEqual({ userId: USER_ID });

    const wrongRoleToken = await sign(privateKey, "service_role");
    await expect(verifyListener(wrongRoleToken, environment())).rejects.toBeInstanceOf(
      UnauthorizedRequest,
    );
  });
});

async function sign(privateKey: CryptoKey, role: string): Promise<string> {
  return new SignJWT({ role })
    .setProtectedHeader({ alg: "ES256", kid: "rakyzu-test-key" })
    .setSubject(USER_ID)
    .setIssuer(`${PROJECT_URL}/auth/v1`)
    .setAudience("authenticated")
    .setIssuedAt()
    .setExpirationTime("5m")
    .sign(privateKey);
}

function environment(): RakyzuApiEnv {
  return {
    MEDIA: {} as R2Bucket,
    ALLOWED_ORIGINS: "https://rakyzu.my.id",
    SUPABASE_URL: PROJECT_URL,
    SUPABASE_PUBLISHABLE_KEY: "public-test-key",
    SUPABASE_SERVICE_ROLE_KEY: "service-test-key",
    PLAY_EVENT_WEBHOOK_SECRET: "play-event-test-secret-at-least-32-bytes",
    COMMERCE_WEBHOOK_SECRET: "commerce-test-secret-at-least-32-bytes",
  };
}
