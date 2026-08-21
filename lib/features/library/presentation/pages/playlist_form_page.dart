import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../providers/playlist_providers.dart';

class PlaylistFormPage extends ConsumerStatefulWidget {
  const PlaylistFormPage({super.key, this.playlistId});
  final String? playlistId;

  @override
  ConsumerState<PlaylistFormPage> createState() => _PlaylistFormPageState();
}

class _PlaylistFormPageState extends ConsumerState<PlaylistFormPage> {
  final _nameCtrl = TextEditingController();
  final _descCtrl = TextEditingController();
  bool _isPublic = false;
  bool _saving = false;
  bool _loaded = false;

  @override
  void dispose() {
    _nameCtrl.dispose();
    _descCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadExisting() async {
    if (widget.playlistId == null || _loaded) return;
    _loaded = true;
    final pl =
        await ref.read(playlistDetailProvider(widget.playlistId!).future);
    if (pl != null && mounted) {
      _nameCtrl.text = pl.name;
      _descCtrl.text = pl.description ?? '';
      setState(() => _isPublic = pl.isPublic);
    }
  }

  Future<void> _save() async {
    final name = _nameCtrl.text.trim();
    if (name.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Nama playlist wajib diisi')),);
      return;
    }
    setState(() => _saving = true);
    try {
      final repo = ref.read(playlistRepositoryProvider);
      if (widget.playlistId == null) {
        await repo.createPlaylist(
            name: name,
            description: _descCtrl.text.trim(),
            isPublic: _isPublic,);
      } else {
        await repo.updatePlaylist(widget.playlistId!,
            name: name,
            description: _descCtrl.text.trim(),
            isPublic: _isPublic,);
      }
      ref.invalidate(myPlaylistsProvider);
      if (widget.playlistId != null) {
        ref.invalidate(playlistDetailProvider(widget.playlistId!));
      }
      if (mounted) context.pop();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Gagal menyimpan: $e')));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    // Load existing data async.
    if (widget.playlistId != null) {
      _loadExisting();
    }
    final isEdit = widget.playlistId != null;
    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
            gradient: isDark
                ? AppColors.darkBackgroundGradient
                : AppColors.lightBackgroundGradient,),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(20),
            children: [
              Row(
                children: [
                  IconButton(
                      onPressed: () => context.pop(),
                      icon: const Icon(CupertinoIcons.back),),
                  Text(isEdit ? 'Edit Playlist' : 'New Playlist',
                      style: Theme.of(context)
                          .textTheme
                          .titleLarge
                          ?.copyWith(fontWeight: FontWeight.w700),),
                ],
              ),
              const SizedBox(height: 20),
              GlassCard(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Name',
                        style: TextStyle(fontWeight: FontWeight.w600),),
                    const SizedBox(height: 8),
                    CupertinoTextField(
                        controller: _nameCtrl,
                        placeholder: 'My Awesome Playlist',
                        maxLength: 60,),
                    const SizedBox(height: 16),
                    const Text('Description (optional)',
                        style: TextStyle(fontWeight: FontWeight.w600),),
                    const SizedBox(height: 8),
                    CupertinoTextField(
                        controller: _descCtrl,
                        placeholder: 'Describe your playlist',
                        maxLines: 3,),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        const Expanded(
                            child: Text('Public',
                                style: TextStyle(fontWeight: FontWeight.w600),),),
                        CupertinoSwitch(
                            value: _isPublic,
                            onChanged: (v) => setState(() => _isPublic = v),),
                      ],
                    ),
                    Text(
                      _isPublic
                          ? 'Anyone can view & play • appears in search'
                          : 'Only you can view',
                      style: const TextStyle(
                          color: AppColors.textSecondary, fontSize: 12,),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                child: CupertinoButton.filled(
                  onPressed: _saving ? null : _save,
                  child: _saving
                      ? const CupertinoActivityIndicator(color: Colors.white)
                      : Text(isEdit ? 'Save Changes' : 'Create Playlist'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
