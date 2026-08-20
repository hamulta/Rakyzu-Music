-- =============================================
-- RAKYZU MUSIC - SEED DATA KATALOG
-- Version: 0.2.8
-- Contoh konten agar app punya isi saat demo (artis/album/lagu).
-- Lagu di-seed tanpa audio_url (audio asli di-upload staff via app).
-- =============================================

insert into public.artists (id, name, bio, image_url, is_verified, created_at)
values
  ('a1111111-1111-1111-1111-111111111111', 'Rania Amara', 'Vokal muda berbakat dari Jakarta dengan nuansa folk-pop.', null, true, now()),
  ('a2222222-2222-2222-2222-222222222222', 'Bintang Kejora', 'Band indie rock asal Bandung sejak 2018.', null, true, now()),
  ('a3333333-3333-3333-3333-333333333333', 'Kelas Lima', 'Duo hip-hop yang meramaikan scene musik bawah tanah.', null, false, now()),
  ('a4444444-4444-4444-4444-444444444444', 'Nada & Nadir', 'Kolaborasi akustik dua bersaudara dari Yogyakarta.', null, false, now())
on conflict (id) do nothing;

insert into public.albums (id, title, artist_id, cover_url, release_date, genre, created_at)
values
  ('b1111111-1111-1111-1111-111111111111', 'Senja di Ujung Kota', 'a1111111-1111-1111-1111-111111111111', null, '2025-06-15', 'Folk Pop', now()),
  ('b2222222-2222-2222-2222-222222222222', 'Api dan Bulan', 'a2222222-2222-2222-2222-222222222222', null, '2024-11-02', 'Indie Rock', now()),
  ('b3333333-3333-3333-3333-333333333333', 'Malam Minggu', 'a3333333-3333-3333-3333-333333333333', null, '2026-01-20', 'Hip Hop', now()),
  ('b4444444-4444-4444-4444-444444444444', 'Ruang Tenang', 'a4444444-4444-4444-4444-444444444444', null, '2023-08-08', 'Akustik', now())
on conflict (id) do nothing;

insert into public.songs (id, title, album_id, artist_id, duration_seconds, audio_url, cover_url, genre, play_count, track_number, created_at)
values
  ('c1111111-1111-1111-1111-111111111111', 'Senja', 'b1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', 214, null, null, 'Folk Pop', 0, 1, now()),
  ('c2222222-2222-2222-2222-222222222222', 'Pulang', 'b1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', 187, null, null, 'Folk Pop', 0, 2, now()),
  ('c3333333-3333-3333-3333-333333333333', 'Rindu Kota', 'b1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', 243, null, null, 'Folk Pop', 0, 3, now()),
  ('c4444444-4444-4444-4444-444444444444', 'Api', 'b2222222-2222-2222-2222-222222222222', 'a2222222-2222-2222-2222-222222222222', 196, null, null, 'Indie Rock', 0, 1, now()),
  ('c5555555-5555-5555-5555-555555555555', 'Bulan Purnama', 'b2222222-2222-2222-2222-222222222222', 'a2222222-2222-2222-2222-222222222222', 232, null, null, 'Indie Rock', 0, 2, now()),
  ('c6666666-6666-6666-6666-666666666666', 'Gelora', 'b2222222-2222-2222-2222-222222222222', 'a2222222-2222-2222-2222-222222222222', 178, null, null, 'Indie Rock', 0, 3, now()),
  ('c7777777-7777-7777-7777-777777777777', 'Malam Minggu', 'b3333333-3333-3333-3333-333333333333', 'a3333333-3333-3333-3333-333333333333', 205, null, null, 'Hip Hop', 0, 1, now()),
  ('c8888888-8888-8888-8888-888888888888', 'Jalan Jalan', 'b3333333-3333-3333-3333-333333333333', 'a3333333-3333-3333-3333-333333333333', 190, null, null, 'Hip Hop', 0, 2, now()),
  ('c9999999-9999-9999-9999-999999999999', 'Tidur Nyenyak', 'b4444444-4444-4444-4444-444444444444', 'a4444444-4444-4444-4444-444444444444', 265, null, null, 'Akustik', 0, 1, now()),
  ('caaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Duduk Bersama', 'b4444444-4444-4444-4444-444444444444', 'a4444444-4444-4444-4444-444444444444', 240, null, null, 'Akustik', 0, 2, now())
on conflict (id) do nothing;