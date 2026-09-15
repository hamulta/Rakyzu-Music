begin;

insert into public.staff_role_permissions (role, permission) values
  ('supervisor', 'content.enforce'),
  ('manager', 'content.enforce'),
  ('manager', 'catalog.team_manage'),
  ('manager', 'audit.view'),
  ('c_level_executive', 'content.enforce'),
  ('c_level_executive', 'catalog.team_manage'),
  ('c_level_executive', 'catalog.review'),
  ('c_level_executive', 'catalog.upload_artwork'),
  ('c_level_executive', 'audit.view'),
  ('c_level_executive', 'audit.export'),
  ('ceo', 'content.enforce'),
  ('ceo', 'catalog.team_manage'),
  ('ceo', 'catalog.review'),
  ('ceo', 'catalog.upload_artwork'),
  ('ceo', 'audit.view'),
  ('ceo', 'audit.export'),
  ('ceo', 'governance.manage');

create table public.content_enforcement_events (
  id bigint generated always as identity primary key,
  subject_type text not null,
  subject_id uuid not null,
  action text not null,
  reason text not null,
  actor_id uuid not null references auth.users (id) on delete restrict,
  moderation_case_id uuid references public.moderation_cases (id) on delete set null,
  created_at timestamptz not null default now(),
  constraint content_enforcement_subject_type
    check (subject_type in ('profile', 'playlist', 'artist', 'album', 'track')),
  constraint content_enforcement_action
    check (action in ('quarantine', 'take_down', 'restore')),
  constraint content_enforcement_reason_length
    check (char_length(btrim(reason)) between 5 and 500)
);

create index content_enforcement_latest_idx
on public.content_enforcement_events (subject_type, subject_id, created_at desc, id desc);

create table public.catalog_labels (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  created_by uuid not null references auth.users (id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint catalog_labels_name_length check (char_length(btrim(name)) between 1 and 120)
);

create table public.catalog_label_artists (
  label_id uuid not null references public.catalog_labels (id) on delete cascade,
  artist_id uuid not null references public.artists (id) on delete cascade,
  created_by uuid not null references auth.users (id) on delete restrict,
  created_at timestamptz not null default now(),
  primary key (label_id, artist_id)
);

create table public.catalog_team_memberships (
  scope_type text not null,
  scope_id uuid not null,
  user_id uuid not null references auth.users (id) on delete cascade,
  access_level text not null,
  active boolean not null default true,
  assigned_by uuid not null references auth.users (id) on delete restrict,
  assigned_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (scope_type, scope_id, user_id),
  constraint catalog_team_scope_type check (scope_type in ('artist', 'label')),
  constraint catalog_team_access_level check (access_level in ('viewer', 'editor', 'admin'))
);

create index catalog_team_user_idx
on public.catalog_team_memberships (user_id, active, scope_type, scope_id);

create table public.album_artwork_assets (
  album_id uuid primary key references public.albums (id) on delete cascade,
  object_key text not null unique,
  content_type text not null,
  size_bytes integer not null,
  etag text not null,
  uploaded_by uuid not null references auth.users (id) on delete restrict,
  uploaded_at timestamptz not null default now(),
  constraint album_artwork_key check (
    object_key = 'media/albums/' || album_id::text || '/artwork.webp'
  ),
  constraint album_artwork_content_type check (content_type = 'image/webp'),
  constraint album_artwork_size check (size_bytes between 12 and 5242880),
  constraint album_artwork_etag_length check (char_length(etag) between 1 and 160)
);

create table public.catalog_review_items (
  id uuid primary key default gen_random_uuid(),
  review_type text not null,
  target_type text not null,
  target_id uuid not null,
  status text not null default 'pending',
  submission_notes text not null default '',
  decision_notes text,
  submitted_by uuid not null references auth.users (id) on delete restrict,
  decided_by uuid references auth.users (id) on delete restrict,
  submitted_at timestamptz not null default now(),
  decided_at timestamptz,
  constraint catalog_review_type check (review_type in ('artwork', 'release')),
  constraint catalog_review_target_type check (target_type = 'album'),
  constraint catalog_review_status check (status in ('pending', 'approved', 'rejected', 'superseded')),
  constraint catalog_review_submission_notes check (char_length(submission_notes) <= 500),
  constraint catalog_review_decision_notes check (
    decision_notes is null or char_length(btrim(decision_notes)) between 3 and 500
  ),
  constraint catalog_review_decision_consistency check (
    (status = 'pending' and decided_by is null and decided_at is null and decision_notes is null) or
    (status in ('approved', 'rejected') and decided_by is not null and decided_at is not null) or
    status = 'superseded'
  )
);

create unique index catalog_review_one_pending_idx
on public.catalog_review_items (review_type, target_type, target_id)
where status = 'pending';

create index catalog_review_queue_idx
on public.catalog_review_items (status, submitted_at, id);

create table public.scheduled_releases (
  album_id uuid primary key references public.albums (id) on delete cascade,
  publish_at timestamptz not null,
  status text not null default 'scheduled',
  scheduled_by uuid not null references auth.users (id) on delete restrict,
  scheduled_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint scheduled_release_status check (status in ('scheduled', 'published', 'cancelled'))
);

create index scheduled_release_due_idx
on public.scheduled_releases (publish_at, album_id)
where status = 'scheduled';

create table public.audit_retention_policy (
  singleton boolean primary key default true check (singleton),
  retention_days integer not null default 365,
  updated_by uuid references auth.users (id) on delete set null,
  updated_at timestamptz not null default now(),
  constraint audit_retention_days check (retention_days between 90 and 2555)
);

insert into public.audit_retention_policy (singleton, retention_days) values (true, 365);

create trigger catalog_labels_set_updated_at
before update on public.catalog_labels
for each row execute function private.set_updated_at();
create trigger catalog_team_memberships_set_updated_at
before update on public.catalog_team_memberships
for each row execute function private.set_updated_at();
create trigger scheduled_releases_set_updated_at
before update on public.scheduled_releases
for each row execute function private.set_updated_at();

alter table public.content_enforcement_events enable row level security;
alter table public.content_enforcement_events force row level security;
alter table public.catalog_labels enable row level security;
alter table public.catalog_labels force row level security;
alter table public.catalog_label_artists enable row level security;
alter table public.catalog_label_artists force row level security;
alter table public.catalog_team_memberships enable row level security;
alter table public.catalog_team_memberships force row level security;
alter table public.album_artwork_assets enable row level security;
alter table public.album_artwork_assets force row level security;
alter table public.catalog_review_items enable row level security;
alter table public.catalog_review_items force row level security;
alter table public.scheduled_releases enable row level security;
alter table public.scheduled_releases force row level security;
alter table public.audit_retention_policy enable row level security;
alter table public.audit_retention_policy force row level security;

revoke all on table public.content_enforcement_events from anon, authenticated;
revoke all on table public.catalog_labels from anon, authenticated;
revoke all on table public.catalog_label_artists from anon, authenticated;
revoke all on table public.catalog_team_memberships from anon, authenticated;
revoke all on table public.album_artwork_assets from anon, authenticated;
revoke all on table public.catalog_review_items from anon, authenticated;
revoke all on table public.scheduled_releases from anon, authenticated;
revoke all on table public.audit_retention_policy from anon, authenticated;

grant select on table public.content_enforcement_events to authenticated;
grant select on table public.catalog_labels to authenticated;
grant select on table public.catalog_label_artists to authenticated;
grant select on table public.catalog_team_memberships to authenticated;
grant select on table public.album_artwork_assets to authenticated;
grant select on table public.catalog_review_items to authenticated;
grant select on table public.scheduled_releases to authenticated;
grant select on table public.audit_retention_policy to authenticated;
grant all on table public.content_enforcement_events, public.catalog_labels,
  public.catalog_label_artists, public.catalog_team_memberships, public.album_artwork_assets,
  public.catalog_review_items, public.scheduled_releases, public.audit_retention_policy
to service_role;
grant usage, select on sequence public.content_enforcement_events_id_seq to service_role;

create policy content_enforcement_staff_select on public.content_enforcement_events
for select to authenticated using ((select private.has_staff_permission('moderation.view')));
create policy catalog_labels_staff_or_member_select on public.catalog_labels
for select to authenticated using (
  (select private.has_staff_permission('catalog.draft')) or exists (
    select 1 from public.catalog_team_memberships membership
    where membership.scope_type = 'label' and membership.scope_id = catalog_labels.id
      and membership.user_id = (select auth.uid()) and membership.active
  )
);
create policy catalog_label_artists_staff_or_member_select on public.catalog_label_artists
for select to authenticated using (
  (select private.has_staff_permission('catalog.draft')) or exists (
    select 1 from public.catalog_team_memberships membership
    where membership.scope_type = 'label' and membership.scope_id = catalog_label_artists.label_id
      and membership.user_id = (select auth.uid()) and membership.active
  )
);
create policy catalog_team_own_or_staff_select on public.catalog_team_memberships
for select to authenticated using (
  user_id = (select auth.uid()) or (select private.has_staff_permission('catalog.team_manage'))
);
create policy artwork_assets_staff_select on public.album_artwork_assets
for select to authenticated using ((select private.has_staff_permission('catalog.draft')));
create policy catalog_reviews_staff_select on public.catalog_review_items
for select to authenticated using (
  (select private.has_staff_permission('catalog.draft')) or
  (select private.has_staff_permission('catalog.review'))
);
create policy scheduled_releases_staff_select on public.scheduled_releases
for select to authenticated using ((select private.has_staff_permission('catalog.draft')));
create policy audit_retention_authorized_select on public.audit_retention_policy
for select to authenticated using ((select private.has_staff_permission('audit.view')));

create or replace function private.content_exists(content_type text, content_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select case content_type
    when 'artist' then exists(select 1 from public.artists where id = content_id)
    when 'album' then exists(select 1 from public.albums where id = content_id)
    when 'track' then exists(select 1 from public.tracks where id = content_id)
    when 'profile' then exists(select 1 from public.profiles where id = content_id)
    when 'playlist' then exists(select 1 from public.playlists where id = content_id)
    else false
  end
$$;

create or replace function private.is_content_available(content_type text, content_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select coalesce((
    select event.action = 'restore'
    from public.content_enforcement_events event
    where event.subject_type = content_type and event.subject_id = content_id
    order by event.created_at desc, event.id desc limit 1
  ), true)
$$;

create or replace function private.album_is_available(target_album_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select (select private.is_content_available('album', target_album_id)) and exists(
    select 1 from public.albums album
    where album.id = target_album_id
      and (select private.is_content_available('artist', album.artist_id))
  ) and coalesce((
    select release.status = 'published' or
      (release.status = 'scheduled' and release.publish_at <= now())
    from public.scheduled_releases release where release.album_id = target_album_id
  ), true)
$$;

create or replace function private.artist_is_available(target_artist_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select (select private.is_content_available('artist', target_artist_id)) and exists(
    select 1 from public.albums album
    where album.artist_id = target_artist_id and album.is_published
      and (select private.album_is_available(album.id))
  )
$$;

revoke all on function private.content_exists(text, uuid) from public, anon, authenticated;
revoke all on function private.is_content_available(text, uuid) from public, anon, authenticated;
revoke all on function private.album_is_available(uuid) from public, anon, authenticated;
revoke all on function private.artist_is_available(uuid) from public, anon, authenticated;
grant execute on function private.is_content_available(text, uuid) to authenticated;
grant execute on function private.album_is_available(uuid) to authenticated;
grant execute on function private.artist_is_available(uuid) to authenticated;

drop policy artists_select_published on public.artists;
drop policy albums_select_published on public.albums;
drop policy tracks_select_published on public.tracks;
create policy artists_select_published on public.artists for select to authenticated
using (is_published and (select private.artist_is_available(id)));
create policy albums_select_published on public.albums for select to authenticated
using (is_published and (select private.album_is_available(id)));
create policy tracks_select_published on public.tracks for select to authenticated
using (is_published and (select private.is_content_available('track', id)) and
  (select private.album_is_available(album_id)));

create or replace function public.admin_apply_content_enforcement(
  target_subject_type text,
  target_subject_id uuid,
  enforcement_action text,
  enforcement_reason text,
  target_case_id uuid default null
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare new_event public.content_enforcement_events;
begin
  if not (select private.has_staff_permission('content.enforce')) then
    raise insufficient_privilege using message = 'Content enforcement is not permitted';
  end if;
  enforcement_reason := btrim(coalesce(enforcement_reason, ''));
  if target_subject_type not in ('profile', 'playlist', 'artist', 'album', 'track') or
    enforcement_action not in ('quarantine', 'take_down', 'restore') or
    char_length(enforcement_reason) not between 5 and 500 then
    raise invalid_parameter_value using message = 'Content enforcement request is invalid';
  end if;
  if not (select private.content_exists(target_subject_type, target_subject_id)) then
    raise invalid_parameter_value using message = 'Content target does not exist';
  end if;
  if target_case_id is not null and not exists(
    select 1 from public.moderation_cases where id = target_case_id
  ) then raise invalid_parameter_value using message = 'Moderation case does not exist'; end if;
  insert into public.content_enforcement_events(
    subject_type, subject_id, action, reason, actor_id, moderation_case_id
  ) values (
    target_subject_type, target_subject_id, enforcement_action, enforcement_reason,
    (select auth.uid()), target_case_id
  ) returning * into new_event;
  perform private.write_staff_audit(
    'content.' || enforcement_action, target_subject_type, target_subject_id,
    jsonb_build_object('reason', enforcement_reason, 'caseId', target_case_id)
  );
  return jsonb_build_object(
    'id', new_event.id, 'subjectType', new_event.subject_type,
    'subjectId', new_event.subject_id, 'action', new_event.action,
    'createdAt', new_event.created_at
  );
end;
$$;

create or replace function public.admin_assign_catalog_team_by_email(
  target_scope_type text,
  target_scope_id uuid,
  target_email text,
  target_access_level text,
  target_active boolean default true
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare resolved_user_id uuid; normalized_email text := lower(btrim(coalesce(target_email, '')));
begin
  if not (select private.has_staff_permission('catalog.team_manage')) then
    raise insufficient_privilege using message = 'Catalog team management is not permitted';
  end if;
  if target_scope_type not in ('artist', 'label') or
    target_access_level not in ('viewer', 'editor', 'admin') then
    raise invalid_parameter_value using message = 'Catalog team assignment is invalid';
  end if;
  if (target_scope_type = 'artist' and not exists(select 1 from public.artists where id = target_scope_id)) or
    (target_scope_type = 'label' and not exists(select 1 from public.catalog_labels where id = target_scope_id)) then
    raise invalid_parameter_value using message = 'Catalog scope does not exist';
  end if;
  select id into resolved_user_id from auth.users where lower(email) = normalized_email limit 1;
  if resolved_user_id is null then raise invalid_parameter_value using message = 'Target account does not exist'; end if;
  insert into public.catalog_team_memberships(
    scope_type, scope_id, user_id, access_level, active, assigned_by
  ) values (
    target_scope_type, target_scope_id, resolved_user_id, target_access_level,
    target_active, (select auth.uid())
  ) on conflict (scope_type, scope_id, user_id) do update set
    access_level = excluded.access_level, active = excluded.active,
    assigned_by = excluded.assigned_by, assigned_at = now(), updated_at = now();
  perform private.write_staff_audit(
    'catalog.team.assignment.updated', target_scope_type, target_scope_id,
    jsonb_build_object('userId', resolved_user_id, 'accessLevel', target_access_level,
      'active', target_active)
  );
  return jsonb_build_object('scopeType', target_scope_type, 'scopeId', target_scope_id,
    'userId', resolved_user_id, 'accessLevel', target_access_level, 'active', target_active);
end;
$$;

create or replace function public.admin_create_catalog_label(label_name text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare label public.catalog_labels;
begin
  if not (select private.has_staff_permission('catalog.draft')) then
    raise insufficient_privilege using message = 'Catalog drafting is not permitted';
  end if;
  label_name := btrim(coalesce(label_name, ''));
  if char_length(label_name) not between 1 and 120 then
    raise invalid_parameter_value using message = 'Label name is invalid';
  end if;
  insert into public.catalog_labels(name, created_by)
  values(label_name, (select auth.uid())) returning * into label;
  perform private.write_staff_audit('catalog.label.created', 'label', label.id);
  return jsonb_build_object('id', label.id, 'name', label.name);
end;
$$;

create or replace function public.admin_link_catalog_label_artist(
  target_label_id uuid, target_artist_id uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  if not (select private.has_staff_permission('catalog.team_manage')) then
    raise insufficient_privilege using message = 'Catalog team management is not permitted';
  end if;
  if not exists(select 1 from public.catalog_labels where id = target_label_id) or
    not exists(select 1 from public.artists where id = target_artist_id) then
    raise invalid_parameter_value using message = 'Label or artist does not exist';
  end if;
  insert into public.catalog_label_artists(label_id, artist_id, created_by)
  values(target_label_id, target_artist_id, (select auth.uid())) on conflict do nothing;
  perform private.write_staff_audit('catalog.label.artist.linked', 'label', target_label_id,
    jsonb_build_object('artistId', target_artist_id));
  return jsonb_build_object('labelId', target_label_id, 'artistId', target_artist_id);
end;
$$;

create or replace function public.admin_record_album_artwork(
  target_album_id uuid, media_object_key text, media_size_bytes integer,
  media_content_type text, media_etag text
) returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  if not (select private.has_staff_permission('catalog.upload_artwork')) then
    raise insufficient_privilege using message = 'Artwork upload is not permitted';
  end if;
  if not exists(select 1 from public.albums where id = target_album_id) or
    media_object_key <> 'media/albums/' || target_album_id::text || '/artwork.webp' or
    media_content_type <> 'image/webp' or media_size_bytes not between 12 and 5242880 then
    raise invalid_parameter_value using message = 'Artwork upload is invalid';
  end if;
  update public.catalog_review_items set status = 'superseded'
  where review_type = 'artwork' and target_type = 'album' and target_id = target_album_id
    and status in ('pending', 'approved');
  insert into public.album_artwork_assets(
    album_id, object_key, content_type, size_bytes, etag, uploaded_by
  ) values (
    target_album_id, media_object_key, media_content_type, media_size_bytes,
    left(media_etag, 160), (select auth.uid())
  ) on conflict (album_id) do update set
    object_key = excluded.object_key, content_type = excluded.content_type,
    size_bytes = excluded.size_bytes, etag = excluded.etag,
    uploaded_by = excluded.uploaded_by, uploaded_at = now();
  perform private.write_staff_audit('catalog.artwork.uploaded', 'album', target_album_id,
    jsonb_build_object('sizeBytes', media_size_bytes));
  return jsonb_build_object('albumId', target_album_id, 'sizeBytes', media_size_bytes);
end;
$$;

create or replace function public.admin_submit_catalog_review(
  requested_review_type text, requested_target_type text, requested_target_id uuid,
  requested_notes text default ''
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare item public.catalog_review_items;
begin
  if not (select private.has_staff_permission('catalog.draft')) then
    raise insufficient_privilege using message = 'Catalog review submission is not permitted';
  end if;
  requested_notes := btrim(coalesce(requested_notes, ''));
  if requested_review_type not in ('artwork', 'release') or requested_target_type <> 'album' or
    char_length(requested_notes) > 500 or
    not exists(select 1 from public.albums where id = requested_target_id) then
    raise invalid_parameter_value using message = 'Catalog review request is invalid';
  end if;
  if requested_review_type = 'artwork' and not exists(
    select 1 from public.album_artwork_assets where album_id = requested_target_id
  ) then raise check_violation using message = 'Upload artwork before review'; end if;
  update public.catalog_review_items set status = 'superseded'
  where review_type = requested_review_type and target_type = requested_target_type
    and target_id = requested_target_id and status in ('pending', 'approved');
  insert into public.catalog_review_items(
    review_type, target_type, target_id, submission_notes, submitted_by
  ) values (
    requested_review_type, requested_target_type, requested_target_id,
    requested_notes, (select auth.uid())
  ) returning * into item;
  perform private.write_staff_audit('catalog.review.submitted', 'album', requested_target_id,
    jsonb_build_object('reviewType', requested_review_type));
  return jsonb_build_object('id', item.id, 'reviewType', item.review_type,
    'targetId', item.target_id, 'status', item.status);
end;
$$;

create or replace function public.admin_decide_catalog_review(
  target_review_id uuid, review_decision text, review_notes text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare item public.catalog_review_items; actor_id uuid := (select auth.uid());
begin
  if not (select private.has_staff_permission('catalog.review')) then
    raise insufficient_privilege using message = 'Catalog review decisions are not permitted';
  end if;
  review_notes := btrim(coalesce(review_notes, ''));
  if review_decision not in ('approved', 'rejected') or char_length(review_notes) not between 3 and 500 then
    raise invalid_parameter_value using message = 'Catalog review decision is invalid';
  end if;
  select * into item from public.catalog_review_items where id = target_review_id for update;
  if item.id is null or item.status <> 'pending' then
    raise invalid_parameter_value using message = 'Catalog review is unavailable';
  end if;
  if item.submitted_by = actor_id then
    raise insufficient_privilege using message = 'A different authorized reviewer is required';
  end if;
  update public.catalog_review_items set status = review_decision,
    decision_notes = review_notes, decided_by = actor_id, decided_at = now()
  where id = target_review_id returning * into item;
  perform private.write_staff_audit('catalog.review.' || review_decision, 'album', item.target_id,
    jsonb_build_object('reviewType', item.review_type));
  return jsonb_build_object('id', item.id, 'status', item.status,
    'reviewType', item.review_type, 'targetId', item.target_id);
end;
$$;

create or replace function private.album_is_release_ready(target_album_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.tracks where album_id = target_album_id) and
    not exists(
      select 1 from public.tracks track where track.album_id = target_album_id and not exists(
        select 1 from public.track_media_variants media
        where media.track_id = track.id and media.quality = 'standard'
      )
    ) and exists(
      select 1 from public.catalog_review_items review
      where review.target_type = 'album' and review.target_id = target_album_id
        and review.review_type = 'release' and review.status = 'approved'
    ) and exists(
      select 1 from public.catalog_review_items review
      where review.target_type = 'album' and review.target_id = target_album_id
        and review.review_type = 'artwork' and review.status = 'approved'
    )
$$;
revoke all on function private.album_is_release_ready(uuid) from public, anon, authenticated;

create or replace function public.admin_publish_album(target_album_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare album_artist_id uuid; track_count integer;
begin
  if not (select private.has_staff_permission('catalog.publish')) then
    raise insufficient_privilege using message = 'Catalog publishing is not permitted';
  end if;
  select artist_id into album_artist_id from public.albums where id = target_album_id for update;
  if album_artist_id is null then raise invalid_parameter_value using message = 'Album does not exist'; end if;
  if not (select private.album_is_release_ready(target_album_id)) then
    raise check_violation using message = 'Approved artwork, release review, and standard audio are required';
  end if;
  select count(*)::integer into track_count from public.tracks where album_id = target_album_id;
  update public.artists set is_published = true, published_by = (select auth.uid()), published_at = now()
  where id = album_artist_id;
  update public.albums set is_published = true, published_by = (select auth.uid()), published_at = now()
  where id = target_album_id;
  update public.tracks set is_published = true, published_by = (select auth.uid()), published_at = now()
  where album_id = target_album_id;
  insert into public.scheduled_releases(album_id, publish_at, status, scheduled_by)
  values(target_album_id, now(), 'published', (select auth.uid()))
  on conflict(album_id) do update set publish_at = now(), status = 'published',
    scheduled_by = excluded.scheduled_by, scheduled_at = now(), updated_at = now();
  perform private.write_staff_audit('catalog.album.published', 'album', target_album_id,
    jsonb_build_object('trackCount', track_count));
  return jsonb_build_object('albumId', target_album_id, 'published', true,
    'trackCount', track_count, 'publishAt', now());
end;
$$;

create or replace function public.admin_schedule_album(
  target_album_id uuid, requested_publish_at timestamptz
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare album_artist_id uuid; track_count integer;
begin
  if not (select private.has_staff_permission('catalog.publish')) then
    raise insufficient_privilege using message = 'Catalog publishing is not permitted';
  end if;
  if requested_publish_at <= now() or requested_publish_at > now() + interval '365 days' then
    raise invalid_parameter_value using message = 'Scheduled release time is invalid';
  end if;
  select artist_id into album_artist_id from public.albums where id = target_album_id for update;
  if album_artist_id is null then raise invalid_parameter_value using message = 'Album does not exist'; end if;
  if not (select private.album_is_release_ready(target_album_id)) then
    raise check_violation using message = 'Approved artwork, release review, and standard audio are required';
  end if;
  select count(*)::integer into track_count from public.tracks where album_id = target_album_id;
  update public.artists set is_published = true where id = album_artist_id;
  update public.albums set is_published = true, published_by = (select auth.uid()),
    published_at = requested_publish_at where id = target_album_id;
  update public.tracks set is_published = true, published_by = (select auth.uid()),
    published_at = requested_publish_at where album_id = target_album_id;
  insert into public.scheduled_releases(album_id, publish_at, status, scheduled_by)
  values(target_album_id, requested_publish_at, 'scheduled', (select auth.uid()))
  on conflict(album_id) do update set publish_at = excluded.publish_at, status = 'scheduled',
    scheduled_by = excluded.scheduled_by, scheduled_at = now(), updated_at = now();
  perform private.write_staff_audit('catalog.album.scheduled', 'album', target_album_id,
    jsonb_build_object('publishAt', requested_publish_at, 'trackCount', track_count));
  return jsonb_build_object('albumId', target_album_id, 'status', 'scheduled',
    'publishAt', requested_publish_at, 'trackCount', track_count);
end;
$$;

create or replace function public.admin_governance_dashboard()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare can_view_audit boolean := (select private.has_staff_permission('audit.view'));
begin
  if not (select private.has_staff_permission('admin.access')) then
    raise insufficient_privilege using message = 'Admin access is not permitted';
  end if;
  return jsonb_build_object(
    'enforcements', case when (select private.has_staff_permission('moderation.view')) then coalesce((
      select jsonb_agg(jsonb_build_object('id', event.id, 'subjectType', event.subject_type,
        'subjectId', event.subject_id, 'action', event.action, 'reason', event.reason,
        'createdAt', event.created_at) order by event.created_at desc, event.id desc)
      from (select * from public.content_enforcement_events order by created_at desc, id desc limit 50) event
    ), '[]'::jsonb) else '[]'::jsonb end,
    'labels', case when (select private.has_staff_permission('catalog.team_manage')) then coalesce((
      select jsonb_agg(jsonb_build_object('id', label.id, 'name', label.name)
        order by label.name, label.id)
      from (select * from public.catalog_labels order by name, id limit 100) label
    ), '[]'::jsonb) else '[]'::jsonb end,
    'teams', case when (select private.has_staff_permission('catalog.team_manage')) then coalesce((
      select jsonb_agg(jsonb_build_object('scopeType', membership.scope_type,
        'scopeId', membership.scope_id, 'userId', membership.user_id,
        'accessLevel', membership.access_level, 'active', membership.active,
        'updatedAt', membership.updated_at) order by membership.updated_at desc)
      from (select * from public.catalog_team_memberships order by updated_at desc limit 50) membership
    ), '[]'::jsonb) else '[]'::jsonb end,
    'reviews', case when (select private.has_staff_permission('catalog.draft')) then coalesce((
      select jsonb_agg(jsonb_build_object('id', review.id, 'reviewType', review.review_type,
        'targetType', review.target_type, 'targetId', review.target_id,
        'status', review.status, 'submissionNotes', review.submission_notes,
        'submittedAt', review.submitted_at) order by review.submitted_at desc, review.id)
      from (select * from public.catalog_review_items order by submitted_at desc, id limit 50) review
    ), '[]'::jsonb) else '[]'::jsonb end,
    'schedules', case when (select private.has_staff_permission('catalog.draft')) then coalesce((
      select jsonb_agg(jsonb_build_object('albumId', release.album_id,
        'publishAt', release.publish_at, 'status', release.status)
        order by release.publish_at, release.album_id)
      from (select * from public.scheduled_releases order by publish_at desc limit 50) release
    ), '[]'::jsonb) else '[]'::jsonb end,
    'auditSummary', case when can_view_audit then jsonb_build_object(
      'events24h', (select count(*) from public.staff_audit_log where created_at >= now() - interval '24 hours'),
      'deniedOrEnforced24h', (select count(*) from public.staff_audit_log
        where created_at >= now() - interval '24 hours' and
          (operation like 'content.%' or operation like 'moderation.%')),
      'highActivity', (select count(*) from public.staff_audit_log
        where created_at >= now() - interval '10 minutes') >= 50,
      'retentionDays', (select retention_days from public.audit_retention_policy where singleton)
    ) else null end
  );
end;
$$;

create or replace function public.admin_export_audit(
  operation_filter text default null, target_type_filter text default null,
  before_time timestamptz default null, page_size integer default 200
) returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  if not (select private.has_staff_permission('audit.export')) then
    raise insufficient_privilege using message = 'Audit export is not permitted';
  end if;
  if char_length(coalesce(operation_filter, '')) > 80 or
    char_length(coalesce(target_type_filter, '')) > 40 then
    raise invalid_parameter_value using message = 'Audit filter is invalid';
  end if;
  return coalesce((select jsonb_agg(jsonb_build_object(
    'id', audit.id, 'operation', audit.operation, 'targetType', audit.target_type,
    'targetId', audit.target_id, 'createdAt', audit.created_at
  ) order by audit.created_at desc, audit.id desc) from (
    select * from public.staff_audit_log where
      (operation_filter is null or operation = operation_filter) and
      (target_type_filter is null or target_type = target_type_filter) and
      (before_time is null or created_at < before_time)
    order by created_at desc, id desc limit greatest(1, least(coalesce(page_size, 200), 500))
  ) audit), '[]'::jsonb);
end;
$$;

create or replace function public.admin_set_audit_retention(requested_days integer)
returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  if not (select private.has_staff_permission('governance.manage')) then
    raise insufficient_privilege using message = 'Governance management is not permitted';
  end if;
  if requested_days not between 90 and 2555 then
    raise invalid_parameter_value using message = 'Retention must be between 90 and 2555 days';
  end if;
  update public.audit_retention_policy set retention_days = requested_days,
    updated_by = (select auth.uid()), updated_at = now() where singleton;
  perform private.write_staff_audit('governance.audit.retention.updated', 'governance', null,
    jsonb_build_object('retentionDays', requested_days));
  return jsonb_build_object('retentionDays', requested_days);
end;
$$;

revoke execute on function public.admin_apply_content_enforcement(text, uuid, text, text, uuid) from public, anon;
revoke execute on function public.admin_assign_catalog_team_by_email(text, uuid, text, text, boolean) from public, anon;
revoke execute on function public.admin_create_catalog_label(text) from public, anon;
revoke execute on function public.admin_link_catalog_label_artist(uuid, uuid) from public, anon;
revoke execute on function public.admin_record_album_artwork(uuid, text, integer, text, text) from public, anon;
revoke execute on function public.admin_submit_catalog_review(text, text, uuid, text) from public, anon;
revoke execute on function public.admin_decide_catalog_review(uuid, text, text) from public, anon;
revoke execute on function public.admin_schedule_album(uuid, timestamptz) from public, anon;
revoke execute on function public.admin_governance_dashboard() from public, anon;
revoke execute on function public.admin_export_audit(text, text, timestamptz, integer) from public, anon;
revoke execute on function public.admin_set_audit_retention(integer) from public, anon;

grant execute on function public.admin_apply_content_enforcement(text, uuid, text, text, uuid) to authenticated;
grant execute on function public.admin_assign_catalog_team_by_email(text, uuid, text, text, boolean) to authenticated;
grant execute on function public.admin_create_catalog_label(text) to authenticated;
grant execute on function public.admin_link_catalog_label_artist(uuid, uuid) to authenticated;
grant execute on function public.admin_record_album_artwork(uuid, text, integer, text, text) to authenticated;
grant execute on function public.admin_submit_catalog_review(text, text, uuid, text) to authenticated;
grant execute on function public.admin_decide_catalog_review(uuid, text, text) to authenticated;
grant execute on function public.admin_schedule_album(uuid, timestamptz) to authenticated;
grant execute on function public.admin_governance_dashboard() to authenticated;
grant execute on function public.admin_export_audit(text, text, timestamptz, integer) to authenticated;
grant execute on function public.admin_set_audit_retention(integer) to authenticated;

comment on table public.content_enforcement_events is
  'Append-only reversible quarantine, takedown, and restore evidence for Rakyzu Music content.';
comment on table public.catalog_team_memberships is
  'Artist and label scoped access; membership never grants organization-global staff authority.';
comment on table public.catalog_review_items is
  'Separation-of-duty artwork and release review queue required before publication.';
comment on table public.audit_retention_policy is
  'CEO-controlled retention intent. Physical deletion requires a separately approved maintenance operation.';

commit;
