# Real Device Verification — Background Playback & Lock Screen (0.9.10)

**Status: BUTUH PENGUJIAN MANUAL MANUSIA — Agent tidak memiliki akses ke perangkat fisik Android**

Agent berjalan di lingkungan container Linux tanpa akses ke device Android fisik, emulator pun tidak tersedia di CI (hanya build APK). Oleh karena itu checklist berikut **belum bisa diverifikasi otomatis** dan wajib dilakukan oleh PM/QA manual sebelum tag v1.0.0.

## Checklist (wajib di-centang manual)

- [ ] Playback lanjut saat app di-minimize (home button)
- [ ] Notification media control muncul & berfungsi (play/pause/next/prev, menampilkan cover/title)
- [ ] Playback lanjut saat layar dikunci (lock screen)
- [ ] Tidak ada audio glitch saat unlock/lock transition
- [ ] Audio focus: incoming call pause, resume setelah call
- [ ] Background playback tetap jalan 10 menit tanpa foreground

## Cara test

1. Install APK dari `build/app/outputs/flutter-apk/app-release.apk` (CI artifact) di device Android fisik (Android 11+).
2. Login sebagai free/premium, play lagu, minimize, lock.
3. Catat hasil di PR description v1.0.0.

> Jangan asumsikan lulus dari emulator — hanya device fisik yang valid per Scope 0.9.10 Task 4.
