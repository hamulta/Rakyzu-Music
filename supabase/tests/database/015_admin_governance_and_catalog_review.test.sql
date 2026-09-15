begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select no_plan();

select has_table('public', 'content_enforcement_events', 'reversible enforcement ledger exists');
select has_table('public', 'catalog_team_memberships', 'scoped catalog teams exist');
select has_table('public', 'album_artwork_assets', 'private artwork inventory exists');
select has_table('public', 'catalog_review_items', 'catalog review queue exists');
select has_table('public', 'scheduled_releases', 'scheduled release boundary exists');
select has_table('public', 'audit_retention_policy', 'audit retention intent exists');
select ok(
  (select bool_and(relrowsecurity and relforcerowsecurity) from pg_class where oid in (
    'public.content_enforcement_events'::regclass,
    'public.catalog_team_memberships'::regclass,
    'public.album_artwork_assets'::regclass,
    'public.catalog_review_items'::regclass,
    'public.scheduled_releases'::regclass,
    'public.audit_retention_policy'::regclass
  )), 'every governance table forces RLS'
);
select ok(
  not has_table_privilege('authenticated', 'public.content_enforcement_events', 'INSERT,UPDATE,DELETE'),
  'clients cannot directly mutate enforcement evidence'
);
select ok(
  not has_table_privilege('authenticated', 'public.catalog_review_items', 'INSERT,UPDATE,DELETE'),
  'clients cannot bypass catalog review RPCs'
);
select ok(
  not has_table_privilege('authenticated', 'public.staff_audit_log', 'SELECT'),
  'raw audit table remains unavailable to clients'
);
select has_function(
  'public', 'admin_apply_content_enforcement', array['text','uuid','text','text','uuid'],
  'reasoned enforcement RPC exists'
);
select has_function(
  'public', 'admin_assign_catalog_team_by_email', array['text','uuid','text','text','boolean'],
  'exact-email scoped team RPC exists'
);
select has_function(
  'public', 'admin_create_catalog_label', array['text'], 'catalog label creation RPC exists'
);
select has_function(
  'public', 'admin_link_catalog_label_artist', array['uuid','uuid'],
  'label-to-artist scope link RPC exists'
);
select has_function(
  'public', 'admin_schedule_album', array['uuid','timestamp with time zone'],
  'scheduled publication RPC exists'
);
select has_function(
  'public', 'admin_export_audit', array['text','text','timestamp with time zone','integer'],
  'bounded audit export RPC exists'
);

insert into auth.users(id, email) values
  ('b2000000-0000-4000-8000-000000000001', 'ceo-governance@rakyzu.test'),
  ('b2000000-0000-4000-8000-000000000002', 'executive-governance@rakyzu.test'),
  ('b2000000-0000-4000-8000-000000000003', 'manager-governance@rakyzu.test'),
  ('b2000000-0000-4000-8000-000000000004', 'supervisor-governance@rakyzu.test'),
  ('b2000000-0000-4000-8000-000000000005', 'member-governance@rakyzu.test'),
  ('b2000000-0000-4000-8000-000000000006', 'listener-governance@rakyzu.test');
insert into public.staff_assignments(user_id, role, assigned_by) values
  ('b2000000-0000-4000-8000-000000000001', 'ceo', 'b2000000-0000-4000-8000-000000000001'),
  ('b2000000-0000-4000-8000-000000000002', 'c_level_executive', 'b2000000-0000-4000-8000-000000000001'),
  ('b2000000-0000-4000-8000-000000000003', 'manager', 'b2000000-0000-4000-8000-000000000001'),
  ('b2000000-0000-4000-8000-000000000004', 'supervisor', 'b2000000-0000-4000-8000-000000000001');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'b2000000-0000-4000-8000-000000000006', true);
select set_config('request.jwt.claims',
  '{"sub":"b2000000-0000-4000-8000-000000000006","role":"authenticated"}', true);
select throws_ok(
  $$select public.admin_apply_content_enforcement(
    'track', 'a3000000-0000-4000-8000-000000000001', 'take_down', 'Listener request', null
  )$$,
  '42501', 'Content enforcement is not permitted', 'listeners cannot enforce content'
);

select set_config('request.jwt.claim.sub', 'b2000000-0000-4000-8000-000000000004', true);
select set_config('request.jwt.claims',
  '{"sub":"b2000000-0000-4000-8000-000000000004","role":"authenticated"}', true);
select is(
  public.admin_apply_content_enforcement(
    'track', 'a3000000-0000-4000-8000-000000000001', 'quarantine',
    'Rights review pending', null
  )->>'action', 'quarantine', 'Supervisor can quarantine with a reason'
);
select is(
  (select count(*)::integer from public.tracks
    where id = 'a3000000-0000-4000-8000-000000000001'),
  0, 'quarantined track disappears through listener catalog policy'
);
select is(
  public.admin_apply_content_enforcement(
    'track', 'a3000000-0000-4000-8000-000000000001', 'restore',
    'Rights review cleared', null
  )->>'action', 'restore', 'Supervisor can restore without deleting evidence'
);
select is(
  (select count(*)::integer from public.tracks
    where id = 'a3000000-0000-4000-8000-000000000001'),
  1, 'restored track is visible again'
);
select is(
  public.admin_apply_content_enforcement(
    'artist', 'a1000000-0000-4000-8000-000000000001', 'take_down',
    'Artist rights suspended', null
  )->>'action', 'take_down', 'artist takedown is recorded'
);
select is((select count(*)::integer from public.albums
  where artist_id = 'a1000000-0000-4000-8000-000000000001'), 0,
  'artist takedown closes descendant albums through RLS');
select is((select count(*)::integer from public.tracks
  where album_id = 'a2000000-0000-4000-8000-000000000001'), 0,
  'artist takedown closes descendant tracks through RLS');
select is(
  public.admin_apply_content_enforcement(
    'artist', 'a1000000-0000-4000-8000-000000000001', 'restore',
    'Artist rights restored', null
  )->>'action', 'restore', 'artist restore reopens descendants without deleting evidence'
);

select set_config('request.jwt.claim.sub', 'b2000000-0000-4000-8000-000000000003', true);
select set_config('request.jwt.claims',
  '{"sub":"b2000000-0000-4000-8000-000000000003","role":"authenticated"}', true);
select matches(public.admin_create_catalog_label('Rakyzu Test Label')->>'id',
  '^[0-9a-f-]{36}$', 'Manager can create a label scope');
select set_config('test.label_id',
  (select id::text from public.catalog_labels where name = 'Rakyzu Test Label'), false);
select is(
  public.admin_link_catalog_label_artist(
    current_setting('test.label_id')::uuid, 'a1000000-0000-4000-8000-000000000001'
  )->>'artistId', 'a1000000-0000-4000-8000-000000000001',
  'Manager can link an artist to a label scope'
);
select is(
  public.admin_assign_catalog_team_by_email(
    'artist', 'a1000000-0000-4000-8000-000000000001',
    'member-governance@rakyzu.test', 'editor', true
  )->>'accessLevel', 'editor', 'Manager assigns an artist-scoped editor by exact email'
);
select throws_ok(
  $$select public.admin_export_audit(null, null, null, 200)$$,
  '42501', 'Audit export is not permitted', 'Manager can view summary but cannot export evidence'
);

select set_config('request.jwt.claim.sub', 'b2000000-0000-4000-8000-000000000005', true);
select set_config('request.jwt.claims',
  '{"sub":"b2000000-0000-4000-8000-000000000005","role":"authenticated"}', true);
select is(
  (select access_level from public.catalog_team_memberships
    where scope_type = 'artist' and scope_id = 'a1000000-0000-4000-8000-000000000001'),
  'editor', 'team member reads only their scoped membership'
);
select is((select count(*)::integer from public.staff_assignments), 0,
  'catalog membership does not widen organization staff access');

reset role;
insert into public.artists(id, name, created_by) values
  ('d1000000-0000-4000-8000-000000000101', 'Governance Artist', 'b2000000-0000-4000-8000-000000000003');
insert into public.albums(id, artist_id, title, created_by) values
  ('d2000000-0000-4000-8000-000000000101', 'd1000000-0000-4000-8000-000000000101',
   'Governance Album', 'b2000000-0000-4000-8000-000000000003');
insert into public.tracks(id, album_id, title, duration_ms, track_number, created_by) values
  ('d3000000-0000-4000-8000-000000000101', 'd2000000-0000-4000-8000-000000000101',
   'Governance Track', 180000, 1, 'b2000000-0000-4000-8000-000000000003');
insert into public.track_media_variants(
  track_id, quality, object_key, size_bytes, etag, uploaded_by
) values (
  'd3000000-0000-4000-8000-000000000101', 'standard',
  'media/tracks/d3000000-0000-4000-8000-000000000101/source.mp3',
  4096, 'audio-etag', 'b2000000-0000-4000-8000-000000000002'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', 'b2000000-0000-4000-8000-000000000002', true);
select set_config('request.jwt.claims',
  '{"sub":"b2000000-0000-4000-8000-000000000002","role":"authenticated"}', true);
select is(
  public.admin_record_album_artwork(
    'd2000000-0000-4000-8000-000000000101',
    'media/albums/d2000000-0000-4000-8000-000000000101/artwork.webp',
    4096, 'image/webp', 'artwork-etag'
  )->>'albumId', 'd2000000-0000-4000-8000-000000000101',
  'C-Level registers an exact private artwork object'
);
select matches(
  public.admin_submit_catalog_review(
    'artwork', 'album', 'd2000000-0000-4000-8000-000000000101', 'Artwork complete'
  )->>'id', '^[0-9a-f-]{36}$', 'artwork can enter review after upload'
);
select matches(
  public.admin_submit_catalog_review(
    'release', 'album', 'd2000000-0000-4000-8000-000000000101', 'Metadata complete'
  )->>'id', '^[0-9a-f-]{36}$', 'release metadata can enter review'
);
select throws_ok(
  $$select public.admin_decide_catalog_review(
    (select id from public.catalog_review_items where review_type = 'artwork' and status = 'pending'),
    'approved', 'Self approval attempt'
  )$$,
  '42501', 'A different authorized reviewer is required', 'submitter cannot approve their own work'
);

select set_config('request.jwt.claim.sub', 'b2000000-0000-4000-8000-000000000001', true);
select set_config('request.jwt.claims',
  '{"sub":"b2000000-0000-4000-8000-000000000001","role":"authenticated"}', true);
select is(
  public.admin_decide_catalog_review(
    (select id from public.catalog_review_items where review_type = 'artwork' and status = 'pending'),
    'approved', 'Artwork policy passed'
  )->>'status', 'approved', 'independent CEO approves artwork'
);
select is(
  public.admin_decide_catalog_review(
    (select id from public.catalog_review_items where review_type = 'release' and status = 'pending'),
    'approved', 'Release policy passed'
  )->>'status', 'approved', 'independent CEO approves release metadata'
);

select set_config('request.jwt.claim.sub', 'b2000000-0000-4000-8000-000000000002', true);
select set_config('request.jwt.claims',
  '{"sub":"b2000000-0000-4000-8000-000000000002","role":"authenticated"}', true);
select is(
  public.admin_record_album_artwork(
    'd2000000-0000-4000-8000-000000000101',
    'media/albums/d2000000-0000-4000-8000-000000000101/artwork.webp',
    8192, 'image/webp', 'replacement-artwork-etag'
  )->>'sizeBytes', '8192', 'replacement artwork is registered'
);
select is(
  (select status from public.catalog_review_items
    where review_type = 'artwork' and decided_by is not null order by decided_at desc limit 1),
  'superseded', 'replacing artwork invalidates its previous approval'
);
select throws_ok(
  $$select public.admin_schedule_album(
    'd2000000-0000-4000-8000-000000000101', now() + interval '1 day'
  )$$,
  '23514', 'Approved artwork, release review, and standard audio are required',
  'album cannot publish using an approval for replaced artwork'
);
select matches(
  public.admin_submit_catalog_review(
    'artwork', 'album', 'd2000000-0000-4000-8000-000000000101', 'Replacement artwork complete'
  )->>'id', '^[0-9a-f-]{36}$', 'replacement artwork enters a fresh review'
);

select set_config('request.jwt.claim.sub', 'b2000000-0000-4000-8000-000000000001', true);
select set_config('request.jwt.claims',
  '{"sub":"b2000000-0000-4000-8000-000000000001","role":"authenticated"}', true);
select is(
  public.admin_decide_catalog_review(
    (select id from public.catalog_review_items where review_type = 'artwork' and status = 'pending'),
    'approved', 'Replacement artwork passed'
  )->>'status', 'approved', 'independent reviewer approves replacement artwork'
);
select is(
  public.admin_schedule_album(
    'd2000000-0000-4000-8000-000000000101', now() + interval '1 day'
  )->>'status', 'scheduled', 'approved release can be scheduled'
);

select set_config('request.jwt.claim.sub', 'b2000000-0000-4000-8000-000000000006', true);
select set_config('request.jwt.claims',
  '{"sub":"b2000000-0000-4000-8000-000000000006","role":"authenticated"}', true);
select is((select count(*)::integer from public.albums
  where id = 'd2000000-0000-4000-8000-000000000101'), 0,
  'listener cannot see a future scheduled release');
reset role;
update public.scheduled_releases set publish_at = now() - interval '1 second'
where album_id = 'd2000000-0000-4000-8000-000000000101';
set local role authenticated;
select set_config('request.jwt.claim.sub', 'b2000000-0000-4000-8000-000000000006', true);
select set_config('request.jwt.claims',
  '{"sub":"b2000000-0000-4000-8000-000000000006","role":"authenticated"}', true);
select is((select count(*)::integer from public.albums
  where id = 'd2000000-0000-4000-8000-000000000101'), 1,
  'listener sees the release automatically after server time reaches publish_at');

select set_config('request.jwt.claim.sub', 'b2000000-0000-4000-8000-000000000001', true);
select set_config('request.jwt.claims',
  '{"sub":"b2000000-0000-4000-8000-000000000001","role":"authenticated"}', true);

select is(
  (public.admin_governance_dashboard()->'auditSummary'->>'highActivity')::boolean,
  false, 'bounded anomaly diagnostic is returned without sensitive payloads'
);
select ok(jsonb_array_length(public.admin_export_audit(null, null, null, 200)) > 0,
  'C-Level and CEO receive a bounded privacy-safe audit export');
select is(public.admin_set_audit_retention(730)->>'retentionDays', '730',
  'CEO can update non-destructive audit retention intent');

reset role;
select is((select count(*)::integer from public.content_enforcement_events), 4,
  'restore keeps both enforcement events as evidence');
select is((select count(*)::integer from public.staff_audit_log
  where operation like 'content.%'), 4, 'enforcement operations are audited');
select * from finish();
rollback;
