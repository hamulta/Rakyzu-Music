begin;

create table public.playlists (
  id uuid primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  name text not null check (
    char_length(name) between 1 and 100
    and name = regexp_replace(btrim(name), '\s+', ' ', 'g')
  ),
  description text not null default '' check (char_length(description) <= 300),
  track_count integer not null default 0 check (track_count >= 0),
  revision bigint not null default 1 check (revision >= 1),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index playlists_owner_updated_idx
on public.playlists (owner_id, updated_at desc, id);

comment on table public.playlists is
  'Account-owned ordered collection metadata; revision is the optimistic concurrency boundary.';
comment on column public.playlists.revision is
  'Monotonic playlist snapshot revision used by later item ordering and collaboration mutations.';

alter table public.playlists enable row level security;
alter table public.playlists force row level security;

revoke all on table public.playlists from anon, authenticated;
grant select on table public.playlists to authenticated;
grant insert (id, owner_id, name, description) on table public.playlists to authenticated;
grant all on table public.playlists to service_role;

create policy playlists_select_own on public.playlists
for select to authenticated using ((select auth.uid()) = owner_id);

create policy playlists_insert_own on public.playlists
for insert to authenticated with check ((select auth.uid()) = owner_id);

create policy playlists_update_own on public.playlists
for update to authenticated
using ((select auth.uid()) = owner_id)
with check ((select auth.uid()) = owner_id);

create policy playlists_delete_own on public.playlists
for delete to authenticated using ((select auth.uid()) = owner_id);

create function public.get_my_playlists(page_size integer default 100)
returns table (
  id uuid,
  name text,
  description text,
  track_count integer,
  revision bigint,
  created_at timestamptz,
  updated_at timestamptz
)
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
  select
    playlist.id,
    playlist.name,
    playlist.description,
    playlist.track_count,
    playlist.revision,
    playlist.created_at,
    playlist.updated_at
  from public.playlists as playlist
  where playlist.owner_id = (select auth.uid())
  order by playlist.updated_at desc, playlist.id
  limit greatest(1, least(coalesce(page_size, 100), 100));
$$;

create function public.create_playlist(
  playlist_id uuid,
  playlist_name text,
  playlist_description text default ''
)
returns table (
  id uuid,
  name text,
  description text,
  track_count integer,
  revision bigint,
  created_at timestamptz,
  updated_at timestamptz
)
language plpgsql
volatile
security invoker
set search_path = public, pg_temp
as $$
declare
  current_user_id uuid := auth.uid();
  normalized_name text := regexp_replace(btrim(playlist_name), '\s+', ' ', 'g');
  normalized_description text := btrim(coalesce(playlist_description, ''));
begin
  if current_user_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if playlist_id is null
     or normalized_name is null
     or char_length(normalized_name) not between 1 and 100
     or char_length(normalized_description) > 300 then
    raise exception 'Invalid playlist metadata' using errcode = '22023';
  end if;

  return query
  insert into public.playlists as playlist (id, owner_id, name, description)
  values (playlist_id, current_user_id, normalized_name, normalized_description)
  returning
    playlist.id,
    playlist.name,
    playlist.description,
    playlist.track_count,
    playlist.revision,
    playlist.created_at,
    playlist.updated_at;
end;
$$;

comment on function public.get_my_playlists(integer) is
  'Returns a bounded, deterministic page of the current listener playlists.';
comment on function public.create_playlist(uuid, text, text) is
  'Creates validated current-listener playlist metadata at revision one.';

revoke all on function public.get_my_playlists(integer) from public, anon;
revoke all on function public.create_playlist(uuid, text, text) from public, anon;
grant execute on function public.get_my_playlists(integer) to authenticated, service_role;
grant execute on function public.create_playlist(uuid, text, text) to authenticated, service_role;

commit;
