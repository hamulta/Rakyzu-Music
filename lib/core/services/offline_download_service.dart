import 'dart:io';
import 'dart:typed_data';
import 'package:crypto/crypto.dart';
import 'package:encrypt/encrypt.dart' as enc;
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';

/// Offline download — AES-256 CBC per-device/per-user.
/// Skema: key = SHA256(userId + deviceKey + "RakyzuOffline2025"), IV random 16 bytes prepended.
/// File di sandbox (getApplicationDocumentsDirectory/offline_tracks/*.bin).
/// Lebih kuat dari XOR 0x5A (v0.7.8) — tetap MVP obfuscation, bukan DRM hardware.
class OfflineDownloadService {
  OfflineDownloadService._();
  static final OfflineDownloadService instance = OfflineDownloadService._();

  static const _prefsKey = 'offline_downloads';
  static const _deviceKeyPref = 'offline_device_key';

  Future<Directory> _dir() async {
    final base = await getApplicationDocumentsDirectory();
    final d = Directory('${base.path}/offline_tracks');
    if (!await d.exists()) await d.create(recursive: true);
    return d;
  }

  Future<File> _fileFor(String songId) async {
    final dir = await _dir();
    return File('${dir.path}/$songId.bin');
  }

  Future<String> _deviceKey() async {
    final prefs = await SharedPreferences.getInstance();
    var key = prefs.getString(_deviceKeyPref);
    if (key == null) {
      key = const Uuid().v4();
      await prefs.setString(_deviceKeyPref, key);
    }
    return key;
  }

  Future<enc.Key> _deriveKey() async {
    final userId = Supabase.instance.client.auth.currentUser?.id ?? 'anon';
    final deviceKey = await _deviceKey();
    final raw = '$userId:$deviceKey:RakyzuOffline2025';
    final digest = sha256.convert(raw.codeUnits);
    // digest 32 bytes = 256-bit
    return enc.Key(Uint8List.fromList(digest.bytes));
  }

  Future<bool> isDownloaded(String songId) async => (await _fileFor(songId)).exists();

  Future<List<String>> getDownloadedIds() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getStringList(_prefsKey) ?? [];
    } catch (_) {
      return [];
    }
  }

  Future<int> getStorageUsageBytes() async {
    final dir = await _dir();
    if (!await dir.exists()) return 0;
    var total = 0;
    await for (final f in dir.list()) {
      if (f is File) total += await f.length();
    }
    return total;
  }

  Future<void> downloadFromUrl(String songId, String signedUrl) async {
    try {
      final client = HttpClient();
      final req = await client.getUrl(Uri.parse(signedUrl));
      final resp = await req.close();
      if (resp.statusCode != 200) throw Exception('HTTP ${resp.statusCode}');
      final bytes = await consolidateHttpClientResponseBytes(resp);
      final encrypted = await _encrypt(bytes);
      final file = await _fileFor(songId);
      await file.writeAsBytes(encrypted);
      final prefs = await SharedPreferences.getInstance();
      final list = prefs.getStringList(_prefsKey) ?? [];
      if (!list.contains(songId)) {
        list.add(songId);
        await prefs.setStringList(_prefsKey, list);
      }
    } catch (e) {
      debugPrint('[Offline] download failed $songId: $e');
      rethrow;
    }
  }

  Future<void> delete(String songId) async {
    final f = await _fileFor(songId);
    if (await f.exists()) await f.delete();
    final prefs = await SharedPreferences.getInstance();
    final list = prefs.getStringList(_prefsKey) ?? [];
    list.remove(songId);
    await prefs.setStringList(_prefsKey, list);
  }

  Future<Uint8List?> readDecrypted(String songId) async {
    final f = await _fileFor(songId);
    if (!await f.exists()) return null;
    final data = await f.readAsBytes();
    // Try AES first, fallback to XOR for legacy files (migration)
    try {
      return await _decrypt(data);
    } on Object {
      // Legacy XOR fallback
      return _xorLegacy(data);
    }
  }

  // AES-CBC with IV prepended (16 bytes)
  Future<Uint8List> _encrypt(Uint8List plain) async {
    final key = await _deriveKey();
    final iv = enc.IV.fromSecureRandom(16);
    final encrypter = enc.Encrypter(enc.AES(key, mode: enc.AESMode.cbc));
    final encrypted = encrypter.encryptBytes(plain, iv: iv);
    // Store iv + ciphertext
    final out = Uint8List(16 + encrypted.bytes.length);
    out.setRange(0, 16, iv.bytes);
    out.setRange(16, out.length, encrypted.bytes);
    return out;
  }

  Future<Uint8List> _decrypt(Uint8List data) async {
    if (data.length < 17) throw Exception('Too short');
    final iv = enc.IV(data.sublist(0, 16));
    final cipher = data.sublist(16);
    final key = await _deriveKey();
    final encrypter = enc.Encrypter(enc.AES(key, mode: enc.AESMode.cbc));
    final decrypted = encrypter.decryptBytes(enc.Encrypted(cipher), iv: iv);
    return Uint8List.fromList(decrypted);
  }

  // Legacy XOR for migration
  Uint8List _xorLegacy(Uint8List data) {
    const xorKey = 0x5A;
    final out = Uint8List(data.length);
    for (var i = 0; i < data.length; i++) out[i] = data[i] ^ xorKey;
    return out;
  }
}
