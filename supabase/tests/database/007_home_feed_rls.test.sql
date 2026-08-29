begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(8);

insert into public.editorial_shelves (id, title, position, is_published)
values (
  'd1000000-0000-4000-8000-000000000001',
  'Unpublished Shelf',
  90,
  false
);

insert into public.editorial_shelf_tracks (shelf_id, track_id, position)
values (
  'd1000000-0000-4000-8000-000000000001',
  'a3000000-0000-4000-8000-000000000001',
  0
);

set local role authenticated;
select set_config(
  'request.jwt.claim.sub',
  '55555555-5555-4555-8555-555555555555',
  true
);
select set_config(
  'request.jwt.claims',
  '{"sub":"55555555-5555-4555-8555-555555555555","role":"authenticated"}',
  true
);

select results_eq(
  $$ select title from public.editorial_shelves order by position $$,
  array['Rakyzu Essentials'::text, 'After Dark'::text],
  'authenticated listeners should read only published editorial shelves'
);
select results_eq(
  $$ select count(*)::integer from public.editorial_shelf_tracks $$,
  array[6],
  'authenticated listeners should read entries only from published shelves and tracks'
);

select throws_ok(
  $$ insert into public.editorial_shelves (title, position) values ('Blocked Shelf', 91) $$,
  '42501',
  'permission denied for table editorial_shelves',
  'listeners should not insert editorial shelves'
);
select throws_ok(
  $$ update public.editorial_shelves set title = 'Blocked Update' $$,
  '42501',
  'permission denied for table editorial_shelves',
  'listeners should not update editorial shelves'
);
select throws_ok(
  $$ delete from public.editorial_shelf_tracks $$,
  '42501',
  'permission denied for table editorial_shelf_tracks',
  'listeners should not delete editorial shelf entries'
);

reset role;
set local role anon;

select throws_ok(
  $$ select id from public.editorial_shelves $$,
  '42501',
  'permission denied for table editorial_shelves',
  'anonymous clients should not read editorial shelves'
);
select throws_ok(
  $$ select shelf_id from public.editorial_shelf_tracks $$,
  '42501',
  'permission denied for table editorial_shelf_tracks',
  'anonymous clients should not read editorial shelf entries'
);
select throws_ok(
  $$ insert into public.editorial_shelf_tracks (shelf_id, track_id, position) values (
    'c1000000-0000-4000-8000-000000000001',
    'a3000000-0000-4000-8000-000000000001',
    99
  ) $$,
  '42501',
  'permission denied for table editorial_shelf_tracks',
  'anonymous clients should not add editorial shelf entries'
);

select * from finish();
rollback;
