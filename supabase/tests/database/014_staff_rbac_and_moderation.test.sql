begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select no_plan();

select has_table('public', 'staff_roles', 'staff role catalog exists');
select has_table('public', 'staff_assignments', 'staff assignments exist');
select has_table('public', 'moderation_cases', 'moderation queue exists');
select has_table('public', 'moderation_actions', 'moderation action history exists');
select has_table('public', 'staff_audit_log', 'privileged audit log exists');
select has_table('public', 'track_media_variants', 'R2 media inventory exists');
select is((select count(*)::integer from public.staff_roles), 5, 'five organization roles are fixed');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class
    where oid = 'public.staff_assignments'::regclass),
  'staff assignments force RLS'
);
select ok(
  not has_table_privilege('authenticated', 'public.staff_assignments', 'INSERT,UPDATE,DELETE'),
  'authenticated clients cannot directly change staff roles'
);
select ok(
  not has_table_privilege('authenticated', 'public.moderation_cases', 'INSERT,UPDATE,DELETE'),
  'authenticated clients cannot directly mutate moderation cases'
);
select ok(
  not has_table_privilege('authenticated', 'public.staff_audit_log', 'SELECT,INSERT,UPDATE,DELETE'),
  'audit evidence remains server-only until the bounded audit explorer milestone'
);
select has_function('public', 'get_my_staff_context', array[]::text[], 'staff context RPC exists');
select has_function(
  'public', 'admin_assign_staff', array['uuid', 'text', 'boolean'],
  'hierarchy-safe staff assignment RPC exists'
);
select has_function(
  'public', 'admin_assign_staff_by_email', array['text', 'text', 'boolean'],
  'staff can be assigned without a database-only UUID workflow'
);
select has_function(
  'public', 'admin_publish_album', array['uuid'],
  'atomic catalog publication RPC exists'
);

insert into auth.users(id, email) values
  ('b1000000-0000-4000-8000-000000000001', 'ceo-rbac@rakyzu.test'),
  ('b1000000-0000-4000-8000-000000000002', 'manager-rbac@rakyzu.test'),
  ('b1000000-0000-4000-8000-000000000003', 'officer-rbac@rakyzu.test'),
  ('b1000000-0000-4000-8000-000000000004', 'listener-rbac@rakyzu.test');

insert into public.staff_assignments(user_id, role, assigned_by) values
  ('b1000000-0000-4000-8000-000000000001', 'ceo', 'b1000000-0000-4000-8000-000000000001'),
  ('b1000000-0000-4000-8000-000000000002', 'manager', 'b1000000-0000-4000-8000-000000000001'),
  ('b1000000-0000-4000-8000-000000000003', 'officer', 'b1000000-0000-4000-8000-000000000001');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'b1000000-0000-4000-8000-000000000004', true);
select set_config('request.jwt.claims',
  '{"sub":"b1000000-0000-4000-8000-000000000004","role":"authenticated"}', true);
select is(public.get_my_staff_context()->>'isStaff', 'false', 'a listener has no Admin Panel role');
select throws_ok(
  $$select public.admin_create_artist('Forbidden Artist')$$,
  '42501', 'Catalog drafting is not permitted', 'listeners cannot create artist profiles'
);

select set_config('request.jwt.claim.sub', 'b1000000-0000-4000-8000-000000000003', true);
select set_config('request.jwt.claims',
  '{"sub":"b1000000-0000-4000-8000-000000000003","role":"authenticated"}', true);
select is(public.get_my_staff_context()->>'displayRole', 'Officer', 'officer context is resolved server-side');
select is(
  public.admin_create_moderation_case(
    'playlist', 'a4000000-0000-4000-8000-000000000001', 'Potential policy violation', 50::smallint
  )->>'status',
  'open', 'officers can open a bounded moderation case'
);
select is(jsonb_array_length(public.admin_list_moderation_cases(50)), 1, 'officer can read the active queue');
select throws_ok(
  $$select public.admin_moderate_case(
    (select id from public.moderation_cases limit 1), 'approve', 'Confirmed violation'
  )$$,
  '42501', 'Moderation decisions are not permitted', 'officers cannot make final decisions'
);
select throws_ok(
  $$select public.admin_create_artist('Officer Artist')$$,
  '42501', 'Catalog drafting is not permitted', 'officers cannot draft catalog content'
);

select set_config('request.jwt.claim.sub', 'b1000000-0000-4000-8000-000000000002', true);
select set_config('request.jwt.claims',
  '{"sub":"b1000000-0000-4000-8000-000000000002","role":"authenticated"}', true);
select is(
  (select count(*)::integer from public.staff_assignments),
  2, 'managers cannot read assignments at or above their own rank'
);
select is(
  public.admin_moderate_case(
    (select id from public.moderation_cases limit 1), 'approve', 'Confirmed violation'
  )->>'status',
  'actioned', 'managers can resolve moderation cases'
);
select matches(
  public.admin_create_artist('Rakyzu Test Artist')->>'id',
  '^[0-9a-f-]{36}$', 'managers can create unpublished artist drafts'
);
select throws_ok(
  $$select public.admin_publish_album('a2000000-0000-4000-8000-000000000001')$$,
  '42501', 'Catalog publishing is not permitted', 'managers cannot publish catalog releases'
);
select throws_ok(
  $$select public.admin_assign_staff(
    'b1000000-0000-4000-8000-000000000004', 'c_level_executive', true
  )$$,
  '42501', 'Only a higher office can manage this role',
  'managers cannot grant a role at or above their rank'
);

select set_config('request.jwt.claim.sub', 'b1000000-0000-4000-8000-000000000001', true);
select set_config('request.jwt.claims',
  '{"sub":"b1000000-0000-4000-8000-000000000001","role":"authenticated"}', true);
select is(public.get_my_staff_context()->>'fullAccess', 'true', 'CEO context has explicit full access');
select is(
  public.admin_assign_staff_by_email(
    'listener-rbac@rakyzu.test', 'c_level_executive', true
  )->>'role',
  'c_level_executive', 'CEO can appoint a C-Level Executive by exact account email'
);
select throws_ok(
  $$select public.admin_assign_staff(
    'b1000000-0000-4000-8000-000000000001', 'ceo', false
  )$$,
  '23514', 'The final active CEO cannot be disabled',
  'the final active CEO cannot remove the control boundary'
);
select throws_ok(
  $$select public.admin_assign_staff(
    'b1000000-0000-4000-8000-000000000001', 'manager', true
  )$$,
  '23514', 'The final active CEO cannot be disabled',
  'the final active CEO cannot demote itself'
);

select set_config('request.jwt.claim.sub', 'b1000000-0000-4000-8000-000000000004', true);
select set_config('request.jwt.claims',
  '{"sub":"b1000000-0000-4000-8000-000000000004","role":"authenticated"}', true);
select matches(
  public.admin_create_album(
    (select id from public.artists where name = 'Rakyzu Test Artist'),
    'RBAC Test Album', '2026-09-15'
  )->>'id', '^[0-9a-f-]{36}$', 'C-Level can create an album draft'
);
select set_config('test.album_id',
  (select id::text from public.albums where title = 'RBAC Test Album'), false);
select matches(
  public.admin_create_track(
    current_setting('test.album_id')::uuid, 'Authorized Track', 180000, 1, 1, false
  )->>'id', '^[0-9a-f-]{36}$', 'C-Level can create a track draft'
);
select set_config('test.track_id',
  (select id::text from public.tracks where title = 'Authorized Track'), false);
select is(
  public.admin_record_track_media(
    current_setting('test.track_id')::uuid,
    'standard',
    'media/tracks/' || current_setting('test.track_id') || '/source.mp3',
    4096,
    'test-etag'
  )->>'quality',
  'standard', 'C-Level can register an authorized Worker audio upload'
);
select is(
  public.admin_publish_album(current_setting('test.album_id')::uuid)->>'published',
  'true', 'C-Level can publish an album only after standard media exists'
);

reset role;
select is(
  (select count(*)::integer from public.staff_audit_log where actor_id is not null),
  8, 'privileged operations produce append-only audit evidence'
);
select * from finish();
rollback;
