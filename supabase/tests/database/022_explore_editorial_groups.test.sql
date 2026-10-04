begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select no_plan();

select has_table('public', 'editorial_shelf_opens',
  'privacy-preserving editorial engagement receipts exist');
select ok((select relrowsecurity and relforcerowsecurity from pg_class
  where oid = 'public.editorial_shelf_opens'::regclass),
  'editorial engagement receipts force RLS');
select ok(not has_table_privilege(
  'authenticated', 'public.editorial_shelf_opens', 'SELECT,INSERT,UPDATE,DELETE'
), 'Android cannot inspect or forge editorial engagement receipts');
select has_function('public', 'record_editorial_shelf_open', array['uuid'],
  'authenticated editorial open RPC exists');
select has_function(
  'public', 'admin_upsert_editorial_shelf',
  array['uuid','text','text','integer','uuid','boolean','text','text'],
  'full Card Group editor RPC exists');
select is((select count(*)::integer from public.editorial_shelves
  where position between 200 and 207), 4,
  'Your Top Genres starts with four real server Card Groups');
select is((select count(*)::integer from public.editorial_shelves
  where position between 300 and 311), 6,
  'Browse All starts with six real server Card Groups');
select ok(not exists(
  select 1 from public.editorial_shelves shelf
  where shelf.position between 200 and 311
    and not exists(select 1 from public.editorial_shelf_tracks item where item.shelf_id = shelf.id)
), 'every initial Explore Card Group has real catalog membership');

insert into auth.users(id, email) values
  ('b9000000-0000-4000-8000-000000000001', 'explore-listener@rakyzu.test'),
  ('b9000000-0000-4000-8000-000000000002', 'explore-ceo@rakyzu.test');
insert into public.staff_assignments(user_id, role, active) values
  ('b9000000-0000-4000-8000-000000000002', 'ceo', true);

set local role authenticated;
select set_config('request.jwt.claim.sub', 'b9000000-0000-4000-8000-000000000001', true);
select set_config('request.jwt.claims',
  '{"sub":"b9000000-0000-4000-8000-000000000001","role":"authenticated"}', true);
select ok(public.record_editorial_shelf_open(
  'c2000000-0000-4000-8000-000000000001'
), 'listener can record an open for a published Card Group');
select ok(public.record_editorial_shelf_open(
  'c2000000-0000-4000-8000-000000000001'
), 'duplicate same-day open remains an idempotent success');
select is((select global_score from public.editorial_shelves
  where id = 'c2000000-0000-4000-8000-000000000001'), 1::bigint,
  'global Smart Recommendation score counts one listener once per day');
select throws_ok(
  $$select public.admin_upsert_editorial_shelf(
    null, 'Denied', null, 204, null, true, 'Denied', '#123456'
  )$$,
  '42501', 'Editorial management is not permitted',
  'ordinary listener cannot create a global Card Group');

select set_config('request.jwt.claim.sub', 'b9000000-0000-4000-8000-000000000002', true);
select set_config('request.jwt.claims',
  '{"sub":"b9000000-0000-4000-8000-000000000002","role":"authenticated"}', true);
select is(public.admin_upsert_editorial_shelf(
  'b9000000-0000-4000-8000-000000000010', 'Electronic', 'Global electronic selection',
  204, 'a3000000-0000-4000-8000-000000000001', true, 'Electronic', '#123ABC'
)->>'colorHex', '#123ABC',
  'authorized editor creates a positioned, colored Explore Card Group');
select is(public.admin_replace_editorial_tracks(
  'b9000000-0000-4000-8000-000000000010',
  array[
    'a3000000-0000-4000-8000-000000000001'::uuid,
    'a3000000-0000-4000-8000-000000000002'::uuid
  ]
)->>'trackCount', '2', 'authorized editor saves real ordered group membership');
select throws_ok(
  $$select public.admin_upsert_editorial_shelf(
    null, 'Outside category', null, 208, null, true, null, '#123456'
  )$$,
  '22023', 'Editorial Card Group is invalid',
  'unsupported category position fails closed');
select is(public.admin_delete_editorial_shelf(
  'b9000000-0000-4000-8000-000000000010'
)->>'deleted', 'true', 'authorized editor can delete the Explore Card Group');

reset role;
select * from finish();
rollback;
