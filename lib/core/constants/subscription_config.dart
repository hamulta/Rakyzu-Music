/// Harga paket Premium — pasar Indonesia.
/// Asumsi: Rp 49.000/bulan (kompetitif vs Spotify Rp 54.900), Yearly Rp 449.000 (hemat ~24%).
/// Owner dapat ubah via Admin Dashboard di 0.8.x, jadi ini default awal saja.
class SubscriptionConfig {
  SubscriptionConfig._();
  static const int monthlyPriceIdr = 49000;
  static const int yearlyPriceIdr = 449000;
  static const String monthlyLabel = 'Monthly';
  static const String yearlyLabel = 'Yearly';
  static const String currency = 'IDR';
  static String formatIdr(int v) {
    // Simple IDR formatter without intl dependency in config.
    final s = v.toString().replaceAllMapped(RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.');
    return 'Rp $s';
  }
}
