import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/ads/ads_gate_provider.dart';
import '../../../../core/services/offline_download_service.dart';
import '../../../catalog/data/r2_storage_service.dart';
import '../../../catalog/models/song.dart';

class DownloadButton extends ConsumerStatefulWidget {
  const DownloadButton({super.key, required this.song});
  final Song song;
  @override
  ConsumerState<DownloadButton> createState() => _DownloadButtonState();
}

class _DownloadButtonState extends ConsumerState<DownloadButton> {
  bool _downloading = false;
  bool? _isDownloaded;

  @override
  void initState() {
    super.initState();
    _check();
  }

  Future<void> _check() async {
    final v =
        await OfflineDownloadService.instance.isDownloaded(widget.song.id);
    if (mounted) setState(() => _isDownloaded = v);
  }

  @override
  Widget build(BuildContext context) {
    final gate = ref.watch(adsGateProvider);
    if (gate.shouldShowAds) {
      // Free tier — disabled, prompt upgrade.
      return IconButton(
        icon: const Icon(CupertinoIcons.cloud_download, color: Colors.grey),
        onPressed: () => ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Download hanya untuk Premium'))),
      );
    }
    if (_isDownloaded == null)
      return const SizedBox(
          width: 24, height: 24, child: CupertinoActivityIndicator());
    if (_isDownloaded == true) {
      return IconButton(
          icon: const Icon(CupertinoIcons.checkmark_alt_circle_fill,
              color: Colors.green),
          onPressed: () async {
            await OfflineDownloadService.instance.delete(widget.song.id);
            if (mounted) setState(() => _isDownloaded = false);
          });
    }
    return IconButton(
      icon: _downloading
          ? const SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(strokeWidth: 2))
          : const Icon(CupertinoIcons.cloud_download),
      onPressed: _downloading
          ? null
          : () async {
              setState(() => _downloading = true);
              try {
                final r2 = ref.read(r2StorageServiceProvider);
                final url =
                    await r2.getReadUrl(widget.song.audioUrl!, expires: 3600);
                await OfflineDownloadService.instance
                    .downloadFromUrl(widget.song.id, url);
                if (mounted) setState(() => _isDownloaded = true);
              } catch (e) {
                if (mounted)
                  ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Download failed: $e')));
              } finally {
                if (mounted) setState(() => _downloading = false);
              }
            },
    );
  }
}
