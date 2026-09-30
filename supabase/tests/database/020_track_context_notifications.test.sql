begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select no_plan();

select has_table('public', 'track_contexts', 'licensed track context exists');
select has_table('public', 'release_notification_preferences', 'release preferences exist');
select has_table('public', 'release_notification_deliveries', 'server delivery queue exists');
select ok((select bool_and(relrowsecurity and relforcerowsecurity) from pg_class where oid in (
  'public.track_contexts'::regclass,
  'public.release_notification_preferences'::regclass,
  'public.release_notification_deliveries'::regclass
)), 'all track-context and notification tables force RLS');
select ok(not has_table_privilege('authenticated', 'public.track_contexts', 'SELECT'),
  'Android cannot bypass rights-aware context projection');
select ok(not has_table_privilege('authenticated', 'public.release_notification_deliveries', 'INSERT'),
  'Android cannot forge notification delivery');
select has_function('public', 'get_track_context', array['uuid','text'],
  'rights-aware track context RPC exists');
select has_function('public', 'set_release_notification_preference', array['text'],
  'account preference mutation RPC exists');

insert into auth.users(id, email) values
  ('b7000000-0000-4000-8000-000000000001', 'context-listener@rakyzu.test');
insert into public.artists(id, name, is_published) values
  ('b7000000-0000-4000-8000-000000000010', 'Context Artist', true);
insert into public.albums(id, artist_id, title, is_published) values
  ('b7000000-0000-4000-8000-000000000011',
   'b7000000-0000-4000-8000-000000000010', 'Context Album', true);
insert into public.tracks(id, album_id, title, duration_ms, track_number, is_published) values
  ('b7000000-0000-4000-8000-000000000012',
   'b7000000-0000-4000-8000-000000000011', 'Context Track', 180000, 1, true);
insert into public.track_contexts(
  track_id, lyrics_kind, lyrics_lines, lyrics_provider_name, lyrics_provider_notice,
  credits, allowed_country_codes
) values (
  'b7000000-0000-4000-8000-000000000012', 'time_synced',
  '[{"text":"Licensed line","startTimeMs":0}]', 'Rights Provider', 'Used under license',
  '[{"displayName":"Context Writer","role":"songwriter","sourceName":"Label source"}]',
  array['id']
);

set local role authenticated;
select set_config('request.jwt.claim.sub', 'b7000000-0000-4000-8000-000000000001', true);
select set_config('request.jwt.claims',
  '{"sub":"b7000000-0000-4000-8000-000000000001","role":"authenticated"}', true);
select is(public.get_track_context(
  'b7000000-0000-4000-8000-000000000012', 'ID')->'lyrics'->>'kind',
  'time_synced', 'licensed territory receives synchronized lyrics');
select is(public.get_track_context(
  'b7000000-0000-4000-8000-000000000012', 'US')->'lyrics'->>'kind',
  'unavailable', 'unlicensed territory fails closed without hiding credits');
select is(public.get_release_notification_preference()->>'preference', 'off',
  'release notifications default off');
select is(public.set_release_notification_preference('followed_artists')->>'preference',
  'followed_artists', 'listener can opt into followed-Artist releases');
select is(public.get_release_notification_preference()->>'preference', 'followed_artists',
  'preference is account scoped and server authoritative');
select throws_ok(
  $$select public.set_release_notification_preference('everything')$$,
  '22023', 'Invalid notification preference', 'unknown preference fails closed');

reset role;
select * from finish();
rollback;
