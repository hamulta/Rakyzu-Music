import { canAccessAlbumArtwork, canStreamTrack, CatalogUnavailable } from "./catalog";
import { readBearerToken, UnauthorizedRequest, verifyListener } from "./auth";
import {
  AdminRequestRejected,
  AdminUpstreamUnavailable,
  callAdminRpc,
  getStaffContext,
} from "./admin";
import { parseRange } from "./range";
import { errorResponse, jsonResponse, responseHeaders } from "./responses";
import type { AdminRpcName, RakyzuApiEnv, RequestDependencies, StaffContext } from "./types";
import { playlistAccess, playlistArtwork } from "./playlist-artwork";

const API_VERSION = "0.5.15";
const AUDIO_QUALITY_HEADER = "x-rakyzu-audio-quality";
const PLAYLIST_ARTWORK_ROUTE = /^\/v1\/playlists\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\/artwork\/?$/i;
const TRACK_ROUTE = /^\/v1\/tracks\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\/stream\/?$/i;
const ALBUM_ARTWORK_ROUTE = /^\/v1\/albums\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\/artwork\/?$/i;
const UUID = "([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})";
const ADMIN_ALBUM_PUBLISH_ROUTE = new RegExp(`^/v1/admin/albums/${UUID}/publish/?$`, "i");
const ADMIN_AUDIO_ROUTE = new RegExp(`^/v1/admin/tracks/${UUID}/audio/(low|standard|high)/?$`, "i");
const ADMIN_MODERATION_ACTION_ROUTE = new RegExp(`^/v1/admin/moderation/${UUID}/?$`, "i");
const ADMIN_STAFF_ASSIGNMENT_ROUTE = new RegExp(`^/v1/admin/staff/${UUID}/?$`, "i");
const MAX_AUDIO_BYTES = 50 * 1024 * 1024;
const ARTWORK_CACHE_CONTROL = "private, max-age=86400";
const dependencies: RequestDependencies = {
  verifyListener,
  canStreamTrack,
  canAccessAlbumArtwork,
  playlistAccess,
  staffContext: getStaffContext,
  adminRpc: callAdminRpc,
};

export function createWorker(
  requestDependencies: RequestDependencies = dependencies,
): ExportedHandler<RakyzuApiEnv> {
  return {
    async fetch(request, env): Promise<Response> {
      const requestId = crypto.randomUUID();
      let origin: string | null = null;
      try {
        const url = new URL(request.url);
        origin = allowedOrigin(request, env);
        if (request.headers.has("origin") && origin === null) {
          return errorResponse("origin_denied", "Origin is not allowed.", 403, requestId, null);
        }

        if (request.method === "OPTIONS") {
          return preflightResponse(requestId, origin);
        }
        if (url.pathname === "/v1/health" && request.method === "GET") {
          return jsonResponse(
            { service: "rakyzu-music-api", status: "ok", version: API_VERSION },
            200,
            requestId,
            origin,
          );
        }

        const isAdminRoute = url.pathname === "/v1/admin/context" ||
          url.pathname === "/v1/admin/staff" ||
          url.pathname === "/v1/admin/catalog/drafts" ||
          url.pathname === "/v1/admin/artists" ||
          url.pathname === "/v1/admin/albums" ||
          url.pathname === "/v1/admin/tracks" ||
          url.pathname === "/v1/admin/moderation" ||
          ADMIN_ALBUM_PUBLISH_ROUTE.test(url.pathname) ||
          ADMIN_AUDIO_ROUTE.test(url.pathname) ||
          ADMIN_MODERATION_ACTION_ROUTE.test(url.pathname) ||
          ADMIN_STAFF_ASSIGNMENT_ROUTE.test(url.pathname);
        const trackRoute = url.pathname.match(TRACK_ROUTE);
        const artworkRoute = url.pathname.match(ALBUM_ARTWORK_ROUTE);
        const playlistRoute = url.pathname.match(PLAYLIST_ARTWORK_ROUTE);
        if (!trackRoute?.[1] && !artworkRoute?.[1] && !playlistRoute?.[1] && !isAdminRoute) {
          return errorResponse("not_found", "Resource not found.", 404, requestId, origin);
        }
        if (!isAdminRoute && request.method !== "GET" && request.method !== "HEAD" &&
          !(playlistRoute && (request.method === "PUT" || request.method === "DELETE"))) {
          return errorResponse(
            "method_not_allowed",
            "Method not allowed.",
            405,
            requestId,
            origin,
            { allow: playlistRoute ? "GET, HEAD, PUT, DELETE, OPTIONS" : "GET, HEAD, OPTIONS" },
          );
        }

        let token: string;
        try {
          token = readBearerToken(request);
          await requestDependencies.verifyListener(token, env);
        } catch (error) {
          if (!(error instanceof UnauthorizedRequest)) throw error;
          return errorResponse(
            "unauthorized",
            "A valid Rakyzu Music session is required.",
            401,
            requestId,
            origin,
            { "www-authenticate": 'Bearer realm="Rakyzu Music"' },
          );
        }

        if (isAdminRoute) {
          return await adminRequest(
            request, env, url.pathname, token, requestDependencies, requestId, origin,
          );
        }

        if (playlistRoute?.[1]) {
          const id = playlistRoute[1].toLowerCase();
          try {
            const access = await requestDependencies.playlistAccess(id, token, env);
            if (!access) {
              return errorResponse("playlist_not_found", "Playlist unavailable.", 404, requestId, origin);
            }
            if (request.method !== "GET" && request.method !== "HEAD" && !access.canEdit) {
              return errorResponse("forbidden", "Only the playlist owner can change its cover.", 403, requestId, origin);
            }
            return await playlistArtwork(request, env, id, access.ownerId, requestId, origin);
          } catch {
            return errorResponse("playlist_unavailable", "Playlist authorization unavailable.", 503, requestId, origin);
          }
        }

        if (trackRoute?.[1]) {
          const trackId = trackRoute[1].toLowerCase();
          try {
            if (!(await requestDependencies.canStreamTrack(trackId, token, env))) {
              return errorResponse("track_not_found", "Track not found.", 404, requestId, origin);
            }
          } catch (error) {
            if (!(error instanceof CatalogUnavailable)) throw error;
            return errorResponse(
              "catalog_unavailable",
              "Catalog authorization is temporarily unavailable.",
              503,
              requestId,
              origin,
              { "retry-after": "30" },
            );
          }
          const quality = readAudioQuality(request.headers.get(AUDIO_QUALITY_HEADER));
          if (quality === null) {
            return errorResponse(
              "invalid_audio_quality",
              "Audio quality is invalid.",
              400,
              requestId,
              origin,
            );
          }
          return await streamTrack(request, env, trackId, quality, requestId, origin);
        }

        const albumId = artworkRoute?.[1]?.toLowerCase();
        if (!albumId) {
          return errorResponse("not_found", "Resource not found.", 404, requestId, origin);
        }
        try {
          if (!(await requestDependencies.canAccessAlbumArtwork(albumId, token, env))) {
            return errorResponse("artwork_not_found", "Artwork not found.", 404, requestId, origin);
          }
        } catch (error) {
          if (!(error instanceof CatalogUnavailable)) throw error;
          return errorResponse(
            "catalog_unavailable",
            "Catalog authorization is temporarily unavailable.",
            503,
            requestId,
            origin,
            { "retry-after": "30" },
          );
        }
        return await serveAlbumArtwork(request, env, albumId, requestId, origin);
      } catch {
        return errorResponse(
          "internal_error",
          "The Rakyzu Music API could not complete this request.",
          500,
          requestId,
          origin,
        );
      }
    },
  };
}

async function adminRequest(
  request: Request,
  env: RakyzuApiEnv,
  path: string,
  token: string,
  requestDependencies: RequestDependencies,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  try {
    const context = await requestDependencies.staffContext(token, env);
    if (path === "/v1/admin/context" && request.method === "GET") {
      return jsonResponse(context, 200, requestId, origin);
    }
    if (!context.isStaff || !context.permissions.includes("admin.access")) {
      return adminForbidden(requestId, origin);
    }

    if (path === "/v1/admin/staff" && request.method === "GET") {
      return await rpcResponse("admin_list_staff", {}, "staff.manage", context, token, env,
        requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/staff" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_assign_staff_by_email", {
        target_email: readString(body, "email", 254),
        target_role: readString(body, "role", 40),
        target_active: readBoolean(body, "active"),
      }, "staff.manage", context, token, env, requestDependencies, requestId, origin);
    }
    const staffMatch = path.match(ADMIN_STAFF_ASSIGNMENT_ROUTE);
    if (staffMatch?.[1] && request.method === "PUT") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_assign_staff", {
        target_user_id: staffMatch[1].toLowerCase(),
        target_role: readString(body, "role", 40),
        target_active: readBoolean(body, "active"),
      }, "staff.manage", context, token, env, requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/catalog/drafts" && request.method === "GET") {
      return await rpcResponse("admin_list_catalog_drafts", {}, "catalog.draft", context, token, env,
        requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/artists" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_create_artist", { artist_name: readString(body, "name", 120) },
        "catalog.draft", context, token, env, requestDependencies, requestId, origin, 201);
    }
    if (path === "/v1/admin/albums" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_create_album", {
        artist_id: readUuid(body, "artistId"),
        album_title: readString(body, "title", 160),
        album_release_date: readOptionalDate(body, "releaseDate"),
      }, "catalog.draft", context, token, env, requestDependencies, requestId, origin, 201);
    }
    if (path === "/v1/admin/tracks" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_create_track", {
        album_id: readUuid(body, "albumId"),
        track_title: readString(body, "title", 160),
        duration_ms: readInteger(body, "durationMs", 1_000, 86_400_000),
        disc_number: readInteger(body, "discNumber", 1, 99),
        track_number: readInteger(body, "trackNumber", 1, 999),
        explicit_content: readBoolean(body, "explicit"),
      }, "catalog.draft", context, token, env, requestDependencies, requestId, origin, 201);
    }
    const publishMatch = path.match(ADMIN_ALBUM_PUBLISH_ROUTE);
    if (publishMatch?.[1] && request.method === "POST") {
      return await rpcResponse("admin_publish_album", {
        target_album_id: publishMatch[1].toLowerCase(),
      }, "catalog.publish", context, token, env, requestDependencies, requestId, origin);
    }
    const audioMatch = path.match(ADMIN_AUDIO_ROUTE);
    if (audioMatch?.[1] && audioMatch[2] && request.method === "PUT") {
      return await uploadTrackAudio(
        request, env, audioMatch[1].toLowerCase(), audioMatch[2].toLowerCase(), context,
        token, requestDependencies, requestId, origin,
      );
    }
    if (path === "/v1/admin/moderation" && request.method === "GET") {
      return await rpcResponse("admin_list_moderation_cases", { page_size: 50 }, "moderation.view",
        context, token, env, requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/moderation" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_create_moderation_case", {
        target_subject_type: readString(body, "subjectType", 20),
        target_subject_id: readUuid(body, "subjectId"),
        case_reason: readString(body, "reason", 500),
        case_priority: readInteger(body, "priority", 1, 100),
      }, "moderation.triage", context, token, env, requestDependencies, requestId, origin, 201);
    }
    const caseMatch = path.match(ADMIN_MODERATION_ACTION_ROUTE);
    if (caseMatch?.[1] && request.method === "PATCH") {
      const body = await readJsonObject(request);
      const action = readString(body, "action", 20);
      const permission = action === "approve" || action === "dismiss"
        ? "moderation.decide" : "moderation.triage";
      return await rpcResponse("admin_moderate_case", {
        target_case_id: caseMatch[1].toLowerCase(),
        moderation_action: action,
        action_notes: readOptionalString(body, "notes", 500),
      }, permission, context, token, env, requestDependencies, requestId, origin);
    }
    return errorResponse("method_not_allowed", "Method not allowed.", 405, requestId, origin);
  } catch (error) {
    if (error instanceof AdminRequestRejected) {
      return errorResponse(
        error.status === 403 ? "forbidden" : "invalid_admin_request",
        error.status === 403 ? "Your Rakyzu Music role cannot perform this action." :
          "The admin request is invalid.",
        error.status, requestId, origin,
      );
    }
    if (error instanceof AdminUpstreamUnavailable) {
      return errorResponse("admin_unavailable", "Admin services are temporarily unavailable.",
        503, requestId, origin, { "retry-after": "30" });
    }
    return errorResponse("invalid_admin_request", "The admin request is invalid.",
      422, requestId, origin);
  }
}

async function rpcResponse(
  name: AdminRpcName,
  payload: Record<string, unknown>,
  permission: string,
  context: StaffContext,
  token: string,
  env: RakyzuApiEnv,
  requestDependencies: RequestDependencies,
  requestId: string,
  origin: string | null,
  status = 200,
): Promise<Response> {
  if (!context.permissions.includes(permission)) return adminForbidden(requestId, origin);
  const value = await requestDependencies.adminRpc(name, payload, token, env);
  return jsonResponse(value, status, requestId, origin);
}

async function uploadTrackAudio(
  request: Request,
  env: RakyzuApiEnv,
  trackId: string,
  quality: string,
  context: StaffContext,
  token: string,
  requestDependencies: RequestDependencies,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  if (!context.permissions.includes("catalog.upload_audio")) {
    return adminForbidden(requestId, origin);
  }
  if (request.headers.get("content-type")?.split(";", 1)[0]?.trim() !== "audio/mpeg") {
    return errorResponse("invalid_audio", "Upload an MP3 audio file.", 415, requestId, origin);
  }
  const claimedLength = Number(request.headers.get("content-length"));
  if (!Number.isInteger(claimedLength) || claimedLength < 4 || claimedLength > MAX_AUDIO_BYTES) {
    return errorResponse("invalid_audio_size", "Audio must be at most 50 MiB.", 413, requestId, origin);
  }
  if (!request.body) {
    return errorResponse("invalid_audio", "The file is not a valid MP3 upload.", 422,
      requestId, origin);
  }
  const bytes = new Uint8Array(claimedLength);
  const reader = request.body.getReader();
  let offset = 0;
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      if (offset + chunk.value.length > claimedLength) {
        await reader.cancel();
        return errorResponse("invalid_audio_size", "Audio length does not match.", 413,
          requestId, origin);
      }
      bytes.set(chunk.value, offset);
      offset += chunk.value.length;
    }
  } finally {
    reader.releaseLock();
  }
  if (offset !== claimedLength || !looksLikeMp3(bytes)) {
    return errorResponse("invalid_audio", "The file is not a valid MP3 upload.", 422,
      requestId, origin);
  }
  const objectName = quality === "low" ? "source-low.mp3" :
    quality === "high" ? "source-high.mp3" : "source.mp3";
  const objectKey = `media/tracks/${trackId}/${objectName}`;
  const uploaded = await env.MEDIA.put(objectKey, bytes, {
    httpMetadata: { contentType: "audio/mpeg", cacheControl: "private, no-store" },
  });
  try {
    const result = await requestDependencies.adminRpc("admin_record_track_media", {
      target_track_id: trackId,
      media_quality: quality,
      media_object_key: objectKey,
      media_size_bytes: bytes.length,
      media_etag: uploaded.etag,
    }, token, env);
    return jsonResponse(result, 201, requestId, origin);
  } catch (error) {
    await env.MEDIA.delete(objectKey);
    throw error;
  }
}

function looksLikeMp3(bytes: Uint8Array): boolean {
  return bytes.length >= 4 && (
    (bytes[0] === 0x49 && bytes[1] === 0x44 && bytes[2] === 0x33) ||
    (bytes[0] === 0xff && (bytes[1]! & 0xe0) === 0xe0)
  );
}

async function readJsonObject(request: Request): Promise<Record<string, unknown>> {
  const length = Number(request.headers.get("content-length"));
  if (Number.isFinite(length) && length > 16_384) throw new AdminRequestRejected(422);
  if (request.headers.get("content-type")?.split(";", 1)[0]?.trim() !== "application/json") {
    throw new AdminRequestRejected(422);
  }
  const raw = await request.text();
  if (new TextEncoder().encode(raw).length > 16_384) throw new AdminRequestRejected(422);
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    throw new AdminRequestRejected(422);
  }
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new AdminRequestRejected(422);
  }
  return value as Record<string, unknown>;
}

function readString(body: Record<string, unknown>, key: string, max: number): string {
  const value = body[key];
  if (typeof value !== "string" || value.trim().length < 1 || value.trim().length > max) {
    throw new AdminRequestRejected(422);
  }
  return value.trim();
}

function readOptionalString(body: Record<string, unknown>, key: string, max: number): string {
  const value = body[key];
  if (value === undefined || value === null) return "";
  if (typeof value !== "string" || value.trim().length > max) throw new AdminRequestRejected(422);
  return value.trim();
}

function readUuid(body: Record<string, unknown>, key: string): string {
  const value = readString(body, key, 36).toLowerCase();
  if (!new RegExp(`^${UUID}$`, "i").test(value)) throw new AdminRequestRejected(422);
  return value;
}

function readBoolean(body: Record<string, unknown>, key: string): boolean {
  const value = body[key];
  if (typeof value !== "boolean") throw new AdminRequestRejected(422);
  return value;
}

function readInteger(
  body: Record<string, unknown>, key: string, minimum: number, maximum: number,
): number {
  const value = body[key];
  if (!Number.isInteger(value) || (value as number) < minimum || (value as number) > maximum) {
    throw new AdminRequestRejected(422);
  }
  return value as number;
}

function readOptionalDate(body: Record<string, unknown>, key: string): string | null {
  const value = body[key];
  if (value === undefined || value === null || value === "") return null;
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new AdminRequestRejected(422);
  }
  return value;
}

function adminForbidden(requestId: string, origin: string | null): Response {
  return errorResponse("forbidden", "Your Rakyzu Music role cannot perform this action.",
    403, requestId, origin);
}

async function serveAlbumArtwork(
  request: Request,
  env: RakyzuApiEnv,
  albumId: string,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  const objectKey = `media/albums/${albumId}/artwork.webp`;
  const metadata = await env.MEDIA.head(objectKey);
  const contentType = metadata?.httpMetadata?.contentType;
  if (metadata === null || !isSupportedArtworkContentType(contentType)) {
    return errorResponse("artwork_not_found", "Artwork is unavailable.", 404, requestId, origin);
  }

  const headers = responseHeaders(requestId, origin);
  metadata.writeHttpMetadata(headers);
  headers.set("cache-control", ARTWORK_CACHE_CONTROL);
  headers.set("etag", metadata.httpEtag);
  headers.set("access-control-expose-headers", "Content-Length, ETag, X-Request-ID");

  if (request.headers.get("if-none-match") === metadata.httpEtag) {
    return new Response(null, { status: 304, headers });
  }
  headers.set("content-length", metadata.size.toString());
  if (request.method === "HEAD") {
    return new Response(null, { status: 200, headers });
  }

  const object = await env.MEDIA.get(objectKey);
  if (object === null) {
    return errorResponse("artwork_not_found", "Artwork is unavailable.", 404, requestId, origin);
  }
  return new Response(object.body, { status: 200, headers });
}

function isSupportedArtworkContentType(contentType: string | undefined): boolean {
  return contentType === "image/avif" ||
    contentType === "image/jpeg" ||
    contentType === "image/png" ||
    contentType === "image/webp";
}

async function streamTrack(
  request: Request,
  env: RakyzuApiEnv,
  trackId: string,
  quality: AudioQuality,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  const objectKey = `media/tracks/${trackId}/${audioObjectName(quality)}`;
  const metadata = await env.MEDIA.head(objectKey);
  if (metadata === null) {
    return errorResponse("media_not_found", "Track media is unavailable.", 404, requestId, origin);
  }

  const parsedRange = parseRange(request.headers.get("range"), metadata.size);
  if (parsedRange.kind === "unsatisfiable") {
    return errorResponse(
      "range_not_satisfiable",
      "Requested byte range is not available.",
      416,
      requestId,
      origin,
      { "content-range": `bytes */${metadata.size}` },
    );
  }

  const headers = responseHeaders(requestId, origin);
  metadata.writeHttpMetadata(headers);
  headers.set("accept-ranges", "bytes");
  headers.set("cache-control", "private, no-store");
  headers.set("etag", metadata.httpEtag);
  headers.set("x-rakyzu-audio-quality", quality);
  headers.set(
    "access-control-expose-headers",
    "Accept-Ranges, Content-Length, Content-Range, ETag, X-Rakyzu-Audio-Quality, X-Request-ID",
  );

  if (request.method === "HEAD") {
    headers.set("content-length", metadata.size.toString());
    return new Response(null, { status: 200, headers });
  }

  if (parsedRange.kind === "full") {
    const object = await env.MEDIA.get(objectKey);
    if (object === null) {
      return errorResponse("media_not_found", "Track media is unavailable.", 404, requestId, origin);
    }
    headers.set("content-length", metadata.size.toString());
    return new Response(object.body, { status: 200, headers });
  }

  const { offset, length } = parsedRange.range;
  const object = await env.MEDIA.get(objectKey, { range: { offset, length } });
  if (object === null) {
    return errorResponse("media_not_found", "Track media is unavailable.", 404, requestId, origin);
  }
  headers.set("content-length", length.toString());
  headers.set("content-range", `bytes ${offset}-${offset + length - 1}/${metadata.size}`);
  return new Response(object.body, { status: 206, headers });
}

type AudioQuality = "low" | "standard" | "high";

function readAudioQuality(value: string | null): AudioQuality | null {
  if (value === null || value === "standard") return "standard";
  if (value === "low" || value === "high") return value;
  return null;
}

function audioObjectName(quality: AudioQuality): string {
  if (quality === "low") return "source-low.mp3";
  if (quality === "high") return "source-high.mp3";
  return "source.mp3";
}

function allowedOrigin(request: Request, env: RakyzuApiEnv): string | null {
  const origin = request.headers.get("origin");
  if (origin === null) return null;
  const allowed = env.ALLOWED_ORIGINS.split(",").map((value) => value.trim());
  return allowed.includes(origin) ? origin : null;
}

function preflightResponse(requestId: string, origin: string | null): Response {
  const headers = responseHeaders(requestId, origin);
  headers.set(
    "access-control-allow-headers",
    "Authorization, Content-Type, Range, X-Rakyzu-Audio-Quality",
  );
  headers.set("access-control-allow-methods", "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS");
  headers.set("access-control-max-age", "86400");
  headers.set("cache-control", "no-store");
  return new Response(null, { status: 204, headers });
}
