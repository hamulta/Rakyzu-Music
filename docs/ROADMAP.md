# Android Release Roadmap

The release train follows the CEO-defined cadence. Each line may contain up to ten validated patches before advancing. Scope can move between patch releases when quality requires it, but version order does not skip.

## Public Spotify reference principles

Rakyzu Music translates public Spotify product and engineering ideas into its own implementation and brand; it does not copy proprietary code, assets, catalog content, trademarks, or private infrastructure.

- Spotify's public [playlist concepts](https://developer.spotify.com/documentation/web-api/concepts/playlists) model a playlist as metadata plus ordered items and use a snapshot identifier for concurrent changes. Rakyzu uses a monotonic `revision` with account-scoped metadata first, then ordered items and conflict-safe mutations.
- Spotify's public [mobile Create controls](https://newsroom.spotify.com/2025-05-07/experience-a-new-dimension-of-music-discovery-with-more-controls-and-enhanced-tools/) place creation alongside the stable Library destination. Rakyzu adds a dedicated adaptive Create destination while retaining Your Library.
- Spotify Engineering's public [client architecture](https://engineering.atspotify.com/2020/5/spotify-modernizes-client-side-architecture-to-accelerate-service-on-all-devices) emphasizes batching metadata, on-device storage, and precomputed ordering for slow networks and older devices. Rakyzu applies bounded RPCs, Room as source of truth, deterministic ordering, and staged feature boundaries.

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
- [x] `0.2.0` — offline-first artist, album, and track search with deterministic relevance, accessible browse/results states, and track queue playback.
- [x] `0.2.1` — search query restoration, focus, and keyboard ergonomics.
- [x] `0.2.2` — artist detail and catalog navigation.
- [x] `0.2.3` — album detail, ordered track listing, and playback.
- [x] `0.2.4` — track context and safe metadata actions.
- [x] `0.2.5` — curated browse categories and discovery entry points.
- [x] `0.2.6` — authenticated paginated full-catalog search boundary.
- [x] `0.2.7` — recent searches with explicit listener privacy controls.
- [x] `0.2.8` — search resilience and offline/online transition hardening.
- [x] `0.2.9` — privacy-safe Search observability and performance hardening.
- [x] `0.2.10` — final Search regression, accessibility, resilience, and performance gate before Library.
- [x] `0.3.0` — account-scoped offline-first Library foundation with liked songs, saved albums, followed artists, playback, Search/detail mutations, Room persistence, Supabase synchronization, and RLS isolation.
- [x] `0.3.1` — Like, save, and follow entry points across Home and Now Playing.
- [x] `0.3.2` — Library search, sort, filter, and listener-scoped UI-state restoration.
- [x] `0.3.3` — Liked Songs ordering, bulk playback, and deterministic queue controls.
- [x] `0.3.4` — Saved-album and followed-artist navigation, artwork, and empty-state polish.
- [x] `0.3.5` — optimistic offline mutations, durable outbox, conflict handling, and recovery.
- [x] `0.3.6` — bounded pagination and incremental Library synchronization.
- [x] `0.3.7` — freshness, validated-connectivity recovery, and retry hardening.
- [x] `0.3.8` — adaptive layouts, large-text behavior, and complete TalkBack contracts.
- [x] `0.3.9` — privacy-safe Library diagnostics and performance hardening.
- [x] `0.3.10` — final Library regression, accessibility contracts, resilience, instrumentation compilation, and performance gate before Playlists. Device/runtime execution was not evidenced in that release.
- [x] `0.4.0` — account-scoped playlist create/list foundation, Room source of truth, Supabase forced RLS, and revision-based concurrency contract.
- [x] `0.4.1` — playlist detail, ordered items, and deterministic queue playback.
- [x] `0.4.2` — add/remove song entry points, duplicate prevention, and catalog validation.
- [x] `0.4.3` — optimistic reorder with revision checks, stale-snapshot rejection, and conflict refresh.
- [x] `0.4.4` — playlist metadata editing and authenticated artwork lifecycle.

The 0.4.1–0.4.4 batch uses one cumulative 0.4.4 artifact, versionCode 44, commit/release
train and final validation cycle. Scope and local validation are complete; publication
evidence is recorded separately after CI/CD. See [work session](WORK_SESSION.md) for gate evidence.
Playlist/Room instrumentation APK compilation remains a CI release requirement; focused
runtime evidence is captured on the CEO-connected physical Android device.

- [ ] `0.4.5` — collaboration invites, owner/editor roles, and revocation.
- [ ] `0.4.6` — private/public visibility, safe sharing, following, and abuse-resistant boundaries.
- [ ] `0.4.7` — offline mutation outbox, idempotency, and conflict recovery.
- [ ] `0.4.8` — bounded pagination and large-playlist performance hardening.
- [ ] `0.4.9` — privacy-safe Playlist diagnostics, adaptive UI, and complete TalkBack contracts.
- [ ] `0.4.10` — final Playlist regression, accessibility, resilience, runtime, security, and performance gate before Queue.
- `0.5.x`: Queue, playback recovery, devices, audio quality controls.
- `0.6.x`: Offline downloads, storage controls, resilient sync.
- `0.7.x`: Lyrics, credits, social sharing, notifications.
- `0.8.x`: Personalization, radio, mixes, history, recommendations.
- `0.9.x`: subscriptions/entitlements, privacy, moderation, admin operations.
- `0.10.x`: release candidate hardening, accessibility, localization, performance, security, Play readiness.
- `1.0.0`: signed production release after all quality gates pass.

No Web or iOS implementation begins before Android `1.0.0` is stable and accepted.
