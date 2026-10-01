# Rakyzu Music — engineering work sessions

## Completed cumulative 0.8.1–0.8.8 batch — 2026-09-30 to 2026-10-01

- [x] Audit the tagged `v0.8.0` baseline and preserve the unrelated `gradlew` mode change.
- [x] `0.8.1`: add bounded weighted, account-scoped listening history in Room schema 12.
- [x] `0.8.2`: add local private taste-profile enablement and per-track taste exclusion.
- [x] `0.8.3`: add deterministic daily Rakyzu Mixes.
- [x] `0.8.4`: add local track and Artist radio.
- [x] `0.8.5`: add explanation, hide, and restore controls for recommendations and signals.
- [x] `0.8.6`: add Familiar/Balanced/Explore modes, daily rotation, and Artist diversity bounds.
- [x] `0.8.7`: add compact/medium/expanded/large-text Home layout and accessibility coverage.
- [x] `0.8.8`: add transactional reset, bounded privacy diagnostics, failure containment, dead-code
  cleanup, migration coverage, and synchronized release metadata.
- [x] Complete Android, Worker, database, dependency, secret, APK identity/signature, and diff gates.
- [x] Deploy Worker `0.8.8` and verify production health.
- [x] Push, tag, publish, download, and audit the
  cumulative GitHub Release artifact.

All recommendation computation and listener feedback remain account-scoped and device-local. No
third-party recommendation API or new Supabase personalization table is used.

The Android gate passed 249 unit tests with zero failures, errors, or skips; all 14 module lint
reports completed; and the debug APK, 14 Android-test APKs, and minified/resource-shrunk release
APK assembled. The signed debug candidate identifies as `my.id.rakyzumusic`, versionName `0.8.8`,
versionCode `106`, min SDK 26, target/compile SDK 37, and one APK Signature Scheme v2 signer. It is
29,646,058 bytes with SHA-256
`af18f14e53ec5117746de59def40e085f293e7a1b2c21b75558e8c00eaebb743`. Room schema 12 and its
11→12 migration test are checked in; the migration preserves existing history with play count one
and adds account-scoped preferences/feedback.

Worker validation passed 79 tests, generated-binding verification, both TypeScript targets, a
no-write deployment dry-run, and a production dependency audit with zero vulnerabilities.
Cloudflare uploaded Worker version `0d6faca2-a3cd-40cd-aaf7-f267a07d05d6`; production
`/v1/health` returns `status: ok`, version `0.8.8`, with the R2 binding intact. All 20 local
Supabase migrations match production and remote `public`/`private` lint returned no findings; no
database migration was required for device-local personalization. The tracked credential-prefix
scan is clean and `credential.env` remains ignored.

Commit `bc6156a` was pushed to `main`, tagged with annotated tag `v0.8.8`, and published as an
Android prerelease. The downloaded `Rakyzu-Music-0.8.8-debug.apk` is byte-identical to the locally
validated candidate: 29,646,058 bytes, SHA-256
`af18f14e53ec5117746de59def40e085f293e7a1b2c21b75558e8c00eaebb743`, package
`my.id.rakyzumusic`, versionName `0.8.8`, versionCode `106`, min SDK 26, target/compile SDK 37,
and one valid v2 signer.

GitHub accepted the push but its Actions backend again created synthetic run `36827351152` at
path `BuildFailed`; it completed with `startup_failure`, zero jobs, and zero check-runs. The active
Android workflow has push/PR triggers but no manual-dispatch trigger, so the attempted explicit
dispatch was rejected before a run could be created. No green remote run is claimed. The complete
local Android gate, Worker gate, linked Supabase checks, production Worker health, and downloaded
release-asset audit above provide the release evidence.

## Active cumulative 0.7.8 and 0.8.0 batch — 2026-09-30

- [x] Audit the `0.7.7` baseline and confirm listener lyrics use the owned Rakyzu API/database.
- [x] `0.7.8`: add first-party lyrics lifecycle, immutable revisions, RLS isolation, role/scope
  enforcement, and draft-only Artist editing.
- [x] `0.8.0`: add manual/LRC/SRT parsing and an editable Lyrics Studio to the Android Admin Panel.
- [x] Add fail-closed size, line, timestamp, language, publication, and listener-projection checks.
- [x] Run the complete Android, Worker, database, dependency, and secret-leak gates.
- [x] Apply the forward Supabase migration, deploy Worker `0.8.0`, and verify production health.
- [x] Explicitly stage validated paths, commit/push, tag, publish, download, and audit the final
  GitHub Release artifact; record the GitHub Actions backend startup incident without claiming a
  green remote run.

No third-party lyrics API is present. Published listeners receive only a rights-aware Rakyzu
projection; drafts and immutable history remain inaccessible through direct client tables.

The final local Android gate completed 1,637 Gradle tasks and passed 240 unit tests with zero
failures, errors, or skips. All 14 module lint reports completed; debug, instrumentation, and
minified/resource-shrunk release APKs assembled. The signed debug candidate identifies as
`my.id.rakyzumusic`, versionName `0.8.0`, versionCode `98`, min SDK 26, target SDK 37, and one v2
signer. Its pre-CI SHA-256 is
`cf4e935a3f21c31ea17757078be47ca91bd7dd4af310a6e3b9094a824b1b49be`.

Worker validation passed 79 tests, both TypeScript targets, generated-binding verification, and a
no-write Wrangler dry-run; the production dependency audit reports zero vulnerabilities. Supabase
accepted the forward first-party lyrics migration, all 20 local/remote migrations match, and
remote `public`/`private` lint returned no findings. Cloudflare deployed Worker version
`14dcace4-b171-494d-b2dd-65ec5bb4cf77` with the R2 binding, and the public health endpoint returned
`status: ok`, version `0.8.0`. The pending-source credential-prefix scan is clean and
`credential.env` remains ignored.

Commit `a6605bf` was pushed to `main`, tagged `v0.8.0`, and published as an Android prerelease.
The downloaded `Rakyzu-Music-0.8.0-debug.apk` is byte-identical to the validated local candidate:
29,580,522 bytes, SHA-256
`cf4e935a3f21c31ea17757078be47ca91bd7dd4af310a6e3b9094a824b1b49be`, package
`my.id.rakyzumusic`, versionName `0.8.0`, versionCode `98`, min SDK 26, target SDK 37, and one valid
v2 signer.

GitHub accepted the push but its Actions backend created synthetic run `36762165615` against a
deleted workflow path named `BuildFailed`; it ended in `startup_failure` with zero jobs, zero
check-runs, and no logs. Direct dispatches of the active Database and Worker workflows produced
the same pre-job failure in runs `36762360921` and `36762368360`. Consequently no green GitHub
run is claimed for this release. The equivalent Android, PostgreSQL, and Worker gates were run
successfully in the workspace, and both production services were verified before the tag.

## Active cumulative 0.7.1–0.7.7 batch — 2026-09-30

- [x] Audit the tagged `v0.7.0` baseline and preserve the pre-existing `gradlew` mode change.
- [x] `0.7.1`: add authenticated rights-aware track-context API and account-scoped local cache.
- [x] `0.7.2`: add licensed plain/time-synced lyrics, current-line focus, and attribution UI.
- [x] `0.7.3`: add bounded normalized and source-attributed song credits.
- [x] `0.7.4`: validate inbound canonical track links and add preview-safe Worker resolution.
- [x] `0.7.5`: add Android notification permission education and stable channel taxonomy.
- [x] `0.7.6`: add server-authoritative followed/saved Artist release preferences and delivery queue.
- [x] `0.7.7`: add Room 10→11 offline track-context cache and catalog reconciliation.
- [x] Add private on-device Smart Recommendation with bounded scoring and Artist diversity.
- [x] Run the cumulative Android unit, lint, debug/instrumentation, and release R8 gates.
- [x] Replay/test/lint/apply the forward Supabase migration and confirm remote parity.
- [x] Run Worker type/tests/dry-run, deploy the exact source, and verify production health.
- [x] Inspect the complete diff, secret scan, versions, generated schema, and release artifact.
- [x] Explicitly stage validated paths, commit, push `main`, verify CI, tag, and publish prerelease.
- [x] Download the release APK and verify identity, signature, checksum, and byte equality.

The batch intentionally adds Smart Recommendation as a local privacy-first slice before `0.8.x`.
Only verified Room catalog and account-local signals are ranked; no raw listening profile is added
to Worker requests, no recommendation enters the explicit queue automatically, and a signal-free
account is not presented with falsely personalized content.

The final local Android gate passed 236 unit tests with zero failures, errors, or skips; all 14
module lint gates completed, and the debug, instrumentation, Room migration-test, and
R8/resource-shrunk release APKs assembled. The signed debug candidate identifies as
`my.id.rakyzumusic`, versionName `0.7.7`, versionCode `96`, min SDK 26, target SDK 37, and one v2
signer; its pre-CI SHA-256 is
`c76bb9cdd9f5d07f9dc4728d2b6e320dad30cf9c19904ddbbd1cd1d764d59ff8`.

Worker validation passed 76 tests, both TypeScript checks, generated-binding verification, and a
no-write Wrangler dry-run. Production dependency audit reported zero vulnerabilities. The forward
PostgreSQL migration passed 14 transactional pgTAP assertions through the official Management API,
remote `public`/`private` lint returned no errors, and all 19 local migrations now match production.
Worker version `0.7.7` was deployed and its production health endpoint returned `ok`. Tracked
credential-prefix and exact-sensitive-value scans were clean; `credential.env` remains ignored.

### 0.7.7 public-configuration release correction — 2026-09-30

- [x] Reproduce the released APK's missing-public-configuration state and identify the build input
  omission.
- [x] Add a fail-fast artifact gate for the hosted Supabase origin, non-privileged publishable key,
  and canonical Rakyzu API origin.
- [x] Prove the gate fails without configuration and passes with the ignored local environment.
- [x] Run the configured unit, lint, debug, Android-test, and R8 release build once.
- [x] Exercise sign-up, first-run profile creation, Home, sign-out, existing-account sign-in, and
  Home on Appetize Android 16.
- [x] Remove the temporary Supabase user, R2 transfer object, Appetize session, and Appetize build.
- [x] Replace and re-download the corrected GitHub Release APK, checksum, and audit evidence.

The release process had loaded no public Android configuration for the original local artifact.
The runtime failed closed as designed, but no build-time control stopped that unusable APK from
being published. `preBuild` now depends on a configuration verifier, so local and CI builds reject
missing or malformed public origins as well as any Supabase secret/service-role key before an APK
can be assembled.

The configured cumulative Gradle gate completed 1,637 tasks successfully, covering unit tests,
all module lint checks, debug and Android-test APKs, and the minified/resource-shrunk release build.
The corrected debug APK contains all required public values and has SHA-256
`84739ccfaf5a6fe2bb89020dd3447dae98ad255ca8c7fc499c8b55e31ff5a6a5`.

On an Appetize Pixel 9 Pro running Android 16, a new temporary listener completed sign-up,
confirmation, profile onboarding, and Home. Sign-out returned to the sign-in screen, and a second
sign-in as the now-existing listener returned directly to Home. The captured UI contained neither
missing configuration nor Profile Unavailable, and the device log contained no application fatal
exception or ANR. All temporary remote test state was removed after verification.

## Completed cumulative 0.6.6–0.7.0 batch — 2026-09-29

- [x] Audit the clean `v0.6.5` baseline and preserve the pre-existing `gradlew` mode change.
- [x] `0.6.6`: reconcile removed/restored catalog tracks non-destructively and persist bounded
  server content revisions.
- [x] `0.6.7`: add account/device isolation and explicit remove-or-keep sign-out policy.
- [x] `0.6.8`: adapt Download rows and actions for compact screens, large text, and TalkBack state.
- [x] `0.6.9`: aggregate-only diagnostics, two-transfer concurrency, retry cap, and quota bounds.
- [x] `0.6.10`: decrypt-to-EOF integrity audit, expiry failure, Room 9→10 migration, and final gates.
- [x] `0.7.0`: add bounded lyrics/credits/notification/share contracts and Android Sharesheet entry.
- [x] Audit correction: make explicit manual retry reset the exhausted automatic-attempt window.
- [x] Run cumulative Android unit tests, lint, debug/instrumentation APK builds, and release R8.
- [x] Run Worker types/tests/dry-run and verify Supabase migration parity.
- [x] Inspect the complete diff, generated Room schema, manifest, version, and secret scan.
- [x] Explicitly stage validated paths, create one cumulative Conventional Commit, and push main.
- [x] Verify the GitHub startup exception, deploy the Worker, then tag and publish the prerelease.
- [x] Download the release APK, verify identity/signature/checksum, and complete the final audit.

The restored host initially lacked Java and `/tmp/android-sdk`; OpenJDK 17, Android SDK 37,
Build Tools 36, platform-tools, QEMU user support, and the native Linux/aarch64 SQLite verifier
were restored before counting any local build evidence. No emulator, Appetize, or ADB runtime is
claimed for this batch unless explicitly added to the final evidence.

The final local Android source gate passed 233 unit tests with zero failures, errors, or skips;
all 14 module lint reports contain zero issues; the debug, app-test, Room migration-test, and
R8/resource-shrunk release APKs assembled. The signed debug APK identifies as
`my.id.rakyzumusic`, versionName `0.7.0`, versionCode `89`, min SDK 26, target SDK 37, and one v2
signer; its pre-CI SHA-256 is `21e420dd0f5afe80d849e62bdad96d280d0252997b590ac8396727e4de5ddd50`.
Worker validation passed 73 tests, both TypeScript checks, generated-binding verification, and a
no-write Wrangler deployment dry-run. Supabase CLI 2.118.0 confirmed all 18 local migrations match
production. Production dependency audit reported zero vulnerabilities, tracked credential-prefix
and exact-value scans were clean, and `credential.env` remains ignored.

## Completed cumulative 0.6.0–0.6.5 batch — 2026-09-24

- [x] Audit the clean v0.5.30 hotfix baseline, release train, current architecture, and official
  Spotify offline/storage references.
- [x] `0.6.0`: encrypted account-scoped download foundation with an authenticated
  rights/entitlement boundary.
- [x] `0.6.1`: explicit album and playlist download actions with per-track progress.
- [x] `0.6.2`: Wi-Fi-first persistent scheduling and an explicit mobile-download opt-in.
- [x] `0.6.3`: offline-first Media3 resolution without exposing R2 keys, raw object URLs, or
  credentials.
- [x] `0.6.4`: storage usage, free-space guard, and clear-download controls.
- [x] `0.6.5`: pause, resume, cancel, retry, and process-death-safe execution.
- [x] Run the cumulative Android, Worker, security, release-candidate, and final local worktree
  gates once after all six versions are implemented.

This batch remains Android-only. Spotify's public offline-listening behavior informs the visible
album/playlist action, per-item state, Wi-Fi default, and storage controls; encryption, entitlement,
account isolation, persistence, and delivery are original Rakyzu Music infrastructure.

The final local Android gate passed 227 unit tests, all 14 lint reports with zero errors, the
debug APK, every instrumentation APK, and the minified/resource-shrunk release APK. The debug
candidate identifies as `my.id.rakyzumusic` versionName `0.6.5`/versionCode `83`, has one v2
debug signer, and has SHA-256
`136b79e299c26f184a39b88cdc9f1ce0a3603ddad14c7547bd430f6a00bd152a`. Worker validation
passed 73 tests, TypeScript/generated-binding checks, and deployment dry-run. Room schema 9 and
its v8 migration compiled into the instrumentation gate; the Supabase CLI confirmed all 18 local
migrations match production, so this Android-only batch requires no PostgreSQL migration. Both
pattern and exact-value scans found no tracked credential, and `credential.env` remains ignored.
GitHub CI/CD, production Worker health, exact downloaded artifact, Appetize launch, tag, and
release evidence are attached to the `v0.6.5` GitHub prerelease after the main-branch gates pass.

## 0.5.30 profile-context hotfix — 2026-09-24

- [x] Reproduce the production RPC with a temporary authenticated listener and inspect its safe
  response shape without logging credentials.
- [x] Correct Android scalar JSON decoding and add listener/staff regression coverage.
- [x] Run the complete Android unit, lint, debug, instrumentation-compile, and R8 release gate.
- [x] Push the hotfix, verify main CI, and inspect the exact CI APK.
- [x] Log in as temporary listener and staff accounts on Appetize Android 16 and reach Home.
- [x] Delete both temporary accounts and restore Appetize ADB/debug access to authenticated.
- [x] Replace the broken v0.5.30 release asset with the verified build 77 APK and re-download it.

The production `get_my_profile_context()` RPC was healthy: an authenticated temporary listener
received HTTP 200 with a scalar JSON object and correctly typed nullable, Boolean, and string
fields. The Android client nevertheless called supabase-kt 3.8.0 `decodeSingle()`. Local inspection
of that dependency confirmed that `decodeSingle()` first decodes a `List<T>` and then selects its
first element, so every valid scalar RPC response failed before reaching the Home gate. The
repository now decodes the scalar object directly with a forward-compatible serializer.

The local gate passed 221 unit tests, all 14 lint reports with zero errors, debug and every
instrumentation APK assembly, and the isolated R8/resource-shrunk release build. Main Android CI
run `35988277445` completed successfully. Its exact APK identifies as `my.id.rakyzumusic`,
versionName `0.5.30`, versionCode `77`, min SDK 26, target SDK 37, and has one valid v2 signer.

That CI APK was uploaded unchanged to Appetize build `vcb637t7azifwabsyiyh3lx6ly`. On Android 16,
both a temporary ordinary listener and a temporary Officer signed in and rendered Home; the staff
session additionally exposed its authorized Admin destination. Both processes remained alive with
`MainActivity` resumed, neither showed Profile unavailable, and neither logcat contained an
application fatal block. Both Auth users were deleted after their sessions and Appetize access was
returned to authenticated.

The corrected release APK is 28,821,044 bytes with SHA-256
`87823927a4820af08491f0f5b9e98e136ec71ba404c782ca5da0e7e58895d07e`. It replaces only the
broken APK asset under the existing v0.5.30 prerelease; the old build remains recoverable from its
historical CI artifact. Hotfix commit: `9d95ce1`.

## Active cumulative 0.5.26–0.5.30 batch — 2026-09-22

- [x] Audit clean `v0.5.25` baseline, released APK, Android startup path, and Appetize capability.
- [x] Run released `0.5.25` APK on Appetize Android 13, 15, and 16 with debug logs.
- [x] Complete startup correction and confirm an Android 16 launch with the final APK.
- [x] `0.5.26`: scoped Artist imagery, team, catalog drafts, and review-only submissions.
- [x] `0.5.27`: trusted play-event foundation and privacy-thresholded Artist/Admin analytics.
- [x] `0.5.28`: signed commerce event ingestion, entitlement/receipt ledger, and scoped Admin dashboard.
- [x] `0.5.29`: reasoned suspension/ban, appeal, reinstatement, and dual-control deletion requests.
- [x] `0.5.30`: security alerts/approval, scoped export, accessibility/performance/resilience,
  Android 8–17 compatibility review, and cumulative release gate.
- [x] Validate Worker, PostgreSQL, Android CI, production deployment, Appetize final APK,
  GitHub tag/release/artifact, and final worktree audit.

The CEO reports a force close on a fresh Android 16 install from GitHub Release `v0.5.25`.
The physical-device logcat now establishes the root cause: `DefaultDispatcher-worker-*` entered
the class `init` coroutine at old source line 65 before construction had initialized the later
`repositories` delegated property at old line 86. Calling `authRepository` therefore invoked
`Lazy.getValue()` on a null delegate and terminated the process. The repeated thermal, launcher,
SurfaceFlinger, and Transsion service errors surrounding it are unrelated device/OEM noise.
Candidate commit `d699554` removes all coroutine launches from the constructor and starts them
only from `Application.onCreate()`, after Kotlin has initialized every property. It also initializes
the repository graph deterministically, isolates optional background failures, and defers Media3
playback creation until authenticated use.

The exact published APK had previously started on Appetize Android 13, 15, and 16 without a
captured `FATAL EXCEPTION`; the race was timing/device dependent. During diagnosis, the Appetize
ADB tunnel confirmed that a candidate APK from successful Android CI run `35714776254` resumed
`MainActivity`, rendered the sign-in screen on Android 16, kept its process alive, and emitted no
fatal crash.
The physical stack trace matches the removed old-code path exactly, so the startup correction is
now evidence-backed. The final APK from main Android CI run `35938741526` was uploaded unchanged
to Appetize build `psxswm4fnm55qa7v6pk2gfbjbi`. On Android 16 its process remained alive,
`my.id.rakyzumusic/.MainActivity` was resumed, the sign-in screen rendered, and captured logcat
contained no fatal exception. Android Studio
IDE is unavailable on this Linux ARM64 host and unsupported by its official Linux system
requirements; the official SDK/Gradle CI and Appetize ADB/logcat are the available diagnostics.
Appetize offers selected Android versions from 8.1 through 16.0, not Android 17; Android 17
source compatibility remains a review item rather than a runtime-tested claim. Temporary
Appetize debug-log and ADB permissions were restored to authenticated after testing.

The CEO's manual follow-up exposed a separate post-authentication blocker: the production profile
RPC emitted JSON `null` for `verified` on an ordinary listener (`false OR null` in PostgreSQL).
Android correctly rejected that value for its non-null Boolean model and displayed Profile
Unavailable. All four Auth users had profile rows and both signup triggers were enabled, proving
the failure was response shape rather than missing data. A forward, rollback-tested null-safety
RPC correction was applied to production immediately; the same listener context then returned
`verified: false`. The RPC also self-repairs only `auth.uid()` if a legacy profile row is absent.

Supabase Auth intentionally obscures a duplicate signup while email confirmation is enabled. The
Android repository now inspects the returned identity list: an explicit empty list maps to the
already-registered path, while an indeterminate response retains safe confirmation wording. Auth
unit tests and app compilation cover both outcomes. Final APK runtime confirmation remains part of
the cumulative release gate.

The three forward schema layers were exercised against production PostgreSQL entirely inside
rollback transactions: Artist workspace `15/15`, lifecycle/security `32/32`, and profile
reliability `4/4`. The scenarios cover forced RLS, service-only and exactly idempotent play/commerce
ingestion, a single derived receipt on webhook retry, enforcement/appeal ownership, and privileged
self-lockout rejection. The final lifecycle helpers revoke effective Artist/staff identity for a
restricted account; staff appeal/deletion decisions require CEO authority, and account owners
cannot approve their own deletion. Managed Worker secrets for the service RPC and both independent
HMAC boundaries are present in Cloudflare and were never written to source or Android.

The restored environment contained generated AGP outputs whose absolute paths still pointed to
the former `/home/rakyzu` workspace. A scoped Gradle clean removed only generated outputs. The ARM64
host also requires debug and release Room/KSP gates in separate daemons because its SQLite verifier
cannot load the same native library in parallel classloaders; CI remains configured for the normal
combined x86 gate. Compatibility review additionally caught API-30-only `WEBP_LOSSY` usage in the
new artwork paths; API 26–29 now select the legacy WebP encoder while API 30+ use WebP lossy.

The final local source gate passed 219 Android unit tests with zero failures/errors/skips, all 14
module lint reports with zero errors, the debug and every Android-test APK build, and the separately
isolated R8/resource-shrunk release build. The debug artifact identifies as
`my.id.rakyzumusic`, versionName `0.5.30`, versionCode `76`, min SDK 26, target SDK 37, and has one
valid APK Signature Scheme v2 signer. Worker validation regenerated and checked its bindings,
type-checked both TypeScript targets, passed all 71 tests, and completed a no-write deploy dry-run.
An exact-value scan found no configured credential in tracked or pending source.

PR `#2` merged as `565d3687d65ab738120d8b4809f169d6e4a39138`. Its Android
`35937418130`, database `35937418179`, and Worker `35937418127` checks passed. The resulting main
Android `35938741526`, database `35938741533`, and Worker `35938741540` workflows also passed,
including production deployment. Production health reports Worker `0.5.30`; the three migration
records `20260922100000`, `20260923100000`, and `20260923110000` and their new trusted-play,
commerce, and profile-context objects are present.

Annotated tag and GitHub Release `v0.5.30` point to the validated merge commit. The downloaded
release APK is byte-identical to the main CI artifact: 28,821,044 bytes with SHA-256
`184a4cb8c5dc188cb74c652522849f146780cbd0f5e340ccf5e860ce0c14d0f7`. It identifies as
`my.id.rakyzumusic`, versionName `0.5.30`, versionCode `76`, min SDK 26, target SDK 37, and has one
valid APK Signature Scheme v2 signer. Release URL:
<https://github.com/Rakyzu-Development/Rakyzu-Music/releases/tag/v0.5.30>.

## Active cumulative 0.5.16–0.5.20 batch — 2026-09-15

Baseline audit found clean synchronized `main` after the published `v0.5.15` evidence commit.
The audit confirmed that Artist was a catalog entity, not yet an account role: no Artist invitation
consent, separate authenticated profile, privilege set, or verification badge existed. The CEO
requested the five planned governance milestones and a ten-version Artist/Admin roadmap extension.

- [x] `0.5.16`: add append-only quarantine/takedown/restore enforcement with required reasons and
  listener visibility enforced inside catalog RLS.
- [x] `0.5.17`: add artist and label records, links, and exact-email viewer/editor/admin team scopes
  without granting organization-global staff access.
- [x] `0.5.18`: add bounded private WebP artwork, independent artwork/release review, and automatic
  server-time scheduled visibility after the complete publication gate.
- [x] `0.5.19`: add permission-filtered audit summary, anomaly threshold, bounded privacy-safe CSV
  export, and CEO retention intent without destructive evidence deletion.
- [x] `0.5.20`: connect the adaptive Android Control Room, synchronize version/docs/automation, and
  run the cumulative regression, security, accessibility, deployment, and artifact gate.
- [x] Add the explicit `0.5.21`–`0.5.30` Artist identity, profile, badge, analytics, commerce,
  enforcement, deletion, and strengthened Admin roadmap.
- [x] Complete final Android, Worker, PostgreSQL pgTAP/lint, configuration, and secret validation.
- [x] Inspect the explicit diff; commit and push; verify CI/deploy; tag and publish the exact CI APK;
  then download and re-audit the release asset.

The schema was exercised against the real Supabase PostgreSQL engine inside rollback transactions
before publication. The first rollback probe correctly caught missing execute access for two
read-only RLS helpers; the grant was narrowed to those helpers and both the prior RBAC scenario and
new governance scenario then completed and rolled back successfully. No production row was changed
by these pre-publication probes.

The final local gate completed all 1,633 Android tasks once, then recompiled the corrected Admin
permission boundary and rebuilt lint/debug/release from the final source. All 216 unit tests passed
with zero failures, errors, or skips; all 14 module lint reports contain zero errors; Android-test
APKs compiled; and the R8-shrunk release variant assembled. The signed debug APK identifies as
`my.id.rakyzumusic`, versionName `0.5.20`, versionCode `66`, with one v2 signer. Its pre-CI SHA-256
is `6cd520ce3fc400faba01847e44cf2f848da4c6daa094f9007c9485d482c9fbcf`.

Worker validation passed all 51 tests, both TypeScript targets, generated-binding checks, and a
no-write deployment dry-run. The old RBAC pgTAP scenario and the new governance scenario both
passed against the linked Supabase PostgreSQL engine inside rollback transactions, including the
regression that replacement artwork invalidates its previous approval. GitHub Actions secret names,
the live Cloudflare R2 media bucket, and configured public endpoints were verified. Exact-value and
credential-pattern scans found zero privileged-secret matches in tracked source and both local APKs.

No Appetize, emulator, ADB, or physical-device runtime claim is planned for this batch. Privileged
credentials remain outside tracked source and Android. Artist identity is intentionally not inferred
from a catalog row or email: activation will require an authenticated invitation acceptance and
versioned consent in the next train.

The cumulative feature commit is `9195bf9ab03f1bef06363cb2685aa6815df865ef`. Android CI
`35011222137`, Worker CI/deployment `35011222310`, and Database pgTAP/lint/deployment
`35011222233` all completed successfully. Production reports Worker `0.5.20`, rejects an
unauthenticated Admin request with `401`, and contains exactly one deployed migration record plus
the enforcement, team, review, schedule, and governance RPC objects.

Annotated tag and GitHub Release `v0.5.20` point to the validated feature commit. The downloaded
release APK is byte-identical to the Android CI artifact: 28,640,820 bytes with SHA-256
`ffd547e6a7e56fe7c560ded179302f07b17e19bb0db053b8094c8d525c71c481`. Its package/version,
single v2 signer, and privileged-secret scan were revalidated after download. Release URL:
<https://github.com/Rakyzu-Development/Rakyzu-Music/releases/tag/v0.5.20>.

## Active cumulative 0.5.11–0.5.15 batch — 2026-09-15

Baseline audit found a clean tagged `v0.5.10` release at
`d9a337e034a41ead7b272d2b0ee43e517d3e21f3`. The CEO extended the 0.5 patch train through
0.5.20 and requested the first five versions as one implementation and publication cycle.

- [x] `0.5.11`: add fixed Officer, Supervisor, Manager, C-Level Executive, and CEO ranks with
  explicit permissions and server-owned account assignments.
- [x] `0.5.12`: add forced-RLS moderation, action history, media inventory and append-only audit
  tables plus hierarchy-safe, narrowly granted RPCs.
- [x] `0.5.13`: add a role-gated adaptive Android Admin Panel whose destinations and actions are
  derived from the current server context, never user-editable metadata.
- [x] `0.5.14`: add original artist, album and track drafting; validated 50 MiB MP3 upload to exact
  private R2 keys; and atomic publication that requires standard audio for every track.
- [x] `0.5.15`: add role-specific moderation controls, staff management, accessibility contracts,
  version/docs/automation synchronization, and the cumulative security gate.
- [x] Run final Android, Worker, database replay/pgTAP/lint, configuration and secret validation.
- [x] Inspect the explicit diff, publish the validated commits, verify all CI/deploy jobs, provision the CEO
  role through the trusted database channel, then publish and re-audit the v0.5.15 artifact.

No Appetize, emulator, ADB, or physical-device runtime claim is planned for this batch. Privileged
credentials remain outside Android and tracked source. The role matrix follows least privilege;
CEO full access is explicit but still authenticated and audited.

The final local Android gate completed successfully from the final source: 215 unit tests passed
with zero skips, failures, or errors; all 14 module lint reports contain zero errors; debug,
Android-test, and R8-shrunk release APK assembly passed. The configured debug APK identifies as
`my.id.rakyzumusic`, versionName `0.5.15`, versionCode `61`, and has one APK Signature Scheme v2
signer. Its pre-CI SHA-256 is
`fa7e0ccc6de22cc1d387ce717e23227b8791a3c3c06f384ef7cb60769e78e95b`.

Worker binding/type checks, deployment dry-run, and all 43 tests passed. The complete migration,
pgTAP scenario, email-based appointment, hierarchy, CEO-protection, moderation, publication, and
audit behavior passed in a real Supabase PostgreSQL rollback transaction, so no pre-CI production
schema mutation occurred. GitHub secret presence and the live Cloudflare R2 bucket were verified;
tracked source and the configured APK contain zero privileged credential matches.

The cumulative feature commit is `ce56184a3501c074f3a4d65bc0fcac44a1558d1a`; the catalog-policy
contract correction is `211e036dcd641801c472a2948476e93f2a5e5a7b`. Worker CI/CD, database
CI/deployment, and Android CI all completed successfully. The production Worker reports `0.5.15`
and rejects an unauthenticated Admin context request with `401`. The confirmed CEO account is
active with the complete eight-permission set, and its idempotent bootstrap has exactly one audit
entry.

Annotated tag and GitHub release `v0.5.15` point to the validated code. The downloaded release APK
is byte-identical to the Android CI artifact: 28,575,284 bytes with SHA-256
`b544642d3b3d50e2595a072e0e169fc1e1f4287686fa37ccc851f22880e51d8c`. Its package/version,
single v2 signer, and privileged-secret scan were revalidated after download. Release URL:
<https://github.com/Rakyzu-Development/Rakyzu-Music/releases/tag/v0.5.15>.

## Active cumulative 0.5.6–0.5.10 batch — 2026-09-14

Baseline audit found a clean `main` worktree at release tag `v0.5.5`; local `main`,
`origin/main`, and the tagged commit all resolve to `94fecfd0b96b0bf727e866df267f9373d175e73b`.
This batch remains Android-only and uses Spotify's public product behavior as a reference without
copying source, assets, catalog content, trademarks, or private infrastructure.

- [x] Audit repository, worktree, version/tag/release baseline, roadmap, local build tools, and
  credential boundaries after the device move.
- [x] Recheck the official Spotify audio-quality and Queue product/API references and bound the
  Rakyzu implementation to capabilities its own catalog and infrastructure can support.
- [x] `0.5.6`: persist separate Wi-Fi/mobile audio-quality choices and Data Saver locally, resolve
  the effective quality from the active network, and enforce the selected R2 media variant.
- [x] `0.5.7`: retain the explicit queue after retryable playback failures and provide bounded
  manual/connectivity recovery without silently appending media.
- [x] `0.5.8`: adapt Queue rows for compact widths and large text while preserving TalkBack state,
  actions, ordering, and 48dp controls.
- [x] `0.5.9`: add bounded, privacy-safe playback diagnostics without media IDs, labels, URLs,
  account identifiers, or authorization material.
- [x] `0.5.10`: synchronize versions/docs/automation, run the cumulative Android/Worker/security
  regression gate, and record honest runtime and catalog limitations.
- [x] Inspect the complete diff and explicit staging set, then create one cumulative Conventional
  Commit, push `main` once, verify CI/CD, deploy Worker, and run linked service checks.
- [x] Publish one annotated `v0.5.10` tag/release, download and inspect the public APK, and complete
  the final branch/worktree/artifact/secret audit.

No milestone is marked complete until its implementation and focused tests exist. No Appetize,
emulator, or ADB runtime claim is implied by this checklist.

The cumulative local Android gate completed 1,021 Gradle tasks successfully: all debug unit tests,
lint for every module, the configured debug APK, and every instrumentation APK compiled. Unit XML
reports contain 210 tests with zero skips, failures, or errors. Dedicated Queue accessibility
instrumentation covers the 1.3x layout, current-track state, retry action, and 48dp edit controls;
it was compiled but not executed on a device. The debug artifact identifies as
`my.id.rakyzumusic`, versionName `0.5.10`, versionCode `56`, and has one APK Signature Scheme v2
signer. Its SHA-256 is `4dae7d114abc7c7e52f5f887fb4dc121fd34260f8cc165a356d6adba7c789462`.

Worker type/binding checks, deployment dry-run, and all 37 tests passed. The linked Supabase CLI
dry-run reports the remote database current with no pending migrations, seeds, or roles. Tracked
source and the APK passed credential/pattern scans without exposing secret values. R2 contains no
rights-cleared playback catalog yet, so only authenticated storage access and Worker contract
tests can be claimed; an end-to-end audio runtime cannot honestly pass until the selected variant
exists. No Appetize, emulator, or ADB session was used.

## Active cumulative 0.5.1–0.5.5 batch — 2026-09-14

Initial recovery audit found the repository clean at `v0.5.0`, but the moved-device environment
was missing a usable JDK/Android SDK and retained a stale ignored SDK path. A local JDK 17,
Android SDK 37, arm64-compatible build adapters, and corrected ignored SDK path were restored
without changing product source or storing credentials in the repository.

- [x] Audit Git baseline, ignored local configuration, credential boundaries, Java, Android SDK,
  Gradle, Worker dependencies, and linked-service tooling.
- [x] `0.5.1`: add explicit Play next/Add to queue actions to the shared track context sheet and
  make duplicate ordering deterministic.
- [x] `0.5.2`: expose queue movement, removal, and clear actions with track-specific TalkBack
  labels routed through Media3 rather than UI-local state.
- [x] `0.5.3`: restore only the signed-in account queue after process death, filter malformed or
  duplicate entries, preserve the active item when available, and pause restoration.
- [x] `0.5.4`: establish the explicit-queue autoplay boundary; no recommendation or unrelated
  media can be inserted implicitly.
- [x] `0.5.5`: represent only the active local playback device and make no remote-control claim.
- [x] Run the complete cumulative validation and live infrastructure checks.
- [x] Inspect artifacts/diff/secrets, publish one conventional commit/tag/release `v0.5.5`, push
  `main`, verify CI/CD, and record final evidence below.

Local Android validation passed after the moved-device audit: the complete `testDebugUnitTest`
task completed successfully, and affected playback/search/player/app lint completed successfully.
The signed debug artifact identifies as `my.id.rakyzumusic`, versionName `0.5.5`, versionCode
`51`, with one v2 signer. Android-test APK compilation completed; no Appetize, emulator, or ADB
runtime claim is made for this batch. The unsigned release variant will be built and attested by
the protected CI release gate.

Worker validation passed with 34 tests, TypeScript/runtime-binding checks, and a no-write Wrangler
deployment check. The first live smoke exposed an incorrect `503` mapping when Supabase denied a
non-member access to private playlist artwork; the Worker now converts expected authorization
denials into a non-disclosing `404`. After redeployment, authenticated playlist mutations and
private R2 upload/read/delete passed end to end, and both temporary accounts were removed. The
linked Supabase migration dry-run reports no pending migration, seed, or role change. These checks
source ignored local credentials only; no credential is printed or included in the Android
artifact.

## Queue foundation 0.5.0 and Auth prerequisite — 2026-09-10

The CEO reproduced a recovery link that reopened sign-in without presenting the existing
new-password panel. The callback recognized only PKCE query codes and marked recovery before
importing a valid recovery session. During correction, the CEO enabled Confirm Email and
provisioned Resend Custom SMTP for `rakyzu.my.id`; the live Auth configuration now reports both
email confirmation and Custom SMTP active. The release milestone remains the original Queue
foundation rather than being repurposed as an Auth-only hotfix.

- [x] Audit live Auth delivery configuration, redirect allow-list, Android intent delivery,
  callback validation, session state and reset-password UI routing.
- [x] Supply the exact signup confirmation redirect and accept bounded PKCE/legacy implicit
  confirmation and recovery callbacks while rejecting wrong origins, paths, types, duplicate
  parameters and missing credentials.
- [x] Import/exchange the callback session first, then route confirmation through the normal
  authenticated Home gate or expose New Password only for recovery.
- [x] Clear processed callback data from the Activity intent to prevent a duplicate PKCE exchange
  after recreation and avoid retaining callback credentials.
- [x] Preserve Confirm Email and verify Resend Custom SMTP without exposing its credentials.
- [x] Add an account-scoped transactional Room Queue cache with deterministic order, bounded
  input validation, active-index persistence, and listener isolation.
- [x] Synchronize Media3 timeline edits and active-item transitions into the Queue repository while
  keeping process-death restoration reserved for `0.5.3`.
- [x] Run the full Android gate, compile Room instrumentation coverage, inspect the configured
  APK and complete live service checks without Appetize or ADB.
- [x] Publish commit/tag/release `v0.5.0`, verify CI and downloaded artifact, then restore the
  release workspace.

Email confirmation must remain enabled. Resend delivery, confirmation, and recovery acceptance
must be rechecked before 1.0.0; no SMTP or privileged Supabase credential is stored in Android.
The CEO explicitly removed Appetize and ADB from this final gate, so no physical link-tap runtime
claim is made for `0.5.0`.

The cumulative local gate completed 1,511 Gradle tasks and 203 unit tests with zero failures,
compiled Room schema-8 instrumentation coverage, ran Android lint, and produced both debug and
R8-shrunk unsigned release artifacts. The configured debug APK reports application ID
`my.id.rakyzumusic`, versionName `0.5.0`, versionCode `46`, and a valid v2 signature; its DEX
payload contains both exact Auth redirects and the New Password UI. Privileged-secret scans found
zero matches in tracked files and the APK. Worker type checks, 33 tests, and Wrangler dry-run
passed; all 11 local/remote Supabase migrations match and the linked push dry-run is empty. Live
Auth reports Confirm Email, Custom SMTP, and both mobile redirect URLs enabled.

## Active cumulative 0.4.5–0.4.10 batch — 2026-09-09

Initial audit found that the screenshot supplied by the CEO came from an unconfigured
local APK: the application correctly failed closed, but the previous ADB smoke only
checked process launch and never exercised authentication. The published 0.4.4 CI APK
does contain the Supabase public URL/key. This batch adds an explicit configured-build
gate and requires signup, login, onboarding, and Home runtime evidence.

- [x] Inspect the supplied screenshot and reproduce its configuration failure boundary.
- [x] Audit Git/release state, Android configuration injection, credential names, and
  the existing Playlist/Room/Supabase/Worker contracts.
- [x] Re-read official Spotify collaboration, privacy/access, and paginated Playlist
  product/API references; use concepts only, not Spotify code/assets/catalog data.
- [x] `0.4.5`: expiring collaboration invites, owner/editor/viewer roles, member list,
  acceptance, revocation, and leave controls.
- [x] `0.4.6`: private/public visibility, access-aware reads, safe sharing, following,
  and bounded abuse-resistant limits.
- [x] `0.4.7`: durable account-scoped mutation outbox, operation idempotency, explicit
  retry, and stale-revision reconciliation.
- [x] `0.4.8`: cursor playlist listing, bounded detail paging, incremental loading, and
  large-list validation/performance tests.
- [x] `0.4.9`: privacy-safe local diagnostics, adaptive layouts, complete TalkBack
  roles/states/actions, and 48dp interaction targets.
- [x] `0.4.10`: configured-build enforcement, cumulative regression/security/performance
  gates, version/docs synchronization, and release artifact audit.
- [x] Run the cumulative Android/Room/Supabase/Worker validation once after all six
  versions are implemented; then inspect the complete diff and scan for secrets.
- [x] Build the configured v0.4.10 debug APK, upload it directly to Appetize, and prove
  signup, clean-session login, onboarding, and Home with a real email account.
- [x] Download the final CI artifact and install it on the connected ADB device as the
  last runtime gate; do not use a local Android emulator.
- [x] Explicitly stage the validated batch, create one Conventional Commit, push main,
  verify CI/CD, deploy forward migrations/Worker, and run live smoke tests.
- [x] Publish one cumulative `v0.4.10` tag/release, download the public APK again, and
  audit manifest, configuration presence, signature, checksum, branches and worktrees.

The CEO supplied a dedicated Appetize API token after the initial audit. It is stored only
in ignored local `credential.env` with the other CLI credentials and will be used directly
for the final acceptance session; it is never copied into Android, source, logs, artifacts,
GitHub, or an alternate proxy.

The cumulative local gate passed Android unit tests, the configured debug APK build,
full lint, release R8/packaging (472 tasks), Worker typecheck/tests/Wrangler dry-run
(33 tests), all 11 Supabase migrations with 161 pgTAP assertions, linked migration
dry-run, release manifest/configuration checks, distributable debug APK v2-signature
verification, diff validation, and the tracked secret scan. Appetize displayed v0.4.10
and completed signup through Home; the temporary
Supabase email auto-confirm setting was restored immediately afterward.

## Previous cumulative 0.4.1–0.4.4 work session

## Initial audit — 2026-09-09

- Baseline: `d18ad5c` (`0.4.0`, Android versionCode 43).
- Working tree clean; only worktree `/root/projects/rakyzumusic`, branch `main`.
- Fetched `origin/main`; ahead/behind counts `0/0`.
- GitHub release `v0.4.0` exists with branded debug APK (28,214,722 bytes).
- Roadmap specifies ordered playlist detail, validated membership, revision-safe reorder,
  metadata editing and authenticated artwork. Web/iOS and Appetize remain out of scope.
- Previous Android instrumentation was compiled, not executed. Do not describe that as
  device/runtime validation. New runtime evidence must be recorded separately.

## Tasks and release gates

- [x] Read repository rules; audit Git baseline and previous release.
- [x] 0.4.1 implementation: ordered detail, local persistence, deterministic playback.
- [x] 0.4.2 implementation: add/remove entry points, duplicate and catalog validation.
- [x] 0.4.3 implementation: optimistic reorder, revision conflict rejection and refresh.
- [x] 0.4.4 implementation: metadata editing and authenticated artwork lifecycle.
- [x] Review implementation, security boundaries, tests and documentation together.
- [x] Run cumulative unit tests, lint, SQL/Worker checks and APK build.
- [x] Synchronize version, roadmap, release notes and artifact names.
- [x] Inspect final diff and scan source against credential values/token patterns.
- [ ] Explicitly stage validated paths; commit once and push main once.
- [ ] Verify CI/CD and migrations; publish one cumulative tag/release (0.4.4).
- [ ] Download published Release APK; inspect manifest, signature and checksum.
- [ ] Final audit: branches, worktrees, tags, artifacts, deployment and clean Git status.
- [ ] Record final evidence and durable handoff; distinguish remaining limitations.

No milestone or gate is complete until its evidence is recorded here. This file is the
durable task list and session handoff; it does not imply automatic chat compaction.

Official OpenAI Docs identifies `/compact` as an interactive CLI/composer command
([reference](https://learn.chatgpt.com/docs/developer-commands?surface=cli)). No tool exposed
to this agent triggers that command in the active chat. Preserve this handoff and the
post-release audit; do not claim manual compaction occurred or modify session internals.
Final post-publication evidence is saved to `docs/Rakyzu-Music-0.4.4-audit.local.md`
(ignored, machine-local) and attached to the GitHub Release. This avoids a second code
commit/CI cycle solely to record identifiers that only exist after publication.

## Local validation before publication

- Worker: 32 tests passed; TypeScript/runtime binding check and deployment dry-run passed.
  Artwork validation checks the complete bounded PNG chunk stream, CRCs, dimensions and
  terminal image data before any object is written to R2.
- Portable local PostgreSQL (PGlite, Supabase Auth schema shim): all nine forward migrations
  replayed; 133 pgTAP assertions passed, including 22 new playlist membership/security cases.
  This is supplementary evidence, not a substitute for native Supabase CI/lint.
- Local Android host is ARM under an Android/proot environment. Native PostgreSQL cluster
  initialization fails on emulated ownership; no production database was touched.
- AAPT2 successfully runs through QEMU with the installed x86_64 loader. Local wrapper:
  `/tmp/rakyzu-tools-0.4.4/aapt2`; Room KSP native-library override:
  `JAVA_TOOL_OPTIONS=-Dorg.sqlite.lib.path=/tmp/rakyzu-sqlite-native`.
- Initial compile caught a nullable Kotlin access; fixed before publishing. AGP 9 source-set
  incompatibility was corrected using the variant device-test assets API.
- The CEO connected an Infinix X6887 (Android API 36) during implementation. Dedicated
  instrumentation APKs and the final debug APK were installed without clearing app data.
  CI compiles all instrumentation APKs; focused runtime execution uses this physical device.
- First device run exposed a migration fixture assumption about omitted empty `indices`
  arrays, then a teardown race caused by closing Room with an invalidation Flow still active.
  The fixture now accepts omitted arrays and the migration test uses deterministic DAO reads;
  all 6 Room cases passed twice consecutively on the connected device.
- Android unit reports currently show 194 tests, zero failures/errors/skips; source scan
  against known privileged credential values and token patterns passed without printing secrets.
- Two environment/session restarts terminated ongoing local Gradle processes. Cached work was
  reused on resume; only terminal builds with explicit successful results are counted.
- Distribution version is 0.4.4/versionCode 44: one cumulative APK/tag/release, not four builds.

## Device findings before publication

- Cumulative debug/unit/lint/instrumentation-build gate completed successfully (194 unit tests).
- A first Playlist process was killed by Android `LOW_MEMORY` while Gradle was active on the
  same device; runtime tests are now run after build processes exit.
- The next UI run executed all four tests and exposed two 40dp icon semantics bounds instead
  of the required 48dp. Refresh, back, move and remove controls now set 48dp explicitly.
- Corrected Playlist accessibility tests pass 4/4 on the connected device. Corrected Room
  migration/database tests pass 6/6 in two consecutive runs.
- Final affected build passed 1,074 Gradle tasks; final APK rebuild after the database test
  stabilization passed 727 tasks, including release R8/lint vital and packaging.
- Debug APK identity is `my.id.rakyzumusic`, versionName `0.4.4`, versionCode `44`, target SDK
  37, signed with APK Signature Scheme v2. It installs and launches MainActivity foreground
  on the connected device with no fatal exception in the post-launch log window.
- No commit/push/release occurred before all of the above local failures were corrected.
- The first live deployment smoke exposed that SQLSTATE `40001` can be retried as a genuine
  serialization failure and time out. A forward migration changes intentional stale-revision
  errors to PostgREST `PT409`/HTTP 409; the Android mapper accepts both codes during rollout.
- An unnecessary CI emulator gate was introduced and produced infrastructure-only boot
  failures before any application test ran. The CEO clarified that emulator execution is
  not required; the gate and KVM setup were removed. Physical-device ADB evidence above is
  the runtime gate, matching the established project workflow.
# Completed cumulative 0.5.21–0.5.25 batch — 2026-09-21

- [x] Audit copied workspace and preserve the validated `0.5.20` baseline.
- [x] Implement optional exact-email Artist identity, in-app consent, profile badge/color, scoped biography, and universal avatar.
- [x] Implement requested staff email intents, Artist/album edit/archive, recommendation card lifecycle, and six audio containers.
- [x] Synchronize source version to `0.5.25`/`71`, Worker package and CI artifact identity.
- [x] Run Worker tests/typecheck on Node 22; see current command evidence below.
- [x] Run database migration replay, pgTAP tests, and database lint in GitHub CI.
- [x] Run Android unit tests, lint, debug/test APK builds, and minified release build in GitHub CI.
- [x] Run candidate CI, deploy forward migrations and Worker, verify live behavior.
- [x] Push validated main commit, tag/release, download and inspect APK, and complete final audit.

The copied ARM64 environment lacks the previous device's Android SDK path, so Android validation
used the project's GitHub CI gate. Candidate and main Android, database, and Worker workflows
all succeeded. The main Android run `35614289698` passed unit tests, lint, debug and Android-test
APK compilation, and the release build. Database run `35614289594` passed pgTAP, lint, replay,
and deployed two forward migrations; Worker run `35614289675` passed the full check (62 tests,
type checks, and dry-run) and deployed the API. Live health reports `0.5.25`; production read-only
checks found five designated roles and both new migration records. The Cloudflare deployment and
R2 media bucket were independently confirmed.

The cumulative feature commits are `fcfd330`, `995af79`, and `6d7682d`; the last is the tagged
`v0.5.25` source on `main`. The GitHub prerelease contains the CI-built debug APK. Its ZIP passed
archive verification; the APK passed package/version inspection and Android v2 signature
verification with one signer. The published APK was downloaded again through the authenticated
GitHub API and matched the CI artifact byte for byte: 28,722,740 bytes, SHA-256
`664fb51a0b01d05decd5ea1fe585baa691971d51389d065b889e11e21a77925d`.
Release: <https://github.com/Rakyzu-Development/Rakyzu-Music/releases/tag/v0.5.25>.
No Appetize, emulator, ADB, or physical-device runtime test is claimed for this batch.
