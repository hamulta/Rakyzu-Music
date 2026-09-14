// Explicit post-deployment smoke only; never imported by unit tests or run automatically.
// Run from repository root: node services/rakyzu-api/test/live-playlist-smoke.mjs --run
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

if (!process.argv.includes('--run')) throw new Error('Explicit --run is required');
process.loadEnvFile('credential.env');
const base = process.env.SUPABASE_URL;
const ref = process.env.SUPABASE_PROJECT_REF;
const publicKey = process.env.SUPABASE_PUBLISHABLE_KEY;
const api = 'https://api.rakyzu.my.id';
const createdUsers = [];
let ownerToken;
let serviceKey;
let coverCreated = false;
const playlistId = randomUUID();
const trackOne = 'a3000000-0000-4000-8000-000000000001';
const trackTwo = 'a3000000-0000-4000-8000-000000000002';

async function request(url, { method = 'GET', token, key = publicKey, body, raw = false, expected = 200 } = {}) {
  let response;
  try {
    response = await fetch(url, {
      method, redirect: 'error', signal: AbortSignal.timeout(20000),
      headers: { ...(key ? { apikey: key } : {}), ...(token ? { authorization: `Bearer ${token}` } : {}),
        ...(body ? { 'content-type': raw ? 'image/png' : 'application/json' } : {}) },
      body: body ? (raw ? body : JSON.stringify(body)) : undefined,
    });
  } catch (error) {
    const kind = error instanceof Error ? error.name : 'transport error';
    throw new Error(`${method} ${new URL(url).pathname} failed (${kind})`);
  }
  // Never log response bodies, request headers or upstream error details.
  assert.equal(response.status, expected, `${method} ${new URL(url).pathname} returned unexpected status`);
  return response;
}
async function rpc(name, body, token = ownerToken, expected = 200) {
  try {
    return (await request(`${base}/rest/v1/rpc/${name}`, { method: 'POST', token, body, expected })).json();
  } catch (error) {
    const action = typeof body.action === 'string' ? body.action : 'none';
    const revision = Number.isInteger(body.expected_revision) ? body.expected_revision : 'none';
    throw new Error(`${error instanceof Error ? error.message : 'RPC failed'}; rpc=${name}; action=${action}; revision=${revision}`);
  }
}
async function user() {
  const email = `rakyzu-smoke-${randomUUID()}@example.invalid`;
  const password = `${randomUUID()}Aa9!`;
  const row = await (await request(`${base}/auth/v1/admin/users`, {
    method: 'POST', token: serviceKey, key: serviceKey, body: { email, password, email_confirm: true },
  })).json();
  const account = row.user ?? row;
  assert.equal(account.email, email);
  assert.match(account.id, /^[0-9a-f-]{36}$/);
  createdUsers.push(account.id);
  const session = await (await request(`${base}/auth/v1/token?grant_type=password`, {
    method: 'POST', body: { email, password },
  })).json();
  assert.ok(session.access_token);
  return session.access_token;
}

try {
  const health = await (await request(`${api}/v1/health`)).json();
  assert.equal(health.version, '0.5.5', 'Deploy 0.5.5 before running smoke');
  const keys = await (await request(`https://api.supabase.com/v1/projects/${ref}/api-keys?reveal=true`, {
    token: process.env.SUPABASE_ACCESS_TOKEN, key: null,
  })).json();
  serviceKey = keys.find(key => key.name === 'service_role')?.api_key;
  assert.ok(serviceKey, 'Admin credential unavailable');
  ownerToken = await user();
  const otherToken = await user();
  await rpc('create_playlist', { playlist_id: playlistId, playlist_name: 'Rakyzu Music release smoke', playlist_description: '' });
  let detail = await rpc('mutate_playlist', { playlist_id: playlistId, expected_revision: 1, action: 'add', track_id: trackOne });
  assert.equal(detail.playlist.revision, 2);
  detail = await rpc('mutate_playlist', { playlist_id: playlistId, expected_revision: 2, action: 'add', track_id: trackOne });
  assert.equal(detail.playlist.revision, 2);
  detail = await rpc('mutate_playlist', { playlist_id: playlistId, expected_revision: 2, action: 'add', track_id: trackTwo });
  assert.equal(detail.playlist.trackCount, 2);
  detail = await rpc('mutate_playlist', { playlist_id: playlistId, expected_revision: 3, action: 'reorder', ordered_ids: [trackTwo, trackOne] });
  assert.deepEqual(detail.items.map(item => item.trackId), [trackTwo, trackOne]);
  const conflict = await rpc('mutate_playlist', { playlist_id: playlistId, expected_revision: 3, action: 'remove', track_id: trackOne }, ownerToken, 409);
  assert.equal(conflict.code, 'PT409');
  detail = await rpc('mutate_playlist', { playlist_id: playlistId, expected_revision: 4, action: 'metadata', playlist_name: 'Updated release smoke', playlist_description: 'Temporary verification only' });
  assert.equal(detail.playlist.name, 'Updated release smoke');
  await rpc('get_playlist_detail', { playlist_id: playlistId }, otherToken, 403);
  console.log('PASS: authenticated detail, add, duplicate, reorder, stale conflict, metadata and owner isolation');

  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64');
  const coverUrl = `${api}/v1/playlists/${playlistId}/artwork`;
  await request(coverUrl, { method: 'PUT', token: otherToken, body: png, raw: true, expected: 404 });
  await request(coverUrl, { method: 'PUT', token: ownerToken, body: png, raw: true, expected: 204 });
  coverCreated = true;
  const read = await request(coverUrl, { token: ownerToken });
  assert.equal(read.headers.get('cache-control'), 'private, no-store');
  assert.deepEqual(Buffer.from(await read.arrayBuffer()), png);
  await request(coverUrl, { token: otherToken, expected: 404 });
  await request(coverUrl, { expected: 401 });
  await request(coverUrl, { method: 'DELETE', token: ownerToken, expected: 204 });
  coverCreated = false;
  await request(coverUrl, { token: ownerToken, expected: 404 });
  detail = await rpc('mutate_playlist', { playlist_id: playlistId, expected_revision: 5, action: 'remove', track_id: trackOne });
  assert.equal(detail.playlist.trackCount, 1);
  console.log('PASS: private R2 upload/read/delete, byte equality, no-store and unauthorized denial');
} catch (error) {
  // Errors contain only static assertion labels or method/path/status; bodies and headers stay redacted.
  console.error('FAIL:', error instanceof Error ? error.message : 'Smoke request failed (details redacted)');
  process.exitCode = 1;
} finally {
  if (coverCreated && ownerToken) {
    try { await request(`${api}/v1/playlists/${playlistId}/artwork`, { method: 'DELETE', token: ownerToken, expected: 204 }); }
    catch { console.error('CLEANUP FAILED: temporary cover', playlistId); process.exitCode = 1; }
  }
  for (const id of createdUsers) {
    try {
      await request(`${base}/auth/v1/admin/users/${id}`, { method: 'DELETE', token: serviceKey, key: serviceKey });
      console.log('CLEANUP: temporary smoke account removed with its test playlist');
    } catch { console.error('CLEANUP FAILED: temporary account', id); process.exitCode = 1; }
  }
}
