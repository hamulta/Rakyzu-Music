# Security Policy

## Supported versions

Until `1.0.0`, only the newest Android development version receives security fixes.

## Reporting

Report vulnerabilities privately to the repository owner. Do not open a public issue containing credentials, exploit details, user data, or unreleased media URLs.

## Secret handling

- Android clients may contain only public/publishable configuration.
- Supabase service-role credentials, Cloudflare API tokens, and R2 S3 credentials are server-only.
- R2 media access must use an authenticated Worker, a public custom domain for intentionally public assets, or short-lived signed URLs.
- Every exposed credential must be revoked and replaced, then repository history and logs must be audited.
- CI uses least-privilege GitHub permissions and protected environment secrets.
- Supabase access/refresh tokens and PKCE verifiers are encrypted at rest with AES-GCM and a non-exportable Android Keystore key; they are never logged or included in backups.
- Authentication uses PKCE, mandatory email confirmation, rotating refresh tokens, and current-device logout by default.

## Data controls

- Row Level Security is mandatory on all exposed Supabase tables.
- Storage and database authorization are verified server-side; client claims are never trusted directly.
- Schema changes use reviewed, reversible migrations.
- Dependency, lint, unit, and build checks must pass before a push is considered releasable.
