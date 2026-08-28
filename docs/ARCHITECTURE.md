# Android Architecture

Status: baseline for `0.0.2`.

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
- `core:model`: platform-independent product models and formatting rules.
- `core:designsystem`: Rakyzu tokens, typography, colors, and reusable primitives.
- `feature:home`: the first independently owned feature surface.

Planned boundaries include `core:data`, `core:database`, `core:network`, `core:playback`, `feature:auth`, `feature:search`, `feature:library`, and `feature:player`.

## Data and media security

The Android app never connects to R2 using S3 credentials. Catalog metadata and user operations go through Supabase with RLS or through a Cloudflare Worker using the user's verified JWT. Protected audio is returned through authorization-aware streaming or short-lived signed URLs. Upload and catalog administration belong to a separate privileged surface.

## Reliability

- A local database becomes the single source of truth for cached catalog/library data.
- Remote synchronization is incremental, paginated, and retry-aware.
- Playback uses a Media3 `MediaSessionService` so audio survives UI lifecycle changes.
- Each feature exposes immutable UI state and handles unavailable dependencies as a degraded state rather than crashing the entire client.

## Release topology

GitHub Actions runs deterministic checks and publishes a branded debug APK artifact for every validated push. Signed release artifacts and Play Console delivery will be introduced only after signing secrets and the release channel are provisioned.
