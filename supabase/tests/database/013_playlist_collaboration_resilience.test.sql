begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select no_plan();

select has_table('public', 'playlist_members', 'playlist collaborators exist');
select has_table('public', 'playlist_invites', 'expiring playlist invites exist');
select has_table('public', 'playlist_follows', 'public playlist follows exist');
select has_table('public', 'playlist_mutation_receipts', 'idempotency receipts exist');
select has_column('public', 'playlists', 'visibility', 'playlist visibility is explicit');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class
    where oid = 'public.playlist_members'::regclass),
  'collaborator membership forces RLS'
);
select ok(
  not has_table_privilege('authenticated', 'public.playlist_invites', 'SELECT,INSERT,UPDATE,DELETE'),
  'invite capabilities cannot be listed or mutated directly'
);
select has_function(
  'public', 'get_playlist_detail_page', array['uuid', 'integer', 'integer'],
  'bounded playlist page function exists'
);
select has_function(
  'public', 'mutate_playlist_v2',
  array['uuid','uuid','bigint','text','uuid','uuid[]','text','text'],
  'idempotent mutation function exists'
);

insert into auth.users(id, email) values
  ('71000000-0000-4000-8000-000000000001', 'owner-collab@rakyzu.test'),
  ('71000000-0000-4000-8000-000000000002', 'editor-collab@rakyzu.test'),
  ('71000000-0000-4000-8000-000000000003', 'viewer-collab@rakyzu.test');

set local role authenticated;
select set_config('request.jwt.claim.sub', '71000000-0000-4000-8000-000000000001', true);
select set_config(
  'request.jwt.claims',
  '{"sub":"71000000-0000-4000-8000-000000000001","role":"authenticated"}', true
);
select * from public.create_playlist(
  '91000000-0000-4000-8000-000000000001', 'Shared Mix', 'Collaboration test'
);
select set_config(
  'test.editor_invite',
  public.create_playlist_invite(
    '91000000-0000-4000-8000-000000000001', 'editor', 168
  )->>'token',
  false
);
select is(
  public.create_playlist_invite(
    '91000000-0000-4000-8000-000000000001', 'viewer', 1
  )->>'role',
  'Viewer',
  'owner can choose a read-only invite role'
);
select throws_ok(
  $$select public.create_playlist_invite(
    '91000000-0000-4000-8000-000000000001', 'owner', 168
  )$$,
  '22023', 'Invalid invite', 'owner role cannot be delegated by invite'
);

select set_config('request.jwt.claim.sub', '71000000-0000-4000-8000-000000000002', true);
select set_config(
  'request.jwt.claims',
  '{"sub":"71000000-0000-4000-8000-000000000002","role":"authenticated"}', true
);
select is(
  public.accept_playlist_invite(current_setting('test.editor_invite')::uuid)
    #>> '{playlist,accessRole}',
  'Editor',
  'an invite grants the selected editor role once'
);
select is(
  jsonb_array_length(public.get_playlist_detail_page(
    '91000000-0000-4000-8000-000000000001', 0, 100
  )->'members'),
  2,
  'detail exposes owner and collaborator display names'
);
select is(
  public.mutate_playlist_v2(
    '91000000-0000-4000-8000-000000000001',
    '92000000-0000-4000-8000-000000000001',
    2,
    'add',
    'a3000000-0000-4000-8000-000000000001'
  ) #>> '{playlist,revision}',
  '3',
  'editor can add a published song'
);
select is(
  public.mutate_playlist_v2(
    '91000000-0000-4000-8000-000000000001',
    '92000000-0000-4000-8000-000000000001',
    2,
    'add',
    'a3000000-0000-4000-8000-000000000001'
  ) #>> '{playlist,revision}',
  '3',
  'replaying an operation id returns its original receipt'
);
select throws_ok(
  $$select public.mutate_playlist_v2(
    '91000000-0000-4000-8000-000000000001',
    '92000000-0000-4000-8000-000000000002',
    3, 'metadata', playlist_name => 'Taken over', playlist_description => ''
  )$$,
  '42501', 'Only the owner can edit playlist details',
  'editor cannot take over owner-only metadata'
);

select set_config('request.jwt.claim.sub', '71000000-0000-4000-8000-000000000001', true);
select set_config(
  'request.jwt.claims',
  '{"sub":"71000000-0000-4000-8000-000000000001","role":"authenticated"}', true
);
select is(
  jsonb_array_length(public.remove_playlist_member(
    '91000000-0000-4000-8000-000000000001',
    '71000000-0000-4000-8000-000000000002'
  )->'members'),
  1,
  'owner can revoke a collaborator'
);
select is(
  public.set_playlist_visibility(
    '91000000-0000-4000-8000-000000000001', 4, 'public'
  ) #>> '{playlist,visibility}',
  'Public',
  'owner can publish a private playlist'
);

select set_config('request.jwt.claim.sub', '71000000-0000-4000-8000-000000000002', true);
select set_config(
  'request.jwt.claims',
  '{"sub":"71000000-0000-4000-8000-000000000002","role":"authenticated"}', true
);
select is(
  public.get_playlist_detail_page(
    '91000000-0000-4000-8000-000000000001', 0, 1
  ) #>> '{playlist,accessRole}',
  'Follower',
  'authenticated listeners can safely view a public playlist'
);
select is(
  public.set_playlist_following(
    '91000000-0000-4000-8000-000000000001', true
  ) #>> '{playlist,isFollowing}',
  'true',
  'a public listener can follow the playlist'
);
select results_eq(
  $$select name from public.get_accessible_playlists(30, null, null)$$,
  array['Shared Mix'::text],
  'followed public playlists enter the account-scoped list'
);
select is(
  public.get_playlist_artwork_access(
    '91000000-0000-4000-8000-000000000001'
  ) #>> '{canEdit}',
  'false',
  'public followers cannot change owner artwork'
);
select throws_ok(
  $$select public.mutate_playlist_v2(
    '91000000-0000-4000-8000-000000000001',
    '92000000-0000-4000-8000-000000000003',
    5, 'remove', 'a3000000-0000-4000-8000-000000000001'
  )$$,
  '42501', 'Playlist unavailable', 'followers cannot mutate songs'
);
select throws_ok(
  $$select * from public.get_accessible_playlists(
    30, now(), null
  )$$,
  '22023', 'Incomplete playlist cursor', 'partial cursors are rejected'
);

select set_config('request.jwt.claim.sub', '71000000-0000-4000-8000-000000000001', true);
select set_config(
  'request.jwt.claims',
  '{"sub":"71000000-0000-4000-8000-000000000001","role":"authenticated"}', true
);
select set_config(
  'test.viewer_invite',
  public.create_playlist_invite(
    '91000000-0000-4000-8000-000000000001', 'viewer', 168
  )->>'token',
  false
);
select set_config('request.jwt.claim.sub', '71000000-0000-4000-8000-000000000003', true);
select set_config(
  'request.jwt.claims',
  '{"sub":"71000000-0000-4000-8000-000000000003","role":"authenticated"}', true
);
select is(
  public.accept_playlist_invite(current_setting('test.viewer_invite')::uuid)
    #>> '{playlist,accessRole}',
  'Viewer',
  'viewer invite grants read-only membership'
);
select throws_ok(
  $$select public.mutate_playlist_v2(
    '91000000-0000-4000-8000-000000000001',
    '92000000-0000-4000-8000-000000000004',
    6, 'remove', 'a3000000-0000-4000-8000-000000000001'
  )$$,
  '42501', 'Playlist unavailable', 'viewer cannot mutate songs'
);
select lives_ok(
  $$select public.leave_playlist('91000000-0000-4000-8000-000000000001')$$,
  'non-owner member can leave'
);

reset role;
select is(
  (select count(*)::integer from public.playlist_mutation_receipts
    where operation_id = '92000000-0000-4000-8000-000000000001'),
  1,
  'exactly one durable receipt is stored for a replayed operation'
);
select * from finish();
rollback;
