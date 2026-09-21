begin;

insert into public.staff_role_permissions (role, permission) values
  ('officer', 'editorial.manage'),
  ('supervisor', 'editorial.manage'),
  ('manager', 'editorial.manage'),
  ('c_level_executive', 'editorial.manage'),
  ('ceo', 'editorial.manage')
on conflict do nothing;

alter table public.profiles
  add column avatar_object_key text,
  add column appearance_mode text not null default 'role';

alter table public.profiles
  add constraint profiles_appearance_mode check (appearance_mode in ('default', 'role')),
  add constraint profiles_avatar_key check (
    avatar_object_key is null or avatar_object_key = 'media/profiles/' || id::text || '/avatar.webp'
  );

alter table public.artists
  add column biography text not null default '';

alter table public.artists
  add constraint artists_biography_length check (char_length(biography) <= 1500);

create table public.artist_account_links (
  artist_id uuid primary key references public.artists (id) on delete cascade,
  user_id uuid unique references auth.users (id) on delete set null,
  invited_email text,
  status text not null default 'unclaimed',
  terms_version text,
  assigned_by uuid not null references auth.users (id) on delete restrict,
  assigned_at timestamptz not null default now(),
  accepted_at timestamptz,
  revoked_at timestamptz,
  updated_at timestamptz not null default now(),
  constraint artist_link_email check (
    invited_email is null or (
      invited_email = lower(btrim(invited_email)) and
      invited_email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' and
      char_length(invited_email) <= 254
    )
  ),
  constraint artist_link_status check (status in ('unclaimed', 'pending_consent', 'active', 'revoked')),
  constraint artist_link_state check (
    (status = 'unclaimed' and user_id is null and invited_email is null and accepted_at is null) or
    (status = 'pending_consent' and invited_email is not null and accepted_at is null) or
    (status = 'active' and user_id is not null and accepted_at is not null and terms_version is not null) or
    (status = 'revoked' and revoked_at is not null)
  )
);

create unique index artist_account_links_email_idx
on public.artist_account_links (invited_email)
where invited_email is not null and status <> 'revoked';

create table public.artist_terms (
  version text primary key,
  title text not null,
  summary text not null,
  terms_text text not null,
  effective_at timestamptz not null,
  active boolean not null default false,
  constraint artist_terms_version check (version ~ '^[0-9]{4}-[0-9]{2}$'),
  constraint artist_terms_copy check (
    char_length(title) between 3 and 120 and
    char_length(summary) between 20 and 600 and
    char_length(terms_text) between 100 and 6000
  )
);

create unique index artist_terms_one_active_idx on public.artist_terms (active) where active;

insert into public.artist_terms (version, title, summary, terms_text, effective_at, active) values (
  '2026-09',
  'Rakyzu Music Artist Terms',
  'Manage only the Artist identity and catalog scopes assigned to your account. Publication remains governed by authorized review.',
  'Welcome to the world of Rakyzu Music Artists. You may maintain the biography, profile imagery, team access, catalog drafts, and release submissions for the Artist identity assigned to you. You must only upload content that you own or are authorized to distribute, keep metadata accurate, respect listeners and other creators, protect account access, and follow moderation decisions. Artist access does not grant organization-wide staff authority or permission to self-publish without the required review. Rakyzu Music may suspend or revoke access when these terms, applicable law, or rights-holder obligations are breached.',
  now(),
  true
);

create table public.artist_terms_consents (
  id bigint generated always as identity primary key,
  artist_id uuid not null references public.artists (id) on delete restrict,
  user_id uuid not null references auth.users (id) on delete restrict,
  terms_version text not null references public.artist_terms (version) on delete restrict,
  accepted_at timestamptz not null default now(),
  unique (artist_id, user_id, terms_version)
);

create table public.profile_avatar_assets (
  user_id uuid primary key references auth.users (id) on delete cascade,
  object_key text not null unique,
  content_type text not null,
  size_bytes integer not null,
  etag text not null,
  uploaded_at timestamptz not null default now(),
  constraint profile_avatar_key check (object_key = 'media/profiles/' || user_id::text || '/avatar.webp'),
  constraint profile_avatar_content_type check (content_type = 'image/webp'),
  constraint profile_avatar_size check (size_bytes between 12 and 5242880),
  constraint profile_avatar_etag check (char_length(etag) between 1 and 160)
);

alter table public.track_media_variants
  add column content_type text not null default 'audio/mpeg',
  add column media_format text not null default 'mp3';

alter table public.track_media_variants
  add constraint track_media_content_type check (
    content_type in ('audio/mpeg', 'audio/aac', 'audio/mp4', 'audio/webm', 'audio/wav', 'audio/flac')
  ),
  add constraint track_media_format check (media_format in ('mp3', 'aac', 'm4a', 'webm', 'wav', 'flac'));

alter table public.editorial_shelves
  add column featured_track_id uuid references public.tracks (id) on delete set null,
  add column artwork_object_key text;

alter table public.editorial_shelves
  add constraint editorial_shelf_artwork_key check (
    artwork_object_key is null or artwork_object_key = 'media/recommendations/' || id::text || '/artwork.webp'
  );

create table public.editorial_shelf_artwork_assets (
  shelf_id uuid primary key references public.editorial_shelves (id) on delete cascade,
  object_key text not null unique,
  content_type text not null,
  size_bytes integer not null,
  etag text not null,
  uploaded_by uuid not null references auth.users (id) on delete restrict,
  uploaded_at timestamptz not null default now(),
  constraint editorial_artwork_key check (object_key = 'media/recommendations/' || shelf_id::text || '/artwork.webp'),
  constraint editorial_artwork_content_type check (content_type = 'image/webp'),
  constraint editorial_artwork_size check (size_bytes between 12 and 5242880),
  constraint editorial_artwork_etag check (char_length(etag) between 1 and 160)
);

create trigger artist_account_links_set_updated_at before update on public.artist_account_links
for each row execute function private.set_updated_at();

alter table public.artist_account_links enable row level security;
alter table public.artist_account_links force row level security;
alter table public.artist_terms enable row level security;
alter table public.artist_terms force row level security;
alter table public.artist_terms_consents enable row level security;
alter table public.artist_terms_consents force row level security;
alter table public.profile_avatar_assets enable row level security;
alter table public.profile_avatar_assets force row level security;
alter table public.editorial_shelf_artwork_assets enable row level security;
alter table public.editorial_shelf_artwork_assets force row level security;

revoke all on public.artist_account_links, public.artist_terms, public.artist_terms_consents,
  public.profile_avatar_assets, public.editorial_shelf_artwork_assets from anon, authenticated;
grant select on public.artist_terms to authenticated;
grant all on public.artist_account_links, public.artist_terms, public.artist_terms_consents,
  public.profile_avatar_assets, public.editorial_shelf_artwork_assets to service_role;

create policy artist_terms_authenticated_select on public.artist_terms
for select to authenticated using (active);
create policy artist_links_own_or_staff_select on public.artist_account_links
for select to authenticated using (
  user_id = (select auth.uid()) or (select private.has_staff_permission('catalog.draft'))
);
create policy artist_consents_own_or_audit_select on public.artist_terms_consents
for select to authenticated using (
  user_id = (select auth.uid()) or (select private.has_staff_permission('audit.view'))
);
create policy profile_avatar_authenticated_select on public.profile_avatar_assets
for select to authenticated using (true);
create policy editorial_artwork_published_select on public.editorial_shelf_artwork_assets
for select to authenticated using (
  exists (select 1 from public.editorial_shelves shelf where shelf.id = shelf_id and shelf.is_published)
  or (select private.has_staff_permission('editorial.manage'))
);

create or replace function private.resolve_artist_link_for_current_user()
returns public.artist_account_links language plpgsql security definer set search_path = '' as $$
declare actor_id uuid := (select auth.uid()); actor_email text; link public.artist_account_links;
begin
  if actor_id is null then return null; end if;
  select lower(email) into actor_email from auth.users where id = actor_id;
  update public.artist_account_links set user_id = actor_id
  where user_id is null and status = 'pending_consent' and invited_email = actor_email;
  select * into link from public.artist_account_links
    where user_id = actor_id and invited_email = actor_email and status <> 'revoked';
  return link;
end;
$$;
revoke all on function private.resolve_artist_link_for_current_user() from public, anon, authenticated;

create or replace function public.get_my_profile_context()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare actor_id uuid := (select auth.uid()); profile public.profiles; staff_role text; link public.artist_account_links;
  terms public.artist_terms;
begin
  if actor_id is null then raise insufficient_privilege using message = 'Authentication required'; end if;
  select * into profile from public.profiles where id = actor_id;
  select assignment.role into staff_role from public.staff_assignments assignment
    where assignment.user_id = actor_id and assignment.active;
  link := private.resolve_artist_link_for_current_user();
  select * into terms from public.artist_terms where active limit 1;
  return jsonb_build_object(
    'userId', actor_id,
    'displayName', profile.display_name,
    'onboardingCompleted', profile.onboarding_completed,
    'avatarAvailable', profile.avatar_object_key is not null,
    'avatarVersion', (select uploaded_at::text from public.profile_avatar_assets
      where user_id = actor_id),
    'appearanceMode', profile.appearance_mode,
    'identityKind', case when staff_role is not null then 'staff' when link.status = 'active' then 'artist' else 'listener' end,
    'role', staff_role,
    'verified', staff_role is not null or link.status = 'active',
    'artist', case when link.artist_id is null then null else jsonb_build_object(
      'id', link.artist_id,
      'name', (select name from public.artists where id = link.artist_id),
      'biography', (select biography from public.artists where id = link.artist_id),
      'status', link.status,
      'termsVersion', terms.version,
      'termsTitle', terms.title,
      'termsSummary', terms.summary,
      'termsText', terms.terms_text
    ) end
  );
end;
$$;

create or replace function public.accept_artist_terms(requested_version text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare actor_id uuid := (select auth.uid()); link public.artist_account_links; active_version text;
begin
  if actor_id is null then raise insufficient_privilege using message = 'Authentication required'; end if;
  link := private.resolve_artist_link_for_current_user();
  select version into active_version from public.artist_terms where active limit 1;
  if link.artist_id is null or link.status <> 'pending_consent' or requested_version <> active_version then
    raise invalid_parameter_value using message = 'Artist terms are unavailable';
  end if;
  insert into public.artist_terms_consents (artist_id, user_id, terms_version)
  values (link.artist_id, actor_id, active_version) on conflict do nothing;
  update public.artist_account_links set status = 'active', terms_version = active_version,
    accepted_at = now(), revoked_at = null where artist_id = link.artist_id;
  return jsonb_build_object('artistId', link.artist_id, 'status', 'active', 'termsVersion', active_version);
end;
$$;

create or replace function public.artist_update_biography(requested_biography text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare link public.artist_account_links; normalized_text text;
begin
  link := private.resolve_artist_link_for_current_user();
  normalized_text := btrim(coalesce(requested_biography, ''));
  if link.artist_id is null or link.status <> 'active' or
    char_length(normalized_text) > 1500 or
    exists(select 1 from public.artists where id = link.artist_id and archived_at is not null) then
    raise insufficient_privilege using message = 'Artist profile editing is unavailable';
  end if;
  update public.artists set biography = normalized_text where id = link.artist_id;
  return jsonb_build_object('artistId', link.artist_id, 'biography', normalized_text);
end;
$$;

create or replace function public.update_profile_appearance(requested_mode text)
returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  if (select auth.uid()) is null or requested_mode not in ('default', 'role') then
    raise invalid_parameter_value using message = 'Profile appearance is invalid';
  end if;
  update public.profiles set appearance_mode = requested_mode where id = (select auth.uid());
  return jsonb_build_object('appearanceMode', requested_mode);
end;
$$;

drop function public.admin_create_artist(text);
create function public.admin_create_artist(artist_name text, artist_email text default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare artist public.artists; normalized_email text; resolved_user_id uuid;
begin
  if not (select private.has_staff_permission('catalog.draft')) then
    raise insufficient_privilege using message = 'Catalog drafting is not permitted';
  end if;
  artist_name := btrim(coalesce(artist_name, ''));
  normalized_email := nullif(lower(btrim(coalesce(artist_email, ''))), '');
  if char_length(artist_name) not between 1 and 120 or
    (normalized_email is not null and (char_length(normalized_email) > 254 or normalized_email !~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$')) then
    raise invalid_parameter_value using message = 'Artist profile is invalid';
  end if;
  if normalized_email is not null then
    select id into resolved_user_id from auth.users where lower(email) = normalized_email limit 1;
  end if;
  insert into public.artists (name, created_by) values (artist_name, (select auth.uid())) returning * into artist;
  insert into public.artist_account_links (artist_id, user_id, invited_email, status, assigned_by)
  values (artist.id, resolved_user_id, normalized_email,
    case when normalized_email is null then 'unclaimed' else 'pending_consent' end, (select auth.uid()));
  perform private.write_staff_audit('catalog.artist.created', 'artist', artist.id,
    jsonb_build_object('accountLink', normalized_email is not null));
  return jsonb_build_object('id', artist.id, 'name', artist.name, 'published', false,
    'accountStatus', case when normalized_email is null then 'unclaimed' else 'pending_consent' end);
end;
$$;

create or replace function public.record_profile_avatar(
  media_object_key text, media_size_bytes integer, media_content_type text, media_etag text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare actor_id uuid := (select auth.uid()); expected_key text;
begin
  if actor_id is null then raise insufficient_privilege using message = 'Authentication required'; end if;
  expected_key := 'media/profiles/' || actor_id::text || '/avatar.webp';
  if media_object_key <> expected_key or media_content_type <> 'image/webp' or media_size_bytes not between 12 and 5242880 then
    raise invalid_parameter_value using message = 'Profile image is invalid';
  end if;
  insert into public.profile_avatar_assets(user_id, object_key, content_type, size_bytes, etag)
  values(actor_id, expected_key, media_content_type, media_size_bytes, left(media_etag, 160))
  on conflict(user_id) do update set size_bytes = excluded.size_bytes, etag = excluded.etag, uploaded_at = now();
  update public.profiles set avatar_object_key = expected_key where id = actor_id;
  return jsonb_build_object('avatarAvailable', true);
end;
$$;

create or replace function public.delete_profile_avatar()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare actor_id uuid := (select auth.uid());
begin
  if actor_id is null then raise insufficient_privilege using message = 'Authentication required'; end if;
  delete from public.profile_avatar_assets where user_id = actor_id;
  update public.profiles set avatar_object_key = null where id = actor_id;
  return jsonb_build_object('avatarAvailable', false);
end;
$$;

create or replace function public.admin_record_track_media(
  target_track_id uuid, media_quality text, media_object_key text, media_size_bytes bigint,
  media_etag text, media_content_type text, media_format text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare expected_name text;
begin
  if not (select private.has_staff_permission('catalog.upload_audio')) then
    raise insufficient_privilege using message = 'Audio upload is not permitted';
  end if;
  if media_quality not in ('low', 'standard', 'high') or
    (media_content_type, media_format) not in (
      ('audio/mpeg', 'mp3'), ('audio/aac', 'aac'), ('audio/mp4', 'm4a'),
      ('audio/webm', 'webm'), ('audio/wav', 'wav'), ('audio/flac', 'flac')
    ) or not exists(select 1 from public.tracks where id = target_track_id) then
    raise invalid_parameter_value using message = 'Audio metadata is invalid';
  end if;
  expected_name := case media_quality when 'low' then 'source-low.' || media_format
    when 'high' then 'source-high.' || media_format else 'source.' || media_format end;
  if media_object_key <> 'media/tracks/' || target_track_id::text || '/' || expected_name then
    raise invalid_parameter_value using message = 'Audio object key is invalid';
  end if;
  insert into public.track_media_variants(track_id, quality, object_key, size_bytes, etag,
    uploaded_by, content_type, media_format)
  values(target_track_id, media_quality, media_object_key, media_size_bytes, left(media_etag, 160),
    (select auth.uid()), media_content_type, media_format)
  on conflict(track_id, quality) do update set object_key = excluded.object_key,
    size_bytes = excluded.size_bytes, etag = excluded.etag, uploaded_by = excluded.uploaded_by,
    content_type = excluded.content_type, media_format = excluded.media_format, uploaded_at = now();
  perform private.write_staff_audit('catalog.audio.uploaded', 'track', target_track_id,
    jsonb_build_object('quality', media_quality, 'format', media_format, 'sizeBytes', media_size_bytes));
  return jsonb_build_object('trackId', target_track_id, 'quality', media_quality,
    'format', media_format, 'sizeBytes', media_size_bytes);
end;
$$;

create or replace function public.admin_upsert_editorial_shelf(
  target_shelf_id uuid, shelf_title text, shelf_subtitle text, shelf_position integer,
  target_track_id uuid, published boolean
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare saved public.editorial_shelves;
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
  insert into public.editorial_shelves(id, title, subtitle, position, is_published, featured_track_id)
  values(coalesce(target_shelf_id, gen_random_uuid()), shelf_title, shelf_subtitle, shelf_position,
    published, target_track_id)
  on conflict(id) do update set title = excluded.title, subtitle = excluded.subtitle,
    position = excluded.position, is_published = excluded.is_published,
    featured_track_id = excluded.featured_track_id, updated_at = now()
  returning * into saved;
  perform private.write_staff_audit('editorial.recommendation.saved', 'editorial_shelf', saved.id,
    jsonb_build_object('position', saved.position, 'published', saved.is_published));
  return jsonb_build_object('id', saved.id, 'title', saved.title, 'position', saved.position,
    'published', saved.is_published, 'featuredTrackId', saved.featured_track_id);
end;
$$;

revoke execute on function public.get_my_profile_context() from public, anon;
revoke execute on function public.accept_artist_terms(text) from public, anon;
revoke execute on function public.artist_update_biography(text) from public, anon;
revoke execute on function public.update_profile_appearance(text) from public, anon;
revoke execute on function public.admin_create_artist(text, text) from public, anon;
revoke execute on function public.record_profile_avatar(text, integer, text, text) from public, anon;
revoke execute on function public.delete_profile_avatar() from public, anon;
revoke execute on function public.admin_record_track_media(uuid, text, text, bigint, text, text, text) from public, anon;
revoke execute on function public.admin_upsert_editorial_shelf(uuid, text, text, integer, uuid, boolean) from public, anon;

grant execute on function public.get_my_profile_context() to authenticated;
grant execute on function public.accept_artist_terms(text) to authenticated;
grant execute on function public.artist_update_biography(text) to authenticated;
grant execute on function public.update_profile_appearance(text) to authenticated;
grant execute on function public.admin_create_artist(text, text) to authenticated;
grant execute on function public.record_profile_avatar(text, integer, text, text) to authenticated;
grant execute on function public.delete_profile_avatar() to authenticated;
grant execute on function public.admin_record_track_media(uuid, text, text, bigint, text, text, text) to authenticated;
grant execute on function public.admin_upsert_editorial_shelf(uuid, text, text, integer, uuid, boolean) to authenticated;

comment on table public.artist_account_links is
  'Optional exact-email Artist account binding. Email delivery and additional email verification are intentionally not required.';
comment on table public.artist_terms_consents is
  'Immutable, versioned evidence required before Artist privileges become active.';
comment on column public.profiles.appearance_mode is
  'A user-controlled presentation preference; role and verification state always remain server-authoritative.';

commit;
