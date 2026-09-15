import { describe, expect, it } from "vitest";

import { createWorker } from "../src/worker";
import { playlistAccess } from "../src/playlist-artwork";
import type { AdminRpcName, RakyzuApiEnv, RequestDependencies, StaffContext } from "../src/types";

const TRACK_ID = "a3000000-0000-4000-8000-000000000001";
const ALBUM_ID = "a2000000-0000-4000-8000-000000000001";
const PLAYLIST_ID = "a4000000-0000-4000-8000-000000000001";
const AUDIO = new TextEncoder().encode("rakyzu-audio");
const ARTWORK = new Uint8Array([0x52, 0x41, 0x4b, 0x59, 0x5a, 0x55]);

describe("Rakyzu Music API", () => {
  it("serves a public health response with security headers", async () => {
    const response = await execute("/v1/health");

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({ status: "ok", version: "0.5.20" });
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
});

interface ExecuteOptions {
  adminCalls?: Array<{ name: AdminRpcName; payload: Record<string, unknown> }>;
  adminResult?: unknown;
  body?: BodyInit;
  catalogError?: Error;
  headers?: HeadersInit;
  method?: string;
  published?: boolean;
  staff?: StaffContext;
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
    async canAccessAlbumArtwork() {
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
      return options.adminResult ?? {};
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
