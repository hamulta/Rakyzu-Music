import {
  canAccessAlbumArtwork,
  canAccessEditorialArtwork,
  canStreamTrack,
  CatalogUnavailable,
  resolveTrackMediaKey,
} from "./catalog";
import { readBearerToken, UnauthorizedRequest, verifyListener } from "./auth";
import {
  AdminRequestRejected,
  AdminUpstreamUnavailable,
  callAdminRpc,
  callServiceRpc,
  getStaffContext,
} from "./admin";
import { parseRange } from "./range";
import { errorResponse, jsonResponse, responseHeaders } from "./responses";
import type { AdminRpcName, RakyzuApiEnv, RequestDependencies, StaffContext } from "./types";
import { playlistAccess, playlistArtwork } from "./playlist-artwork";
import { profileAvatar } from "./profile-avatar";
import { legalPageResponse, resolveLegalPage } from "./legal";

const API_VERSION = "0.8.8.2";
const AUDIO_QUALITY_HEADER = "x-rakyzu-audio-quality";
const UUID = "([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})";
const PLAYLIST_ARTWORK_ROUTE = /^\/v1\/playlists\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\/artwork\/?$/i;
const TRACK_ROUTE = /^\/v1\/tracks\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\/stream\/?$/i;
const TRACK_DOWNLOAD_ROUTE = /^\/v1\/tracks\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\/download\/?$/i;
const TRACK_CONTEXT_ROUTE = new RegExp(`^/v1/tracks/${UUID}/context/?$`, "i");
const TRACK_SHARE_ROUTE = new RegExp(`^/v1/tracks/${UUID}/share/?$`, "i");
const PROFILE_AVATAR_ROUTE = /^\/v1\/profiles\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\/avatar\/?$/i;
const ARTIST_BIOGRAPHY_ROUTE = /^\/v1\/artists\/me\/biography\/?$/i;
const ARTIST_PROFILE_ARTWORK_ROUTE = /^\/v1\/artists\/me\/artwork\/?$/i;
const RECOMMENDATION_ARTWORK_ROUTE = /^\/v1\/recommendations\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\/artwork\/?$/i;
const ALBUM_ARTWORK_ROUTE = /^\/v1\/albums\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\/artwork\/?$/i;
const ADMIN_ALBUM_PUBLISH_ROUTE = new RegExp(`^/v1/admin/albums/${UUID}/publish/?$`, "i");
const ADMIN_ARTIST_ROUTE = new RegExp(`^/v1/admin/artists/${UUID}/?$`, "i");
const ADMIN_ALBUM_ROUTE = new RegExp(`^/v1/admin/albums/${UUID}/?$`, "i");
const ADMIN_RECOMMENDATION_ROUTE = new RegExp(`^/v1/admin/recommendations/${UUID}/?$`, "i");
const ADMIN_RECOMMENDATION_ARTWORK_ROUTE = new RegExp(
  `^/v1/admin/recommendations/${UUID}/artwork/?$`, "i",
);
const ADMIN_RECOMMENDATION_TRACKS_ROUTE = new RegExp(
  `^/v1/admin/recommendations/${UUID}/tracks/?$`, "i",
);
const ADMIN_ALBUM_SCHEDULE_ROUTE = new RegExp(`^/v1/admin/albums/${UUID}/schedule/?$`, "i");
const ADMIN_ARTWORK_UPLOAD_ROUTE = new RegExp(`^/v1/admin/albums/${UUID}/artwork/?$`, "i");
const ADMIN_AUDIO_ROUTE = new RegExp(`^/v1/admin/tracks/${UUID}/audio/(low|standard|high)/?$`, "i");
const ADMIN_LYRICS_ROUTE = new RegExp(`^/v1/admin/tracks/${UUID}/lyrics/?$`, "i");
const ARTIST_ARTWORK_ROUTE = new RegExp(`^/v1/artists/${UUID}/artwork/?$`, "i");
const ARTIST_ALBUM_ARTWORK_ROUTE = new RegExp(`^/v1/artist/albums/${UUID}/artwork/?$`, "i");
const ARTIST_AUDIO_ROUTE = new RegExp(`^/v1/artist/tracks/${UUID}/audio/(low|standard|high)/?$`, "i");
const ARTIST_LYRICS_ROUTE = new RegExp(`^/v1/artist/tracks/${UUID}/lyrics/?$`, "i");
const ARTIST_ANALYTICS_ROUTE = new RegExp(`^/v1/artists/${UUID}/analytics/?$`, "i");
const ADMIN_MODERATION_ACTION_ROUTE = new RegExp(`^/v1/admin/moderation/${UUID}/?$`, "i");
const ADMIN_STAFF_ASSIGNMENT_ROUTE = new RegExp(`^/v1/admin/staff/${UUID}/?$`, "i");
const ADMIN_REVIEW_ACTION_ROUTE = new RegExp(`^/v1/admin/reviews/${UUID}/?$`, "i");
const ADMIN_ACCOUNT_ENFORCEMENT_ROUTE = new RegExp(`^/v1/admin/accounts/${UUID}/enforcement/?$`, "i");
const ADMIN_APPEAL_ROUTE = new RegExp(`^/v1/admin/appeals/${UUID}/?$`, "i");
const ADMIN_DELETION_APPROVAL_ROUTE = new RegExp(`^/v1/admin/deletions/${UUID}/approval/?$`, "i");
const ADMIN_SECURITY_ALERT_ROUTE = new RegExp(`^/v1/admin/security/alerts/${UUID}/?$`, "i");
const MAX_AUDIO_BYTES = 50 * 1024 * 1024;
const MAX_ARTWORK_BYTES = 5 * 1024 * 1024;
const MAX_LYRICS_JSON_BYTES = 1024 * 1024;
const ARTWORK_CACHE_CONTROL = "private, max-age=86400";
const dependencies: RequestDependencies = {
  verifyListener,
  canStreamTrack,
  resolveTrackMediaKey,
  canAccessAlbumArtwork,
  canAccessEditorialArtwork,
  playlistAccess,
  staffContext: getStaffContext,
  adminRpc: callAdminRpc,
  serviceRpc: callServiceRpc,
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
        const legalPage = resolveLegalPage(url.pathname);
        if (legalPage) {
          return legalPageResponse(legalPage, request.method, requestId);
        }
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
        if (url.pathname === "/v1/events/play" || url.pathname === "/v1/webhooks/commerce") {
          if (request.method !== "POST") {
            return errorResponse("method_not_allowed", "Method not allowed.", 405,
              requestId, origin, { allow: "POST, OPTIONS" });
          }
          return await signedIngestionRequest(request, env, url.pathname,
            requestDependencies, requestId, origin);
        }

        const isAdminRoute = url.pathname === "/v1/admin/context" ||
          url.pathname === "/v1/admin/staff" ||
          url.pathname === "/v1/admin/catalog/drafts" ||
          url.pathname === "/v1/admin/governance" ||
          url.pathname === "/v1/admin/enforcement" ||
          url.pathname === "/v1/admin/catalog/teams" ||
          url.pathname === "/v1/admin/catalog/labels" ||
          url.pathname === "/v1/admin/catalog/labels/artists" ||
          url.pathname === "/v1/admin/reviews" ||
          url.pathname === "/v1/admin/audit/export" ||
          url.pathname === "/v1/admin/audit/retention" ||
          url.pathname === "/v1/admin/artists" ||
          url.pathname === "/v1/admin/recommendations" ||
          url.pathname === "/v1/admin/albums" ||
          url.pathname === "/v1/admin/tracks" ||
          url.pathname === "/v1/admin/moderation" ||
          url.pathname === "/v1/admin/commerce" ||
          url.pathname === "/v1/admin/accounts" ||
          url.pathname === "/v1/admin/security" ||
          ADMIN_ALBUM_PUBLISH_ROUTE.test(url.pathname) ||
          ADMIN_ARTIST_ROUTE.test(url.pathname) ||
          ADMIN_ALBUM_ROUTE.test(url.pathname) ||
          ADMIN_RECOMMENDATION_ROUTE.test(url.pathname) ||
          ADMIN_RECOMMENDATION_ARTWORK_ROUTE.test(url.pathname) ||
          ADMIN_RECOMMENDATION_TRACKS_ROUTE.test(url.pathname) ||
          ADMIN_ALBUM_SCHEDULE_ROUTE.test(url.pathname) ||
          ADMIN_ARTWORK_UPLOAD_ROUTE.test(url.pathname) ||
          ADMIN_AUDIO_ROUTE.test(url.pathname) ||
          ADMIN_LYRICS_ROUTE.test(url.pathname) ||
          ADMIN_MODERATION_ACTION_ROUTE.test(url.pathname) ||
          ADMIN_STAFF_ASSIGNMENT_ROUTE.test(url.pathname) ||
          ADMIN_REVIEW_ACTION_ROUTE.test(url.pathname) ||
          ADMIN_ACCOUNT_ENFORCEMENT_ROUTE.test(url.pathname) ||
          ADMIN_APPEAL_ROUTE.test(url.pathname) ||
          ADMIN_DELETION_APPROVAL_ROUTE.test(url.pathname) ||
          ADMIN_SECURITY_ALERT_ROUTE.test(url.pathname);
        const trackRoute = url.pathname.match(TRACK_ROUTE);
        const trackDownloadRoute = url.pathname.match(TRACK_DOWNLOAD_ROUTE);
        const trackContextRoute = url.pathname.match(TRACK_CONTEXT_ROUTE);
        const trackShareRoute = url.pathname.match(TRACK_SHARE_ROUTE);
        const artworkRoute = url.pathname.match(ALBUM_ARTWORK_ROUTE);
        const playlistRoute = url.pathname.match(PLAYLIST_ARTWORK_ROUTE);
        const profileAvatarRoute = url.pathname.match(PROFILE_AVATAR_ROUTE);
        const isArtistBiographyRoute = ARTIST_BIOGRAPHY_ROUTE.test(url.pathname);
        const artistArtworkRoute = url.pathname.match(ARTIST_ARTWORK_ROUTE);
        const isArtistRoute = url.pathname === "/v1/artists/me/workspace" ||
          url.pathname === "/v1/artists/me/team" ||
          url.pathname === "/v1/artists/me/albums" ||
          url.pathname === "/v1/artists/me/tracks" ||
          url.pathname === "/v1/artists/me/reviews" ||
          ARTIST_PROFILE_ARTWORK_ROUTE.test(url.pathname) ||
          ARTIST_ALBUM_ARTWORK_ROUTE.test(url.pathname) ||
          ARTIST_AUDIO_ROUTE.test(url.pathname) || ARTIST_LYRICS_ROUTE.test(url.pathname) ||
          ARTIST_ANALYTICS_ROUTE.test(url.pathname) ||
          !!artistArtworkRoute;
        const isAccountRoute = url.pathname === "/v1/account/lifecycle" ||
          url.pathname === "/v1/account/appeals" ||
          url.pathname === "/v1/account/deletion" ||
          url.pathname === "/v1/account/export" ||
          url.pathname === "/v1/account/release-notifications";
        const recommendationArtworkRoute = url.pathname.match(RECOMMENDATION_ARTWORK_ROUTE);
        if (!trackRoute?.[1] && !trackDownloadRoute?.[1] && !trackContextRoute?.[1] &&
          !trackShareRoute?.[1] && !artworkRoute?.[1] && !playlistRoute?.[1] &&
          !profileAvatarRoute?.[1] && !isArtistBiographyRoute &&
          !recommendationArtworkRoute?.[1] && !isAdminRoute && !isArtistRoute && !isAccountRoute) {
          return errorResponse("not_found", "Resource not found.", 404, requestId, origin);
        }
        if (!isAdminRoute && !isArtistRoute && !isAccountRoute &&
          request.method !== "GET" && request.method !== "HEAD" &&
          !(playlistRoute && (request.method === "PUT" || request.method === "DELETE")) &&
          !(profileAvatarRoute && (request.method === "PUT" || request.method === "DELETE")) &&
          !(isArtistBiographyRoute && request.method === "PUT")) {
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
        let listener: { userId: string };
        try {
          token = readBearerToken(request);
          listener = await requestDependencies.verifyListener(token, env);
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

        if (profileAvatarRoute?.[1]) {
          const profileId = profileAvatarRoute[1].toLowerCase();
          if ((request.method === "PUT" || request.method === "DELETE") && profileId !== listener.userId) {
            return errorResponse("forbidden", "Only the profile owner can change this image.", 403, requestId, origin);
          }
          return await profileAvatar(request, env, profileId, token, requestDependencies, requestId, origin);
        }

        if (isAccountRoute) {
          return await accountRequest(request, env, url.pathname, listener.userId, token,
            requestDependencies, requestId, origin);
        }

        if (trackContextRoute?.[1]) {
          if (request.method !== "GET") {
            return errorResponse("method_not_allowed", "Method not allowed.", 405,
              requestId, origin, { allow: "GET, OPTIONS" });
          }
          return await trackContextRequest(
            trackContextRoute[1].toLowerCase(), request, env, token,
            requestDependencies, requestId, origin,
          );
        }

        if (trackShareRoute?.[1]) {
          if (request.method !== "GET") {
            return errorResponse("method_not_allowed", "Method not allowed.", 405,
              requestId, origin, { allow: "GET, OPTIONS" });
          }
          const trackId = trackShareRoute[1].toLowerCase();
          try {
            if (!(await requestDependencies.canStreamTrack(trackId, token, env))) {
              return errorResponse("track_not_found", "Track not found.", 404, requestId, origin);
            }
          } catch (error) {
            if (!(error instanceof CatalogUnavailable)) throw error;
            return errorResponse("catalog_unavailable", "Catalog authorization is temporarily unavailable.",
              503, requestId, origin, { "retry-after": "30" });
          }
          return jsonResponse({
            trackId,
            canonicalUri: `my.id.rakyzumusic://track/${trackId}`,
            previewSafe: true,
          }, 200, requestId, origin);
        }

        if (isArtistBiographyRoute) {
          if (request.method !== "PUT") {
            return errorResponse("method_not_allowed", "Method not allowed.", 405,
              requestId, origin, { allow: "PUT, OPTIONS" });
          }
          const body = await readJsonObject(request);
          const biography = readOptionalString(body, "biography", 1_500);
          const result = await requestDependencies.adminRpc("artist_update_biography",
            { requested_biography: biography }, token, env);
          return jsonResponse(result, 200, requestId, origin);
        }

        if (isArtistRoute) {
          return await artistRequest(request, env, url.pathname, token,
            requestDependencies, requestId, origin);
        }

        if (recommendationArtworkRoute?.[1]) {
          const shelfId = recommendationArtworkRoute[1].toLowerCase();
          try {
            if (!(await requestDependencies.canAccessEditorialArtwork(shelfId, token, env))) {
              return errorResponse("artwork_not_found", "Artwork not found.", 404, requestId, origin);
            }
          } catch (error) {
            if (!(error instanceof CatalogUnavailable)) throw error;
            return errorResponse("catalog_unavailable", "Catalog authorization is temporarily unavailable.",
              503, requestId, origin, { "retry-after": "30" });
          }
          return await serveRecommendationArtwork(request, env, shelfId, requestId, origin);
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

        if (trackRoute?.[1] || trackDownloadRoute?.[1]) {
          const trackId = (trackRoute?.[1] ?? trackDownloadRoute?.[1])!.toLowerCase();
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
          let objectKey: string | null;
          try {
            objectKey = await requestDependencies.resolveTrackMediaKey(trackId, quality, token, env);
          } catch (error) {
            if (!(error instanceof CatalogUnavailable)) throw error;
            return errorResponse("catalog_unavailable", "Media authorization is temporarily unavailable.",
              503, requestId, origin, { "retry-after": "30" });
          }
          if (objectKey === null) {
            return errorResponse("media_not_found", "Track media is unavailable.", 404, requestId, origin);
          }
          return await streamTrack(
            request,
            env,
            objectKey,
            quality,
            requestId,
            origin,
            trackDownloadRoute?.[1] !== undefined,
          );
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

async function signedIngestionRequest(
  request: Request,
  env: RakyzuApiEnv,
  path: string,
  requestDependencies: RequestDependencies,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  if (!requestDependencies.serviceRpc) {
    return errorResponse("ingestion_unavailable", "Event ingestion is unavailable.",
      503, requestId, origin, { "retry-after": "30" });
  }
  const raw = await readBoundedJsonBody(request, 32_768);
  if (raw === null) {
    return errorResponse("invalid_event", "The event payload is invalid.", 422, requestId, origin);
  }
  const secret = path === "/v1/events/play"
    ? env.PLAY_EVENT_WEBHOOK_SECRET : env.COMMERCE_WEBHOOK_SECRET;
  if (!await verifyWebhookSignature(request.headers, raw.text, secret)) {
    return errorResponse("invalid_signature", "The event signature is invalid.",
      401, requestId, origin);
  }
  try {
    if (path === "/v1/events/play") {
      const body = raw.value;
      const result = await requestDependencies.serviceRpc("service_record_play_event", {
        requested_source_event_id: readString(body, "eventId", 160),
        requested_user_id: readOptionalUuid(body, "userId"),
        requested_track_id: readUuid(body, "trackId"),
        requested_occurred_at: readInstant(body, "occurredAt"),
        requested_listening_ms: readInteger(body, "listeningMs", 10_000, 86_400_000),
        requested_completed: readBoolean(body, "completed"),
        requested_country_code: readNullableCountryCode(body, "countryCode"),
      }, env);
      return jsonResponse(result, 202, requestId, origin);
    }
    const body = raw.value;
    const payloadHash = await sha256Hex(raw.text);
    const result = await requestDependencies.serviceRpc("service_ingest_commerce_event", {
      requested_provider: readString(body, "provider", 32).toLowerCase(),
      requested_event_id: readString(body, "eventId", 160),
      requested_event_type: readString(body, "type", 40),
      requested_user_id: readOptionalUuid(body, "userId"),
      requested_customer_ref: readNullableString(body, "customerReference", 160),
      requested_product_ref: readNullableString(body, "productReference", 160),
      requested_amount_minor: readNullableInteger(body, "amountMinor", 0, Number.MAX_SAFE_INTEGER),
      requested_currency: readNullableCurrency(body, "currency"),
      requested_occurred_at: readInstant(body, "occurredAt"),
      requested_payload_sha256: payloadHash,
      requested_payload: body,
    }, env);
    return jsonResponse(result, 202, requestId, origin);
  } catch (error) {
    if (error instanceof AdminRequestRejected) {
      return errorResponse("invalid_event", "The event payload is invalid.",
        422, requestId, origin);
    }
    if (error instanceof AdminUpstreamUnavailable) {
      return errorResponse("ingestion_unavailable", "Event ingestion is unavailable.",
        503, requestId, origin, { "retry-after": "30" });
    }
    throw error;
  }
}

async function accountRequest(
  request: Request,
  env: RakyzuApiEnv,
  path: string,
  userId: string,
  token: string,
  requestDependencies: RequestDependencies,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  try {
    if (path === "/v1/account/release-notifications" && request.method === "GET") {
      return jsonResponse(await requestDependencies.adminRpc(
        "get_release_notification_preference", {}, token, env), 200, requestId, origin);
    }
    if (path === "/v1/account/release-notifications" && request.method === "PUT") {
      const body = await readJsonObject(request);
      return jsonResponse(await requestDependencies.adminRpc(
        "set_release_notification_preference", {
          requested_preference: readString(body, "preference", 24),
        }, token, env), 200, requestId, origin);
    }
    if (path === "/v1/account/lifecycle" && request.method === "GET") {
      return jsonResponse(await requestDependencies.adminRpc(
        "account_lifecycle_context", {}, token, env), 200, requestId, origin);
    }
    if (path === "/v1/account/appeals" && request.method === "POST") {
      const body = await readJsonObject(request);
      return jsonResponse(await requestDependencies.adminRpc("account_submit_appeal", {
        target_enforcement_id: readUuid(body, "enforcementId"),
        requested_statement: readString(body, "statement", 2_000),
      }, token, env), 201, requestId, origin);
    }
    if (path === "/v1/account/deletion" && request.method === "POST") {
      const body = await readJsonObject(request);
      return jsonResponse(await requestDependencies.adminRpc("account_request_deletion", {
        requested_reason: readString(body, "reason", 1_000),
      }, token, env), 201, requestId, origin);
    }
    if (path === "/v1/account/export" && request.method === "GET") {
      return jsonResponse(await requestDependencies.adminRpc("admin_export_account_data", {
        target_user_id: userId,
      }, token, env), 200, requestId, origin);
    }
    return errorResponse("method_not_allowed", "Method not allowed.", 405, requestId, origin);
  } catch (error) {
    if (error instanceof AdminRequestRejected) {
      return errorResponse(error.status === 403 ? "forbidden" : "invalid_account_request",
        error.status === 403 ? "This account action is not available." :
          "The account request is invalid.", error.status, requestId, origin);
    }
    if (error instanceof AdminUpstreamUnavailable) {
      return errorResponse("account_unavailable", "Account services are temporarily unavailable.",
        503, requestId, origin, { "retry-after": "30" });
    }
    throw error;
  }
}

async function trackContextRequest(
  trackId: string,
  request: Request,
  env: RakyzuApiEnv,
  token: string,
  requestDependencies: RequestDependencies,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  const country = (request.cf as { country?: string } | undefined)?.country;
  const normalizedCountry = country && /^[A-Z]{2}$/i.test(country) ? country.toUpperCase() : null;
  try {
    const value = await requestDependencies.adminRpc("get_track_context", {
      requested_track_id: trackId,
      requested_country_code: normalizedCountry,
    }, token, env);
    return jsonResponse(value, 200, requestId, origin, {
      "cache-control": "private, max-age=300, must-revalidate",
    });
  } catch (error) {
    if (error instanceof AdminRequestRejected) {
      const status = error.status === 401 ? 401 : error.status === 403 ? 403 : 404;
      return errorResponse(status === 404 ? "track_not_found" : "forbidden",
        status === 404 ? "Track context is unavailable." : "Track context access is denied.",
        status, requestId, origin);
    }
    if (error instanceof AdminUpstreamUnavailable) {
      return errorResponse("context_unavailable", "Track context is temporarily unavailable.",
        503, requestId, origin, { "retry-after": "30" });
    }
    throw error;
  }
}

async function artistRequest(
  request: Request,
  env: RakyzuApiEnv,
  path: string,
  token: string,
  requestDependencies: RequestDependencies,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  try {
    const analytics = path.match(ARTIST_ANALYTICS_ROUTE);
    if (analytics?.[1] && request.method === "GET") {
      const url = new URL(request.url);
      const from = readQueryDate(url.searchParams.get("from"));
      const to = readQueryDate(url.searchParams.get("to"));
      const value = await requestDependencies.adminRpc("artist_analytics", {
        target_artist_id: analytics[1].toLowerCase(), range_start: from, range_end: to,
      }, token, env);
      return jsonResponse(value, 200, requestId, origin);
    }
    if (path === "/v1/artists/me/workspace" && request.method === "GET") {
      const value = await requestDependencies.adminRpc("artist_workspace_context", {}, token, env);
      return jsonResponse(value, 200, requestId, origin);
    }
    if (path === "/v1/artists/me/team" && request.method === "POST") {
      const body = await readJsonObject(request);
      const value = await requestDependencies.adminRpc("artist_assign_team_by_email", {
        target_email: readString(body, "email", 254),
        target_access_level: readString(body, "accessLevel", 20),
        target_active: readBoolean(body, "active"),
      }, token, env);
      return jsonResponse(value, 200, requestId, origin);
    }
    if (path === "/v1/artists/me/albums" && request.method === "POST") {
      const body = await readJsonObject(request);
      const value = await requestDependencies.adminRpc("artist_create_album_draft", {
        target_artist_id: readUuid(body, "artistId"),
        album_title: readString(body, "title", 160),
        album_release_date: readOptionalDate(body, "releaseDate"),
      }, token, env);
      return jsonResponse(value, 201, requestId, origin);
    }
    if (path === "/v1/artists/me/tracks" && request.method === "POST") {
      const body = await readJsonObject(request);
      const value = await requestDependencies.adminRpc("artist_create_track_draft", {
        target_album_id: readUuid(body, "albumId"),
        track_title: readString(body, "title", 160),
        duration_ms: readInteger(body, "durationMs", 1_000, 86_400_000),
        disc_number: readOptionalInteger(body, "discNumber", 1, 100, 1),
        track_number: readOptionalInteger(body, "trackNumber", 1, 1_000, 1),
        explicit_content: readBoolean(body, "explicit"),
      }, token, env);
      return jsonResponse(value, 201, requestId, origin);
    }
    if (path === "/v1/artists/me/reviews" && request.method === "POST") {
      const body = await readJsonObject(request);
      const value = await requestDependencies.adminRpc("artist_submit_catalog_review", {
        requested_review_type: readString(body, "type", 20),
        target_album_id: readUuid(body, "albumId"),
        requested_notes: readOptionalString(body, "notes", 500),
      }, token, env);
      return jsonResponse(value, 201, requestId, origin);
    }
    if (ARTIST_PROFILE_ARTWORK_ROUTE.test(path) && request.method === "PUT") {
      const scope = await requestDependencies.adminRpc("artist_artwork_upload_scope", {}, token, env);
      if (typeof scope !== "string" || !new RegExp(`^${UUID}$`, "i").test(scope)) {
        return errorResponse("artist_unavailable", "Artist identity is unavailable.",
          503, requestId, origin);
      }
      return await uploadArtistProfileArtwork(request, env, scope.toLowerCase(), token,
        requestDependencies, requestId, origin);
    }
    const albumArtwork = path.match(ARTIST_ALBUM_ARTWORK_ROUTE);
    if (albumArtwork?.[1] && request.method === "PUT") {
      const albumId = albumArtwork[1].toLowerCase();
      const allowed = await requestDependencies.adminRpc("artist_can_edit_album",
        { target_album_id: albumId }, token, env);
      if (allowed !== true) return artistForbidden(requestId, origin);
      return await uploadAlbumArtwork(request, env, albumId, null, token,
        requestDependencies, requestId, origin, "artist_record_album_artwork");
    }
    const audio = path.match(ARTIST_AUDIO_ROUTE);
    if (audio?.[1] && audio[2] && request.method === "PUT") {
      const trackId = audio[1].toLowerCase();
      const allowed = await requestDependencies.adminRpc("artist_can_edit_track",
        { target_track_id: trackId }, token, env);
      if (allowed !== true) return artistForbidden(requestId, origin);
      return await uploadTrackAudio(request, env, trackId, audio[2].toLowerCase(),
        null, token, requestDependencies, requestId, origin, "artist_record_track_media");
    }
    const lyrics = path.match(ARTIST_LYRICS_ROUTE);
    if (lyrics?.[1]) {
      const trackId = lyrics[1].toLowerCase();
      const allowed = await requestDependencies.adminRpc("artist_can_edit_track",
        { target_track_id: trackId }, token, env);
      if (allowed !== true) return artistForbidden(requestId, origin);
      if (request.method === "GET") {
        const value = await requestDependencies.adminRpc("get_editable_track_lyrics",
          { target_track_id: trackId }, token, env);
        return jsonResponse(value, 200, requestId, origin);
      }
      if (request.method === "PUT") {
        const payload = await readLyricsPayload(request, false);
        const value = await requestDependencies.adminRpc("upsert_track_lyrics", {
          target_track_id: trackId,
          ...payload,
        }, token, env);
        return jsonResponse(value, 200, requestId, origin);
      }
      if (request.method === "DELETE") {
        const value = await requestDependencies.adminRpc("delete_track_lyrics",
          { target_track_id: trackId }, token, env);
        return jsonResponse(value, 200, requestId, origin);
      }
    }
    const artwork = path.match(ARTIST_ARTWORK_ROUTE);
    if (artwork?.[1] && (request.method === "GET" || request.method === "HEAD")) {
      const artistId = artwork[1].toLowerCase();
      const allowed = await requestDependencies.adminRpc("artist_can_view_artwork",
        { target_artist_id: artistId }, token, env);
      if (allowed !== true) return errorResponse("artwork_not_found", "Artwork not found.",
        404, requestId, origin);
      return await serveArtistArtwork(request, env, artistId, requestId, origin);
    }
    return errorResponse("method_not_allowed", "Method not allowed.", 405,
      requestId, origin);
  } catch (error) {
    if (error instanceof AdminRequestRejected) {
      const status = error.status === 401 ? 401 : error.status === 403 ? 403 : 422;
      return errorResponse(status === 403 ? "forbidden" : "invalid_artist_request",
        status === 403 ? "Artist access is required for this action." :
          "The Artist request is invalid.", status, requestId, origin);
    }
    if (error instanceof AdminUpstreamUnavailable) {
      return errorResponse("artist_unavailable", "Artist services are temporarily unavailable.",
        503, requestId, origin, { "retry-after": "30" });
    }
    throw error;
  }
}

function artistForbidden(requestId: string, origin: string | null): Response {
  return errorResponse("forbidden", "Artist access is required for this action.",
    403, requestId, origin);
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
    if (path === "/v1/admin/governance" && request.method === "GET") {
      return await rpcResponse("admin_governance_dashboard", {}, "admin.access", context, token, env,
        requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/enforcement" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_apply_content_enforcement", {
        target_subject_type: readString(body, "subjectType", 20),
        target_subject_id: readUuid(body, "subjectId"),
        enforcement_action: readString(body, "action", 20),
        enforcement_reason: readString(body, "reason", 500),
        target_case_id: readOptionalUuid(body, "caseId"),
      }, "content.enforce", context, token, env, requestDependencies, requestId, origin, 201);
    }
    if (path === "/v1/admin/catalog/teams" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_assign_catalog_team_by_email", {
        target_scope_type: readString(body, "scopeType", 20),
        target_scope_id: readUuid(body, "scopeId"),
        target_email: readString(body, "email", 254).toLowerCase(),
        target_access_level: readString(body, "accessLevel", 20),
        target_active: readBoolean(body, "active"),
      }, "catalog.team_manage", context, token, env, requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/catalog/labels" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_create_catalog_label", {
        label_name: readString(body, "name", 120),
      }, "catalog.draft", context, token, env, requestDependencies, requestId, origin, 201);
    }
    if (path === "/v1/admin/catalog/labels/artists" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_link_catalog_label_artist", {
        target_label_id: readUuid(body, "labelId"),
        target_artist_id: readUuid(body, "artistId"),
      }, "catalog.team_manage", context, token, env, requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/artists" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_create_artist", {
        artist_name: readString(body, "name", 120),
        artist_email: readNullableString(body, "email", 254),
      },
        "catalog.draft", context, token, env, requestDependencies, requestId, origin, 201);
    }
    const artistMatch = path.match(ADMIN_ARTIST_ROUTE);
    if (artistMatch?.[1] && (request.method === "PATCH" || request.method === "PUT")) {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_update_artist", {
        target_artist_id: artistMatch[1].toLowerCase(),
        artist_name: readString(body, "name", 120),
        artist_email: readNullableString(body, "email", 254),
      }, "catalog.draft", context, token, env, requestDependencies, requestId, origin);
    }
    if (artistMatch?.[1] && request.method === "DELETE") {
      return await rpcResponse("admin_archive_artist", {
        target_artist_id: artistMatch[1].toLowerCase(),
      }, "catalog.draft", context, token, env, requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/recommendations" && request.method === "GET") {
      return await rpcResponse("admin_list_recommendations", {}, "editorial.manage", context,
        token, env, requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/recommendations" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_upsert_editorial_shelf", {
        target_shelf_id: readOptionalUuid(body, "id"),
        shelf_title: readString(body, "title", 80),
        shelf_subtitle: readNullableString(body, "subtitle", 160),
        shelf_position: readEditorialPosition(body, "position"),
        target_track_id: readOptionalUuid(body, "trackId"),
        published: readBoolean(body, "published"),
        shelf_card_label: readNullableString(body, "cardLabel", 40),
        shelf_color_hex: readColorHex(body, "colorHex"),
      }, "editorial.manage", context, token, env, requestDependencies, requestId, origin, 201);
    }
    const recommendationMatch = path.match(ADMIN_RECOMMENDATION_ROUTE);
    if (recommendationMatch?.[1] && request.method === "DELETE") {
      return await rpcResponse("admin_delete_editorial_shelf", {
        target_shelf_id: recommendationMatch[1].toLowerCase(),
      }, "editorial.manage", context, token, env, requestDependencies, requestId, origin);
    }
    const recommendationArtworkMatch = path.match(ADMIN_RECOMMENDATION_ARTWORK_ROUTE);
    if (recommendationArtworkMatch?.[1] && request.method === "PUT") {
      return await uploadRecommendationArtwork(
        request, env, recommendationArtworkMatch[1].toLowerCase(), context, token,
        requestDependencies, requestId, origin,
      );
    }
    if (recommendationArtworkMatch?.[1] && request.method === "DELETE") {
      return await deleteRecommendationArtwork(
        env, recommendationArtworkMatch[1].toLowerCase(), context, token,
        requestDependencies, requestId, origin,
      );
    }
    const recommendationTracksMatch = path.match(ADMIN_RECOMMENDATION_TRACKS_ROUTE);
    if (recommendationTracksMatch?.[1] && request.method === "PUT") {
      const body = await readJsonObject(request);
      const rawTrackIds = body.trackIds;
      if (!Array.isArray(rawTrackIds) || rawTrackIds.length < 1 || rawTrackIds.length > 50) {
        throw new AdminRequestRejected(422);
      }
      const trackIds = rawTrackIds.map((value) => {
        if (typeof value !== "string" || !new RegExp(`^${UUID}$`, "i").test(value)) {
          throw new AdminRequestRejected(422);
        }
        return value.toLowerCase();
      });
      if (new Set(trackIds).size !== trackIds.length) throw new AdminRequestRejected(422);
      return await rpcResponse("admin_replace_editorial_tracks", {
        target_shelf_id: recommendationTracksMatch[1].toLowerCase(),
        target_track_ids: trackIds,
      }, "editorial.manage", context, token, env, requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/albums" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_create_album", {
        artist_id: readUuid(body, "artistId"),
        album_title: readString(body, "title", 160),
        album_release_date: readOptionalDate(body, "releaseDate"),
      }, "catalog.draft", context, token, env, requestDependencies, requestId, origin, 201);
    }
    const albumMatch = path.match(ADMIN_ALBUM_ROUTE);
    if (albumMatch?.[1] && (request.method === "PATCH" || request.method === "PUT")) {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_update_album", {
        target_album_id: albumMatch[1].toLowerCase(),
        album_title: readString(body, "title", 160),
        album_release_date: readOptionalDate(body, "releaseDate"),
      }, "catalog.draft", context, token, env, requestDependencies, requestId, origin);
    }
    if (albumMatch?.[1] && request.method === "DELETE") {
      return await rpcResponse("admin_archive_album", {
        target_album_id: albumMatch[1].toLowerCase(),
      }, "catalog.draft", context, token, env, requestDependencies, requestId, origin);
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
    const scheduleMatch = path.match(ADMIN_ALBUM_SCHEDULE_ROUTE);
    if (scheduleMatch?.[1] && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_schedule_album", {
        target_album_id: scheduleMatch[1].toLowerCase(),
        requested_publish_at: readInstant(body, "publishAt"),
      }, "catalog.publish", context, token, env, requestDependencies, requestId, origin);
    }
    const artworkMatch = path.match(ADMIN_ARTWORK_UPLOAD_ROUTE);
    if (artworkMatch?.[1] && request.method === "PUT") {
      return await uploadAlbumArtwork(
        request, env, artworkMatch[1].toLowerCase(), context, token,
        requestDependencies, requestId, origin,
      );
    }
    const audioMatch = path.match(ADMIN_AUDIO_ROUTE);
    if (audioMatch?.[1] && audioMatch[2] && request.method === "PUT") {
      return await uploadTrackAudio(
        request, env, audioMatch[1].toLowerCase(), audioMatch[2].toLowerCase(), context,
        token, requestDependencies, requestId, origin,
      );
    }
    const lyricsMatch = path.match(ADMIN_LYRICS_ROUTE);
    if (lyricsMatch?.[1]) {
      const trackId = lyricsMatch[1].toLowerCase();
      if (request.method === "GET") {
        return await rpcResponse("get_editable_track_lyrics", { target_track_id: trackId },
          "lyrics.manage", context, token, env, requestDependencies, requestId, origin);
      }
      if (request.method === "PUT") {
        const payload = await readLyricsPayload(request, true);
        return await rpcResponse("upsert_track_lyrics", { target_track_id: trackId, ...payload },
          "lyrics.manage", context, token, env, requestDependencies, requestId, origin);
      }
      if (request.method === "DELETE") {
        return await rpcResponse("delete_track_lyrics", { target_track_id: trackId },
          "lyrics.manage", context, token, env, requestDependencies, requestId, origin);
      }
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
    if (caseMatch?.[1] && (request.method === "PATCH" || request.method === "PUT")) {
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
    if (path === "/v1/admin/reviews" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_submit_catalog_review", {
        requested_review_type: readString(body, "reviewType", 20),
        requested_target_type: readString(body, "targetType", 20),
        requested_target_id: readUuid(body, "targetId"),
        requested_notes: readOptionalString(body, "notes", 500),
      }, "catalog.draft", context, token, env, requestDependencies, requestId, origin, 201);
    }
    const reviewMatch = path.match(ADMIN_REVIEW_ACTION_ROUTE);
    if (reviewMatch?.[1] && (request.method === "PATCH" || request.method === "PUT")) {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_decide_catalog_review", {
        target_review_id: reviewMatch[1].toLowerCase(),
        review_decision: readString(body, "decision", 20),
        review_notes: readString(body, "notes", 500),
      }, "catalog.review", context, token, env, requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/commerce" && request.method === "GET") {
      return await rpcResponse("admin_commerce_dashboard", {}, "commerce.view", context,
        token, env, requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/accounts" && request.method === "GET") {
      return await rpcResponse("admin_account_dashboard", {}, "account.enforce", context,
        token, env, requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/security" && request.method === "GET") {
      return await rpcResponse("admin_security_dashboard", {}, "security.manage", context,
        token, env, requestDependencies, requestId, origin);
    }
    const enforcementMatch = path.match(ADMIN_ACCOUNT_ENFORCEMENT_ROUTE);
    if (enforcementMatch?.[1] && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_enforce_account", {
        target_user_id: enforcementMatch[1].toLowerCase(),
        requested_action: readString(body, "action", 20),
        requested_reason: readString(body, "reason", 1_000),
        requested_expires_at: readNullableInstant(body, "expiresAt"),
      }, "account.enforce", context, token, env, requestDependencies, requestId, origin, 201);
    }
    const appealMatch = path.match(ADMIN_APPEAL_ROUTE);
    if (appealMatch?.[1] && (request.method === "PATCH" || request.method === "PUT")) {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_decide_appeal", {
        target_appeal_id: appealMatch[1].toLowerCase(),
        decision: readString(body, "decision", 20),
        requested_notes: readString(body, "notes", 1_000),
      }, "appeal.review", context, token, env, requestDependencies, requestId, origin);
    }
    const deletionMatch = path.match(ADMIN_DELETION_APPROVAL_ROUTE);
    if (deletionMatch?.[1] && request.method === "POST") {
      return await rpcResponse("admin_approve_deletion", {
        target_request_id: deletionMatch[1].toLowerCase(),
      }, "deletion.approve", context, token, env, requestDependencies, requestId, origin);
    }
    const securityAlertMatch = path.match(ADMIN_SECURITY_ALERT_ROUTE);
    if (securityAlertMatch?.[1] && (request.method === "PATCH" || request.method === "PUT")) {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_acknowledge_security_alert", {
        target_alert_id: securityAlertMatch[1].toLowerCase(),
        resolved: readBoolean(body, "resolved"),
      }, "security.manage", context, token, env, requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/audit/export" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_export_audit", {
        operation_filter: readNullableString(body, "operation", 80),
        target_type_filter: readNullableString(body, "targetType", 40),
        before_time: readNullableInstant(body, "before"),
        page_size: readOptionalInteger(body, "pageSize", 1, 500, 200),
      }, "audit.export", context, token, env, requestDependencies, requestId, origin);
    }
    if (path === "/v1/admin/audit/retention" && request.method === "POST") {
      const body = await readJsonObject(request);
      return await rpcResponse("admin_set_audit_retention", {
        requested_days: readInteger(body, "retentionDays", 90, 2555),
      }, "governance.manage", context, token, env, requestDependencies, requestId, origin);
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

async function uploadAlbumArtwork(
  request: Request,
  env: RakyzuApiEnv,
  albumId: string,
  context: StaffContext | null,
  token: string,
  requestDependencies: RequestDependencies,
  requestId: string,
  origin: string | null,
  recordRpc: "admin_record_album_artwork" | "artist_record_album_artwork" = "admin_record_album_artwork",
): Promise<Response> {
  if (context && !context.permissions.includes("catalog.upload_artwork")) {
    return adminForbidden(requestId, origin);
  }
  if (request.headers.get("content-type")?.split(";", 1)[0]?.trim() !== "image/webp") {
    return errorResponse("invalid_artwork", "Upload WebP artwork.", 415, requestId, origin);
  }
  const claimedLength = Number(request.headers.get("content-length"));
  if (!Number.isInteger(claimedLength) || claimedLength < 12 || claimedLength > MAX_ARTWORK_BYTES) {
    return errorResponse("invalid_artwork_size", "Artwork must be at most 5 MiB.", 413,
      requestId, origin);
  }
  const bytes = await readExactBody(request, claimedLength);
  if (bytes === null || !looksLikeWebp(bytes)) {
    return errorResponse("invalid_artwork", "The file is not valid WebP artwork.", 422,
      requestId, origin);
  }
  const objectKey = `media/albums/${albumId}/artwork.webp`;
  const uploaded = await env.MEDIA.put(objectKey, bytes, {
    httpMetadata: { contentType: "image/webp", cacheControl: "private, no-store" },
  });
  try {
    const result = await requestDependencies.adminRpc(recordRpc, {
      target_album_id: albumId,
      media_object_key: objectKey,
      media_size_bytes: bytes.length,
      media_content_type: "image/webp",
      media_etag: uploaded.etag,
    }, token, env);
    return jsonResponse(result, 201, requestId, origin);
  } catch (error) {
    await env.MEDIA.delete(objectKey);
    throw error;
  }
}

async function uploadArtistProfileArtwork(
  request: Request,
  env: RakyzuApiEnv,
  artistId: string,
  token: string,
  requestDependencies: RequestDependencies,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  if (request.headers.get("content-type")?.split(";", 1)[0]?.trim() !== "image/webp") {
    return errorResponse("invalid_artwork", "Upload WebP artwork.", 415, requestId, origin);
  }
  const claimedLength = Number(request.headers.get("content-length"));
  if (!Number.isInteger(claimedLength) || claimedLength < 12 || claimedLength > MAX_ARTWORK_BYTES) {
    return errorResponse("invalid_artwork_size", "Artwork must be at most 5 MiB.", 413,
      requestId, origin);
  }
  const bytes = await readExactBody(request, claimedLength);
  if (bytes === null || !looksLikeWebp(bytes)) {
    return errorResponse("invalid_artwork", "The file is not valid WebP artwork.", 422,
      requestId, origin);
  }
  const objectKey = `media/artists/${artistId}/artwork.webp`;
  const uploaded = await env.MEDIA.put(objectKey, bytes, {
    httpMetadata: { contentType: "image/webp", cacheControl: "private, no-store" },
  });
  try {
    const result = await requestDependencies.adminRpc("artist_record_profile_artwork", {
      media_object_key: objectKey,
      media_size_bytes: bytes.length,
      media_content_type: "image/webp",
      media_etag: uploaded.etag,
    }, token, env);
    return jsonResponse(result, 201, requestId, origin);
  } catch (error) {
    await env.MEDIA.delete(objectKey);
    throw error;
  }
}

async function uploadRecommendationArtwork(
  request: Request,
  env: RakyzuApiEnv,
  shelfId: string,
  context: StaffContext,
  token: string,
  requestDependencies: RequestDependencies,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  if (!context.fullAccess && !context.permissions.includes("editorial.manage")) {
    return adminForbidden(requestId, origin);
  }
  if (request.headers.get("content-type")?.split(";", 1)[0]?.trim() !== "image/webp") {
    return errorResponse("invalid_artwork", "Upload WebP artwork.", 415, requestId, origin);
  }
  const claimedLength = Number(request.headers.get("content-length"));
  if (!Number.isInteger(claimedLength) || claimedLength < 12 || claimedLength > MAX_ARTWORK_BYTES) {
    return errorResponse("invalid_artwork_size", "Artwork must be at most 5 MiB.", 413,
      requestId, origin);
  }
  const bytes = await readExactBody(request, claimedLength);
  if (bytes === null || !looksLikeWebp(bytes)) {
    return errorResponse("invalid_artwork", "The file is not valid WebP artwork.", 422,
      requestId, origin);
  }
  const objectKey = `media/recommendations/${shelfId}/artwork.webp`;
  const uploaded = await env.MEDIA.put(objectKey, bytes, {
    httpMetadata: { contentType: "image/webp", cacheControl: "private, no-store" },
  });
  try {
    const result = await requestDependencies.adminRpc("admin_record_editorial_artwork", {
      target_shelf_id: shelfId,
      media_object_key: objectKey,
      media_size_bytes: bytes.length,
      media_content_type: "image/webp",
      media_etag: uploaded.etag,
    }, token, env);
    return jsonResponse(result, 201, requestId, origin);
  } catch (error) {
    await env.MEDIA.delete(objectKey);
    throw error;
  }
}

async function deleteRecommendationArtwork(
  env: RakyzuApiEnv,
  shelfId: string,
  context: StaffContext,
  token: string,
  requestDependencies: RequestDependencies,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  if (!context.fullAccess && !context.permissions.includes("editorial.manage")) {
    return adminForbidden(requestId, origin);
  }
  const result = await requestDependencies.adminRpc("admin_delete_editorial_artwork", {
    target_shelf_id: shelfId,
  }, token, env);
  await env.MEDIA.delete(`media/recommendations/${shelfId}/artwork.webp`);
  return jsonResponse(result, 200, requestId, origin);
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
  context: StaffContext | null,
  token: string,
  requestDependencies: RequestDependencies,
  requestId: string,
  origin: string | null,
  recordRpc: "admin_record_track_media" | "artist_record_track_media" = "admin_record_track_media",
): Promise<Response> {
  if (context && !context.permissions.includes("catalog.upload_audio")) {
    return adminForbidden(requestId, origin);
  }
  const contentType = request.headers.get("content-type")?.split(";", 1)[0]?.trim();
  const format = contentType === undefined ? null : AUDIO_FORMATS[contentType];
  if (!format) return errorResponse("invalid_audio",
    "Upload MP3, AAC, M4A, WebM, WAV, or FLAC audio.", 415, requestId, origin);
  const claimedLength = Number(request.headers.get("content-length"));
  if (!Number.isInteger(claimedLength) || claimedLength < 4 || claimedLength > MAX_AUDIO_BYTES) {
    return errorResponse("invalid_audio_size", "Audio must be at most 50 MiB.", 413, requestId, origin);
  }
  const bytes = await readExactBody(request, claimedLength);
  if (bytes === null || !looksLikeAudio(bytes, format)) {
    return errorResponse("invalid_audio", "The audio container does not match its media type.", 422,
      requestId, origin);
  }
  const objectName = quality === "low" ? `source-low.${format}` :
    quality === "high" ? `source-high.${format}` : `source.${format}`;
  const objectKey = `media/tracks/${trackId}/${objectName}`;
  const uploaded = await env.MEDIA.put(objectKey, bytes, {
    httpMetadata: { contentType, cacheControl: "private, no-store" },
  });
  try {
    const result = await requestDependencies.adminRpc(recordRpc, {
      target_track_id: trackId,
      media_quality: quality,
      media_object_key: objectKey,
      media_size_bytes: bytes.length,
      media_etag: uploaded.etag,
      media_content_type: contentType,
      media_format: format,
    }, token, env);
    return jsonResponse(result, 201, requestId, origin);
  } catch (error) {
    await env.MEDIA.delete(objectKey);
    throw error;
  }
}

async function readExactBody(request: Request, claimedLength: number): Promise<Uint8Array | null> {
  if (!request.body) return null;
  const bytes = new Uint8Array(claimedLength);
  const reader = request.body.getReader();
  let offset = 0;
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      if (offset + chunk.value.length > claimedLength) {
        await reader.cancel();
        return null;
      }
      bytes.set(chunk.value, offset);
      offset += chunk.value.length;
    }
  } finally {
    reader.releaseLock();
  }
  return offset === claimedLength ? bytes : null;
}

type AudioFormat = "mp3" | "aac" | "m4a" | "webm" | "wav" | "flac";
const AUDIO_FORMATS: Record<string, AudioFormat> = {
  "audio/mpeg": "mp3", "audio/aac": "aac", "audio/mp4": "m4a",
  "audio/webm": "webm", "audio/wav": "wav", "audio/flac": "flac",
};

function looksLikeAudio(bytes: Uint8Array, format: AudioFormat): boolean {
  if (bytes.length < 4) return false;
  if (format === "mp3") return (
    (bytes[0] === 0x49 && bytes[1] === 0x44 && bytes[2] === 0x33) ||
    (bytes[0] === 0xff && (bytes[1]! & 0xe0) === 0xe0 && (bytes[1]! & 0x06) !== 0)
  );
  if (format === "aac") return bytes[0] === 0xff && (bytes[1]! & 0xf6) === 0xf0;
  if (format === "m4a") return bytes.length >= 12 && bytes[4] === 0x66 && bytes[5] === 0x74 &&
    bytes[6] === 0x79 && bytes[7] === 0x70;
  if (format === "webm") return bytes[0] === 0x1a && bytes[1] === 0x45 &&
    bytes[2] === 0xdf && bytes[3] === 0xa3;
  if (format === "wav") return bytes.length >= 12 && bytes[0] === 0x52 && bytes[1] === 0x49 &&
    bytes[2] === 0x46 && bytes[3] === 0x46 && bytes[8] === 0x57 && bytes[9] === 0x41 &&
    bytes[10] === 0x56 && bytes[11] === 0x45;
  return bytes[0] === 0x66 && bytes[1] === 0x4c && bytes[2] === 0x61 && bytes[3] === 0x43;
}

function looksLikeWebp(bytes: Uint8Array): boolean {
  return bytes.length >= 12 && bytes[0] === 0x52 && bytes[1] === 0x49 &&
    bytes[2] === 0x46 && bytes[3] === 0x46 && bytes[8] === 0x57 &&
    bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50;
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

async function readBoundedJsonBody(
  request: Request,
  maximumBytes: number,
): Promise<{ text: string; value: Record<string, unknown> } | null> {
  if (request.headers.get("content-type")?.split(";", 1)[0]?.trim() !== "application/json") {
    return null;
  }
  const claimed = Number(request.headers.get("content-length"));
  if (Number.isFinite(claimed) && claimed > maximumBytes) return null;
  const text = await request.text();
  if (new TextEncoder().encode(text).length > maximumBytes) return null;
  try {
    const value: unknown = JSON.parse(text);
    if (typeof value !== "object" || value === null || Array.isArray(value)) return null;
    return { text, value: value as Record<string, unknown> };
  } catch {
    return null;
  }
}

async function readLyricsPayload(
  request: Request,
  allowPublication: boolean,
): Promise<Record<string, unknown>> {
  const decoded = await readBoundedJsonBody(request, MAX_LYRICS_JSON_BYTES);
  if (decoded === null) throw new AdminRequestRejected(422);
  const body = decoded.value;
  const sourceFormat = readString(body, "sourceFormat", 10).toLowerCase();
  if (!new Set(["manual", "lrc", "srt"]).has(sourceFormat)) {
    throw new AdminRequestRejected(422);
  }
  const kind = sourceFormat === "manual" ? "plain" : "time_synced";
  const language = readNullableString(body, "language", 35);
  if (language !== null && !/^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$/.test(language)) {
    throw new AdminRequestRejected(422);
  }
  const published = readBoolean(body, "published");
  if (published && !allowPublication) throw new AdminRequestRejected(403);
  const rawLines = body.lines;
  if (!Array.isArray(rawLines) || rawLines.length < 1 || rawLines.length > 2_000) {
    throw new AdminRequestRejected(422);
  }
  let previousTime = -1;
  const lines = rawLines.map((raw) => {
    if (typeof raw !== "object" || raw === null || Array.isArray(raw)) {
      throw new AdminRequestRejected(422);
    }
    const line = raw as Record<string, unknown>;
    const text = readString(line, "text", 500);
    if (kind === "plain") return { text };
    const startTimeMs = readInteger(line, "startTimeMs", 0, 86_400_000);
    if (startTimeMs < previousTime) throw new AdminRequestRejected(422);
    previousTime = startTimeMs;
    return { text, startTimeMs };
  });
  return {
    requested_kind: kind,
    requested_source_format: sourceFormat,
    requested_language: language,
    requested_lines: lines,
    requested_published: published,
  };
}

async function verifyWebhookSignature(
  headers: Headers,
  body: string,
  secret: string,
): Promise<boolean> {
  const timestamp = headers.get("x-rakyzu-timestamp");
  const signature = headers.get("x-rakyzu-signature")?.toLowerCase();
  if (!timestamp || !signature || !/^\d{10}$/.test(timestamp) || !/^[a-f0-9]{64}$/.test(signature) ||
    typeof secret !== "string" || secret.length < 32) return false;
  const seconds = Number(timestamp);
  if (!Number.isSafeInteger(seconds) || Math.abs(Date.now() / 1000 - seconds) > 300) return false;
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" }, false, ["verify"]);
  return crypto.subtle.verify("HMAC", key, hexToBytes(signature),
    new TextEncoder().encode(`${timestamp}.${body}`));
}

async function sha256Hex(value: string): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256",
    new TextEncoder().encode(value)));
  return Array.from(digest, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function hexToBytes(value: string): Uint8Array {
  const bytes = new Uint8Array(value.length / 2);
  for (let index = 0; index < bytes.length; index += 1) {
    bytes[index] = Number.parseInt(value.slice(index * 2, index * 2 + 2), 16);
  }
  return bytes;
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

function readNullableString(body: Record<string, unknown>, key: string, max: number): string | null {
  const value = readOptionalString(body, key, max);
  return value.length === 0 ? null : value;
}

function readColorHex(body: Record<string, unknown>, key: string): string {
  if (body[key] === undefined || body[key] === null || body[key] === "") return "#4A558F";
  const value = readString(body, key, 7).toUpperCase();
  if (!/^#[0-9A-F]{6}$/.test(value)) throw new AdminRequestRejected(422);
  return value;
}

function readEditorialPosition(body: Record<string, unknown>, key: string): number {
  const value = readInteger(body, key, 0, 311);
  if ((value >= 0 && value <= 6) || (value >= 100 && value <= 106) ||
      (value >= 200 && value <= 207) || (value >= 300 && value <= 311)) return value;
  throw new AdminRequestRejected(422);
}

function readOptionalUuid(body: Record<string, unknown>, key: string): string | null {
  const value = body[key];
  if (value === undefined || value === null || value === "") return null;
  return readUuid(body, key);
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

function readOptionalInteger(
  body: Record<string, unknown>, key: string, minimum: number, maximum: number, fallback: number,
): number {
  if (body[key] === undefined || body[key] === null) return fallback;
  return readInteger(body, key, minimum, maximum);
}

function readNullableInteger(
  body: Record<string, unknown>, key: string, minimum: number, maximum: number,
): number | null {
  if (body[key] === undefined || body[key] === null) return null;
  return readInteger(body, key, minimum, maximum);
}

function readInstant(body: Record<string, unknown>, key: string): string {
  const value = readString(body, key, 40);
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp) || !value.includes("T")) throw new AdminRequestRejected(422);
  return new Date(timestamp).toISOString();
}

function readNullableInstant(body: Record<string, unknown>, key: string): string | null {
  const value = body[key];
  if (value === undefined || value === null || value === "") return null;
  return readInstant(body, key);
}

function readOptionalDate(body: Record<string, unknown>, key: string): string | null {
  const value = body[key];
  if (value === undefined || value === null || value === "") return null;
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new AdminRequestRejected(422);
  }
  return value;
}

function readQueryDate(value: string | null): string {
  if (value === null || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new AdminRequestRejected(422);
  }
  return value;
}

function readNullableCountryCode(body: Record<string, unknown>, key: string): string | null {
  const value = body[key];
  if (value === undefined || value === null || value === "") return null;
  if (typeof value !== "string" || !/^[a-z]{2}$/i.test(value)) {
    throw new AdminRequestRejected(422);
  }
  return value.toUpperCase();
}

function readNullableCurrency(body: Record<string, unknown>, key: string): string | null {
  const value = body[key];
  if (value === undefined || value === null || value === "") return null;
  if (typeof value !== "string" || !/^[a-z]{3}$/i.test(value)) {
    throw new AdminRequestRejected(422);
  }
  return value.toUpperCase();
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

async function serveArtistArtwork(
  request: Request,
  env: RakyzuApiEnv,
  artistId: string,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  const objectKey = `media/artists/${artistId}/artwork.webp`;
  const metadata = await env.MEDIA.head(objectKey);
  if (metadata === null || metadata.httpMetadata?.contentType !== "image/webp") {
    return errorResponse("artwork_not_found", "Artwork is unavailable.", 404, requestId, origin);
  }
  const headers = responseHeaders(requestId, origin);
  metadata.writeHttpMetadata(headers);
  headers.set("cache-control", ARTWORK_CACHE_CONTROL);
  headers.set("etag", metadata.httpEtag);
  headers.set("content-length", metadata.size.toString());
  if (request.headers.get("if-none-match") === metadata.httpEtag) {
    return new Response(null, { status: 304, headers });
  }
  if (request.method === "HEAD") return new Response(null, { status: 200, headers });
  const object = await env.MEDIA.get(objectKey);
  if (object === null) {
    return errorResponse("artwork_not_found", "Artwork is unavailable.", 404, requestId, origin);
  }
  return new Response(object.body, { status: 200, headers });
}

async function serveRecommendationArtwork(
  request: Request,
  env: RakyzuApiEnv,
  shelfId: string,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  const objectKey = `media/recommendations/${shelfId}/artwork.webp`;
  const metadata = await env.MEDIA.head(objectKey);
  if (metadata === null || metadata.httpMetadata?.contentType !== "image/webp") {
    return errorResponse("artwork_not_found", "Artwork is unavailable.", 404, requestId, origin);
  }
  const headers = responseHeaders(requestId, origin);
  metadata.writeHttpMetadata(headers);
  headers.set("cache-control", ARTWORK_CACHE_CONTROL);
  headers.set("etag", metadata.httpEtag);
  headers.set("content-length", metadata.size.toString());
  if (request.method === "HEAD") return new Response(null, { status: 200, headers });
  const object = await env.MEDIA.get(objectKey);
  if (object === null) return errorResponse("artwork_not_found", "Artwork is unavailable.",
    404, requestId, origin);
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
  objectKey: string,
  quality: AudioQuality,
  requestId: string,
  origin: string | null,
  offlineDownload = false,
): Promise<Response> {
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
  headers.set("x-rakyzu-content-revision", metadata.etag);
  if (offlineDownload) {
    const expires = new Date(Date.now() + OFFLINE_LICENSE_MILLIS).toISOString();
    headers.set("content-disposition", "attachment");
    headers.set("x-rakyzu-offline-allowed", "true");
    headers.set("x-rakyzu-offline-license-expires", expires);
  }
  headers.set(
    "access-control-expose-headers",
    "Accept-Ranges, Content-Length, Content-Range, ETag, X-Rakyzu-Audio-Quality, " +
      "X-Rakyzu-Content-Revision, X-Rakyzu-Offline-Allowed, " +
      "X-Rakyzu-Offline-License-Expires, X-Request-ID",
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

const OFFLINE_LICENSE_MILLIS = 30 * 24 * 60 * 60 * 1000;

function readAudioQuality(value: string | null): AudioQuality | null {
  if (value === null || value === "standard") return "standard";
  if (value === "low" || value === "high") return value;
  return null;
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
