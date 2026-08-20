# 🎧 MASTER PROMPT — RAKYZU MUSIC
### Blueprint Utama untuk AI Coding Agent

> Dokumen ini adalah sumber kebenaran tunggal (single source of truth) untuk seluruh scope produk "Rakyzu Music". Gunakan bersamaan dengan `02_Guidelines_Rules_Rakyzu_Music.md` (aturan main) dan `03_Roadmap_Rakyzu_Music.md` (urutan eksekusi bertahap).

---

## 0. IDENTITAS PROJECT

| Item | Detail |
|---|---|
| Nama Aplikasi | **Rakyzu Music** |
| Tagline | *"Your Sound, Your Vibe."* |
| Inspirasi | Spotify (sistem & flow), dengan branding & identitas visual sendiri |
| Tipe Aplikasi | Full-Stack Music Streaming Platform (Mobile + Web) |
| Target Rilis Final | v1.0.0 (Production-ready, siap Store Submission) |

---

## 1. PURPOSE & TARGET AUDIENCE

**Tujuan utama:** Rakyzu Music adalah platform streaming musik terkurasi (curated catalog) yang memungkinkan pendengar menikmati musik berkualitas tinggi dengan pengalaman UI/UX premium bergaya *Glassmorphism*, sambil memberikan kontrol penuh atas katalog musik kepada tim internal (Owner/Admin/Staff) — bukan model *user-generated content* seperti SoundCloud.

**Target User:**
- **Listener (Free & Premium)** — pendengar umum yang ingin streaming musik, buat playlist, follow artist.
- **Staff/Admin (Internal Team)** — tim kurator konten yang meng-upload & mengelola lagu, album, dan artis.
- **Owner** — pemegang kendali penuh, termasuk analytics, manajemen user, dan kebijakan monetisasi.

**Problem Statement yang diselesaikan:**
- Belum ada platform musik lokal/independen dengan branding sendiri yang punya kualitas UX setara Spotify.
- Kebutuhan kontrol penuh atas katalog (lisensi, kualitas audio, kurasi) tanpa risiko hukum dari UGC bebas.

---

## 2. USER ROLES & PERMISSION MATRIX

| Fitur | Free User | Premium User | Staff | Admin | Owner |
|---|:---:|:---:|:---:|:---:|:---:|
| Streaming musik (dengan iklan) | ✅ | ❌ (no ads) | ✅ | ✅ | ✅ |
| Streaming tanpa iklan | ❌ | ✅ | ✅ | ✅ | ✅ |
| Download offline | ❌ | ✅ | ✅ | ✅ | ✅ |
| Buat & kelola playlist pribadi | ✅ | ✅ | ✅ | ✅ | ✅ |
| Skip lagu tanpa batas | ❌ (limit 6x/jam) | ✅ | ✅ | ✅ | ✅ |
| Upload lagu/album/artist | ❌ | ❌ | ✅ | ✅ | ✅ |
| Edit/Hapus katalog milik sendiri | ❌ | ❌ | ✅ | ✅ | ✅ |
| Edit/Hapus katalog milik staff lain | ❌ | ❌ | ❌ | ✅ | ✅ |
| Manajemen user (ban, ubah role) | ❌ | ❌ | ❌ | ✅ | ✅ |
| Dashboard Analytics & Revenue | ❌ | ❌ | ❌ | Partial | ✅ Full |
| Kelola harga subscription & payout | ❌ | ❌ | ❌ | ❌ | ✅ |

> Implementasi wajib menggunakan **Supabase Row Level Security (RLS)** per role — bukan hanya validasi di sisi client.

---

## 3. CORE FEATURES (Full Scope — dieksekusi bertahap sesuai Roadmap)

### 3.1 Autentikasi & Profil
- Sign up/login via Email-Password, Google OAuth, Apple Sign-In (wajib untuk iOS App Store).
- Onboarding: pilih genre favorit → dipakai untuk seed rekomendasi awal.
- Edit profil, avatar, ganti password, hapus akun (GDPR-friendly).

### 3.2 Manajemen Katalog (Staff/Admin/Owner Only)
- CRUD Artist (nama, bio, foto profil, status verified).
- CRUD Album (judul, cover, tanggal rilis, artist terkait, genre).
- CRUD Song (judul, file audio, durasi otomatis terbaca, cover, lirik opsional, genre, album terkait).
- Upload audio → Cloudflare R2 (via signed URL dari Cloudflare Worker), metadata → Supabase.
- Bulk upload & drag-drop reordering track dalam album.

### 3.3 Player Inti (Core Playback Engine)
- Mini player (persistent, di seluruh halaman) + Full player (swipe-up).
- Queue management: play next, add to queue, reorder, shuffle, repeat (off/all/one).
- Background audio playback (lock screen controls, notification controls — via `audio_service`).
- Cross-fade antar lagu (opsional, toggle di settings).
- Lyrics view (real-time synced jika tersedia timestamp).

### 3.4 Discovery & Browse
- Home Feed: "Made For You", "Recently Played", "Trending Now", "New Releases", berbasis genre pilihan user.
- Search: real-time search (song/artist/album/playlist), search history.
- Genre/Mood browsing page (grid kategori).
- Halaman Artist (top tracks, albums, discography, follower count).
- Halaman Album/Playlist detail.

### 3.5 Library & Personalisasi
- Liked Songs (Your Library).
- Buat, edit, hapus, share playlist milik sendiri (public/private toggle).
- Recently played history.
- Follow/unfollow artist.
- Rekomendasi berbasis play history sederhana (untuk MVP: rule-based, bukan ML).

### 3.6 Monetisasi (Freemium + Subscription)
- Free tier: iklan audio (setiap N lagu) via AdMob (mobile) & ad-slot (web), limit skip.
- Premium tier: langganan bulanan/tahunan, integrasi payment gateway (Midtrans — QRIS/VA/e-wallet/kartu).
- Halaman "Upgrade to Premium" dengan perbandingan benefit.
- Auto-renewal handling & subscription status sync (webhook dari Midtrans → Supabase Edge Function).
- Riwayat transaksi user.

### 3.7 Admin Dashboard (Web — Owner/Admin/Staff)
- Dashboard analytics: total user, total stream, top songs, revenue (khusus Owner/Admin).
- Manajemen user (lihat, ban, ubah role).
- Manajemen katalog terpusat (approve/reject upload dari Staff — opsional workflow approval).
- Manajemen harga & paket subscription (Owner only).

### 3.8 Notifikasi & Engagement (Fase lanjutan)
- Push notification (rilisan baru dari artist yang di-follow).
- In-app notification center.

---

## 4. DATA & SCHEMA REQUIREMENT (Supabase / PostgreSQL)

```
users
  id (uuid, PK, = auth.uid())
  username (text, unique)
  email (text, unique)
  full_name (text)
  avatar_url (text)
  role (enum: free, premium, staff, admin, owner)
  subscription_status (enum: none, active, expired, cancelled)
  subscription_expiry (timestamptz, nullable)
  created_at (timestamptz)

artists
  id (uuid, PK)
  name (text)
  bio (text)
  image_url (text)
  is_verified (bool, default false)
  created_by (uuid, FK -> users.id)
  created_at (timestamptz)

albums
  id (uuid, PK)
  title (text)
  artist_id (uuid, FK -> artists.id)
  cover_url (text)
  release_date (date)
  genre (text)
  created_by (uuid, FK -> users.id)
  created_at (timestamptz)

songs
  id (uuid, PK)
  title (text)
  album_id (uuid, FK -> albums.id, nullable -- single tanpa album)
  artist_id (uuid, FK -> artists.id)
  duration_seconds (int)
  audio_url (text -- Cloudflare R2 object path)
  cover_url (text)
  genre (text)
  lyrics (text, nullable)
  play_count (bigint, default 0)
  uploaded_by (uuid, FK -> users.id)
  created_at (timestamptz)

playlists
  id (uuid, PK)
  user_id (uuid, FK -> users.id)
  name (text)
  description (text, nullable)
  cover_url (text, nullable)
  is_public (bool, default false)
  created_at (timestamptz)

playlist_songs
  playlist_id (uuid, FK -> playlists.id)
  song_id (uuid, FK -> songs.id)
  position (int)
  added_at (timestamptz)
  PRIMARY KEY (playlist_id, song_id)

liked_songs
  user_id (uuid, FK -> users.id)
  song_id (uuid, FK -> songs.id)
  liked_at (timestamptz)
  PRIMARY KEY (user_id, song_id)

follows
  user_id (uuid, FK -> users.id)
  artist_id (uuid, FK -> artists.id)
  followed_at (timestamptz)
  PRIMARY KEY (user_id, artist_id)

play_history
  id (uuid, PK)
  user_id (uuid, FK -> users.id)
  song_id (uuid, FK -> songs.id)
  played_at (timestamptz)

subscriptions
  id (uuid, PK)
  user_id (uuid, FK -> users.id)
  plan_type (enum: monthly, yearly)
  status (enum: pending, active, expired, cancelled)
  payment_provider (text, default 'midtrans')
  transaction_id (text)
  start_date (timestamptz)
  end_date (timestamptz)
  created_at (timestamptz)

genres
  id (uuid, PK)
  name (text, unique)
  icon_url (text, nullable)
```

**Wajib:** Semua tabel di atas menggunakan **Row Level Security (RLS)** sesuai matrix permission di Bagian 2. File audio & gambar **tidak** disimpan di Supabase Storage — semua disimpan di **Cloudflare R2**, Supabase hanya menyimpan path/URL referensinya.

---

## 5. DESIGN SYSTEM — GLASSMORPHISM "AZURE MIST & IVORY"

### 5.1 Filosofi Desain
UI mengusung **Glassmorphism**: elemen kartu/panel semi-transparan dengan efek *frosted glass* (backdrop blur), lapisan gradasi lembut di background, border tipis semi-transparan, dan shadow halus untuk memberi kedalaman (depth) — memberi kesan premium, ringan, dan modern.

### 5.2 Palet Warna

| Token | Hex | Penggunaan |
|---|---|---|
| `azure-mist-base` | `#DCEEFB` | Gradient background utama (light) |
| `azure-mist-deep` | `#7FB3E8` | Aksen, tombol primer, active state |
| `ivory-base` | `#FAF7F0` | Background kartu/permukaan glass |
| `ivory-soft` | `#FFFDF8` | Highlight/lapisan atas glass panel |
| `text-primary` | `#1E2A38` | Teks utama (kontras di atas ivory) |
| `text-secondary` | `#5C6B7A` | Teks sekunder/caption |
| `glass-overlay` | `rgba(255,255,255,0.25)` | Layer blur pada card (backdrop-filter: blur 16-20px) |
| `border-glass` | `rgba(255,255,255,0.4)` | Border tipis 1px pada elemen glass |
| `accent-success` | `#8FD9A8` | Status aktif/berhasil (mis. Premium badge) |
| `accent-warning` | `#F4C68A` | Notifikasi/limitasi Free tier |

### 5.3 Prinsip UI/UX
- **Cupertino Widgets** sebagai basis komponen (`CupertinoNavigationBar`, `CupertinoTabScaffold`, `CupertinoButton`, dll) untuk nuansa iOS-native di semua platform.
- Border radius besar (20-28px) pada card & sheet untuk kesan lembut.
- Backdrop blur konsisten (`BackdropFilter` + `ImageFilter.blur`) pada: mini player, bottom nav, modal sheet, search bar.
- Tipografi: font rounded/modern (rekomendasi: **SF Pro** untuk iOS-feel, fallback **Inter**).
- Micro-interaction: animasi smooth (200-300ms) pada transisi play/pause, like button, page transition.
- Dark Mode wajib ada sebagai varian kedua (Azure Mist gelap: `#0F1A24` base + aksen azure yang sama).
- Navigasi utama: `CupertinoTabScaffold` dengan tab **Home, Search, Library, Premium, Profile**.

---

## 6. TECH STACK & ARSITEKTUR

| Layer | Teknologi |
|---|---|
| Frontend Framework | **Flutter** (stable channel terbaru), target iOS + Android + Web dari satu codebase |
| UI Components | **Cupertino Widgets** sebagai basis, custom glassmorphism widget library di atasnya |
| State Management | **Riverpod** (recommended — testable, scalable untuk app sebesar ini) |
| Audio Engine | `just_audio` + `audio_service` (background playback, lock screen control) |
| Database & Auth | **Supabase** (PostgreSQL, Supabase Auth, Realtime untuk sync play state opsional) |
| Business Logic Serverless | **Supabase Edge Functions** (webhook payment, generate signed URL, dsb) |
| File Storage & CDN | **Cloudflare R2** (audio & image files) + **Cloudflare Workers** (signed URL, image resize on-the-fly) |
| Payment Gateway | **Midtrans** (Snap API — QRIS, VA, e-wallet, kartu kredit) *(asumsi, dapat diganti)* |
| Ads (Free Tier) | **Google AdMob** (mobile), ad-slot custom (web) |
| CI/CD | **GitHub Actions** — build APK/IPA/Web otomatis, lint, test, auto-deploy web ke Cloudflare Pages |
| Version Control | GitHub (branch strategy & commit convention di `02_Guidelines_Rules`) |

**Arsitektur Alur Data (ringkas):**
```
Flutter App (iOS/Android/Web)
   │
   ├── Supabase (Auth, Postgres DB, RLS, Edge Functions)
   │        └── Webhook <── Midtrans (status pembayaran)
   │
   └── Cloudflare Worker (signed URL) ── Cloudflare R2 (audio/image files, CDN-delivered)
```

---

## 7. NON-FUNCTIONAL REQUIREMENTS

- **Performance:** First playback latency < 1.5 detik pada koneksi 4G; caching audio metadata di local (Hive/SharedPreferences) agar app terasa instan saat reopen.
- **Security:** Tidak ada API key/secret di client code. Semua akses Storage lewat signed URL berumur pendek. RLS aktif di semua tabel.
- **Scalability:** Skema database disiapkan agar mudah ditambah fitur (podcast, radio) di masa depan tanpa migrasi besar.
- **Accessibility:** Kontras warna teks-background memenuhi WCAG AA meski dengan efek glass transparan.
- **Offline Support:** Premium user bisa download lagu terenkripsi lokal (tidak bisa diekstrak jadi file mp3 biasa).

---

## 8. ACCEPTANCE CRITERIA (Definition of Done untuk v1.0.0)

- [ ] Semua fitur di Bagian 3 berfungsi di iOS, Android, dan Web tanpa bug kritikal.
- [ ] RLS policy teraudit — tidak ada role yang bisa akses data di luar permission matrix.
- [ ] Alur pembayaran subscription end-to-end teruji (sandbox & production Midtrans).
- [ ] CI/CD pipeline GitHub Actions berhasil build & deploy tanpa intervensi manual.
- [ ] Dark mode & light mode konsisten di seluruh halaman.
- [ ] Aplikasi lolos syarat submission App Store & Play Store (privacy policy, app icon, screenshot, dsb).

---

*Dokumen ini akan dieksekusi secara bertahap mengikuti `03_Roadmap_Rakyzu_Music.md`. Setiap versi akan memiliki Execution Prompt terpisah yang merujuk kembali ke dokumen ini sebagai konteks utama.*
