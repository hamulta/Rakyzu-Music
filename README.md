# Rakyzu Music

Full-stack music streaming app — Flutter (iOS/Android/Web) + Supabase + Cloudflare R2 + Midtrans.

## Stack

- **Flutter** 3.24.x (stable) · Riverpod · Feature-First Architecture (`lib/core`, `lib/features`, `lib/shared`)
- **Supabase** — Postgres (RLS aktif), Auth, migration di `supabase/migrations/`
- **Cloudflare R2 + Worker** — storage audio/cover via presigned URL, worker di `cloudflare/workers/signed-url/`
- **GitHub Actions** — CI analyze/test/build (web→Cloudflare Pages, android APK, ios .app)

## Prasyarat (wajib sebelum deploy Worker)

Cloudflare Worker **wajib memverifikasi JWT Supabase** dari setiap request `/signed-url`. Tanpa konfigurasi berikut, endpoint mengembalikan `503` dan tidak ada fallback development:

1. **`SUPABASE_URL`** — URL project Supabase (untuk derivasi JWKS).
2. **`SUPABASE_JWT_JWKS_URL`** *(opsional)* — default `{SUPABASE_URL}/auth/v1/.well-known/jwks.json`. Dipakai memverifikasi user token modern (ES256).
3. **`SUPABASE_JWT_SECRET`** — JWT Secret (legacy HS256) dari Dashboard Supabase → Project Settings → API → JWT Settings. Hanya dibutuhkan jika masih ada token HS256 (misal service_role/anon lama). Set sebagai **Cloudflare Worker secret**, jangan pernah di plaintext `wrangler.toml`.

Aturan secret Worker (jangan di-commit):

```bash
wrangler secret put SUPABASE_JWT_SECRET
wrangler secret put R2_ACCESS_KEY_ID
wrangler secret put R2_SECRET_ACCESS_KEY
```

Worker menolak `401` untuk token tidak valid/kadaluarsa dan `403` untuk upload oleh role non-staff (`staff`/`admin`/`owner` saja).

## Development

```bash
flutter pub get
flutter run            # device/emulator; --dart-define=... untuk SUPABASE_URL/ANON_KEY
flutter test
flutter analyze
```

Environment lokal: salin `.env.example` → `.env` dan isi nilai asli (jangan commit `.env`).

## CI/CD

Workflow `.github/workflows/ci.yml`:
- `analyze` & `test` — di setiap push/PR ke `develop` dan `main`
- `build-web` (deploy Cloudflare Pages `rakyzu-music`), `build-android`, `build-ios` — saat push ke `main`

Secrets GitHub Actions yang dibutuhkan: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `CLOUDFLARE_WORKER_URL`, `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`.