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
 */

import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import {
  S3Client,
  GetObjectCommand,
  PutObjectCommand,
} from '@aws-sdk/client-s3';

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

    // Route: GET /health
    if (path === '/health') {
      return json({ status: 'ok' });
    }

    return json({ error: 'Not found' }, 404);
  },
};

async function handleSignedUrl(url, env, user) {
  const key = url.searchParams.get('key');
  const action = url.searchParams.get('action') || 'get';
  const expires = Math.min(
    parseInt(url.searchParams.get('expires') || '0', 10) || 0,
    3600,
  );

  if (!key) {
    return json({ error: 'Missing "key" query param' }, 400);
  }

  // Restrict uploads to staff/admin/owner roles
  if (action === 'put') {
    const allowedRoles = ['staff', 'admin', 'owner'];
    if (!allowedRoles.includes(user.role)) {
      return json({ error: 'Forbidden: only staff/admin/owner can upload' }, 403);
    }
  }

  const s3 = new S3Client({
    region: 'auto',
    endpoint: env.R2_ENDPOINT,
    credentials: {
      accessKeyId: env.R2_ACCESS_KEY_ID,
      secretAccessKey: env.R2_SECRET_ACCESS_KEY,
    },
  });

  const command =
    action === 'put'
      ? new PutObjectCommand({ Bucket: env.R2_BUCKET, Key: key })
      : new GetObjectCommand({ Bucket: env.R2_BUCKET, Key: key });

  const expiresIn = action === 'put' ? Math.min(expires || 900, 900) : Math.min(expires || 3600, 3600);
  const signedUrl = await getSignedUrl(s3, command, { expiresIn });

  return json({
    signedUrl,
    expiresIn,
    action,
    key,
    bucket: env.R2_BUCKET,
  });
}

/**
 * Verify a Supabase JWT using the signing secret (JWT secret from Supabase).
 * Uses Web Crypto to avoid external dependencies in the worker.
 */
async function verifySupabaseJwt(token, secret) {
  if (!secret) {
    // In development without secret configured, allow (see README)
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