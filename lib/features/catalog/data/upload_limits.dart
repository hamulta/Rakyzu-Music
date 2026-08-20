/// Batas & ekstensi file upload — HARUS sinkron dengan Cloudflare Worker
/// (audio: mp3/m4a/aac/flac/wav max 200MB; images: jpg/jpeg/png/webp/svg max 120MB).
class UploadLimits {
  UploadLimits._();

  static const Set<String> audioExtensions = {
    'mp3',
    'm4a',
    'aac',
    'flac',
    'wav',
  };
  static const Set<String> imageExtensions = {
    'jpg',
    'jpeg',
    'png',
    'webp',
    'svg',
  };
  static const int maxAudioBytes = 200 * 1024 * 1024;
  static const int maxImageBytes = 120 * 1024 * 1024;

  /// Folder object key di R2 (prefix `audio/` dan `images/`).
  static const String audioFolder = 'audio';
  static const String imageFolder = 'images';
}

/// Validasi client-side (UX) — otoritatif tetap di Worker & RLS.
class UploadValidator {
  UploadValidator._();

  static String? validateAudio(String extension, int size) {
    if (!UploadLimits.audioExtensions.contains(extension.toLowerCase())) {
      return 'Format audio tidak didukung. Gunakan mp3/m4a/aac/flac/wav.';
    }
    if (size > UploadLimits.maxAudioBytes) {
      return 'Ukuran file melebihi batas 200MB.';
    }
    return null;
  }

  static String? validateImage(String extension, int size) {
    if (!UploadLimits.imageExtensions.contains(extension.toLowerCase())) {
      return 'Format gambar tidak didukung. Gunakan jpg/png/webp/svg.';
    }
    if (size > UploadLimits.maxImageBytes) {
      return 'Ukuran gambar melebihi batas 120MB.';
    }
    return null;
  }
}

/// Exception domain storage (R2).
class StorageException implements Exception {
  const StorageException(this.message);

  final String message;

  @override
  String toString() => message;
}
