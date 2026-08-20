-- =============================================
-- RAKYZU MUSIC - INITIAL SCHEMA & RLS POLICIES
-- Version: 0.1.1
-- Based on Master Prompt §4 & §2 permission matrix
-- =============================================

-- =============================================
-- ENUMS
-- =============================================
create type public.user_role as enum ('free', 'premium', 'staff', 'admin', 'owner');
create type public.subscription_status as enum ('none', 'active', 'expired', 'cancelled');
create type public.subscription_plan as enum ('monthly', 'yearly');
create type public.payment_status as enum ('pending', 'active', 'expired', 'cancelled');

-- =============================================
-- USERS
-- =============================================
create table public.users (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique,
  email text unique,
  full_name text,
  avatar_url text,
  role public.user_role not null default 'free',
  subscription_status public.subscription_status not null default 'none',
  subscription_expiry timestamptz,
  created_at timestamptz not null default now()
);

alter table public.users enable row level security;

-- Public read: any authenticated user can read basic profile info
create policy "users_select_authenticated"
  on public.users for select
  to authenticated
  using (true);

-- User can update their own profile only
create policy "users_update_own"
  on public.users for update
  to authenticated
  using (auth.uid() = id)
  with check (auth.uid() = id);

-- Only staff/admin/owner can insert/update others
create policy "users_insert_staff"
  on public.users for insert
  to authenticated
  with check (
    exists (
      select 1 from public.users u
      where u.id = auth.uid()
        and u.role in ('staff', 'admin', 'owner')
    )
  );

create policy "users_update_staff"
  on public.users for update
  to authenticated
  using (
    exists (
      select 1 from public.users u
      where u.id = auth.uid()
        and u.role in ('staff', 'admin', 'owner')
    )
  );

-- =============================================
-- ARTISTS
-- =============================================
create table public.artists (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  bio text,
  image_url text,
  is_verified boolean not null default false,
  created_by uuid references public.users(id),
  created_at timestamptz not null default now()
);

alter table public.artists enable row level security;

-- Public catalog read
create policy "artists_select_public"
  on public.artists for select
  to anon, authenticated
  using (true);

-- Staff/admin/owner can manage artists
create policy "artists_insert_staff"
  on public.artists for insert
  to authenticated
  with check (
    exists (
      select 1 from public.users u
      where u.id = auth.uid()
        and u.role in ('staff', 'admin', 'owner')
    )
  );

create policy "artists_update_staff"
  on public.artists for update
  to authenticated
  using (
    exists (
      select 1 from public.users u
      where u.id = auth.uid()
        and u.role in ('staff', 'admin', 'owner')
    )
  );

create policy "artists_delete_staff"
  on public.artists for delete
  to authenticated
  using (
    exists (
      select 1 from public.users u
      where u.id = auth.uid()
        and u.role in ('staff', 'admin', 'owner')
    )
  );

-- =============================================
-- ALBUMS
-- =============================================
create table public.albums (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  artist_id uuid references public.artists(id) on delete set null,
  cover_url text,
  release_date date,
  genre text,
  created_by uuid references public.users(id),
  created_at timestamptz not null default now()
);

alter table public.albums enable row level security;

create policy "albums_select_public"
  on public.albums for select
  to anon, authenticated
  using (true);

create policy "albums_insert_staff"
  on public.albums for insert
  to authenticated
  with check (
    exists (
      select 1 from public.users u
      where u.id = auth.uid()
        and u.role in ('staff', 'admin', 'owner')
    )
  );

create policy "albums_update_staff"
  on public.albums for update
  to authenticated
  using (
    exists (
      select 1 from public.users u
      where u.id = auth.uid()
        and u.role in ('staff', 'admin', 'owner')
    )
  );

create policy "albums_delete_staff"
  on public.albums for delete
  to authenticated
  using (
    exists (
      select 1 from public.users u
      where u.id = auth.uid()
        and u.role in ('staff', 'admin', 'owner')
    )
  );

-- =============================================
-- SONGS
-- =============================================
create table public.songs (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  album_id uuid references public.albums(id) on delete set null,
  artist_id uuid references public.artists(id) on delete set null,
  duration_seconds int,
  audio_url text,
  cover_url text,
  genre text,
  lyrics text,
  play_count bigint not null default 0,
  uploaded_by uuid references public.users(id),
  created_at timestamptz not null default now()
);

alter table public.songs enable row level security;

create policy "songs_select_public"
  on public.songs for select
  to anon, authenticated
  using (true);

create policy "songs_insert_staff"
  on public.songs for insert
  to authenticated
  with check (
    exists (
      select 1 from public.users u
      where u.id = auth.uid()
        and u.role in ('staff', 'admin', 'owner')
    )
  );

create policy "songs_update_staff"
  on public.songs for update
  to authenticated
  using (
    exists (
      select 1 from public.users u
      where u.id = auth.uid()
        and u.role in ('staff', 'admin', 'owner')
    )
  );

create policy "songs_delete_staff"
  on public.songs for delete
  to authenticated
  using (
    exists (
      select 1 from public.users u
      where u.id = auth.uid()
        and u.role in ('staff', 'admin', 'owner')
    )
  );

-- =============================================
-- PLAYLISTS
-- =============================================
create table public.playlists (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.users(id) on delete cascade not null,
  name text not null,
  description text,
  cover_url text,
  is_public boolean not null default false,
  created_at timestamptz not null default now()
);

alter table public.playlists enable row level security;

-- Owner can read own; public playlists readable by all
create policy "playlists_select_own_or_public"
  on public.playlists for select
  to authenticated
  using (user_id = auth.uid() or is_public = true);

create policy "playlists_insert_own"
  on public.playlists for insert
  to authenticated
  with check (user_id = auth.uid());

create policy "playlists_update_own"
  on public.playlists for update
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "playlists_delete_own"
  on public.playlists for delete
  to authenticated
  using (user_id = auth.uid());

-- =============================================
-- PLAYLIST_SONGS
-- =============================================
create table public.playlist_songs (
  playlist_id uuid references public.playlists(id) on delete cascade,
  song_id uuid references public.songs(id) on delete cascade,
  position int not null default 0,
  added_at timestamptz not null default now(),
  primary key (playlist_id, song_id)
);

alter table public.playlist_songs enable row level security;

create policy "playlist_songs_select_own"
  on public.playlist_songs for select
  to authenticated
  using (
    exists (
      select 1 from public.playlists p
      where p.id = playlist_id
        and (p.user_id = auth.uid() or p.is_public = true)
    )
  );

create policy "playlist_songs_insert_own"
  on public.playlist_songs for insert
  to authenticated
  with check (
    exists (
      select 1 from public.playlists p
      where p.id = playlist_id and p.user_id = auth.uid()
    )
  );

create policy "playlist_songs_delete_own"
  on public.playlist_songs for delete
  to authenticated
  using (
    exists (
      select 1 from public.playlists p
      where p.id = playlist_id and p.user_id = auth.uid()
    )
  );

-- =============================================
-- LIKED_SONGS
-- =============================================
create table public.liked_songs (
  user_id uuid references public.users(id) on delete cascade,
  song_id uuid references public.songs(id) on delete cascade,
  liked_at timestamptz not null default now(),
  primary key (user_id, song_id)
);

alter table public.liked_songs enable row level security;

create policy "liked_songs_select_own"
  on public.liked_songs for select
  to authenticated
  using (user_id = auth.uid());

create policy "liked_songs_insert_own"
  on public.liked_songs for insert
  to authenticated
  with check (user_id = auth.uid());

create policy "liked_songs_delete_own"
  on public.liked_songs for delete
  to authenticated
  using (user_id = auth.uid());

-- =============================================
-- FOLLOWS
-- =============================================
create table public.follows (
  user_id uuid references public.users(id) on delete cascade,
  artist_id uuid references public.artists(id) on delete cascade,
  followed_at timestamptz not null default now(),
  primary key (user_id, artist_id)
);

alter table public.follows enable row level security;

create policy "follows_select_public"
  on public.follows for select
  to authenticated
  using (true);

create policy "follows_insert_own"
  on public.follows for insert
  to authenticated
  with check (user_id = auth.uid());

create policy "follows_delete_own"
  on public.follows for delete
  to authenticated
  using (user_id = auth.uid());

-- =============================================
-- PLAY_HISTORY
-- =============================================
create table public.play_history (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.users(id) on delete cascade,
  song_id uuid references public.songs(id) on delete cascade,
  played_at timestamptz not null default now()
);

alter table public.play_history enable row level security;

create policy "play_history_select_own"
  on public.play_history for select
  to authenticated
  using (user_id = auth.uid());

create policy "play_history_insert_own"
  on public.play_history for insert
  to authenticated
  with check (user_id = auth.uid());

-- =============================================
-- SUBSCRIPTIONS
-- =============================================
create table public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.users(id) on delete cascade not null,
  plan_type public.subscription_plan not null default 'monthly',
  status public.payment_status not null default 'pending',
  payment_provider text not null default 'midtrans',
  transaction_id text,
  start_date timestamptz,
  end_date timestamptz,
  created_at timestamptz not null default now()
);

alter table public.subscriptions enable row level security;

-- User can view own subscriptions
create policy "subscriptions_select_own"
  on public.subscriptions for select
  to authenticated
  using (user_id = auth.uid());

-- Staff/admin/owner can view all
create policy "subscriptions_select_staff"
  on public.subscriptions for select
  to authenticated
  using (
    exists (
      select 1 from public.users u
      where u.id = auth.uid()
        and u.role in ('staff', 'admin', 'owner')
    )
  );

-- =============================================
-- GENRES
-- =============================================
create table public.genres (
  id uuid primary key default gen_random_uuid(),
  name text unique not null,
  icon_url text
);

alter table public.genres enable row level security;

create policy "genres_select_public"
  on public.genres for select
  to anon, authenticated
  using (true);

create policy "genres_insert_staff"
  on public.genres for insert
  to authenticated
  with check (
    exists (
      select 1 from public.users u
      where u.id = auth.uid()
        and u.role in ('staff', 'admin', 'owner')
    )
  );

-- =============================================
-- SEED: DEFAULT GENRES
-- =============================================
insert into public.genres (name) values
  ('Pop'), ('Rock'), ('Hip-Hop'), ('Electronic'), ('Jazz'),
  ('R&B'), ('K-Pop'), ('Indie'), ('Classical'), ('Metal'),
  ('Reggae'), ('Folk'), ('Dangdut'), ('Jazz Lounge')
on conflict (name) do nothing;

-- =============================================
-- INDEXES
-- =============================================
create index if not exists idx_songs_album on public.songs(album_id);
create index if not exists idx_songs_artist on public.songs(artist_id);
create index if not exists idx_songs_genre on public.songs(genre);
create index if not exists idx_albums_artist on public.albums(artist_id);
create index if not exists idx_playlists_user on public.playlists(user_id);
create index if not exists idx_play_history_user on public.play_history(user_id, played_at desc);
create index if not exists idx_subscriptions_user on public.subscriptions(user_id);
create index if not exists idx_liked_songs_user on public.liked_songs(user_id);