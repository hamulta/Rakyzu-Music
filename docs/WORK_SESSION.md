# Rakyzu Music — engineering work sessions

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
- [ ] Inspect the explicit diff, commit and push once, verify all CI/deploy jobs, provision the CEO
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
