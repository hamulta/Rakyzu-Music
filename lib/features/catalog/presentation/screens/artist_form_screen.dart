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
import '../../providers/catalog_providers.dart';
import '../../providers/role_provider.dart';
import '../widgets/catalog_access_guard.dart';

/// Form tambah/edit artis (staff/admin/owner).
/// - Tambah: nama wajib, bio opsional, foto profil opsional.
/// - Edit: semua field bisa diubah + toggle verified (hanya admin/owner).
class ArtistFormScreen extends ConsumerStatefulWidget {
  const ArtistFormScreen({super.key, this.artistId});

  final String? artistId;

  @override
  ConsumerState<ArtistFormScreen> createState() => _ArtistFormScreenState();
}

class _ArtistFormScreenState extends ConsumerState<ArtistFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _bioController = TextEditingController();

  bool _isEdit = false;
  bool _loading = false;
  bool _saving = false;
  bool _isVerified = false;

  Uint8List? _imageBytes;
  String _imageExtension = '';
  String? _existingImageKey;

  bool get _canUploadImage => _imageBytes != null && _imageExtension.isNotEmpty;

  @override
  void initState() {
    super.initState();
    _isEdit = widget.artistId != null;
    if (_isEdit) {
      _loadArtist();
    }
  }

  @override
  void didUpdateWidget(covariant ArtistFormScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.artistId != widget.artistId) {
      // Reset state when navigating to different artist or to Tambah (null)
      _nameController.clear();
      _bioController.clear();
      _imageBytes = null;
      _imageExtension = '';
      _existingImageKey = null;
      _isVerified = false;
      _isEdit = widget.artistId != null;
      if (_isEdit) {
        _loadArtist();
      } else {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _loadArtist() async {
    setState(() => _loading = true);
    try {
      final artist =
          await ref.read(catalogRepositoryProvider).getArtist(widget.artistId!);
      if (!mounted || artist == null) return;
      _nameController.text = artist.name;
      _bioController.text = artist.bio ?? '';
      _isVerified = artist.isVerified;
      _existingImageKey = artist.imageUrl;
    } on CatalogException catch (e) {
      if (!mounted) return;
      _showError(e.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _pickImage() async {
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
      _imageBytes = bytes;
      _imageExtension = ext;
    });
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      var imageKey = _existingImageKey;
      if (_canUploadImage) {
        imageKey = await ref.read(r2StorageServiceProvider).uploadBytes(
              folder: UploadLimits.imageFolder,
              bytes: _imageBytes!,
              extension: _imageExtension,
            );
      }

      final controller = ref.read(artistsControllerProvider.notifier);
      final name = _nameController.text.trim();
      final bio = _bioController.text.trim();

      if (_isEdit) {
        await controller.update(
          widget.artistId!,
          name: name,
          bio: bio.isEmpty ? null : bio,
          imageUrl: imageKey,
          isVerified: _isVerified,
        );
      } else {
        await controller.create(
          name: name,
          bio: bio.isEmpty ? null : bio,
          imageUrl: imageKey,
        );
      }

      if (!mounted) return;
      context.go(AppRoutes.catalogArtists);
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
    _nameController.dispose();
    _bioController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final role = ref.watch(currentAppRoleProvider).valueOrNull;
    final canToggleVerified = role?.isAdminOrOwner ?? false;

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
                  title: Text(_isEdit ? 'Edit Artis' : 'Tambah Artis'),
                  leading: IconButton(
                    icon: const Icon(CupertinoIcons.chevron_left),
                    onPressed: () => context.go(AppRoutes.catalogArtists),
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
                                      _buildImagePicker(),
                                      const SizedBox(height: 16),
                                      _buildTextField(
                                        controller: _nameController,
                                        label: 'Nama Artis *',
                                        icon: CupertinoIcons.person_fill,
                                        validator: (value) {
                                          if (value == null ||
                                              value.trim().isEmpty) {
                                            return 'Nama artis wajib diisi';
                                          }
                                          return null;
                                        },
                                      ),
                                      const SizedBox(height: 14),
                                      _buildTextField(
                                        controller: _bioController,
                                        label: 'Bio',
                                        icon: CupertinoIcons.text_alignleft,
                                        maxLines: 4,
                                      ),
                                      if (canToggleVerified) ...[
                                        const SizedBox(height: 14),
                                        SwitchListTile(
                                          value: _isVerified,
                                          onChanged: (value) => setState(
                                            () => _isVerified = value,
                                          ),
                                          title: const Text('Verified Artist'),
                                          subtitle: const Text(
                                            'Hanya admin/owner yang bisa mengubah',
                                            style: TextStyle(
                                              fontSize: 12,
                                              color: AppColors.textSecondary,
                                            ),
                                          ),
                                          activeColor: AppColors.azureMistDeep,
                                          contentPadding: EdgeInsets.zero,
                                        ),
                                      ],
                                    ],
                                  ),
                                ),
                                const SizedBox(height: 24),
                                GlassButton(
                                  label: _isEdit
                                      ? 'Simpan Perubahan'
                                      : 'Simpan Artis',
                                  icon: _isEdit
                                      ? CupertinoIcons.check_mark
                                      : CupertinoIcons.person_add,
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

  Widget _buildImagePicker() {
    final preview = _imageBytes != null
        ? Image.memory(_imageBytes!, width: 88, height: 88, fit: BoxFit.cover)
        : null;

    return Row(
      children: [
        GestureDetector(
          onTap: _pickImage,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(44),
            child: preview ??
                Container(
                  width: 88,
                  height: 88,
                  color: AppColors.azureMistDeep.withOpacity(0.15),
                  child: const Icon(
                    CupertinoIcons.camera_fill,
                    size: 28,
                    color: AppColors.azureMistDeep,
                  ),
                ),
          ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Text(
            _canUploadImage
                ? 'Foto siap diupload (.$_imageExtension)'
                : _existingImageKey != null
                    ? 'Foto profil sudah ada — tap gambar untuk ganti'
                    : 'Tap gambar untuk memilih foto profil (jpg/png/webp/svg, max 120MB)',
            style: const TextStyle(
              fontSize: 13,
              color: AppColors.textSecondary,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String label,
    required IconData icon,
    String? Function(String?)? validator,
    int maxLines = 1,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return TextFormField(
      controller: controller,
      maxLines: maxLines,
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
