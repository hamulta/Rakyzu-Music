begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(8);

insert into public.artists (id, name, is_published)
values ('b1000000-0000-4000-8000-000000000001', 'Unpublished Artist', false);

insert into public.albums (id, artist_id, title, is_published)
values (
  'b2000000-0000-4000-8000-000000000001',
  'b1000000-0000-4000-8000-000000000001',
  'Unpublished Album',
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
  'b3000000-0000-4000-8000-000000000001',
  'b2000000-0000-4000-8000-000000000001',
  'Unpublished Track',
  120000,
  1,
  false
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
  $$ select name from public.artists order by name $$,
  array['Rakyzu Sessions'::text],
  'authenticated listeners should read only published artists'
);
select results_eq(
  $$ select title from public.albums order by title $$,
  array['Signal Zero'::text],
  'authenticated listeners should read only published albums'
);
select results_eq(
  $$ select count(*)::integer from public.tracks $$,
  array[3],
  'authenticated listeners should read only published tracks'
);

select throws_ok(
  $$ insert into public.artists (name) values ('Blocked Artist') $$,
  '42501',
  'permission denied for table artists',
  'listeners should not insert catalog metadata'
);
select throws_ok(
  $$ update public.albums set title = 'Blocked Update' $$,
  '42501',
  'permission denied for table albums',
  'listeners should not update catalog metadata'
);
select throws_ok(
  $$ delete from public.tracks $$,
  '42501',
  'permission denied for table tracks',
  'listeners should not delete catalog metadata'
);

reset role;
set local role anon;

select throws_ok(
  $$ select id from public.artists $$,
  '42501',
  'permission denied for table artists',
  'anonymous clients should not read artists'
);
select throws_ok(
  $$ select id from public.tracks $$,
  '42501',
  'permission denied for table tracks',
  'anonymous clients should not read tracks'
);

select * from finish();
rollback;
