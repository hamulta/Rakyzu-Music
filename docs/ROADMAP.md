# Android Release Roadmap

The release train follows the CEO-defined cadence. Each line may contain up to ten validated patches before advancing. Scope can move between patch releases when quality requires it, but version order does not skip.

## Foundation — 0.0.x

- `0.0.1` — complete: repository, Android modular scaffold, Rakyzu design baseline, test/lint/build CI.
- `0.0.2` — complete: adaptive app shell, Navigation 3, accessibility baseline.
- `0.0.3` — complete: Supabase project bootstrap, migrations, RLS test strategy.
- `0.0.4`: email authentication and secure session lifecycle.
- `0.0.5`: profile/onboarding and account recovery.
- `0.0.6`: catalog domain and offline-first database.
- `0.0.7`: Worker API contract and authenticated R2 delivery spike.
- `0.0.8`: Media3 playback service and notification controls.
- `0.0.9`: queue, mini player, Now Playing foundation.
- `0.0.10`: foundation hardening, performance and security review.

## Product milestones

- `0.1.x`: Home feed, recently played, editorial shelves.
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
