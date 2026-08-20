# 🤖 AI CODING AGENT GUIDELINES — RAKYZU MUSIC

Dokumen ini berisi standar kerja & aturan **WAJIB** bagi AI Agent (Developer) yang mengerjakan repository Rakyzu Music. Berlaku untuk seluruh fase, dari v0.1.0 hingga v1.0.0.

---

## 1. Project Context & Environment

- **Framework:** Flutter (stable channel) — target build: iOS, Android, Web.
- **UI Base:** Cupertino Widgets + custom Glassmorphism component library (lihat `01_Master_Prompt` §5).
- **State Management:** Riverpod. Dilarang mencampur state management lain (Provider/GetX/Bloc) kecuali disetujui eksplisit.
- **Backend:** Supabase (Postgres + Auth + Edge Functions).
- **Storage/CDN:** Cloudflare R2 + Cloudflare Workers.
- **Payment:** Midtrans Snap API.
- **Repository:** GitHub, dengan GitHub Actions sebagai CI/CD.
- Gunakan file `.env` (dan `--dart-define` untuk Flutter build) untuk seluruh kredensial. **Dilarang keras** hardcode API key, Supabase anon/service key, atau Midtrans server key langsung di kode. Sediakan `.env.example` yang selalu ter-update setiap ada variabel baru.

---

## 2. Code Quality & Security

- Ikuti **Effective Dart** style guide. Jalankan `dart format .` dan `flutter analyze` sebelum setiap commit — **zero warning** sebagai syarat commit.
- Struktur folder mengikuti **Feature-First Architecture**:
  ```
  lib/
    core/          (theme, constants, glassmorphism widgets, utils)
    features/
      auth/
      home/
      player/
      library/
      catalog_management/
      subscription/
      ...
    shared/        (shared widgets, models, providers)
  ```
- Setiap fitur baru wajib punya minimal 1 unit test (logic/provider) sebelum dianggap selesai — tidak perlu 100% coverage, tapi *critical path* (auth, payment, playback) wajib punya test.
- **Row Level Security (RLS)** di Supabase adalah lapisan keamanan utama — validasi role di client HANYA untuk UX, bukan satu-satunya proteksi. Setiap tabel baru wajib disertai policy SQL yang eksplisit, tidak boleh ada tabel tanpa RLS aktif.
- Semua akses file audio/gambar dari Cloudflare R2 **wajib** melalui signed URL bertenggat waktu (short-lived), digenerate lewat Cloudflare Worker/Supabase Edge Function — dilarang expose bucket R2 secara publik/permanen.
- Validasi & sanitasi seluruh input user (form upload, search query, comment jika ada) untuk mencegah injection & abuse.

---

## 3. Git Workflow & Versioning Rules (WAJIB)

### 3.1 Skema Versi
Project mengikuti staged versioning sesuai `03_Roadmap_Rakyzu_Music.md`:
`0.1.0 → 0.1.9 → 0.2.0 → ... → 0.9.9 → 1.0.0`

Setiap angka minor (`0.X.0`) merepresentasikan satu **fase besar** (mis. 0.3.x = Player Engine). Setiap angka patch (`0.X.Y`) merepresentasikan satu **task/increment kecil** dalam fase tersebut.

### 3.2 Commit Convention
Gunakan **Conventional Commits**:
```
feat: tambah CupertinoTabScaffold navigasi utama
fix: perbaiki bug play_count tidak bertambah
chore: update dependency just_audio ke v0.9.x
refactor: pisahkan PlayerProvider dari HomeProvider
docs: update .env.example dengan variabel Midtrans
```
Setiap commit **wajib mencantumkan versi target** di body commit, contoh:
```
feat: implementasi glass mini-player

Version: 0.3.2
```

### 3.3 Branch Strategy
- `main` → selalu mencerminkan versi stabil terakhir yang sudah lolos test.
- `develop` → integrasi kerja aktif per fase.
- `feature/<nama-fitur>` → task individual, merge ke `develop` via PR (self-review AI Agent tetap wajib mencantumkan checklist di deskripsi PR).
- Hanya `develop` → `main` yang memicu **auto build & release** (lihat 3.4).

### 3.4 Auto Push & CI/CD Trigger
1. **Granular commit** untuk setiap task/increment kecil yang selesai (idealnya 1 commit = 1 patch version).
2. Setelah commit dipastikan **bebas error** (`flutter analyze` clean + test pass), Agent **WAJIB** menjalankan:
   ```
   git push origin <branch-aktif>
   ```
   > ⚠️ Push ke `main` adalah trigger otomatis pipeline GitHub Actions: build APK (Android), build IPA (iOS, jika runner tersedia), build Web, lalu deploy Web ke Cloudflare Pages.
3. Setiap merge ke `main` **wajib** dibarengi git tag versi, contoh: `git tag v0.3.2 && git push origin v0.3.2`.

---

## 4. Testing & Quality Assurance

- **Sebelum menandai sebuah versi "selesai"**, Agent wajib memverifikasi:
  - [ ] `flutter analyze` — 0 error, 0 warning.
  - [ ] `flutter test` — seluruh test pass.
  - [ ] Manual checklist fitur versi tersebut (disediakan di tiap Execution Prompt) tercentang semua.
  - [ ] Tidak ada TODO/FIXME kritikal tertinggal tanpa catatan di PR description.
- Untuk fitur yang menyentuh **pembayaran (Midtrans)**: wajib diuji di **sandbox environment** dulu, tidak boleh langsung production key.
- Untuk fitur **upload/storage (Cloudflare R2)**: wajib diuji dengan file dummy sebelum dianggap selesai, pastikan signed URL expired sesuai waktu yang ditentukan.

---

## 5. Communication Protocol (Kapan Agent Harus Bertanya)

Agent **wajib berhenti dan bertanya ke Project Manager (user)** sebelum melanjutkan jika:
- Ada ambiguitas pada requirement yang berdampak ke struktur database atau arsitektur inti.
- Perlu keputusan yang berdampak biaya (mis. ganti tier Cloudflare/Supabase, upgrade paket Midtrans).
- Menemukan requirement di Master Prompt yang bertentangan dengan constraint teknis Flutter/Cupertino/Supabase.
- Sebuah task ternyata jauh lebih besar dari estimasi versi yang dialokasikan (butuh split jadi beberapa patch version tambahan).

Agent **tidak perlu bertanya** untuk keputusan implementasi teknis kecil (naming variable, struktur file internal, dsb) — cukup ikuti konvensi di dokumen ini.

---

## 6. Restrictions (Yang Dilarang) ❌

- ❌ Dilarang `git push` ke `main` jika masih ada error/warning dari `flutter analyze` atau test yang gagal.
- ❌ Dilarang commit credential/API key/service role key dalam bentuk apapun ke repository (termasuk di file `.env` — pastikan `.env` masuk `.gitignore`).
- ❌ Dilarang membuat tabel Supabase baru tanpa RLS policy.
- ❌ Dilarang mengubah struktur folder utama (`lib/core`, `lib/features`) tanpa instruksi eksplisit dari PM.
- ❌ Dilarang menjalankan perintah destruktif (`DROP TABLE`, `rm -rf`, `git push --force` ke `main`) tanpa persetujuan eksplisit.
- ❌ Dilarang skip urutan versi (mis. loncat dari implementasi 0.2.x langsung ke 0.5.x) tanpa alasan yang didiskusikan dengan PM.
- ❌ Dilarang menggunakan production key (Midtrans/Supabase/Cloudflare) untuk testing — selalu gunakan environment sandbox/staging sampai fase 0.9.x (Hardening & Release Prep).

---

*Dokumen ini adalah aturan tetap sepanjang project. Update hanya dilakukan oleh Project Manager (user), bukan oleh AI Agent secara sepihak.*
