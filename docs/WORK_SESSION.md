# Rakyzu Music — engineering work sessions

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
