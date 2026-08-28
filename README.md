# Rakyzu Music

Rakyzu Music is a full-stack music platform being delivered Android-first. The current release train targets a production-ready Android `1.0.0`; Web and iOS begin only after that milestone is stable.

Current version: **0.0.1**

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
docs/               Product, architecture, and release documentation
.github/workflows/  CI/CD automation
```

Future backend, Web, and iOS roots are created only when their planned phase begins.

## Build Android

Prerequisites: JDK 17 and Android SDK 37.

```bash
cd apps/android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `apps/android/app/build/outputs/apk/debug/Rakyzu-Music-0.0.1-debug.apk`.

## Configuration and security

Copy `.env.example` only for local server/tooling configuration. Android may receive a Supabase publishable key and public URL through generated build configuration in a future integration milestone; service-role keys, Cloudflare tokens, and R2 secrets are server-only.

See [Architecture](docs/ARCHITECTURE.md), [Roadmap](docs/ROADMAP.md), and [Security](SECURITY.md).
