# Rakyzu Music Engineering Rules

These rules apply to the entire repository.

## Product scope

- Deliver Android through version `1.0.0` before starting the Web or iOS product phases.
- Use the Android application ID and namespace prefix `my.id.rakyzumusic`.
- Keep all user-facing names, artifacts, release files, and automation branded as Rakyzu Music.
- Spotify is a product and engineering reference only. Do not copy Spotify source code, proprietary assets, trademarks, copyrighted catalog content, or private infrastructure.

## Architecture and quality

- Prefer Kotlin, Jetpack Compose, layered architecture, unidirectional data flow, coroutines/Flow, dependency injection, and offline-first repositories.
- Keep UI, domain, data, playback, and infrastructure responsibilities separated by clear module boundaries.
- Put privileged operations behind the Rakyzu API. Never ship database service-role keys, Cloudflare tokens, or R2 S3 credentials in a client.
- Enforce Supabase Row Level Security for every client-accessible table and use short-lived signed URLs or authenticated Worker endpoints for media.
- Validate every change with the smallest relevant test set; run unit tests, lint, and a debug build before publishing a version.

## Secrets

- Store secrets only in local ignored environment files or managed secret stores.
- Never print, commit, hard-code, or place credentials in command-line arguments.
- Treat credentials pasted into chat or logs as exposed and rotate them.

## Git and releases

- One completed, validated unit of work per Conventional Commit.
- Inspect status and diff, then stage only explicit paths. Never use `git add .`, `git add -A`, or `git add --all`.
- Push validated commits to `origin/main` as required by the CEO workflow.
- Keep `versionName`, `versionCode`, documentation, CI artifact names, and release notes synchronized.
- Use the CEO release train: patch versions `0.x.1` through `0.x.10`, then the next `0.(x+1).0`; after `0.10.10`, release `1.0.0`.
- Never push a build with failing tests, lint errors, or compilation errors.

## Safety

- Preserve unrelated user changes and never perform destructive Git or filesystem operations without explicit approval.
- Database schema changes must be forward migrations; destructive SQL requires an approved backup and rollback plan.
- Do not reorganize established top-level folders without explicit CEO direction.
