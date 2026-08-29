import type { RakyzuApiEnv } from "./types";

interface TrackRow {
  id: string;
}

export class CatalogUnavailable extends Error {}

export async function canStreamTrack(
  trackId: string,
  token: string,
  env: RakyzuApiEnv,
): Promise<boolean> {
  const endpoint = new URL("/rest/v1/tracks", env.SUPABASE_URL);
  endpoint.searchParams.set("select", "id");
  endpoint.searchParams.set("id", `eq.${trackId}`);
  endpoint.searchParams.set("limit", "1");

  let response: Response;
  try {
    response = await fetch(endpoint, {
      headers: {
        accept: "application/json",
        apikey: env.SUPABASE_PUBLISHABLE_KEY,
        authorization: `Bearer ${token}`,
      },
    });
  } catch {
    throw new CatalogUnavailable();
  }

  if (!response.ok) throw new CatalogUnavailable();
  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    throw new CatalogUnavailable();
  }
  if (!Array.isArray(payload)) throw new CatalogUnavailable();
  return payload.some(isMatchingTrack(trackId));
}

function isMatchingTrack(trackId: string): (value: unknown) => value is TrackRow {
  return (value: unknown): value is TrackRow => {
    if (typeof value !== "object" || value === null) return false;
    return "id" in value && value.id === trackId;
  };
}
