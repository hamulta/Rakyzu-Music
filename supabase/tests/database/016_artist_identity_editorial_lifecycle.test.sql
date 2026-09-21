begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select no_plan();

select has_table('public', 'artist_account_links', 'Artist account ownership is explicit');
select has_table('public', 'artist_terms_consents', 'Artist consent evidence is durable');
select has_table('public', 'staff_assignment_intents', 'staff email intents are recorded');
select has_table('public', 'profile_avatar_assets', 'profile image inventory exists');
select has_table('public', 'editorial_shelf_artwork_assets', 'recommendation artwork inventory exists');
select ok((select bool_and(relrowsecurity and relforcerowsecurity) from pg_class where oid in (
  'public.artist_account_links'::regclass, 'public.artist_terms_consents'::regclass,
  'public.staff_assignment_intents'::regclass, 'public.profile_avatar_assets'::regclass,
  'public.editorial_shelf_artwork_assets'::regclass
)), 'new account and media tables force RLS');
select ok(not has_table_privilege('authenticated', 'public.artist_account_links', 'INSERT,UPDATE,DELETE'),
  'listeners cannot grant themselves Artist status');
select ok(not has_table_privilege('authenticated', 'public.staff_assignment_intents', 'SELECT,INSERT,UPDATE,DELETE'),
  'staff assignment intents stay server-only');
select has_function('public', 'accept_artist_terms', array['text'], 'Artist consent RPC exists');
select has_function('public', 'get_track_media_key', array['uuid','text'],
  'streaming resolves the authoritative audio variant');

insert into auth.users(id, email) values
  ('b3000000-0000-4000-8000-000000000001', 'rakyzudev@gmail.com'),
  ('b3000000-0000-4000-8000-000000000002', 'artist-identity@rakyzu.test');
select is((select role from public.staff_assignments
  where user_id = 'b3000000-0000-4000-8000-000000000001'), 'ceo',
  'designated CEO email receives the CEO role without manual assignment');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'b3000000-0000-4000-8000-000000000002', true);
select set_config('request.jwt.claims',
  '{"sub":"b3000000-0000-4000-8000-000000000002","role":"authenticated"}', true);
select throws_ok($$select public.admin_create_artist('Denied Artist', null)$$,
  '42501', 'Catalog drafting is not permitted', 'listener cannot create an Artist profile');

select set_config('request.jwt.claim.sub', 'b3000000-0000-4000-8000-000000000001', true);
select set_config('request.jwt.claims',
  '{"sub":"b3000000-0000-4000-8000-000000000001","role":"authenticated"}', true);
select is(public.admin_create_artist('Test Identity Artist', 'artist-identity@rakyzu.test')->>'accountStatus',
  'pending_consent', 'exact existing email creates a pending Artist profile');
select is(public.admin_upsert_editorial_shelf(null, 'Test Identity Card', 'New music', 0, null, true)->>'title',
  'Test Identity Card', 'CEO can create an editorial card without a target track');
select ok(jsonb_array_length(public.admin_list_recommendations()) > 0,
  'Admin can list editable placeholder and newly created recommendation cards');

select set_config('request.jwt.claim.sub', 'b3000000-0000-4000-8000-000000000002', true);
select set_config('request.jwt.claims',
  '{"sub":"b3000000-0000-4000-8000-000000000002","role":"authenticated"}', true);
select is(public.get_my_profile_context()->'artist'->>'status', 'pending_consent',
  'the exact matched listener sees the in-app Artist invitation');
select is(public.accept_artist_terms('2026-09')->>'status', 'active',
  'Artist identity activates only after the current terms are accepted');
select is(public.get_my_profile_context()->>'identityKind', 'artist',
  'accepted Artist identity is distinct from a listener');
select is(public.artist_update_biography('Original Artist profile')->>'biography',
  'Original Artist profile', 'only the linked Artist can edit the biography');

select set_config('request.jwt.claim.sub', 'b3000000-0000-4000-8000-000000000001', true);
select set_config('request.jwt.claims',
  '{"sub":"b3000000-0000-4000-8000-000000000001","role":"authenticated"}', true);
select is(public.admin_archive_artist((select id from public.artists
  where name = 'Test Identity Artist'))->>'archived', 'true',
  'Artist archive is reversible at the database row level');
select is(public.admin_delete_editorial_shelf((select id from public.editorial_shelves
  where title = 'Test Identity Card'))->>'deleted', 'true',
  'authorized editor can delete an editorial card');

reset role;
select is((select count(*)::integer from public.artist_terms_consents
  where user_id = 'b3000000-0000-4000-8000-000000000002'), 1,
  'consent evidence is stored once');
select * from finish();
rollback;
