# Rakyzu Music

Rakyzu Music is a full-stack music platform being delivered Android-first. The current release train targets a production-ready Android `1.0.0`; Web and iOS begin only after that milestone is stable.

Current version: **0.1.2**

## Technology baseline

- Android: Kotlin, Jetpack Compose, Material 3, AndroidX, Gradle Kotlin DSL
- Playback: AndroidX Media3 ExoPlayer, MediaSessionService, and system notification controls
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
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
```

The debug APK is written to `apps/android/app/build/outputs/apk/debug/Rakyzu-Music-0.1.2-debug.apk`. The unsigned release variant is built only as an R8/resource-shrinking quality gate until production signing is provisioned.

## Configuration and security

Android reads `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, and `RAKYZU_API_BASE_URL` from Gradle properties or environment variables and exposes only those public values through generated BuildConfig fields. GitHub Actions injects them from encrypted repository secrets. A build with missing or invalid public configuration fails closed for the affected repository; service-role keys, Cloudflare tokens, and R2 secrets remain server-only.

Privileged CLI credentials may be kept in the ignored local `credential.env` file with permission `600`. That file must never be staged, committed, logged, uploaded as an artifact, or read by the Android build.

Database migrations and pgTAP policy tests live under `supabase/`. The database workflow replays them against an isolated Postgres instance before deploying forward-only migrations to the linked Rakyzu Music Android project.

Email/password authentication uses Supabase Auth with mandatory email confirmation and PKCE callbacks to `my.id.rakyzumusic://auth`. Access tokens, refresh tokens, and PKCE verifiers are encrypted at rest with an AES-GCM key generated inside Android Keystore. Local logout revokes and removes only the current device session.

Password recovery uses the exact callback `my.id.rakyzumusic://auth/recovery`, a persisted encrypted recovery requirement, and a non-enumerating email response. Incoming recovery callbacks are allowlisted by exact scheme, authority, path, query shape, and bounded authorization code before changing local state. Android explicitly rejects cleartext network traffic. Authenticated listener profiles are owner-scoped by RLS; the Android shell remains gated until required display-name onboarding completes.

Published artist, album, track, and editorial-shelf metadata is read through authenticated Supabase RLS and synchronized into Room 3. The local database is the Home screen's observable source of truth, so the last verified feed remains available when refresh fails. Home presents ordered editorial shelves, recently played tracks isolated by listener account, new releases, and the complete catalog. An empty successful refresh renders an explicit accessible state with a refresh action instead of inactive playback controls; loading and degraded status changes are announced as polite accessibility live regions. Shelf playback exposes track-specific TalkBack action labels and deterministic queue-order traversal, while every Home interaction maintains an explicit minimum 48dp target.

Protected media requests use the listener's in-memory Supabase access token only in an `Authorization` header to `https://api.rakyzu.my.id`. The Worker verifies the ES256 JWT against Supabase JWKS, rechecks catalog visibility through RLS, maps validated track IDs to private R2 keys, and streams full or single-range responses without buffering the object in memory. The API health endpoint is public; media endpoints fail closed without a valid listener session.

Playback runs in a Media3 `MediaSessionService` with automatic MediaStyle notification controls, audio-focus handling, and noisy-output protection. The player receives only internal track UUID URIs; the current bearer session is resolved into an HTTPS Worker request just before each data-source open and is never placed in media metadata, logs, or a persisted queue. Home selections populate a Media3 playlist, while the compact player and branded Now Playing surface expose synchronized progress, seeking, previous/next actions, and queue navigation. Actual Media3 item transitions update the active listener's bounded local recently-played history. Progress sampling is suspended whenever playback is inactive, and queue metadata is rebuilt only when the Media3 timeline changes. Playback clears when the listener session is no longer signed in.

See [Architecture](docs/ARCHITECTURE.md), [Database](docs/DATABASE.md), [Roadmap](docs/ROADMAP.md), and [Security](SECURITY.md).
