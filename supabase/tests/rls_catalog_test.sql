-- =============================================
-- RAKYZU MUSIC - RLS CATALOG POLICY TEST
-- Version: 0.2.0
-- Menjalankan simulasi per role terhadap policy katalog final.
-- Di-execute sebagai postgres (via Supabase Management API query endpoint).
-- Script dijalankan dalam SATU transaksi implisit: gunakan BEGIN eksplisit
-- agar SAVEPOINT tersedia; tiap skenario di-isolasi dengan rollback to savepoint.
-- Role & claims disimulasikan via `set local role` + set_config('request.jwt.claims').
-- Catatan: UPDATE/DELETE yang ditolak RLS menghasilkan 0 baris (silent
-- filter via USING), sedangkan INSERT yang ditolak melempar exception
-- (via WITH CHECK). Karena itu ada 2 helper berbeda.
-- =============================================

-- =============================================
-- SETUP: test users & data
-- =============================================
begin;
-- users.id memiliki FK ke auth.users, jadi buat dulu di auth.users
insert into auth.users
  (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
   confirmation_sent_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data,
   confirmation_token, email_change_token_new, recovery_token)
values
  ('00000000-0000-0000-0000-000000000000', '11111111-1111-1111-1111-111111111111', 'authenticated', 'authenticated', 'free@test.rakyzu',  'x', now(), now(), now(), now(), '{"provider":"email","providers":["email"]}', '{}', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '22222222-2222-2222-2222-222222222222', 'authenticated', 'authenticated', 'prem@test.rakyzu',  'x', now(), now(), now(), now(), '{"provider":"email","providers":["email"]}', '{}', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '33333333-3333-3333-3333-333333333333', 'authenticated', 'authenticated', 'staff@test.rakyzu', 'x', now(), now(), now(), now(), '{"provider":"email","providers":["email"]}', '{}', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '44444444-4444-4444-4444-444444444444', 'authenticated', 'authenticated', 'admin@test.rakyzu', 'x', now(), now(), now(), now(), '{"provider":"email","providers":["email"]}', '{}', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '55555555-5555-5555-5555-555555555555', 'authenticated', 'authenticated', 'owner@test.rakyzu', 'x', now(), now(), now(), now(), '{"provider":"email","providers":["email"]}', '{}', '', '', '')
on conflict (id) do nothing;

do $$
begin
  insert into public.users (id, username, email, full_name, role) values
    ('11111111-1111-1111-1111-111111111111', 'free_tester',  'free@test.rakyzu',  'Free Tester',  'free'),
    ('22222222-2222-2222-2222-222222222222', 'prem_tester',  'prem@test.rakyzu',  'Prem Tester',  'premium'),
    ('33333333-3333-3333-3333-333333333333', 'staff_tester', 'staff@test.rakyzu', 'Staff Tester', 'staff'),
    ('44444444-4444-4444-4444-444444444444', 'admin_tester', 'admin@test.rakyzu', 'Admin Tester', 'admin'),
    ('55555555-5555-5555-5555-555555555555', 'owner_tester', 'owner@test.rakyzu', 'Owner Tester', 'owner')
  -- Trigger handle_new_user sudah membuat baris role 'free'; timpa role sesuai skenario
  on conflict (id) do update set
    username = excluded.username,
    email = excluded.email,
    full_name = excluded.full_name,
    role = excluded.role;
end $$;

-- data dimiliki staff (3333...) dan admin (4444...)
insert into public.artists (id, name, created_by) values
  ('aaaa0000-0000-0000-0000-000000000001', 'Artist Staff',  '33333333-3333-3333-3333-333333333333'),
  ('aaaa0000-0000-0000-0000-000000000002', 'Artist Admin',  '44444444-4444-4444-4444-444444444444')
on conflict (id) do nothing;

insert into public.albums (id, title, artist_id, created_by) values
  ('bbbb0000-0000-0000-0000-000000000001', 'Album Staff', 'aaaa0000-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333'),
  ('bbbb0000-0000-0000-0000-000000000002', 'Album Admin', 'aaaa0000-0000-0000-0000-000000000002', '44444444-4444-4444-4444-444444444444')
on conflict (id) do nothing;

insert into public.songs (id, title, album_id, artist_id, uploaded_by, track_number) values
  ('cccc0000-0000-0000-0000-000000000001', 'Song Staff 1', 'bbbb0000-0000-0000-0000-000000000001', 'aaaa0000-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333', 1),
  ('cccc0000-0000-0000-0000-000000000002', 'Song Staff 2', 'bbbb0000-0000-0000-0000-000000000001', 'aaaa0000-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333', 2),
  ('cccc0000-0000-0000-0000-000000000003', 'Song Admin',   'bbbb0000-0000-0000-0000-000000000002', 'aaaa0000-0000-0000-0000-000000000002', '44444444-4444-4444-4444-444444444444', 1)
on conflict (id) do nothing;

-- =============================================
-- UTILITY: helper
--   1) _rls_ok      : jalankan ekspresi, cek sukses(gagal) => untuk INSERT
--   2) _rls_affected: jalankan DML, bandingkan jumlah baris terpengaruh => untuk UPDATE/DELETE
-- =============================================
create or replace function public._rls_ok(expected_ok boolean, test_name text, test_sql text)
returns void language plpgsql as $$
declare actual_ok boolean;
begin
  begin
    execute test_sql;
    actual_ok := true;
  exception when others then
    actual_ok := false;
  end;
  if actual_ok != expected_ok then
    raise exception 'FAIL [%]: expected %', test_name, expected_ok;
  end if;
  raise notice 'PASS [%]', test_name;
end;
$$;

create or replace function public._rls_affected(expected_affected integer, test_name text, test_sql text)
returns void language plpgsql as $$
declare actual_affected integer;
begin
  begin
    execute test_sql;
    get diagnostics actual_affected = row_count;
  exception when others then
    actual_affected := -1;
  end;
  if actual_affected != expected_affected then
    raise exception 'FAIL [%]: expected % affected, got %', test_name, expected_affected, actual_affected;
  end if;
  raise notice 'PASS [%]', test_name;
end;
$$;

-- =============================================
-- SCENARIO: anon (public reader)
-- =============================================
savepoint sp_anon;
set local role anon;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000000","role":"anon"}', true);
do $$
begin
  perform public._rls_ok(true,        'anon: SELECT songs',        'select count(*) from public.songs');
  perform public._rls_ok(false,       'anon: INSERT songs',        'insert into public.songs (title) values (''x'')');
  perform public._rls_affected(0,     'anon: UPDATE artist',       'update public.artists set bio = ''x''');
  perform public._rls_affected(0,     'anon: DELETE song',         'delete from public.songs');
end $$;
rollback to sp_anon;

-- =============================================
-- SCENARIO: free user
-- =============================================
savepoint sp_free;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated"}', true);
do $$
begin
  perform public._rls_ok(true,        'free: SELECT songs',            'select count(*) from public.songs');
  perform public._rls_ok(false,       'free: INSERT artist',           'insert into public.artists (name) values (''x'')');
  perform public._rls_ok(false,       'free: INSERT song',             'insert into public.songs (title) values (''x'')');
  perform public._rls_affected(0,     'free: UPDATE song milik staff', 'update public.songs set title = ''x''');
  perform public._rls_affected(0,     'free: DELETE album',            'delete from public.albums');
end $$;
rollback to sp_free;

-- =============================================
-- SCENARIO: premium user
-- =============================================
savepoint sp_premium;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"22222222-2222-2222-2222-222222222222","role":"authenticated"}', true);
do $$
begin
  perform public._rls_ok(true,        'premium: SELECT artist',     'select count(*) from public.artists');
  perform public._rls_ok(false,       'premium: INSERT album',      'insert into public.albums (title) values (''x'')');
  perform public._rls_affected(0,     'premium: UPDATE album',      'update public.albums set title = ''x''');
  perform public._rls_affected(0,     'premium: DELETE song',       'delete from public.songs');
end $$;
rollback to sp_premium;

-- =============================================
-- SCENARIO: staff (row milik sendiri boleh, milik admin TIDAK)
-- =============================================
savepoint sp_staff;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"33333333-3333-3333-3333-333333333333","role":"authenticated"}', true);
do $$
begin
  -- INSERT diizinkan
  perform public._rls_ok(true,      'staff: INSERT artist', 'insert into public.artists (name, created_by) values (''New Staff'', ''33333333-3333-3333-3333-333333333333'')');
  -- UPDATE row milik sendiri
  perform public._rls_affected(1,   'staff: UPDATE artist sendiri', 'update public.artists set bio = ''own'' where id = ''aaaa0000-0000-0000-0000-000000000001''');
  -- UPDATE row milik admin -> 0 baris
  perform public._rls_affected(0,   'staff: UPDATE artist admin',   'update public.artists set bio = ''x'' where id = ''aaaa0000-0000-0000-0000-000000000002''');
  -- DELETE song milik sendiri
  perform public._rls_affected(1,   'staff: DELETE song sendiri',   'delete from public.songs where id = ''cccc0000-0000-0000-0000-000000000002''');
  -- DELETE song milik admin -> 0 baris
  perform public._rls_affected(0,   'staff: DELETE song admin',     'delete from public.songs where id = ''cccc0000-0000-0000-0000-000000000003''');
  -- is_verified dikunci: staff tidak bisa (trigger raise)
  perform public._rls_ok(false,     'staff: set is_verified',       'update public.artists set is_verified = true where id = ''aaaa0000-0000-0000-0000-000000000001''');
  -- UPDATE yang tidak menyentuh is_verified tetap OK
  perform public._rls_affected(1,   'staff: UPDATE artist (non-verif)', 'update public.artists set bio = ''ok'' where id = ''aaaa0000-0000-0000-0000-000000000001''');
end $$;
rollback to sp_staff;

-- =============================================
-- SCENARIO: admin (semua row)
-- =============================================
savepoint sp_admin;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"44444444-4444-4444-4444-444444444444","role":"authenticated"}', true);
do $$
begin
  perform public._rls_affected(1, 'admin: UPDATE artist staff', 'update public.artists set bio = ''x'' where id = ''aaaa0000-0000-0000-0000-000000000001''');
  perform public._rls_affected(1, 'admin: DELETE song staff',   'delete from public.songs where id = ''cccc0000-0000-0000-0000-000000000001''');
  perform public._rls_ok(true,    'admin: set is_verified',     'update public.artists set is_verified = true where id = ''aaaa0000-0000-0000-0000-000000000001''');
  perform public._rls_ok(true,    'admin: INSERT album',        'insert into public.albums (title) values (''Admin Album'')');
end $$;
rollback to sp_admin;

-- =============================================
-- SCENARIO: owner (semua row)
-- =============================================
savepoint sp_owner;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"55555555-5555-5555-5555-555555555555","role":"authenticated"}', true);
do $$
begin
  perform public._rls_affected(1, 'owner: UPDATE song staff', 'update public.songs set title = ''x'' where id = ''cccc0000-0000-0000-0000-000000000001''');
  perform public._rls_affected(1, 'owner: DELETE artist',     'delete from public.artists where id = ''aaaa0000-0000-0000-0000-000000000002''');
  perform public._rls_ok(true,    'owner: set is_verified',   'update public.artists set is_verified = true where id = ''aaaa0000-0000-0000-0000-000000000002''');
end $$;
rollback to sp_owner;

-- =============================================
-- CLEANUP
-- =============================================
delete from public.songs where id::text like 'cccc0000-%';
delete from public.albums where id::text like 'bbbb0000-%';
delete from public.artists where id::text like 'aaaa0000-%' or name = 'New Staff';
delete from public.users where id in (
  '11111111-1111-1111-1111-111111111111',
  '22222222-2222-2222-2222-222222222222',
  '33333333-3333-3333-3333-333333333333',
  '44444444-4444-4444-4444-444444444444',
  '55555555-5555-5555-5555-555555555555'
);
delete from auth.users where id in (
  '11111111-1111-1111-1111-111111111111',
  '22222222-2222-2222-2222-222222222222',
  '33333333-3333-3333-3333-333333333333',
  '44444444-4444-4444-4444-444444444444',
  '55555555-5555-5555-5555-555555555555'
);

drop function if exists public._rls_ok(boolean, text, text);
drop function if exists public._rls_affected(integer, text, text);

commit;