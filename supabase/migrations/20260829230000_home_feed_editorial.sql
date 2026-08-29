begin;

create table public.editorial_shelves (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  subtitle text,
  position smallint not null,
  is_published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint editorial_shelves_title_length
    check (char_length(btrim(title)) between 1 and 80),
  constraint editorial_shelves_subtitle_length
    check (subtitle is null or char_length(btrim(subtitle)) between 1 and 160),
  constraint editorial_shelves_position_range
    check (position between 0 and 1000),
  constraint editorial_shelves_position_unique unique (position)
);

create table public.editorial_shelf_tracks (
  shelf_id uuid not null references public.editorial_shelves (id) on delete cascade,
  track_id uuid not null references public.tracks (id) on delete cascade,
  position smallint not null,
  created_at timestamptz not null default now(),
  primary key (shelf_id, track_id),
  constraint editorial_shelf_tracks_position_range
    check (position between 0 and 1000),
  constraint editorial_shelf_tracks_position_unique unique (shelf_id, position)
);

create index editorial_shelves_published_position_idx
on public.editorial_shelves (position, id)
where is_published;

create index editorial_shelf_tracks_track_id_idx
on public.editorial_shelf_tracks (track_id);

comment on table public.editorial_shelves is
  'Ordered, server-curated Home shelves visible only after publication.';
comment on table public.editorial_shelf_tracks is
  'Deterministic track ordering for a published Rakyzu Music editorial shelf.';

alter table public.editorial_shelves enable row level security;
alter table public.editorial_shelves force row level security;
alter table public.editorial_shelf_tracks enable row level security;
alter table public.editorial_shelf_tracks force row level security;

revoke all on table public.editorial_shelves from anon, authenticated;
revoke all on table public.editorial_shelf_tracks from anon, authenticated;

grant select on table public.editorial_shelves to authenticated;
grant select on table public.editorial_shelf_tracks to authenticated;
grant all on table public.editorial_shelves to service_role;
grant all on table public.editorial_shelf_tracks to service_role;

create policy editorial_shelves_select_published
on public.editorial_shelves
for select
to authenticated
using (is_published);

create policy editorial_shelf_tracks_select_published
on public.editorial_shelf_tracks
for select
to authenticated
using (
  exists (
    select 1
    from public.editorial_shelves as shelf
    where shelf.id = shelf_id
      and shelf.is_published
  )
  and exists (
    select 1
    from public.tracks as track
    where track.id = track_id
      and track.is_published
  )
);

create trigger editorial_shelves_set_updated_at
before update on public.editorial_shelves
for each row
execute function private.set_updated_at();

insert into public.editorial_shelves (id, title, subtitle, position, is_published)
values
  (
    'c1000000-0000-4000-8000-000000000001',
    'Rakyzu Essentials',
    'The defining sounds of Rakyzu Music.',
    0,
    true
  ),
  (
    'c1000000-0000-4000-8000-000000000002',
    'After Dark',
    'Late-night signals and neon atmosphere.',
    1,
    true
  );

insert into public.editorial_shelf_tracks (shelf_id, track_id, position)
values
  ('c1000000-0000-4000-8000-000000000001', 'a3000000-0000-4000-8000-000000000001', 0),
  ('c1000000-0000-4000-8000-000000000001', 'a3000000-0000-4000-8000-000000000002', 1),
  ('c1000000-0000-4000-8000-000000000001', 'a3000000-0000-4000-8000-000000000003', 2),
  ('c1000000-0000-4000-8000-000000000002', 'a3000000-0000-4000-8000-000000000002', 0),
  ('c1000000-0000-4000-8000-000000000002', 'a3000000-0000-4000-8000-000000000001', 1),
  ('c1000000-0000-4000-8000-000000000002', 'a3000000-0000-4000-8000-000000000003', 2);

commit;
