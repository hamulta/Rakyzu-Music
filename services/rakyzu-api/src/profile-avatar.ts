import { errorResponse, responseHeaders } from "./responses";
import type { RakyzuApiEnv, RequestDependencies } from "./types";

const MAX_AVATAR_BYTES = 5 * 1024 * 1024;

export async function profileAvatar(
  request: Request,
  env: RakyzuApiEnv,
  profileId: string,
  token: string,
  dependencies: RequestDependencies,
  requestId: string,
  origin: string | null,
): Promise<Response> {
  const key = `media/profiles/${profileId}/avatar.webp`;
  if (request.method === "PUT") {
    if (request.headers.get("content-type")?.split(";", 1)[0]?.trim() !== "image/webp") {
      return errorResponse("invalid_avatar", "Upload a WebP profile image.", 415, requestId, origin);
    }
    const length = Number(request.headers.get("content-length"));
    if (!Number.isInteger(length) || length < 12 || length > MAX_AVATAR_BYTES) {
      return errorResponse("invalid_avatar_size", "Profile image must be at most 5 MiB.", 413, requestId, origin);
    }
    const bytes = await readBoundedBody(request, length);
    if (bytes === null || !isWebp(bytes)) {
      return errorResponse("invalid_avatar", "Profile image is not valid WebP.", 422, requestId, origin);
    }
    const object = await env.MEDIA.put(key, bytes, {
      httpMetadata: { contentType: "image/webp", cacheControl: "private, no-store" },
    });
    try {
      await dependencies.adminRpc("record_profile_avatar", {
        media_object_key: key,
        media_size_bytes: bytes.length,
        media_content_type: "image/webp",
        media_etag: object.etag,
      }, token, env);
      return new Response(null, { status: 204, headers: responseHeaders(requestId, origin) });
    } catch (error) {
      throw error;
    }
  }
  if (request.method === "DELETE") {
    await dependencies.adminRpc("delete_profile_avatar", {}, token, env);
    await env.MEDIA.delete(key);
    return new Response(null, { status: 204, headers: responseHeaders(requestId, origin) });
  }
  const metadata = await env.MEDIA.head(key);
  if (metadata === null) return errorResponse("avatar_not_found", "Profile image is unavailable.", 404, requestId, origin);
  const headers = responseHeaders(requestId, origin);
  metadata.writeHttpMetadata(headers);
  headers.set("cache-control", "private, no-store");
  headers.set("etag", metadata.httpEtag);
  headers.set("content-length", metadata.size.toString());
  if (request.method === "HEAD") return new Response(null, { status: 200, headers });
  const object = await env.MEDIA.get(key);
  if (object === null) return errorResponse("avatar_not_found", "Profile image is unavailable.", 404, requestId, origin);
  return new Response(object.body, { status: 200, headers });
}

function isWebp(bytes: Uint8Array): boolean {
  return bytes.length >= 12 && bytes[0] === 0x52 && bytes[1] === 0x49 &&
    bytes[2] === 0x46 && bytes[3] === 0x46 && bytes[8] === 0x57 &&
    bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50;
}

async function readBoundedBody(request: Request, claimedLength: number): Promise<Uint8Array | null> {
  if (request.body === null) return null;
  const reader = request.body.getReader();
  const bytes = new Uint8Array(claimedLength);
  let offset = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (offset + value.length > claimedLength) {
        await reader.cancel();
        return null;
      }
      bytes.set(value, offset);
      offset += value.length;
    }
    return offset === claimedLength ? bytes : null;
  } finally {
    reader.releaseLock();
  }
}
