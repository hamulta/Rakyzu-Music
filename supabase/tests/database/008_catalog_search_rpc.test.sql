begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(8);

select has_function(
  'public',
  'search_catalog',
  array['text', 'integer', 'integer'],
  'bounded catalog search function should exist'
);

insert into public.artists (id, name, is_published)
values ('c1000000-0000-4000-8000-000000000001', 'Private Signal Artist', false);

insert into public.albums (id, artist_id, title, is_published)
values (
  'c2000000-0000-4000-8000-000000000001',
  'c1000000-0000-4000-8000-000000000001',
  'Private Signal Album',
  false
);

insert into public.tracks (
  id,
  album_id,
  title,
  duration_ms,
  track_number,
  is_published
)
values (
  'c3000000-0000-4000-8000-000000000001',
  'c2000000-0000-4000-8000-000000000001',
  'Private Signal Track',
  120000,
  1,
  false
);

set local role authenticated;
select set_config(
  'request.jwt.claims',
  '{"sub":"55555555-5555-4555-8555-555555555555","role":"authenticated"}',
  true
);

select results_eq(
  $$ select distinct artist_name from public.search_catalog('rakyzu sessions', 0, 30) $$,
  array['Rakyzu Sessions'::text],
  'association matches should retain canonical published artist metadata'
);

select is(
  (select count(*)::integer from public.search_catalog('private signal', 0, 30)),
  0,
  'unpublished catalog rows should never cross the authenticated RPC boundary'
);

select is(
  (select count(*)::integer from public.search_catalog('signal', 0, 1)),
  1,
  'page limit should be enforced'
);

select isnt(
  (select kind || ':' || id::text from public.search_catalog('signal', 0, 1)),
  (select kind || ':' || id::text from public.search_catalog('signal', 1, 1)),
  'successive offsets should return distinct deterministic rows'
);

select is(
  (select total_count::integer from public.search_catalog('signal', 0, 1)),
  4,
  'each page should expose the exact total count'
);

select is(
  (select count(*)::integer from public.search_catalog('x', 0, 30)),
  0,
  'queries shorter than two characters should fail closed with no rows'
);

reset role;
set local role anon;

select throws_ok(
  $$ select * from public.search_catalog('signal', 0, 30) $$,
  '42501',
  'permission denied for function search_catalog',
  'anonymous clients should not execute catalog search'
);

select * from finish();
rollback;
