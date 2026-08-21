import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Offline download MVP — enkripsi XOR dasar agar tidak bisa langsung dibuka sebagai mp3.
/// Level proteksi: obfuscation, bukan DRM kuat. File di sandbox app (getApplicationDocumentsDirectory).
/// Staff/Premium/Admin/Owner only (gate via AdsGate/role).
class OfflineDownloadService {
  OfflineDownloadService._();
  static final OfflineDownloadService instance = OfflineDownloadService._();

  static const _xorKey = 0x5A;
  static const _prefsKey = 'offline_downloads';

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

  Future<bool> isDownloaded(String songId) async => (await _fileFor(songId)).exists();

  Future<List<String>> getDownloadedIds() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final list = prefs.getStringList(_prefsKey) ?? [];
      return list;
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
      final enc = _xor(bytes);
      final file = await _fileFor(songId);
      await file.writeAsBytes(enc);
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
    final enc = await f.readAsBytes();
    return _xor(enc);
  }

  Uint8List _xor(Uint8List data) {
    final out = Uint8List(data.length);
    for (var i = 0; i < data.length; i++) {
      out[i] = data[i] ^ _xorKey;
    }
    return out;
  }
}
