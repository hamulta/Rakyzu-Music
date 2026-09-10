import { errorResponse, responseHeaders } from "./responses";
import type { RakyzuApiEnv } from "./types";

export const MAX_ARTWORK_BYTES = 1024 * 1024;

const PNG_SIGNATURE = [137, 80, 78, 71, 13, 10, 26, 10];
const PNG_CRC_TABLE = new Uint32Array(256).map((_, index) => {
  let value = index;
  for (let bit = 0; bit < 8; bit += 1) value = (value >>> 1) ^ (value & 1 ? 0xedb88320 : 0);
  return value >>> 0;
});

function validChunkCrc(bytes: Uint8Array, typeOffset: number, dataEnd: number, expected: number): boolean {
  let crc = 0xffffffff;
  for (let index = typeOffset; index < dataEnd; index += 1) {
    crc = PNG_CRC_TABLE[(crc ^ bytes[index]!) & 0xff]! ^ (crc >>> 8);
  }
  return ((crc ^ 0xffffffff) >>> 0) === expected;
}

/** Accept only bounded raster PNGs. Never accept active SVG/HTML or arbitrary object keys. */
export function validArtwork(bytes: Uint8Array): boolean {
  if (bytes.length < 45 || bytes.length > MAX_ARTWORK_BYTES ||
    !PNG_SIGNATURE.every((value, index) => bytes[index] === value)) return false;
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let offset = 8;
  let sawHeader = false;
  let sawImageData = false;
  while (offset + 12 <= bytes.length) {
    const length = view.getUint32(offset);
    const typeOffset = offset + 4;
    const dataOffset = typeOffset + 4;
    const dataEnd = dataOffset + length;
    const next = dataEnd + 4;
    if (dataEnd < dataOffset || next > bytes.length) return false;
    const type = String.fromCharCode(...bytes.subarray(typeOffset, dataOffset));
    if (!/^[A-Za-z]{4}$/.test(type) ||
      !validChunkCrc(bytes, typeOffset, dataEnd, view.getUint32(dataEnd))) return false;
    if (!sawHeader) {
      if (type !== "IHDR" || length !== 13) return false;
      const width = view.getUint32(dataOffset);
      const height = view.getUint32(dataOffset + 4);
      const bitDepth = bytes[dataOffset + 8]!;
      const colorType = bytes[dataOffset + 9]!;
      const allowedDepths: Record<number, readonly number[]> = {
        0: [1, 2, 4, 8, 16], 2: [8, 16], 3: [1, 2, 4, 8], 4: [8, 16], 6: [8, 16],
      };
      if (width < 1 || width > 1024 || height < 1 || height > 1024 ||
        !allowedDepths[colorType]?.includes(bitDepth) || bytes[dataOffset + 10] !== 0 ||
        bytes[dataOffset + 11] !== 0 || ![0, 1].includes(bytes[dataOffset + 12]!)) return false;
      sawHeader = true;
    } else if (type === "IHDR") return false;
    if (type === "IDAT") sawImageData = true;
    if (type === "IEND") return length === 0 && sawImageData && next === bytes.length;
    offset = next;
  }
  return false;
}

export async function playlistAccess(
  id: string,
  token: string,
  env: RakyzuApiEnv,
): Promise<{ ownerId: string; canEdit: boolean } | null> {
  const url = new URL("/rest/v1/rpc/get_playlist_artwork_access", env.SUPABASE_URL);
  const response = await fetch(url, {
    method: "POST",
    headers: {
      apikey: env.SUPABASE_PUBLISHABLE_KEY,
      authorization: `Bearer ${token}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ playlist_id: id }),
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) throw new Error("Playlist authorization unavailable");
  const value: unknown = await response.json();
  if (typeof value !== "object" || value === null ||
    !("ownerId" in value) || typeof value.ownerId !== "string" ||
    !("canRead" in value) || value.canRead !== true ||
    !("canEdit" in value) || typeof value.canEdit !== "boolean") {
    throw new Error("Invalid authorization response");
  }
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
    .test(value.ownerId) ? { ownerId: value.ownerId, canEdit: value.canEdit } : null;
}

export async function playlistArtwork(
  request: Request, env: RakyzuApiEnv, id: string, ownerId: string,
  requestId: string, origin: string | null,
): Promise<Response> {
  const key = `media/playlists/${ownerId}/${id}/artwork.png`;
  const headers = responseHeaders(requestId, origin);
  // Authorization is checked on every read, including after account changes or deletion.
  headers.set("cache-control", "private, no-store");
  if (request.method === "DELETE") {
    await env.MEDIA.delete(key);
    return new Response(null, { status: 204, headers });
  }
  if (request.method === "PUT") {
    if (request.headers.get("content-type") !== "image/png") {
      return errorResponse("invalid_artwork", "Use a PNG cover.", 415, requestId, origin);
    }
    const length = Number(request.headers.get("content-length"));
    if (!Number.isInteger(length) || length < 45 || length > MAX_ARTWORK_BYTES || !request.body) {
      return errorResponse("artwork_too_large", "Cover must be at most 1 MiB.", 413, requestId, origin);
    }
    const reader = request.body.getReader();
    const bytes = new Uint8Array(length);
    let offset = 0;
    try {
      while (true) {
        const chunk = await reader.read();
        if (chunk.done) break;
        if (offset + chunk.value.length > length) {
          await reader.cancel();
          return errorResponse("artwork_too_large", "Invalid cover length.", 413, requestId, origin);
        }
        bytes.set(chunk.value, offset); offset += chunk.value.length;
      }
    } finally { reader.releaseLock(); }
    if (offset !== length || !validArtwork(bytes)) {
      return errorResponse("invalid_artwork", "Use a PNG cover up to 1024 by 1024 pixels.", 422, requestId, origin);
    }
    await env.MEDIA.put(key, bytes, { httpMetadata: { contentType: "image/png", cacheControl: "private, no-store" } });
    return new Response(null, { status: 204, headers });
  }
  const object = await env.MEDIA.get(key);
  if (!object || object.size > MAX_ARTWORK_BYTES || object.httpMetadata?.contentType !== "image/png") {
    return errorResponse("artwork_not_found", "Cover unavailable.", 404, requestId, origin);
  }
  headers.set("content-type", "image/png");
  headers.set("content-length", String(object.size));
  headers.set("etag", object.httpEtag);
  return new Response(request.method === "HEAD" ? null : object.body, { status: 200, headers });
}
