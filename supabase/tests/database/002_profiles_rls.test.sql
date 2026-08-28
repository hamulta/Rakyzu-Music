begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(7);

insert into auth.users (
  id,
  instance_id,
  aud,
  role,
  email,
  encrypted_password,
  email_confirmed_at,
  raw_app_meta_data,
  raw_user_meta_data,
  created_at,
  updated_at
)
values
  (
    '11111111-1111-4111-8111-111111111111',
    '00000000-0000-0000-0000-000000000000',
    'authenticated',
    'authenticated',
    'listener-one@rakyzu.test',
    '',
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Listener One"}'::jsonb,
    now(),
    now()
  ),
  (
    '22222222-2222-4222-8222-222222222222',
    '00000000-0000-0000-0000-000000000000',
    'authenticated',
    'authenticated',
    'listener-two@rakyzu.test',
    '',
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Listener Two"}'::jsonb,
    now(),
    now()
  );

select results_eq(
  $$
    select display_name
    from public.profiles
    where id = '11111111-1111-4111-8111-111111111111'
  $$,
  array['Listener One'::text],
  'auth signup trigger should create the listener profile'
);

set local role authenticated;
select set_config(
  'request.jwt.claim.sub',
  '11111111-1111-4111-8111-111111111111',
  true
);
select set_config(
  'request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}',
  true
);

select results_eq(
  $$ select id from public.profiles order by id $$,
  array['11111111-1111-4111-8111-111111111111'::uuid],
  'an authenticated listener should read only their profile'
);

select lives_ok(
  $$
    update public.profiles
    set display_name = 'Listener One Updated'
    where id = '11111111-1111-4111-8111-111111111111'
  $$,
  'a listener should update their display name'
);

select results_eq(
  $$
    update public.profiles
    set display_name = 'Blocked Update'
    where id = '22222222-2222-4222-8222-222222222222'
    returning id
  $$,
  array[]::uuid[],
  'a listener should not update another profile'
);

select throws_ok(
  $$
    insert into public.profiles (id, display_name)
    values ('33333333-3333-4333-8333-333333333333', 'Blocked Insert')
  $$,
  '42501',
  'permission denied for table profiles',
  'clients should not create profile rows directly'
);

select throws_ok(
  $$
    delete from public.profiles
    where id = '11111111-1111-4111-8111-111111111111'
  $$,
  '42501',
  'permission denied for table profiles',
  'clients should not delete profiles directly'
);

reset role;
set local role anon;

select throws_ok(
  $$ select id from public.profiles $$,
  '42501',
  'permission denied for table profiles',
  'anonymous clients should have no profile access'
);

select * from finish();
rollback;
