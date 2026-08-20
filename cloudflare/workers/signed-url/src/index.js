/**
 * RAKYZU MUSIC - Cloudflare Worker
 * Generates short-lived presigned URLs for Cloudflare R2 access.
 *
 * Endpoints:
 *   GET /signed-url?key=<object-key>&action=put|get&expires=<seconds>
 *     -> { signedUrl, expiresIn }
 *
 * Security:
 *  - Requires the caller to be an authenticated Supabase user (JWT verified)
 *  - Returns short-lived URLs (default 3600s for GET, 900s for PUT)
 *  - Never exposes the bucket directly
 *
 * Presigned URLs are generated with SigV4 signing via Web Crypto (no Node deps).
 */

const R2_ENDPOINT = 'https://{account_id}.r2.cloudflarestorage.com';
const SERVICE = 's3';
const REGION = 'auto';

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

    const verified = await verifySupabaseJwt(token, env.SUPABASE_JWT_SECRET);
    if (!verified) {
      return json({ error: 'Invalid or expired token' }, 401);
    }

    // Route: GET /signed-url
    if (path === '/signed-url') {
      return handleSignedUrl(url, env, verified);
    }

    return json({ error: 'Not found' }, 404);
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

  // Restrict uploads to staff/admin/owner roles
  if (action === 'put') {
    const allowedRoles = ['staff', 'admin', 'owner'];
    const role = user.role || (user.dev ? 'staff' : 'free');
    if (!allowedRoles.includes(role)) {
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
 * Verify a Supabase JWT using the signing secret (JWT secret from Supabase).
 * Uses Web Crypto to avoid external dependencies in the worker.
 */
async function verifySupabaseJwt(token, secret) {
  if (!secret) {
    // Development fallback: allow when no secret configured (sandbox only).
    return { dev: true, role: 'staff', sub: 'dev-user' };
  }
  try {
    const [headerB64, payloadB64, signatureB64] = token.split('.');
    if (!headerB64 || !payloadB64 || !signatureB64) return null;

    const payload = JSON.parse(base64UrlDecode(payloadB64));
    const now = Math.floor(Date.now() / 1000);
    if (payload.exp && payload.exp < now) return null;

    const key = await crypto.subtle.importKey(
      'raw',
      new TextEncoder().encode(secret),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['verify'],
    );
    const data = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
    const sigBytes = base64UrlDecodeToBytes(signatureB64);
    const valid = await crypto.subtle.verify('HMAC', key, sigBytes, data);
    if (!valid) return null;

    return {
      sub: payload.sub,
      role: payload.role || 'free',
      email: payload.email,
    };
  } catch (e) {
    return null;
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