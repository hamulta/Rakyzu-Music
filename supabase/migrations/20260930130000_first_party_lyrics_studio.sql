begin;

insert into public.staff_role_permissions (role, permission) values
  ('manager', 'lyrics.manage'),
  ('c_level_executive', 'lyrics.manage'),
  ('ceo', 'lyrics.manage')
on conflict do nothing;

alter table public.track_contexts
  add column lyrics_source_format text not null default 'manual'
    check (lyrics_source_format in ('manual', 'lrc', 'srt')),
  add column lyrics_language text
    check (lyrics_language is null or lyrics_language ~ '^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$'),
  add column lyrics_status text not null default 'draft'
    check (lyrics_status in ('draft', 'published')),
  add column lyrics_updated_by uuid references auth.users (id) on delete set null,
  add column lyrics_published_at timestamptz;

update public.track_contexts
set lyrics_source_format = 'manual',
    lyrics_status = case when lyrics_kind = 'unavailable' then 'draft' else 'published' end,
    lyrics_provider_name = case when lyrics_kind = 'unavailable' then null else 'Rakyzu Music' end,
    lyrics_provider_notice = case when lyrics_kind = 'unavailable' then null
      else 'First-party lyrics maintained in Rakyzu Music.' end,
    lyrics_published_at = case when lyrics_kind = 'unavailable' then null else updated_at end;

alter table public.track_contexts add constraint track_contexts_lyrics_publication_consistency check (
  (lyrics_status = 'draft') or
  (lyrics_status = 'published' and lyrics_kind <> 'unavailable' and lyrics_published_at is not null)
);

create table public.track_lyrics_revisions (
  id bigint generated always as identity primary key,
  track_id uuid not null references public.tracks (id) on delete cascade,
  revision uuid not null,
  action text not null check (action in ('save', 'delete')),
  lyrics_kind text not null check (lyrics_kind in ('unavailable', 'plain', 'time_synced')),
  source_format text not null check (source_format in ('manual', 'lrc', 'srt')),
  language text check (language is null or language ~ '^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$'),
  status text not null check (status in ('draft', 'published')),
  lines jsonb not null check (jsonb_typeof(lines) = 'array' and jsonb_array_length(lines) <= 2000),
  changed_by uuid not null references auth.users (id) on delete restrict,
  created_at timestamptz not null default now()
);

create index track_lyrics_revisions_track_created_idx
on public.track_lyrics_revisions (track_id, created_at desc, id desc);

alter table public.track_lyrics_revisions enable row level security;
alter table public.track_lyrics_revisions force row level security;
revoke all on public.track_lyrics_revisions from public, anon, authenticated;
grant all on public.track_lyrics_revisions to service_role;

create or replace function private.valid_track_lyrics(
  requested_kind text,
  requested_lines jsonb
) returns boolean
language plpgsql
immutable
set search_path = ''
as $$
declare invalid_line boolean;
begin
  if requested_kind not in ('plain', 'time_synced') or
    jsonb_typeof(requested_lines) <> 'array' or
    jsonb_array_length(requested_lines) not between 1 and 2000 then
    return false;
  end if;

  select coalesce(bool_or(
    jsonb_typeof(line.value) <> 'object' or
    jsonb_typeof(line.value -> 'text') <> 'string' or
    char_length(btrim(line.value ->> 'text')) not between 1 and 500 or
    (requested_kind = 'time_synced' and (
      jsonb_typeof(line.value -> 'startTimeMs') <> 'number' or
      coalesce(line.value ->> 'startTimeMs', '') !~ '^[0-9]{1,8}$'
    ))
  ), false) into invalid_line
  from jsonb_array_elements(requested_lines) as line(value);

  if invalid_line then return false; end if;
  if requested_kind = 'time_synced' and exists (
    select 1 from (
      select (line.value ->> 'startTimeMs')::bigint as current_timestamp_ms,
        lag((line.value ->> 'startTimeMs')::bigint) over (order by line.ordinality) as previous_timestamp_ms
      from jsonb_array_elements(requested_lines) with ordinality as line(value, ordinality)
    ) ordered where current_timestamp_ms > 86400000 or
      (previous_timestamp_ms is not null and current_timestamp_ms < previous_timestamp_ms)
  ) then return false; end if;
  return true;
end;
$$;

create or replace function private.can_manage_track_lyrics(target_track_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select (select private.has_staff_permission('lyrics.manage')) or
    public.artist_can_edit_track(target_track_id)
$$;

create or replace function public.get_editable_track_lyrics(target_track_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare context_row public.track_contexts%rowtype;
begin
  if (select auth.uid()) is null then
    raise insufficient_privilege using message = 'Authentication required';
  end if;
  if not private.can_manage_track_lyrics(target_track_id) then
    raise insufficient_privilege using message = 'Lyrics management access is required';
  end if;
  select * into context_row from public.track_contexts where track_id = target_track_id;
  if not found then
    return jsonb_build_object(
      'trackId', target_track_id,
      'kind', 'unavailable',
      'sourceFormat', 'manual',
      'language', null,
      'status', 'draft',
      'lines', '[]'::jsonb,
      'revision', null
    );
  end if;
  return jsonb_build_object(
    'trackId', context_row.track_id,
    'kind', context_row.lyrics_kind,
    'sourceFormat', context_row.lyrics_source_format,
    'language', context_row.lyrics_language,
    'status', context_row.lyrics_status,
    'lines', context_row.lyrics_lines,
    'revision', context_row.revision
  );
end;
$$;

create or replace function public.upsert_track_lyrics(
  target_track_id uuid,
  requested_kind text,
  requested_source_format text,
  requested_language text,
  requested_lines jsonb,
  requested_published boolean default false
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare saved public.track_contexts%rowtype;
declare normalized_language text := nullif(btrim(coalesce(requested_language, '')), '');
begin
  if (select auth.uid()) is null then
    raise insufficient_privilege using message = 'Authentication required';
  end if;
  if not private.can_manage_track_lyrics(target_track_id) then
    raise insufficient_privilege using message = 'Lyrics management access is required';
  end if;
  if requested_published and not (select private.has_staff_permission('lyrics.manage')) then
    raise insufficient_privilege using message = 'Staff lyrics publication access is required';
  end if;
  if requested_source_format not in ('manual', 'lrc', 'srt') or
    (requested_source_format = 'manual' and requested_kind <> 'plain') or
    (requested_source_format in ('lrc', 'srt') and requested_kind <> 'time_synced') or
    (normalized_language is not null and normalized_language !~ '^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$') or
    not private.valid_track_lyrics(requested_kind, requested_lines) then
    raise invalid_parameter_value using message = 'Lyrics payload is invalid';
  end if;
  if not exists (select 1 from public.tracks where id = target_track_id) then
    raise invalid_parameter_value using message = 'Track does not exist';
  end if;

  insert into public.track_contexts (
    track_id, lyrics_kind, lyrics_lines, lyrics_provider_name, lyrics_provider_notice,
    lyrics_source_format, lyrics_language, lyrics_status, lyrics_updated_by, lyrics_published_at
  ) values (
    target_track_id, requested_kind, requested_lines, 'Rakyzu Music',
    'First-party lyrics maintained in Rakyzu Music.', requested_source_format,
    normalized_language, case when requested_published then 'published' else 'draft' end,
    (select auth.uid()), case when requested_published then now() else null end
  ) on conflict (track_id) do update set
    lyrics_kind = excluded.lyrics_kind,
    lyrics_lines = excluded.lyrics_lines,
    lyrics_provider_name = excluded.lyrics_provider_name,
    lyrics_provider_notice = excluded.lyrics_provider_notice,
    lyrics_source_format = excluded.lyrics_source_format,
    lyrics_language = excluded.lyrics_language,
    lyrics_status = excluded.lyrics_status,
    lyrics_updated_by = excluded.lyrics_updated_by,
    lyrics_published_at = excluded.lyrics_published_at
  returning * into saved;

  insert into public.track_lyrics_revisions (
    track_id, revision, action, lyrics_kind, source_format, language, status, lines, changed_by
  ) values (
    saved.track_id, saved.revision, 'save', saved.lyrics_kind, saved.lyrics_source_format,
    saved.lyrics_language, saved.lyrics_status, saved.lyrics_lines, (select auth.uid())
  );
  perform private.write_staff_audit('lyrics.saved', 'track', target_track_id,
    jsonb_build_object('format', saved.lyrics_source_format, 'status', saved.lyrics_status,
      'lineCount', jsonb_array_length(saved.lyrics_lines)));
  return public.get_editable_track_lyrics(target_track_id);
end;
$$;

create or replace function public.delete_track_lyrics(target_track_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare saved public.track_contexts%rowtype;
begin
  if (select auth.uid()) is null then
    raise insufficient_privilege using message = 'Authentication required';
  end if;
  if not private.can_manage_track_lyrics(target_track_id) then
    raise insufficient_privilege using message = 'Lyrics management access is required';
  end if;
  update public.track_contexts set
    lyrics_kind = 'unavailable', lyrics_lines = '[]'::jsonb,
    lyrics_provider_name = null, lyrics_provider_notice = null,
    lyrics_source_format = 'manual', lyrics_language = null, lyrics_status = 'draft',
    lyrics_updated_by = (select auth.uid()), lyrics_published_at = null
  where track_id = target_track_id returning * into saved;
  if not found then
    raise invalid_parameter_value using message = 'Lyrics do not exist';
  end if;
  insert into public.track_lyrics_revisions (
    track_id, revision, action, lyrics_kind, source_format, language, status, lines, changed_by
  ) values (
    saved.track_id, saved.revision, 'delete', 'unavailable', 'manual', null, 'draft',
    '[]'::jsonb, (select auth.uid())
  );
  perform private.write_staff_audit('lyrics.deleted', 'track', target_track_id, '{}'::jsonb);
  return public.get_editable_track_lyrics(target_track_id);
end;
$$;

create or replace function public.get_track_context(
  requested_track_id uuid,
  requested_country_code text default null
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  context_row public.track_contexts%rowtype;
  track_artist_name text;
  normalized_country text;
  track_available boolean;
  lyrics_payload jsonb;
begin
  if (select auth.uid()) is null then
    raise insufficient_privilege using message = 'Authentication required';
  end if;
  normalized_country := nullif(upper(trim(coalesce(requested_country_code, ''))), '');
  if normalized_country is not null and normalized_country !~ '^[A-Z]{2}$' then
    raise invalid_parameter_value using message = 'Invalid country code';
  end if;

  select artist.name,
    track.is_published and album.is_published and artist.is_published and
      album.archived_at is null and artist.archived_at is null and
      private.is_content_available('track', track.id) and
      private.album_is_available(album.id) and private.artist_is_available(artist.id)
  into track_artist_name, track_available
  from public.tracks track
  join public.albums album on album.id = track.album_id
  join public.artists artist on artist.id = album.artist_id
  where track.id = requested_track_id;

  if coalesce(track_available, false) is false then
    raise invalid_parameter_value using message = 'Track unavailable';
  end if;

  select * into context_row from public.track_contexts
  where track_id = requested_track_id and enabled;

  if not found or (
    context_row.allowed_country_codes is not null and
    (normalized_country is null or not exists (
      select 1 from unnest(context_row.allowed_country_codes) as allowed_country_code
      where upper(trim(allowed_country_code)) = normalized_country
    ))
  ) then
    return jsonb_build_object(
      'trackId', requested_track_id,
      'revision', coalesce(context_row.revision::text, 'unavailable-v1'),
      'cacheTtlSeconds', 3600,
      'lyrics', jsonb_build_object('kind', 'unavailable', 'lines', '[]'::jsonb),
      'credits', jsonb_build_array(jsonb_build_object(
        'displayName', track_artist_name, 'role', 'primary_artist', 'sourceName', 'Rakyzu Music'
      ))
    );
  end if;

  lyrics_payload := case when context_row.lyrics_status = 'published' then
    jsonb_build_object(
      'kind', context_row.lyrics_kind,
      'lines', context_row.lyrics_lines,
      'sourceName', 'Rakyzu Music',
      'providerName', 'Rakyzu Music',
      'providerNotice', 'First-party lyrics maintained in Rakyzu Music.'
    ) else jsonb_build_object('kind', 'unavailable', 'lines', '[]'::jsonb) end;

  return jsonb_build_object(
    'trackId', requested_track_id,
    'revision', context_row.revision::text,
    'cacheTtlSeconds', 86400,
    'lyrics', lyrics_payload,
    'credits', case when jsonb_array_length(context_row.credits) = 0 then
      jsonb_build_array(jsonb_build_object(
        'displayName', track_artist_name, 'role', 'primary_artist', 'sourceName', 'Rakyzu Music'
      )) else context_row.credits end
  );
end;
$$;

revoke all on function private.valid_track_lyrics(text, jsonb),
  private.can_manage_track_lyrics(uuid) from public, anon, authenticated;
revoke all on function public.get_editable_track_lyrics(uuid),
  public.upsert_track_lyrics(uuid, text, text, text, jsonb, boolean),
  public.delete_track_lyrics(uuid) from public, anon;
grant execute on function public.get_editable_track_lyrics(uuid),
  public.upsert_track_lyrics(uuid, text, text, text, jsonb, boolean),
  public.delete_track_lyrics(uuid) to authenticated;

comment on table public.track_lyrics_revisions is
  'Immutable first-party Rakyzu Music lyrics history; no external lyrics provider is queried.';

commit;
