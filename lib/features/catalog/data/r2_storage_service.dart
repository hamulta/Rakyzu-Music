import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart' hide StorageException;
import 'package:uuid/uuid.dart';

import '../../../config/env_config.dart';
import '../../../shared/providers/supabase_providers.dart';
import 'upload_limits.dart';

class SignedUrlResponse {
  const SignedUrlResponse({
    required this.signedUrl,
    required this.expiresIn,
    required this.key,
  });

  factory SignedUrlResponse.fromJson(Map<String, dynamic> json) {
    return SignedUrlResponse(
      signedUrl: json['signedUrl'] as String,
      expiresIn: json['expiresIn'] as int? ?? 0,
      key: json['key'] as String? ?? '',
    );
  }

  final String signedUrl;
  final int expiresIn;
  final String key;
}

/// Klien upload/download objek Cloudflare R2 via Worker signed URL.
///
/// Alur: minta signed URL dari Worker (butuh user JWT), lalu upload/download
/// langsung ke endpoint R2. Object key (`audio/..`, `images/..`) disimpan di
/// DB; URL signed (short-lived) dibuat on-demand saat ditampilkan.
class R2StorageService {
  R2StorageService(this._supabase);

  final SupabaseClient _supabase;
  final Dio _dio = Dio();
  final Uuid _uuid = const Uuid();

  /// Upload bytes ke R2, mengembalikan object key (mis. `images/abc.jpg`).
  Future<String> uploadBytes({
    required String folder,
    required Uint8List bytes,
    required String extension,
    void Function(double progress)? onProgress,
  }) async {
    final key = '$folder/${_uuid.v4()}.$extension.toLowerCase()';
    final signed = await _requestSignedUrl(
      key: key,
      action: 'put',
      size: bytes.length,
    );

    await _putFile(
      signed.signedUrl,
      bytes,
      _contentTypeFor(folder, extension),
      onProgress,
    );
    return key;
  }

  /// Dapatkan signed URL (short-lived) untuk object key.
  Future<String> getReadUrl(String key, {int expires = 3600}) async {
    final signed = await _requestSignedUrl(
      key: key,
      action: 'get',
      expires: expires,
    );
    return signed.signedUrl;
  }

  Future<SignedUrlResponse> _requestSignedUrl({
    required String key,
    required String action,
    int expires = 900,
    int? size,
  }) async {
    final base = EnvConfig.cloudflareWorkerUrl;
    if (base.isEmpty) {
      throw const StorageException(
        'CLOUDFLARE_WORKER_URL belum dikonfigurasi.',
      );
    }
    final token = _supabase.auth.currentSession?.accessToken;
    if (token == null) {
      throw const StorageException('Sesi login tidak ditemukan.');
    }

    final uri = Uri.parse('$base/signed-url').replace(
      queryParameters: {
        'key': key,
        'action': action,
        'expires': '$expires',
        if (size != null) 'size': '$size',
      },
    );

    final response = await _dio.get<dynamic>(
      uri.toString(),
      options: Options(headers: {'Authorization': 'Bearer $token'}),
    );

    if (response.statusCode != 200 || response.data is! Map) {
      throw StorageException(_errorMessage(response.data));
    }
    return SignedUrlResponse.fromJson(response.data as Map<String, dynamic>);
  }

  Future<void> _putFile(
    String signedUrl,
    Uint8List bytes,
    String contentType,
    void Function(double progress)? onProgress,
  ) async {
    await _dio.put<dynamic>(
      signedUrl,
      data: bytes,
      options: Options(
        headers: {'Content-Type': contentType},
      ),
      onSendProgress: (sent, total) {
        if (onProgress != null && total > 0) {
          onProgress(sent / total);
        }
      },
    );
  }

  String _contentTypeFor(String folder, String extension) {
    final ext = extension.toLowerCase();
    if (folder == UploadLimits.audioFolder) {
      return switch (ext) {
        'mp3' => 'audio/mpeg',
        'm4a' => 'audio/mp4',
        'aac' => 'audio/aac',
        'flac' => 'audio/flac',
        'wav' => 'audio/wav',
        _ => 'application/octet-stream',
      };
    }
    return switch (ext) {
      'jpg' || 'jpeg' => 'image/jpeg',
      'png' => 'image/png',
      'webp' => 'image/webp',
      'svg' => 'image/svg+xml',
      _ => 'application/octet-stream',
    };
  }

  String _errorMessage(dynamic data) {
    if (data is Map && data['error'] is String) {
      return data['error'] as String;
    }
    return 'Upload gagal. Silakan coba lagi.';
  }
}

final r2StorageServiceProvider = Provider<R2StorageService>((ref) {
  return R2StorageService(ref.watch(supabaseProvider));
});

/// Signed URL untuk object key (cached sementara oleh FutureProvider).
final signedImageUrlProvider =
    FutureProvider.family<String, String>((ref, key) {
  return ref.watch(r2StorageServiceProvider).getReadUrl(key);
});

/// Signed URL untuk file audio (cached sementara oleh FutureProvider).
final signedAudioUrlProvider =
    FutureProvider.family<String, String>((ref, key) {
  return ref.watch(r2StorageServiceProvider).getReadUrl(key);
});

/// Helper: parse pesan error dari response worker/dio.
String storageErrorMessage(Object error) {
  if (error is StorageException) {
    return error.message;
  }
  if (error is DioException) {
    final data = error.response?.data;
    if (data is Map && data['error'] is String) {
      return data['error'] as String;
    }
    if (data is String && data.isNotEmpty) {
      return jsonDecodeSafeMessage(data);
    }
    return 'Upload gagal. Periksa koneksi lalu coba lagi.';
  }
  return error.toString();
}

String jsonDecodeSafeMessage(String raw) {
  try {
    final decoded = jsonDecode(raw);
    if (decoded is Map && decoded['error'] is String) {
      return decoded['error'] as String;
    }
  } on FormatException {
    // ignore: fallback ke raw
  }
  return raw;
}
