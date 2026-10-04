begin;

create or replace function public.admin_delete_editorial_artwork(target_shelf_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not (select private.has_staff_permission('editorial.manage')) then
    raise insufficient_privilege using message = 'Editorial management is not permitted';
  end if;
  if not exists(select 1 from public.editorial_shelves where id = target_shelf_id) then
    raise invalid_parameter_value using message = 'Recommendation card does not exist';
  end if;
  delete from public.editorial_shelf_artwork_assets where shelf_id = target_shelf_id;
  update public.editorial_shelves
  set artwork_object_key = null, updated_at = now()
  where id = target_shelf_id;
  perform private.write_staff_audit(
    'editorial.recommendation.artwork.deleted',
    'editorial_shelf',
    target_shelf_id
  );
  return jsonb_build_object('id', target_shelf_id, 'hasArtwork', false);
end;
$$;

revoke execute on function public.admin_delete_editorial_artwork(uuid) from public, anon;
grant execute on function public.admin_delete_editorial_artwork(uuid) to authenticated;

commit;
