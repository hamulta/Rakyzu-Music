begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select no_plan();

insert into auth.users(id, email) values
  ('b6000000-0000-4000-8000-000000000001', 'profile-regression@rakyzu.test');
delete from public.profiles where id = 'b6000000-0000-4000-8000-000000000001';

set local role authenticated;
select set_config('request.jwt.claim.sub', 'b6000000-0000-4000-8000-000000000001', true);
select set_config('request.jwt.claims',
  '{"sub":"b6000000-0000-4000-8000-000000000001","role":"authenticated"}', true);

select is((public.get_my_profile_context()->'verified')::text, 'false',
  'ordinary listener verification is a non-null false Boolean');
select is(public.get_my_profile_context()->>'identityKind', 'listener',
  'ordinary account resolves to listener identity');
select is(public.get_my_profile_context()->>'displayName', 'Rakyzu Listener',
  'missing profile row is repaired deterministically');
select ok(exists(select 1 from public.profiles
  where id = 'b6000000-0000-4000-8000-000000000001'),
  'profile repair persists inside the authentication transaction');

reset role;
select * from finish();
rollback;
