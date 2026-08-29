import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from "jose";

import type { ListenerIdentity, RakyzuApiEnv } from "./types";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const jwksByProject = new Map<string, JWTVerifyGetKey>();

export class UnauthorizedRequest extends Error {}

export function readBearerToken(request: Request): string {
  const header = request.headers.get("authorization");
  const match = header?.match(/^Bearer ([^\s]+)$/i);
  if (!match?.[1]) throw new UnauthorizedRequest();
  return match[1];
}

export async function verifyListener(
  token: string,
  env: RakyzuApiEnv,
): Promise<ListenerIdentity> {
  const projectUrl = normalizeProjectUrl(env.SUPABASE_URL);
  let jwks = jwksByProject.get(projectUrl);
  if (!jwks) {
    jwks = createRemoteJWKSet(
      new URL(`${projectUrl}/auth/v1/.well-known/jwks.json`),
    );
    jwksByProject.set(projectUrl, jwks);
  }

  try {
    const result = await jwtVerify(token, jwks, {
      algorithms: ["ES256"],
      audience: "authenticated",
      issuer: `${projectUrl}/auth/v1`,
    });
    const userId = result.payload.sub;
    if (!userId || !UUID_PATTERN.test(userId) || result.payload.role !== "authenticated") {
      throw new UnauthorizedRequest();
    }
    return { userId };
  } catch (error) {
    if (error instanceof UnauthorizedRequest) throw error;
    throw new UnauthorizedRequest();
  }
}

function normalizeProjectUrl(value: string): string {
  const url = new URL(value);
  if (url.protocol !== "https:") throw new UnauthorizedRequest();
  return url.origin;
}
