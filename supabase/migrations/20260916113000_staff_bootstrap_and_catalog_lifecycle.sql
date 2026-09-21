begin;

create table public.staff_assignment_intents (
  email text primary key,
  role text not null references public.staff_roles (role) on delete restrict,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint staff_assignment_intent_email check (
    email = lower(btrim(email)) and
    email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' and
    char_length(email) <= 254
  )
);

alter table public.staff_assignment_intents enable row level security;
alter table public.staff_assignment_intents force row level security;
revoke all on public.staff_assignment_intents from anon, authenticated;
grant all on public.staff_assignment_intents to service_role;

insert into public.staff_assignment_intents(email, role) values
  ('rakyzudev@gmail.com', 'ceo'),
  ('hamulta215@gmail.com', 'c_level_executive'),
  ('hamultastarius@gmail.com', 'manager'),
  ('hmultastarius@gmail.com', 'supervisor'),
  ('juevice@gmail.com', 'officer')
on conflict(email) do update set role = excluded.role, active = true, updated_at = now();

insert into public.staff_assignments(user_id, role, active, assigned_by)
select users.id, intent.role, intent.active, null
from auth.users users
join public.staff_assignment_intents intent on intent.email = lower(users.email)
on conflict(user_id) do update set
  role = excluded.role, active = excluded.active, assigned_by = null, updated_at = now();

create or replace function private.apply_staff_assignment_intent()
returns trigger language plpgsql security definer set search_path = '' as $$
declare intent public.staff_assignment_intents;
begin
  select * into intent from public.staff_assignment_intents
  where email = lower(new.email) and active;
  if intent.email is not null then
    insert into public.staff_assignments(user_id, role, active, assigned_by)
    values(new.id, intent.role, true, null)
    on conflict(user_id) do update set role = excluded.role, active = true,
      assigned_by = null, updated_at = now();
  elsif tg_op = 'UPDATE' and lower(old.email) is distinct from lower(new.email) then
    update public.staff_assignments set active = false, updated_at = now()
    where user_id = new.id and assigned_by is null
      and exists (select 1 from public.staff_assignment_intents
        where email = lower(old.email) and active);
  end if;
  return new;
end;
$$;
revoke all on function private.apply_staff_assignment_intent() from public, anon, authenticated;

create trigger auth_user_apply_staff_assignment_intent
after insert or update of email on auth.users
for each row execute function private.apply_staff_assignment_intent();

alter table public.artists
  add column archived_at timestamptz,
  add column archived_by uuid references auth.users(id) on delete set null;
alter table public.albums
  add column archived_at timestamptz,
  add column archived_by uuid references auth.users(id) on delete set null;

create or replace function private.album_is_available(target_album_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select (select private.is_content_available('album', target_album_id)) and exists(
    select 1 from public.albums album join public.artists artist on artist.id = album.artist_id
    where album.id = target_album_id and album.archived_at is null and
      artist.archived_at is null and
      (select private.is_content_available('artist', artist.id))
  ) and coalesce((
    select release.status = 'published' or
      (release.status = 'scheduled' and release.publish_at <= now())
    from public.scheduled_releases release where release.album_id = target_album_id
  ), true)
$$;

create or replace function private.album_is_release_ready(target_album_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.albums album join public.artists artist
    on artist.id = album.artist_id where album.id = target_album_id and
      album.archived_at is null and artist.archived_at is null) and
    exists(select 1 from public.tracks where album_id = target_album_id) and
    not exists(select 1 from public.tracks track where track.album_id = target_album_id
      and not exists(select 1 from public.track_media_variants media
        where media.track_id = track.id and media.quality = 'standard')) and
    exists(select 1 from public.catalog_review_items review
      where review.target_type = 'album' and review.target_id = target_album_id and
        review.review_type = 'release' and review.status = 'approved') and
    exists(select 1 from public.catalog_review_items review
      where review.target_type = 'album' and review.target_id = target_album_id and
        review.review_type = 'artwork' and review.status = 'approved')
$$;

alter table public.editorial_shelves drop constraint editorial_shelves_position_unique;
alter table public.editorial_shelves add constraint editorial_shelves_position_unique
  unique(position) deferrable initially deferred;

drop policy artists_select_published on public.artists;
drop policy albums_select_published on public.albums;
create policy artists_select_published on public.artists for select to authenticated
using (is_published and archived_at is null and (select private.artist_is_available(id)));
create policy albums_select_published on public.albums for select to authenticated
using (is_published and archived_at is null and (select private.album_is_available(id)));

create or replace function public.admin_update_artist(
  target_artist_id uuid, artist_name text, artist_email text default null
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare normalized_email text; resolved_user_id uuid; current_email text;
begin
  if not (select private.has_staff_permission('catalog.draft')) then
    raise insufficient_privilege using message = 'Catalog drafting is not permitted';
  end if;
  artist_name := btrim(coalesce(artist_name, ''));
  normalized_email := nullif(lower(btrim(coalesce(artist_email, ''))), '');
  if char_length(artist_name) not between 1 and 120 or
    (normalized_email is not null and (char_length(normalized_email) > 254 or
      normalized_email !~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$')) then
    raise invalid_parameter_value using message = 'Artist profile is invalid';
  end if;
  if not exists(select 1 from public.artists where id = target_artist_id and archived_at is null) then
    raise invalid_parameter_value using message = 'Artist profile does not exist';
  end if;
  select invited_email into current_email from public.artist_account_links where artist_id = target_artist_id;
  if normalized_email is not null then
    select id into resolved_user_id from auth.users where lower(email) = normalized_email limit 1;
  end if;
  update public.artists set name = artist_name where id = target_artist_id;
  if normalized_email is distinct from current_email then
    insert into public.artist_account_links(artist_id, invited_email, user_id, status, assigned_by)
    values(target_artist_id, normalized_email, resolved_user_id,
      case when normalized_email is null then 'unclaimed' else 'pending_consent' end,
      (select auth.uid()))
    on conflict(artist_id) do update set user_id = excluded.user_id,
      invited_email = excluded.invited_email, status = excluded.status,
      terms_version = null, accepted_at = null, revoked_at = null;
  end if;
  perform private.write_staff_audit('catalog.artist.updated', 'artist', target_artist_id,
    jsonb_build_object('accountLinkChanged', normalized_email is distinct from current_email));
  return jsonb_build_object('id', target_artist_id, 'name', artist_name,
    'accountStatus', case when normalized_email is null then 'unclaimed' else
      (select status from public.artist_account_links where artist_id = target_artist_id) end);
end;
$$;

create or replace function public.admin_archive_artist(target_artist_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  if not (select private.has_staff_permission('catalog.draft')) then
    raise insufficient_privilege using message = 'Catalog drafting is not permitted';
  end if;
  if not exists(select 1 from public.artists where id = target_artist_id and archived_at is null) then
    raise invalid_parameter_value using message = 'Artist profile does not exist';
  end if;
  update public.tracks set is_published = false where album_id in
    (select id from public.albums where artist_id = target_artist_id);
  update public.albums set is_published = false, archived_at = now(), archived_by = (select auth.uid())
    where artist_id = target_artist_id and archived_at is null;
  update public.artists set is_published = false, archived_at = now(), archived_by = (select auth.uid())
    where id = target_artist_id;
  update public.artist_account_links set status = 'revoked', revoked_at = now()
    where artist_id = target_artist_id;
  perform private.write_staff_audit('catalog.artist.archived', 'artist', target_artist_id);
  return jsonb_build_object('id', target_artist_id, 'archived', true);
end;
$$;

create or replace function public.admin_update_album(
  target_album_id uuid, album_title text, album_release_date date default null
) returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  if not (select private.has_staff_permission('catalog.draft')) then
    raise insufficient_privilege using message = 'Catalog drafting is not permitted';
  end if;
  album_title := btrim(coalesce(album_title, ''));
  if char_length(album_title) not between 1 and 160 or
    not exists(select 1 from public.albums where id = target_album_id and archived_at is null) then
    raise invalid_parameter_value using message = 'Album is invalid';
  end if;
  update public.albums set title = album_title, release_date = album_release_date
    where id = target_album_id;
  perform private.write_staff_audit('catalog.album.updated', 'album', target_album_id);
  return jsonb_build_object('id', target_album_id, 'title', album_title,
    'releaseDate', album_release_date);
end;
$$;

create or replace function public.admin_archive_album(target_album_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  if not (select private.has_staff_permission('catalog.draft')) then
    raise insufficient_privilege using message = 'Catalog drafting is not permitted';
  end if;
  if not exists(select 1 from public.albums where id = target_album_id and archived_at is null) then
    raise invalid_parameter_value using message = 'Album does not exist';
  end if;
  update public.tracks set is_published = false where album_id = target_album_id;
  update public.albums set is_published = false, archived_at = now(), archived_by = (select auth.uid())
    where id = target_album_id;
  perform private.write_staff_audit('catalog.album.archived', 'album', target_album_id);
  return jsonb_build_object('id', target_album_id, 'archived', true);
end;
$$;

create or replace function public.admin_list_recommendations()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  if not (select private.has_staff_permission('editorial.manage')) then
    raise insufficient_privilege using message = 'Editorial management is not permitted';
  end if;
  return coalesce((select jsonb_agg(jsonb_build_object(
    'id', shelf.id, 'title', shelf.title, 'subtitle', shelf.subtitle,
    'position', shelf.position, 'published', shelf.is_published,
    'trackId', shelf.featured_track_id, 'hasArtwork', shelf.artwork_object_key is not null
  ) order by shelf.position, shelf.id) from public.editorial_shelves shelf), '[]'::jsonb);
end;
$$;

create or replace function public.admin_list_catalog_drafts()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  if not ((select private.has_staff_permission('catalog.draft')) or
    (select private.has_staff_permission('catalog.publish'))) then
    raise insufficient_privilege using message = 'Catalog access is not permitted';
  end if;
  return jsonb_build_object(
    'artists', coalesce((select jsonb_agg(jsonb_build_object(
      'id', artist.id, 'name', artist.name, 'published', artist.is_published,
      'email', link.invited_email) order by artist.updated_at desc, artist.id)
      from (select * from public.artists where archived_at is null
        order by updated_at desc, id limit 50) artist
      left join public.artist_account_links link on link.artist_id = artist.id), '[]'::jsonb),
    'albums', coalesce((select jsonb_agg(jsonb_build_object(
      'id', album.id, 'artistId', album.artist_id, 'title', album.title,
      'releaseDate', album.release_date, 'published', album.is_published)
      order by album.updated_at desc, album.id)
      from (select * from public.albums where archived_at is null
        order by updated_at desc, id limit 50) album), '[]'::jsonb),
    'tracks', coalesce((select jsonb_agg(jsonb_build_object(
      'id', track.id, 'albumId', track.album_id, 'title', track.title,
      'trackNumber', track.track_number, 'published', track.is_published,
      'hasStandardAudio', exists(select 1 from public.track_media_variants media
        where media.track_id = track.id and media.quality = 'standard'))
      order by track.updated_at desc, track.id)
      from (select track.* from public.tracks track join public.albums album
        on album.id = track.album_id where album.archived_at is null
        order by track.updated_at desc, track.id limit 100) track), '[]'::jsonb)
  );
end;
$$;

create or replace function public.get_track_media_key(target_track_id uuid, requested_quality text)
returns text language plpgsql stable security definer set search_path = '' as $$
declare media_key text;
begin
  if (select auth.uid()) is null or requested_quality not in ('low', 'standard', 'high') then
    raise insufficient_privilege using message = 'Track media is unavailable';
  end if;
  if not exists (select 1 from public.tracks track
    join public.albums album on album.id = track.album_id
    join public.artists artist on artist.id = album.artist_id
    where track.id = target_track_id and track.is_published and
      album.is_published and album.archived_at is null and
      artist.is_published and artist.archived_at is null and
      (select private.is_content_available('track', track.id)) and
      (select private.album_is_available(track.album_id))) then
    return null;
  end if;
  select object_key into media_key from public.track_media_variants
  where track_id = target_track_id and quality = requested_quality;
  return media_key;
end;
$$;
revoke execute on function public.get_track_media_key(uuid, text) from public, anon;
grant execute on function public.get_track_media_key(uuid, text) to authenticated;

create or replace function public.admin_upsert_editorial_shelf(
  target_shelf_id uuid, shelf_title text, shelf_subtitle text, shelf_position integer,
  target_track_id uuid, published boolean
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare saved public.editorial_shelves; old_position integer;
begin
  if not (select private.has_staff_permission('editorial.manage')) then
    raise insufficient_privilege using message = 'Editorial management is not permitted';
  end if;
  shelf_title := btrim(coalesce(shelf_title, ''));
  shelf_subtitle := nullif(btrim(coalesce(shelf_subtitle, '')), '');
  if char_length(shelf_title) not between 1 and 80 or char_length(coalesce(shelf_subtitle, '')) > 160 or
    shelf_position not between 0 and 1000 or
    (target_track_id is not null and not exists(select 1 from public.tracks where id = target_track_id)) then
    raise invalid_parameter_value using message = 'Recommendation card is invalid';
  end if;
  if target_shelf_id is null then
    update public.editorial_shelves set position = position + 1 where position >= shelf_position;
    insert into public.editorial_shelves(title, subtitle, position, is_published, featured_track_id)
    values(shelf_title, shelf_subtitle, shelf_position, published, target_track_id) returning * into saved;
  else
    select position into old_position from public.editorial_shelves where id = target_shelf_id for update;
    if old_position is null then raise invalid_parameter_value using message = 'Recommendation card does not exist'; end if;
    if shelf_position < old_position then
      update public.editorial_shelves set position = position + 1
      where position >= shelf_position and position < old_position and id <> target_shelf_id;
    elsif shelf_position > old_position then
      update public.editorial_shelves set position = position - 1
      where position <= shelf_position and position > old_position and id <> target_shelf_id;
    end if;
    update public.editorial_shelves set title = shelf_title, subtitle = shelf_subtitle,
      position = shelf_position, is_published = published, featured_track_id = target_track_id,
      updated_at = now() where id = target_shelf_id returning * into saved;
  end if;
  if target_track_id is not null then
    delete from public.editorial_shelf_tracks
    where shelf_id = saved.id and (track_id = target_track_id or position = 0);
    insert into public.editorial_shelf_tracks(shelf_id, track_id, position)
    values(saved.id, target_track_id, 0);
  end if;
  perform private.write_staff_audit('editorial.recommendation.saved', 'editorial_shelf', saved.id,
    jsonb_build_object('position', saved.position, 'published', saved.is_published));
  return jsonb_build_object('id', saved.id, 'title', saved.title, 'position', saved.position,
    'published', saved.is_published, 'featuredTrackId', saved.featured_track_id);
end;
$$;

create or replace function public.admin_delete_editorial_shelf(target_shelf_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare removed_position integer;
begin
  if not (select private.has_staff_permission('editorial.manage')) then
    raise insufficient_privilege using message = 'Editorial management is not permitted';
  end if;
  delete from public.editorial_shelves where id = target_shelf_id returning position into removed_position;
  if removed_position is null then raise invalid_parameter_value using message = 'Recommendation card does not exist'; end if;
  update public.editorial_shelves set position = position - 1 where position > removed_position;
  perform private.write_staff_audit('editorial.recommendation.deleted', 'editorial_shelf', target_shelf_id);
  return jsonb_build_object('id', target_shelf_id, 'deleted', true);
end;
$$;

create or replace function public.admin_record_editorial_artwork(
  target_shelf_id uuid, media_object_key text, media_size_bytes integer,
  media_content_type text, media_etag text
) returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  if not (select private.has_staff_permission('editorial.manage')) then
    raise insufficient_privilege using message = 'Editorial management is not permitted';
  end if;
  if not exists(select 1 from public.editorial_shelves where id = target_shelf_id) or
    media_object_key <> 'media/recommendations/' || target_shelf_id::text || '/artwork.webp' or
    media_content_type <> 'image/webp' or media_size_bytes not between 12 and 5242880 then
    raise invalid_parameter_value using message = 'Recommendation artwork is invalid';
  end if;
  insert into public.editorial_shelf_artwork_assets(
    shelf_id, object_key, content_type, size_bytes, etag, uploaded_by
  ) values (
    target_shelf_id, media_object_key, media_content_type, media_size_bytes,
    left(media_etag, 160), (select auth.uid())
  ) on conflict(shelf_id) do update set size_bytes = excluded.size_bytes,
    etag = excluded.etag, uploaded_by = excluded.uploaded_by, uploaded_at = now();
  update public.editorial_shelves set artwork_object_key = media_object_key
    where id = target_shelf_id;
  perform private.write_staff_audit('editorial.recommendation.artwork.updated',
    'editorial_shelf', target_shelf_id, jsonb_build_object('sizeBytes', media_size_bytes));
  return jsonb_build_object('id', target_shelf_id, 'hasArtwork', true);
end;
$$;

revoke execute on function public.admin_update_artist(uuid, text, text) from public, anon;
revoke execute on function public.admin_archive_artist(uuid) from public, anon;
revoke execute on function public.admin_update_album(uuid, text, date) from public, anon;
revoke execute on function public.admin_archive_album(uuid) from public, anon;
revoke execute on function public.admin_list_recommendations() from public, anon;
revoke execute on function public.admin_delete_editorial_shelf(uuid) from public, anon;
revoke execute on function public.admin_record_editorial_artwork(uuid, text, integer, text, text)
  from public, anon;
grant execute on function public.admin_update_artist(uuid, text, text) to authenticated;
grant execute on function public.admin_archive_artist(uuid) to authenticated;
grant execute on function public.admin_update_album(uuid, text, date) to authenticated;
grant execute on function public.admin_archive_album(uuid) to authenticated;
grant execute on function public.admin_list_recommendations() to authenticated;
grant execute on function public.admin_delete_editorial_shelf(uuid) to authenticated;
grant execute on function public.admin_record_editorial_artwork(uuid, text, integer, text, text)
  to authenticated;

comment on table public.staff_assignment_intents is
  'CEO-approved exact-email organization role bootstrap, consumed immediately or on future signup.';
comment on column public.artists.archived_at is
  'Reversible user-facing removal marker; audit and relational history are preserved.';

commit;
