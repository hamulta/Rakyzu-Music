import 'package:flutter_dotenv/flutter_dotenv.dart';

/// Centralized environment configuration.
/// Reads from `.env` via flutter_dotenv, with `--dart-define` overrides.
class EnvConfig {
  EnvConfig._();

  // Supabase
  static String get supabaseUrl =>
      _get('SUPABASE_URL', 'https://your-project-ref.supabase.co');
  static String get supabaseAnonKey => _get('SUPABASE_ANON_KEY', '');

  // Cloudflare R2 & Worker
  static String get cloudflareAccountId => _get('CLOUDFLARE_ACCOUNT_ID', '');
  static String get cloudflareApiToken => _get('CLOUDFLARE_API_TOKEN', '');
  static String get cloudflareR2AccessKeyId =>
      _get('CLOUDFLARE_R2_ACCESS_KEY_ID', '');
  static String get cloudflareR2SecretAccessKey =>
      _get('CLOUDFLARE_R2_SECRET_ACCESS_KEY', '');
  static String get cloudflareR2Endpoint => _get(
        'CLOUDFLARE_R2_ENDPOINT',
        'https://<account-id>.r2.cloudflarestorage.com',
      );
  static String get cloudflareR2Bucket =>
      _get('CLOUDFLARE_R2_BUCKET', 'rakyzu-music');
  static String get cloudflareWorkerUrl => _get('CLOUDFLARE_WORKER_URL', '');

  // Midtrans
  static String get midtransServerKey => _get('MIDTRANS_SERVER_KEY', '');
  static String get midtransClientKey => _get('MIDTRANS_CLIENT_KEY', '');
  static bool get midtransIsProduction =>
      _get('MIDTRANS_IS_PRODUCTION', 'false') == 'true';

  // AdMob
  static String get admobIosAppId => _get('ADMOB_IOS_APP_ID', '');
  static String get admobAndroidAppId => _get('ADMOB_ANDROID_APP_ID', '');
  static String get admobIosBannerId => _get('ADMOB_IOS_BANNER_ID', '');
  static String get admobAndroidBannerId => _get('ADMOB_ANDROID_BANNER_ID', '');
  static String get admobIosInterstitialId =>
      _get('ADMOB_IOS_INTERSTITIAL_ID', '');
  static String get admobAndroidInterstitialId =>
      _get('ADMOB_ANDROID_INTERSTITIAL_ID', '');

  // Google Sign-In
  static String get googleSignInIosClientId =>
      _get('GOOGLE_SIGN_IN_IOS_CLIENT_ID', '');
  static String get googleSignInAndroidClientId =>
      _get('GOOGLE_SIGN_IN_ANDROID_CLIENT_ID', '');
  static String get googleSignInWebClientId =>
      _get('GOOGLE_SIGN_IN_WEB_CLIENT_ID', '');

  // App
  static String get appName => _get('APP_NAME', 'Rakyzu Music');
  static String get appBundleId => _get('APP_BUNDLE_ID', 'com.rakyzu.music');
  static String get appVersion => _get('APP_VERSION', '0.1.0');

  /// Reads from `.env` via flutter_dotenv, falling back to [defaultValue].
  /// Note: `--dart-define` overrides are handled per-field using const
  /// `String.fromEnvironment` keys, e.g. `SUPABASE_URL`.
  static String _get(String key, String defaultValue) {
    final value = dotenv.get(key, fallback: defaultValue);
    return _dartDefine(key) ?? value;
  }

  /// Returns the compile-time value for [key] if it was passed via --dart-define.
  static String? _dartDefine(String key) {
    return switch (key) {
      'SUPABASE_URL' => const String.fromEnvironment('SUPABASE_URL'),
      'SUPABASE_ANON_KEY' => const String.fromEnvironment('SUPABASE_ANON_KEY'),
      'CLOUDFLARE_WORKER_URL' =>
        const String.fromEnvironment('CLOUDFLARE_WORKER_URL'),
      _ => null,
    };
  }
}
