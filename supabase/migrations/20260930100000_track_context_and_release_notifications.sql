create table public.track_contexts (
  track_id uuid primary key references public.tracks (id) on delete cascade,
  lyrics_kind text not null default 'unavailable'
    check (lyrics_kind in ('unavailable', 'plain', 'time_synced')),
  lyrics_lines jsonb not null default '[]'::jsonb
    check (jsonb_typeof(lyrics_lines) = 'array' and jsonb_array_length(lyrics_lines) <= 2000),
  lyrics_provider_name text check (lyrics_provider_name is null or char_length(lyrics_provider_name) between 1 and 120),
  lyrics_provider_notice text check (lyrics_provider_notice is null or char_length(lyrics_provider_notice) between 1 and 240),
  credits jsonb not null default '[]'::jsonb
    check (jsonb_typeof(credits) = 'array' and jsonb_array_length(credits) <= 200),
  allowed_country_codes text[],
  revision uuid not null default gen_random_uuid(),
  enabled boolean not null default true,
  updated_at timestamptz not null default now(),
  check (lyrics_kind = 'unavailable' or jsonb_array_length(lyrics_lines) > 0),
  check (allowed_country_codes is null or cardinality(allowed_country_codes) between 1 and 250)
);

create table public.release_notification_preferences (
  user_id uuid primary key references auth.users (id) on delete cascade,
  preference text not null default 'off'
    check (preference in ('off', 'followed_artists', 'all_saved_artists')),
  updated_at timestamptz not null default now()
);

create table public.release_notification_deliveries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  album_id uuid not null references public.albums (id) on delete cascade,
  channel text not null default 'new_releases' check (channel = 'new_releases'),
  state text not null default 'pending' check (state in ('pending', 'sent', 'suppressed', 'failed')),
  available_at timestamptz not null default now(),
  delivered_at timestamptz,
  attempt_count integer not null default 0 check (attempt_count between 0 and 10),
  created_at timestamptz not null default now(),
  unique (user_id, album_id, channel)
);

create index release_notification_deliveries_pending_idx
on public.release_notification_deliveries (available_at, id)
where state = 'pending';

create or replace function private.refresh_track_context_revision()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  new.revision = gen_random_uuid();
  new.updated_at = now();
  return new;
end;
$$;

revoke all on function private.refresh_track_context_revision() from public, anon, authenticated;

create trigger track_contexts_refresh_revision
before update on public.track_contexts
for each row execute function private.refresh_track_context_revision();

alter table public.track_contexts enable row level security;
alter table public.track_contexts force row level security;
alter table public.release_notification_preferences enable row level security;
alter table public.release_notification_preferences force row level security;
alter table public.release_notification_deliveries enable row level security;
alter table public.release_notification_deliveries force row level security;

revoke all on public.track_contexts from anon, authenticated;
revoke all on public.release_notification_preferences from anon, authenticated;
revoke all on public.release_notification_deliveries from anon, authenticated;

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
    (
      normalized_country is null or
      not exists (
        select 1
        from unnest(context_row.allowed_country_codes) as allowed_country_code
        where upper(trim(allowed_country_code)) = normalized_country
      )
    )
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

  return jsonb_build_object(
    'trackId', requested_track_id,
    'revision', context_row.revision::text,
    'cacheTtlSeconds', 86400,
    'lyrics', jsonb_build_object(
      'kind', context_row.lyrics_kind,
      'lines', context_row.lyrics_lines,
      'providerName', context_row.lyrics_provider_name,
      'providerNotice', context_row.lyrics_provider_notice
    ),
    'credits', case when jsonb_array_length(context_row.credits) = 0 then
      jsonb_build_array(jsonb_build_object(
        'displayName', track_artist_name, 'role', 'primary_artist', 'sourceName', 'Rakyzu Music'
      )) else context_row.credits end
  );
end;
$$;

create or replace function public.get_release_notification_preference()
returns jsonb
language sql
security definer
set search_path = ''
stable
as $$
  select case when (select auth.uid()) is null then
    jsonb_build_object('preference', 'off', 'updatedAtEpochMillis', 0)
  else coalesce((
    select jsonb_build_object(
      'preference', preference,
      'updatedAtEpochMillis', (extract(epoch from updated_at) * 1000)::bigint
    )
    from public.release_notification_preferences
    where user_id = (select auth.uid())
  ), jsonb_build_object('preference', 'off', 'updatedAtEpochMillis', 0)) end;
$$;

create or replace function public.set_release_notification_preference(
  requested_preference text
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  saved public.release_notification_preferences%rowtype;
begin
  if (select auth.uid()) is null then
    raise insufficient_privilege using message = 'Authentication required';
  end if;
  if requested_preference not in ('off', 'followed_artists', 'all_saved_artists') then
    raise invalid_parameter_value using message = 'Invalid notification preference';
  end if;
  insert into public.release_notification_preferences(user_id, preference, updated_at)
  values ((select auth.uid()), requested_preference, now())
  on conflict(user_id) do update set preference = excluded.preference, updated_at = excluded.updated_at
  returning * into saved;
  if requested_preference = 'off' then
    update public.release_notification_deliveries set state = 'suppressed'
    where user_id = (select auth.uid()) and state = 'pending';
  end if;
  return jsonb_build_object(
    'preference', saved.preference,
    'updatedAtEpochMillis', (extract(epoch from saved.updated_at) * 1000)::bigint
  );
end;
$$;

revoke all on function public.get_track_context(uuid, text) from public, anon;
revoke all on function public.get_release_notification_preference() from public, anon;
revoke all on function public.set_release_notification_preference(text) from public, anon;
grant execute on function public.get_track_context(uuid, text) to authenticated;
grant execute on function public.get_release_notification_preference() to authenticated;
grant execute on function public.set_release_notification_preference(text) to authenticated;

comment on table public.track_contexts is
  'Licensed lyrics and normalized credits; listeners receive only rights-filtered RPC projections.';
comment on table public.release_notification_deliveries is
  'Server-owned, idempotent delivery queue; Android cannot enqueue or mark notification delivery.';
