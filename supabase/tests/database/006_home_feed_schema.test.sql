begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(14);

select has_table('public', 'editorial_shelves', 'editorial shelves table should exist');
select has_table('public', 'editorial_shelf_tracks', 'editorial shelf tracks table should exist');
select has_column('public', 'editorial_shelves', 'position', 'shelves should have deterministic order');
select has_column('public', 'editorial_shelves', 'is_published', 'shelves should have a publication gate');
select has_column('public', 'editorial_shelf_tracks', 'track_id', 'shelf entries should reference tracks');
select has_column('public', 'editorial_shelf_tracks', 'position', 'shelf entries should have deterministic order');

select ok(
  (
    select bool_and(relrowsecurity and relforcerowsecurity)
    from pg_class
    where oid in (
      'public.editorial_shelves'::regclass,
      'public.editorial_shelf_tracks'::regclass
    )
  ),
  'all editorial tables should enforce row level security'
);

select ok(
  (
    select count(*) = 1 and bool_and(policyname = 'editorial_shelves_select_published')
    from pg_policies
    where schemaname = 'public' and tablename = 'editorial_shelves'
  ),
  'editorial shelves should expose only their published-read policy'
);

select ok(
  (
    select count(*) = 1 and bool_and(policyname = 'editorial_shelf_tracks_select_published')
    from pg_policies
    where schemaname = 'public' and tablename = 'editorial_shelf_tracks'
  ),
  'editorial shelf entries should expose only their published-read policy'
);

select results_eq(
  $$ select count(*)::integer from public.editorial_shelves where position between 0 and 106 $$,
  array[5],
  'bootstrap data should contain the five governed Home editorial shelves'
);

select results_eq(
  $$ select count(*)::integer from public.editorial_shelf_tracks where shelf_id in (
    select id from public.editorial_shelves where position between 0 and 106
  ) $$,
  array[15],
  'bootstrap Home editorial shelves should contain fifteen ordered entries'
);

select results_eq(
  $$ select title from public.editorial_shelves where position between 0 and 106 order by position $$,
  array['Pop Mix'::text, 'Chill Mix'::text, 'Fresh Mix'::text, 'After Hours'::text, 'Daily Discovery'::text],
  'editorial shelves should retain their intended order'
);

select results_eq(
  $$
    select track_id
    from public.editorial_shelf_tracks
    where shelf_id = 'c1000000-0000-4000-8000-000000000001'
    order by position
  $$,
  array[
    'a3000000-0000-4000-8000-000000000001'::uuid,
    'a3000000-0000-4000-8000-000000000002'::uuid,
    'a3000000-0000-4000-8000-000000000003'::uuid
  ],
  'Pop Mix should retain deterministic track order'
);

select col_is_fk(
  'public',
  'editorial_shelf_tracks',
  'track_id',
  'shelf entries should enforce their track relationship'
);

select * from finish();
rollback;
