import type { RakyzuApiEnv } from "./types";

interface TrackRow {
  id: string;
}

interface AlbumRow {
  id: string;
}

interface EditorialShelfRow {
  id: string;
}

export class CatalogUnavailable extends Error {}

export async function resolveTrackMediaKey(
  trackId: string,
  quality: "low" | "standard" | "high",
  token: string,
  env: RakyzuApiEnv,
): Promise<string | null> {
  const endpoint = new URL("/rest/v1/rpc/get_track_media_key", env.SUPABASE_URL);
  let response: Response;
  try {
    response = await fetch(endpoint, {
      method: "POST",
      headers: {
        accept: "application/json",
        apikey: env.SUPABASE_PUBLISHABLE_KEY,
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({ target_track_id: trackId, requested_quality: quality }),
      signal: AbortSignal.timeout(10_000),
    });
  } catch {
    throw new CatalogUnavailable();
  }
  if (!response.ok) throw new CatalogUnavailable();
  let value: unknown;
  try {
    value = await response.json();
  } catch {
    throw new CatalogUnavailable();
  }
  if (value === null) return null;
  const prefix = `media/tracks/${trackId}/`;
  const stem = quality === "low" ? "source-low" : quality === "high" ? "source-high" : "source";
  if (typeof value !== "string" || ![
    "mp3", "aac", "m4a", "webm", "wav", "flac",
  ].some((extension) => value === `${prefix}${stem}.${extension}`)) {
    throw new CatalogUnavailable();
  }
  return value;
}

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

export async function canAccessEditorialArtwork(
  shelfId: string,
  token: string,
  env: RakyzuApiEnv,
): Promise<boolean> {
  return canAccessCatalogResource(
    "editorial_shelves", shelfId, token, env, isMatchingShelf(shelfId),
  );
}

async function canAccessCatalogResource<T>(
  table: "albums" | "tracks" | "editorial_shelves",
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

function isMatchingShelf(shelfId: string): (value: unknown) => value is EditorialShelfRow {
  return (value: unknown): value is EditorialShelfRow => {
    if (typeof value !== "object" || value === null) return false;
    return "id" in value && value.id === shelfId;
  };
}
