import { describe, expect, it } from "vitest";

import { createWorker } from "../src/worker";
import { playlistAccess } from "../src/playlist-artwork";
import type { AdminRpcName, RakyzuApiEnv, RequestDependencies, StaffContext } from "../src/types";

const TRACK_ID = "a3000000-0000-4000-8000-000000000001";
const ALBUM_ID = "a2000000-0000-4000-8000-000000000001";
const PLAYLIST_ID = "a4000000-0000-4000-8000-000000000001";
const PROFILE_ID = "b1000000-0000-4000-8000-000000000001";
const ARTIST_ID = "b2000000-0000-4000-8000-000000000001";
const AUDIO = new TextEncoder().encode("rakyzu-audio");
const ARTWORK = new Uint8Array([0x52, 0x41, 0x4b, 0x59, 0x5a, 0x55]);

describe("Rakyzu Music API", () => {
  it("serves a public health response with security headers", async () => {
    const response = await execute("/v1/health");

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({ status: "ok", version: "0.6.5" });
    expect(response.headers.get("x-content-type-options")).toBe("nosniff");
  });

  it("requires a bearer session for media", async () => {
    const response = await execute(`/v1/tracks/${TRACK_ID}/stream`);

    expect(response.status).toBe(401);
    expect(response.headers.get("www-authenticate")).toContain("Bearer");
  });

  it("does not disclose unpublished tracks", async () => {
    const response = await execute(`/v1/tracks/${TRACK_ID}/stream`, {
      headers: { authorization: "Bearer listener-token" },
      published: false,
    });

    expect(response.status).toBe(404);
    await expect(response.json()).resolves.toMatchObject({ error: { code: "track_not_found" } });
  });

  it("streams authorized media without buffering", async () => {
    const response = await execute(`/v1/tracks/${TRACK_ID}/stream`, {
      headers: { authorization: "Bearer listener-token" },
    });

    expect(response.status).toBe(200);
    expect(response.headers.get("accept-ranges")).toBe("bytes");
    expect(response.headers.get("content-type")).toBe("audio/mpeg");
    expect(response.headers.get("x-rakyzu-audio-quality")).toBe("standard");
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(AUDIO);
  });

  it("issues a bounded offline license only after catalog authorization", async () => {
    const response = await execute(`/v1/tracks/${TRACK_ID}/download`, {
      headers: { authorization: "Bearer listener-token" },
    });

    expect(response.status).toBe(200);
    expect(response.headers.get("x-rakyzu-offline-allowed")).toBe("true");
    expect(Date.parse(response.headers.get("x-rakyzu-offline-license-expires") ?? "")).toBeGreaterThan(Date.now());
    expect(response.headers.get("content-disposition")).toBe("attachment");
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(AUDIO);
  });

  it("does not issue an offline license for unavailable catalog rights", async () => {
    const response = await execute(`/v1/tracks/${TRACK_ID}/download`, {
      headers: { authorization: "Bearer listener-token" },
      published: false,
    });

    expect(response.status).toBe(404);
    expect(response.headers.get("x-rakyzu-offline-allowed")).toBeNull();
  });

  it.each([
    ["low", "rakyzu-audio-low"],
    ["high", "rakyzu-audio-high"],
  ])("selects the exact %s media variant", async (quality, expectedBody) => {
    const response = await execute(`/v1/tracks/${TRACK_ID}/stream`, {
      headers: {
        authorization: "Bearer listener-token",
        "x-rakyzu-audio-quality": quality,
      },
    });

    expect(response.status).toBe(200);
    expect(response.headers.get("x-rakyzu-audio-quality")).toBe(quality);
    expect(await response.text()).toBe(expectedBody);
  });

  it("rejects an unknown media variant after authorization", async () => {
    const response = await execute(`/v1/tracks/${TRACK_ID}/stream`, {
      headers: {
        authorization: "Bearer listener-token",
        "x-rakyzu-audio-quality": "lossless",
      },
    });

    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toMatchObject({
      error: { code: "invalid_audio_quality" },
    });
  });

  it("returns one validated byte range", async () => {
    const response = await execute(`/v1/tracks/${TRACK_ID}/stream`, {
      headers: {
        authorization: "Bearer listener-token",
        range: "bytes=2-5",
      },
    });

    expect(response.status).toBe(206);
    expect(response.headers.get("content-range")).toBe(`bytes 2-5/${AUDIO.length}`);
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(AUDIO.slice(2, 6));
  });

  it("rejects unsatisfiable ranges without returning media", async () => {
    const response = await execute(`/v1/tracks/${TRACK_ID}/stream`, {
      headers: {
        authorization: "Bearer listener-token",
        range: `bytes=${AUDIO.length}-`,
      },
    });

    expect(response.status).toBe(416);
    expect(response.headers.get("content-range")).toBe(`bytes */${AUDIO.length}`);
  });

  it("supports metadata-only HEAD requests", async () => {
    const response = await execute(`/v1/tracks/${TRACK_ID}/stream`, {
      method: "HEAD",
      headers: { authorization: "Bearer listener-token" },
    });

    expect(response.status).toBe(200);
    expect(response.headers.get("content-length")).toBe(AUDIO.length.toString());
    expect(await response.text()).toBe("");
  });

  it("requires a bearer session for album artwork", async () => {
    const response = await execute(`/v1/albums/${ALBUM_ID}/artwork`);

    expect(response.status).toBe(401);
    expect(response.headers.get("www-authenticate")).toContain("Bearer");
  });

  it("does not disclose artwork for an unpublished album", async () => {
    const response = await execute(`/v1/albums/${ALBUM_ID}/artwork`, {
      headers: { authorization: "Bearer listener-token" },
      published: false,
    });

    expect(response.status).toBe(404);
    await expect(response.json()).resolves.toMatchObject({ error: { code: "artwork_not_found" } });
  });

  it("treats a private-playlist access denial as absent", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => new Response(null, { status: 403 });
    try {
      await expect(playlistAccess(PLAYLIST_ID, "listener-token", testEnv())).resolves.toBeNull();
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("serves authorized album artwork with bounded private caching", async () => {
    const response = await execute(`/v1/albums/${ALBUM_ID}/artwork`, {
      headers: { authorization: "Bearer listener-token" },
    });

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("image/webp");
    expect(response.headers.get("cache-control")).toBe("private, max-age=86400");
    expect(response.headers.get("etag")).toBe('"artwork-etag"');
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(ARTWORK);
  });

  it("revalidates cached album artwork with its entity tag", async () => {
    const response = await execute(`/v1/albums/${ALBUM_ID}/artwork`, {
      headers: {
        authorization: "Bearer listener-token",
        "if-none-match": '"artwork-etag"',
      },
    });

    expect(response.status).toBe(304);
    expect(await response.text()).toBe("");
  });

  it("uses a bounded JSON error for unexpected failures", async () => {
    const response = await execute(`/v1/tracks/${TRACK_ID}/stream`, {
      headers: { authorization: "Bearer listener-token" },
      catalogError: new Error("sensitive upstream detail"),
    });

    expect(response.status).toBe(500);
    expect(await response.text()).not.toContain("sensitive upstream detail");
  });

  it("rejects untrusted browser origins before authentication", async () => {
    const response = await execute(`/v1/tracks/${TRACK_ID}/stream`, {
      headers: { origin: "https://attacker.example" },
    });

    expect(response.status).toBe(403);
    expect(response.headers.get("access-control-allow-origin")).toBeNull();
  });

  it("returns a non-staff context without exposing admin tools", async () => {
    const response = await execute("/v1/admin/context", {
      headers: { authorization: "Bearer listener-token" },
    });

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      isStaff: false,
      role: null,
      displayRole: null,
      fullAccess: false,
      permissions: [],
    });
  });

  it("blocks an officer from catalog creation at the Worker boundary", async () => {
    const response = await execute("/v1/admin/artists", {
      method: "POST",
      headers: {
        authorization: "Bearer officer-token",
        "content-type": "application/json",
      },
      body: JSON.stringify({ name: "Unauthorized draft" }),
      staff: staffContext("officer", ["admin.access", "moderation.view", "moderation.triage"]),
    });

    expect(response.status).toBe(403);
    await expect(response.json()).resolves.toMatchObject({ error: { code: "forbidden" } });
  });

  it("allows a manager to create a validated artist draft", async () => {
    const response = await execute("/v1/admin/artists", {
      method: "POST",
      headers: {
        authorization: "Bearer manager-token",
        "content-type": "application/json",
      },
      body: JSON.stringify({ name: "Rakyzu Original" }),
      staff: staffContext("manager", ["admin.access", "catalog.draft"]),
      adminResult: { id: "d1000000-0000-4000-8000-000000000001", name: "Rakyzu Original" },
    });

    expect(response.status).toBe(201);
    await expect(response.json()).resolves.toMatchObject({ name: "Rakyzu Original" });
  });

  it("passes an optional exact Artist email to the protected create RPC", async () => {
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute("/v1/admin/artists", {
      method: "POST",
      headers: { authorization: "Bearer manager-token", "content-type": "application/json" },
      body: JSON.stringify({ name: "New Artist", email: "Artist@Example.Test" }),
      staff: staffContext("manager", ["admin.access", "catalog.draft"]),
      adminCalls: calls,
    });
    expect(response.status).toBe(201);
    expect(calls).toEqual([{ name: "admin_create_artist", payload: {
      artist_name: "New Artist", artist_email: "Artist@Example.Test",
    } }]);
  });

  it("restricts editing another listener's profile image", async () => {
    const response = await execute(`/v1/profiles/${TRACK_ID}/avatar`, {
      method: "DELETE",
      headers: { authorization: "Bearer listener-token" },
    });
    expect(response.status).toBe(403);
  });

  it("allows a listener to upload a bounded WebP profile image", async () => {
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const webp = new Uint8Array([0x52, 0x49, 0x46, 0x46, 4, 0, 0, 0,
      0x57, 0x45, 0x42, 0x50]);
    const response = await execute(`/v1/profiles/${PROFILE_ID}/avatar`, {
      method: "PUT",
      headers: { authorization: "Bearer listener-token", "content-type": "image/webp",
        "content-length": String(webp.length) },
      body: webp,
      adminCalls: calls,
    });
    expect(response.status).toBe(204);
    expect(calls[0]?.name).toBe("record_profile_avatar");
  });

  it("forwards Artist biography changes to the self-scoped RPC", async () => {
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute("/v1/artists/me/biography", {
      method: "PUT",
      headers: { authorization: "Bearer artist-token", "content-type": "application/json" },
      body: JSON.stringify({ biography: "Original music from Rakyzu." }),
      adminCalls: calls,
    });
    expect(response.status).toBe(200);
    expect(calls).toEqual([{ name: "artist_update_biography",
      payload: { requested_biography: "Original music from Rakyzu." } }]);
  });

  it("saves an editorial recommendation through the scoped permission", async () => {
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute("/v1/admin/recommendations", {
      method: "POST",
      headers: { authorization: "Bearer officer-token", "content-type": "application/json" },
      body: JSON.stringify({ title: "Today", position: 0, trackId: TRACK_ID, published: true }),
      staff: staffContext("officer", ["admin.access", "editorial.manage"]),
      adminCalls: calls,
    });
    expect(response.status).toBe(201);
    expect(calls[0]).toMatchObject({ name: "admin_upsert_editorial_shelf",
      payload: { shelf_title: "Today", shelf_position: 0, target_track_id: TRACK_ID } });
  });

  it("blocks a recommendation position outside the database constraint", async () => {
    const response = await execute("/v1/admin/recommendations", {
      method: "POST",
      headers: { authorization: "Bearer officer-token", "content-type": "application/json" },
      body: JSON.stringify({ title: "Today", position: 1001, published: true }),
      staff: staffContext("officer", ["admin.access", "editorial.manage"]),
    });
    expect(response.status).toBe(422);
  });

  it("assigns a lower role through an exact account email", async () => {
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute("/v1/admin/staff", {
      method: "POST",
      headers: {
        authorization: "Bearer manager-token",
        "content-type": "application/json",
      },
      body: JSON.stringify({ email: "officer@example.test", role: "officer", active: true }),
      staff: staffContext("manager", ["admin.access", "staff.manage"]),
      adminCalls: calls,
      adminResult: { role: "officer", active: true },
    });

    expect(response.status).toBe(200);
    expect(calls).toEqual([{
      name: "admin_assign_staff_by_email",
      payload: {
        target_email: "officer@example.test",
        target_role: "officer",
        target_active: true,
      },
    }]);
  });

  it("allows C-Level audio upload only for bounded MP3 media", async () => {
    const mp3 = new Uint8Array([0x49, 0x44, 0x33, 0x04, 0x00, 0x00]);
    const response = await execute(`/v1/admin/tracks/${TRACK_ID}/audio/standard`, {
      method: "PUT",
      headers: {
        authorization: "Bearer executive-token",
        "content-type": "audio/mpeg",
        "content-length": String(mp3.length),
      },
      body: mp3,
      staff: staffContext("c_level_executive", ["admin.access", "catalog.upload_audio"]),
      adminResult: { trackId: TRACK_ID, quality: "standard", sizeBytes: mp3.length },
    });

    expect(response.status).toBe(201);
    await expect(response.json()).resolves.toMatchObject({
      trackId: TRACK_ID,
      quality: "standard",
      sizeBytes: mp3.length,
    });
  });

  it.each([
    ["audio/aac", new Uint8Array([0xff, 0xf1, 0x50, 0x80]), "aac"],
    ["audio/mp4", new Uint8Array([0, 0, 0, 12, 0x66, 0x74, 0x79, 0x70, 0, 0, 0, 0]), "m4a"],
    ["audio/webm", new Uint8Array([0x1a, 0x45, 0xdf, 0xa3]), "webm"],
    ["audio/wav", new Uint8Array([0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0,
      0x57, 0x41, 0x56, 0x45]), "wav"],
    ["audio/flac", new Uint8Array([0x66, 0x4c, 0x61, 0x43]), "flac"],
  ])("accepts validated %s uploads", async (contentType, bytes, format) => {
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute(`/v1/admin/tracks/${TRACK_ID}/audio/standard`, {
      method: "PUT",
      headers: { authorization: "Bearer executive-token", "content-type": contentType,
        "content-length": String(bytes.length) },
      body: bytes,
      staff: staffContext("c_level_executive", ["admin.access", "catalog.upload_audio"]),
      adminCalls: calls,
    });
    expect(response.status).toBe(201);
    expect(calls[0]).toMatchObject({ name: "admin_record_track_media",
      payload: { media_format: format, media_content_type: contentType } });
  });

  it("rejects arbitrary bytes before writing an audio object", async () => {
    const bytes = new Uint8Array([0x52, 0x41, 0x4b, 0x59]);
    const response = await execute(`/v1/admin/tracks/${TRACK_ID}/audio/high`, {
      method: "PUT",
      headers: {
        authorization: "Bearer executive-token",
        "content-type": "audio/mpeg",
        "content-length": String(bytes.length),
      },
      body: bytes,
      staff: staffContext("c_level_executive", ["admin.access", "catalog.upload_audio"]),
    });

    expect(response.status).toBe(422);
    await expect(response.json()).resolves.toMatchObject({ error: { code: "invalid_audio" } });
  });

  it("records a reasoned reversible enforcement action", async () => {
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute("/v1/admin/enforcement", {
      method: "POST",
      headers: { authorization: "Bearer supervisor-token", "content-type": "application/json" },
      body: JSON.stringify({
        subjectType: "track",
        subjectId: TRACK_ID,
        action: "quarantine",
        reason: "Rights review pending",
        caseId: null,
      }),
      staff: staffContext("supervisor", ["admin.access", "content.enforce"]),
      adminCalls: calls,
      adminResult: { subjectId: TRACK_ID, action: "quarantine" },
    });

    expect(response.status).toBe(201);
    expect(calls).toEqual([{
      name: "admin_apply_content_enforcement",
      payload: {
        target_subject_type: "track",
        target_subject_id: TRACK_ID,
        enforcement_action: "quarantine",
        enforcement_reason: "Rights review pending",
        target_case_id: null,
      },
    }]);
  });

  it("does not widen officers into content enforcement", async () => {
    const response = await execute("/v1/admin/enforcement", {
      method: "POST",
      headers: { authorization: "Bearer officer-token", "content-type": "application/json" },
      body: JSON.stringify({
        subjectType: "track", subjectId: TRACK_ID, action: "take_down", reason: "Policy breach",
      }),
      staff: staffContext("officer", ["admin.access", "moderation.triage"]),
    });

    expect(response.status).toBe(403);
  });

  it("assigns an exact-email artist team member without staff authority", async () => {
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute("/v1/admin/catalog/teams", {
      method: "POST",
      headers: { authorization: "Bearer manager-token", "content-type": "application/json" },
      body: JSON.stringify({
        scopeType: "artist", scopeId: TRACK_ID, email: "Artist@Example.Test",
        accessLevel: "editor", active: true,
      }),
      staff: staffContext("manager", ["admin.access", "catalog.team_manage"]),
      adminCalls: calls,
    });

    expect(response.status).toBe(200);
    expect(calls[0]).toMatchObject({
      name: "admin_assign_catalog_team_by_email",
      payload: { target_email: "artist@example.test", target_access_level: "editor" },
    });
  });

  it("uploads only bounded WebP artwork to the exact private key", async () => {
    const webp = new Uint8Array([
      0x52, 0x49, 0x46, 0x46, 0x04, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50,
    ]);
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute(`/v1/admin/albums/${ALBUM_ID}/artwork`, {
      method: "PUT",
      headers: {
        authorization: "Bearer executive-token",
        "content-type": "image/webp",
        "content-length": String(webp.length),
      },
      body: webp,
      staff: staffContext("c_level_executive", ["admin.access", "catalog.upload_artwork"]),
      adminCalls: calls,
      adminResult: { albumId: ALBUM_ID, sizeBytes: webp.length },
    });

    expect(response.status).toBe(201);
    expect(calls[0]).toMatchObject({
      name: "admin_record_album_artwork",
      payload: {
        target_album_id: ALBUM_ID,
        media_object_key: `media/albums/${ALBUM_ID}/artwork.webp`,
        media_content_type: "image/webp",
      },
    });
  });

  it("routes independent catalog review decisions through review permission", async () => {
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute(`/v1/admin/reviews/${TRACK_ID}`, {
      method: "PATCH",
      headers: { authorization: "Bearer executive-token", "content-type": "application/json" },
      body: JSON.stringify({ decision: "approved", notes: "Independent review passed" }),
      staff: staffContext("c_level_executive", ["admin.access", "catalog.review"]),
      adminCalls: calls,
    });

    expect(response.status).toBe(200);
    expect(calls[0]).toMatchObject({
      name: "admin_decide_catalog_review",
      payload: { review_decision: "approved", review_notes: "Independent review passed" },
    });
  });

  it("normalizes a validated scheduled publication instant", async () => {
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute(`/v1/admin/albums/${ALBUM_ID}/schedule`, {
      method: "POST",
      headers: { authorization: "Bearer executive-token", "content-type": "application/json" },
      body: JSON.stringify({ publishAt: "2026-10-01T07:00:00+07:00" }),
      staff: staffContext("c_level_executive", ["admin.access", "catalog.publish"]),
      adminCalls: calls,
    });

    expect(response.status).toBe(200);
    expect(calls[0]?.payload.requested_publish_at).toBe("2026-10-01T00:00:00.000Z");
  });

  it("exports only bounded privacy-safe audit fields for authorized executives", async () => {
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute("/v1/admin/audit/export", {
      method: "POST",
      headers: { authorization: "Bearer executive-token", "content-type": "application/json" },
      body: JSON.stringify({ operation: null, targetType: "album", before: null, pageSize: 200 }),
      staff: staffContext("c_level_executive", ["admin.access", "audit.export"]),
      adminCalls: calls,
      adminResult: [],
    });

    expect(response.status).toBe(200);
    expect(calls[0]).toEqual({
      name: "admin_export_audit",
      payload: {
        operation_filter: null,
        target_type_filter: "album",
        before_time: null,
        page_size: 200,
      },
    });
  });

  it("reserves retention governance for CEO permission", async () => {
    const response = await execute("/v1/admin/audit/retention", {
      method: "POST",
      headers: { authorization: "Bearer executive-token", "content-type": "application/json" },
      body: JSON.stringify({ retentionDays: 730 }),
      staff: staffContext("c_level_executive", ["admin.access", "audit.export"]),
    });

    expect(response.status).toBe(403);
  });

  it("requires authentication for the Artist workspace", async () => {
    const response = await execute("/v1/artists/me/workspace");
    expect(response.status).toBe(401);
  });

  it("routes Artist drafts to the scoped database RPC, not staff permissions", async () => {
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute("/v1/artists/me/albums", {
      method: "POST",
      headers: { authorization: "Bearer artist-token", "content-type": "application/json" },
      body: JSON.stringify({ artistId: ARTIST_ID, title: "Artist Draft", releaseDate: null }),
      adminCalls: calls,
      rpcResults: { artist_create_album_draft: { id: ALBUM_ID, title: "Artist Draft" } },
    });
    expect(response.status).toBe(201);
    expect(calls).toEqual([{ name: "artist_create_album_draft", payload: {
      target_artist_id: ARTIST_ID, album_title: "Artist Draft", album_release_date: null,
    } }]);
  });

  it("blocks Artist media uploads before writing R2 when scoped edit is denied", async () => {
    const calls: Array<{ name: AdminRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute(`/v1/artist/albums/${ALBUM_ID}/artwork`, {
      method: "PUT",
      headers: {
        authorization: "Bearer listener-token", "content-type": "image/webp", "content-length": "12",
      },
      body: new Uint8Array([0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50]),
      adminCalls: calls,
      rpcResults: { artist_can_edit_album: false },
    });
    expect(response.status).toBe(403);
    expect(calls).toEqual([{ name: "artist_can_edit_album", payload: { target_album_id: ALBUM_ID } }]);
  });

  it("serves Artist artwork only after scoped authorization", async () => {
    const denied = await execute(`/v1/artists/${ARTIST_ID}/artwork`, {
      headers: { authorization: "Bearer listener-token" },
      rpcResults: { artist_can_view_artwork: false },
    });
    expect(denied.status).toBe(404);
    const allowed = await execute(`/v1/artists/${ARTIST_ID}/artwork`, {
      method: "HEAD", headers: { authorization: "Bearer artist-token" },
      rpcResults: { artist_can_view_artwork: true },
    });
    expect(allowed.status).toBe(200);
    expect(allowed.headers.get("content-type")).toBe("image/webp");
  });

  it("accepts only signed, bounded trusted play events", async () => {
    const body = JSON.stringify({ eventId: "play-event-00000001", userId: PROFILE_ID,
      trackId: TRACK_ID, occurredAt: new Date().toISOString(), listeningMs: 30_000,
      completed: true, countryCode: "id" });
    const calls: Array<{ name: TestRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute("/v1/events/play", {
      method: "POST", body, adminCalls: calls,
      headers: await signedHeaders(body, "play-event-test-secret-at-least-32-bytes"),
      adminResult: { accepted: true },
    });
    expect(response.status).toBe(202);
    expect(calls[0]?.name).toBe("service_record_play_event");
    expect(calls[0]?.payload).toMatchObject({ requested_track_id: TRACK_ID,
      requested_user_id: PROFILE_ID, requested_country_code: "ID" });

    const denied = await execute("/v1/events/play", {
      method: "POST", body, headers: { "content-type": "application/json",
        "x-rakyzu-timestamp": Math.floor(Date.now() / 1000).toString(),
        "x-rakyzu-signature": "0".repeat(64) },
    });
    expect(denied.status).toBe(401);
  });

  it("verifies commerce webhook signatures before the service-role RPC", async () => {
    const body = JSON.stringify({ provider: "rakyzu_test", eventId: "payment-event-0001",
      type: "purchase_completed", userId: PROFILE_ID, customerReference: "cus_123456",
      productReference: "premium_monthly", amountMinor: 49000, currency: "idr",
      occurredAt: new Date().toISOString() });
    const calls: Array<{ name: TestRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute("/v1/webhooks/commerce", {
      method: "POST", body, adminCalls: calls,
      headers: await signedHeaders(body, "commerce-test-secret-at-least-32-bytes"),
      adminResult: { accepted: true },
    });
    expect(response.status).toBe(202);
    expect(calls[0]?.name).toBe("service_ingest_commerce_event");
    expect(calls[0]?.payload).toMatchObject({ requested_currency: "IDR",
      requested_amount_minor: 49000 });
    expect(calls[0]?.payload.requested_payload_sha256).toMatch(/^[a-f0-9]{64}$/);
  });

  it("routes privacy-scoped Artist analytics", async () => {
    const calls: Array<{ name: TestRpcName; payload: Record<string, unknown> }> = [];
    const response = await execute(`/v1/artists/${ARTIST_ID}/analytics?from=2026-09-01&to=2026-09-23`, {
      headers: { authorization: "Bearer artist-token" }, adminCalls: calls,
      adminResult: { privacyThresholdMet: false },
    });
    expect(response.status).toBe(200);
    expect(calls[0]).toEqual({ name: "artist_analytics", payload: {
      target_artist_id: ARTIST_ID, range_start: "2026-09-01", range_end: "2026-09-23",
    } });
  });

  it("enforces commerce and account governance permissions", async () => {
    const commerceDenied = await execute("/v1/admin/commerce", {
      headers: { authorization: "Bearer officer-token" },
      staff: staffContext("officer", ["admin.access"]),
    });
    expect(commerceDenied.status).toBe(403);

    const calls: Array<{ name: TestRpcName; payload: Record<string, unknown> }> = [];
    const enforced = await execute(`/v1/admin/accounts/${PROFILE_ID}/enforcement`, {
      method: "POST", headers: { authorization: "Bearer manager-token",
        "content-type": "application/json" },
      body: JSON.stringify({ action: "suspend", reason: "Repeated verified policy violations",
        expiresAt: "2026-10-01T00:00:00.000Z" }),
      staff: staffContext("manager", ["admin.access", "account.enforce"]),
      adminCalls: calls,
    });
    expect(enforced.status).toBe(201);
    expect(calls[0]?.name).toBe("admin_enforce_account");
  });

  it("keeps deletion approval behind the dedicated executive permission", async () => {
    const requestId = "c1000000-0000-4000-8000-000000000001";
    const denied = await execute(`/v1/admin/deletions/${requestId}/approval`, {
      method: "POST", headers: { authorization: "Bearer manager-token" },
      staff: staffContext("manager", ["admin.access", "account.enforce"]),
    });
    expect(denied.status).toBe(403);
  });
});

interface ExecuteOptions {
  adminCalls?: Array<{ name: TestRpcName; payload: Record<string, unknown> }>;
  adminResult?: unknown;
  body?: BodyInit;
  catalogError?: Error;
  headers?: HeadersInit;
  method?: string;
  published?: boolean;
  rpcResults?: Partial<Record<TestRpcName, unknown>>;
  staff?: StaffContext;
}

type TestRpcName = AdminRpcName | "service_record_play_event" | "service_ingest_commerce_event";

async function signedHeaders(body: string, secret: string): Promise<HeadersInit> {
  const timestamp = Math.floor(Date.now() / 1000).toString();
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const bytes = new Uint8Array(await crypto.subtle.sign("HMAC", key,
    new TextEncoder().encode(`${timestamp}.${body}`)));
  const signature = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
  return { "content-type": "application/json", "x-rakyzu-timestamp": timestamp,
    "x-rakyzu-signature": signature };
}

async function execute(path: string, options: ExecuteOptions = {}): Promise<Response> {
  const media = new FakeR2Bucket(AUDIO);
  const dependencies: RequestDependencies = {
    async playlistAccess() {
      return options.published === false ? null : {
        ownerId: "b1000000-0000-4000-8000-000000000001",
        canEdit: true,
      };
    },
    async verifyListener() {
      return { userId: "b1000000-0000-4000-8000-000000000001" };
    },
    async canStreamTrack() {
      if (options.catalogError) throw options.catalogError;
      return options.published ?? true;
    },
    async resolveTrackMediaKey(trackId, quality) {
      if (options.catalogError) throw options.catalogError;
      const stem = quality === "low" ? "source-low" : quality === "high" ? "source-high" : "source";
      return `media/tracks/${trackId}/${stem}.mp3`;
    },
    async canAccessAlbumArtwork() {
      if (options.catalogError) throw options.catalogError;
      return options.published ?? true;
    },
    async canAccessEditorialArtwork() {
      if (options.catalogError) throw options.catalogError;
      return options.published ?? true;
    },
    async staffContext() {
      return options.staff ?? {
        isStaff: false,
        role: null,
        displayRole: null,
        fullAccess: false,
        permissions: [],
      };
    },
    async adminRpc(name, payload) {
      options.adminCalls?.push({ name, payload });
      return options.rpcResults?.[name] ?? options.adminResult ?? {};
    },
    async serviceRpc(name, payload) {
      options.adminCalls?.push({ name, payload });
      return options.rpcResults?.[name] ?? options.adminResult ?? {};
    },
  };
  const worker = createWorker(dependencies);
  const env = { ...testEnv(), MEDIA: media as unknown as R2Bucket } satisfies RakyzuApiEnv;
  return worker.fetch!(
    new Request(`https://api.rakyzu.my.id${path}`, {
      headers: options.headers,
      method: options.method,
      body: options.body,
    }),
    env,
    {} as ExecutionContext,
  );
}

function testEnv(): RakyzuApiEnv {
  return {
    MEDIA: {} as R2Bucket,
    ALLOWED_ORIGINS: "https://rakyzu.my.id",
    SUPABASE_URL: "https://example.supabase.co",
    SUPABASE_PUBLISHABLE_KEY: "public-test-key",
    SUPABASE_SERVICE_ROLE_KEY: "service-test-key",
    PLAY_EVENT_WEBHOOK_SECRET: "play-event-test-secret-at-least-32-bytes",
    COMMERCE_WEBHOOK_SECRET: "commerce-test-secret-at-least-32-bytes",
  };
}

class FakeR2Bucket {
  constructor(private readonly audioBytes: Uint8Array) {}

  async head(key: string): Promise<R2Object> {
    return this.metadata(key) as R2Object;
  }

  async get(key: string, options?: R2GetOptions): Promise<R2ObjectBody> {
    const bytes = this.bytes(key);
    const range = options?.range as { offset?: number; length?: number } | undefined;
    const offset = range?.offset ?? 0;
    const length = range?.length ?? bytes.length;
    const body = bytes.slice(offset, offset + length);
    return {
      ...this.metadata(key),
      body: new Blob([body]).stream(),
      bodyUsed: false,
      arrayBuffer: async () => body.buffer,
      blob: async () => new Blob([body]),
      json: async <T>() => JSON.parse(new TextDecoder().decode(body)) as T,
      text: async () => new TextDecoder().decode(body),
    } as R2ObjectBody;
  }

  async put(key: string, value: ReadableStream | ArrayBuffer | ArrayBufferView | string | Blob): Promise<R2Object> {
    void value;
    return this.metadata(key) as R2Object;
  }

  async delete(_key: string): Promise<void> {}

  private bytes(key: string): Uint8Array {
    if (key.endsWith("/artwork.webp")) return ARTWORK;
    if (key.endsWith("/source-low.mp3")) {
      return new TextEncoder().encode("rakyzu-audio-low");
    }
    if (key.endsWith("/source-high.mp3")) {
      return new TextEncoder().encode("rakyzu-audio-high");
    }
    return this.audioBytes;
  }

  private metadata(key: string) {
    const isArtwork = key.endsWith("/artwork.webp");
    const bytes = this.bytes(key);
    return {
      key,
      version: "1",
      size: bytes.length,
      etag: isArtwork ? "artwork-etag" : "audio-etag",
      httpEtag: isArtwork ? '"artwork-etag"' : '"audio-etag"',
      uploaded: new Date(0),
      httpMetadata: { contentType: isArtwork ? "image/webp" : "audio/mpeg" },
      customMetadata: {},
      checksums: {},
      storageClass: "Standard",
      writeHttpMetadata(headers: Headers) {
        headers.set("content-type", isArtwork ? "image/webp" : "audio/mpeg");
      },
    };
  }
}

function staffContext(role: string, permissions: string[]): StaffContext {
  return {
    isStaff: true,
    role,
    displayRole: role === "c_level_executive" ? "C-Level Executive" :
      role.charAt(0).toUpperCase() + role.slice(1),
    fullAccess: role === "ceo",
    permissions,
  };
}
