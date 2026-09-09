# Database Development

Rakyzu Music Android uses a dedicated Supabase project in the Singapore region. The previous project is not linked to this repository and receives no migrations from this workflow.

## Repository layout

- `supabase/config.toml`: reproducible local Supabase configuration.
- `supabase/migrations/`: ordered, forward-only database changes.
- `supabase/tests/database/`: transactional pgTAP schema and RLS tests.
- `supabase/seed.sql`: intentionally empty until synthetic development fixtures are required.

## Local validation

A Docker-compatible runtime is required by the Supabase CLI:

```bash
supabase db start
supabase test db --local
supabase db lint --local --schema public,private --level error --fail-on error
supabase stop --no-backup
```

GitHub Actions runs this sequence against an isolated database on every database change. A successful push to `main` then links with encrypted repository secrets and applies only pending migrations.

## Migration discipline

1. Create one timestamped migration for one coherent schema change.
2. Add positive and negative policy tests in the same commit.
3. Run a linked dry-run before deployment: `supabase db push --linked --dry-run`.
4. Apply with `supabase db push --linked`; never use `db reset --linked` on the shared project.
5. Confirm local and remote migration history with `supabase migration list --linked`.

Every client-facing table must explicitly enable and force RLS, revoke default access, grant only required operations, and test anonymous, owner, and cross-owner behavior. Privileged functions use a fixed empty `search_path`, fully qualified object names, and revoked public execution.

`public.profiles` stores the listener's required `display_name` and non-null `onboarding_completed` state. Authenticated clients may select and update only their own row; profile inserts and deletes remain lifecycle operations controlled by the Auth trigger and privileged backend. The onboarding migration defaults existing and new rows to incomplete without deleting or rewriting profile data.

`public.artists`, `public.albums`, and `public.tracks` contain published catalog metadata. Authenticated listeners receive SELECT-only access to rows whose `is_published` flag is true; anonymous reads and all client writes are denied. Foreign keys, positive duration/position checks, unique album positions, and pgTAP policy tests protect the server catalog. The initial synthetic Rakyzu Sessions fixtures are owned development content and include no third-party catalog or media.

`public.editorial_shelves` and `public.editorial_shelf_tracks` define ordered Home programming. Authenticated listeners can select only published shelves whose referenced tracks are also published. Anonymous reads and every client mutation are denied. Titles, subtitles, ordering, foreign keys, uniqueness, and RLS behavior are covered by transactional pgTAP tests.

Android mirrors the catalog and editorial graph in Room 3 using checked-in schema version 3 and automatic forward migrations from versions 1 and 2. Refresh validates the complete remote graph before a single transaction replaces artists, albums, tracks, shelves, entries, and the successful-sync timestamp. If network, service, or validation fails, the existing local snapshot remains untouched.

`public.liked_tracks`, `public.saved_albums`, and `public.followed_artists` store canonical account Library relationships. Forced RLS and owner-only policies restrict authenticated reads and mutations to `auth.uid()`; anonymous table and RPC access is revoked. Bounded `get_library_items_page`, `get_library_sync_anchor`, and `get_library_changes` functions use invoker rights and preserve RLS. The append-only `library_changes` stream records insertions and deletion tombstones for resumable per-listener synchronization. Room mirrors canonical IDs and adds a coalescing account/item mutation outbox; snapshot replacement and cursor application preserve newer pending intents.

`public.playlists` stores owner-scoped playlist metadata. Forced RLS and security-invoker `get_my_playlists` and `create_playlist` functions keep reads and creates inside `auth.uid()`, normalize bounded names/descriptions, and deny anonymous execution. `revision` begins at one as the optimistic-concurrency snapshot boundary for later ordered-item and collaboration migrations. Room schema 5 caches only the authenticated listener's validated collection and updates it transactionally without storing the owner identifier in the domain/UI model.

### Ordered playlist mutations — 0.4.1–0.4.4

Forward migration `20260909120000_playlist_items_and_mutations.sql` adds forced-RLS
`playlist_items`, unique `(playlist_id, track_id)` membership and deferred unique positions.
Authenticated clients have SELECT only; writes must use `mutate_playlist`. That function
locks the caller's own playlist, compares `expected_revision`, validates action-specific
parameters/publication/exact permutations, and atomically updates membership, count and
revision. It returns the same ordered JSON contract as `get_playlist_detail`. Both definer
functions have an empty search path, explicit `auth.uid()` checks, and no anonymous grants.
Unpublished tracks keep a removable membership placeholder; their metadata is not returned.
No catalog/auth data is deleted by a playlist mutation. No existing migration is rewritten.

Room schema 6 has an additive 5→6 migration and a `playlist_details` payload table keyed by
account and playlist. Summary and detail are written in one transaction. Instrumentation
constructs a real schema-5 database from the exported schema and verifies preservation,
new detail storage and account isolation after Room opens it as schema 6.

Migration release gate: native Supabase replay, pgTAP and schema lint in CI. Portable local
PGlite replay with an Auth schema shim is supplementary only. Future collaboration,
pagination and outbox migrations remain in 0.4.5–0.4.8; they are not part of this release.

`recently_played` is device-local metadata keyed by `(user_id, track_id)`. Writes are accepted only for a track in the verified Room catalog, capped at the 20 most recent entries per listener, and intentionally survive catalog replacement without creating a destructive foreign-key path. Home resolves retained IDs back through the current verified catalog and omits tracks no longer available.

Credentials belong only in local ignored files or GitHub encrypted secrets. Neither SQL migrations nor the Android build may contain privileged keys.
