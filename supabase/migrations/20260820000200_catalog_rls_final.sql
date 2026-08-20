-- =============================================
-- RAKYZU MUSIC - CATALOG RLS FINAL
-- Version: 0.2.0
-- Upgrade RLS katalog (artists/albums/songs) ke permission matrix final:
--   SELECT : semua role (read-only listener)
--   INSERT : staff/admin/owner
--   UPDATE/DELETE : staff hanya row created_by=dirinya; admin/owner semua row
-- Tambahan:
--   - kolom songs.track_number untuk urutan track dalam album (v0.2.7)
--   - trigger proteksi is_verified (hanya admin/owner yang bisa ubah)
-- =============================================

-- =============================================
-- HELPER FUNCTIONS (digunakan policy)
-- =============================================
create or replace function public.is_staff()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.users u
    where u.id = auth.uid()
      and u.role in ('staff', 'admin', 'owner')
  );
$$;

create or replace function public.is_admin_or_owner()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.users u
    where u.id = auth.uid()
      and u.role in ('admin', 'owner')
  );
$$;

-- =============================================
-- SCHEMA: songs.track_number
-- =============================================
alter table public.songs add column if not exists track_number int not null default 0;

-- =============================================
-- ARTISTS — RLS FINAL
-- =============================================
drop policy if exists "artists_select_public" on public.artists;
drop policy if exists "artists_insert_staff" on public.artists;
drop policy if exists "artists_update_staff" on public.artists;
drop policy if exists "artists_delete_staff" on public.artists;

create policy "artists_select_public"
  on public.artists for select
  to anon, authenticated
  using (true);

create policy "artists_insert_staff"
  on public.artists for insert
  to authenticated
  with check (public.is_staff());

create policy "artists_update_staff"
  on public.artists for update
  to authenticated
  using (
    public.is_staff()
    and (created_by = auth.uid() or public.is_admin_or_owner())
  )
  with check (public.is_staff());

create policy "artists_delete_staff"
  on public.artists for delete
  to authenticated
  using (
    public.is_staff()
    and (created_by = auth.uid() or public.is_admin_or_owner())
  );

-- Proteksi is_verified: hanya admin/owner yang boleh mengubah
create or replace function public.prevent_staff_verify_artist()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.is_verified is distinct from old.is_verified then
    if not public.is_admin_or_owner() then
      raise exception 'Hanya admin/owner yang dapat mengubah status verified';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_artists_verified on public.artists;
create trigger trg_artists_verified
  before update on public.artists
  for each row execute function public.prevent_staff_verify_artist();

-- =============================================
-- ALBUMS — RLS FINAL
-- =============================================
drop policy if exists "albums_select_public" on public.albums;
drop policy if exists "albums_insert_staff" on public.albums;
drop policy if exists "albums_update_staff" on public.albums;
drop policy if exists "albums_delete_staff" on public.albums;

create policy "albums_select_public"
  on public.albums for select
  to anon, authenticated
  using (true);

create policy "albums_insert_staff"
  on public.albums for insert
  to authenticated
  with check (public.is_staff());

create policy "albums_update_staff"
  on public.albums for update
  to authenticated
  using (
    public.is_staff()
    and (created_by = auth.uid() or public.is_admin_or_owner())
  )
  with check (public.is_staff());

create policy "albums_delete_staff"
  on public.albums for delete
  to authenticated
  using (
    public.is_staff()
    and (created_by = auth.uid() or public.is_admin_or_owner())
  );

-- =============================================
-- SONGS — RLS FINAL
-- =============================================
drop policy if exists "songs_select_public" on public.songs;
drop policy if exists "songs_insert_staff" on public.songs;
drop policy if exists "songs_update_staff" on public.songs;
drop policy if exists "songs_delete_staff" on public.songs;

create policy "songs_select_public"
  on public.songs for select
  to anon, authenticated
  using (true);

create policy "songs_insert_staff"
  on public.songs for insert
  to authenticated
  with check (public.is_staff());

create policy "songs_update_staff"
  on public.songs for update
  to authenticated
  using (
    public.is_staff()
    and (uploaded_by = auth.uid() or public.is_admin_or_owner())
  )
  with check (public.is_staff());

create policy "songs_delete_staff"
  on public.songs for delete
  to authenticated
  using (
    public.is_staff()
    and (uploaded_by = auth.uid() or public.is_admin_or_owner())
  );

-- =============================================
-- INDEXES
-- =============================================
create index if not exists idx_songs_track_number on public.songs(album_id, track_number);