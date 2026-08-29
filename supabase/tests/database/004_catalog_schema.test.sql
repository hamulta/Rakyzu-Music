begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(13);

select has_table('public', 'artists', 'artists table should exist');
select has_table('public', 'albums', 'albums table should exist');
select has_table('public', 'tracks', 'tracks table should exist');
select has_column('public', 'albums', 'artist_id', 'albums should reference an artist');
select has_column('public', 'tracks', 'album_id', 'tracks should reference an album');
select has_column('public', 'tracks', 'duration_ms', 'tracks should store duration');
select has_column('public', 'tracks', 'track_number', 'tracks should store album order');

select ok(
  (
    select bool_and(relrowsecurity and relforcerowsecurity)
    from pg_class
    where oid in (
      'public.artists'::regclass,
      'public.albums'::regclass,
      'public.tracks'::regclass
    )
  ),
  'all catalog tables should enforce row level security'
);

select ok(
  (
    select count(*) = 1 and bool_and(policyname = 'artists_select_published')
    from pg_policies
    where schemaname = 'public' and tablename = 'artists'
  ),
  'artists should expose only its published-read policy'
);
select ok(
  (
    select count(*) = 1 and bool_and(policyname = 'albums_select_published')
    from pg_policies
    where schemaname = 'public' and tablename = 'albums'
  ),
  'albums should expose only its published-read policy'
);
select ok(
  (
    select count(*) = 1 and bool_and(policyname = 'tracks_select_published')
    from pg_policies
    where schemaname = 'public' and tablename = 'tracks'
  ),
  'tracks should expose only its published-read policy'
);

select results_eq(
  $$
    select count(*)::integer
    from public.tracks
    where album_id = 'a2000000-0000-4000-8000-000000000001'
  $$,
  array[3],
  'Rakyzu synthetic bootstrap album should contain three tracks'
);

select results_eq(
  $$
    select title
    from public.tracks
    where album_id = 'a2000000-0000-4000-8000-000000000001'
    order by disc_number, track_number
  $$,
  array['Midnight Signal'::text, 'Afterglow Circuit'::text, 'New Horizons'::text],
  'bootstrap tracks should retain deterministic album order'
);

select * from finish();
rollback;
