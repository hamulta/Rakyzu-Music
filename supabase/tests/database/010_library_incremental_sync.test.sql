begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(12);

select has_table(
  'public',
  'library_changes',
  'Library change stream should exist'
);
select has_function(
  'public',
  'get_library_sync_anchor',
  array[]::text[],
  'Library anchor function should exist'
);
select has_function(
  'public',
  'get_library_items_page',
  array['integer', 'timestamp with time zone', 'text', 'uuid'],
  'Library page function should exist'
);
select has_function(
  'public',
  'get_library_changes',
  array['bigint', 'integer'],
  'Library changes function should exist'
);

insert into auth.users (
  id, instance_id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at
)
values
  (
    '55555555-5555-4555-8555-555555555555',
    '00000000-0000-0000-0000-000000000000',
    'authenticated', 'authenticated', 'sync-one@rakyzu.test', '', now(),
    '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()
  ),
  (
    '66666666-6666-4666-8666-666666666666',
    '00000000-0000-0000-0000-000000000000',
    'authenticated', 'authenticated', 'sync-two@rakyzu.test', '', now(),
    '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()
  );

insert into public.followed_artists (user_id, artist_id)
values (
  '66666666-6666-4666-8666-666666666666',
  'a1000000-0000-4000-8000-000000000001'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '55555555-5555-4555-8555-555555555555', true);
select set_config(
  'request.jwt.claims',
  '{"sub":"55555555-5555-4555-8555-555555555555","role":"authenticated"}',
  true
);

select results_eq(
  $$ select success from public.set_library_item(
    'track', 'a3000000-0000-4000-8000-000000000001', true
  ) $$,
  array[true],
  'saving a track should succeed'
);
select results_eq(
  $$ select success from public.set_library_item(
    'album', 'a2000000-0000-4000-8000-000000000001', true
  ) $$,
  array[true],
  'saving an album should succeed'
);
select is(
  (select count(*)::integer from public.get_library_items_page(1, null, null, null)),
  1,
  'Library snapshot page size should be applied'
);
select is(
  (select sequence from public.get_library_sync_anchor()),
  (select max(sequence) from public.library_changes),
  'anchor should equal the current listener latest sequence'
);
select is(
  (select count(*)::integer from public.get_library_changes(0, 500)),
  2,
  'change pages should be isolated by listener and bounded by available rows'
);

select results_eq(
  $$ select success from public.set_library_item(
    'track', 'a3000000-0000-4000-8000-000000000001', false
  ) $$,
  array[true],
  'removing a track should succeed'
);
select results_eq(
  $$
    select saved
    from public.get_library_changes(0, 50)
    where kind = 'track'
    order by sequence desc
    limit 1
  $$,
  array[false],
  'incremental changes should preserve deletion tombstones'
);

reset role;
set local role anon;
select throws_ok(
  $$ select * from public.get_library_changes(0, 50) $$,
  '42501',
  'permission denied for function get_library_changes',
  'anonymous clients should not read Library changes'
);

select * from finish();
rollback;
