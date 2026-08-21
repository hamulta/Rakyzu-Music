# Play Store Console — Submission Aktual (0.9.10)

**Status: BUTUH AKSI MANUAL PM — Agent tidak memiliki akses kredensial Play Console**

Dari `docs/PLAY_STORE_0.9.8.md` sudah ada persiapan (icon, screenshots, privacy policy). Langkah aktual yang **harus dilakukan PM secara manual**:

## 1. Akun Developer Play Console

- Jika belum punya: daftar di https://play.google.com/console (biaya satu kali $25, butuh kartu kredit, email terverifikasi, DUNS tidak perlu untuk individu).
- Jika sudah punya: pastikan user PM adalah Owner/Admin.

## 2. Buat App Baru

- Create app → Name: Rakyzu Music, Package: com.rakyzu.rakyzu_music, Category: Music & Audio.

## 3. Upload Build ke Internal Testing Track (bukan Production)

- Ambil APK/AAB dari CI artifact `app-release-apk` (main push) atau build lokal `flutter build appbundle`.
- Upload ke **Testing → Internal testing** → Create new release → upload AAB/APK → Save.

## 4. Lengkapi Listing

- App icon 512x512, feature graphic 1024x500, screenshots (dari `docs/PLAY_STORE_0.9.8.md`), short/long description, privacy policy URL (host dari `/privacy` atau `rakyzu.com/privacy`).
- Content rating, data safety (collect email, play history, ad ID via Start.io).

## 5. Cloudflare Pages Production

- Dari staging `rakyzu-music.pages.dev` → production domain final (misal `rakyzu.com`) via Cloudflare Dashboard → Pages → Custom domain.

## Yang sudah bisa otomatis

- Build Web & APK di CI sudah hijau (Start.io App ID 207228132, Midtrans Sandbox).
- Privacy Policy & TOS halaman sudah ada di `/privacy` & `/terms`.

**Agent tidak bisa upload tanpa kredensial Play Console — laporkan langkah di atas ke PM.**
