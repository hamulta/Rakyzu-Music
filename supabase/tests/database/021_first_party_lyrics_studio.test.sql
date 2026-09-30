begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select no_plan();

select has_table('public', 'track_lyrics_revisions', 'immutable lyrics revisions exist');
select ok((select relrowsecurity and relforcerowsecurity from pg_class
  where oid = 'public.track_lyrics_revisions'::regclass), 'lyrics revisions force RLS');
select ok(not has_table_privilege('authenticated', 'public.track_lyrics_revisions', 'SELECT,INSERT,UPDATE,DELETE'),
  'Android cannot read or forge lyrics history');
select has_function('public', 'get_editable_track_lyrics', array['uuid'],
  'authorized lyrics read RPC exists');
select has_function('public', 'upsert_track_lyrics', array['uuid','text','text','text','jsonb','boolean'],
  'validated lyrics mutation RPC exists');
select has_function('public', 'delete_track_lyrics', array['uuid'],
  'logical lyrics deletion RPC exists');

insert into auth.users(id, email) values
  ('b8000000-0000-4000-8000-000000000001', 'lyrics-ceo@rakyzu.test'),
  ('b8000000-0000-4000-8000-000000000002', 'lyrics-listener@rakyzu.test');
insert into public.staff_assignments(user_id, role, active) values
  ('b8000000-0000-4000-8000-000000000001', 'ceo', true);
insert into public.artists(id, name, is_published) values
  ('b8000000-0000-4000-8000-000000000010', 'Lyrics Artist', true);
insert into public.albums(id, artist_id, title, is_published) values
  ('b8000000-0000-4000-8000-000000000011',
   'b8000000-0000-4000-8000-000000000010', 'Lyrics Album', true);
insert into public.tracks(id, album_id, title, duration_ms, track_number, is_published) values
  ('b8000000-0000-4000-8000-000000000012',
   'b8000000-0000-4000-8000-000000000011', 'Lyrics Track', 180000, 1, true);

set local role authenticated;
select set_config('request.jwt.claim.sub', 'b8000000-0000-4000-8000-000000000002', true);
select set_config('request.jwt.claims',
  '{"sub":"b8000000-0000-4000-8000-000000000002","role":"authenticated"}', true);
select throws_ok(
  $$select public.get_editable_track_lyrics('b8000000-0000-4000-8000-000000000012')$$,
  '42501', 'Lyrics management access is required', 'listener cannot access lyrics drafts');

select set_config('request.jwt.claim.sub', 'b8000000-0000-4000-8000-000000000001', true);
select set_config('request.jwt.claims',
  '{"sub":"b8000000-0000-4000-8000-000000000001","role":"authenticated"}', true);
select is(public.upsert_track_lyrics(
  'b8000000-0000-4000-8000-000000000012', 'time_synced', 'lrc', 'id',
  '[{"text":"Baris satu","startTimeMs":1000},{"text":"Baris dua","startTimeMs":2500}]', false
)->>'status', 'draft', 'CEO can save validated first-party lyrics as draft');
select is(public.get_track_context(
  'b8000000-0000-4000-8000-000000000012', 'ID')->'lyrics'->>'kind',
  'unavailable', 'draft lyrics never leak to listeners');
select throws_ok(
  $$select public.upsert_track_lyrics(
    'b8000000-0000-4000-8000-000000000012', 'time_synced', 'srt', 'id',
    '[{"text":"Later","startTimeMs":2000},{"text":"Earlier","startTimeMs":1000}]', false)$$,
  '22023', 'Lyrics payload is invalid', 'non-monotonic timestamps fail closed');
select is(public.upsert_track_lyrics(
  'b8000000-0000-4000-8000-000000000012', 'time_synced', 'lrc', 'id',
  '[{"text":"Baris satu","startTimeMs":1000},{"text":"Baris dua","startTimeMs":2500}]', true
)->>'status', 'published', 'authorized staff can publish owned lyrics');
select is(public.get_track_context(
  'b8000000-0000-4000-8000-000000000012', 'ID')->'lyrics'->>'sourceName',
  'Rakyzu Music', 'listener projection exposes only Rakyzu first-party attribution');
select is(public.delete_track_lyrics(
  'b8000000-0000-4000-8000-000000000012')->>'kind',
  'unavailable', 'authorized staff can remove current lyrics');

reset role;
select is((select count(*)::integer from public.track_lyrics_revisions
  where track_id = 'b8000000-0000-4000-8000-000000000012'), 3,
  'every save and deletion retains an immutable revision');
select * from finish();
rollback;
