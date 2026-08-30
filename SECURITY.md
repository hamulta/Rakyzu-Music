# Security Policy

## Supported versions

Until `1.0.0`, only the newest Android development version receives security fixes.

## Reporting

Report vulnerabilities privately to the repository owner. Do not open a public issue containing credentials, exploit details, user data, or unreleased media URLs.

## Secret handling

- Android clients may contain only public/publishable configuration.
- Supabase service-role credentials, Cloudflare API tokens, and R2 S3 credentials are server-only.
- R2 media access must use an authenticated Worker, a public custom domain for intentionally public assets, or short-lived signed URLs.
- Protected media is stored in a private R2 bucket and addressed only by server-derived object keys; clients cannot submit arbitrary R2 keys.
- Every exposed credential must be revoked and replaced, then repository history and logs must be audited.
- CI uses least-privilege GitHub permissions, protected environment secrets, and full-length immutable commit pins for every Android workflow action.
- Supabase access/refresh tokens and PKCE verifiers are encrypted at rest with AES-GCM and a non-exportable Android Keystore key; they are never logged or included in backups.
- Authentication uses PKCE, mandatory email confirmation, rotating refresh tokens, and current-device logout by default.
- Password recovery accepts only the exact branded callback containing one bounded PKCE code and no unexpected query or fragment data; its required state is encrypted and cannot be skipped by restarting the app.
- Recovery requests use a generic success response so the client does not disclose whether an email address is registered.
- Android rejects cleartext traffic through both the application manifest and network security configuration, and trusts system certificate authorities for HTTPS delivery.

## Data controls

- Row Level Security is mandatory on all exposed Supabase tables.
- Listener profile reads and updates are restricted to the active `auth.uid()` and covered by owner/cross-owner pgTAP tests.
- Catalog clients receive only published metadata through authenticated SELECT policies; anonymous reads and all client catalog mutations are denied.
- Editorial shelf clients receive only published shelves whose referenced tracks are also published; anonymous access and listener mutations are denied by RLS and grants.
- The Room cache contains metadata only. It never stores database credentials, R2 keys, or privileged media URLs.
- Recently played metadata is bounded, remains local to the device, is keyed by the authenticated listener ID, and accepts only track IDs present in the verified Room catalog.
- Storage and database authorization are verified server-side; client claims are never trusted directly.
- The media Worker verifies Supabase ES256 JWT issuer, audience, role, and UUID subject through JWKS, then forwards the same bearer session to PostgREST so catalog RLS remains authoritative.
- Media responses support one validated byte range, stream the R2 body without application buffering, use private/no-store caching, and return bounded errors that do not expose upstream details.
- Schema changes use reviewed, reversible migrations.
- Dependency, lint, unit, debug/test APK, and minified R8 release-build checks must pass before a push is considered releasable.
