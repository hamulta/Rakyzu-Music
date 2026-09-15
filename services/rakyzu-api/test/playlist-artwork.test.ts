import { afterEach, describe, expect, it, vi } from "vitest";
import { createWorker } from "../src/worker";
import { MAX_ARTWORK_BYTES, playlistAccess, validArtwork } from "../src/playlist-artwork";
import type { RakyzuApiEnv, RequestDependencies } from "../src/types";

const id = "90000000-0000-4000-8000-000000000001";
const userId = "77777777-7777-4777-8777-777777777777";
const png = Uint8Array.from(Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=", "base64"));
const env = { ALLOWED_ORIGINS: "https://rakyzu.my.id", SUPABASE_URL: "https://example.supabase.co",
  SUPABASE_PUBLISHABLE_KEY: "public-test-key" } as RakyzuApiEnv;

afterEach(() => vi.unstubAllGlobals());

function fixture(allowed = true, canEdit = true) {
  let bytes: Uint8Array | null = null;
  const put = vi.fn(async (_key: string, data: Uint8Array) => { bytes = data; });
  const remove = vi.fn(async () => { bytes = null; });
  const get = vi.fn(async () => bytes && ({ body: bytes, size: bytes.length, httpEtag: '"cover"', httpMetadata: { contentType: "image/png" } }));
  const dependencies: RequestDependencies = {
    verifyListener: async () => ({ userId }),
    playlistAccess: async () => allowed ? { ownerId: userId, canEdit } : null,
    canStreamTrack: async () => false, canAccessAlbumArtwork: async () => false,
    staffContext: async () => ({
      isStaff: false, role: null, displayRole: null, fullAccess: false, permissions: [],
    }),
    adminRpc: async () => ({}),
  };
  const worker = createWorker(dependencies);
  const call = (method: string, body?: Uint8Array, headers: Record<string, string> = {}) => worker.fetch!(
    new Request(`https://api.rakyzu.my.id/v1/playlists/${id}/artwork`, { method,
      headers: { authorization: "Bearer test-session", "content-type": "image/png",
        ...(body ? { "content-length": String(body.length) } : {}), ...headers }, body: body as BodyInit | undefined }),
    { ...env, MEDIA: { put, delete: remove, get } as unknown as R2Bucket }, {} as ExecutionContext);
  return { call, put, remove, get };
}

describe("private playlist artwork", () => {
  it("uploads, reads and deletes only the owner-scoped object", async () => {
    const { call, put } = fixture();
    expect((await call("PUT", png)).status).toBe(204);
    expect(put.mock.calls[0]?.[0]).toBe(`media/playlists/${userId}/${id}/artwork.png`);
    const read = await call("GET");
    expect(read.status).toBe(200);
    expect(read.headers.get("cache-control")).toBe("private, no-store");
    expect(new Uint8Array(await read.arrayBuffer())).toEqual(png);
    expect((await call("DELETE")).status).toBe(204);
    expect((await call("GET")).status).toBe(404);
  });
  it.each(["GET", "HEAD", "PUT", "DELETE"])("denies another owner before R2 %s", async (method) => {
    const f = fixture(false);
    expect((await f.call(method, method === "PUT" ? png : undefined)).status).toBe(404);
    expect(f.put).not.toHaveBeenCalled(); expect(f.get).not.toHaveBeenCalled(); expect(f.remove).not.toHaveBeenCalled();
  });
  it("denies missing authentication before writes", async () => {
    const f = fixture();
    expect((await f.call("PUT", png, { authorization: "" })).status).toBe(401);
    expect(f.put).not.toHaveBeenCalled();
  });
  it("allows collaborator reads but keeps cover writes owner-only", async () => {
    const f = fixture(true, false);
    expect((await f.call("GET")).status).toBe(404);
    expect((await f.call("PUT", png)).status).toBe(403);
    expect(f.put).not.toHaveBeenCalled();
  });
  it("rejects active content, oversized input and forged lengths", async () => {
    const f = fixture();
    expect((await f.call("PUT", png, { "content-type": "image/svg+xml" })).status).toBe(415);
    expect((await f.call("PUT", png, { "content-length": String(MAX_ARTWORK_BYTES + 1) })).status).toBe(413);
    expect((await f.call("PUT", png, { "content-length": "45" })).status).toBe(413);
    expect((await f.call("PUT", new Uint8Array(100))).status).toBe(422);
    expect(f.put).not.toHaveBeenCalled();
  });
  it("rejects oversized decoded dimensions", () => {
    expect(validArtwork(png)).toBe(true);
    const huge = png.slice(); new DataView(huge.buffer).setUint32(16, 20000);
    expect(validArtwork(huge)).toBe(false);
  });
  it("rejects forged dimensions, corrupt CRCs and incomplete chunk streams", () => {
    const badCrc = png.slice(); badCrc[29] = badCrc[29]! ^ 1;
    expect(validArtwork(badCrc)).toBe(false);
    const headerAndEndOnly = new Uint8Array([...png.slice(0, 33), ...png.slice(-12)]);
    expect(validArtwork(headerAndEndOnly)).toBe(false);
    expect(validArtwork(png.slice(0, -1))).toBe(false);
  });
  it("validates the access RPC payload and fails closed", async () => {
    const upstream = vi.fn().mockResolvedValue(Response.json({
      ownerId: userId, canRead: true, canEdit: false,
    }));
    vi.stubGlobal("fetch", upstream);
    await expect(playlistAccess(id, "session", env)).resolves.toEqual({
      ownerId: userId, canEdit: false,
    });
    const url = upstream.mock.calls[0]?.[0] as URL;
    expect(url.pathname).toBe("/rest/v1/rpc/get_playlist_artwork_access");
    upstream.mockResolvedValue(new Response(null, { status: 503 }));
    await expect(playlistAccess(id, "session", env)).rejects.toThrow();
  });
});
