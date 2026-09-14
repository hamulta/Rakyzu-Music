import { canAccessAlbumArtwork, canStreamTrack, CatalogUnavailable } from "./catalog";
import { readBearerToken, UnauthorizedRequest, verifyListener } from "./auth";
import { parseRange } from "./range";
import { errorResponse, jsonResponse, responseHeaders } from "./responses";
import type { RakyzuApiEnv, RequestDependencies } from "./types";
import { playlistAccess, playlistArtwork } from "./playlist-artwork";

const API_VERSION = "0.5.5";
const PLAYLIST_ARTWORK_ROUTE = /^\/v1\/playlists\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\/artwork\/?$/i;
const TRACK_ROUTE = /^\/v1\/tracks\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\/stream\/?$/i;
const ALBUM_ARTWORK_ROUTE = /^\/v1\/albums\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\/artwork\/?$/i;
const ARTWORK_CACHE_CONTROL = "private, max-age=86400";
const dependencies: RequestDependencies = {
  verifyListener,
  canStreamTrack,
  canAccessAlbumArtwork,
  playlistAccess,
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

        const trackRoute = url.pathname.match(TRACK_ROUTE);
        const artworkRoute = url.pathname.match(ALBUM_ARTWORK_ROUTE);
        const playlistRoute = url.pathname.match(PLAYLIST_ARTWORK_ROUTE);
        if (!trackRoute?.[1] && !artworkRoute?.[1] && !playlistRoute?.[1]) {
          return errorResponse("not_found", "Resource not found.", 404, requestId, origin);
        }
        if (request.method !== "GET" && request.method !== "HEAD" &&
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
          return await streamTrack(request, env, trackId, requestId, origin);
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
  requestId: string,
  origin: string | null,
): Promise<Response> {
  const objectKey = `media/tracks/${trackId}/source.mp3`;
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
  headers.set(
    "access-control-expose-headers",
    "Accept-Ranges, Content-Length, Content-Range, ETag, X-Request-ID",
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

function allowedOrigin(request: Request, env: RakyzuApiEnv): string | null {
  const origin = request.headers.get("origin");
  if (origin === null) return null;
  const allowed = env.ALLOWED_ORIGINS.split(",").map((value) => value.trim());
  return allowed.includes(origin) ? origin : null;
}

function preflightResponse(requestId: string, origin: string | null): Response {
  const headers = responseHeaders(requestId, origin);
  headers.set("access-control-allow-headers", "Authorization, Content-Type, Range");
  headers.set("access-control-allow-methods", "GET, HEAD, PUT, DELETE, OPTIONS");
  headers.set("access-control-max-age", "86400");
  headers.set("cache-control", "no-store");
  return new Response(null, { status: 204, headers });
}
