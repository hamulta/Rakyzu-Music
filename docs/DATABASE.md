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

Credentials belong only in local ignored files or GitHub encrypted secrets. Neither SQL migrations nor the Android build may contain privileged keys.
