begin;

alter table public.editorial_shelf_tracks
  drop constraint editorial_shelf_tracks_position_range;
alter table public.editorial_shelf_tracks
  add constraint editorial_shelf_tracks_position_range
  check (position between 0 and 49);

create or replace function public.admin_replace_editorial_tracks(
  target_shelf_id uuid,
  target_track_ids uuid[]
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  requested_count integer;
begin
  if not (select private.has_staff_permission('editorial.manage')) then
    raise insufficient_privilege using message = 'Editorial management is not permitted';
  end if;
  requested_count := coalesce(cardinality(target_track_ids), 0);
  if requested_count not between 1 and 50 or
    (select count(distinct track_id)
     from unnest(target_track_ids) as requested(track_id)) <> requested_count or
    not exists(select 1 from public.editorial_shelves where id = target_shelf_id) or
    (select count(*) from public.tracks where id = any(target_track_ids)) <> requested_count then
    raise invalid_parameter_value using message = 'Card Group tracks are invalid';
  end if;
  delete from public.editorial_shelf_tracks where shelf_id = target_shelf_id;
  insert into public.editorial_shelf_tracks(shelf_id, track_id, position)
  select target_shelf_id, value, (ordinal - 1)::smallint
  from unnest(target_track_ids) with ordinality as requested(value, ordinal);
  update public.editorial_shelves
  set featured_track_id = target_track_ids[1], updated_at = now()
  where id = target_shelf_id;
  perform private.write_staff_audit(
    'editorial.recommendation.tracks.replaced',
    'editorial_shelf',
    target_shelf_id,
    jsonb_build_object('trackCount', requested_count)
  );
  return jsonb_build_object('id', target_shelf_id, 'trackCount', requested_count);
end;
$$;

revoke execute on function public.admin_replace_editorial_tracks(uuid, uuid[]) from public, anon;
grant execute on function public.admin_replace_editorial_tracks(uuid, uuid[]) to authenticated;

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
        'trackIds', coalesce((
          select jsonb_agg(item.track_id order by item.position)
          from public.editorial_shelf_tracks item
          where item.shelf_id = shelf.id
        ), '[]'::jsonb)
      ) order by shelf.position, shelf.id
    )
    from public.editorial_shelves shelf
  ), '[]'::jsonb);
end;
$$;

-- Positions 0-6 are Top Mixes and 100-106 are image-led recent-listening groups.
-- These five launch groups contain published catalog tracks; they are not client placeholders.
update public.editorial_shelves
set title = 'Pop Mix',
    subtitle = 'Popular Rakyzu releases in one ranked mix.',
    position = 0,
    is_published = true
where id = 'c1000000-0000-4000-8000-000000000001';

update public.editorial_shelves
set title = 'Chill Mix',
    subtitle = 'A softer ranked selection for unhurried listening.',
    position = 1,
    is_published = true
where id = 'c1000000-0000-4000-8000-000000000002';

insert into public.editorial_shelves (id, title, subtitle, position, is_published)
values
  (
    'c1000000-0000-4000-8000-000000000003',
    'Fresh Mix',
    'Recently published tracks selected by Rakyzu Music.',
    2,
    true
  ),
  (
    'c1000000-0000-4000-8000-000000000004',
    'After Hours',
    'Global songs for the late-night rotation.',
    100,
    true
  ),
  (
    'c1000000-0000-4000-8000-000000000005',
    'Daily Discovery',
    'A global chart assembled for new discoveries.',
    101,
    true
  )
on conflict (id) do update
set title = excluded.title,
    subtitle = excluded.subtitle,
    position = excluded.position,
    is_published = excluded.is_published,
    updated_at = now();

insert into public.editorial_shelf_tracks (shelf_id, track_id, position)
values
  ('c1000000-0000-4000-8000-000000000003', 'a3000000-0000-4000-8000-000000000003', 0),
  ('c1000000-0000-4000-8000-000000000003', 'a3000000-0000-4000-8000-000000000001', 1),
  ('c1000000-0000-4000-8000-000000000003', 'a3000000-0000-4000-8000-000000000002', 2),
  ('c1000000-0000-4000-8000-000000000004', 'a3000000-0000-4000-8000-000000000002', 0),
  ('c1000000-0000-4000-8000-000000000004', 'a3000000-0000-4000-8000-000000000003', 1),
  ('c1000000-0000-4000-8000-000000000004', 'a3000000-0000-4000-8000-000000000001', 2),
  ('c1000000-0000-4000-8000-000000000005', 'a3000000-0000-4000-8000-000000000001', 0),
  ('c1000000-0000-4000-8000-000000000005', 'a3000000-0000-4000-8000-000000000003', 1),
  ('c1000000-0000-4000-8000-000000000005', 'a3000000-0000-4000-8000-000000000002', 2)
on conflict (shelf_id, track_id) do update set position = excluded.position;

commit;
