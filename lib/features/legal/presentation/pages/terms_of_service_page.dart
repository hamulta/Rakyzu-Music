import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class TermsOfServicePage extends StatelessWidget {
  const TermsOfServicePage({super.key});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Terms of Service')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Rakyzu Music — Terms of Service',
                style: Theme.of(context)
                    .textTheme
                    .titleLarge
                    ?.copyWith(fontWeight: FontWeight.w800)),
            const SizedBox(height: 12),
            const Text(
                '1. Langganan & Auto-Renewal\nPremium Monthly (Rp 49.000) dan Yearly (Rp 449.000) bersifat auto-renewal via Midtrans. Perpanjangan otomatis tiap periode kecuali dibatalkan sebelum end_date. Batal via /premium/upgrade → Cancel — tetap Premium sampai expiry.'),
            const SizedBox(height: 12),
            const Text(
                '2. Kebijakan Refund (Pasar Indonesia)\nNo-refund setelah periode aktif dimulai. Grace period 3 hari setelah pembayaran pertama: refund penuh jika belum ada streaming >30 menit. Setelah itu, refund pro-rata tidak tersedia — ajukan via support@rakyzu.com dalam 3 hari. Asumsi ini wajar untuk IDR & akan di-review PM.'),
            const SizedBox(height: 12),
            const Text(
                '3. Konten\nKatalog dikurasi Staff/Admin/Owner, bukan UGC. Platform tidak bertanggung jawab atas klaim lisensi pihak ketiga di luar kurasi, namun akan takedown dalam 48 jam setelah laporan.'),
            const SizedBox(height: 12),
            const Text(
                '4. Batasan Tanggung Jawab\nLayanan disediakan apa adanya, tidak ada jaminan uptime 100%. Maksimum tanggung jawab terbatas pada biaya langganan bulan terakhir.'),
            const SizedBox(height: 12),
            const Text(
                '5. Perubahan Harga\nOwner dapat mengubah harga via /admin/pricing; perubahan tidak retroaktif, berlaku periode berikutnya.'),
            const SizedBox(height: 20),
            TextButton(
                onPressed: () => context.go('/privacy'),
                child: const Text('Lihat Privacy Policy')),
          ],
        ),
      ),
    );
  }
}
