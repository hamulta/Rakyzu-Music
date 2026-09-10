begin;

alter table public.playlists
  add column visibility text not null default 'private'
  check (visibility in ('private', 'public'));

create table public.playlist_members (
  playlist_id uuid not null references public.playlists(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null check (role in ('editor', 'viewer')),
  joined_at timestamptz not null default now(),
  primary key (playlist_id, user_id)
);
create index playlist_members_user_joined_idx
on public.playlist_members(user_id, joined_at desc, playlist_id);

create table public.playlist_invites (
  token uuid primary key default gen_random_uuid(),
  playlist_id uuid not null references public.playlists(id) on delete cascade,
  role text not null check (role in ('editor', 'viewer')),
  created_by uuid not null references auth.users(id) on delete cascade,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  accepted_by uuid references auth.users(id) on delete set null,
  accepted_at timestamptz,
  created_at timestamptz not null default now(),
  check (expires_at > created_at),
  check ((accepted_by is null) = (accepted_at is null))
);
create index playlist_invites_active_idx
on public.playlist_invites(playlist_id, expires_at)
where revoked_at is null and accepted_at is null;

create table public.playlist_follows (
  playlist_id uuid not null references public.playlists(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  followed_at timestamptz not null default now(),
  primary key (playlist_id, user_id)
);
create index playlist_follows_user_idx
on public.playlist_follows(user_id, followed_at desc, playlist_id);

create table public.playlist_mutation_receipts (
  user_id uuid not null references auth.users(id) on delete cascade,
  operation_id uuid not null,
  playlist_id uuid not null references public.playlists(id) on delete cascade,
  result jsonb not null,
  created_at timestamptz not null default now(),
  primary key (user_id, operation_id)
);
create index playlist_mutation_receipts_playlist_idx
on public.playlist_mutation_receipts(playlist_id, created_at desc);

alter table public.playlist_members enable row level security;
alter table public.playlist_members force row level security;
alter table public.playlist_invites enable row level security;
alter table public.playlist_invites force row level security;
alter table public.playlist_follows enable row level security;
alter table public.playlist_follows force row level security;
alter table public.playlist_mutation_receipts enable row level security;
alter table public.playlist_mutation_receipts force row level security;

revoke all on public.playlist_members from public, anon, authenticated;
revoke all on public.playlist_invites from public, anon, authenticated;
revoke all on public.playlist_follows from public, anon, authenticated;
revoke all on public.playlist_mutation_receipts from public, anon, authenticated;
grant all on public.playlist_members to service_role;
grant all on public.playlist_invites to service_role;
grant all on public.playlist_follows to service_role;
grant all on public.playlist_mutation_receipts to service_role;

create function public.playlist_role(requested_playlist_id uuid, requested_user_id uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select case
    when playlist.owner_id = requested_user_id then 'owner'
    else (
      select member.role
      from public.playlist_members member
      where member.playlist_id = playlist.id and member.user_id = requested_user_id
    )
  end
  from public.playlists playlist
  where playlist.id = requested_playlist_id;
$$;

create function public.playlist_can_view(requested_playlist_id uuid, requested_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.playlists playlist
    where playlist.id = requested_playlist_id
      and requested_user_id is not null
      and (
        playlist.owner_id = requested_user_id
        or playlist.visibility = 'public'
        or exists (
          select 1 from public.playlist_members member
          where member.playlist_id = playlist.id and member.user_id = requested_user_id
        )
      )
  );
$$;

create function public.playlist_can_edit(requested_playlist_id uuid, requested_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(public.playlist_role(requested_playlist_id, requested_user_id)
    in ('owner', 'editor'), false);
$$;

revoke all on function public.playlist_role(uuid, uuid) from public, anon;
revoke all on function public.playlist_can_view(uuid, uuid) from public, anon;
revoke all on function public.playlist_can_edit(uuid, uuid) from public, anon;
grant execute on function public.playlist_role(uuid, uuid) to authenticated, service_role;
grant execute on function public.playlist_can_view(uuid, uuid) to authenticated, service_role;
grant execute on function public.playlist_can_edit(uuid, uuid) to authenticated, service_role;

create policy playlists_select_accessible on public.playlists
for select to authenticated
using (public.playlist_can_view(id, (select auth.uid())));

create policy playlist_items_read_accessible on public.playlist_items
for select to authenticated
using (public.playlist_can_view(playlist_id, (select auth.uid())));

create function public.get_accessible_playlists(
  page_size integer default 30,
  before_updated_at timestamptz default null,
  before_id uuid default null
)
returns table (
  id uuid,
  owner_id uuid,
  name text,
  description text,
  track_count integer,
  revision bigint,
  visibility text,
  access_role text,
  is_following boolean,
  created_at timestamptz,
  updated_at timestamptz,
  has_more boolean
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  bounded_size integer := greatest(1, least(coalesce(page_size, 30), 50));
  current_user_id uuid := auth.uid();
begin
  if current_user_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if (before_updated_at is null) <> (before_id is null) then
    raise exception 'Incomplete playlist cursor' using errcode = '22023';
  end if;

  return query
  with candidates as (
    select
      playlist.id,
      playlist.owner_id,
      playlist.name,
      playlist.description,
      playlist.track_count,
      playlist.revision,
      playlist.visibility,
      case
        when playlist.owner_id = current_user_id then 'Owner'
        when member.role = 'editor' then 'Editor'
        when member.role = 'viewer' then 'Viewer'
        else 'Follower'
      end as access_role,
      (follow.user_id is not null) as is_following,
      playlist.created_at,
      playlist.updated_at
    from public.playlists playlist
    left join public.playlist_members member
      on member.playlist_id = playlist.id and member.user_id = current_user_id
    left join public.playlist_follows follow
      on follow.playlist_id = playlist.id and follow.user_id = current_user_id
    where (
      playlist.owner_id = current_user_id
      or member.user_id is not null
      or (playlist.visibility = 'public' and follow.user_id is not null)
    )
      and (
        before_updated_at is null
        or (playlist.updated_at, playlist.id) < (before_updated_at, before_id)
      )
    order by playlist.updated_at desc, playlist.id desc
    limit bounded_size + 1
  ),
  numbered as (
    select candidates.*, row_number() over () as row_number, count(*) over () as candidate_count
    from candidates
  )
  select
    numbered.id,
    numbered.owner_id,
    numbered.name,
    numbered.description,
    numbered.track_count,
    numbered.revision,
    numbered.visibility,
    numbered.access_role,
    numbered.is_following,
    numbered.created_at,
    numbered.updated_at,
    numbered.candidate_count > bounded_size
  from numbered
  where numbered.row_number <= bounded_size
  order by numbered.updated_at desc, numbered.id desc;
end;
$$;

create function public.get_playlist_detail_page(
  playlist_id uuid,
  page_offset integer default 0,
  page_size integer default 100
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  playlist public.playlists;
  current_user_id uuid := auth.uid();
  bounded_offset integer := greatest(0, coalesce(page_offset, 0));
  bounded_size integer := greatest(1, least(coalesce(page_size, 100), 100));
  item_rows jsonb;
  member_rows jsonb;
  returned_count integer;
  role_name text;
  following boolean;
begin
  if not public.playlist_can_view(playlist_id, current_user_id) then
    raise exception 'Playlist unavailable' using errcode = '42501';
  end if;
  if bounded_offset > 500 then
    raise exception 'Invalid playlist offset' using errcode = '22023';
  end if;

  select * into playlist from public.playlists where id = playlist_id;
  role_name := public.playlist_role(playlist.id, current_user_id);
  following := exists (
    select 1 from public.playlist_follows follow
    where follow.playlist_id = playlist.id and follow.user_id = current_user_id
  );

  select
    coalesce(jsonb_agg(entry.payload order by entry.position), '[]'::jsonb),
    count(*)::integer
  into item_rows, returned_count
  from (
    select
      item.position,
      jsonb_build_object(
        'trackId', item.track_id,
        'track', case
          when track.is_published and album.is_published and artist.is_published then
            jsonb_build_object(
              'id', track.id,
              'title', track.title,
              'artist', artist.name,
              'durationMs', track.duration_ms,
              'artistId', artist.id,
              'albumId', album.id,
              'albumTitle', album.title,
              'discNumber', track.disc_number,
              'trackNumber', track.track_number,
              'isExplicit', track.is_explicit
            )
          else null
        end
      ) as payload
    from public.playlist_items item
    join public.tracks track on track.id = item.track_id
    join public.albums album on album.id = track.album_id
    join public.artists artist on artist.id = album.artist_id
    where item.playlist_id = playlist.id
    order by item.position
    offset bounded_offset
    limit bounded_size
  ) entry;

  select coalesce(jsonb_agg(member.payload order by member.sort_order, member.display_name), '[]'::jsonb)
  into member_rows
  from (
    select 0 as sort_order, profile.display_name,
      jsonb_build_object(
        'userId', playlist.owner_id,
        'displayName', profile.display_name,
        'role', 'Owner',
        'isCurrentUser', playlist.owner_id = current_user_id
      ) as payload
    from public.profiles profile where profile.id = playlist.owner_id
    union all
    select 1, profile.display_name,
      jsonb_build_object(
        'userId', collaborator.user_id,
        'displayName', profile.display_name,
        'role', case collaborator.role when 'editor' then 'Editor' else 'Viewer' end,
        'isCurrentUser', collaborator.user_id = current_user_id
      )
    from public.playlist_members collaborator
    join public.profiles profile on profile.id = collaborator.user_id
    where collaborator.playlist_id = playlist.id
  ) member;

  return jsonb_build_object(
    'playlist', jsonb_build_object(
      'id', playlist.id,
      'ownerId', playlist.owner_id,
      'name', playlist.name,
      'description', playlist.description,
      'trackCount', playlist.track_count,
      'revision', playlist.revision,
      'visibility', case playlist.visibility when 'public' then 'Public' else 'Private' end,
      'accessRole', case
        when role_name = 'owner' then 'Owner'
        when role_name = 'editor' then 'Editor'
        when role_name = 'viewer' then 'Viewer'
        else 'Follower'
      end,
      'isFollowing', following,
      'createdAtEpochMillis', floor(extract(epoch from playlist.created_at) * 1000)::bigint,
      'updatedAtEpochMillis', floor(extract(epoch from playlist.updated_at) * 1000)::bigint
    ),
    'items', item_rows,
    'members', member_rows,
    'totalItems', playlist.track_count,
    'nextOffset', case
      when bounded_offset + returned_count < playlist.track_count
        then bounded_offset + returned_count
      else null
    end
  );
end;
$$;

create or replace function public.get_playlist_detail(playlist_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select public.get_playlist_detail_page(playlist_id, 0, 100);
$$;

create or replace function public.mutate_playlist(
  playlist_id uuid, expected_revision bigint, action text,
  track_id uuid default null, ordered_ids uuid[] default null,
  playlist_name text default null, playlist_description text default null
) returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare
  playlist public.playlists;
  ids uuid[];
  normalized_name text;
  changed boolean := false;
  current_user_id uuid := auth.uid();
begin
  select * into playlist from public.playlists where id = playlist_id for update;
  if not found or not public.playlist_can_edit(playlist_id, current_user_id) then
    raise exception 'Playlist unavailable' using errcode = '42501';
  end if;
  if expected_revision is null or playlist.revision <> expected_revision then
    raise exception 'Playlist revision conflict' using errcode = 'PT409';
  end if;
  select coalesce(array_agg(item.track_id order by item.position), '{}'::uuid[]) into ids
  from public.playlist_items item where item.playlist_id = playlist.id;

  if action = 'add' then
    if track_id is null or not exists (
      select 1 from public.tracks track
      join public.albums album on album.id = track.album_id
      join public.artists artist on artist.id = album.artist_id
      where track.id = mutate_playlist.track_id
        and track.is_published and album.is_published and artist.is_published
    ) then raise exception 'Track unavailable' using errcode = '22023'; end if;
    if not track_id = any(ids) then
      if cardinality(ids) >= 500 then
        raise exception 'Playlist limit reached' using errcode = '22023';
      end if;
      ids := array_append(ids, track_id);
      changed := true;
    end if;
  elsif action = 'remove' then
    if track_id is null then raise exception 'Track required' using errcode = '22023'; end if;
    changed := track_id = any(ids);
    ids := array_remove(ids, track_id);
  elsif action = 'reorder' then
    if ordered_ids is null or cardinality(ordered_ids) <> cardinality(ids)
      or exists (select 1 from unnest(ordered_ids) value where value is null)
      or (select count(distinct value) from unnest(ordered_ids) value) <> cardinality(ids)
      or not ordered_ids @> ids then
      raise exception 'Order must be an exact permutation' using errcode = '22023';
    end if;
    changed := ids <> ordered_ids;
    ids := ordered_ids;
  elsif action = 'metadata' then
    if playlist.owner_id <> current_user_id then
      raise exception 'Only the owner can edit playlist details' using errcode = '42501';
    end if;
    normalized_name := btrim(regexp_replace(playlist_name, '\s+', ' ', 'g'));
    if normalized_name is null or char_length(normalized_name) not between 1 and 100
      or playlist_description is null or char_length(btrim(playlist_description)) > 300 then
      raise exception 'Invalid playlist metadata' using errcode = '22023';
    end if;
    changed := playlist.name <> normalized_name
      or playlist.description <> btrim(playlist_description);
    playlist.name := normalized_name;
    playlist.description := btrim(playlist_description);
  else
    raise exception 'Invalid action' using errcode = '22023';
  end if;

  if changed then
    delete from public.playlist_items item where item.playlist_id = playlist.id;
    insert into public.playlist_items(playlist_id, track_id, position)
    select playlist.id, value, (ordinality - 1)::integer
    from unnest(ids) with ordinality as ordered(value, ordinality);
    update public.playlists
    set name = playlist.name,
        description = playlist.description,
        track_count = cardinality(ids),
        revision = playlist.revision + 1,
        updated_at = clock_timestamp()
    where id = playlist.id;
  end if;
  return public.get_playlist_detail_page(playlist.id, 0, 100);
end;
$$;

create function public.mutate_playlist_v2(
  playlist_id uuid,
  operation_id uuid,
  expected_revision bigint,
  action text,
  track_id uuid default null,
  ordered_ids uuid[] default null,
  playlist_name text default null,
  playlist_description text default null
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  prior_result jsonb;
  mutation_result jsonb;
begin
  if current_user_id is null or operation_id is null then
    raise exception 'Authentication and operation id required' using errcode = '22023';
  end if;
  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(current_user_id::text || ':' || operation_id::text, 0)
  );
  select receipt.result into prior_result
  from public.playlist_mutation_receipts receipt
  where receipt.user_id = current_user_id and receipt.operation_id = mutate_playlist_v2.operation_id;
  if found then return prior_result; end if;

  mutation_result := public.mutate_playlist(
    playlist_id, expected_revision, action, track_id, ordered_ids,
    playlist_name, playlist_description
  );
  insert into public.playlist_mutation_receipts(user_id, operation_id, playlist_id, result)
  values (current_user_id, operation_id, playlist_id, mutation_result);
  return mutation_result;
end;
$$;

create function public.create_playlist_invite(
  playlist_id uuid,
  invite_role text default 'editor',
  valid_hours integer default 168
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  playlist public.playlists;
  invite_token uuid := gen_random_uuid();
  expiry timestamptz;
begin
  select * into playlist from public.playlists
  where id = playlist_id and owner_id = auth.uid() for update;
  if not found then raise exception 'Playlist unavailable' using errcode = '42501'; end if;
  if invite_role not in ('editor', 'viewer') or valid_hours not between 1 and 168 then
    raise exception 'Invalid invite' using errcode = '22023';
  end if;
  if (
    select count(*) from public.playlist_invites invite
    where invite.playlist_id = playlist.id and invite.revoked_at is null
      and invite.accepted_at is null and invite.expires_at > now()
  ) >= 20 then
    raise exception 'Active invite limit reached' using errcode = '22023';
  end if;
  expiry := now() + make_interval(hours => valid_hours);
  insert into public.playlist_invites(token, playlist_id, role, created_by, expires_at)
  values (invite_token, playlist.id, invite_role, auth.uid(), expiry);
  return jsonb_build_object(
    'token', invite_token,
    'role', case invite_role when 'editor' then 'Editor' else 'Viewer' end,
    'expiresAtEpochMillis', floor(extract(epoch from expiry) * 1000)::bigint
  );
end;
$$;

create function public.accept_playlist_invite(invite_token uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  invite public.playlist_invites;
  playlist public.playlists;
  current_user_id uuid := auth.uid();
begin
  if current_user_id is null then raise exception 'Authentication required' using errcode = '42501'; end if;
  select * into invite from public.playlist_invites
  where token = invite_token for update;
  if not found or invite.revoked_at is not null or invite.accepted_at is not null
    or invite.expires_at <= now() then
    raise exception 'Invite is invalid or expired' using errcode = '22023';
  end if;
  select * into playlist from public.playlists where id = invite.playlist_id for update;
  if playlist.owner_id = current_user_id then
    raise exception 'Owner cannot accept an invite' using errcode = '22023';
  end if;
  if not exists (
    select 1 from public.playlist_members member
    where member.playlist_id = playlist.id and member.user_id = current_user_id
  ) and (
    select count(*) from public.playlist_members member where member.playlist_id = playlist.id
  ) >= 99 then
    raise exception 'Collaborator limit reached' using errcode = '22023';
  end if;

  insert into public.playlist_members(playlist_id, user_id, role)
  values (playlist.id, current_user_id, invite.role)
  on conflict (playlist_id, user_id)
  do update set role = excluded.role, joined_at = now();
  update public.playlist_invites
  set accepted_by = current_user_id, accepted_at = now()
  where token = invite.token;
  update public.playlists
  set revision = revision + 1, updated_at = clock_timestamp()
  where id = playlist.id;
  return public.get_playlist_detail_page(playlist.id, 0, 100);
end;
$$;

create function public.revoke_playlist_invite(playlist_id uuid, invite_token uuid)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
  if not exists (
    select 1 from public.playlists playlist
    where playlist.id = playlist_id and playlist.owner_id = auth.uid()
  ) then raise exception 'Playlist unavailable' using errcode = '42501'; end if;
  update public.playlist_invites invite set revoked_at = now()
  where invite.token = invite_token and invite.playlist_id = revoke_playlist_invite.playlist_id
    and invite.accepted_at is null and invite.revoked_at is null;
end;
$$;

create function public.remove_playlist_member(playlist_id uuid, member_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  playlist public.playlists;
begin
  select * into playlist from public.playlists
  where id = playlist_id and owner_id = auth.uid() for update;
  if not found then raise exception 'Playlist unavailable' using errcode = '42501'; end if;
  delete from public.playlist_members member
  where member.playlist_id = playlist.id and member.user_id = member_id;
  if found then
    update public.playlists set revision = revision + 1, updated_at = clock_timestamp()
    where id = playlist.id;
  end if;
  return public.get_playlist_detail_page(playlist.id, 0, 100);
end;
$$;

create function public.leave_playlist(playlist_id uuid)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
  if exists (
    select 1 from public.playlists playlist
    where playlist.id = $1 and playlist.owner_id = auth.uid()
  ) then raise exception 'Owner cannot leave their playlist' using errcode = '22023'; end if;
  delete from public.playlist_members member
  where member.playlist_id = $1 and member.user_id = auth.uid();
end;
$$;

create function public.set_playlist_visibility(
  playlist_id uuid,
  expected_revision bigint,
  requested_visibility text
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  playlist public.playlists;
begin
  select * into playlist from public.playlists
  where id = playlist_id and owner_id = auth.uid() for update;
  if not found then raise exception 'Playlist unavailable' using errcode = '42501'; end if;
  if playlist.revision <> expected_revision then
    raise exception 'Playlist revision conflict' using errcode = 'PT409';
  end if;
  if requested_visibility not in ('private', 'public') then
    raise exception 'Invalid visibility' using errcode = '22023';
  end if;
  if playlist.visibility <> requested_visibility then
    update public.playlists
    set visibility = requested_visibility, revision = revision + 1, updated_at = clock_timestamp()
    where id = playlist.id;
    if requested_visibility = 'private' then
      delete from public.playlist_follows follow where follow.playlist_id = playlist.id;
    end if;
  end if;
  return public.get_playlist_detail_page(playlist.id, 0, 100);
end;
$$;

create function public.set_playlist_following(playlist_id uuid, following boolean)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  playlist public.playlists;
  current_user_id uuid := auth.uid();
begin
  select * into playlist from public.playlists
  where id = playlist_id and visibility = 'public';
  if not found or current_user_id is null then
    raise exception 'Playlist unavailable' using errcode = '42501';
  end if;
  if playlist.owner_id = current_user_id
    or public.playlist_role(playlist.id, current_user_id) is not null then
    raise exception 'Members do not need to follow this playlist' using errcode = '22023';
  end if;
  if following then
    if not exists (
      select 1 from public.playlist_follows follow
      where follow.user_id = current_user_id and follow.playlist_id = playlist.id
    ) and (
      select count(*) from public.playlist_follows follow where follow.user_id = current_user_id
    ) >= 1000 then
      raise exception 'Follow limit reached' using errcode = '22023';
    end if;
    insert into public.playlist_follows(playlist_id, user_id)
    values (playlist.id, current_user_id) on conflict do nothing;
  else
    delete from public.playlist_follows follow
    where follow.playlist_id = playlist.id and follow.user_id = current_user_id;
  end if;
  return public.get_playlist_detail_page(playlist.id, 0, 100);
end;
$$;

create function public.get_playlist_artwork_access(playlist_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  playlist public.playlists;
  current_user_id uuid := auth.uid();
begin
  if not public.playlist_can_view(playlist_id, current_user_id) then
    raise exception 'Playlist unavailable' using errcode = '42501';
  end if;
  select * into playlist from public.playlists where id = playlist_id;
  return jsonb_build_object(
    'ownerId', playlist.owner_id,
    'canRead', true,
    'canEdit', playlist.owner_id = current_user_id
  );
end;
$$;

comment on table public.playlist_members is
  'Bounded playlist collaborators; owners remain canonical on playlists.owner_id.';
comment on table public.playlist_invites is
  'Single-use, expiring collaboration capabilities; tokens are never listable by clients.';
comment on table public.playlist_mutation_receipts is
  'Account-scoped idempotency receipts for durable client playlist mutations.';

revoke all on function public.get_accessible_playlists(integer, timestamptz, uuid) from public, anon;
revoke all on function public.get_playlist_detail_page(uuid, integer, integer) from public, anon;
revoke all on function public.get_playlist_detail(uuid) from public, anon;
revoke all on function public.mutate_playlist(uuid, bigint, text, uuid, uuid[], text, text) from public, anon;
revoke all on function public.mutate_playlist_v2(uuid, uuid, bigint, text, uuid, uuid[], text, text) from public, anon;
revoke all on function public.create_playlist_invite(uuid, text, integer) from public, anon;
revoke all on function public.accept_playlist_invite(uuid) from public, anon;
revoke all on function public.revoke_playlist_invite(uuid, uuid) from public, anon;
revoke all on function public.remove_playlist_member(uuid, uuid) from public, anon;
revoke all on function public.leave_playlist(uuid) from public, anon;
revoke all on function public.set_playlist_visibility(uuid, bigint, text) from public, anon;
revoke all on function public.set_playlist_following(uuid, boolean) from public, anon;
revoke all on function public.get_playlist_artwork_access(uuid) from public, anon;

grant execute on function public.get_accessible_playlists(integer, timestamptz, uuid) to authenticated;
grant execute on function public.get_playlist_detail_page(uuid, integer, integer) to authenticated;
grant execute on function public.get_playlist_detail(uuid) to authenticated;
grant execute on function public.mutate_playlist(uuid, bigint, text, uuid, uuid[], text, text) to authenticated;
grant execute on function public.mutate_playlist_v2(uuid, uuid, bigint, text, uuid, uuid[], text, text) to authenticated;
grant execute on function public.create_playlist_invite(uuid, text, integer) to authenticated;
grant execute on function public.accept_playlist_invite(uuid) to authenticated;
grant execute on function public.revoke_playlist_invite(uuid, uuid) to authenticated;
grant execute on function public.remove_playlist_member(uuid, uuid) to authenticated;
grant execute on function public.leave_playlist(uuid) to authenticated;
grant execute on function public.set_playlist_visibility(uuid, bigint, text) to authenticated;
grant execute on function public.set_playlist_following(uuid, boolean) to authenticated;
grant execute on function public.get_playlist_artwork_access(uuid) to authenticated;

commit;
