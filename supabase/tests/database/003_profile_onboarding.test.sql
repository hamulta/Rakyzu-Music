begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(6);

select has_column(
  'public',
  'profiles',
  'onboarding_completed',
  'profiles.onboarding_completed should exist'
);
select col_type_is(
  'public',
  'profiles',
  'onboarding_completed',
  'boolean',
  'profiles.onboarding_completed should be boolean'
);
select col_not_null(
  'public',
  'profiles',
  'onboarding_completed',
  'profiles.onboarding_completed should be required'
);

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
    '33333333-3333-4333-8333-333333333333',
    '00000000-0000-0000-0000-000000000000',
    'authenticated',
    'authenticated',
    'onboarding-one@rakyzu.test',
    '',
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{}'::jsonb,
    now(),
    now()
  ),
  (
    '44444444-4444-4444-8444-444444444444',
    '00000000-0000-0000-0000-000000000000',
    'authenticated',
    'authenticated',
    'onboarding-two@rakyzu.test',
    '',
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{}'::jsonb,
    now(),
    now()
  );

select results_eq(
  $$
    select onboarding_completed
    from public.profiles
    where id = '33333333-3333-4333-8333-333333333333'
  $$,
  array[false],
  'new profiles should require onboarding'
);

set local role authenticated;
select set_config(
  'request.jwt.claim.sub',
  '33333333-3333-4333-8333-333333333333',
  true
);
select set_config(
  'request.jwt.claims',
  '{"sub":"33333333-3333-4333-8333-333333333333","role":"authenticated"}',
  true
);

select results_eq(
  $$
    update public.profiles
    set onboarding_completed = true
    where id = '33333333-3333-4333-8333-333333333333'
    returning onboarding_completed
  $$,
  array[true],
  'a listener should complete onboarding on their profile'
);

select results_eq(
  $$
    update public.profiles
    set onboarding_completed = true
    where id = '44444444-4444-4444-8444-444444444444'
    returning id
  $$,
  array[]::uuid[],
  'a listener should not complete onboarding for another profile'
);

select * from finish();
rollback;
