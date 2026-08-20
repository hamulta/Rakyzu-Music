-- =============================================
-- RAKYZU MUSIC - AUTO CREATE users ROW ON SIGNUP
-- Version: 0.2.1
-- Setiap user auth baru otomatis mendapat baris public.users (role 'free').
-- Tanpa ini, worker tidak bisa melakukan role lookup & role provider
-- di aplikasi tidak berfungsi untuk user baru.
-- =============================================

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.users (id, username, email, full_name, role)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'username', split_part(new.email, '@', 1)),
    new.email,
    new.raw_user_meta_data ->> 'full_name',
    'free'
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();