# Android Architecture

Status: baseline for `0.1.9`.

## Goals

Rakyzu Music uses the parts of Spotify's published engineering approach that fit an early-stage product: feature ownership, graceful degradation, local persistence, batched synchronization, independent services, and a consistent developer path. It does not attempt to reproduce Spotify's private platform or scale prematurely.

```text
Compose UI -> ViewModel/state holder -> domain use case -> repository
                                                     |-> local database (source of truth)
                                                     |-> Rakyzu Worker API
                                                           |-> Supabase
                                                           |-> R2 media
```

The initial UI modules are intentionally small. Data, domain, database, network, authentication, and playback modules are added at the milestone where they become executable rather than as empty scaffolding.

## Android modules

- `app`: application assembly, activity, navigation host, and build/release configuration.
- `core:data`: Supabase client assembly, authentication/profile/media-request repositories, catalog synchronization, session state mapping, and encrypted Android persistence.
- `core:database`: Room 3 catalog/editorial entities, account-isolated listening history, transactional replacement DAO, schema history, and observable local data source.
- `core:model`: platform-independent product models and formatting rules.
- `core:playback`: Media3 player/session ownership, authenticated stream resolution, audio focus, system controls, and playback state.
- `core:designsystem`: Rakyzu tokens, typography, colors, and reusable primitives.
- `feature:auth`: email sign-in/sign-up UI and its unidirectional state holder.
- `feature:home`: offline-first editorial feed, recently played, new releases, and complete-catalog shelves.
- `feature:player`: branded Now Playing, seek controls, transport actions, and the current Media3 queue surface.
- `feature:profile`: required display-name onboarding, profile loading/edit state, and degraded profile UI.

Planned boundaries include `core:network`, `feature:search`, and `feature:library`.

## Data and media security

The Android app never connects to R2 using S3 credentials. Catalog metadata and user operations go through Supabase with RLS or through a Cloudflare Worker using the user's verified JWT. Protected audio is returned through authorization-aware streaming or short-lived signed URLs. Upload and catalog administration belong to a separate privileged surface.

Supabase schema changes are committed as ordered forward migrations. Client-facing tables start with RLS enabled and forced, explicit role grants, and pgTAP coverage for both allowed and denied access. The initial `profiles` boundary is private to its authenticated owner; privileged lifecycle operations remain server-controlled.

The app observes Supabase `sessionStatus` as the sole authentication navigation source. Email confirmation callbacks use PKCE through the branded Android deep link. The SDK's session and verifier stores are replaced by AES-GCM persistence whose non-exportable key is generated in Android Keystore; corrupt or invalidated encrypted state is discarded and requires a new sign-in. Auth failures are mapped to bounded product errors so raw backend responses and tokens never reach the UI or logs.

Password recovery uses a separate exact deep-link path and only marks recovery after receiving one bounded PKCE authorization code. An application-owned parser rejects any callback whose scheme, raw authority, path, query shape, or fragment differs from the allowlist. `MainActivity` uses `singleTop` and applies the same parser to cold-start and `onNewIntent` callbacks. The recovery requirement is encrypted with the session state, survives process death, and is cleared only after a successful password update or local sign-out. Recovery-email responses do not reveal whether an account exists. The Android network security policy independently rejects cleartext traffic.

The authenticated shell loads the current user's `profiles` row through Postgrest using the same short-lived Auth JWT. Both the explicit `id` filter and database RLS enforce ownership. New and existing incomplete profiles remain behind a profile gate until the listener saves a valid display name; profile network failures degrade to a retry surface rather than bypassing onboarding or crashing navigation.

Published catalog and editorial tables expose only metadata to the authenticated role and allow no client mutations. Editorial entry policies require both their shelf and referenced track to remain published. Android fetches bounded columns, validates identifiers, parent relationships, durations, album positions, shelf limits/order, and track membership, then replaces the Room snapshot in one transaction. Room emits the canonical Home graph as a Flow; failed or invalid refreshes never delete the previous verified snapshot. Media locations remain outside catalog rows.

`services/rakyzu-api` is the Cloudflare Worker delivery boundary. `GET /v1/health` is public; `GET` and `HEAD` requests to `/v1/tracks/{uuid}/stream` and `/v1/albums/{uuid}/artwork` require a Supabase bearer JWT. The Worker verifies ES256 signatures against the project's remote JWKS with a fixed issuer, `authenticated` audience and role, and a UUID subject. It then queries the requested catalog resource through PostgREST using that same session, so the database's published-only RLS policy is re-evaluated before any R2 access.

The client supplies only a validated track UUID. The Worker derives `media/tracks/{uuid}/source.mp3`, reads the private `MEDIA` R2 binding, and streams the object body without buffering it. Full responses, `HEAD`, open-ended ranges, bounded ranges, suffix ranges, and `416` responses share strict private/no-store, CORS, request-ID, and security-header behavior. R2 S3 credentials and Supabase privileged keys never enter the Worker bundle or APK.

Album artwork uses the deterministic private key `media/albums/{uuid}/artwork.webp` after the same JWT and RLS checks. The Worker accepts only AVIF, JPEG, PNG, or WebP metadata, returns an ETag, honors exact `If-None-Match` requests with `304`, and applies `private, max-age=86400`; missing or unauthorized albums remain non-disclosing `404` responses. Android builds each Coil request from the authenticated media boundary, holds the bearer value only in request headers, and redacts it from object diagnostics. A process-wide Coil loader uses a bounded 25% memory cache, a 128 MiB disk cache under the application cache directory, HTTP cache-control semantics, and crossfades. Cache keys use normalized album UUIDs rather than credential-bearing URLs or headers.

Media3 owns playback inside `RakyzuPlaybackService`, independently of Compose and activity lifecycles. Catalog selections become internal `rakyzu://tracks/{uuid}` media items containing only public display metadata. A resolving data source validates that URI, asks the application data boundary for the current in-memory bearer session at request-open time, and accepts only an authenticated HTTPS response contract. Tokens are not embedded in the MediaItem, notification, queue metadata, or persisted state. Untrusted external media controllers are rejected; system-trusted controllers retain lock-screen, headset, Bluetooth, and notification interoperability.

The Media3 timeline is the playback queue source of truth. `RakyzuPlaybackController` maps its current item, queue metadata, transport availability, errors, and bounded position/buffer/duration values into one immutable `PlaybackSnapshot`. Media3 callbacks publish structural changes; queue metadata is rebuilt only for a changed timeline or item count. A lifecycle-aware coroutine sampler starts only while playback is active, cannot create duplicate jobs, and is cancelled immediately for static playback or session clearing. Compose consumes that snapshot in both the compact player and `feature:player`; seeking and queue selection return through controller commands rather than mutating UI-local playback state.

Media3 item transitions are the listening-history event source, so manual selection, queue navigation, and automatic advance share one path. The application resolves the current authenticated listener at event time and writes only the track ID and timestamp to Room. History is capped at 20 entries per listener, survives catalog snapshot replacement, and is joined back to the current verified track graph before Home renders it.

## Reliability

- Room is the single source of truth for cached Home/catalog data and device-local recently played; library persistence follows at its roadmap milestone.
- Catalog synchronization currently uses a validated full snapshot. Pagination and incremental cursors are introduced when catalog scale requires them.
- Playback uses a Media3 `MediaSessionService` so audio survives UI lifecycle changes; ExoPlayer owns audio focus and pauses for noisy-output events.
- Queue order, current index, transport availability, and playback progress come from the Media3 timeline; UI-local slider state exists only during a seek gesture.
- Each feature exposes immutable UI state and handles unavailable dependencies as a degraded state rather than crashing the entire client.
- Home derives playable, saved, and successful-empty states from its immutable catalog state. Empty feeds suppress inactive filters and hero playback, expose one refresh action, and announce loading or degraded transitions through polite accessibility live regions. Concurrent refresh requests are coalesced by the Home state holder.
- Home derives relative feed freshness from Room's persisted catalog synchronization timestamp. Unknown and future timestamps fail safely, while a failed refresh distinguishes a recent saved catalog from one at least 24 hours old and exposes the stale warning alongside accessible freshness metadata.
- Home resolves one immutable layout policy from the current width and font scale. Widths below 360dp or font scales from 1.3x use compact horizontal padding, a vertically stacked featured card, shelf cards up to 220dp wide, and two text lines; standard screens retain the denser shelf presentation.
- Home canonicalizes structurally equal catalog and listening-history snapshots at the state-holder boundary. Featured playback and new-release ordering are cached derived sections rebuilt only for a real catalog change; scalar status inputs, stable callbacks, and remembered card labels keep unrelated refresh recompositions outside shelf content.
- Catalog refresh coalesces concurrent triggers and applies two bounded transient retries after 1 second and 3 seconds. Invalid payloads fail immediately; loss of validated Android connectivity cancels further backoff, exposes an accessible waiting state, and causes one immediate recovery refresh when the default network is validated again. The process-scoped monitor registers callbacks only while collected and never polls.
- Each Home shelf is an accessibility traversal group. Track controls preserve the Media3 queue order through explicit traversal indices, expose a track-specific `Play` action with button semantics, and forward the same list/index pair used by visual taps. Profile, filters, retry/refresh, featured playback, and track cards enforce a minimum 48dp interactive height; Compose instrumentation coverage guards those contracts.
- Home artwork has explicit placeholder, loading, loaded, and failure states. Decorative cover images add no duplicate TalkBack description; their enclosing track controls remain the semantic owner, while loading and failure visuals preserve the established card geometry and touch targets.
- Home persists its filter as a version-tolerant stable key and restores unknown values to Music. Compose `LazyListState` savers retain the vertical feed, filter row, and every horizontal shelf position across saved-instance recreation. Explicit section, shelf, and track keys preserve identity when refresh status or catalog rows change, and the complete saveable subtree is keyed by listener ID so one account never inherits another account's navigation position.
- Home records process-local structured diagnostics for feed-shape emissions, connectivity transitions, refresh triggers, coalesced requests, scheduled retries, outcomes, bounded attempt ordinals, and monotonic duration buckets. Content quantities are reduced to fixed buckets; listener/catalog identifiers, titles, URLs, tokens, payloads, and raw exceptions are absent by construction. The FIFO keeps 32 events with a hard ceiling of 64, rendered lines are capped at 240 characters, and sink failures are isolated from product state.

## Release topology

GitHub Actions runs deterministic Android checks and publishes a branded debug APK artifact for every validated push. Android workflow actions are pinned to verified full-length commits, wrapper validation is enabled, and every run also builds the minified/resource-shrunk release variant to exercise R8 before publication. A separate path-filtered workflow type-checks, tests, checks generated Worker bindings, dry-runs the bundle, and deploys the API only after verification succeeds. Signed Android artifacts and Play Console delivery will be introduced only after signing secrets and the release channel are provisioned.
