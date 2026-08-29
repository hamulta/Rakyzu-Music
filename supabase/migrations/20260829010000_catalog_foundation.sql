begin;

create table public.artists (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  is_published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint artists_name_length
    check (char_length(btrim(name)) between 1 and 120)
);

create table public.albums (
  id uuid primary key default gen_random_uuid(),
  artist_id uuid not null references public.artists (id) on delete restrict,
  title text not null,
  release_date date,
  is_published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint albums_title_length
    check (char_length(btrim(title)) between 1 and 160)
);

create index albums_artist_id_idx on public.albums (artist_id);
create index albums_published_release_idx
on public.albums (release_date desc, id)
where is_published;

create table public.tracks (
  id uuid primary key default gen_random_uuid(),
  album_id uuid not null references public.albums (id) on delete restrict,
  title text not null,
  duration_ms integer not null,
  disc_number integer not null default 1,
  track_number integer not null,
  is_explicit boolean not null default false,
  is_published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint tracks_title_length
    check (char_length(btrim(title)) between 1 and 160),
  constraint tracks_duration_range
    check (duration_ms between 1000 and 86400000),
  constraint tracks_disc_number_positive
    check (disc_number > 0),
  constraint tracks_track_number_positive
    check (track_number > 0),
  constraint tracks_album_position_unique
    unique (album_id, disc_number, track_number)
);

create index tracks_album_id_idx on public.tracks (album_id);
create index tracks_published_updated_idx
on public.tracks (updated_at, id)
where is_published;

comment on table public.artists is
  'Read-only published artist metadata for authenticated Rakyzu Music listeners.';
comment on table public.albums is
  'Read-only published album metadata synchronized into the Android offline catalog.';
comment on table public.tracks is
  'Read-only published track metadata; media delivery is intentionally outside this table.';

alter table public.artists enable row level security;
alter table public.artists force row level security;
alter table public.albums enable row level security;
alter table public.albums force row level security;
alter table public.tracks enable row level security;
alter table public.tracks force row level security;

revoke all on table public.artists from anon, authenticated;
revoke all on table public.albums from anon, authenticated;
revoke all on table public.tracks from anon, authenticated;

grant select on table public.artists to authenticated;
grant select on table public.albums to authenticated;
grant select on table public.tracks to authenticated;
grant all on table public.artists to service_role;
grant all on table public.albums to service_role;
grant all on table public.tracks to service_role;

create policy artists_select_published
on public.artists
for select
to authenticated
using (is_published);

create policy albums_select_published
on public.albums
for select
to authenticated
using (is_published);

create policy tracks_select_published
on public.tracks
for select
to authenticated
using (is_published);

create trigger artists_set_updated_at
before update on public.artists
for each row
execute function private.set_updated_at();

create trigger albums_set_updated_at
before update on public.albums
for each row
execute function private.set_updated_at();

create trigger tracks_set_updated_at
before update on public.tracks
for each row
execute function private.set_updated_at();

insert into public.artists (id, name, is_published)
values (
  'a1000000-0000-4000-8000-000000000001',
  'Rakyzu Sessions',
  true
);

insert into public.albums (id, artist_id, title, release_date, is_published)
values (
  'a2000000-0000-4000-8000-000000000001',
  'a1000000-0000-4000-8000-000000000001',
  'Signal Zero',
  '2026-08-29',
  true
);

insert into public.tracks (
  id,
  album_id,
  title,
  duration_ms,
  disc_number,
  track_number,
  is_explicit,
  is_published
)
values
  (
    'a3000000-0000-4000-8000-000000000001',
    'a2000000-0000-4000-8000-000000000001',
    'Midnight Signal',
    185900,
    1,
    1,
    false,
    true
  ),
  (
    'a3000000-0000-4000-8000-000000000002',
    'a2000000-0000-4000-8000-000000000001',
    'Afterglow Circuit',
    204000,
    1,
    2,
    false,
    true
  ),
  (
    'a3000000-0000-4000-8000-000000000003',
    'a2000000-0000-4000-8000-000000000001',
    'New Horizons',
    198000,
    1,
    3,
    false,
    true
  );

commit;
