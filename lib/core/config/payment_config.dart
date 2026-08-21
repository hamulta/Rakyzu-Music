/// Midtrans Production config — bertahap.
/// Merchant ID: M046699444, Client Key: Mid-client-6CGNGblh8i2GfdRB (publik, aman di client).
/// Server Key TIDAK ada di sini — di-set PM sebagai Supabase secret MIDTRANS_SERVER_KEY via channel aman.
/// PAYMENT_ENV default sandbox; ganti ke production hanya setelah PM konfirmasi go-live.
class PaymentConfig {
  PaymentConfig._();
  static const String merchantId = 'M046699444';
  static const String clientKey = 'Mid-client-6CGNGblh8i2GfdRB';
  // Server key TIDAK disimpan di client — lihat supabase/functions secrets
  static const String env = String.fromEnvironment('PAYMENT_ENV',
      defaultValue: 'sandbox'); // sandbox | production
  static bool get isProduction => env == 'production';
  static bool get isSandbox => !isProduction;
  static String get snapJsUrl => isProduction
      ? 'https://app.midtrans.com/snap/snap.js'
      : 'https://app.sandbox.midtrans.com/snap/snap.js';
}
