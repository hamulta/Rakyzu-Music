import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/constants/app_routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_app_bar.dart';
import '../../../../core/widgets/glass_button.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../data/catalog_exception.dart';
import '../../data/r2_storage_service.dart';
import '../../data/upload_limits.dart';
import '../../models/artist.dart';
import '../../providers/catalog_providers.dart';
import '../widgets/catalog_access_guard.dart';

/// Form tambah/edit album (staff/admin/owner).
/// - Tambah: judul & artis wajib, genre/rilis/sampul opsional.
/// - Edit: semua field bisa diubah.
class AlbumFormScreen extends ConsumerStatefulWidget {
  const AlbumFormScreen({super.key, this.albumId});

  final String? albumId;

  @override
  ConsumerState<AlbumFormScreen> createState() => _AlbumFormScreenState();
}

class _AlbumFormScreenState extends ConsumerState<AlbumFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _titleController = TextEditingController();
  final _genreController = TextEditingController();

  bool _isEdit = false;
  bool _loading = false;
  bool _saving = false;

  String? _artistId;
  DateTime? _releaseDate;

  Uint8List? _coverBytes;
  String _coverExtension = '';
  String? _existingCoverKey;

  bool get _canUploadCover => _coverBytes != null && _coverExtension.isNotEmpty;

  @override
  void initState() {
    super.initState();
    _isEdit = widget.albumId != null;
    if (_isEdit) {
      _loadAlbum();
    }
  }

  Future<void> _loadAlbum() async {
    setState(() => _loading = true);
    try {
      final album =
          await ref.read(catalogRepositoryProvider).getAlbum(widget.albumId!);
      if (!mounted || album == null) return;
      _titleController.text = album.title;
      _genreController.text = album.genre ?? '';
      _artistId = album.artistId.isEmpty ? null : album.artistId;
      _releaseDate = album.releaseDate;
      _existingCoverKey = album.coverUrl;
    } on CatalogException catch (e) {
      if (!mounted) return;
      _showError(e.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
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
    });
  }

  Future<void> _pickReleaseDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _releaseDate ?? now,
      firstDate: DateTime(1900),
      lastDate: now,
      builder: (context, child) => Theme(
        data: Theme.of(context).copyWith(
          colorScheme: ColorScheme.fromSeed(
            seedColor: AppColors.azureMistDeep,
            brightness: Theme.of(context).brightness,
          ),
        ),
        child: child!,
      ),
    );
    if (picked != null && mounted) {
      setState(() => _releaseDate = picked);
    }
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    final artistId = _artistId;
    if (artistId == null) {
      _showError('Pilih artis terlebih dahulu.');
      return;
    }
    setState(() => _saving = true);
    try {
      var coverKey = _existingCoverKey;
      if (_canUploadCover) {
        coverKey = await ref.read(r2StorageServiceProvider).uploadBytes(
              folder: UploadLimits.imageFolder,
              bytes: _coverBytes!,
              extension: _coverExtension,
            );
      }

      final controller = ref.read(albumsControllerProvider.notifier);
      final title = _titleController.text.trim();
      final genre = _genreController.text.trim();

      if (_isEdit) {
        await controller.update(
          widget.albumId!,
          title: title,
          artistId: artistId,
          coverUrl: coverKey,
          releaseDate: _releaseDate,
          genre: genre.isEmpty ? null : genre,
        );
      } else {
        await controller.create(
          title: title,
          artistId: artistId,
          coverUrl: coverKey,
          releaseDate: _releaseDate,
          genre: genre.isEmpty ? null : genre,
        );
      }

      if (!mounted) return;
      context.go(AppRoutes.catalogAlbums);
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

  void _showError(String message) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  void dispose() {
    _titleController.dispose();
    _genreController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final artists =
        ref.watch(artistsControllerProvider).valueOrNull ?? const [];

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
                  title: Text(_isEdit ? 'Edit Album' : 'Tambah Album'),
                  leading: IconButton(
                    icon: const Icon(CupertinoIcons.chevron_left),
                    onPressed: () => context.go(AppRoutes.catalogAlbums),
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
                                      _buildCoverPicker(),
                                      const SizedBox(height: 16),
                                      _buildTextField(
                                        controller: _titleController,
                                        label: 'Judul Album *',
                                        icon: CupertinoIcons.music_albums,
                                        validator: (value) {
                                          if (value == null ||
                                              value.trim().isEmpty) {
                                            return 'Judul album wajib diisi';
                                          }
                                          return null;
                                        },
                                      ),
                                      const SizedBox(height: 14),
                                      _buildArtistDropdown(artists),
                                      const SizedBox(height: 14),
                                      _buildTextField(
                                        controller: _genreController,
                                        label: 'Genre',
                                        icon: CupertinoIcons.tag,
                                      ),
                                      const SizedBox(height: 14),
                                      _buildReleaseDateField(),
                                    ],
                                  ),
                                ),
                                const SizedBox(height: 24),
                                GlassButton(
                                  label: _isEdit
                                      ? 'Simpan Perubahan'
                                      : 'Simpan Album',
                                  icon: _isEdit
                                      ? CupertinoIcons.check_mark
                                      : CupertinoIcons.music_albums,
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

  Widget _buildCoverPicker() {
    final preview = _coverBytes != null
        ? Image.memory(_coverBytes!, width: 88, height: 88, fit: BoxFit.cover)
        : null;

    return Row(
      children: [
        GestureDetector(
          onTap: _pickCover,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: preview ??
                Container(
                  width: 88,
                  height: 88,
                  color: AppColors.azureMistDeep.withOpacity(0.15),
                  child: const Icon(
                    CupertinoIcons.music_albums,
                    size: 28,
                    color: AppColors.azureMistDeep,
                  ),
                ),
          ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Text(
            _canUploadCover
                ? 'Sampul siap diupload (.$_coverExtension)'
                : _existingCoverKey != null
                    ? 'Sampul sudah ada — tap gambar untuk ganti'
                    : 'Tap gambar untuk memilih sampul (jpg/png/webp/svg, max 120MB)',
            style: const TextStyle(
              fontSize: 13,
              color: AppColors.textSecondary,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildArtistDropdown(List<Artist> artists) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return DropdownButtonFormField<String>(
      value: _artistId,
      isExpanded: true,
      items: artists.map((artist) {
        return DropdownMenuItem(
          value: artist.id,
          child: Text(artist.name, overflow: TextOverflow.ellipsis),
        );
      }).toList(),
      onChanged: (value) => setState(() => _artistId = value),
      decoration: InputDecoration(
        labelText: 'Artis *',
        prefixIcon: const Icon(CupertinoIcons.person_2, size: 20),
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

  Widget _buildReleaseDateField() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final label = _releaseDate == null
        ? 'Tanggal Rilis'
        : 'Rilis ${_releaseDate!.year}-${_releaseDate!.month.toString().padLeft(2, '0')}-${_releaseDate!.day.toString().padLeft(2, '0')}';
    return InkWell(
      onTap: _pickReleaseDate,
      borderRadius: BorderRadius.circular(14),
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: label,
          prefixIcon: const Icon(CupertinoIcons.calendar, size: 20),
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
        child: Text(
          _releaseDate == null ? 'Tap untuk memilih' : 'Tap untuk mengubah',
          style: TextStyle(color: AppColors.getTextPrimary(isDark)),
        ),
      ),
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String label,
    required IconData icon,
    String? Function(String?)? validator,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return TextFormField(
      controller: controller,
      validator: validator,
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
