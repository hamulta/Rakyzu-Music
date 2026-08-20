# 🗺️ ROADMAP EKSEKUSI BERTAHAP — RAKYZU MUSIC
### v0.1.0 → v1.0.0

Roadmap ini memecah seluruh scope di `01_Master_Prompt_Rakyzu_Music.md` menjadi **9 fase besar** (minor version), masing-masing berisi hingga 10 increment kecil (patch version, `.0`–`.9`). Setiap fase akan punya **Execution Prompt** terpisah yang dibuat saat fase tersebut dimulai (dibuat mendekati waktunya agar tetap relevan dengan kondisi kode aktual — bukan dibuat sekaligus di depan).

---

## Ringkasan 9 Fase

| Versi | Nama Fase | Fokus Utama | Exit Criteria |
|---|---|---|---|
| **0.1.x** | Foundation & Design System | Setup project, arsitektur folder, Glassmorphism component library, koneksi Supabase & Cloudflare, Auth dasar | Splash → Login → Home shell (dummy) jalan di 3 platform |
| **0.2.x** | Katalog & Sistem Upload (Internal) | CRUD Artist/Album/Song, role-based access, upload ke Cloudflare R2, admin dashboard basic | Staff bisa upload lagu, tersimpan & tampil di app |
| **0.3.x** | Player Engine | Mini player, full player, queue, background playback, lyrics view | Playback stabil + lock screen control berfungsi |
| **0.4.x** | Discovery & Search | Home feed, search real-time, genre browsing, halaman Artist/Album | User bisa cari & temukan lagu dengan lancar |
| **0.5.x** | Library & Personalisasi | Playlist, liked songs, follow artist, recently played | User bisa kelola koleksi musik pribadinya penuh |
| **0.6.x** | Monetisasi — Free & Ads | Integrasi AdMob, limit skip, ad-slot web | Free tier berjalan sesuai batasan yang ditentukan |
| **0.7.x** | Monetisasi — Subscription | Integrasi Midtrans, halaman upgrade, webhook status, offline download Premium | Alur bayar → jadi Premium end-to-end berhasil |
| **0.8.x** | Admin Dashboard & Analytics | Dashboard web lengkap (Owner/Admin), manajemen user, revenue report | Owner bisa pantau seluruh metrik dari dashboard |
| **0.9.x** | Hardening, Testing & Polish | Security audit RLS, performance tuning, dark mode polish, bug bash | Semua Acceptance Criteria Master Prompt §8 tercentang |
| **1.0.0** | Public Release | Store submission (App Store/Play Store), Web production deploy, marketing assets | App live & bisa diunduh publik |

---

## Detail Breakdown Fase 0.1.x — Foundation & Design System
*(Contoh breakdown granular; fase-fase berikutnya akan dirinci dengan pola serupa saat gilirannya tiba)*

| Versi | Task |
|---|---|
| 0.1.0 | Inisialisasi repo Flutter, setup folder Feature-First Architecture, setup GitHub Actions skeleton (lint + analyze) |
| 0.1.1 | Setup Supabase project (schema awal dari Master Prompt §4), koneksi `.env` |
| 0.1.2 | Setup Cloudflare R2 bucket + Worker signed-URL skeleton |
| 0.1.3 | Bangun Design Token & Theme (warna Azure Mist & Ivory, light/dark mode) |
| 0.1.4 | Bangun Glassmorphism widget library dasar (GlassCard, GlassButton, GlassBottomBar) |
| 0.1.5 | Implementasi Auth: Sign Up & Login (email/password) via Supabase Auth |
| 0.1.6 | Implementasi Google & Apple Sign-In |
| 0.1.7 | Onboarding flow (pilih genre favorit) |
| 0.1.8 | `CupertinoTabScaffold` shell navigasi utama (Home, Search, Library, Premium, Profile — masih dummy content) |
| 0.1.9 | QA menyeluruh fase 0.1.x + deploy preview build (internal testing) |

---

## Prinsip Kerja Bertahap

1. **Satu fase, satu fokus.** Tidak mengerjakan fitur dari fase lain sebelum fase aktif selesai & lolos QA, kecuali ada dependency teknis yang mengharuskan (didiskusikan dulu dengan PM).
2. **Setiap versi = deliverable yang bisa didemokan.** Tidak ada versi yang "setengah jadi" tanpa keterangan jelas.
3. **Execution Prompt dibuat per fase**, bukan sekaligus semua di awal — supaya prompt selalu selaras dengan kondisi kode real dan keputusan yang mungkin berubah selama development.
4. **Setiap akhir fase (`.9`)** dialokasikan khusus untuk QA & stabilisasi sebelum lanjut ke fase berikutnya.

---

## Status Project Saat Ini

📍 **Kita mulai dari `v0.1.0`.**

