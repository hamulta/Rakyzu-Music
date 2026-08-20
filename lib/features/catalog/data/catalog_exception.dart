import 'package:supabase_flutter/supabase_flutter.dart';

/// Exception domain katalog dengan pesan siap-tampil (Bahasa Indonesia).
class CatalogException implements Exception {
  const CatalogException(this.message, {this.code});

  factory CatalogException.from(Object error) {
    if (error is CatalogException) {
      return error;
    }
    if (error is PostgrestException) {
      return CatalogException(
        _readableMessage(error.message),
        code: error.code,
      );
    }
    if (error is AuthException) {
      return CatalogException(
        error.message,
        code: error.statusCode?.toString(),
      );
    }
    return CatalogException(_readableMessage('$error'));
  }

  final String message;
  final String? code;

  static String _readableMessage(String raw) {
    final lower = raw.toLowerCase();
    if (lower.contains('row-level security') ||
        lower.contains('permission denied') ||
        lower.contains('new row violates')) {
      return 'Kamu tidak memiliki izin untuk melakukan operasi ini.';
    }
    if (lower.contains('unique') || lower.contains('duplicate')) {
      return 'Data sudah ada. Gunakan nama/judul yang berbeda.';
    }
    if (lower.contains('not found')) {
      return 'Data tidak ditemukan.';
    }
    if (lower.contains('connection') ||
        lower.contains('network') ||
        lower.contains('timeout')) {
      return 'Koneksi bermasalah. Silakan coba lagi.';
    }
    return raw;
  }

  @override
  String toString() => message;
}
