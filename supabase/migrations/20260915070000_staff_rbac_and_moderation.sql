begin;

create table public.staff_roles (
  role text primary key,
  display_name text not null unique,
  rank smallint not null unique,
  constraint staff_roles_role_format check (role ~ '^[a-z][a-z_]{1,39}$'),
  constraint staff_roles_display_name_length check (char_length(display_name) between 2 and 60),
  constraint staff_roles_rank_range check (rank between 1 and 100)
);

insert into public.staff_roles (role, display_name, rank) values
  ('officer', 'Officer', 60),
  ('supervisor', 'Supervisor', 70),
  ('manager', 'Manager', 80),
  ('c_level_executive', 'C-Level Executive', 90),
  ('ceo', 'CEO', 100);

create table public.staff_role_permissions (
  role text not null references public.staff_roles (role) on delete restrict,
  permission text not null,
  primary key (role, permission),
  constraint staff_role_permissions_format
    check (permission ~ '^[a-z][a-z_]*\.[a-z][a-z_]*$')
);

insert into public.staff_role_permissions (role, permission) values
  ('officer', 'admin.access'),
  ('officer', 'moderation.view'),
  ('officer', 'moderation.triage'),
  ('supervisor', 'admin.access'),
  ('supervisor', 'moderation.view'),
  ('supervisor', 'moderation.triage'),
  ('supervisor', 'moderation.decide'),
  ('manager', 'admin.access'),
  ('manager', 'moderation.view'),
  ('manager', 'moderation.triage'),
  ('manager', 'moderation.decide'),
  ('manager', 'catalog.draft'),
  ('manager', 'staff.manage'),
  ('c_level_executive', 'admin.access'),
  ('c_level_executive', 'moderation.view'),
  ('c_level_executive', 'moderation.triage'),
  ('c_level_executive', 'moderation.decide'),
  ('c_level_executive', 'catalog.draft'),
  ('c_level_executive', 'catalog.upload_audio'),
  ('c_level_executive', 'catalog.publish'),
  ('c_level_executive', 'staff.manage'),
  ('ceo', 'admin.access'),
  ('ceo', 'moderation.view'),
  ('ceo', 'moderation.triage'),
  ('ceo', 'moderation.decide'),
  ('ceo', 'catalog.draft'),
  ('ceo', 'catalog.upload_audio'),
  ('ceo', 'catalog.publish'),
  ('ceo', 'staff.manage');

create table public.staff_assignments (
  user_id uuid primary key references auth.users (id) on delete cascade,
  role text not null references public.staff_roles (role) on delete restrict,
  active boolean not null default true,
  assigned_by uuid references auth.users (id) on delete set null,
  assigned_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index staff_assignments_active_role_idx
on public.staff_assignments (role, user_id)
where active;

create table public.moderation_cases (
  id uuid primary key default gen_random_uuid(),
  subject_type text not null,
  subject_id uuid not null,
  reason text not null,
  status text not null default 'open',
  priority smallint not null default 50,
  created_by uuid not null references auth.users (id) on delete restrict,
  assigned_to uuid references auth.users (id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  resolved_at timestamptz,
  constraint moderation_cases_subject_type
    check (subject_type in ('profile', 'playlist', 'artist', 'album', 'track')),
  constraint moderation_cases_reason_length check (char_length(btrim(reason)) between 3 and 500),
  constraint moderation_cases_status
    check (status in ('open', 'in_review', 'actioned', 'dismissed')),
  constraint moderation_cases_priority_range check (priority between 1 and 100),
  constraint moderation_cases_resolution_consistency check (
    (status in ('actioned', 'dismissed') and resolved_at is not null) or
    (status in ('open', 'in_review') and resolved_at is null)
  )
);

create index moderation_cases_queue_idx
on public.moderation_cases (status, priority desc, created_at, id);

create table public.moderation_actions (
  id uuid primary key default gen_random_uuid(),
  case_id uuid not null references public.moderation_cases (id) on delete restrict,
  actor_id uuid not null references auth.users (id) on delete restrict,
  action text not null,
  notes text not null default '',
  created_at timestamptz not null default now(),
  constraint moderation_actions_action
    check (action in ('claim', 'escalate', 'approve', 'dismiss')),
  constraint moderation_actions_notes_length check (char_length(notes) <= 500)
);

create index moderation_actions_case_created_idx
on public.moderation_actions (case_id, created_at, id);

create table public.staff_audit_log (
  id bigint generated always as identity primary key,
  actor_id uuid not null references auth.users (id) on delete restrict,
  operation text not null,
  target_type text not null,
  target_id uuid,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint staff_audit_operation_length check (char_length(operation) between 3 and 80),
  constraint staff_audit_target_length check (char_length(target_type) between 2 and 40),
  constraint staff_audit_details_object check (jsonb_typeof(details) = 'object')
);

create index staff_audit_actor_created_idx
on public.staff_audit_log (actor_id, created_at desc, id desc);

create table public.track_media_variants (
  track_id uuid not null references public.tracks (id) on delete cascade,
  quality text not null,
  object_key text not null unique,
  size_bytes bigint not null,
  etag text not null,
  uploaded_by uuid not null references auth.users (id) on delete restrict,
  uploaded_at timestamptz not null default now(),
  primary key (track_id, quality),
  constraint track_media_quality check (quality in ('low', 'standard', 'high')),
  constraint track_media_size check (size_bytes between 1 and 52428800),
  constraint track_media_key_length check (char_length(object_key) between 40 and 160),
  constraint track_media_etag_length check (char_length(etag) between 1 and 160)
);

alter table public.artists add column created_by uuid references auth.users (id) on delete set null;
alter table public.artists add column published_by uuid references auth.users (id) on delete set null;
alter table public.artists add column published_at timestamptz;
alter table public.albums add column created_by uuid references auth.users (id) on delete set null;
alter table public.albums add column published_by uuid references auth.users (id) on delete set null;
alter table public.albums add column published_at timestamptz;
alter table public.tracks add column created_by uuid references auth.users (id) on delete set null;
alter table public.tracks add column published_by uuid references auth.users (id) on delete set null;
alter table public.tracks add column published_at timestamptz;

create trigger staff_assignments_set_updated_at
before update on public.staff_assignments
for each row execute function private.set_updated_at();

create trigger moderation_cases_set_updated_at
before update on public.moderation_cases
for each row execute function private.set_updated_at();

create or replace function private.current_staff_role()
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select assignment.role
  from public.staff_assignments as assignment
  where assignment.user_id = (select auth.uid())
    and assignment.active
$$;

create or replace function private.has_staff_permission(requested_permission text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.staff_assignments as assignment
    join public.staff_role_permissions as permission
      on permission.role = assignment.role
    where assignment.user_id = (select auth.uid())
      and assignment.active
      and permission.permission = requested_permission
  )
$$;

create or replace function private.write_staff_audit(
  operation_name text,
  target_kind text,
  target_uuid uuid,
  audit_details jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.staff_audit_log (actor_id, operation, target_type, target_id, details)
  values ((select auth.uid()), operation_name, target_kind, target_uuid, audit_details);
end;
$$;

revoke all on function private.current_staff_role() from public, anon, authenticated;
revoke all on function private.has_staff_permission(text) from public, anon, authenticated;
revoke all on function private.write_staff_audit(text, text, uuid, jsonb)
from public, anon, authenticated;
grant usage on schema private to authenticated;
grant execute on function private.current_staff_role() to authenticated;
grant execute on function private.has_staff_permission(text) to authenticated;

alter table public.staff_roles enable row level security;
alter table public.staff_roles force row level security;
alter table public.staff_role_permissions enable row level security;
alter table public.staff_role_permissions force row level security;
alter table public.staff_assignments enable row level security;
alter table public.staff_assignments force row level security;
alter table public.moderation_cases enable row level security;
alter table public.moderation_cases force row level security;
alter table public.moderation_actions enable row level security;
alter table public.moderation_actions force row level security;
alter table public.staff_audit_log enable row level security;
alter table public.staff_audit_log force row level security;
alter table public.track_media_variants enable row level security;
alter table public.track_media_variants force row level security;

revoke all on table public.staff_roles from anon, authenticated;
revoke all on table public.staff_role_permissions from anon, authenticated;
revoke all on table public.staff_assignments from anon, authenticated;
revoke all on table public.moderation_cases from anon, authenticated;
revoke all on table public.moderation_actions from anon, authenticated;
revoke all on table public.staff_audit_log from anon, authenticated;
revoke all on table public.track_media_variants from anon, authenticated;

grant select on table public.staff_roles to authenticated;
grant select on table public.staff_role_permissions to authenticated;
grant select on table public.staff_assignments to authenticated;
grant select on table public.moderation_cases to authenticated;
grant select on table public.moderation_actions to authenticated;
grant select on table public.track_media_variants to authenticated;
grant all on table public.staff_roles to service_role;
grant all on table public.staff_role_permissions to service_role;
grant all on table public.staff_assignments to service_role;
grant all on table public.moderation_cases to service_role;
grant all on table public.moderation_actions to service_role;
grant all on table public.staff_audit_log to service_role;
grant all on table public.track_media_variants to service_role;
grant usage, select on sequence public.staff_audit_log_id_seq to service_role;

create policy staff_roles_staff_select on public.staff_roles
for select to authenticated
using ((select private.has_staff_permission('admin.access')));

create policy staff_permissions_staff_select on public.staff_role_permissions
for select to authenticated
using ((select private.has_staff_permission('admin.access')));

create policy staff_assignments_own_or_manager_select on public.staff_assignments
for select to authenticated
using (
  user_id = (select auth.uid()) or
  (
    (select private.has_staff_permission('staff.manage')) and
    (
      (select private.current_staff_role()) = 'ceo' or
      (select rank from public.staff_roles where role = staff_assignments.role) <
        (select rank from public.staff_roles where role = (select private.current_staff_role()))
    )
  )
);

create policy moderation_cases_staff_select on public.moderation_cases
for select to authenticated
using ((select private.has_staff_permission('moderation.view')));

create policy moderation_actions_staff_select on public.moderation_actions
for select to authenticated
using ((select private.has_staff_permission('moderation.view')));

create policy track_media_authorized_select on public.track_media_variants
for select to authenticated
using (
  (select private.has_staff_permission('catalog.draft')) or
  (select private.has_staff_permission('catalog.upload_audio')) or
  (select private.has_staff_permission('catalog.publish'))
);

create policy artists_staff_select on public.artists
for select to authenticated
using ((select private.has_staff_permission('catalog.draft')));

create policy albums_staff_select on public.albums
for select to authenticated
using ((select private.has_staff_permission('catalog.draft')));

create policy tracks_staff_select on public.tracks
for select to authenticated
using ((select private.has_staff_permission('catalog.draft')));

create or replace function public.get_my_staff_context()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select case
    when assignment.user_id is null then jsonb_build_object(
      'isStaff', false,
      'role', null,
      'displayRole', null,
      'fullAccess', false,
      'permissions', '[]'::jsonb
    )
    else jsonb_build_object(
      'isStaff', true,
      'role', assignment.role,
      'displayRole', staff_role.display_name,
      'fullAccess', assignment.role = 'ceo',
      'permissions', coalesce((
        select jsonb_agg(permission.permission order by permission.permission)
        from public.staff_role_permissions as permission
        where permission.role = assignment.role
      ), '[]'::jsonb)
    )
  end
  from (select (select auth.uid()) as caller_id) as caller
  left join public.staff_assignments as assignment
    on assignment.user_id = caller.caller_id and assignment.active
  left join public.staff_roles as staff_role on staff_role.role = assignment.role
$$;

create or replace function public.admin_list_staff()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  actor_role text := (select private.current_staff_role());
  actor_rank smallint;
begin
  if not (select private.has_staff_permission('staff.manage')) then
    raise insufficient_privilege using message = 'Staff management is not permitted';
  end if;
  select rank into actor_rank from public.staff_roles where role = actor_role;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'userId', assignment.user_id,
      'email', account.email,
      'displayName', coalesce(profile.display_name, 'Rakyzu Staff'),
      'role', assignment.role,
      'displayRole', staff_role.display_name,
      'active', assignment.active,
      'updatedAt', assignment.updated_at
    ) order by staff_role.rank desc, assignment.updated_at desc, assignment.user_id)
    from public.staff_assignments as assignment
    join public.staff_roles as staff_role on staff_role.role = assignment.role
    join auth.users as account on account.id = assignment.user_id
    left join public.profiles as profile on profile.id = assignment.user_id
    where actor_role = 'ceo' or assignment.user_id = (select auth.uid()) or
      staff_role.rank < actor_rank
  ), '[]'::jsonb);
end;
$$;

create or replace function public.admin_assign_staff(
  target_user_id uuid,
  target_role text,
  target_active boolean default true
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor_id uuid := (select auth.uid());
  actor_role text := (select private.current_staff_role());
  actor_rank smallint;
  requested_rank smallint;
  existing_rank smallint;
begin
  if actor_id is null or not (select private.has_staff_permission('staff.manage')) then
    raise insufficient_privilege using message = 'Staff management is not permitted';
  end if;
  select rank into actor_rank from public.staff_roles where role = actor_role;
  select rank into requested_rank from public.staff_roles where role = target_role;
  if requested_rank is null then
    raise invalid_parameter_value using message = 'Unknown staff role';
  end if;
  if not exists (select 1 from auth.users where id = target_user_id) then
    raise invalid_parameter_value using message = 'Target account does not exist';
  end if;
  select staff_role.rank into existing_rank
  from public.staff_assignments as assignment
  join public.staff_roles as staff_role on staff_role.role = assignment.role
  where assignment.user_id = target_user_id;

  if actor_role <> 'ceo' and (
    requested_rank >= actor_rank or coalesce(existing_rank, 0) >= actor_rank
  ) then
    raise insufficient_privilege using message = 'Only a higher office can manage this role';
  end if;
  if target_role = 'ceo' and actor_role <> 'ceo' then
    raise insufficient_privilege using message = 'Only the CEO can grant CEO access';
  end if;
  if target_user_id = actor_id and actor_role = 'ceo' and
    (not target_active or target_role <> 'ceo') and
    (select count(*) from public.staff_assignments where role = 'ceo' and active) <= 1 then
    raise check_violation using message = 'The final active CEO cannot be disabled';
  end if;

  insert into public.staff_assignments (user_id, role, active, assigned_by)
  values (target_user_id, target_role, target_active, actor_id)
  on conflict (user_id) do update set
    role = excluded.role,
    active = excluded.active,
    assigned_by = excluded.assigned_by,
    assigned_at = now(),
    updated_at = now();
  perform private.write_staff_audit(
    'staff.assignment.updated', 'staff', target_user_id,
    jsonb_build_object('role', target_role, 'active', target_active)
  );
  return jsonb_build_object('userId', target_user_id, 'role', target_role, 'active', target_active);
end;
$$;

create or replace function public.admin_assign_staff_by_email(
  target_email text,
  target_role text,
  target_active boolean default true
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  resolved_user_id uuid;
  normalized_email text := lower(btrim(coalesce(target_email, '')));
begin
  if not (select private.has_staff_permission('staff.manage')) then
    raise insufficient_privilege using message = 'Staff management is not permitted';
  end if;
  if char_length(normalized_email) not between 3 and 254 then
    raise invalid_parameter_value using message = 'Account email is invalid';
  end if;
  select id into resolved_user_id
  from auth.users
  where lower(email) = normalized_email
  limit 1;
  if resolved_user_id is null then
    raise invalid_parameter_value using message = 'Target account does not exist';
  end if;
  return public.admin_assign_staff(resolved_user_id, target_role, target_active);
end;
$$;

create or replace function public.admin_create_artist(artist_name text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  artist public.artists;
begin
  if not (select private.has_staff_permission('catalog.draft')) then
    raise insufficient_privilege using message = 'Catalog drafting is not permitted';
  end if;
  artist_name := btrim(coalesce(artist_name, ''));
  if char_length(artist_name) not between 1 and 120 then
    raise invalid_parameter_value using message = 'Artist name is invalid';
  end if;
  insert into public.artists (name, created_by)
  values (artist_name, (select auth.uid())) returning * into artist;
  perform private.write_staff_audit('catalog.artist.created', 'artist', artist.id);
  return jsonb_build_object('id', artist.id, 'name', artist.name, 'published', artist.is_published);
end;
$$;

create or replace function public.admin_create_album(
  artist_id uuid,
  album_title text,
  album_release_date date default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  album public.albums;
begin
  if not (select private.has_staff_permission('catalog.draft')) then
    raise insufficient_privilege using message = 'Catalog drafting is not permitted';
  end if;
  album_title := btrim(coalesce(album_title, ''));
  if char_length(album_title) not between 1 and 160 then
    raise invalid_parameter_value using message = 'Album title is invalid';
  end if;
  if not exists (select 1 from public.artists where id = artist_id) then
    raise invalid_parameter_value using message = 'Artist does not exist';
  end if;
  insert into public.albums (artist_id, title, release_date, created_by)
  values (artist_id, album_title, album_release_date, (select auth.uid()))
  returning * into album;
  perform private.write_staff_audit('catalog.album.created', 'album', album.id);
  return jsonb_build_object(
    'id', album.id, 'artistId', album.artist_id, 'title', album.title,
    'releaseDate', album.release_date, 'published', album.is_published
  );
end;
$$;

create or replace function public.admin_create_track(
  album_id uuid,
  track_title text,
  duration_ms integer,
  disc_number integer default 1,
  track_number integer default 1,
  explicit_content boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  track public.tracks;
begin
  if not (select private.has_staff_permission('catalog.draft')) then
    raise insufficient_privilege using message = 'Catalog drafting is not permitted';
  end if;
  track_title := btrim(coalesce(track_title, ''));
  if char_length(track_title) not between 1 and 160 or duration_ms not between 1000 and 86400000 or
    disc_number < 1 or track_number < 1 then
    raise invalid_parameter_value using message = 'Track metadata is invalid';
  end if;
  if not exists (select 1 from public.albums where id = album_id) then
    raise invalid_parameter_value using message = 'Album does not exist';
  end if;
  insert into public.tracks (
    album_id, title, duration_ms, disc_number, track_number, is_explicit, created_by
  ) values (
    album_id, track_title, duration_ms, disc_number, track_number, explicit_content,
    (select auth.uid())
  ) returning * into track;
  perform private.write_staff_audit('catalog.track.created', 'track', track.id);
  return jsonb_build_object(
    'id', track.id, 'albumId', track.album_id, 'title', track.title,
    'durationMs', track.duration_ms, 'trackNumber', track.track_number,
    'published', track.is_published
  );
end;
$$;

create or replace function public.admin_record_track_media(
  target_track_id uuid,
  media_quality text,
  media_object_key text,
  media_size_bytes bigint,
  media_etag text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  expected_name text;
begin
  if not (select private.has_staff_permission('catalog.upload_audio')) then
    raise insufficient_privilege using message = 'Audio upload is not permitted';
  end if;
  if media_quality not in ('low', 'standard', 'high') then
    raise invalid_parameter_value using message = 'Audio quality is invalid';
  end if;
  if not exists (select 1 from public.tracks where id = target_track_id) then
    raise invalid_parameter_value using message = 'Track does not exist';
  end if;
  expected_name := case media_quality
    when 'low' then 'source-low.mp3'
    when 'high' then 'source-high.mp3'
    else 'source.mp3'
  end;
  if media_object_key <> 'media/tracks/' || target_track_id::text || '/' || expected_name then
    raise invalid_parameter_value using message = 'Audio object key is invalid';
  end if;
  insert into public.track_media_variants (
    track_id, quality, object_key, size_bytes, etag, uploaded_by
  ) values (
    target_track_id, media_quality, media_object_key, media_size_bytes,
    left(media_etag, 160), (select auth.uid())
  ) on conflict (track_id, quality) do update set
    object_key = excluded.object_key,
    size_bytes = excluded.size_bytes,
    etag = excluded.etag,
    uploaded_by = excluded.uploaded_by,
    uploaded_at = now();
  perform private.write_staff_audit(
    'catalog.audio.uploaded', 'track', target_track_id,
    jsonb_build_object('quality', media_quality, 'sizeBytes', media_size_bytes)
  );
  return jsonb_build_object(
    'trackId', target_track_id, 'quality', media_quality, 'sizeBytes', media_size_bytes
  );
end;
$$;

create or replace function public.admin_publish_album(target_album_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  album_artist_id uuid;
  track_count integer;
  missing_media integer;
begin
  if not (select private.has_staff_permission('catalog.publish')) then
    raise insufficient_privilege using message = 'Catalog publishing is not permitted';
  end if;
  select artist_id into album_artist_id
  from public.albums where id = target_album_id for update;
  if album_artist_id is null then
    raise invalid_parameter_value using message = 'Album does not exist';
  end if;
  select count(*)::integer into track_count from public.tracks where album_id = target_album_id;
  select count(*)::integer into missing_media
  from public.tracks as track
  where track.album_id = target_album_id and not exists (
    select 1 from public.track_media_variants as media
    where media.track_id = track.id and media.quality = 'standard'
  );
  if track_count = 0 or missing_media > 0 then
    raise check_violation using message = 'Every album track requires standard audio before publishing';
  end if;
  update public.artists set
    is_published = true, published_by = (select auth.uid()), published_at = now()
  where id = album_artist_id;
  update public.albums set
    is_published = true, published_by = (select auth.uid()), published_at = now()
  where id = target_album_id;
  update public.tracks set
    is_published = true, published_by = (select auth.uid()), published_at = now()
  where album_id = target_album_id;
  perform private.write_staff_audit(
    'catalog.album.published', 'album', target_album_id,
    jsonb_build_object('trackCount', track_count)
  );
  return jsonb_build_object('albumId', target_album_id, 'published', true, 'trackCount', track_count);
end;
$$;

create or replace function public.admin_list_catalog_drafts()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if not (
    (select private.has_staff_permission('catalog.draft')) or
    (select private.has_staff_permission('catalog.publish'))
  ) then
    raise insufficient_privilege using message = 'Catalog access is not permitted';
  end if;
  return jsonb_build_object(
    'artists', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', artist.id, 'name', artist.name, 'published', artist.is_published
      ) order by artist.updated_at desc, artist.id)
      from (select * from public.artists order by updated_at desc, id limit 50) as artist
    ), '[]'::jsonb),
    'albums', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', album.id, 'artistId', album.artist_id, 'title', album.title,
        'published', album.is_published
      ) order by album.updated_at desc, album.id)
      from (select * from public.albums order by updated_at desc, id limit 50) as album
    ), '[]'::jsonb),
    'tracks', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', track.id, 'albumId', track.album_id, 'title', track.title,
        'trackNumber', track.track_number, 'published', track.is_published,
        'hasStandardAudio', exists (
          select 1 from public.track_media_variants as media
          where media.track_id = track.id and media.quality = 'standard'
        )
      ) order by track.updated_at desc, track.id)
      from (select * from public.tracks order by updated_at desc, id limit 100) as track
    ), '[]'::jsonb)
  );
end;
$$;

create or replace function public.admin_create_moderation_case(
  target_subject_type text,
  target_subject_id uuid,
  case_reason text,
  case_priority smallint default 50
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  new_case public.moderation_cases;
begin
  if not (select private.has_staff_permission('moderation.triage')) then
    raise insufficient_privilege using message = 'Moderation triage is not permitted';
  end if;
  case_reason := btrim(coalesce(case_reason, ''));
  insert into public.moderation_cases (
    subject_type, subject_id, reason, priority, created_by
  ) values (
    target_subject_type, target_subject_id, case_reason, case_priority, (select auth.uid())
  ) returning * into new_case;
  perform private.write_staff_audit('moderation.case.created', 'moderation_case', new_case.id);
  return jsonb_build_object(
    'id', new_case.id, 'subjectType', new_case.subject_type,
    'subjectId', new_case.subject_id, 'reason', new_case.reason,
    'status', new_case.status, 'priority', new_case.priority,
    'assignedTo', new_case.assigned_to, 'createdAt', new_case.created_at
  );
end;
$$;

create or replace function public.admin_list_moderation_cases(page_size integer default 50)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if not (select private.has_staff_permission('moderation.view')) then
    raise insufficient_privilege using message = 'Moderation access is not permitted';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', moderation_case.id,
      'subjectType', moderation_case.subject_type,
      'subjectId', moderation_case.subject_id,
      'reason', moderation_case.reason,
      'status', moderation_case.status,
      'priority', moderation_case.priority,
      'assignedTo', moderation_case.assigned_to,
      'createdAt', moderation_case.created_at
    ) order by moderation_case.priority desc, moderation_case.created_at, moderation_case.id)
    from (
      select * from public.moderation_cases
      where status in ('open', 'in_review')
      order by priority desc, created_at, id
      limit greatest(1, least(coalesce(page_size, 50), 100))
    ) as moderation_case
  ), '[]'::jsonb);
end;
$$;

create or replace function public.admin_moderate_case(
  target_case_id uuid,
  moderation_action text,
  action_notes text default ''
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_case public.moderation_cases;
  actor_id uuid := (select auth.uid());
begin
  action_notes := btrim(coalesce(action_notes, ''));
  if char_length(action_notes) > 500 then
    raise invalid_parameter_value using message = 'Moderation notes are too long';
  end if;
  if moderation_action in ('claim', 'escalate') then
    if not (select private.has_staff_permission('moderation.triage')) then
      raise insufficient_privilege using message = 'Moderation triage is not permitted';
    end if;
  elsif moderation_action in ('approve', 'dismiss') then
    if not (select private.has_staff_permission('moderation.decide')) then
      raise insufficient_privilege using message = 'Moderation decisions are not permitted';
    end if;
    if char_length(action_notes) < 3 then
      raise invalid_parameter_value using message = 'Decision notes are required';
    end if;
  else
    raise invalid_parameter_value using message = 'Moderation action is invalid';
  end if;

  select * into current_case from public.moderation_cases
  where id = target_case_id for update;
  if current_case.id is null or current_case.status in ('actioned', 'dismissed') then
    raise invalid_parameter_value using message = 'Moderation case is unavailable';
  end if;

  update public.moderation_cases set
    assigned_to = case when moderation_action = 'claim' then actor_id else assigned_to end,
    priority = case when moderation_action = 'escalate' then least(priority + 10, 100) else priority end,
    status = case
      when moderation_action in ('claim', 'escalate') then 'in_review'
      when moderation_action = 'approve' then 'actioned'
      else 'dismissed'
    end,
    resolved_at = case when moderation_action in ('approve', 'dismiss') then now() else null end
  where id = target_case_id returning * into current_case;
  insert into public.moderation_actions (case_id, actor_id, action, notes)
  values (target_case_id, actor_id, moderation_action, action_notes);
  perform private.write_staff_audit(
    'moderation.case.' || moderation_action, 'moderation_case', target_case_id
  );
  return jsonb_build_object(
    'id', current_case.id, 'status', current_case.status,
    'priority', current_case.priority, 'assignedTo', current_case.assigned_to
  );
end;
$$;

revoke execute on function public.get_my_staff_context() from public, anon;
revoke execute on function public.admin_list_staff() from public, anon;
revoke execute on function public.admin_assign_staff(uuid, text, boolean) from public, anon;
revoke execute on function public.admin_assign_staff_by_email(text, text, boolean) from public, anon;
revoke execute on function public.admin_create_artist(text) from public, anon;
revoke execute on function public.admin_create_album(uuid, text, date) from public, anon;
revoke execute on function public.admin_create_track(uuid, text, integer, integer, integer, boolean)
from public, anon;
revoke execute on function public.admin_record_track_media(uuid, text, text, bigint, text)
from public, anon;
revoke execute on function public.admin_publish_album(uuid) from public, anon;
revoke execute on function public.admin_list_catalog_drafts() from public, anon;
revoke execute on function public.admin_create_moderation_case(text, uuid, text, smallint)
from public, anon;
revoke execute on function public.admin_list_moderation_cases(integer) from public, anon;
revoke execute on function public.admin_moderate_case(uuid, text, text) from public, anon;

grant execute on function public.get_my_staff_context() to authenticated;
grant execute on function public.admin_list_staff() to authenticated;
grant execute on function public.admin_assign_staff(uuid, text, boolean) to authenticated;
grant execute on function public.admin_assign_staff_by_email(text, text, boolean) to authenticated;
grant execute on function public.admin_create_artist(text) to authenticated;
grant execute on function public.admin_create_album(uuid, text, date) to authenticated;
grant execute on function public.admin_create_track(uuid, text, integer, integer, integer, boolean)
to authenticated;
grant execute on function public.admin_record_track_media(uuid, text, text, bigint, text)
to authenticated;
grant execute on function public.admin_publish_album(uuid) to authenticated;
grant execute on function public.admin_list_catalog_drafts() to authenticated;
grant execute on function public.admin_create_moderation_case(text, uuid, text, smallint)
to authenticated;
grant execute on function public.admin_list_moderation_cases(integer) to authenticated;
grant execute on function public.admin_moderate_case(uuid, text, text) to authenticated;

comment on table public.staff_assignments is
  'Server-authoritative Rakyzu Music organization roles; clients cannot mutate assignments directly.';
comment on table public.staff_audit_log is
  'Append-only audit evidence for privileged staff operations; no direct authenticated writes.';
comment on table public.track_media_variants is
  'Private R2 object inventory written only after an authorized Worker upload succeeds.';

commit;
