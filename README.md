# Rakyzu Music

Rakyzu Music is a full-stack music platform being delivered Android-first. The current release train targets a production-ready Android `1.0.0`; Web and iOS begin only after that milestone is stable.

Current version: **0.0.7**

## Technology baseline

- Android: Kotlin, Jetpack Compose, Material 3, AndroidX, Gradle Kotlin DSL
- Playback: AndroidX Media3 (introduced in a later playback milestone)
- Data and identity: Supabase Postgres, Auth, Realtime, and Row Level Security
- API and delivery: Cloudflare Workers, Pages, and R2
- Automation: GitHub Actions

The app uses `my.id.rakyzumusic` as its Android application ID. Spotify informs product capabilities and engineering principles, but Rakyzu Music uses its own brand, implementation, content rights, data model, and infrastructure.

## Repository layout

```text
apps/android/       Android Gradle project
services/rakyzu-api Authenticated Cloudflare Worker and R2 delivery boundary
docs/               Product, architecture, and release documentation
.github/workflows/  CI/CD automation
```

Web and iOS roots are created only when their planned phase begins.

## Build Android

Prerequisites: JDK 17 and Android SDK 37.

```bash
cd apps/android
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The debug APK is written to `apps/android/app/build/outputs/apk/debug/Rakyzu-Music-0.0.7-debug.apk`.

## Configuration and security

Android reads `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, and `RAKYZU_API_BASE_URL` from Gradle properties or environment variables and exposes only those public values through generated BuildConfig fields. GitHub Actions injects them from encrypted repository secrets. A build with missing or invalid public configuration fails closed for the affected repository; service-role keys, Cloudflare tokens, and R2 secrets remain server-only.

Privileged CLI credentials may be kept in the ignored local `credential.env` file with permission `600`. That file must never be staged, committed, logged, uploaded as an artifact, or read by the Android build.

Database migrations and pgTAP policy tests live under `supabase/`. The database workflow replays them against an isolated Postgres instance before deploying forward-only migrations to the linked Rakyzu Music Android project.

Email/password authentication uses Supabase Auth with mandatory email confirmation and PKCE callbacks to `my.id.rakyzumusic://auth`. Access tokens, refresh tokens, and PKCE verifiers are encrypted at rest with an AES-GCM key generated inside Android Keystore. Local logout revokes and removes only the current device session.

Password recovery uses the exact callback `my.id.rakyzumusic://auth/recovery`, a persisted encrypted recovery requirement, and a non-enumerating email response. Authenticated listener profiles are owner-scoped by RLS; the Android shell remains gated until required display-name onboarding completes.

Published artist, album, and track metadata is read through authenticated Supabase RLS and synchronized into Room 3. The local database is the Home screen's observable source of truth, so the last verified catalog remains available when refresh fails.

Protected media requests use the listener's in-memory Supabase access token only in an `Authorization` header to `https://api.rakyzu.my.id`. The Worker verifies the ES256 JWT against Supabase JWKS, rechecks catalog visibility through RLS, maps validated track IDs to private R2 keys, and streams full or single-range responses without buffering the object in memory. The API health endpoint is public; media endpoints fail closed without a valid listener session.

See [Architecture](docs/ARCHITECTURE.md), [Database](docs/DATABASE.md), [Roadmap](docs/ROADMAP.md), and [Security](SECURITY.md).
