begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select no_plan();

select has_table('public', 'trusted_play_events', 'trusted play evidence exists');
select has_table('public', 'commerce_event_ledger', 'commerce webhook ledger exists');
select has_table('public', 'commerce_entitlements', 'commerce entitlements exist');
select has_table('public', 'commerce_receipts', 'commerce receipts exist');
select has_table('public', 'account_enforcement_events', 'account enforcement evidence exists');
select has_table('public', 'account_appeals', 'account appeals exist');
select has_table('public', 'account_deletion_requests', 'account deletion workflow exists');
select has_table('public', 'security_alerts', 'security alert inventory exists');
select has_table('public', 'privileged_action_requests', 'protected action approval exists');

select ok((select bool_and(relrowsecurity and relforcerowsecurity) from pg_class where oid in (
  'public.trusted_play_events'::regclass, 'public.commerce_event_ledger'::regclass,
  'public.commerce_entitlements'::regclass, 'public.commerce_receipts'::regclass,
  'public.account_enforcement_events'::regclass, 'public.account_appeals'::regclass,
  'public.account_deletion_requests'::regclass, 'public.security_alerts'::regclass,
  'public.privileged_action_requests'::regclass
)), 'all lifecycle tables force RLS');
select ok(not has_table_privilege('authenticated', 'public.trusted_play_events', 'INSERT'),
  'Android cannot forge play evidence');
select ok(not has_table_privilege('authenticated', 'public.commerce_event_ledger', 'INSERT'),
  'Android cannot forge commerce evidence');
select ok(not has_function_privilege('authenticated',
  'public.service_record_play_event(text,uuid,uuid,timestamptz,integer,boolean,text)', 'EXECUTE'),
  'trusted play ingestion is service-role only');
select ok(not has_function_privilege('authenticated',
  'public.service_ingest_commerce_event(text,text,text,uuid,text,text,bigint,text,timestamptz,text,jsonb)',
  'EXECUTE'), 'commerce ingestion is service-role only');
select has_function('public', 'artist_analytics', array['uuid','date','date'],
  'privacy-scoped Artist analytics RPC exists');
select has_function('public', 'admin_commerce_dashboard', array[]::text[],
  'least-privilege commerce dashboard exists');
select has_function('public', 'account_submit_appeal', array['uuid','text'],
  'listener appeal RPC exists');
select has_function('public', 'admin_approve_deletion', array['uuid'],
  'dual-control deletion approval RPC exists');
select has_function('public', 'admin_security_dashboard', array[]::text[],
  'security dashboard RPC exists');
select has_function('public', 'admin_export_account_data', array['uuid'],
  'scoped account export RPC exists');

insert into auth.users(id, email) values
  ('b5000000-0000-4000-8000-000000000001', 'lifecycle-listener@rakyzu.test'),
  ('b5000000-0000-4000-8000-000000000002', 'lifecycle-listener-two@rakyzu.test');

insert into public.artists(id, name) values
  ('b5000000-0000-4000-8000-000000000010', 'Lifecycle Test Artist');
insert into public.albums(id, artist_id, title) values
  ('b5000000-0000-4000-8000-000000000011',
   'b5000000-0000-4000-8000-000000000010', 'Lifecycle Test Album');
insert into public.tracks(id, album_id, title, duration_ms, track_number) values
  ('b5000000-0000-4000-8000-000000000012',
   'b5000000-0000-4000-8000-000000000011', 'Lifecycle Test Track', 180000, 1);

set local role service_role;
select is(public.service_record_play_event(
  'play-event-idempotency-001', 'b5000000-0000-4000-8000-000000000001',
  'b5000000-0000-4000-8000-000000000012', now(), 180000, true, 'id')->>'duplicate',
  'false', 'first trusted play event is accepted');
select is(public.service_record_play_event(
  'play-event-idempotency-001', 'b5000000-0000-4000-8000-000000000001',
  'b5000000-0000-4000-8000-000000000012', now(), 180000, true, 'id')->>'duplicate',
  'true', 'duplicate trusted play event is acknowledged without duplication');
select is(public.service_ingest_commerce_event(
  'google_play', 'commerce-idempotency-001', 'purchase_completed',
  'b5000000-0000-4000-8000-000000000001', 'customer-test-001', 'premium_monthly',
  49000, 'idr', now(), repeat('a', 64), '{"source":"test"}'::jsonb)->>'duplicate',
  'false', 'first signed commerce event is accepted');
select is(public.service_ingest_commerce_event(
  'google_play', 'commerce-idempotency-001', 'purchase_completed',
  'b5000000-0000-4000-8000-000000000001', 'customer-test-001', 'premium_monthly',
  49000, 'idr', now(), repeat('a', 64), '{"source":"test"}'::jsonb)->>'duplicate',
  'true', 'duplicate commerce event is acknowledged without side effects');
select is((select count(*)::text from public.commerce_receipts
  where receipt_reference = 'google_play:commerce-idempotency-001'), '1',
  'duplicate commerce delivery creates one receipt');
reset role;

select set_config('rakyzu.test_ceo_id',
  (select id::text from auth.users where lower(email) = 'rakyzudev@gmail.com' limit 1), true);
set local role authenticated;
select set_config('request.jwt.claim.sub', 'b5000000-0000-4000-8000-000000000001', true);
select set_config('request.jwt.claims',
  '{"sub":"b5000000-0000-4000-8000-000000000001","role":"authenticated"}', true);
select is(public.account_lifecycle_context()->>'state', 'active',
  'a new listener begins active');
select is(public.account_request_deletion('Please close this account after the retention period')->>'status',
  'pending', 'listener can create a retention-aware deletion request');

select set_config('request.jwt.claim.sub', current_setting('rakyzu.test_ceo_id'), true);
select set_config('request.jwt.claims', jsonb_build_object(
  'sub', current_setting('rakyzu.test_ceo_id'), 'role', 'authenticated')::text, true);
select throws_ok(format('select public.admin_enforce_account(%L::uuid, %L, %L, null)',
  current_setting('rakyzu.test_ceo_id'), 'ban',
  'A privileged actor must never lock their own active account'), '22023',
  'Self-enforcement is not allowed', 'privileged actor cannot enforce their own account');
select is(public.admin_enforce_account('b5000000-0000-4000-8000-000000000001', 'ban',
  'Repeated verified abuse after documented warnings', null)->>'state', 'banned',
  'authorized staff can record a reasoned ban');

select set_config('request.jwt.claim.sub', 'b5000000-0000-4000-8000-000000000001', true);
select set_config('request.jwt.claims',
  '{"sub":"b5000000-0000-4000-8000-000000000001","role":"authenticated"}', true);
select is(public.account_lifecycle_context()->>'state', 'banned',
  'listener sees server-authoritative enforcement state');
select set_config('rakyzu.test_enforcement_id', (select id::text from public.account_enforcement_events
  where user_id = 'b5000000-0000-4000-8000-000000000001' order by created_at desc limit 1), true);
select is(public.account_submit_appeal(current_setting('rakyzu.test_enforcement_id')::uuid,
  'I request a review and can provide additional context for this decision')->>'status',
  'pending', 'listener can appeal their own enforcement once');

select set_config('request.jwt.claim.sub', 'b5000000-0000-4000-8000-000000000002', true);
select set_config('request.jwt.claims',
  '{"sub":"b5000000-0000-4000-8000-000000000002","role":"authenticated"}', true);
select throws_ok(format('select public.account_submit_appeal(%L::uuid, %L)',
  current_setting('rakyzu.test_enforcement_id'),
  'I should not be able to appeal another listener enforcement record'), '22023',
  'Enforcement is unavailable', 'listener cannot appeal another account enforcement');

reset role;
select * from finish();
rollback;
