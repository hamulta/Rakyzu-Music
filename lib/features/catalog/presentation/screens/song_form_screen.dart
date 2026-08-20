import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:just_audio/just_audio.dart';

import '../../../../core/constants/app_routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_app_bar.dart';
import '../../../../core/widgets/glass_button.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/signed_audio_player.dart';
import '../../data/catalog_exception.dart';
import '../../data/r2_storage_service.dart';
import '../../data/upload_limits.dart';
import '../../models/album.dart';
import '../../models/artist.dart';
import '../../providers/catalog_providers.dart';
import '../widgets/catalog_access_guard.dart';

/// Form tambah/edit lagu (staff/admin/owner).
/// - Tambah: judul, album & artis wajib; audio wajib (upload ke R2).
/// - Edit: semua field bisa diubah, audio bisa diganti.
class SongFormScreen extends ConsumerStatefulWidget {
  const SongFormScreen({super.key, this.songId});

  final String? songId;

  @override
  ConsumerState<SongFormScreen> createState() => _SongFormScreenState();
}

class _SongFormScreenState extends ConsumerState<SongFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _titleController = TextEditingController();
  final _genreController = TextEditingController();
  final _trackController = TextEditingController();

  bool _isEdit = false;
  bool _loading = false;
  bool _saving = false;

  String? _albumId;
  String? _artistId;

  // Audio
  Uint8List? _audioBytes;
  String _audioExtension = '';
  String _audioFilename = '';
  String? _existingAudioKey;

  // Sampul
  Uint8List? _coverBytes;
  String _coverExtension = '';
  String? _existingCoverKey;

  bool get _hasNewAudio => _audioBytes != null && _audioExtension.isNotEmpty;
  bool get _hasNewCover => _coverBytes != null && _coverExtension.isNotEmpty;

  @override
  void initState() {
    super.initState();
    _isEdit = widget.songId != null;
    if (_isEdit) {
      _loadSong();
    }
  }

  Future<void> _loadSong() async {
    setState(() => _loading = true);
    try {
      final song =
          await ref.read(catalogRepositoryProvider).getSong(widget.songId!);
      if (!mounted || song == null) return;
      _titleController.text = song.title;
      _genreController.text = song.genre ?? '';
      _trackController.text = song.trackNumber > 0 ? '${song.trackNumber}' : '';
      _albumId = song.albumId.isEmpty ? null : song.albumId;
      _artistId = song.artistId.isEmpty ? null : song.artistId;
      _existingAudioKey = song.audioUrl;
      _existingCoverKey = song.coverUrl;
      _loadedDuration = song.durationSeconds;
    } on CatalogException catch (e) {
      if (!mounted) return;
      _showError(e.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _pickAudio() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: UploadLimits.audioExtensions.toList(),
      withData: true,
    );
    final file = result?.files.single;
    if (file == null || !mounted) return;

    final ext = file.extension?.toLowerCase() ?? '';
    final bytes = file.bytes;
    if (bytes == null) {
      _showError('Gagal membaca file audio.');
      return;
    }
    final validation = UploadValidator.validateAudio(ext, bytes.length);
    if (validation != null) {
      _showError(validation);
      return;
    }
    setState(() {
      _audioBytes = bytes;
      _audioExtension = ext;
      _audioFilename = file.name;
      _existingAudioKey = null;
    });
  }

  Future<void> _pickCover() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: UploadLimits.imageExtensions.toList(),
      withData: true,
    );
    final file = result?.files.single;
    if (file == null || !mounted) return;

    final ext = file.extension?.toLowerCase() ?? '';
    final bytes = file.bytes;
    if (bytes == null) {
      _showError('Gagal membaca file gambar.');
      return;
    }
    final validation = UploadValidator.validateImage(ext, bytes.length);
    if (validation != null) {
      _showError(validation);
      return;
    }
    setState(() {
      _coverBytes = bytes;
      _coverExtension = ext;
      _existingCoverKey = null;
    });
  }

  Future<int?> _detectDuration(String audioKey) async {
    try {
      final url = await ref.read(r2StorageServiceProvider).getReadUrl(audioKey);
      final player = AudioPlayer();
      try {
        await player.setUrl(url);
        final duration = await player.durationStream.first;
        return duration?.inSeconds;
      } finally {
        await player.dispose();
      }
    } on Object {
      return null;
    }
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    final albumId = _albumId;
    final artistId = _artistId;
    if (albumId == null) {
      _showError('Pilih album terlebih dahulu.');
      return;
    }
    if (artistId == null) {
      _showError('Pilih artis terlebih dahulu.');
      return;
    }

    var audioKey = _existingAudioKey;
    var coverKey = _existingCoverKey;
    var duration = _loadedDuration;
    if (_hasNewAudio) {
      audioKey = await ref.read(r2StorageServiceProvider).uploadBytes(
            folder: UploadLimits.audioFolder,
            bytes: _audioBytes!,
            extension: _audioExtension,
          );
      duration = await _detectDuration(audioKey);
    }
    if (_hasNewCover) {
      coverKey = await ref.read(r2StorageServiceProvider).uploadBytes(
            folder: UploadLimits.imageFolder,
            bytes: _coverBytes!,
            extension: _coverExtension,
          );
    }

    setState(() => _saving = true);
    try {
      final controller = ref.read(songsControllerProvider.notifier);
      final title = _titleController.text.trim();
      final genre = _genreController.text.trim();
      final track = int.tryParse(_trackController.text.trim()) ?? 0;

      if (_isEdit) {
        await controller.update(
          widget.songId!,
          title: title,
          albumId: albumId,
          artistId: artistId,
          durationSeconds: duration,
          audioUrl: audioKey,
          coverUrl: coverKey,
          genre: genre.isEmpty ? null : genre,
          trackNumber: track,
        );
      } else {
        await controller.create(
          title: title,
          albumId: albumId,
          artistId: artistId,
          durationSeconds: duration,
          audioUrl: audioKey,
          coverUrl: coverKey,
          genre: genre.isEmpty ? null : genre,
          trackNumber: track,
        );
      }

      if (!mounted) return;
      context.go(AppRoutes.catalogSongs);
    } on CatalogException catch (e) {
      if (!mounted) return;
      _showError(e.message);
    } on Exception catch (e) {
      if (!mounted) return;
      _showError(storageErrorMessage(e));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  int? _loadedDuration;

  void _showError(String message) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  void dispose() {
    _titleController.dispose();
    _genreController.dispose();
    _trackController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final artists =
        ref.watch(artistsControllerProvider).valueOrNull ?? const [];
    final albums = ref.watch(albumsControllerProvider).valueOrNull ?? const [];

    return CatalogAccessGuard(
      child: Scaffold(
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
                  title: Text(_isEdit ? 'Edit Lagu' : 'Tambah Lagu'),
                  leading: IconButton(
                    icon: const Icon(CupertinoIcons.chevron_left),
                    onPressed: () => context.go(AppRoutes.catalogSongs),
                  ),
                ),
                Expanded(
                  child: _loading
                      ? const Center(child: CircularProgressIndicator())
                      : SingleChildScrollView(
                          padding: const EdgeInsets.all(20),
                          child: Form(
                            key: _formKey,
                            child: Column(
                              children: [
                                GlassCard(
                                  borderRadius: 16,
                                  child: Column(
                                    children: [
                                      _buildAudioSection(),
                                      const SizedBox(height: 16),
                                      _buildTextField(
                                        controller: _titleController,
                                        label: 'Judul Lagu *',
                                        icon: CupertinoIcons.music_note,
                                        validator: (value) {
                                          if (value == null ||
                                              value.trim().isEmpty) {
                                            return 'Judul lagu wajib diisi';
                                          }
                                          return null;
                                        },
                                      ),
                                      const SizedBox(height: 14),
                                      _buildAlbumDropdown(albums),
                                      const SizedBox(height: 14),
                                      _buildArtistDropdown(artists),
                                      const SizedBox(height: 14),
                                      _buildTextField(
                                        controller: _trackController,
                                        label: 'Nomor Track',
                                        icon: CupertinoIcons.number,
                                        keyboardType: TextInputType.number,
                                      ),
                                      const SizedBox(height: 14),
                                      _buildTextField(
                                        controller: _genreController,
                                        label: 'Genre',
                                        icon: CupertinoIcons.tag,
                                      ),
                                      const SizedBox(height: 14),
                                      _buildCoverPicker(),
                                    ],
                                  ),
                                ),
                                const SizedBox(height: 24),
                                GlassButton(
                                  label: _isEdit
                                      ? 'Simpan Perubahan'
                                      : 'Simpan Lagu',
                                  icon: _isEdit
                                      ? CupertinoIcons.check_mark
                                      : CupertinoIcons.music_note,
                                  isPrimary: true,
                                  loading: _saving,
                                  onPressed: _saving ? null : _save,
                                ),
                              ],
                            ),
                          ),
                        ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildAudioSection() {
    if (_hasNewAudio) {
      return Row(
        children: [
          const Icon(
            CupertinoIcons.doc_checkmark,
            color: AppColors.azureMistDeep,
            size: 28,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _audioFilename,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  '${(_audioBytes!.length / (1024 * 1024)).toStringAsFixed(2)}MB · siap diupload',
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(CupertinoIcons.refresh),
            tooltip: 'Ganti File',
            onPressed: _pickAudio,
          ),
        ],
      );
    }

    if (_existingAudioKey != null) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Preview Audio',
            style: TextStyle(
              fontSize: 12,
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: 8),
          SignedAudioPlayer(audioKey: _existingAudioKey),
          const SizedBox(height: 8),
          TextButton.icon(
            onPressed: _pickAudio,
            icon: const Icon(CupertinoIcons.refresh, size: 16),
            label: const Text('Ganti File Audio'),
          ),
        ],
      );
    }

    return InkWell(
      onTap: _pickAudio,
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.azureMistDeep.withOpacity(0.08),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.azureMistDeep.withOpacity(0.3)),
        ),
        child: Row(
          children: [
            const Icon(
              CupertinoIcons.music_note,
              color: AppColors.azureMistDeep,
              size: 24,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                _isEdit
                    ? 'File audio belum ada — tap untuk memilih (mp3/m4a/aac/flac/wav, max 200MB)'
                    : 'Tap untuk memilih file audio * (mp3/m4a/aac/flac/wav, max 200MB)',
                style: const TextStyle(
                  fontSize: 13,
                  color: AppColors.textSecondary,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCoverPicker() {
    final preview = _coverBytes != null
        ? Image.memory(_coverBytes!, width: 56, height: 56, fit: BoxFit.cover)
        : null;

    return Row(
      children: [
        GestureDetector(
          onTap: _pickCover,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: preview ??
                Container(
                  width: 56,
                  height: 56,
                  color: AppColors.azureMistDeep.withOpacity(0.15),
                  child: const Icon(
                    CupertinoIcons.photo,
                    size: 22,
                    color: AppColors.azureMistDeep,
                  ),
                ),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            _hasNewCover
                ? 'Sampul siap diupload (.$_coverExtension)'
                : _existingCoverKey != null
                    ? 'Sampul sudah ada — tap untuk ganti'
                    : 'Sampul lagu (opsional)',
            style: const TextStyle(
              fontSize: 13,
              color: AppColors.textSecondary,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildAlbumDropdown(List<Album> albums) {
    return _buildDropdown<String>(
      value: _albumId,
      label: 'Album *',
      icon: CupertinoIcons.music_albums,
      items: albums
          .map(
            (album) => DropdownMenuItem(
              value: album.id,
              child: Text(album.title, overflow: TextOverflow.ellipsis),
            ),
          )
          .toList(),
      onChanged: (value) => setState(() => _albumId = value),
      emptyHint: 'Belum ada album',
    );
  }

  Widget _buildArtistDropdown(List<Artist> artists) {
    return _buildDropdown<String>(
      value: _artistId,
      label: 'Artis *',
      icon: CupertinoIcons.person_2,
      items: artists
          .map(
            (artist) => DropdownMenuItem(
              value: artist.id,
              child: Text(artist.name, overflow: TextOverflow.ellipsis),
            ),
          )
          .toList(),
      onChanged: (value) => setState(() => _artistId = value),
      emptyHint: 'Belum ada artis',
    );
  }

  Widget _buildDropdown<T>({
    required T? value,
    required String label,
    required IconData icon,
    required List<DropdownMenuItem<T>> items,
    required ValueChanged<T?> onChanged,
    required String emptyHint,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final effectiveItems = items.isEmpty
        ? [DropdownMenuItem<T>(value: null, child: Text(emptyHint))]
        : items;
    return DropdownButtonFormField<T>(
      value: value,
      isExpanded: true,
      items: effectiveItems,
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
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide(color: AppColors.getBorderGlass(isDark)),
        ),
      ),
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String label,
    required IconData icon,
    String? Function(String?)? validator,
    TextInputType? keyboardType,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return TextFormField(
      controller: controller,
      validator: validator,
      keyboardType: keyboardType,
      style: TextStyle(color: AppColors.getTextPrimary(isDark)),
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
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide(color: AppColors.getBorderGlass(isDark)),
        ),
      ),
    );
  }
}
