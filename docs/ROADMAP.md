# Android Release Roadmap

The release train follows the CEO-defined cadence. Each line may contain up to ten validated patches before advancing. Scope can move between patch releases when quality requires it, but version order does not skip.

## Foundation — 0.0.x

- [x] `0.0.1` — repository, Android modular scaffold, Rakyzu design baseline, test/lint/build CI.
- [x] `0.0.2` — adaptive app shell, Navigation 3, accessibility baseline.
- [x] `0.0.3` — Supabase project bootstrap, migrations, RLS test strategy.
- [x] `0.0.4` — email authentication, PKCE callbacks, encrypted session lifecycle, and local logout.
- [x] `0.0.5` — owner-scoped profile onboarding, account editing, and secure password recovery.
- [x] `0.0.6` — published catalog schema, Room 3 source of truth, validated Supabase synchronization, and degraded offline Home state.
- [x] `0.0.7` — Supabase JWT/RLS-authorized Worker API, private R2 range streaming, automated deployment, and Android authenticated media-request boundary.
- [x] `0.0.8` — secure Media3 playback service, authenticated ExoPlayer data source, audio focus, notification controls, and live compact-player state.
- [x] `0.0.9` — Media3 queue state and controls, compact-player progress/actions, seeking, and branded Now Playing foundation.
- [x] `0.0.10` — strict auth callback and cleartext defenses, idle playback/per-queue allocation reductions, immutable CI action pins, and automated R8 release validation.

## Product milestones

- [x] `0.1.0` — offline-first Home feed, Supabase-curated editorial shelves, account-isolated recently played, new releases, and playback-transition history capture.
- [x] `0.1.1` — resilient successful-empty Home state, accessible refresh/status announcements, inactive-control cleanup, and duplicate-refresh protection.
- [x] `0.1.2` — Home shelf playback semantics, deterministic focus order, 48dp touch targets, and Compose accessibility coverage.
- [x] `0.1.3` — feed freshness metadata and saved-catalog staleness messaging.
- [x] `0.1.4` — adaptive Home shelf layouts for large text and compact screens.
- [x] `0.1.5` — derived-section allocation and recomposition performance hardening.
- [x] `0.1.6` — catalog refresh retry policy and connectivity recovery.
- [x] `0.1.7` — artwork loading, cache policy, placeholders, and failure states.
- [x] `0.1.8` — Home filter and scroll state restoration across recreation.
- [x] `0.1.9` — Home feed observability and bounded diagnostics.
- [x] `0.1.10` — final Home regression, accessibility, resilience, and performance gate before Search.
- `0.2.x`: Search, browse, artists, albums, and tracks.
- `0.3.x`: Library, liked songs, saved albums, follows.
- `0.4.x`: Playlist create/edit/order/share and concurrency handling.
- `0.5.x`: Queue, playback recovery, devices, audio quality controls.
- `0.6.x`: Offline downloads, storage controls, resilient sync.
- `0.7.x`: Lyrics, credits, social sharing, notifications.
- `0.8.x`: Personalization, radio, mixes, history, recommendations.
- `0.9.x`: subscriptions/entitlements, privacy, moderation, admin operations.
- `0.10.x`: release candidate hardening, accessibility, localization, performance, security, Play readiness.
- `1.0.0`: signed production release after all quality gates pass.

No Web or iOS implementation begins before Android `1.0.0` is stable and accepted.
