# Android Release Roadmap

The release train follows the CEO-defined cadence. Each line may contain up to ten validated patches before advancing. Scope can move between patch releases when quality requires it, but version order does not skip.

## Public Spotify reference principles

Rakyzu Music translates public Spotify product and engineering ideas into its own implementation and brand; it does not copy proprietary code, assets, catalog content, trademarks, or private infrastructure.

- Spotify's public [playlist concepts](https://developer.spotify.com/documentation/web-api/concepts/playlists) model a playlist as metadata plus ordered items and use a snapshot identifier for concurrent changes. Rakyzu uses a monotonic `revision` with account-scoped metadata first, then ordered items and conflict-safe mutations.
- Spotify's public [mobile Create controls](https://newsroom.spotify.com/2025-05-07/experience-a-new-dimension-of-music-discovery-with-more-controls-and-enhanced-tools/) place creation alongside the stable Library destination. Rakyzu adds a dedicated adaptive Create destination while retaining Your Library.
- Spotify Engineering's public [client architecture](https://engineering.atspotify.com/2020/5/spotify-modernizes-client-side-architecture-to-accelerate-service-on-all-devices) emphasizes batching metadata, on-device storage, and precomputed ordering for slow networks and older devices. Rakyzu applies bounded RPCs, Room as source of truth, deterministic ordering, and staged feature boundaries.
- Spotify's public [Play Queue](https://support.spotify.com/article/play-queue/) exposes what plays next and supports explicit add, reorder, remove, and clear actions. Rakyzu follows that product vocabulary with original account-scoped state, deterministic Media3 edits, and Room persistence; it does not call Spotify playback APIs or use Spotify content.

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

- [x] `0.4.5` — collaboration invites, owner/editor/viewer roles, revocation, and leave controls.
- [x] `0.4.6` — private/public visibility, safe sharing, following, and abuse-resistant boundaries.
- [x] `0.4.7` — offline mutation outbox, idempotency, and conflict recovery.
- [x] `0.4.8` — bounded cursor/detail pagination and large-playlist performance hardening.
- [x] `0.4.9` — privacy-safe Playlist diagnostics, adaptive UI, and complete TalkBack contracts.
- [x] `0.4.10` — final Playlist regression, accessibility, resilience, runtime, security, and performance gate before Queue.

The 0.4.5–0.4.10 batch ships as one cumulative 0.4.10 artifact/versionCode 45 and one
validation/publication cycle. The design follows public Spotify collaboration concepts—
expiring invites, explicit access roles, public/private reach, bounded pages, and
snapshot-like revision safety—through original Rakyzu Music code and infrastructure.

- [x] `0.5.0` — account-scoped queue foundation, deterministic editing, Room persistence, and Media3 synchronization.
- [x] `0.5.1` — add-next/add-to-queue entry points and duplicate-safe ordering.
- [x] `0.5.2` — reorder/remove queue controls with complete TalkBack actions.
- [x] `0.5.3` — process-death playback and queue restoration with stale-media handling.
- [x] `0.5.4` — autoplay boundary and explicit listener controls.
- [x] `0.5.5` — playback-device state foundation without remote-control claims.

The 0.5.1–0.5.5 batch ships as one cumulative 0.5.5 artifact, versionCode 51, and one
validation/publication cycle. Queue editing remains listener-directed: duplicate requests are
deterministic, recommendations are never appended implicitly, restoration is paused, and only
the local playback device is represented.
- [x] `0.5.6` — audio-quality and data-usage preferences enforced at the media source.
- [x] `0.5.7` — offline/degraded queue reconciliation and recovery.
- [x] `0.5.8` — adaptive queue layout, large-text behavior, and TalkBack regression.
- [x] `0.5.9` — privacy-safe playback diagnostics and performance hardening.
- [x] `0.5.10` — final Queue/playback regression, accessibility, resilience, and security gate.

The cumulative 0.5.6–0.5.10 artifact uses versionCode 56. Its Wi-Fi/mobile choices follow the
public [Spotify audio-quality concept](https://support.spotify.com/id-id/article/audio-quality/),
but use original Rakyzu labels and only the `low`, `standard`, and `high` variants actually
defined by Rakyzu storage. Data Saver lowers metered playback, failures retain only the explicit
queue for bounded recovery, and diagnostics contain coarse fixed state rather than listener or
catalog data. Runtime playback still requires rights-cleared media objects for the selected R2
variant; the release gate must not imply a catalog or remote-device capability that does not exist.

- [x] `0.5.11` — fixed organization role hierarchy, permission matrix, and account assignments.
- [x] `0.5.12` — forced-RLS moderation, audit, and catalog administration RPC boundaries.
- [x] `0.5.13` — dynamically role-gated Android Admin Panel and server-authoritative session context.
- [x] `0.5.14` — artist/album/track drafting, bounded private R2 MP3 upload, and atomic publication.
- [x] `0.5.15` — role-specific moderation actions, staff assignment controls, accessibility, and security regression.
- [x] `0.5.16` — content takedown/quarantine workflow with reversible reasoned decisions.
- [x] `0.5.17` — artist and label team scopes without widening organization-global access.
- [x] `0.5.18` — artwork/release review queues, approval previews, and scheduled publication.
- [x] `0.5.19` — privacy-safe audit search, export, retention controls, and anomaly diagnostics.
- [x] `0.5.20` — final Admin/moderation runtime, hierarchy, resilience, accessibility, and performance gate.

The CEO-directed extended 0.5 train ships `0.5.11`–`0.5.15` as one cumulative artifact with
versionCode 61. Its access-level vocabulary is informed by
[Spotify for Artists' public access levels](https://support.spotify.com/mx-en/artists/article/access-levels-in-spotify-for-artists/),
while Rakyzu uses its own ranked organization model. Android
never grants authority: Supabase RLS/RPC and the Rakyzu Worker independently fail closed.

The cumulative `0.5.16`–`0.5.20` artifact uses versionCode 66. Enforcement is append-only and
reversible; catalog teams are scope-bound; publication requires independent artwork and release
approval; scheduled releases remain invisible to listeners until their server time; and raw audit
tables stay unavailable to Android. Retention changes record policy intent only—physical deletion
remains a separately approved maintenance operation.

### Artist identity and strengthened Control Room — CEO extension

- [x] `0.5.21` — optional exact-email Artist account link, server-authoritative pending/active/revoked
  identity, and automatic matching for an existing or future account. No extra Artist email challenge.
- [x] `0.5.22` — in-Home welcome, versioned terms, checked consent, and immutable consent evidence
  before the matched Artist identity activates.
- [x] `0.5.23` — distinct Artist and rank-specific staff profile identity, revocable verification badge,
  role-color animation with default toggle, and a self-scoped Artist biography workspace.
- [x] `0.5.24` — authenticated profile image for every account and validated MP3/AAC/M4A/WebM/WAV/FLAC
  audio upload/streaming; album and recommendation images accept JPEG, PNG, or WebP selection.
- [x] `0.5.25` — CEO-requested staff email bootstrap, existing/new Artist and album edit/archive,
  and full seeded/new Home recommendation edit/order/target/image/delete controls.
- [x] `0.5.26` — expand Artist workspace to scoped imagery, team, catalog drafts, and release
  submissions without organization-global staff permissions or self-publication.
- [x] `0.5.27` — privacy-thresholded Artist/Admin analytics for streams, listeners, followers,
  releases, geography, date ranges, and CSV export; add trusted event collection first.
- [x] `0.5.28` — Admin commerce dashboard for user subscriptions and purchases, provider-signed
  webhook ledger, entitlements, receipts, refunds, disputes, and least-privilege visibility.
- [x] `0.5.29` — reasoned listener/Artist suspension or ban, appeal, badge/privilege revocation,
  reinstatement, and retention-aware account-deletion requests with dual control.
- [x] `0.5.30` — strengthened Admin security alerts, destructive-action approval, scoped export,
  accessibility, performance, resilience, and Artist lifecycle release gate.

Spotify's current public wording distinguishes a profile-management registration mark from broader
quality endorsement. Rakyzu's badge will therefore state exactly what was verified, be controlled
only by trusted server state, and be revocable. An optional Artist account email is an exact
account match, not a second confirmation step; privileges activate only after the matched
authenticated account accepts the current terms.

The cumulative `0.5.26`–`0.5.30` Android artifact originally used versionCode `76`; its profile-context hotfix uses versionCode `77`. Signed event ingestion,
privacy thresholds, commerce evidence, account enforcement, dual-control deletion, and security
approval stay server-authoritative; no provider secret or Supabase service key enters Android.

- [x] `0.6.0` — encrypted, account-scoped download foundation with rights/entitlement boundary.
- [x] `0.6.1` — explicit album and playlist download actions with per-item progress.
- [x] `0.6.2` — Wi-Fi-first scheduler plus an opt-in mobile-download setting.
- [x] `0.6.3` — offline playback resolution without exposing raw R2 objects or credentials.
- [x] `0.6.4` — storage usage summary, available-space guard, and clear-download controls.
- [x] `0.6.5` — pause, resume, cancel, retry, and process-death-safe download work.
- [x] `0.6.6` — catalog revision and removed-content reconciliation with non-destructive cleanup.
- [x] `0.6.7` — per-account/device download isolation and sign-out retention policy.
- [x] `0.6.8` — adaptive Download UI, large-text layout, TalkBack status and action regression.
- [x] `0.6.9` — privacy-safe download diagnostics, bounded concurrency, and performance hardening.
- [x] `0.6.10` — final Offline/storage resilience, runtime, security, accessibility, and quota gate.
- [x] `0.7.0` — bounded track-context models for licensed lyrics and credits, canonical app-share
  contract, notification preference foundation, and an adaptive Android Sharesheet entry point.

The 0.6 plan takes only public concepts from Spotify's
[offline listening](https://support.spotify.com/us/article/listen-offline/) and
[storage guidance](https://support.spotify.com/us/article/storage-information/): explicit status,
Wi-Fi-first transfer, storage visibility, and listener controls. Rakyzu limits, entitlements,
encryption, expiry, and rights policy will be defined by its own licensed catalog and backend.

The cumulative `0.6.0`–`0.7.0` source uses Android versionCode `89`. A dedicated authenticated
Worker route rechecks catalog rights before issuing a bounded offline license; Android encrypts
the response with an account-derived, non-exportable Keystore key and stores only opaque local
file tokens. Room 10 persists content revisions and an explicit per-account sign-out policy;
WorkManager persists transfer state and network policy across process death.

### Lyrics, credits, sharing, and notifications — `0.7.x`

- [x] `0.7.1` — authenticated, rights-aware track-context API and local cache with explicit
  unavailable, plain-lyrics, and time-synced-lyrics states.
- [x] `0.7.2` — accessible lyrics sheet, current-line focus, manual scrolling, provider attribution,
  and fail-closed licensing behavior.
- [x] `0.7.3` — normalized songwriter, producer, performer, featured-artist, and primary-artist
  credits with source attribution and bounded contributor lists.
- [x] `0.7.4` — canonical share-link resolution, validated inbound track deep links, preview-safe
  metadata, and graceful unavailable-track handling.
- [x] `0.7.5` — Android notification permission education, stable channel taxonomy, and per-channel
  device controls without dark patterns.
- [x] `0.7.6` — followed-Artist and saved-Artist release preferences, server-authoritative delivery,
  deduplication, quiet behavior, and direct preference revocation.
- [x] `0.7.7` — offline lyrics/credits caching with account isolation, revision reconciliation,
  bounded retention, and explicit storage visibility.

The cumulative `0.7.1`–`0.7.7` artifact uses Android versionCode `96`, Room schema `11`, and one
rights-aware Worker boundary. Smart Recommendation is included ahead of the broader `0.8.x`
personalization train as a private, deterministic on-device ranker: recent playback, Likes, saved
albums, and followed Artists influence a bounded, artist-diverse shelf, while consumed tracks are
excluded and recommendations are never appended to playback automatically.
- [x] `0.7.8` — replace provider-oriented lyrics metadata with Rakyzu-owned draft/publish/delete
  lifecycle, immutable revisions, server validation, and staff/Artist scope enforcement.
- [x] `0.8.0` — ship Rakyzu Lyrics Studio with manual authoring, bounded editable `.LRC`/`.SRT`
  import, language metadata, listener-safe publication, and first-party attribution.

This plan uses only the public product concepts described by Spotify's
[lyrics guidance](https://support.spotify.com/article/lyrics/) and
[song-credit guidance](https://support.spotify.com/article/song-credits/). Lyrics text, credit
records, provider attribution, notification policy, and sharing infrastructure must be licensed
or owned by Rakyzu; no Spotify catalog data, code, assets, or private behavior is copied.
- [x] `0.8.1` — persist account-scoped weighted listening history in Room schema 12 and surface
  bounded play counts without sending listening activity to a third party.
- [x] `0.8.2` — add a private taste profile with pause/resume and per-track taste exclusion.
- [x] `0.8.3` — generate deterministic daily Rakyzu Mixes locally from listening and Library signals.
- [x] `0.8.4` — derive bounded track and Artist radio stations without an external recommender API.
- [x] `0.8.5` — explain each recommendation and persist account-scoped hide/exclude feedback.
- [x] `0.8.6` — add Familiar, Balanced, and Explore discovery modes with stable daily rotation and
  an artist-diversity ceiling.
- [x] `0.8.7` — adapt Home spacing/cards for compact, medium, expanded, and large-text layouts;
  preserve headings, traversal groups, live regions, and 48 dp interaction targets.
- [x] `0.8.8` — add transactional personalization reset, privacy-bucketed diagnostics, bounded
  counters, failure-safe controls, migration coverage, and the cumulative release gate.
- [x] `0.8.8.1` — deliver branded interactive Welcome, Sign In, Log In, and Sign Up screens;
  wire password auth, native Google, Supabase Facebook, the Apple availability notice, first-run
  persistence, sign-out routing, app icon, and public legal pages without changing the 0.8 train.
- [x] `0.8.8.2` — ship the interactive Explore surface with four Your Top Genres groups, six Browse
  All groups, private Recommended For You cards, long-press Card Group editing, real group detail
  playback, artwork/ambient colors, permission-checked editorial RPCs, privacy-preserving engagement
  ranking, notification primer, and Room schema 13 metadata migration.
- [ ] `0.8.9` — harden recommendation cold-start quality, catalog churn handling, and restoration
  coverage from the `0.8.8` production baseline.
- [ ] `0.8.10` — complete the `0.8.x` accessibility, performance, security, and release audit before
  opening the `0.9.x` subscription and entitlement train.
- `0.9.x`: subscriptions/entitlements, privacy, and advanced trust-and-safety governance.
- `0.10.x`: release candidate hardening, accessibility, localization, performance, security, Play readiness.
- `1.0.0`: signed production release after all quality gates pass.

No Web or iOS implementation begins before Android `1.0.0` is stable and accepted.
