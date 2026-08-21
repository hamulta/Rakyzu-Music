# RLS Audit 0.9.0 — Full Matrix

| Table | Operation | free | premium | staff (own) | staff (other) | admin | owner | Expected | Status | Fix |
|---|---|---|---|---|---|---|---|---|---|---|
| users SELECT | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | authenticated can read | PASS | — |
| users UPDATE own (non-role) | ✓ self | ✓ self | ✓ self | — | ✓ self | ✓ | Own profile only | PASS | — |
| users UPDATE own role/is_banned | ✗ | ✗ | ✗ | — | ✗ | ✓ | Only owner via trigger | PASS after 20260822 trigger | prevent_role_escalation |
| users UPDATE other role | ✗ | ✗ | ✗ (only free/premium/staff limited) | ✗ | limited | ✓ | Admin limited, Owner full | PASS after trigger |  |
| artists/album/songs INSERT | ✗ | ✗ | ✓ | ✓ | ✓ | ✓ | staff/admin/owner | PASS | gap: staff can insert any — acceptable MVP |
| artists/album/songs UPDATE/DELETE own | — | — | ✓ | — | ✓ | ✓ | staff own, admin/owner all | **GAP** | staff can update any (should be own only) — documented, fix deferred to 0.9.x hardening backlog |
| artists/album/songs SELECT | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | public | PASS | — |
| songs.is_published toggle | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ | admin/owner only | **FIXED** | trigger + app filter |
| playlists SELECT | own+public | own+public | own+public | own+public | own+public | own+public | ✓ | PASS | — |
| playlists INSERT/UPDATE/DELETE | own | own | own | ✗ other | own | own | owner only | PASS | — |
| playlist_songs | via playlist ownership/public read | | | | | | | PASS | — |
| liked_songs/follows/play_history | own only | own only | own only | ✗ | own | own | | PASS | — |
| subscriptions SELECT | own | own | **GAP** staff could view all | owner | admin | owner | free/premium own only, admin/owner all | **FIXED** | drop staff, keep admin/owner |
| subscriptions INSERT | via Edge Function service_role | | | | | | | PASS | — |
| genres SELECT | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | PASS | — |
| pricing_plans SELECT | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | public | PASS | — |
| pricing_plans UPDATE | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | owner only | PASS | — |
| ad_impressions INSERT | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | PASS | — |
| ad_impressions SELECT | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ | admin/owner only | PASS | — |

Manual per-role CRUD tested via Supabase SQL + Postman (anon/auth tokens). Gaps fixed in `20260823000100_rls_audit_fix.sql`.
