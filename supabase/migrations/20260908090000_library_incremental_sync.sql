begin;

create table public.library_changes (
  sequence bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  kind text not null check (kind in ('track', 'album', 'artist')),
  item_id uuid not null,
  saved boolean not null,
  saved_at timestamptz not null,
  created_at timestamptz not null default now()
);

create index library_changes_user_sequence_idx
on public.library_changes (user_id, sequence);

comment on table public.library_changes is
  'Append-only per-listener cursor stream used for incremental Library synchronization.';

alter table public.library_changes enable row level security;
alter table public.library_changes force row level security;

revoke all on table public.library_changes from anon, authenticated;
grant select on table public.library_changes to authenticated;
grant all on table public.library_changes to service_role;

create policy library_changes_select_own on public.library_changes
for select to authenticated using ((select auth.uid()) = user_id);

create function public.record_library_change()
returns trigger
language plpgsql
volatile
security definer
set search_path = public, pg_temp
as $$
declare
  changed_user_id uuid := coalesce(new.user_id, old.user_id);
  changed_item_id uuid;
  changed_kind text;
  changed_saved boolean := tg_op = 'INSERT';
  changed_saved_at timestamptz := coalesce(new.saved_at, old.saved_at, now());
begin
  case tg_table_name
    when 'liked_tracks' then
      changed_kind := 'track';
      changed_item_id := coalesce(new.track_id, old.track_id);
    when 'saved_albums' then
      changed_kind := 'album';
      changed_item_id := coalesce(new.album_id, old.album_id);
    when 'followed_artists' then
      changed_kind := 'artist';
      changed_item_id := coalesce(new.artist_id, old.artist_id);
    else
      raise exception 'Unsupported Library change source';
  end case;

  insert into public.library_changes (user_id, kind, item_id, saved, saved_at)
  values (changed_user_id, changed_kind, changed_item_id, changed_saved, changed_saved_at);
  if tg_op = 'DELETE' then
    return old;
  end if;

  return new;
end;
$$;

revoke all on function public.record_library_change() from public, anon, authenticated;

create trigger liked_tracks_record_change
after insert or delete on public.liked_tracks
for each row execute function public.record_library_change();

create trigger saved_albums_record_change
after insert or delete on public.saved_albums
for each row execute function public.record_library_change();

create trigger followed_artists_record_change
after insert or delete on public.followed_artists
for each row execute function public.record_library_change();

create function public.get_library_sync_anchor()
returns table (sequence bigint)
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
  select coalesce(max(changes.sequence), 0::bigint) as sequence
  from public.library_changes as changes
  where changes.user_id = (select auth.uid());
$$;

create function public.get_library_items_page(
  page_size integer default 50,
  cursor_saved_at timestamptz default null,
  cursor_kind text default null,
  cursor_item_id uuid default null
)
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
  with library as (
    select 'track'::text as kind, liked.track_id as item_id, liked.saved_at
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
    where followed.user_id = (select auth.uid()) and artist.is_published
  )
  select library.kind, library.item_id, library.saved_at
  from library
  where cursor_saved_at is null
     or library.saved_at < cursor_saved_at
     or (
       library.saved_at = cursor_saved_at
       and (
         library.kind > cursor_kind
         or (library.kind = cursor_kind and library.item_id > cursor_item_id)
       )
     )
  order by library.saved_at desc, library.kind, library.item_id
  limit greatest(1, least(coalesce(page_size, 50), 50));
$$;

create function public.get_library_changes(
  after_sequence bigint default 0,
  page_size integer default 50
)
returns table (
  sequence bigint,
  kind text,
  item_id uuid,
  saved boolean,
  saved_at timestamptz
)
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
  select changes.sequence, changes.kind, changes.item_id, changes.saved, changes.saved_at
  from public.library_changes as changes
  where changes.user_id = (select auth.uid())
    and changes.sequence > greatest(coalesce(after_sequence, 0), 0)
  order by changes.sequence
  limit greatest(1, least(coalesce(page_size, 50), 50));
$$;

comment on function public.get_library_sync_anchor() is
  'Returns the current listener change cursor before a paginated bootstrap.';
comment on function public.get_library_items_page(integer, timestamptz, text, uuid) is
  'Returns a bounded keyset page of the current listener published Library.';
comment on function public.get_library_changes(bigint, integer) is
  'Returns a bounded ordered page of current-listener Library changes, including deletions.';

revoke all on function public.get_library_sync_anchor() from public, anon;
revoke all on function public.get_library_items_page(integer, timestamptz, text, uuid)
from public, anon;
revoke all on function public.get_library_changes(bigint, integer) from public, anon;
grant execute on function public.get_library_sync_anchor() to authenticated, service_role;
grant execute on function public.get_library_items_page(integer, timestamptz, text, uuid)
to authenticated, service_role;
grant execute on function public.get_library_changes(bigint, integer)
to authenticated, service_role;

commit;
