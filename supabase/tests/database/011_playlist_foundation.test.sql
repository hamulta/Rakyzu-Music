begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(12);

select has_table('public', 'playlists', 'playlist metadata table should exist');
select has_function(
  'public',
  'get_my_playlists',
  array['integer'],
  'account-scoped playlist listing function should exist'
);
select has_function(
  'public',
  'create_playlist',
  array['uuid', 'text', 'text'],
  'validated playlist creation function should exist'
);
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.playlists'::regclass),
  'playlist metadata should enforce RLS'
);
select ok(
  not has_table_privilege('authenticated', 'public.playlists', 'UPDATE'),
  'authenticated clients should not directly rewrite playlist revision metadata'
);

insert into auth.users (
  id, instance_id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at
)
values
  (
    '77777777-7777-4777-8777-777777777777',
    '00000000-0000-0000-0000-000000000000',
    'authenticated', 'authenticated', 'playlist-one@rakyzu.test', '', now(),
    '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()
  ),
  (
    '88888888-8888-4888-8888-888888888888',
    '00000000-0000-0000-0000-000000000000',
    'authenticated', 'authenticated', 'playlist-two@rakyzu.test', '', now(),
    '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()
  );

set local role authenticated;
select set_config('request.jwt.claim.sub', '77777777-7777-4777-8777-777777777777', true);
select set_config(
  'request.jwt.claims',
  '{"sub":"77777777-7777-4777-8777-777777777777","role":"authenticated"}',
  true
);

select results_eq(
  $$ select name from public.create_playlist(
    '90000000-0000-4000-8000-000000000001', '  Road   Trip  ', 'For the coast'
  ) $$,
  array['Road Trip'::text],
  'playlist names should be normalized before storage'
);
select results_eq(
  $$ select revision from public.get_my_playlists(100) $$,
  array[1::bigint],
  'new playlists should start at snapshot revision one'
);
select is(
  (select count(*)::integer from public.get_my_playlists(100)),
  1,
  'the creator should list their playlist'
);
select throws_ok(
  $$ select * from public.create_playlist(
    '90000000-0000-4000-8000-000000000002', '   ', ''
  ) $$,
  '22023',
  'Invalid playlist metadata',
  'blank playlist names should be rejected'
);

select set_config('request.jwt.claim.sub', '88888888-8888-4888-8888-888888888888', true);
select set_config(
  'request.jwt.claims',
  '{"sub":"88888888-8888-4888-8888-888888888888","role":"authenticated"}',
  true
);
select is(
  (select count(*)::integer from public.get_my_playlists(100)),
  0,
  'another listener should not list the first listener playlist'
);
select is(
  (select count(*)::integer from public.playlists),
  0,
  'direct table reads should remain isolated by owner'
);

reset role;
set local role anon;
select throws_ok(
  $$ select * from public.get_my_playlists(100) $$,
  '42501',
  'permission denied for function get_my_playlists',
  'anonymous clients should not list playlists'
);

select * from finish();
rollback;
