/**
 * RAKYZU MUSIC - Cloudflare Worker
 * Generates short-lived presigned URLs for Cloudflare R2 access.
 *
 * Endpoints:
 *   GET /signed-url?key=<object-key>&action=put|get&expires=<seconds>[&size=<bytes>]
 *     -> { signedUrl, expiresIn }
 *
 * Security:
 *  - Requires the caller to be an authenticated Supabase user (JWT verified)
 *  - JWTs are verified against Supabase's JWKS (ES256) or legacy JWT secret (HS256)
 *  - Role app (staff/admin/owner) dibaca otoritatif dari tabel `users` via
 *    service_role key (claim `role` JWT selalu "authenticated")
 *  - PUT divalidasi: tipe file (audio/: mp3,m4a,aac,flac,wav; images/: jpg,jpeg,png,webp,svg)
 *    dan ukuran (audio max 200MB, gambar max 120MB) via query param `size`
 *  - Returns short-lived URLs (default 3600s for GET, 900s for PUT)
 *  - Never exposes the bucket directly
 *
 * Presigned URLs are generated with SigV4 signing via Web Crypto (no Node deps).
 */

const R2_ENDPOINT = 'https://{account_id}.r2.cloudflarestorage.com';
const SERVICE = 's3';
const REGION = 'auto';
const JWKS_CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes
let jwksCache = { keys: null, fetchedAt: 0 };

// --- Upload validation (client-side dan worker harus sama) ---
const AUDIO_EXTENSIONS = new Set(['mp3', 'm4a', 'aac', 'flac', 'wav']);
const IMAGE_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'webp', 'svg']);
const MAX_AUDIO_BYTES = 200 * 1024 * 1024; // 200 MB per track
const MAX_IMAGE_BYTES = 120 * 1024 * 1024; // 120 MB per gambar
const UPLOAD_ROLES = ['staff', 'admin', 'owner'];

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    // CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: corsHeaders(env.ALLOWED_ORIGINS || '*'),
        status: 204,
      });
    }

    // Only GET/POST allowed
    if (request.method !== 'GET' && request.method !== 'POST') {
      return json({ error: 'Method not allowed' }, 405);
    }

    // Public health check (no auth required)
    if (path === '/health') {
      return json({ status: 'ok' });
    }

    // Verify Supabase JWT for authenticated access
    const authHeader = request.headers.get('Authorization') || '';
    const token = authHeader.replace(/^Bearer\s+/i, '');
    if (!token) {
      return json({ error: 'Missing Authorization header' }, 401);
    }

    try {
      const verified = await verifySupabaseJwt(token, env);
      if (!verified) {
        return json({ error: 'Invalid or expired token' }, 401);
      }

      // Route: GET /signed-url
      if (path === '/signed-url') {
        return handleSignedUrl(url, env, verified);
      }

      return json({ error: 'Not found' }, 404);
    } catch (err) {
      if (err && err.status === 503) {
        return json({ error: err.message || 'Verification keys unavailable' }, 503);
      }
      return json({ error: 'Invalid or expired token' }, 401);
    }
  },
};

async function handleSignedUrl(url, env, user) {
  const key = url.searchParams.get('key');
  const action = (url.searchParams.get('action') || 'get').toLowerCase();
  const requested = parseInt(url.searchParams.get('expires') || '0', 10) || 0;
  const expiresIn =
    action === 'put'
      ? Math.min(Math.max(requested, 60), 900)
      : Math.min(Math.max(requested, 60), 3600);

  if (!key) {
    return json({ error: 'Missing "key" query param' }, 400);
  }

  // Restrict uploads to staff/admin/owner.
  // Claim `role` pada JWT Supabase selalu "authenticated", jadi role app asli
  // diambil secara otoritatif dari tabel `users` via service_role key.
  if (action === 'put') {
    const size = parseInt(url.searchParams.get('size') || '0', 10) || 0;
    const invalid = validateUpload(key, size);
    if (invalid) {
      return json({ error: invalid }, 400);
    }
    const role = await getUserRole(env, user.sub);
    if (!UPLOAD_ROLES.includes(role)) {
      return json({ error: 'Forbidden: only staff/admin/owner can upload' }, 403);
    }
  }

  const endpoint = env.R2_ENDPOINT || R2_ENDPOINT.replace('{account_id}', env.ACCOUNT_ID || '');
  const signedUrl = await signUrl({
    endpoint,
    accessKeyId: env.R2_ACCESS_KEY_ID,
    secretAccessKey: env.R2_SECRET_ACCESS_KEY,
    bucket: env.R2_BUCKET_NAME,
    key,
    action,
    expiresIn,
    method: action === 'put' ? 'PUT' : 'GET',
  });

  return json({
    signedUrl,
    expiresIn,
    action,
    key,
    bucket: env.R2_BUCKET_NAME,
  });
}

/**
 * Validasi tipe & ukuran file upload berdasarkan prefix object key.
 *   audio/{id}.mp3     -> mp3/m4a/aac/flac/wav, max 200MB
 *   images/{id}.jpg    -> jpg/jpeg/png/webp/svg, max 120MB
 * Mengembalikan string error bila tidak valid, atau null bila valid.
 */
function validateUpload(key, size) {
  const lower = key.toLowerCase();
  const ext = lower.slice(lower.lastIndexOf('.') + 1);
  const isAudio = lower.startsWith('audio/') && AUDIO_EXTENSIONS.has(ext);
  const isImage = lower.startsWith('images/') && IMAGE_EXTENSIONS.has(ext);

  if (!isAudio && !isImage) {
    return 'Unsupported file type: gunakan audio/ untuk audio dan images/ untuk gambar';
  }

  if (size <= 0) {
    return 'Missing "size" query param (ukuran file dalam bytes)';
  }

  const maxBytes = isAudio ? MAX_AUDIO_BYTES : MAX_IMAGE_BYTES;
  if (size > maxBytes) {
    const mb = maxBytes / (1024 * 1024);
    return `File too large: max ${mb}MB`;
  }
  return null;
}

/**
 * Ambil role app user secara otoritatif dari tabel `users`.
 * Diperlukan karena JWT Supabase selalu berisi claim role "authenticated",
 * bukan role aplikasi (free/premium/staff/admin/owner).
 */
async function getUserRole(env, sub) {
  if (!env.SUPABASE_SERVICE_ROLE_KEY) {
    const err = new Error('SUPABASE_SERVICE_ROLE_KEY not configured');
    err.status = 503;
    throw err;
  }
  if (!sub) {
    const err = new Error('Missing user id');
    err.status = 401;
    throw err;
  }

  const base = (env.SUPABASE_URL || '').replace(/\/$/, '');
  const url = `${base}/rest/v1/users?select=role&id=eq.${encodeURIComponent(sub)}`;
  const res = await fetch(url, {
    headers: {
      apikey: env.SUPABASE_SERVICE_ROLE_KEY,
      Authorization: `Bearer ${env.SUPABASE_SERVICE_ROLE_KEY}`,
      Accept: 'application/json',
    },
  });

  if (!res.ok) {
    const err = new Error(`Role lookup failed (HTTP ${res.status})`);
    err.status = 503;
    throw err;
  }

  const rows = await res.json();
  return Array.isArray(rows) && rows.length > 0 ? rows[0].role : null;
}

/**
 * Generate an AWS SigV4 presigned URL for R2.
 * Implements the signing protocol manually using Web Crypto.
 */
async function signUrl({ endpoint, accessKeyId, secretAccessKey, bucket, key, action, expiresIn, method }) {
  const now = new Date();
  const amzDate = toAmzDate(now);
  const dateStamp = amzDate.slice(0, 8);
  const host = new URL(endpoint).hostname;

  const canonicalUri = '/' + bucket + '/' + encodeRfc3986Path(key);
  const canonicalQuery =
    'X-Amz-Algorithm=AWS4-HMAC-SHA256' +
    '&X-Amz-Credential=' + encodeURIComponent(`${accessKeyId}/${dateStamp}/${REGION}/${SERVICE}/aws4_request`) +
    '&X-Amz-Date=' + amzDate +
    '&X-Amz-Expires=' + expiresIn +
    '&X-Amz-SignedHeaders=host';

  const canonicalHeaders = `host:${host}\n`;
  const signedHeaders = 'host';
  const payloadHash = 'UNSIGNED-PAYLOAD';

  const canonicalRequest =
    method + '\n' +
    canonicalUri + '\n' +
    canonicalQuery + '\n' +
    canonicalHeaders + '\n' +
    signedHeaders + '\n' +
    payloadHash;

  const scope = `${dateStamp}/${REGION}/${SERVICE}/aws4_request`;
  const stringToSign =
    'AWS4-HMAC-SHA256\n' +
    amzDate + '\n' +
    scope + '\n' +
    await sha256Hex(canonicalRequest);

  const signingKey = await getSigningKey(secretAccessKey, dateStamp, REGION, SERVICE);
  const signature = await hmacHex(signingKey, stringToSign);

  return `${endpoint}/${bucket}/${encodeRfc3986Path(key)}?${canonicalQuery}&X-Amz-Signature=${signature}`;
}

async function getSigningKey(secret, dateStamp, region, service) {
  const kDate = await hmac(`AWS4${secret}`, dateStamp);
  const kRegion = await hmac(kDate, region);
  const kService = await hmac(kRegion, service);
  return hmac(kService, 'aws4_request');
}

async function hmac(key, data) {
  const cryptoKey = await crypto.subtle.importKey(
    'raw',
    typeof key === 'string' ? new TextEncoder().encode(key) : key,
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const sig = await crypto.subtle.sign('HMAC', cryptoKey, new TextEncoder().encode(data));
  return new Uint8Array(sig);
}

async function hmacHex(key, data) {
  const bytes = await hmac(key, data);
  return bytesToHex(bytes);
}

async function sha256Hex(data) {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(data));
  return bytesToHex(new Uint8Array(hash));
}

function bytesToHex(bytes) {
  let out = '';
  for (const b of bytes) out += b.toString(16).padStart(2, '0');
  return out;
}

function toAmzDate(date) {
  return date.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
}

function encodeRfc3986Path(str) {
  return encodeURIComponent(str).replace(/%2F/g, '/').replace(/\+/g, '%20');
}

/**
 * Verify a Supabase JWT.
 *
 * Modern Supabase signs user access tokens with ES256 (ECDSA P-256) using a
 * per-project signing key exposed via JWKS at
 *   {SUPABASE_URL}/auth/v1/.well-known/jwks.json
 * Legacy tokens (and static anon/service keys) use HS256 signed with the
 * per-project JWT secret.
 *
 * No dev fallback exists: an unverifiable token is always rejected.
 */
async function verifySupabaseJwt(token, env) {
  const [headerB64, payloadB64, signatureB64] = token.split('.');
  if (!headerB64 || !payloadB64 || !signatureB64) return null;

  const header = JSON.parse(base64UrlDecode(headerB64));
  const payload = JSON.parse(base64UrlDecode(payloadB64));
  const now = Math.floor(Date.now() / 1000);
  if (payload.exp && payload.exp < now) return null;

  const data = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const signature = base64UrlDecodeToBytes(signatureB64);
  const alg = header.alg || '';

  if (alg === 'ES256') {
    const ok = await verifyEs256(header.kid, data, signature, env);
    if (!ok) return null;
  } else if (alg === 'HS256') {
    if (!env.SUPABASE_JWT_SECRET) {
      const err = new Error('SUPABASE_JWT_SECRET not configured for HS256 verification');
      err.status = 503;
      throw err;
    }
    const ok = await verifyHs256(env.SUPABASE_JWT_SECRET, data, signature);
    if (!ok) return null;
  } else {
    return null;
  }

  return {
    sub: payload.sub,
    role: payload.role || 'free',
    email: payload.email,
  };
}

/** Verify an ES256 JWS signature using the key from Supabase's JWKS. */
async function verifyEs256(kid, data, signature, env) {
  const jwks = await getJwks(env);
  const key = jwks.keys.find((k) => k.kid === kid && k.kty === 'EC' && k.crv === 'P-256');
  if (!key) return false;

  try {
    const publicKey = await crypto.subtle.importKey(
      'jwk',
      { kty: 'EC', crv: 'P-256', x: key.x, y: key.y, ext: true },
      { name: 'ECDSA', namedCurve: 'P-256' },
      false,
      ['verify'],
    );
    return await crypto.subtle.verify(
      { name: 'ECDSA', hash: 'SHA-256' },
      publicKey,
      signature,
      data,
    );
  } catch (e) {
    return false;
  }
}

/** Verify an HS256 JWS signature with the legacy shared secret. */
async function verifyHs256(secret, data, signature) {
  try {
    const key = await crypto.subtle.importKey(
      'raw',
      new TextEncoder().encode(secret),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['verify'],
    );
    return await crypto.subtle.verify('HMAC', key, signature, data);
  } catch (e) {
    return false;
  }
}

/** Fetch Supabase JWKS with a short in-memory TTL cache. */
async function getJwks(env) {
  const url =
    env.SUPABASE_JWT_JWKS_URL ||
    `${(env.SUPABASE_URL || '').replace(/\/$/, '')}/auth/v1/.well-known/jwks.json`;

  if (!url.startsWith('https://')) {
    const err = new Error('SUPABASE_JWT_JWKS_URL (or SUPABASE_URL) is not configured');
    err.status = 503;
    throw err;
  }

  if (jwksCache.keys && Date.now() - jwksCache.fetchedAt < JWKS_CACHE_TTL_MS) {
    return jwksCache.keys;
  }

  try {
    const res = await fetch(url, { cf: { cacheTtl: 300, cacheEverything: true } });
    if (!res.ok) {
      const err = new Error(`Failed to fetch JWKS (HTTP ${res.status})`);
      err.status = 503;
      throw err;
    }
    const body = await res.json();
    if (!Array.isArray(body.keys) || body.keys.length === 0) {
      const err = new Error('JWKS response has no keys');
      err.status = 503;
      throw err;
    }
    jwksCache = { keys: body, fetchedAt: Date.now() };
    return body;
  } catch (e) {
    if (e && e.status === 503) throw e;
    const err = new Error('JWKS fetch failed');
    err.status = 503;
    throw err;
  }
}

function base64UrlDecode(str) {
  const b64 = str.replace(/-/g, '+').replace(/_/g, '/');
  const padded = b64.padEnd(b64.length + ((4 - (b64.length % 4)) % 4), '=');
  const bin = atob(padded);
  let out = '';
  for (let i = 0; i < bin.length; i++) {
    out += String.fromCharCode(bin.charCodeAt(i));
  }
  return decodeURIComponent(escape(out));
}

function base64UrlDecodeToBytes(str) {
  const b64 = str.replace(/-/g, '+').replace(/_/g, '/');
  const padded = b64.padEnd(b64.length + ((4 - (b64.length % 4)) % 4), '=');
  const bin = atob(padded);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) {
    bytes[i] = bin.charCodeAt(i);
  }
  return bytes;
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      ...corsHeaders(),
    },
  });
}

function corsHeaders(origins = '*') {
  return {
    'Access-Control-Allow-Origin': origins,
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Max-Age': '86400',
  };
}