import type { RakyzuApiEnv } from "./types";

interface TrackRow {
  id: string;
}

interface AlbumRow {
  id: string;
}

export class CatalogUnavailable extends Error {}

export async function canStreamTrack(
  trackId: string,
  token: string,
  env: RakyzuApiEnv,
): Promise<boolean> {
  return canAccessCatalogResource("tracks", trackId, token, env, isMatchingTrack(trackId));
}

export async function canAccessAlbumArtwork(
  albumId: string,
  token: string,
  env: RakyzuApiEnv,
): Promise<boolean> {
  return canAccessCatalogResource("albums", albumId, token, env, isMatchingAlbum(albumId));
}

async function canAccessCatalogResource<T>(
  table: "albums" | "tracks",
  resourceId: string,
  token: string,
  env: RakyzuApiEnv,
  isMatchingResource: (value: unknown) => value is T,
): Promise<boolean> {
  const endpoint = new URL(`/rest/v1/${table}`, env.SUPABASE_URL);
  endpoint.searchParams.set("select", "id");
  endpoint.searchParams.set("id", `eq.${resourceId}`);
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
  return payload.some(isMatchingResource);
}

function isMatchingTrack(trackId: string): (value: unknown) => value is TrackRow {
  return (value: unknown): value is TrackRow => {
    if (typeof value !== "object" || value === null) return false;
    return "id" in value && value.id === trackId;
  };
}

function isMatchingAlbum(albumId: string): (value: unknown) => value is AlbumRow {
  return (value: unknown): value is AlbumRow => {
    if (typeof value !== "object" || value === null) return false;
    return "id" in value && value.id === albumId;
  };
}
