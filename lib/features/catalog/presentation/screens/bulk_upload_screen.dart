import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_app_bar.dart';
import '../../../../core/widgets/glass_button.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../data/catalog_exception.dart';
import '../../data/r2_storage_service.dart';
import '../../data/upload_limits.dart';
import '../../providers/catalog_providers.dart';

/// Upload beberapa file audio sekaligus untuk 1 album.
/// Tiap file di-upload sekuensial lewat signed URL + validasi yang sudah ada.
class BulkUploadScreen extends ConsumerStatefulWidget {
  const BulkUploadScreen({super.key});

  @override
  ConsumerState<BulkUploadScreen> createState() => _BulkUploadScreenState();
}

class _BulkUploadScreenState extends ConsumerState<BulkUploadScreen> {
  String? _selectedAlbumId;
  String? _selectedArtistId;
  final List<_UploadItem> _queue = [];
  bool _uploading = false;
  int _currentIdx = 0;

  Future<void> _pickFiles() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: UploadLimits.audioExtensions.toList(),
      allowMultiple: true,
      withData: true,
    );
    if (result == null || !mounted) return;

    final items = <_UploadItem>[];
    for (final file in result.files) {
      final ext = file.extension?.toLowerCase() ?? '';
      final bytes = file.bytes;
      if (bytes == null) continue;
      final validation = UploadValidator.validateAudio(ext, bytes.length);
      items.add(
        _UploadItem(
          filename: file.name,
          bytes: bytes,
          extension: ext,
          error: validation,
        ),
      );
    }
    setState(() => _queue.addAll(items));
  }

  Future<void> _startUpload() async {
    if (_selectedAlbumId == null || _selectedArtistId == null) {
      _showError('Pilih album dan artis terlebih dahulu.');
      return;
    }
    final pending = _queue.where((i) => i.error == null && !i.done).toList();
    if (pending.isEmpty) {
      _showError('Tidak ada file valid untuk diupload.');
      return;
    }

    setState(() {
      _uploading = true;
      _currentIdx = 0;
    });

    final r2 = ref.read(r2StorageServiceProvider);
    final songsCtrl = ref.read(songsControllerProvider.notifier);

    for (var idx = 0; idx < _queue.length; idx++) {
      final item = _queue[idx];
      if (item.error != null || item.done) continue;

      setState(() {
        _currentIdx = idx;
        item.status = _UploadStatus.uploading;
      });

      try {
        final key = await r2.uploadBytes(
          folder: UploadLimits.audioFolder,
          bytes: item.bytes,
          extension: item.extension,
          onProgress: (p) => setState(() => item.progress = p),
        );
        item.audioKey = key;

        // deteksi durasi via player
        int? duration;
        try {
          final url = await r2.getReadUrl(key);
          final player = await _detectDuration(url);
          duration = player;
        } on Object {
          // durasi optional
        }

        await songsCtrl.create(
          title: item.filename.replaceAll(RegExp(r'\.\w+$'), ''),
          albumId: _selectedAlbumId!,
          artistId: _selectedArtistId!,
          audioUrl: key,
          durationSeconds: duration,
          trackNumber: idx + 1,
        );

        setState(() {
          item.status = _UploadStatus.done;
          item.progress = 1.0;
        });
      } on CatalogException catch (e) {
        setState(() {
          item.status = _UploadStatus.error;
          item.errorMessage = e.message;
        });
      } on Exception catch (e) {
        setState(() {
          item.status = _UploadStatus.error;
          item.errorMessage = storageErrorMessage(e);
        });
      }
    }

    setState(() => _uploading = false);
  }

  Future<int?> _detectDuration(String url) async {
    try {
      final player = AudioPlayer();
      try {
        await player.setUrl(url);
        final dur = await player.durationStream.first;
        return dur?.inSeconds;
      } finally {
        await player.dispose();
      }
    } on Object {
      return null;
    }
  }

  void _showError(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final albums = ref.watch(albumsControllerProvider).valueOrNull ?? const [];
    final artists =
        ref.watch(artistsControllerProvider).valueOrNull ?? const [];

    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: isDark
              ? AppColors.darkBackgroundGradient
              : AppColors.lightBackgroundGradient,
        ),
        child: SafeArea(
          child: Column(
            children: [
              GlassAppBar(
                title: const Text('Bulk Upload Audio'),
                leading: IconButton(
                  icon: const Icon(CupertinoIcons.chevron_left),
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ),
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.all(20),
                  children: [
                    GlassCard(
                      borderRadius: 16,
                      child: Column(
                        children: [
                          _buildDropdown<String>(
                            value: _selectedAlbumId,
                            label: 'Album *',
                            icon: CupertinoIcons.music_albums,
                            items: albums
                                .map(
                                  (a) => DropdownMenuItem(
                                    value: a.id,
                                    child: Text(
                                      a.title,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                )
                                .toList(),
                            onChanged: (v) =>
                                setState(() => _selectedAlbumId = v),
                          ),
                          const SizedBox(height: 14),
                          _buildDropdown<String>(
                            value: _selectedArtistId,
                            label: 'Artis *',
                            icon: CupertinoIcons.person_2,
                            items: artists
                                .map(
                                  (a) => DropdownMenuItem(
                                    value: a.id,
                                    child: Text(
                                      a.name,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                )
                                .toList(),
                            onChanged: (v) =>
                                setState(() => _selectedArtistId = v),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    GlassButton(
                      label: 'Pilih File Audio',
                      icon: CupertinoIcons.doc_text,
                      isPrimary: false,
                      onPressed: _uploading ? null : _pickFiles,
                    ),
                    if (_queue.isNotEmpty) ...[
                      const SizedBox(height: 16),
                      GlassCard(
                        borderRadius: 16,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    '${_queue.length} file dipilih',
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ),
                                if (_uploading)
                                  SizedBox(
                                    width: 18,
                                    height: 18,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                      value:
                                          _queue.where((i) => i.done).length /
                                              _queue.length,
                                    ),
                                  ),
                              ],
                            ),
                            const SizedBox(height: 12),
                            for (var i = 0; i < _queue.length; i++)
                              _UploadTile(item: _queue[i], index: i),
                          ],
                        ),
                      ),
                      const SizedBox(height: 16),
                      GlassButton(
                        label: _uploading
                            ? 'Mengupload... ($_currentIdx/${_queue.length})'
                            : 'Mulai Upload',
                        icon: _uploading
                            ? CupertinoIcons.stop_fill
                            : CupertinoIcons.cloud_upload,
                        isPrimary: true,
                        loading: _uploading,
                        onPressed: _uploading ? null : _startUpload,
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildDropdown<T>({
    required T? value,
    required String label,
    required IconData icon,
    required List<DropdownMenuItem<T>> items,
    required ValueChanged<T?> onChanged,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return DropdownButtonFormField<T>(
      value: value,
      isExpanded: true,
      items: items.isEmpty
          ? [
              DropdownMenuItem<T>(
                value: null,
                child: const Text('Belum ada data'),
              ),
            ]
          : items,
      onChanged: onChanged,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon, size: 20),
        labelStyle: const TextStyle(color: AppColors.textSecondary),
        filled: true,
        fillColor: isDark
            ? Colors.white.withOpacity(0.04)
            : Colors.black.withOpacity(0.04),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide(color: AppColors.getBorderGlass(isDark)),
        ),
      ),
    );
  }
}

enum _UploadStatus { pending, uploading, done, error }

class _UploadItem {
  _UploadItem({
    required this.filename,
    required this.bytes,
    required this.extension,
    this.error,
  });

  final String filename;
  final Uint8List bytes;
  final String extension;
  final String? error;
  _UploadStatus status = _UploadStatus.pending;
  double progress = 0;
  String? audioKey;
  String? errorMessage;

  bool get done => status == _UploadStatus.done;
}

class _UploadTile extends StatelessWidget {
  const _UploadTile({required this.item, required this.index});

  final _UploadItem item;
  final int index;

  @override
  Widget build(BuildContext context) {
    final sizeMB = (item.bytes.length / (1024 * 1024)).toStringAsFixed(1);

    IconData icon;
    Color iconColor;
    Widget? trailing;

    if (item.error != null) {
      icon = CupertinoIcons.exclamationmark_circle_fill;
      iconColor = AppColors.accentError;
      trailing = Text(
        item.error!,
        style: const TextStyle(fontSize: 11, color: AppColors.accentError),
      );
    } else {
      switch (item.status) {
        case _UploadStatus.done:
          icon = CupertinoIcons.checkmark_circle_fill;
          iconColor = AppColors.azureMistDeep;
          trailing = const Icon(
            CupertinoIcons.check_mark,
            size: 16,
            color: AppColors.azureMistDeep,
          );
        case _UploadStatus.uploading:
          icon = CupertinoIcons.arrow_down_circle_fill;
          iconColor = AppColors.azureMistDeep;
          trailing = SizedBox(
            width: 40,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                LinearProgressIndicator(
                  value: item.progress,
                  minHeight: 4,
                  backgroundColor: AppColors.textSecondary.withOpacity(0.2),
                  color: AppColors.azureMistDeep,
                ),
                const SizedBox(height: 2),
                Text(
                  '${(item.progress * 100).toInt()}%',
                  style: const TextStyle(
                    fontSize: 10,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          );
        case _UploadStatus.error:
          icon = CupertinoIcons.xmark_circle_fill;
          iconColor = AppColors.accentError;
          trailing = Text(
            item.errorMessage ?? 'Gagal',
            style: const TextStyle(
              fontSize: 11,
              color: AppColors.accentError,
            ),
          );
        default:
          icon = CupertinoIcons.doc;
          iconColor = AppColors.textSecondary;
      }
    }

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        children: [
          Icon(icon, size: 20, color: iconColor),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.filename,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 13),
                ),
                Text(
                  '$sizeMB MB · .${item.extension}',
                  style: const TextStyle(
                    fontSize: 11,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),
          if (trailing != null) trailing,
        ],
      ),
    );
  }
}
