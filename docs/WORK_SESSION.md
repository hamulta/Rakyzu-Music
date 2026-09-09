# Rakyzu Music — cumulative 0.4.1–0.4.4 work session

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
  CI also includes Android 35 Playlist/Room execution, with reports retained.
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
