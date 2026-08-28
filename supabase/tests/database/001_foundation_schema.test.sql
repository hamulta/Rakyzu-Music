begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(8);

select has_schema('private', 'private schema should exist');
select has_table('public', 'profiles', 'profiles table should exist');
select has_column('public', 'profiles', 'id', 'profiles.id should exist');
select col_type_is('public', 'profiles', 'id', 'uuid', 'profiles.id should be uuid');
select has_column(
  'public',
  'profiles',
  'display_name',
  'profiles.display_name should exist'
);
select col_not_null(
  'public',
  'profiles',
  'display_name',
  'profiles.display_name should be required'
);
select ok(
  (
    select relrowsecurity and relforcerowsecurity
    from pg_class
    where oid = 'public.profiles'::regclass
  ),
  'profiles should enforce row level security'
);
select ok(
  (
    select count(*) = 2
      and bool_and(
        policyname in ('profiles_select_own', 'profiles_update_own')
      )
    from pg_policies
    where schemaname = 'public' and tablename = 'profiles'
  ),
  'profiles should expose only the intended policies'
);

select * from finish();
rollback;
