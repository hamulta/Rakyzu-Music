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

Android mirrors those tables in Room 3 using a checked-in version-1 schema. Refresh validates the complete remote graph before a single transaction replaces artists, albums, tracks, and the successful-sync timestamp. If network, service, or validation fails, the existing local snapshot remains untouched.

Credentials belong only in local ignored files or GitHub encrypted secrets. Neither SQL migrations nor the Android build may contain privileged keys.
