import { describe, expect, it } from "vitest";

import { createWorker } from "../src/worker";
import type { RakyzuApiEnv, RequestDependencies } from "../src/types";

const TRACK_ID = "a3000000-0000-4000-8000-000000000001";
const AUDIO = new TextEncoder().encode("rakyzu-audio");

describe("Rakyzu Music API", () => {
  it("serves a public health response with security headers", async () => {
    const response = await execute("/v1/health");

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({ status: "ok", version: "0.0.7" });
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
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(AUDIO);
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
});

interface ExecuteOptions {
  catalogError?: Error;
  headers?: HeadersInit;
  method?: string;
  published?: boolean;
}

async function execute(path: string, options: ExecuteOptions = {}): Promise<Response> {
  const media = new FakeR2Bucket(AUDIO);
  const dependencies: RequestDependencies = {
    async verifyListener() {
      return { userId: "b1000000-0000-4000-8000-000000000001" };
    },
    async canStreamTrack() {
      if (options.catalogError) throw options.catalogError;
      return options.published ?? true;
    },
  };
  const worker = createWorker(dependencies);
  const env = {
    MEDIA: media as unknown as R2Bucket,
    ALLOWED_ORIGINS: "https://rakyzu.my.id",
    SUPABASE_URL: "https://example.supabase.co",
    SUPABASE_PUBLISHABLE_KEY: "public-test-key",
  } satisfies RakyzuApiEnv;
  return worker.fetch!(
    new Request(`https://api.rakyzu.my.id${path}`, {
      headers: options.headers,
      method: options.method,
    }),
    env,
    {} as ExecutionContext,
  );
}

class FakeR2Bucket {
  constructor(private readonly bytes: Uint8Array) {}

  async head(): Promise<R2Object> {
    return this.metadata() as R2Object;
  }

  async get(_key: string, options?: R2GetOptions): Promise<R2ObjectBody> {
    const range = options?.range as { offset?: number; length?: number } | undefined;
    const offset = range?.offset ?? 0;
    const length = range?.length ?? this.bytes.length;
    const body = this.bytes.slice(offset, offset + length);
    return {
      ...this.metadata(),
      body: new Blob([body]).stream(),
      bodyUsed: false,
      arrayBuffer: async () => body.buffer,
      blob: async () => new Blob([body]),
      json: async <T>() => JSON.parse(new TextDecoder().decode(body)) as T,
      text: async () => new TextDecoder().decode(body),
    } as R2ObjectBody;
  }

  private metadata() {
    return {
      key: "media",
      version: "1",
      size: this.bytes.length,
      etag: "etag",
      httpEtag: '"etag"',
      uploaded: new Date(0),
      httpMetadata: { contentType: "audio/mpeg" },
      customMetadata: {},
      checksums: {},
      storageClass: "Standard",
      writeHttpMetadata(headers: Headers) {
        headers.set("content-type", "audio/mpeg");
      },
    };
  }
}
