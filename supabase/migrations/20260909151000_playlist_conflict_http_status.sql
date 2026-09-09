begin;

-- SQLSTATE 40001 is reserved for serialization failures and may be retried by database
-- middleware. Expose an intentional HTTP 409 instead so stale client revisions fail fast.
create or replace function public.mutate_playlist(
  playlist_id uuid, expected_revision bigint, action text,
  track_id uuid default null, ordered_ids uuid[] default null,
  playlist_name text default null, playlist_description text default null
) returns jsonb language plpgsql volatile security definer set search_path = '' as $$
declare p public.playlists; ids uuid[]; normalized_name text; changed boolean := false;
begin
  select * into p from public.playlists where id = playlist_id and owner_id = auth.uid() for update;
  if not found then raise exception 'Playlist unavailable' using errcode = '42501'; end if;
  if expected_revision is null or p.revision <> expected_revision then
    raise exception 'Playlist revision conflict' using errcode = 'PT409';
  end if;
  select coalesce(array_agg(i.track_id order by i.position), '{}'::uuid[]) into ids
  from public.playlist_items i where i.playlist_id = p.id;
  if action = 'add' then
    if track_id is null or not exists (
      select 1 from public.tracks t join public.albums a on a.id = t.album_id
      join public.artists ar on ar.id = a.artist_id
      where t.id = mutate_playlist.track_id and t.is_published and a.is_published and ar.is_published
    ) then raise exception 'Track unavailable' using errcode = '22023'; end if;
    if not track_id = any(ids) then
      if cardinality(ids) >= 500 then raise exception 'Playlist limit reached' using errcode = '22023'; end if;
      ids := array_append(ids, track_id); changed := true;
    end if;
  elsif action = 'remove' then
    if track_id is null then raise exception 'Track required' using errcode = '22023'; end if;
    changed := track_id = any(ids); ids := array_remove(ids, track_id);
  elsif action = 'reorder' then
    if ordered_ids is null or cardinality(ordered_ids) <> cardinality(ids)
      or exists (select 1 from unnest(ordered_ids) x where x is null)
      or (select count(distinct x) from unnest(ordered_ids) x) <> cardinality(ids)
      or not ordered_ids @> ids then
      raise exception 'Order must be an exact permutation' using errcode = '22023';
    end if;
    changed := ids <> ordered_ids; ids := ordered_ids;
  elsif action = 'metadata' then
    normalized_name := btrim(regexp_replace(playlist_name, '\s+', ' ', 'g'));
    if normalized_name is null or char_length(normalized_name) not between 1 and 100
      or playlist_description is null or char_length(btrim(playlist_description)) > 300 then
      raise exception 'Invalid playlist metadata' using errcode = '22023';
    end if;
    changed := p.name <> normalized_name or p.description <> btrim(playlist_description);
    p.name := normalized_name; p.description := btrim(playlist_description);
  else raise exception 'Invalid action' using errcode = '22023';
  end if;
  if changed then
    delete from public.playlist_items i where i.playlist_id = p.id;
    insert into public.playlist_items(playlist_id, track_id, position)
      select p.id, value, (ordinality - 1)::integer from unnest(ids) with ordinality as x(value, ordinality);
    update public.playlists set name = p.name, description = p.description,
      track_count = cardinality(ids), revision = p.revision + 1, updated_at = clock_timestamp()
      where id = p.id;
  end if;
  return public.get_playlist_detail(p.id);
end $$;

revoke all on function public.mutate_playlist(uuid, bigint, text, uuid, uuid[], text, text) from public, anon;
grant execute on function public.mutate_playlist(uuid, bigint, text, uuid, uuid[], text, text) to authenticated;

commit;
