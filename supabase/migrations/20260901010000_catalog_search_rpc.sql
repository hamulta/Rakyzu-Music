begin;

create function public.search_catalog(
  search_query text,
  page_offset integer default 0,
  page_limit integer default 30
)
returns table (
  kind text,
  id uuid,
  title text,
  artist_id uuid,
  artist_name text,
  album_id uuid,
  album_title text,
  release_date date,
  duration_ms bigint,
  disc_number integer,
  track_number integer,
  is_explicit boolean,
  total_count bigint
)
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
  with request as (
    select
      lower(btrim(search_query)) as query,
      greatest(page_offset, 0) as requested_offset,
      least(greatest(page_limit, 1), 50) as requested_limit
    where search_query is not null
      and char_length(btrim(search_query)) between 2 and 100
      and page_offset between 0 and 10000
      and page_limit between 1 and 50
  ), matches as (
    select
      'artist'::text as kind,
      artist.id,
      artist.name as title,
      artist.id as artist_id,
      artist.name as artist_name,
      null::uuid as album_id,
      null::text as album_title,
      null::date as release_date,
      null::bigint as duration_ms,
      null::integer as disc_number,
      null::integer as track_number,
      null::boolean as is_explicit,
      case
        when lower(artist.name) = request.query then 0
        when left(lower(artist.name), char_length(request.query)) = request.query then 1
        else 3
      end as match_rank
    from public.artists as artist
    cross join request
    where artist.is_published
      and strpos(lower(artist.name), request.query) > 0

    union all

    select
      'album'::text,
      album.id,
      album.title,
      artist.id,
      artist.name,
      album.id,
      album.title,
      album.release_date,
      null::bigint,
      null::integer,
      null::integer,
      null::boolean,
      case
        when lower(album.title) = request.query then 0
        when left(lower(album.title), char_length(request.query)) = request.query then 1
        when strpos(lower(album.title), request.query) > 0 then 3
        when lower(artist.name) = request.query then 4
        when left(lower(artist.name), char_length(request.query)) = request.query then 5
        else 7
      end
    from public.albums as album
    join public.artists as artist on artist.id = album.artist_id
    cross join request
    where album.is_published
      and artist.is_published
      and (
        strpos(lower(album.title), request.query) > 0
        or strpos(lower(artist.name), request.query) > 0
      )

    union all

    select
      'track'::text,
      track.id,
      track.title,
      artist.id,
      artist.name,
      album.id,
      album.title,
      album.release_date,
      track.duration_ms::bigint,
      track.disc_number,
      track.track_number,
      track.is_explicit,
      case
        when lower(track.title) = request.query then 0
        when left(lower(track.title), char_length(request.query)) = request.query then 1
        when strpos(lower(track.title), request.query) > 0 then 3
        when lower(artist.name) = request.query or lower(album.title) = request.query then 4
        when left(lower(artist.name), char_length(request.query)) = request.query
          or left(lower(album.title), char_length(request.query)) = request.query then 5
        else 7
      end
    from public.tracks as track
    join public.albums as album on album.id = track.album_id
    join public.artists as artist on artist.id = album.artist_id
    cross join request
    where track.is_published
      and album.is_published
      and artist.is_published
      and (
        strpos(lower(track.title), request.query) > 0
        or strpos(lower(album.title), request.query) > 0
        or strpos(lower(artist.name), request.query) > 0
      )
  ), counted as (
    select matches.*, count(*) over () as total_count
    from matches
  )
  select
    counted.kind,
    counted.id,
    counted.title,
    counted.artist_id,
    counted.artist_name,
    counted.album_id,
    counted.album_title,
    counted.release_date,
    counted.duration_ms,
    counted.disc_number,
    counted.track_number,
    counted.is_explicit,
    counted.total_count
  from counted
  order by counted.match_rank, lower(counted.title), counted.kind, counted.id
  offset (select requested_offset from request)
  limit (select requested_limit from request);
$$;

comment on function public.search_catalog(text, integer, integer) is
  'Authenticated, RLS-preserving, bounded and deterministic catalog search page.';

revoke all on function public.search_catalog(text, integer, integer) from public, anon;
grant execute on function public.search_catalog(text, integer, integer) to authenticated, service_role;

commit;
