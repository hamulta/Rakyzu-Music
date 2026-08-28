# Android Architecture

Status: baseline for `0.0.5`.

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
- `core:data`: Supabase client assembly, authentication repository, session state mapping, and encrypted Android persistence.
- `core:model`: platform-independent product models and formatting rules.
- `core:designsystem`: Rakyzu tokens, typography, colors, and reusable primitives.
- `feature:auth`: email sign-in/sign-up UI and its unidirectional state holder.
- `feature:home`: the first independently owned feature surface.
- `feature:profile`: required display-name onboarding, profile loading/edit state, and degraded profile UI.

Planned boundaries include `core:database`, `core:network`, `core:playback`, `feature:search`, `feature:library`, and `feature:player`.

## Data and media security

The Android app never connects to R2 using S3 credentials. Catalog metadata and user operations go through Supabase with RLS or through a Cloudflare Worker using the user's verified JWT. Protected audio is returned through authorization-aware streaming or short-lived signed URLs. Upload and catalog administration belong to a separate privileged surface.

Supabase schema changes are committed as ordered forward migrations. Client-facing tables start with RLS enabled and forced, explicit role grants, and pgTAP coverage for both allowed and denied access. The initial `profiles` boundary is private to its authenticated owner; privileged lifecycle operations remain server-controlled.

The app observes Supabase `sessionStatus` as the sole authentication navigation source. Email confirmation callbacks use PKCE through the branded Android deep link. The SDK's session and verifier stores are replaced by AES-GCM persistence whose non-exportable key is generated in Android Keystore; corrupt or invalidated encrypted state is discarded and requires a new sign-in. Auth failures are mapped to bounded product errors so raw backend responses and tokens never reach the UI or logs.

Password recovery uses a separate exact deep-link path and only marks recovery after receiving a PKCE authorization code. The recovery requirement is encrypted with the session state, survives process death, and is cleared only after a successful password update or local sign-out. Recovery-email responses do not reveal whether an account exists.

The authenticated shell loads the current user's `profiles` row through Postgrest using the same short-lived Auth JWT. Both the explicit `id` filter and database RLS enforce ownership. New and existing incomplete profiles remain behind a profile gate until the listener saves a valid display name; profile network failures degrade to a retry surface rather than bypassing onboarding or crashing navigation.

## Reliability

- A local database becomes the single source of truth for cached catalog/library data.
- Remote synchronization is incremental, paginated, and retry-aware.
- Playback uses a Media3 `MediaSessionService` so audio survives UI lifecycle changes.
- Each feature exposes immutable UI state and handles unavailable dependencies as a degraded state rather than crashing the entire client.

## Release topology

GitHub Actions runs deterministic checks and publishes a branded debug APK artifact for every validated push. Signed release artifacts and Play Console delivery will be introduced only after signing secrets and the release channel are provisioned.
