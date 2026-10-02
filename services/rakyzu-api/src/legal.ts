const SITE_ORIGIN = "https://api.rakyzu.my.id";
const CONTACT_EMAIL = "rakyzudev@gmail.com";
const EFFECTIVE_DATE = "2 Oktober 2026";

type LegalPage = "home" | "privacy" | "terms" | "data-deletion";

const PAGE_META: Record<LegalPage, { title: string; description: string; path: string }> = {
  home: {
    title: "Rakyzu Music — Musik, artis, dan komunitas dalam satu aplikasi",
    description: "Situs resmi Rakyzu Music, layanan streaming musik independen untuk Android.",
    path: "/",
  },
  privacy: {
    title: "Kebijakan Privasi — Rakyzu Music",
    description: "Kebijakan Privasi Rakyzu Music dan penjelasan pemrosesan data pengguna.",
    path: "/privacy",
  },
  terms: {
    title: "Ketentuan Layanan — Rakyzu Music",
    description: "Ketentuan penggunaan aplikasi dan layanan Rakyzu Music.",
    path: "/terms",
  },
  "data-deletion": {
    title: "Penghapusan Data — Rakyzu Music",
    description: "Cara meminta penghapusan akun dan data Rakyzu Music.",
    path: "/data-deletion",
  },
};

const STYLES = `
:root {
  color-scheme: dark;
  --bg: #09090d;
  --surface: #14141e;
  --surface-raised: #1b1b27;
  --text: #f7f7fb;
  --muted: #b8b8c8;
  --accent: #41c3d6;
  --accent-strong: #00e5d4;
  --border: #343442;
  --danger: #ff8b95;
  --max: 72rem;
  --reading: 48rem;
}
* { box-sizing: border-box; }
html { scroll-behavior: smooth; }
body {
  margin: 0;
  background:
    radial-gradient(circle at 15% 0%, rgba(65,195,214,.13), transparent 28rem),
    var(--bg);
  color: var(--text);
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  font-size: 1rem;
  line-height: 1.72;
}
a { color: var(--accent); text-underline-offset: .2em; }
a:hover { color: var(--accent-strong); }
a:focus-visible, button:focus-visible, summary:focus-visible {
  outline: .18rem solid var(--accent-strong);
  outline-offset: .2rem;
  border-radius: .2rem;
}
.skip-link {
  position: fixed;
  z-index: 20;
  top: .75rem;
  left: .75rem;
  transform: translateY(-180%);
  padding: .65rem 1rem;
  background: var(--text);
  color: var(--bg);
  border-radius: .5rem;
}
.skip-link:focus { transform: none; }
.site-header {
  position: sticky;
  z-index: 10;
  top: 0;
  border-bottom: 1px solid rgba(52,52,66,.8);
  background: rgba(9,9,13,.9);
  backdrop-filter: blur(1rem);
}
.nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  width: min(calc(100% - 2rem), var(--max));
  min-height: 4.5rem;
  margin: auto;
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: .7rem;
  color: var(--text);
  font-weight: 800;
  letter-spacing: -.02em;
  text-decoration: none;
  white-space: nowrap;
}
.mark {
  display: grid;
  width: 2rem;
  height: 2rem;
  place-items: center;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent-strong), var(--accent));
  color: #052b2b;
  font-size: .85rem;
  box-shadow: 0 0 1.5rem rgba(0,229,212,.2);
}
.nav-links { display: flex; flex-wrap: wrap; gap: .25rem .9rem; justify-content: flex-end; }
.nav-links a { color: var(--muted); font-size: .9rem; text-decoration: none; }
.nav-links a[aria-current="page"], .nav-links a:hover { color: var(--text); }
main { width: min(calc(100% - 2rem), var(--max)); margin: auto; }
.hero { padding: clamp(4rem, 10vw, 8rem) 0 clamp(3rem, 8vw, 6rem); }
.eyebrow {
  color: var(--accent-strong);
  font-size: .78rem;
  font-weight: 800;
  letter-spacing: .14em;
  text-transform: uppercase;
}
h1, h2, h3 { line-height: 1.18; letter-spacing: -.025em; text-wrap: balance; }
h1 { max-width: 16ch; margin: .7rem 0 1.25rem; font-size: clamp(2.5rem, 8vw, 5.7rem); }
h2 { margin: 3rem 0 1rem; font-size: clamp(1.55rem, 4vw, 2.15rem); }
h3 { margin: 2rem 0 .65rem; font-size: 1.18rem; }
p, li { color: var(--muted); }
strong { color: var(--text); }
.lead { max-width: 44rem; font-size: clamp(1.05rem, 2.5vw, 1.3rem); }
.actions { display: flex; flex-wrap: wrap; gap: .75rem; margin-top: 2rem; }
.button {
  display: inline-flex;
  min-height: 2.9rem;
  align-items: center;
  justify-content: center;
  padding: .7rem 1rem;
  border: 1px solid var(--border);
  border-radius: 999px;
  color: var(--text);
  font-weight: 750;
  text-decoration: none;
}
.button.primary { border-color: transparent; background: var(--accent-strong); color: #052b2b; }
.button:hover { border-color: var(--accent); color: var(--text); }
.button.primary:hover { background: var(--accent); color: #052b2b; }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; margin: 2rem 0; }
.card, .notice, .toc {
  border: 1px solid var(--border);
  border-radius: 1rem;
  background: linear-gradient(145deg, rgba(27,27,39,.94), rgba(20,20,30,.94));
  padding: 1.25rem;
}
.card h2, .card h3 { margin-top: 0; }
.legal-header { max-width: var(--reading); padding: clamp(3.25rem, 8vw, 6rem) 0 1.5rem; }
.legal-header h1 { max-width: 18ch; font-size: clamp(2.25rem, 7vw, 4.4rem); }
.meta { color: var(--muted); font-size: .9rem; }
.legal-layout { display: grid; grid-template-columns: minmax(0, 1fr) 16rem; gap: 4rem; align-items: start; }
.legal-content { max-width: var(--reading); padding-bottom: 5rem; }
.legal-content section { scroll-margin-top: 6rem; }
.toc { position: sticky; top: 6rem; }
.toc strong { display: block; margin-bottom: .5rem; }
.toc ol { margin: 0; padding-left: 1.25rem; }
.toc li { margin: .25rem 0; font-size: .9rem; }
.toc a { color: var(--muted); text-decoration: none; }
.toc a:hover { color: var(--accent); }
.notice { margin: 1.5rem 0; border-color: rgba(65,195,214,.55); }
.notice > :first-child { margin-top: 0; }
.notice > :last-child { margin-bottom: 0; }
.table-wrap { max-width: 100%; overflow-x: auto; border: 1px solid var(--border); border-radius: .8rem; }
table { width: 100%; min-width: 40rem; border-collapse: collapse; }
th, td { padding: .9rem 1rem; border-bottom: 1px solid var(--border); text-align: left; vertical-align: top; }
th { color: var(--text); background: var(--surface-raised); }
tr:last-child td { border-bottom: 0; }
ul, ol { padding-left: 1.35rem; }
li + li { margin-top: .4rem; }
code { padding: .1rem .35rem; border-radius: .3rem; background: var(--surface-raised); color: var(--text); }
.site-footer { border-top: 1px solid var(--border); background: #08080b; }
.footer-inner {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 1rem;
  width: min(calc(100% - 2rem), var(--max));
  margin: auto;
  padding: 2rem 0;
}
.footer-inner p { margin: 0; font-size: .9rem; }
.footer-links { display: flex; flex-wrap: wrap; gap: 1rem; }
@media (max-width: 52rem) {
  .nav { align-items: flex-start; flex-direction: column; padding: .8rem 0; }
  .nav-links { justify-content: flex-start; }
  .grid { grid-template-columns: 1fr; }
  .legal-layout { grid-template-columns: 1fr; gap: 0; }
  .toc { position: static; grid-row: 1; margin-bottom: 1rem; }
}
@media (max-width: 32rem) {
  .nav-links { gap: .35rem .75rem; }
  .nav-links a { font-size: .82rem; }
  .hero { padding-top: 3rem; }
  .card, .notice, .toc { padding: 1rem; }
}
@media (prefers-reduced-motion: reduce) { html { scroll-behavior: auto; } }
@media print {
  :root { color-scheme: light; --bg: #fff; --surface: #fff; --surface-raised: #f4f4f4; --text: #111; --muted: #333; --border: #bbb; }
  body { background: #fff; }
  .site-header, .site-footer, .toc, .actions, .skip-link { display: none; }
  main { width: 100%; }
  .legal-layout { display: block; }
  a { color: inherit; }
}
`;

function navigation(current: LegalPage): string {
  const link = (page: LegalPage, label: string) => {
    const meta = PAGE_META[page];
    const currentAttribute = page === current ? ' aria-current="page"' : "";
    return `<a href="${meta.path}"${currentAttribute}>${label}</a>`;
  };
  return `<header class="site-header">
    <nav class="nav" aria-label="Navigasi utama">
      <a class="brand" href="/"><span class="mark" aria-hidden="true">R</span><span>Rakyzu Music</span></a>
      <div class="nav-links">
        ${link("home", "Beranda")}
        ${link("privacy", "Privasi")}
        ${link("terms", "Ketentuan")}
        ${link("data-deletion", "Hapus data")}
      </div>
    </nav>
  </header>`;
}

function layout(page: LegalPage, body: string): string {
  const meta = PAGE_META[page];
  const canonical = `${SITE_ORIGIN}${meta.path}`;
  return `<!doctype html>
<html lang="id">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="${meta.description}">
  <meta name="theme-color" content="#09090d">
  <meta property="og:type" content="website">
  <meta property="og:site_name" content="Rakyzu Music">
  <meta property="og:title" content="${meta.title}">
  <meta property="og:description" content="${meta.description}">
  <meta property="og:url" content="${canonical}">
  <link rel="canonical" href="${canonical}">
  <title>${meta.title}</title>
  <style>${STYLES}</style>
</head>
<body>
  <a class="skip-link" href="#main-content">Lewati ke konten utama</a>
  ${navigation(page)}
  ${body}
  <footer class="site-footer">
    <div class="footer-inner">
      <p>© 2026 Rakyzu Development. Rakyzu Music adalah produk musik independen.</p>
      <nav class="footer-links" aria-label="Tautan legal">
        <a href="/privacy">Kebijakan Privasi</a>
        <a href="/terms">Ketentuan Layanan</a>
        <a href="/data-deletion">Penghapusan Data</a>
      </nav>
    </div>
  </footer>
</body>
</html>`;
}

function homePage(): string {
  return layout("home", `<main id="main-content">
    <section class="hero" aria-labelledby="home-title">
      <p class="eyebrow">Rakyzu Development mempersembahkan</p>
      <h1 id="home-title">Dengarkan musik dengan cara yang terasa milikmu.</h1>
      <p class="lead">Rakyzu Music adalah layanan streaming musik independen untuk Android yang menyatukan katalog artis, album, playlist, lirik tersinkron, pemutaran offline, dan rekomendasi personal dalam pengalaman yang aman dan ramah pengguna.</p>
      <div class="actions">
        <a class="button primary" href="/privacy">Baca Kebijakan Privasi</a>
        <a class="button" href="/data-deletion">Kelola atau hapus data</a>
      </div>
    </section>
    <section aria-labelledby="features-title">
      <p class="eyebrow">Tentang layanan</p>
      <h2 id="features-title">Dibangun untuk pendengar dan kreator</h2>
      <div class="grid">
        <article class="card"><h3>Temukan musik</h3><p>Jelajahi rilisan, artis, playlist, rekomendasi editorial, dan rekomendasi cerdas berdasarkan interaksi Anda di Rakyzu Music.</p></article>
        <article class="card"><h3>Dengarkan di mana saja</h3><p>Putar antrean musik dengan kontrol yang konsisten dan simpan konten yang memenuhi syarat untuk pengalaman offline yang terlindungi.</p></article>
        <article class="card"><h3>Ruang bagi artis</h3><p>Profil artis, katalog, karya visual, audio, lirik, analitik, dan alur publikasi dikelola melalui izin berbasis peran.</p></article>
      </div>
    </section>
    <section class="notice" aria-labelledby="signin-title">
      <h2 id="signin-title">Masuk dengan aman</h2>
      <p>Rakyzu Music mendukung akun email dan, ketika telah diaktifkan, pilihan masuk melalui Google, Facebook, atau Apple. Login sosial hanya digunakan untuk autentikasi dan menghubungkan identitas dasar yang Anda izinkan—bukan untuk iklan dan bukan untuk menjual data.</p>
      <p>Halaman ini, <a href="/privacy">Kebijakan Privasi</a>, <a href="/terms">Ketentuan Layanan</a>, dan <a href="/data-deletion">petunjuk penghapusan data</a> dapat dibaca tanpa akun.</p>
    </section>
    <section aria-labelledby="contact-title" style="padding: 2rem 0 5rem">
      <h2 id="contact-title">Kontak</h2>
      <p>Pertanyaan tentang aplikasi, privasi, atau akun dapat dikirim ke <a href="mailto:${CONTACT_EMAIL}">${CONTACT_EMAIL}</a>.</p>
    </section>
  </main>`);
}

function privacyPage(): string {
  return layout("privacy", `<main id="main-content">
    <header class="legal-header">
      <p class="eyebrow">Dokumen legal</p>
      <h1>Kebijakan Privasi Rakyzu Music</h1>
      <p class="lead">Dokumen ini menjelaskan secara transparan data apa yang kami proses, alasan pemrosesan, pihak yang membantu kami, pilihan Anda, dan cara menjalankan hak privasi.</p>
      <p class="meta"><strong>Berlaku dan terakhir diperbarui:</strong> ${EFFECTIVE_DATE}</p>
    </header>
    <div class="legal-layout">
      <article class="legal-content">
        <section id="ringkasan">
          <h2>1. Ringkasan penting</h2>
          <div class="notice">
            <p><strong>Rakyzu Music tidak menjual data pribadi Anda dan tidak membagikannya untuk iklan perilaku lintas konteks.</strong> Kami menggunakan data untuk menyediakan akun, streaming, pustaka, personalisasi dalam layanan, dukungan, keamanan, pembayaran bila tersedia, dan pemenuhan kewajiban hukum.</p>
          </div>
          <p>Kebijakan ini berlaku pada aplikasi Rakyzu Music, API resmi, situs legal ini, fitur artis dan administrasi, komunikasi layanan, serta fitur lain yang menautkan kebijakan ini. Rakyzu Development (selanjutnya “Rakyzu”, “kami”, atau “milik kami”) bertindak sebagai pengelola/pengendali data untuk layanan tersebut.</p>
          <p>Dengan menggunakan layanan, Anda menyatakan telah membaca kebijakan ini. Jika Anda tidak menyetujuinya, jangan berikan data dan hentikan penggunaan layanan. Persetujuan bukan satu-satunya dasar pemrosesan; pada bagian berikut kami menjelaskan dasar yang relevan.</p>
        </section>

        <section id="cakupan">
          <h2>2. Identitas dan cakupan</h2>
          <ul>
            <li><strong>Produk:</strong> Rakyzu Music.</li>
            <li><strong>Pengelola:</strong> Rakyzu Development.</li>
            <li><strong>Aplikasi Android:</strong> <code>my.id.rakyzumusic</code>.</li>
            <li><strong>Kontak privasi:</strong> <a href="mailto:${CONTACT_EMAIL}">${CONTACT_EMAIL}</a>.</li>
            <li><strong>Wilayah operasi:</strong> Indonesia, dengan kemungkinan akses dari wilayah lain.</li>
          </ul>
          <p>Kebijakan ini tidak mengatur situs atau layanan independen milik pihak lain. Jika Anda mengikuti tautan eksternal atau menggunakan toko aplikasi dan penyedia identitas, kebijakan pihak tersebut juga berlaku atas pemrosesan yang mereka lakukan sendiri.</p>
        </section>

        <section id="data">
          <h2>3. Data yang kami kumpulkan</h2>
          <div class="table-wrap" role="region" aria-label="Kategori data pribadi" tabindex="0">
            <table>
              <thead><tr><th>Kategori</th><th>Contoh data</th><th>Sumber</th></tr></thead>
              <tbody>
                <tr><td>Akun dan identitas</td><td>Alamat email, pengenal akun, nama tampilan, foto profil, waktu pembuatan akun, status konfirmasi, dan preferensi akun.</td><td>Anda, penyedia login, dan sistem autentikasi.</td></tr>
                <tr><td>Login sosial</td><td>Pengenal penyedia, email, nama, dan foto profil jika tersedia serta Anda izinkan.</td><td>Google, Facebook/Meta, atau Apple.</td></tr>
                <tr><td>Profil dan peran</td><td>Profil pengguna atau artis, biografi, karya visual, lencana/status, peran organisasi, izin, undangan, dan penerimaan persyaratan artis.</td><td>Anda dan pejabat Rakyzu yang berwenang.</td></tr>
                <tr><td>Aktivitas musik</td><td>Track, album, atau artis yang diputar; durasi dengar; suka; simpan; ikuti; playlist; antrean; unduhan; dan interaksi rekomendasi.</td><td>Penggunaan layanan Anda.</td></tr>
                <tr><td>Pencarian</td><td>Kueri pencarian yang dikirim ke layanan. Riwayat pencarian terbaru di perangkat bersifat opsional, dibatasi, dan disimpan lokal secara terenkripsi.</td><td>Anda dan perangkat Anda.</td></tr>
                <tr><td>Konten pengguna/kreator</td><td>Nama dan deskripsi playlist, audio, sampul, gambar profil, metadata rilisan, lirik, file LRC/SRT, dan materi moderasi.</td><td>Anda, artis, atau staf berwenang.</td></tr>
                <tr><td>Teknis dan keamanan</td><td>Alamat IP, waktu permintaan, jenis perangkat/aplikasi, sistem operasi, user-agent, pengenal sesi, log error, kejadian keamanan, dan data diagnostik terbatas.</td><td>Perangkat, API, dan penyedia infrastruktur.</td></tr>
                <tr><td>Perkiraan lokasi</td><td>Negara atau wilayah kasar yang diturunkan dari alamat IP untuk keamanan, lisensi, atau analitik agregat. Kami tidak meminta lokasi GPS.</td><td>Jaringan/perangkat.</td></tr>
                <tr><td>Transaksi</td><td>Produk, mata uang, jumlah, status hak akses, referensi transaksi, tanda terima/status pembayaran, dan penyedia pembayaran. Kami tidak menyimpan nomor kartu lengkap.</td><td>Toko aplikasi atau penyedia pembayaran.</td></tr>
                <tr><td>Dukungan dan tata kelola</td><td>Isi korespondensi, laporan, banding, permintaan akses/ekspor/penghapusan, tindakan moderasi, dan catatan audit.</td><td>Anda dan staf berwenang.</td></tr>
              </tbody>
            </table>
          </div>
          <h3>Data yang tidak kami minta</h3>
          <p>Aplikasi tidak meminta akses ke kontak, SMS, mikrofon, kamera, atau lokasi presisi untuk fungsi inti saat ini. Jangan mengirim kata sandi, token, nomor kartu lengkap, dokumen identitas, atau data sensitif lain melalui kolom bebas dan email kecuali kami secara khusus dan sah memintanya untuk verifikasi.</p>
        </section>

        <section id="tujuan">
          <h2>4. Tujuan dan dasar pemrosesan</h2>
          <div class="table-wrap" role="region" aria-label="Tujuan dan dasar pemrosesan" tabindex="0">
            <table>
              <thead><tr><th>Tujuan</th><th>Data terkait</th><th>Dasar pemrosesan</th></tr></thead>
              <tbody>
                <tr><td>Membuat, mengautentikasi, dan memelihara akun</td><td>Akun, login sosial, sesi, profil</td><td>Pelaksanaan kontrak dan langkah yang Anda minta sebelum menggunakan layanan.</td></tr>
                <tr><td>Menyediakan streaming, unduhan, pustaka, playlist, lirik, dan sinkronisasi</td><td>Aktivitas musik, perangkat, konten, hak akses</td><td>Pelaksanaan kontrak.</td></tr>
                <tr><td>Menyusun rekomendasi personal dan editorial</td><td>Riwayat dengar, suka, simpan, interaksi</td><td>Pelaksanaan layanan dan kepentingan sah untuk meningkatkan relevansi; persetujuan bila diwajibkan.</td></tr>
                <tr><td>Mengelola artis, katalog, publikasi, dan moderasi</td><td>Profil/peran, konten, audit, laporan</td><td>Pelaksanaan kontrak dan kepentingan sah dalam tata kelola serta keamanan katalog.</td></tr>
                <tr><td>Memproses langganan atau pembelian</td><td>Transaksi dan hak akses</td><td>Pelaksanaan kontrak dan kewajiban hukum/akuntansi.</td></tr>
                <tr><td>Mencegah penipuan, penyalahgunaan, dan akses tidak sah</td><td>Teknis, keamanan, akun, audit</td><td>Kepentingan sah dan kewajiban hukum.</td></tr>
                <tr><td>Menjawab dukungan dan permintaan privasi</td><td>Korespondensi, akun, verifikasi, permintaan</td><td>Pelaksanaan kontrak, kewajiban hukum, dan kepentingan sah.</td></tr>
                <tr><td>Mengirim email transaksional dan notifikasi layanan</td><td>Email, status akun, preferensi notifikasi</td><td>Pelaksanaan kontrak, keamanan akun, atau persetujuan untuk komunikasi opsional.</td></tr>
                <tr><td>Menganalisis dan memperbaiki layanan</td><td>Log, performa, data penggunaan agregat</td><td>Kepentingan sah; kami meminimalkan atau mengagregasikan data jika memungkinkan.</td></tr>
                <tr><td>Memenuhi proses hukum dan melindungi hak</td><td>Data yang relevan dengan permintaan sah</td><td>Kewajiban hukum dan kepentingan sah.</td></tr>
              </tbody>
            </table>
          </div>
          <p>Jika kami mengandalkan persetujuan, Anda dapat menariknya kapan saja tanpa memengaruhi keabsahan pemrosesan sebelum penarikan. Fitur tertentu mungkin tidak dapat berfungsi tanpa data yang diperlukan untuk menjalankannya.</p>
        </section>

        <section id="login-sosial">
          <h2>5. Google, Facebook, dan Sign in with Apple</h2>
          <p>Jika login sosial tersedia dan Anda memilihnya, Rakyzu Music mengarahkan Anda ke penyedia terkait. Penyedia mengautentikasi Anda dan mengirim data yang tercantum pada layar persetujuan, biasanya pengenal unik, nama, email, dan foto profil bila diizinkan. <strong>Kami tidak menerima kata sandi akun Google, Facebook, atau Apple Anda.</strong></p>
          <ul>
            <li>Data login digunakan untuk membuat atau menghubungkan akun Rakyzu Music, menjaga sesi, mencegah penyalahgunaan, dan menampilkan profil.</li>
            <li>Data tersebut tidak digunakan untuk iklan pihak ketiga, tidak dijual, dan tidak digunakan untuk melatih model AI tujuan umum.</li>
            <li>Akses Google dipakai sesuai <em>Google API Services User Data Policy</em>, termasuk persyaratan Limited Use, sejauh berlaku.</li>
            <li>Anda dapat mencabut koneksi melalui pengaturan akun penyedia. Pencabutan menghentikan akses berikutnya tetapi tidak otomatis menghapus akun atau data Rakyzu Music yang telah diproses secara sah.</li>
            <li>Untuk menghapus data di Rakyzu Music, ikuti halaman <a href="/data-deletion">Penghapusan Data</a>. Permintaan tersebut tidak menghapus akun Google, Facebook, atau Apple Anda.</li>
          </ul>
          <p>Jika penyedia memberikan email relay privat (misalnya melalui Apple), kami memproses alamat relay tersebut sebagai alamat akun sampai Anda memperbarui atau menghapus akun sesuai opsi yang tersedia.</p>
        </section>

        <section id="personalisasi">
          <h2>6. Rekomendasi, profil, dan keputusan otomatis</h2>
          <p>Kami dapat menggunakan sinyal dalam layanan—seperti pemutaran, suka, simpan, ikuti, konteks antrean, dan umpan balik—untuk mengurutkan konten dan membuat rekomendasi. Tujuannya adalah personalisasi pengalaman musik, bukan menentukan kelayakan kredit, pekerjaan, perumahan, atau keputusan lain yang menimbulkan dampak hukum atau serupa secara signifikan.</p>
          <p>Sistem keamanan atau moderasi dapat menandai aktivitas untuk pemeriksaan. Tindakan pembatasan akun dapat didukung oleh sinyal otomatis, namun tersedia proses banding dan peninjauan berwenang sesuai fitur layanan dan hukum yang berlaku.</p>
        </section>

        <section id="penyimpanan-lokal">
          <h2>7. Penyimpanan di perangkat</h2>
          <p>Aplikasi menyimpan data yang diperlukan untuk pengalaman cepat dan offline, seperti sesi terenkripsi, metadata cache, antrean, preferensi, unduhan terenkripsi, dan—bila Anda mengaktifkannya—maksimal sejumlah terbatas riwayat pencarian terbaru. Data lokal dapat hilang saat Anda keluar, menghapus unduhan, membersihkan data aplikasi, atau mencopot aplikasi, bergantung pada jenis data.</p>
          <p>Kami tidak memakai cookie iklan pada halaman legal ini. Situs ini tidak memuat skrip analitik, piksel pemasaran, atau font eksternal.</p>
        </section>

        <section id="berbagi">
          <h2>8. Kapan data dibagikan</h2>
          <p>Kami tidak menjual data pribadi. Kami dapat memberikan data terbatas kepada kategori penerima berikut hanya sesuai kebutuhan dan perjanjian yang relevan:</p>
          <ul>
            <li><strong>Supabase:</strong> autentikasi, database, kebijakan akses, dan fungsi backend.</li>
            <li><strong>Cloudflare:</strong> API, perlindungan jaringan, pengiriman konten, dan penyimpanan objek/media melalui Workers dan R2.</li>
            <li><strong>Resend:</strong> pengiriman email transaksional seperti konfirmasi akun dan pemulihan kata sandi.</li>
            <li><strong>Google, Meta/Facebook, dan Apple:</strong> hanya jika Anda memilih autentikasi penyedia tersebut.</li>
            <li><strong>Google Play, Apple App Store, atau penyedia pembayaran:</strong> distribusi aplikasi, pembelian, bukti transaksi, dan pengelolaan hak akses bila fitur pembayaran tersedia.</li>
            <li><strong>Penyedia profesional dan otoritas:</strong> penasihat, auditor, penegak hukum, pengadilan, atau regulator bila diwajibkan hukum atau diperlukan untuk melindungi hak dan keselamatan.</li>
            <li><strong>Transaksi organisasi:</strong> calon penerus yang sah dalam merger, restrukturisasi, pendanaan, atau pengalihan aset, dengan perlindungan kerahasiaan dan pemberitahuan bila diwajibkan.</li>
          </ul>
          <p>Artis atau pengelola playlist kolaboratif dapat melihat identitas publik dan kontribusi yang memang Anda pilih untuk dibagikan dalam fitur tersebut. Jangan menambahkan informasi pribadi yang tidak ingin Anda tampilkan kepada kolaborator atau publik.</p>
        </section>

        <section id="transfer">
          <h2>9. Pemrosesan lintas negara</h2>
          <p>Penyedia infrastruktur dapat memproses data di negara selain tempat tinggal Anda. Negara tersebut mungkin mempunyai aturan perlindungan data yang berbeda. Kami berupaya memakai penyedia tepercaya dan perlindungan kontraktual, organisasi, serta teknis yang sesuai, termasuk mekanisme transfer yang diakui hukum bila diwajibkan.</p>
        </section>

        <section id="retensi">
          <h2>10. Retensi data</h2>
          <p>Kami menyimpan data hanya selama diperlukan untuk tujuan yang dijelaskan, menyediakan akun, memenuhi kontrak, menjaga keamanan, menyelesaikan sengketa, dan mematuhi kewajiban hukum. Lama penyimpanan ditentukan berdasarkan jenis data, sensitivitas, risiko penyalahgunaan, kebutuhan operasional, status akun, serta periode hukum/akuntansi yang berlaku.</p>
          <ul>
            <li>Data akun dan pustaka umumnya disimpan selama akun aktif.</li>
            <li>Log keamanan dan audit disimpan selama periode terbatas yang wajar untuk investigasi, pencegahan penipuan, dan kepatuhan.</li>
            <li>Catatan transaksi dapat disimpan selama diwajibkan oleh hukum pajak, akuntansi, atau penyelesaian sengketa.</li>
            <li>Setelah permintaan penghapusan disetujui, data dihapus atau dianonimkan, kecuali bagian yang perlu dipertahankan secara sah. Salinan cadangan dapat memerlukan waktu terbatas untuk berputar keluar dan tetap dilindungi selama periode tersebut.</li>
            <li>Data agregat atau anonim yang tidak lagi dapat dikaitkan dengan individu dapat dipertahankan untuk statistik dan perbaikan produk.</li>
          </ul>
        </section>

        <section id="keamanan">
          <h2>11. Keamanan</h2>
          <p>Kami menerapkan langkah teknis dan organisasi yang proporsional, termasuk enkripsi saat transit, penyimpanan sesi dan data lokal tertentu secara terenkripsi, kontrol akses berbasis peran, Row Level Security pada data yang dapat diakses klien, endpoint media terautentikasi, prinsip hak akses minimum, audit tindakan administratif, serta pemantauan penyalahgunaan.</p>
          <p>Tidak ada sistem yang sepenuhnya bebas risiko. Anda bertanggung jawab menjaga kredensial, perangkat, dan akses ke email Anda. Segera hubungi kami jika mencurigai akses tanpa izin. Jika insiden data menimbulkan kewajiban pemberitahuan, kami akan memberi tahu pihak dan otoritas terkait sesuai hukum.</p>
        </section>

        <section id="pilihan">
          <h2>12. Pilihan dan kontrol Anda</h2>
          <ul>
            <li>Memperbarui nama, foto profil, dan informasi akun yang tersedia.</li>
            <li>Mengelola suka, playlist, ikuti, unduhan, dan preferensi notifikasi.</li>
            <li>Menonaktifkan serta menghapus riwayat pencarian lokal melalui pengaturan yang tersedia.</li>
            <li>Mencabut izin notifikasi melalui pengaturan Android.</li>
            <li>Mencabut koneksi login sosial melalui akun penyedia.</li>
            <li>Meminta akses, koreksi, ekspor, pembatasan, keberatan, atau penghapusan sesuai hukum melalui email kontak kami.</li>
          </ul>
        </section>

        <section id="hak">
          <h2>13. Hak privasi berdasarkan wilayah</h2>
          <h3>Indonesia</h3>
          <p>Sesuai Undang-Undang Pelindungan Data Pribadi dan aturan yang berlaku, Anda dapat mempunyai hak untuk mendapatkan informasi, mengakses, melengkapi atau memperbaiki, mengakhiri pemrosesan, menghapus atau memusnahkan, menarik persetujuan, mengajukan keberatan atas keputusan tertentu, membatasi pemrosesan, memperoleh atau memindahkan data dalam format yang berlaku, serta mengajukan keluhan atau tuntutan.</p>
          <h3>Wilayah Ekonomi Eropa dan Britania Raya</h3>
          <p>Jika hukum GDPR/UK GDPR berlaku, Anda dapat mempunyai hak akses, perbaikan, penghapusan, pembatasan, portabilitas, keberatan, penarikan persetujuan, dan pengaduan kepada otoritas pengawas. Anda juga dapat meminta informasi tentang dasar transfer internasional.</p>
          <h3>California</h3>
          <p>Jika CCPA/CPRA berlaku, Anda dapat mempunyai hak untuk mengetahui, mengakses, memperbaiki, menghapus, menerima data, menolak penjualan atau pembagian untuk iklan lintas konteks, membatasi penggunaan informasi sensitif tertentu, dan bebas dari diskriminasi karena menggunakan hak. <strong>Kami tidak menjual data pribadi dan tidak membagikannya untuk iklan perilaku lintas konteks.</strong></p>
          <p>Hak dapat dibatasi oleh pengecualian hukum. Kami dapat meminta verifikasi yang wajar, misalnya konfirmasi dari email akun, agar data tidak diberikan atau dihapus atas permintaan pihak yang salah. Agen resmi harus menunjukkan kewenangan yang valid. Kami akan merespons dalam tenggat yang diwajibkan hukum yang berlaku.</p>
        </section>

        <section id="anak">
          <h2>14. Anak-anak</h2>
          <p>Rakyzu Music tidak ditujukan kepada anak di bawah usia 13 tahun. Jika hukum tempat tinggal Anda menetapkan usia persetujuan digital yang lebih tinggi, penggunaan memerlukan persetujuan orang tua/wali atau dasar sah lain. Kami tidak dengan sengaja mengumpulkan data anak tanpa otorisasi yang diperlukan. Orang tua atau wali yang meyakini anak telah memberikan data dapat menghubungi kami agar ditinjau dan dihapus.</p>
        </section>

        <section id="penghapusan">
          <h2>15. Penghapusan akun dan data</h2>
          <p>Anda dapat meminta penghapusan akun beserta data terkait melalui kontrol akun yang tersedia pada versi aplikasi Anda atau mengikuti <a href="/data-deletion">instruksi penghapusan data</a>. Penghapusan bersifat permanen setelah diproses dan dapat menghilangkan profil, pustaka, playlist, status artis, serta akses ke konten atau hak tertentu.</p>
          <p>Kami dapat mempertahankan data minimum bila diperlukan untuk transaksi, keamanan, pencegahan penipuan, klaim hukum, atau kewajiban peraturan. Menghapus aplikasi dari perangkat tidak dengan sendirinya menghapus akun di server.</p>
        </section>

        <section id="perubahan">
          <h2>16. Perubahan kebijakan</h2>
          <p>Kami dapat memperbarui kebijakan ini ketika produk, penyedia, atau hukum berubah. Tanggal pembaruan akan disesuaikan. Untuk perubahan material, kami akan memberikan pemberitahuan yang wajar melalui aplikasi, email, atau situs ini sebelum berlaku jika diwajibkan. Riwayat Git dan proses rilis internal membantu menjaga akuntabilitas perubahan dokumen.</p>
        </section>

        <section id="kontak">
          <h2>17. Kontak dan keluhan</h2>
          <p>Kirim pertanyaan, permintaan hak, atau keluhan ke:</p>
          <div class="notice">
            <p><strong>Rakyzu Development — Privasi Rakyzu Music</strong><br>
            Email: <a href="mailto:${CONTACT_EMAIL}">${CONTACT_EMAIL}</a><br>
            Subjek yang disarankan: <code>Permintaan Privasi Rakyzu Music</code></p>
          </div>
          <p>Jelaskan jenis permintaan, email akun, dan negara tempat tinggal. Jangan sertakan kata sandi atau token. Jika jawaban kami tidak memuaskan dan hukum memberi hak tersebut, Anda dapat menghubungi otoritas perlindungan data yang berwenang.</p>
        </section>
      </article>
      <aside class="toc" aria-label="Daftar isi">
        <strong>Daftar isi</strong>
        <ol>
          <li><a href="#ringkasan">Ringkasan</a></li>
          <li><a href="#cakupan">Identitas & cakupan</a></li>
          <li><a href="#data">Data yang dikumpulkan</a></li>
          <li><a href="#tujuan">Tujuan & dasar</a></li>
          <li><a href="#login-sosial">Login sosial</a></li>
          <li><a href="#personalisasi">Personalisasi</a></li>
          <li><a href="#penyimpanan-lokal">Data lokal</a></li>
          <li><a href="#berbagi">Berbagi data</a></li>
          <li><a href="#transfer">Transfer</a></li>
          <li><a href="#retensi">Retensi</a></li>
          <li><a href="#keamanan">Keamanan</a></li>
          <li><a href="#pilihan">Pilihan</a></li>
          <li><a href="#hak">Hak Anda</a></li>
          <li><a href="#anak">Anak-anak</a></li>
          <li><a href="#penghapusan">Penghapusan</a></li>
          <li><a href="#perubahan">Perubahan</a></li>
          <li><a href="#kontak">Kontak</a></li>
        </ol>
      </aside>
    </div>
  </main>`);
}

function termsPage(): string {
  return layout("terms", `<main id="main-content">
    <header class="legal-header">
      <p class="eyebrow">Dokumen legal</p>
      <h1>Ketentuan Layanan Rakyzu Music</h1>
      <p class="lead">Ketentuan ini mengatur akses dan penggunaan aplikasi, konten, akun, fitur artis, serta layanan Rakyzu Music.</p>
      <p class="meta"><strong>Berlaku dan terakhir diperbarui:</strong> ${EFFECTIVE_DATE}</p>
    </header>
    <div class="legal-layout">
      <article class="legal-content">
        <section id="penerimaan"><h2>1. Penerimaan ketentuan</h2><p>Dengan membuat akun atau menggunakan Rakyzu Music, Anda menyetujui ketentuan ini dan <a href="/privacy">Kebijakan Privasi</a>. Jika bertindak untuk organisasi, Anda menyatakan berwenang mengikat organisasi tersebut. Jika tidak setuju, jangan gunakan layanan.</p></section>
        <section id="kelayakan"><h2>2. Kelayakan dan akun</h2><p>Anda harus memenuhi usia minimum berdasarkan hukum setempat dan memberikan informasi yang akurat. Anda bertanggung jawab atas keamanan email, metode login, perangkat, serta aktivitas akun. Satu identitas tidak boleh digunakan untuk menyamar, menipu, atau menghindari penegakan.</p></section>
        <section id="layanan"><h2>3. Layanan dan perubahan</h2><p>Rakyzu Music menyediakan streaming, katalog, pencarian, pustaka, playlist, lirik, unduhan yang memenuhi syarat, rekomendasi, profil artis, administrasi, serta fitur terkait. Fitur dapat berbeda menurut versi, wilayah, perangkat, status akun, lisensi, dan paket. Kami dapat memperbaiki, menambah, menghentikan, atau membatasi fitur dengan pemberitahuan yang wajar bila perubahan berdampak material.</p></section>
        <section id="lisensi"><h2>4. Izin penggunaan</h2><p>Kami memberi Anda lisensi pribadi, terbatas, dapat dicabut, tidak eksklusif, dan tidak dapat dialihkan untuk memakai aplikasi sesuai ketentuan ini. Hak ini tidak memindahkan kepemilikan aplikasi, merek, desain, perangkat lunak, katalog, atau konten.</p></section>
        <section id="aturan"><h2>5. Penggunaan yang diperbolehkan</h2><p>Anda dilarang:</p><ul><li>menyalin, mengekstrak, merekam ulang, mendistribusikan, atau mengeksploitasi konten di luar izin;</li><li>mengakali autentikasi, hak akses, DRM, batas wilayah, pembayaran, atau proteksi media;</li><li>menggunakan bot, scraping, otomatisasi, reverse engineering, atau beban tidak wajar kecuali diizinkan hukum atau persetujuan tertulis;</li><li>mengunggah malware, materi ilegal, kebencian, eksploitasi, pelanggaran privasi, atau konten yang melanggar hak pihak lain;</li><li>melecehkan pengguna, memanipulasi metrik, melakukan spam, penipuan, atau penyamaran;</li><li>membagikan kredensial staf, menyalahgunakan peran, atau menghapus jejak audit.</li></ul></section>
        <section id="konten"><h2>6. Konten Anda</h2><p>Anda mempertahankan hak atas konten yang sah milik Anda. Dengan mengunggah konten, Anda memberi Rakyzu lisensi non-eksklusif, berlaku selama konten ada di layanan, untuk menyimpan, mengonversi, menampilkan, mendistribusikan, dan menyediakan konten sejauh diperlukan untuk menjalankan serta mempromosikan fitur Rakyzu Music. Anda menjamin mempunyai hak, lisensi, persetujuan, dan izin yang diperlukan, termasuk untuk audio, komposisi, lirik, gambar, nama, dan metadata.</p></section>
        <section id="artis"><h2>7. Artis dan pejabat berwenang</h2><p>Akun artis dan staf memperoleh fungsi berdasarkan peran. Undangan, verifikasi, persetujuan persyaratan, katalog, jadwal publikasi, moderasi, dan analitik dapat ditinjau. Pemegang peran wajib bertindak sesuai kewenangan, menjaga materi belum terbit, serta tidak mengakses atau mengubah data tanpa tujuan sah. Rakyzu dapat menangguhkan hak istimewa jika ada risiko atau penyalahgunaan.</p></section>
        <section id="hak"><h2>8. Hak kekayaan intelektual</h2><p>Nama Rakyzu Music, elemen merek, perangkat lunak, dan desain milik Rakyzu atau pemberi lisensinya. Musik dan karya terkait tetap milik pemegang hak masing-masing. Pelaporan dugaan pelanggaran harus menyertakan identitas pelapor, karya yang dilindungi, lokasi materi, dasar klaim, dan pernyataan beritikad baik.</p></section>
        <section id="pembayaran"><h2>9. Pembayaran dan langganan</h2><p>Jika pembelian tersedia, harga, periode, perpanjangan, manfaat, pajak, dan cara pembatalan ditampilkan sebelum transaksi. Pembayaran dapat diproses toko aplikasi atau penyedia lain sehingga ketentuan mereka juga berlaku. Pengembalian dana mengikuti hukum, kebijakan penyedia, dan syarat penawaran. Menghapus aplikasi tidak otomatis membatalkan langganan.</p></section>
        <section id="penegakan"><h2>10. Moderasi, penangguhan, dan penghentian</h2><p>Kami dapat menghapus konten, membatasi fitur, menangguhkan, atau menghentikan akun bila diperlukan untuk keamanan, pelanggaran ketentuan, kewajiban hukum, risiko terhadap pihak lain, atau nonpembayaran. Jika sesuai, kami memberikan alasan dan jalur banding. Anda dapat berhenti menggunakan layanan dan meminta penghapusan data kapan saja.</p></section>
        <section id="pihak-ketiga"><h2>11. Layanan pihak ketiga</h2><p>Login sosial, toko aplikasi, pembayaran, dan tautan eksternal dikendalikan pihak masing-masing. Kami tidak bertanggung jawab atas layanan independen tersebut, tetapi memilih penyedia pemrosesan untuk operasi Rakyzu secara wajar sebagaimana dijelaskan dalam Kebijakan Privasi.</p></section>
        <section id="jaminan"><h2>12. Ketersediaan dan penyangkalan</h2><p>Layanan diberikan “sebagaimana adanya” dan “sebagaimana tersedia” sejauh diizinkan hukum. Kami berusaha menjaga kualitas, keamanan, serta kesinambungan, namun tidak menjamin setiap konten selalu tersedia, bebas gangguan, atau cocok untuk semua kebutuhan. Hak konsumen wajib yang tidak dapat dikesampingkan tetap berlaku.</p></section>
        <section id="tanggung-jawab"><h2>13. Batas tanggung jawab</h2><p>Sejauh diizinkan hukum, Rakyzu tidak bertanggung jawab atas kerugian tidak langsung, insidental, khusus, atau konsekuensial yang tidak dapat diperkirakan secara wajar. Tidak ada ketentuan yang membatasi tanggung jawab yang menurut hukum tidak boleh dibatasi, termasuk penipuan, kesengajaan, atau hak konsumen wajib.</p></section>
        <section id="hukum"><h2>14. Hukum dan penyelesaian sengketa</h2><p>Ketentuan ini ditafsirkan berdasarkan hukum Republik Indonesia tanpa mengurangi perlindungan konsumen wajib di tempat tinggal Anda. Sebelum proses formal, para pihak dianjurkan menyelesaikan sengketa dengan itikad baik melalui kontak tertulis.</p></section>
        <section id="perubahan"><h2>15. Perubahan ketentuan</h2><p>Kami dapat memperbarui ketentuan untuk perubahan layanan atau hukum. Perubahan material akan diberitahukan secara wajar jika diwajibkan. Penggunaan setelah tanggal berlaku berarti penerimaan sejauh diizinkan hukum.</p></section>
        <section id="kontak"><h2>16. Kontak</h2><p>Hubungi Rakyzu Development melalui <a href="mailto:${CONTACT_EMAIL}">${CONTACT_EMAIL}</a>. Jangan kirim kata sandi, token, atau informasi pembayaran lengkap.</p></section>
      </article>
      <aside class="toc" aria-label="Daftar isi"><strong>Daftar isi</strong><ol>
        <li><a href="#penerimaan">Penerimaan</a></li><li><a href="#kelayakan">Akun</a></li><li><a href="#layanan">Layanan</a></li><li><a href="#lisensi">Lisensi</a></li><li><a href="#aturan">Aturan</a></li><li><a href="#konten">Konten Anda</a></li><li><a href="#artis">Artis & staf</a></li><li><a href="#hak">Hak cipta</a></li><li><a href="#pembayaran">Pembayaran</a></li><li><a href="#penegakan">Penegakan</a></li><li><a href="#pihak-ketiga">Pihak ketiga</a></li><li><a href="#jaminan">Ketersediaan</a></li><li><a href="#tanggung-jawab">Tanggung jawab</a></li><li><a href="#hukum">Hukum</a></li><li><a href="#perubahan">Perubahan</a></li><li><a href="#kontak">Kontak</a></li>
      </ol></aside>
    </div>
  </main>`);
}

function dataDeletionPage(): string {
  return layout("data-deletion", `<main id="main-content">
    <header class="legal-header">
      <p class="eyebrow">Kontrol privasi</p>
      <h1>Penghapusan akun dan data</h1>
      <p class="lead">Anda dapat meminta penghapusan data Rakyzu Music kapan saja, termasuk jika akun dibuat melalui Google, Facebook, atau Apple.</p>
      <p class="meta"><strong>Terakhir diperbarui:</strong> ${EFFECTIVE_DATE}</p>
    </header>
    <div class="legal-layout">
      <article class="legal-content">
        <section id="cara"><h2>1. Cara mengajukan permintaan</h2>
          <ol>
            <li>Jika opsi penghapusan tersedia pada versi aplikasi Anda, buka kontrol akun dan ikuti konfirmasi yang ditampilkan.</li>
            <li>Atau kirim email dari alamat yang terhubung ke akun menuju <a href="mailto:${CONTACT_EMAIL}?subject=Permintaan%20Penghapusan%20Data%20Rakyzu%20Music">${CONTACT_EMAIL}</a> dengan subjek <code>Permintaan Penghapusan Data Rakyzu Music</code>.</li>
            <li>Sebutkan alamat email akun, metode login (email/Google/Facebook/Apple), dan apakah Anda meminta penghapusan penuh atau data tertentu. <strong>Jangan pernah kirim kata sandi, kode OTP, token, atau kunci pemulihan.</strong></li>
            <li>Kami akan melakukan verifikasi yang wajar—biasanya melalui email akun—untuk mencegah penghapusan tanpa izin, lalu memberikan konfirmasi atau nomor referensi.</li>
          </ol>
        </section>
        <section id="facebook"><h2>2. Instruksi khusus pengguna Facebook</h2>
          <p>Jika Anda memakai Facebook Login, langkah di atas merupakan instruksi resmi penghapusan data aplikasi Rakyzu Music. Anda juga dapat menghapus koneksi Rakyzu Music dari pengaturan <em>Apps and Websites</em> pada Facebook. Pencabutan koneksi Facebook tidak selalu menghapus data yang tersimpan di Rakyzu Music; ajukan permintaan melalui aplikasi atau email agar akun dan data Rakyzu diproses untuk penghapusan.</p>
        </section>
        <section id="google-apple"><h2>3. Pengguna Google dan Apple</h2>
          <p>Mencabut akses Rakyzu Music dari halaman keamanan Google atau pengaturan Sign in with Apple menghentikan akses login berikutnya, tetapi tidak otomatis menghapus akun Rakyzu. Gunakan proses pada bagian 1. Menghapus akun Rakyzu tidak menghapus akun Google, Facebook, atau Apple Anda.</p>
        </section>
        <section id="dihapus"><h2>4. Data yang dihapus atau dianonimkan</h2>
          <p>Setelah permintaan sah diproses, kami menghapus atau menganonimkan data yang terhubung ke akun, termasuk sejauh berlaku:</p>
          <ul><li>profil, avatar, pengenal login, dan preferensi;</li><li>pustaka, suka, ikuti, riwayat aktivitas yang terhubung, playlist pribadi, dan rekomendasi personal;</li><li>token/koneksi penyedia identitas dan sesi aktif;</li><li>permintaan notifikasi serta data perangkat yang dikaitkan;</li><li>konten pengguna atau profil artis, dengan mempertimbangkan hak pemegang karya, kontrak, dan kepentingan pengguna lain pada konten kolaboratif.</li></ul>
        </section>
        <section id="dipertahankan"><h2>5. Data yang mungkin dipertahankan</h2>
          <p>Kami dapat menyimpan data minimum yang diwajibkan atau diizinkan hukum, misalnya catatan transaksi untuk pajak/akuntansi, bukti persetujuan, pencegahan penipuan dan penyalahgunaan, penyelesaian sengketa, pelaksanaan hak hukum, serta log keamanan terbatas. Data tersebut dibatasi penggunaannya dan dihapus setelah tujuan retensinya berakhir. Data agregat yang tidak lagi mengidentifikasi Anda dapat dipertahankan.</p>
        </section>
        <section id="waktu"><h2>6. Waktu dan dampak</h2>
          <p>Kami menanggapi dalam tenggat yang diwajibkan hukum yang berlaku. Permintaan yang kompleks atau perlu verifikasi dapat membutuhkan waktu tambahan yang sah; kami akan memberi tahu bila demikian. Salinan pada cadangan terenkripsi dapat bertahan sementara sampai siklus rotasinya selesai dan tidak digunakan kembali untuk operasional normal.</p>
          <p>Penghapusan akun bersifat permanen setelah selesai. Anda dapat kehilangan playlist, status peran/artis, akses katalog, dan hak lain yang terikat akun. Pembatalan atau pengembalian dana langganan harus dilakukan melalui penyedia pembayaran terkait dan tidak otomatis terjadi karena penghapusan akun.</p>
        </section>
        <section id="perangkat"><h2>7. Data di perangkat</h2><p>Setelah proses akun selesai, keluar dari aplikasi lalu hapus data aplikasi atau copot Rakyzu Music untuk menghapus cache, antrean, preferensi, dan unduhan lokal. Hanya mencopot aplikasi tidak menghapus data akun di server.</p></section>
        <section id="kontak"><h2>8. Bantuan</h2><div class="notice"><p><strong>Rakyzu Development</strong><br>Email: <a href="mailto:${CONTACT_EMAIL}">${CONTACT_EMAIL}</a><br>Subjek: <code>Permintaan Penghapusan Data Rakyzu Music</code></p></div><p>Jika Anda tidak lagi memiliki akses ke email akun, jelaskan situasinya. Kami dapat meminta bukti alternatif yang proporsional dan tidak akan meminta kata sandi.</p></section>
      </article>
      <aside class="toc" aria-label="Daftar isi"><strong>Daftar isi</strong><ol><li><a href="#cara">Cara meminta</a></li><li><a href="#facebook">Facebook</a></li><li><a href="#google-apple">Google & Apple</a></li><li><a href="#dihapus">Yang dihapus</a></li><li><a href="#dipertahankan">Yang dipertahankan</a></li><li><a href="#waktu">Waktu & dampak</a></li><li><a href="#perangkat">Data perangkat</a></li><li><a href="#kontak">Bantuan</a></li></ol></aside>
    </div>
  </main>`);
}

const PAGE_RENDERERS: Record<LegalPage, () => string> = {
  home: homePage,
  privacy: privacyPage,
  terms: termsPage,
  "data-deletion": dataDeletionPage,
};

function legalHeaders(requestId: string): Headers {
  return new Headers({
    "cache-control": "public, max-age=300, stale-while-revalidate=3600",
    "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
    "content-type": "text/html; charset=utf-8",
    "cross-origin-opener-policy": "same-origin",
    "permissions-policy": "camera=(), microphone=(), geolocation=(), browsing-topics=()",
    "referrer-policy": "no-referrer",
    "x-content-type-options": "nosniff",
    "x-frame-options": "DENY",
    "x-request-id": requestId,
  });
}

export function legalPageResponse(
  page: LegalPage,
  method: string,
  requestId: string,
): Response {
  const headers = legalHeaders(requestId);
  headers.set("allow", "GET, HEAD");
  if (method !== "GET" && method !== "HEAD") {
    return new Response("Method not allowed.", { status: 405, headers });
  }
  const html = PAGE_RENDERERS[page]();
  return new Response(method === "HEAD" ? null : html, { status: 200, headers });
}

export function resolveLegalPage(pathname: string): LegalPage | null {
  const normalized = pathname.length > 1 ? pathname.replace(/\/+$/, "") : pathname;
  switch (normalized) {
    case "/": return "home";
    case "/privacy": return "privacy";
    case "/terms": return "terms";
    case "/data-deletion": return "data-deletion";
    default: return null;
  }
}
