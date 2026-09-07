begin;

create table public.liked_tracks (
  user_id uuid not null references auth.users (id) on delete cascade,
  track_id uuid not null references public.tracks (id) on delete cascade,
  saved_at timestamptz not null default now(),
  primary key (user_id, track_id)
);

create table public.saved_albums (
  user_id uuid not null references auth.users (id) on delete cascade,
  album_id uuid not null references public.albums (id) on delete cascade,
  saved_at timestamptz not null default now(),
  primary key (user_id, album_id)
);

create table public.followed_artists (
  user_id uuid not null references auth.users (id) on delete cascade,
  artist_id uuid not null references public.artists (id) on delete cascade,
  saved_at timestamptz not null default now(),
  primary key (user_id, artist_id)
);

create index liked_tracks_user_saved_idx
on public.liked_tracks (user_id, saved_at desc, track_id);
create index saved_albums_user_saved_idx
on public.saved_albums (user_id, saved_at desc, album_id);
create index followed_artists_user_saved_idx
on public.followed_artists (user_id, saved_at desc, artist_id);

comment on table public.liked_tracks is
  'Listener-owned liked tracks; client access is isolated by RLS.';
comment on table public.saved_albums is
  'Listener-owned saved albums; client access is isolated by RLS.';
comment on table public.followed_artists is
  'Listener-owned followed artists; client access is isolated by RLS.';

alter table public.liked_tracks enable row level security;
alter table public.liked_tracks force row level security;
alter table public.saved_albums enable row level security;
alter table public.saved_albums force row level security;
alter table public.followed_artists enable row level security;
alter table public.followed_artists force row level security;

revoke all on table public.liked_tracks from anon, authenticated;
revoke all on table public.saved_albums from anon, authenticated;
revoke all on table public.followed_artists from anon, authenticated;
grant select, insert, delete on table public.liked_tracks to authenticated;
grant select, insert, delete on table public.saved_albums to authenticated;
grant select, insert, delete on table public.followed_artists to authenticated;
grant all on table public.liked_tracks to service_role;
grant all on table public.saved_albums to service_role;
grant all on table public.followed_artists to service_role;

create policy liked_tracks_select_own on public.liked_tracks
for select to authenticated using ((select auth.uid()) = user_id);
create policy liked_tracks_insert_own on public.liked_tracks
for insert to authenticated with check ((select auth.uid()) = user_id);
create policy liked_tracks_delete_own on public.liked_tracks
for delete to authenticated using ((select auth.uid()) = user_id);

create policy saved_albums_select_own on public.saved_albums
for select to authenticated using ((select auth.uid()) = user_id);
create policy saved_albums_insert_own on public.saved_albums
for insert to authenticated with check ((select auth.uid()) = user_id);
create policy saved_albums_delete_own on public.saved_albums
for delete to authenticated using ((select auth.uid()) = user_id);

create policy followed_artists_select_own on public.followed_artists
for select to authenticated using ((select auth.uid()) = user_id);
create policy followed_artists_insert_own on public.followed_artists
for insert to authenticated with check ((select auth.uid()) = user_id);
create policy followed_artists_delete_own on public.followed_artists
for delete to authenticated using ((select auth.uid()) = user_id);

create function public.get_library_items()
returns table (
  kind text,
  item_id uuid,
  saved_at timestamptz
)
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
  select 'track'::text, liked.track_id, liked.saved_at
  from public.liked_tracks as liked
  join public.tracks as track on track.id = liked.track_id
  join public.albums as album on album.id = track.album_id
  join public.artists as artist on artist.id = album.artist_id
  where liked.user_id = (select auth.uid())
    and track.is_published and album.is_published and artist.is_published
  union all
  select 'album'::text, saved.album_id, saved.saved_at
  from public.saved_albums as saved
  join public.albums as album on album.id = saved.album_id
  join public.artists as artist on artist.id = album.artist_id
  where saved.user_id = (select auth.uid())
    and album.is_published and artist.is_published
  union all
  select 'artist'::text, followed.artist_id, followed.saved_at
  from public.followed_artists as followed
  join public.artists as artist on artist.id = followed.artist_id
  where followed.user_id = (select auth.uid())
    and artist.is_published
  order by saved_at desc, kind, item_id;
$$;

create function public.set_library_item(
  item_kind text,
  item_id uuid,
  should_save boolean
)
returns table (success boolean)
language plpgsql
volatile
security invoker
set search_path = public, pg_temp
as $$
declare
  listener_id uuid := auth.uid();
begin
  if listener_id is null or item_id is null or should_save is null then
    return query select false;
    return;
  end if;

  case item_kind
    when 'track' then
      if should_save then
        if not exists (
          select 1 from public.tracks as track
          join public.albums as album on album.id = track.album_id
          join public.artists as artist on artist.id = album.artist_id
          where track.id = item_id
            and track.is_published and album.is_published and artist.is_published
        ) then
          return query select false;
          return;
        end if;
        insert into public.liked_tracks (user_id, track_id)
        values (listener_id, item_id)
        on conflict do nothing;
      else
        delete from public.liked_tracks
        where user_id = listener_id and track_id = item_id;
      end if;
    when 'album' then
      if should_save then
        if not exists (
          select 1 from public.albums as album
          join public.artists as artist on artist.id = album.artist_id
          where album.id = item_id and album.is_published and artist.is_published
        ) then
          return query select false;
          return;
        end if;
        insert into public.saved_albums (user_id, album_id)
        values (listener_id, item_id)
        on conflict do nothing;
      else
        delete from public.saved_albums
        where user_id = listener_id and album_id = item_id;
      end if;
    when 'artist' then
      if should_save then
        if not exists (
          select 1 from public.artists as artist
          where artist.id = item_id and artist.is_published
        ) then
          return query select false;
          return;
        end if;
        insert into public.followed_artists (user_id, artist_id)
        values (listener_id, item_id)
        on conflict do nothing;
      else
        delete from public.followed_artists
        where user_id = listener_id and artist_id = item_id;
      end if;
    else
      return query select false;
      return;
  end case;

  return query select true;
end;
$$;

comment on function public.get_library_items() is
  'RLS-preserving current-listener Library projection containing published catalog IDs only.';
comment on function public.set_library_item(text, uuid, boolean) is
  'Idempotent RLS-preserving Like, Save, or Follow mutation for a published catalog item.';

revoke all on function public.get_library_items() from public, anon;
revoke all on function public.set_library_item(text, uuid, boolean) from public, anon;
grant execute on function public.get_library_items() to authenticated, service_role;
grant execute on function public.set_library_item(text, uuid, boolean) to authenticated, service_role;

commit;
