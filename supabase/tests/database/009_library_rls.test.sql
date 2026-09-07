begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(15);

insert into auth.users (
  id, instance_id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at
)
values
  (
    '33333333-3333-4333-8333-333333333333',
    '00000000-0000-0000-0000-000000000000',
    'authenticated', 'authenticated', 'library-one@rakyzu.test', '', now(),
    '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()
  ),
  (
    '44444444-4444-4444-8444-444444444444',
    '00000000-0000-0000-0000-000000000000',
    'authenticated', 'authenticated', 'library-two@rakyzu.test', '', now(),
    '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()
  );

select has_table('public', 'liked_tracks', 'liked tracks table should exist');
select has_table('public', 'saved_albums', 'saved albums table should exist');
select has_table('public', 'followed_artists', 'followed artists table should exist');
select has_function('public', 'get_library_items', array[]::text[], 'library projection should exist');
select has_function(
  'public', 'set_library_item', array['text', 'uuid', 'boolean'],
  'library mutation function should exist'
);

insert into public.artists (id, name, is_published)
values ('c1000000-0000-4000-8000-000000000001', 'Hidden Library Artist', false);
insert into public.albums (id, artist_id, title, is_published)
values (
  'c2000000-0000-4000-8000-000000000001',
  'c1000000-0000-4000-8000-000000000001',
  'Hidden Library Album',
  false
);
insert into public.tracks (id, album_id, title, duration_ms, track_number, is_published)
values (
  'c3000000-0000-4000-8000-000000000001',
  'c2000000-0000-4000-8000-000000000001',
  'Hidden Library Track',
  120000,
  1,
  false
);

insert into public.liked_tracks (user_id, track_id)
values (
  '44444444-4444-4444-8444-444444444444',
  'a3000000-0000-4000-8000-000000000001'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '33333333-3333-4333-8333-333333333333', true);
select set_config(
  'request.jwt.claims',
  '{"sub":"33333333-3333-4333-8333-333333333333","role":"authenticated"}',
  true
);

select results_eq(
  $$ select success from public.set_library_item(
    'track', 'a3000000-0000-4000-8000-000000000001', true
  ) $$,
  array[true],
  'listener should like a published track'
);
select results_eq(
  $$ select success from public.set_library_item(
    'album', 'a2000000-0000-4000-8000-000000000001', true
  ) $$,
  array[true],
  'listener should save a published album'
);
select results_eq(
  $$ select success from public.set_library_item(
    'artist', 'a1000000-0000-4000-8000-000000000001', true
  ) $$,
  array[true],
  'listener should follow a published artist'
);
select results_eq(
  $$ select kind from public.get_library_items() order by kind $$,
  array['album'::text, 'artist'::text, 'track'::text],
  'library projection should return all saved item kinds'
);
select is(
  (select count(*)::integer from public.liked_tracks),
  1,
  'RLS should expose only the current listener liked rows'
);
select results_eq(
  $$ select success from public.set_library_item(
    'playlist', 'a3000000-0000-4000-8000-000000000001', true
  ) $$,
  array[false],
  'unknown item kinds should fail closed'
);
select results_eq(
  $$ select success from public.set_library_item(
    'track', 'c3000000-0000-4000-8000-000000000001', true
  ) $$,
  array[false],
  'unpublished tracks should fail closed'
);
select results_eq(
  $$ select success from public.set_library_item(
    'track', 'a3000000-0000-4000-8000-000000000001', false
  ) $$,
  array[true],
  'unlike should be idempotent'
);
select is(
  (select count(*)::integer from public.get_library_items()),
  2,
  'unliked track should leave saved album and followed artist'
);

reset role;
set local role anon;
select throws_ok(
  $$ select * from public.get_library_items() $$,
  '42501',
  'permission denied for function get_library_items',
  'anonymous clients should not read Library'
);

select * from finish();
rollback;
