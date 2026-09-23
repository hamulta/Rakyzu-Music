begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select no_plan();

select has_table('public', 'artist_artwork_assets', 'Artist artwork inventory exists');
select ok((select relrowsecurity and relforcerowsecurity from pg_class
  where oid = 'public.artist_artwork_assets'::regclass), 'Artist artwork forces RLS');
select ok(not has_table_privilege('authenticated', 'public.artist_artwork_assets',
  'INSERT,UPDATE,DELETE'), 'listeners cannot directly edit Artist artwork inventory');
select has_function('public', 'artist_workspace_context', array[]::text[],
  'Artist workspace context is available');
select has_function('public', 'artist_submit_catalog_review',
  array['text', 'uuid', 'text'], 'Artist submissions use an explicit review gate');

insert into auth.users(id, email)
select 'b4000000-0000-4000-8000-000000000001', 'rakyzudev@gmail.com'
where not exists (select 1 from auth.users where email = 'rakyzudev@gmail.com');
insert into auth.users(id, email) values
  ('b4000000-0000-4000-8000-000000000002', 'workspace-artist@rakyzu.test'),
  ('b4000000-0000-4000-8000-000000000003', 'workspace-outsider@rakyzu.test');
select set_config('rakyzu.test_ceo_id',
  (select id::text from auth.users where email = 'rakyzudev@gmail.com'), true);

set local role authenticated;
select set_config('request.jwt.claim.sub', 'b4000000-0000-4000-8000-000000000003', true);
select set_config('request.jwt.claims',
  '{"sub":"b4000000-0000-4000-8000-000000000003","role":"authenticated"}', true);
select throws_ok($$select public.artist_workspace_context()$$, '42501',
  'An active Artist identity is required', 'unlinked listener cannot open Artist workspace');

select set_config('request.jwt.claim.sub', current_setting('rakyzu.test_ceo_id'), true);
select set_config('request.jwt.claims',
  jsonb_build_object('sub', current_setting('rakyzu.test_ceo_id'),
    'role', 'authenticated')::text, true);
select is(public.admin_create_artist('Workspace Artist', 'workspace-artist@rakyzu.test')->>'accountStatus',
  'pending_consent', 'CEO links Artist by exact email');

select set_config('request.jwt.claim.sub', 'b4000000-0000-4000-8000-000000000002', true);
select set_config('request.jwt.claims',
  '{"sub":"b4000000-0000-4000-8000-000000000002","role":"authenticated"}', true);
select throws_ok($$select public.artist_workspace_context()$$, '42501',
  'An active Artist identity is required', 'pending Artist must accept terms before workspace access');
select is(public.accept_artist_terms('2026-09')->>'status', 'active',
  'Artist activates through existing in-app consent');
select is(public.artist_workspace_context()->>'artistName', 'Workspace Artist',
  'Artist can read their scoped workspace');
select set_config('rakyzu.test_album_id',
  public.artist_create_album_draft(public.artist_artwork_upload_scope(),
    'My Draft', null)->>'id', true);
select is(public.artist_workspace_context()->'albums'->0->>'title',
  'My Draft', 'Artist can create an unpublished draft');
select is(public.artist_create_track_draft(current_setting('rakyzu.test_album_id')::uuid,
  'First Song', 120000, 1, 1, false)->>'title',
  'First Song', 'Artist can add a track draft');
select throws_ok($$select public.artist_submit_catalog_review('release',
  current_setting('rakyzu.test_album_id')::uuid, '')$$, '23514',
  'Upload standard audio for every track', 'release review requires standard audio');

select set_config('request.jwt.claim.sub', 'b4000000-0000-4000-8000-000000000003', true);
select set_config('request.jwt.claims',
  '{"sub":"b4000000-0000-4000-8000-000000000003","role":"authenticated"}', true);
select is(public.artist_can_edit_album(current_setting('rakyzu.test_album_id')::uuid),
  false, 'unlinked listener cannot edit Artist draft');
select throws_ok($$select public.artist_create_album_draft(
  (select artist_id from public.albums where id = current_setting('rakyzu.test_album_id')::uuid),
  'Unauthorized', null)$$, '42501',
  'Artist draft access is required', 'unlinked listener cannot create Artist draft');

reset role;
select * from finish();
rollback;
