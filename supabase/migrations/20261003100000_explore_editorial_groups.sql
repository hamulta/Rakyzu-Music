begin;

alter table public.editorial_shelves
  add column if not exists card_label text,
  add column if not exists color_hex text not null default '#4A558F',
  add column if not exists global_score bigint not null default 0;

alter table public.editorial_shelves
  add constraint editorial_shelves_card_label_length
    check (card_label is null or char_length(btrim(card_label)) between 1 and 40),
  add constraint editorial_shelves_color_hex_format
    check (color_hex ~ '^#[0-9A-Fa-f]{6}$'),
  add constraint editorial_shelves_global_score_nonnegative
    check (global_score >= 0),
  add constraint editorial_shelves_supported_placement
    check (position between 0 and 6 or position between 100 and 106 or
      position between 200 and 207 or position between 300 and 311);

update public.editorial_shelves
set card_label = case id
      when 'c1000000-0000-4000-8000-000000000001'::uuid then 'Pop Mix'
      when 'c1000000-0000-4000-8000-000000000002'::uuid then 'Chill Mix'
      when 'c1000000-0000-4000-8000-000000000003'::uuid then 'Fresh Mix'
      else card_label
    end,
    color_hex = case id
      when 'c1000000-0000-4000-8000-000000000001'::uuid then '#E05252'
      when 'c1000000-0000-4000-8000-000000000002'::uuid then '#E39A43'
      when 'c1000000-0000-4000-8000-000000000003'::uuid then '#E2C94C'
      when 'c1000000-0000-4000-8000-000000000004'::uuid then '#31775A'
      when 'c1000000-0000-4000-8000-000000000005'::uuid then '#3856A8'
      else color_hex
    end
where id in (
  'c1000000-0000-4000-8000-000000000001',
  'c1000000-0000-4000-8000-000000000002',
  'c1000000-0000-4000-8000-000000000003',
  'c1000000-0000-4000-8000-000000000004',
  'c1000000-0000-4000-8000-000000000005'
);

create table public.editorial_shelf_opens (
  shelf_id uuid not null references public.editorial_shelves(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  opened_on date not null default current_date,
  created_at timestamptz not null default now(),
  primary key (shelf_id, user_id, opened_on)
);

alter table public.editorial_shelf_opens enable row level security;
alter table public.editorial_shelf_opens force row level security;
revoke all on table public.editorial_shelf_opens from public, anon, authenticated;
grant all on table public.editorial_shelf_opens to service_role;

create or replace function public.record_editorial_shelf_open(target_shelf_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare inserted_count integer;
begin
  if (select auth.uid()) is null or not exists(
    select 1 from public.editorial_shelves
    where id = target_shelf_id and is_published
  ) then
    return false;
  end if;
  insert into public.editorial_shelf_opens(shelf_id, user_id, opened_on)
  values(target_shelf_id, (select auth.uid()), current_date)
  on conflict do nothing;
  get diagnostics inserted_count = row_count;
  if inserted_count = 1 then
    update public.editorial_shelves
    set global_score = global_score + 1
    where id = target_shelf_id;
  end if;
  return true;
end;
$$;

revoke execute on function public.record_editorial_shelf_open(uuid) from public, anon;
grant execute on function public.record_editorial_shelf_open(uuid) to authenticated;

drop function if exists public.admin_upsert_editorial_shelf(uuid, text, text, integer, uuid, boolean);

create or replace function public.admin_upsert_editorial_shelf(
  target_shelf_id uuid,
  shelf_title text,
  shelf_subtitle text,
  shelf_position integer,
  target_track_id uuid,
  published boolean,
  shelf_card_label text,
  shelf_color_hex text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  saved public.editorial_shelves;
  old_position integer;
  placement_min integer;
  placement_max integer;
  occupied_count integer;
begin
  if not (select private.has_staff_permission('editorial.manage')) then
    raise insufficient_privilege using message = 'Editorial management is not permitted';
  end if;
  shelf_title := btrim(coalesce(shelf_title, ''));
  shelf_subtitle := nullif(btrim(coalesce(shelf_subtitle, '')), '');
  shelf_card_label := nullif(btrim(coalesce(shelf_card_label, '')), '');
  shelf_color_hex := upper(btrim(coalesce(shelf_color_hex, '')));
  placement_min := case
    when shelf_position between 0 and 6 then 0
    when shelf_position between 100 and 106 then 100
    when shelf_position between 200 and 207 then 200
    when shelf_position between 300 and 311 then 300
    else null
  end;
  placement_max := case placement_min
    when 0 then 6 when 100 then 106 when 200 then 207 when 300 then 311 else null
  end;
  if char_length(shelf_title) not between 1 and 80 or
    char_length(coalesce(shelf_subtitle, '')) > 160 or
    char_length(coalesce(shelf_card_label, '')) > 40 or
    shelf_color_hex !~ '^#[0-9A-F]{6}$' or
    placement_min is null or
    (target_track_id is not null and not exists(
      select 1 from public.tracks where id = target_track_id
    )) then
    raise invalid_parameter_value using message = 'Editorial Card Group is invalid';
  end if;

  if target_shelf_id is null or not exists(
    select 1 from public.editorial_shelves where id = target_shelf_id
  ) then
    select count(*) into occupied_count from public.editorial_shelves
    where position between placement_min and placement_max;
    if occupied_count >= placement_max - placement_min + 1 then
      raise invalid_parameter_value using message = 'Editorial category has reached its card limit';
    end if;
    update public.editorial_shelves set position = position + 1
    where position between shelf_position and placement_max;
    insert into public.editorial_shelves(
      id, title, subtitle, position, is_published, featured_track_id,
      card_label, color_hex
    ) values(
      coalesce(target_shelf_id, gen_random_uuid()), shelf_title, shelf_subtitle,
      shelf_position, published, target_track_id,
      shelf_card_label, shelf_color_hex
    ) returning * into saved;
  else
    select position into old_position from public.editorial_shelves
    where id = target_shelf_id for update;
    if old_position is null or not (
      old_position between placement_min and placement_max
    ) then
      raise invalid_parameter_value using message = 'Card Group cannot move between categories';
    end if;
    if shelf_position < old_position then
      update public.editorial_shelves set position = position + 1
      where position >= shelf_position and position < old_position and id <> target_shelf_id;
    elsif shelf_position > old_position then
      update public.editorial_shelves set position = position - 1
      where position <= shelf_position and position > old_position and id <> target_shelf_id;
    end if;
    update public.editorial_shelves set
      title = shelf_title,
      subtitle = shelf_subtitle,
      position = shelf_position,
      is_published = published,
      featured_track_id = target_track_id,
      card_label = shelf_card_label,
      color_hex = shelf_color_hex,
      updated_at = now()
    where id = target_shelf_id
    returning * into saved;
  end if;
  if target_track_id is not null and not exists(
    select 1 from public.editorial_shelf_tracks
    where shelf_id = saved.id and track_id = target_track_id
  ) then
    update public.editorial_shelf_tracks set position = position + 1
    where shelf_id = saved.id;
    insert into public.editorial_shelf_tracks(shelf_id, track_id, position)
    values(saved.id, target_track_id, 0);
  end if;
  perform private.write_staff_audit(
    'editorial.group.saved', 'editorial_shelf', saved.id,
    jsonb_build_object('position', saved.position, 'published', saved.is_published,
      'colorHex', saved.color_hex)
  );
  return jsonb_build_object(
    'id', saved.id, 'title', saved.title, 'position', saved.position,
    'published', saved.is_published, 'featuredTrackId', saved.featured_track_id,
    'cardLabel', saved.card_label, 'colorHex', saved.color_hex
  );
end;
$$;

revoke execute on function public.admin_upsert_editorial_shelf(
  uuid, text, text, integer, uuid, boolean, text, text
) from public, anon;
grant execute on function public.admin_upsert_editorial_shelf(
  uuid, text, text, integer, uuid, boolean, text, text
) to authenticated;

create or replace function public.admin_delete_editorial_shelf(target_shelf_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare removed_position integer; placement_max integer;
begin
  if not (select private.has_staff_permission('editorial.manage')) then
    raise insufficient_privilege using message = 'Editorial management is not permitted';
  end if;
  delete from public.editorial_shelves where id = target_shelf_id
  returning position into removed_position;
  if removed_position is null then
    raise invalid_parameter_value using message = 'Card Group does not exist';
  end if;
  placement_max := case
    when removed_position between 0 and 6 then 6
    when removed_position between 100 and 106 then 106
    when removed_position between 200 and 207 then 207
    when removed_position between 300 and 311 then 311
  end;
  update public.editorial_shelves set position = position - 1
  where position > removed_position and position <= placement_max;
  perform private.write_staff_audit(
    'editorial.group.deleted', 'editorial_shelf', target_shelf_id
  );
  return jsonb_build_object('id', target_shelf_id, 'deleted', true);
end;
$$;

create or replace function public.admin_list_recommendations()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if not (select private.has_staff_permission('editorial.manage')) then
    raise insufficient_privilege using message = 'Editorial management is not permitted';
  end if;
  return coalesce((
    select jsonb_agg(
      jsonb_build_object(
        'id', shelf.id,
        'title', shelf.title,
        'subtitle', shelf.subtitle,
        'position', shelf.position,
        'published', shelf.is_published,
        'trackId', shelf.featured_track_id,
        'hasArtwork', shelf.artwork_object_key is not null,
        'cardLabel', shelf.card_label,
        'colorHex', shelf.color_hex,
        'globalScore', shelf.global_score,
        'trackIds', coalesce((
          select jsonb_agg(item.track_id order by item.position)
          from public.editorial_shelf_tracks item
          where item.shelf_id = shelf.id
        ), '[]'::jsonb)
      ) order by shelf.position, shelf.id
    ) from public.editorial_shelves shelf
  ), '[]'::jsonb);
end;
$$;

insert into public.editorial_shelves(
  id, title, subtitle, position, is_published, featured_track_id, card_label, color_hex
)
values
  ('c2000000-0000-4000-8000-000000000001', 'K-Pop', 'Global K-Pop selections curated by Rakyzu Music.', 200, true, 'a3000000-0000-4000-8000-000000000001', 'K-Pop', '#70CF18'),
  ('c2000000-0000-4000-8000-000000000002', 'Indie', 'Independent voices and emerging sounds.', 201, true, 'a3000000-0000-4000-8000-000000000002', 'Indie', '#D3229D'),
  ('c2000000-0000-4000-8000-000000000003', 'R&B', 'Contemporary rhythm and soul.', 202, true, 'a3000000-0000-4000-8000-000000000003', 'R&B', '#4A558F'),
  ('c2000000-0000-4000-8000-000000000004', 'Pop', 'Global pop songs selected for discovery.', 203, true, 'a3000000-0000-4000-8000-000000000001', 'Pop', '#BD6220'),
  ('c3000000-0000-4000-8000-000000000001', 'Made For You', 'Editorial discoveries shaped for Rakyzu listeners.', 300, true, 'a3000000-0000-4000-8000-000000000002', 'Made for You', '#1E82AC'),
  ('c3000000-0000-4000-8000-000000000002', 'Released', 'Newly released music from the Rakyzu catalog.', 301, true, 'a3000000-0000-4000-8000-000000000003', 'RELEASED', '#76259C'),
  ('c3000000-0000-4000-8000-000000000003', 'Music Charts', 'Ranked catalog songs from current global activity.', 302, true, 'a3000000-0000-4000-8000-000000000001', 'Music Charts', '#25319C'),
  ('c3000000-0000-4000-8000-000000000004', 'Podcasts', 'Spoken-word shows available from Rakyzu Music.', 303, true, 'a3000000-0000-4000-8000-000000000002', 'Podcasts', '#9C2542'),
  ('c3000000-0000-4000-8000-000000000005', 'Bollywood', 'Film music and South Asian catalog highlights.', 304, true, 'a3000000-0000-4000-8000-000000000003', 'Bollywood', '#9C7425'),
  ('c3000000-0000-4000-8000-000000000006', 'Pop Fusion', 'Pop crossed with electronic and alternative influences.', 305, true, 'a3000000-0000-4000-8000-000000000001', 'Pop Fusion', '#479775')
on conflict(id) do update set
  title = excluded.title,
  subtitle = excluded.subtitle,
  position = excluded.position,
  is_published = excluded.is_published,
  featured_track_id = excluded.featured_track_id,
  card_label = excluded.card_label,
  color_hex = excluded.color_hex,
  updated_at = now();

insert into public.editorial_shelf_tracks(shelf_id, track_id, position)
select shelf.id, track.id, track.position
from (
  values
    ('c2000000-0000-4000-8000-000000000001'::uuid), ('c2000000-0000-4000-8000-000000000002'::uuid),
    ('c2000000-0000-4000-8000-000000000003'::uuid), ('c2000000-0000-4000-8000-000000000004'::uuid),
    ('c3000000-0000-4000-8000-000000000001'::uuid), ('c3000000-0000-4000-8000-000000000002'::uuid),
    ('c3000000-0000-4000-8000-000000000003'::uuid), ('c3000000-0000-4000-8000-000000000004'::uuid),
    ('c3000000-0000-4000-8000-000000000005'::uuid), ('c3000000-0000-4000-8000-000000000006'::uuid)
) shelf(id)
cross join (
  values
    ('a3000000-0000-4000-8000-000000000001'::uuid, 0::smallint),
    ('a3000000-0000-4000-8000-000000000002'::uuid, 1::smallint),
    ('a3000000-0000-4000-8000-000000000003'::uuid, 2::smallint)
) track(id, position)
on conflict(shelf_id, track_id) do update set position = excluded.position;

comment on column public.editorial_shelves.card_label is
  'Optional front-card label; title remains the synchronized Card Group/detail name.';
comment on column public.editorial_shelves.color_hex is
  'Editable or artwork-derived dominant color used by cards and group detail ambience.';
comment on column public.editorial_shelves.global_score is
  'Privacy-preserving daily unique-open score used for global Smart Recommendation ordering.';

commit;
