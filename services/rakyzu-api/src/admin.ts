import type { AdminRpcName, RakyzuApiEnv, StaffContext } from "./types";

const ROLE_NAMES = new Set([
  "officer",
  "supervisor",
  "manager",
  "c_level_executive",
  "ceo",
]);
const PERMISSION_PATTERN = /^[a-z][a-z_]*\.[a-z][a-z_]*$/;

export class AdminRequestRejected extends Error {
  constructor(readonly status: number) {
    super("Admin request rejected");
  }
}

export class AdminUpstreamUnavailable extends Error {}

export async function getStaffContext(
  token: string,
  env: RakyzuApiEnv,
): Promise<StaffContext> {
  const value = await callSupabaseRpc("get_my_staff_context", {}, token, env);
  if (typeof value !== "object" || value === null || !("isStaff" in value) ||
    typeof value.isStaff !== "boolean") throw new AdminUpstreamUnavailable();
  if (!value.isStaff) {
    return { isStaff: false, role: null, displayRole: null, fullAccess: false, permissions: [] };
  }
  if (!("role" in value) || typeof value.role !== "string" || !ROLE_NAMES.has(value.role) ||
    !("displayRole" in value) || typeof value.displayRole !== "string" ||
    !("fullAccess" in value) || typeof value.fullAccess !== "boolean" ||
    !("permissions" in value) || !Array.isArray(value.permissions) ||
    !value.permissions.every((permission) =>
      typeof permission === "string" && PERMISSION_PATTERN.test(permission))) {
    throw new AdminUpstreamUnavailable();
  }
  return {
    isStaff: true,
    role: value.role,
    displayRole: value.displayRole,
    fullAccess: value.fullAccess,
    permissions: value.permissions,
  };
}

export async function callAdminRpc(
  name: AdminRpcName,
  payload: Record<string, unknown>,
  token: string,
  env: RakyzuApiEnv,
): Promise<unknown> {
  return callSupabaseRpc(name, payload, token, env);
}

export async function callServiceRpc(
  name: "service_record_play_event" | "service_ingest_commerce_event",
  payload: Record<string, unknown>,
  env: RakyzuApiEnv,
): Promise<unknown> {
  return callSupabaseRpc(name, payload, env.SUPABASE_SERVICE_ROLE_KEY, env,
    env.SUPABASE_SERVICE_ROLE_KEY);
}

async function callSupabaseRpc(
  name: string,
  payload: Record<string, unknown>,
  token: string,
  env: RakyzuApiEnv,
  apiKey: string = env.SUPABASE_PUBLISHABLE_KEY,
): Promise<unknown> {
  const url = new URL(`/rest/v1/rpc/${name}`, env.SUPABASE_URL);
  let response: Response;
  try {
    response = await fetch(url, {
      method: "POST",
      headers: {
        accept: "application/json",
        apikey: apiKey,
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(10_000),
    });
  } catch {
    throw new AdminUpstreamUnavailable();
  }
  if (!response.ok) {
    if ([400, 401, 403, 404, 409, 422].includes(response.status)) {
      throw new AdminRequestRejected(response.status === 401 ? 401 :
        response.status === 403 ? 403 : 422);
    }
    throw new AdminUpstreamUnavailable();
  }
  try {
    return await response.json();
  } catch {
    throw new AdminUpstreamUnavailable();
  }
}
