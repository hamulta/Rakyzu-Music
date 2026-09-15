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
- Catalog clients receive only published, non-restricted, server-time-available metadata through authenticated SELECT policies; anonymous reads and all client catalog mutations are denied.
- Editorial shelf clients receive only published shelves whose referenced tracks are also published; anonymous access and listener mutations are denied by RLS and grants.
- The Room cache contains metadata only. It never stores database credentials, R2 keys, or privileged media URLs.
- Recently played metadata is bounded, remains local to the device, is keyed by the authenticated listener ID, and accepts only track IDs present in the verified Room catalog.
- Storage and database authorization are verified server-side; client claims are never trusted directly.
- Staff authority comes only from active database assignments and fixed permission rows, never from
  user-editable Auth metadata or Android navigation state. Direct authenticated writes to roles,
  moderation, audit, media inventory, and catalog tables are revoked.
- Every Admin Worker request verifies the Supabase session and current staff context. Ranked RPCs
  prevent lower offices from assigning equal/higher roles, reserve CEO appointments to an active
  CEO, and prevent disabling the final active CEO.
- Audio administration accepts only bounded MP3 input, derives the exact private R2 key from a
  validated track UUID and quality, removes the object if database recording fails, and requires a
  standard variant for every track before atomic catalog publication.
- Content enforcement is append-only and reasoned. Quarantine or takedown changes listener
  visibility through RLS; restore preserves the full evidence chain rather than deleting history.
- Artist/label team membership is scope-only and cannot grant staff authority. A catalog artist row
  or matching email is not treated as an authenticated Artist identity before explicit invitation
  consent is implemented.
- Album artwork accepts only bounded WebP with a validated signature and canonical private R2 key;
  compensating deletion removes an object when its database inventory write fails.
- Release publication requires approved artwork and metadata reviews from an authorized account
  other than the submitter. Future schedules remain hidden until database server time reaches the
  approved instant.
- Audit export is capped and excludes raw details and email. Retention settings record intent only;
  actual evidence deletion is a separate destructive maintenance operation requiring backup and
  explicit approval.
- The media Worker verifies Supabase ES256 JWT issuer, audience, role, and UUID subject through JWKS, then forwards the same bearer session to PostgREST so catalog RLS remains authoritative.
- Media responses support one validated byte range, stream the R2 body without application buffering, use private/no-store caching, and return bounded errors that do not expose upstream details.
- Schema changes use reviewed, reversible migrations.
- Dependency, lint, unit, debug/test APK, and minified R8 release-build checks must pass before a push is considered releasable.
