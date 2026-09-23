begin;

create or replace function public.get_my_profile_context()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare current_user_id uuid := (select auth.uid()); profile_row public.profiles;
  staff_role text; artist_link public.artist_account_links; terms_row public.artist_terms;
  account_state text;
begin
  if current_user_id is null then
    raise insufficient_privilege using message = 'Authentication required';
  end if;

  -- Repair legacy/auth rows if an earlier trigger transaction was interrupted.
  insert into public.profiles(id, display_name)
  values (current_user_id, 'Rakyzu Listener')
  on conflict (id) do nothing;

  select profile.* into profile_row from public.profiles profile
    where profile.id = current_user_id;
  select assignment.role into staff_role from public.staff_assignments assignment
    where assignment.user_id = current_user_id and assignment.active;
  artist_link := private.resolve_artist_link_for_current_user();
  account_state := private.current_account_state(current_user_id);
  select terms.* into terms_row from public.artist_terms terms where terms.active limit 1;

  return jsonb_build_object(
    'userId', current_user_id,
    'displayName', profile_row.display_name,
    'onboardingCompleted', profile_row.onboarding_completed,
    'avatarAvailable', profile_row.avatar_object_key is not null,
    'avatarVersion', (select asset.uploaded_at::text from public.profile_avatar_assets asset
      where asset.user_id = current_user_id),
    'appearanceMode', profile_row.appearance_mode,
    'identityKind', case when account_state = 'active' and staff_role is not null then 'staff'
      when account_state = 'active' and artist_link.status = 'active' then 'artist'
      else 'listener' end,
    'role', case when account_state = 'active' then staff_role else null end,
    'verified', account_state = 'active' and (
      (staff_role is not null) or coalesce(artist_link.status = 'active', false)),
    'artist', case when artist_link.artist_id is null then null else jsonb_build_object(
      'id', artist_link.artist_id,
      'name', (select artist.name from public.artists artist where artist.id = artist_link.artist_id),
      'biography', (select artist.biography from public.artists artist where artist.id = artist_link.artist_id),
      'status', artist_link.status,
      'termsVersion', terms_row.version,
      'termsTitle', terms_row.title,
      'termsSummary', terms_row.summary,
      'termsText', terms_row.terms_text
    ) end
  );
end;
$$;

revoke all on function public.get_my_profile_context() from public, anon;
grant execute on function public.get_my_profile_context() to authenticated;

comment on function public.get_my_profile_context() is
  'Returns a null-safe authenticated identity context and repairs a missing listener profile row without granting client insert access.';

commit;
