begin;

alter table public.artists add column artwork_object_key text;
alter table public.artists add constraint artists_artwork_object_key check (
  artwork_object_key is null or
  artwork_object_key = 'media/artists/' || id::text || '/artwork.webp'
);

create table public.artist_artwork_assets (
  artist_id uuid primary key references public.artists (id) on delete restrict,
  object_key text not null unique,
  content_type text not null,
  size_bytes integer not null,
  etag text not null,
  uploaded_by uuid not null references auth.users (id) on delete restrict,
  uploaded_at timestamptz not null default now(),
  constraint artist_artwork_exact_key check (
    object_key = 'media/artists/' || artist_id::text || '/artwork.webp'
  ),
  constraint artist_artwork_webp check (content_type = 'image/webp'),
  constraint artist_artwork_size check (size_bytes between 12 and 5242880),
  constraint artist_artwork_etag check (char_length(etag) between 1 and 160)
);
alter table public.artist_artwork_assets enable row level security;
alter table public.artist_artwork_assets force row level security;
revoke all on public.artist_artwork_assets from anon, authenticated;
grant select on public.artist_artwork_assets to authenticated;
grant all on public.artist_artwork_assets to service_role;

create or replace function private.artist_workspace_owner(target_artist_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is not null and exists (
    select 1 from public.artist_account_links link
    where link.artist_id = target_artist_id and link.user_id = (select auth.uid())
      and link.status = 'active'
  )
$$;

create or replace function private.artist_workspace_editor(target_artist_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select (select private.artist_workspace_owner(target_artist_id)) or (
    exists (select 1 from public.artist_account_links link
      where link.artist_id = target_artist_id and link.status = 'active') and exists (
    select 1 from public.catalog_team_memberships membership
    where membership.scope_type = 'artist' and membership.scope_id = target_artist_id
      and membership.user_id = (select auth.uid()) and membership.active
      and membership.access_level in ('editor', 'admin')
    )
  )
$$;

revoke all on function private.artist_workspace_owner(uuid),
  private.artist_workspace_editor(uuid) from public, anon, authenticated;
grant execute on function private.artist_workspace_owner(uuid),
  private.artist_workspace_editor(uuid) to authenticated;

create policy artist_artwork_scoped_select on public.artist_artwork_assets
for select to authenticated using (
  (select private.artist_is_available(artist_id)) or
  (select private.artist_workspace_editor(artist_id)) or
  (select private.has_staff_permission('catalog.draft'))
);

create or replace function public.artist_workspace_context()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare actor_id uuid := (select auth.uid()); linked public.artist_account_links;
  albums_json jsonb; tracks_json jsonb; team_json jsonb; reviews_json jsonb;
begin
  linked := private.resolve_artist_link_for_current_user();
  if linked.artist_id is null or linked.status <> 'active' then
    raise insufficient_privilege using message = 'An active Artist identity is required';
  end if;
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', album.id, 'title', album.title, 'releaseDate', album.release_date,
    'published', album.is_published, 'hasArtwork', artwork.album_id is not null
  ) order by album.created_at desc, album.id), '[]'::jsonb) into albums_json
  from public.albums album left join public.album_artwork_assets artwork on artwork.album_id = album.id
  where album.artist_id = linked.artist_id;
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', track.id, 'albumId', track.album_id, 'title', track.title,
    'durationMs', track.duration_ms, 'published', track.is_published,
    'hasStandardAudio', media.track_id is not null
  ) order by track.created_at desc, track.id), '[]'::jsonb) into tracks_json
  from public.tracks track join public.albums album on album.id = track.album_id
  left join public.track_media_variants media on media.track_id = track.id and media.quality = 'standard'
  where album.artist_id = linked.artist_id;
  select coalesce(jsonb_agg(jsonb_build_object(
    'userId', member.user_id, 'email', lower(account.email),
    'accessLevel', member.access_level, 'active', member.active
  ) order by lower(account.email)), '[]'::jsonb) into team_json
  from public.catalog_team_memberships member join auth.users account on account.id = member.user_id
  where member.scope_type = 'artist' and member.scope_id = linked.artist_id;
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', review.id, 'albumId', review.target_id, 'reviewType', review.review_type,
    'status', review.status, 'submittedAt', review.submitted_at,
    'decisionNotes', review.decision_notes
  ) order by review.submitted_at desc, review.id), '[]'::jsonb) into reviews_json
  from public.catalog_review_items review join public.albums album on album.id = review.target_id
  where album.artist_id = linked.artist_id and review.target_type = 'album';
  return jsonb_build_object(
    'artistId', linked.artist_id,
    'artistName', (select name from public.artists where id = linked.artist_id),
    'hasArtwork', exists(select 1 from public.artist_artwork_assets where artist_id = linked.artist_id),
    'albums', albums_json, 'tracks', tracks_json, 'team', team_json, 'reviews', reviews_json
  );
end;
$$;

create or replace function public.artist_assign_team_by_email(
  target_email text, target_access_level text, target_active boolean default true
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare actor_id uuid := (select auth.uid()); linked_artist_id uuid; target_user_id uuid;
  linked public.artist_account_links;
  normalized_email text := lower(btrim(coalesce(target_email, '')));
begin
  linked := private.resolve_artist_link_for_current_user();
  linked_artist_id := linked.artist_id;
  if linked_artist_id is null or linked.status <> 'active' then
    raise insufficient_privilege using message = 'Only the active Artist can manage their team';
  end if;
  if target_access_level not in ('viewer', 'editor') or
    normalized_email !~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' or
    char_length(normalized_email) > 254 then
    raise invalid_parameter_value using message = 'Team assignment is invalid';
  end if;
  select id into target_user_id from auth.users where lower(email) = normalized_email limit 1;
  if target_user_id is null or target_user_id = actor_id then
    raise invalid_parameter_value using message = 'Target account is unavailable';
  end if;
  insert into public.catalog_team_memberships(
    scope_type, scope_id, user_id, access_level, active, assigned_by
  ) values ('artist', linked_artist_id, target_user_id, target_access_level,
    target_active, actor_id)
  on conflict (scope_type, scope_id, user_id) do update set
    access_level = excluded.access_level, active = excluded.active,
    assigned_by = excluded.assigned_by, assigned_at = now(), updated_at = now();
  perform private.write_staff_audit('artist.team.updated', 'artist', linked_artist_id,
    jsonb_build_object('targetUserId', target_user_id, 'accessLevel', target_access_level,
      'active', target_active));
  return jsonb_build_object('artistId', linked_artist_id, 'userId', target_user_id,
    'accessLevel', target_access_level, 'active', target_active);
end;
$$;

create or replace function public.artist_create_album_draft(
  target_artist_id uuid, album_title text, album_release_date date default null
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare saved public.albums;
begin
  if not (select private.artist_workspace_editor(target_artist_id)) then
    raise insufficient_privilege using message = 'Artist draft access is required';
  end if;
  album_title := btrim(coalesce(album_title, ''));
  if char_length(album_title) not between 1 and 160 or
    not exists(select 1 from public.artists where id = target_artist_id) then
    raise invalid_parameter_value using message = 'Album draft is invalid';
  end if;
  insert into public.albums(artist_id, title, release_date, created_by)
  values(target_artist_id, album_title, album_release_date, (select auth.uid()))
  returning * into saved;
  perform private.write_staff_audit('artist.album.draft_created', 'album', saved.id);
  return jsonb_build_object('id', saved.id, 'artistId', saved.artist_id,
    'title', saved.title, 'published', false);
end;
$$;

create or replace function public.artist_create_track_draft(
  target_album_id uuid, track_title text, duration_ms integer,
  disc_number integer default 1, track_number integer default 1,
  explicit_content boolean default false
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare album public.albums; saved public.tracks;
begin
  select * into album from public.albums where id = target_album_id;
  if album.id is null or album.is_published or
    not (select private.artist_workspace_editor(album.artist_id)) then
    raise insufficient_privilege using message = 'Artist draft access is required';
  end if;
  track_title := btrim(coalesce(track_title, ''));
  if char_length(track_title) not between 1 and 160 or
    duration_ms not between 1000 and 86400000 or disc_number < 1 or track_number < 1 then
    raise invalid_parameter_value using message = 'Track draft is invalid';
  end if;
  insert into public.tracks(album_id, title, duration_ms, disc_number, track_number,
    is_explicit, created_by)
  values(target_album_id, track_title, duration_ms, disc_number, track_number,
    explicit_content, (select auth.uid())) returning * into saved;
  perform private.write_staff_audit('artist.track.draft_created', 'track', saved.id);
  return jsonb_build_object('id', saved.id, 'albumId', saved.album_id,
    'title', saved.title, 'published', false);
end;
$$;

create or replace function public.artist_submit_catalog_review(
  requested_review_type text, target_album_id uuid, requested_notes text default ''
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare album public.albums; saved public.catalog_review_items;
begin
  select * into album from public.albums where id = target_album_id;
  if album.id is null or album.is_published or
    not (select private.artist_workspace_editor(album.artist_id)) then
    raise insufficient_privilege using message = 'Artist draft access is required';
  end if;
  requested_notes := btrim(coalesce(requested_notes, ''));
  if requested_review_type not in ('artwork', 'release') or char_length(requested_notes) > 500 then
    raise invalid_parameter_value using message = 'Review request is invalid';
  end if;
  if requested_review_type = 'artwork' and not exists(
    select 1 from public.album_artwork_assets where album_id = target_album_id
  ) then raise check_violation using message = 'Upload artwork before review'; end if;
  if requested_review_type = 'release' and (
    not exists(select 1 from public.tracks where album_id = target_album_id) or
    exists(select 1 from public.tracks track where track.album_id = target_album_id
      and not exists(select 1 from public.track_media_variants media
        where media.track_id = track.id and media.quality = 'standard'))
  ) then raise check_violation using message = 'Upload standard audio for every track'; end if;
  update public.catalog_review_items set status = 'superseded'
  where review_type = requested_review_type and target_type = 'album'
    and target_id = target_album_id and status in ('pending', 'approved');
  insert into public.catalog_review_items(review_type, target_type, target_id,
    submission_notes, submitted_by)
  values(requested_review_type, 'album', target_album_id, requested_notes,
    (select auth.uid())) returning * into saved;
  perform private.write_staff_audit('artist.review.submitted', 'album', target_album_id,
    jsonb_build_object('reviewType', requested_review_type));
  return jsonb_build_object('id', saved.id, 'albumId', target_album_id,
    'reviewType', saved.review_type, 'status', saved.status);
end;
$$;

create or replace function public.artist_artwork_upload_scope()
returns uuid language plpgsql security definer set search_path = '' as $$
declare linked public.artist_account_links;
begin
  linked := private.resolve_artist_link_for_current_user();
  if linked.artist_id is null or linked.status <> 'active' then
    raise insufficient_privilege using message = 'An active Artist identity is required';
  end if;
  return linked.artist_id;
end;
$$;

create or replace function public.artist_can_edit_album(target_album_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.albums album where album.id = target_album_id
    and not album.is_published and (select private.artist_workspace_editor(album.artist_id)))
$$;

create or replace function public.artist_can_edit_track(target_track_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.tracks track join public.albums album on album.id = track.album_id
    where track.id = target_track_id and not album.is_published
      and (select private.artist_workspace_editor(album.artist_id)))
$$;

create or replace function public.artist_can_view_artwork(target_artist_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.artist_artwork_assets where artist_id = target_artist_id)
    and ((select private.artist_is_available(target_artist_id)) or
      (select private.artist_workspace_editor(target_artist_id)) or
      (select private.has_staff_permission('catalog.draft')))
$$;

create or replace function public.artist_record_profile_artwork(
  media_object_key text, media_size_bytes integer, media_content_type text, media_etag text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare linked_artist_id uuid := public.artist_artwork_upload_scope();
begin
  if media_object_key <> 'media/artists/' || linked_artist_id::text || '/artwork.webp' or
    media_content_type <> 'image/webp' or media_size_bytes not between 12 and 5242880 or
    char_length(coalesce(media_etag, '')) not between 1 and 160 then
    raise invalid_parameter_value using message = 'Artist artwork is invalid';
  end if;
  insert into public.artist_artwork_assets(artist_id, object_key, content_type,
    size_bytes, etag, uploaded_by)
  values(linked_artist_id, media_object_key, media_content_type,
    media_size_bytes, media_etag, (select auth.uid()))
  on conflict(artist_id) do update set object_key = excluded.object_key,
    size_bytes = excluded.size_bytes, etag = excluded.etag,
    uploaded_by = excluded.uploaded_by, uploaded_at = now();
  update public.artists set artwork_object_key = media_object_key where id = linked_artist_id;
  perform private.write_staff_audit('artist.artwork.uploaded', 'artist', linked_artist_id,
    jsonb_build_object('sizeBytes', media_size_bytes));
  return jsonb_build_object('artistId', linked_artist_id, 'hasArtwork', true);
end;
$$;

create or replace function public.artist_record_album_artwork(
  target_album_id uuid, media_object_key text, media_size_bytes integer,
  media_content_type text, media_etag text
) returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  if not public.artist_can_edit_album(target_album_id) then
    raise insufficient_privilege using message = 'Artist draft access is required';
  end if;
  if media_object_key <> 'media/albums/' || target_album_id::text || '/artwork.webp' or
    media_content_type <> 'image/webp' or media_size_bytes not between 12 and 5242880 or
    char_length(coalesce(media_etag, '')) not between 1 and 160 then
    raise invalid_parameter_value using message = 'Album artwork is invalid';
  end if;
  update public.catalog_review_items set status = 'superseded'
  where review_type = 'artwork' and target_type = 'album' and target_id = target_album_id
    and status in ('pending', 'approved');
  insert into public.album_artwork_assets(album_id, object_key, content_type,
    size_bytes, etag, uploaded_by)
  values(target_album_id, media_object_key, media_content_type,
    media_size_bytes, media_etag, (select auth.uid()))
  on conflict(album_id) do update set object_key = excluded.object_key,
    size_bytes = excluded.size_bytes, etag = excluded.etag,
    uploaded_by = excluded.uploaded_by, uploaded_at = now();
  perform private.write_staff_audit('artist.album_artwork.uploaded', 'album', target_album_id,
    jsonb_build_object('sizeBytes', media_size_bytes));
  return jsonb_build_object('albumId', target_album_id, 'hasArtwork', true);
end;
$$;

create or replace function public.artist_record_track_media(
  target_track_id uuid, media_quality text, media_object_key text, media_size_bytes bigint,
  media_etag text, media_content_type text, media_format text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare expected_name text;
begin
  if not public.artist_can_edit_track(target_track_id) then
    raise insufficient_privilege using message = 'Artist draft access is required';
  end if;
  if media_quality not in ('low', 'standard', 'high') or
    (media_content_type, media_format) not in (
      ('audio/mpeg', 'mp3'), ('audio/aac', 'aac'), ('audio/mp4', 'm4a'),
      ('audio/webm', 'webm'), ('audio/wav', 'wav'), ('audio/flac', 'flac')
    ) or media_size_bytes not between 12 and 52428800 or
    char_length(coalesce(media_etag, '')) not between 1 and 160 then
    raise invalid_parameter_value using message = 'Audio metadata is invalid';
  end if;
  expected_name := case media_quality when 'low' then 'source-low.' || media_format
    when 'high' then 'source-high.' || media_format else 'source.' || media_format end;
  if media_object_key <> 'media/tracks/' || target_track_id::text || '/' || expected_name then
    raise invalid_parameter_value using message = 'Audio object key is invalid';
  end if;
  insert into public.track_media_variants(track_id, quality, object_key, size_bytes,
    etag, uploaded_by, content_type, media_format)
  values(target_track_id, media_quality, media_object_key, media_size_bytes,
    media_etag, (select auth.uid()), media_content_type, media_format)
  on conflict(track_id, quality) do update set object_key = excluded.object_key,
    size_bytes = excluded.size_bytes, etag = excluded.etag, uploaded_by = excluded.uploaded_by,
    content_type = excluded.content_type, media_format = excluded.media_format, uploaded_at = now();
  perform private.write_staff_audit('artist.audio.uploaded', 'track', target_track_id,
    jsonb_build_object('quality', media_quality, 'format', media_format,
      'sizeBytes', media_size_bytes));
  return jsonb_build_object('trackId', target_track_id, 'quality', media_quality,
    'format', media_format);
end;
$$;

revoke all on function public.artist_workspace_context(),
  public.artist_assign_team_by_email(text, text, boolean),
  public.artist_create_album_draft(uuid, text, date),
  public.artist_create_track_draft(uuid, text, integer, integer, integer, boolean),
  public.artist_submit_catalog_review(text, uuid, text),
  public.artist_artwork_upload_scope(),
  public.artist_can_edit_album(uuid), public.artist_can_edit_track(uuid),
  public.artist_can_view_artwork(uuid),
  public.artist_record_profile_artwork(text, integer, text, text),
  public.artist_record_album_artwork(uuid, text, integer, text, text),
  public.artist_record_track_media(uuid, text, text, bigint, text, text, text)
from public, anon;
grant execute on function public.artist_workspace_context(),
  public.artist_assign_team_by_email(text, text, boolean),
  public.artist_create_album_draft(uuid, text, date),
  public.artist_create_track_draft(uuid, text, integer, integer, integer, boolean),
  public.artist_submit_catalog_review(text, uuid, text),
  public.artist_artwork_upload_scope(),
  public.artist_can_edit_album(uuid), public.artist_can_edit_track(uuid),
  public.artist_can_view_artwork(uuid),
  public.artist_record_profile_artwork(text, integer, text, text),
  public.artist_record_album_artwork(uuid, text, integer, text, text),
  public.artist_record_track_media(uuid, text, text, bigint, text, text, text)
to authenticated;

commit;
