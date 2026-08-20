import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/catalog/data/r2_storage_service.dart';

/// Menampilkan gambar dari R2 berdasarkan object key (mis. `images/abc.jpg`).
///
/// - Jika [value] sudah berupa URL http/https, dipakai langsung.
/// - Jika berupa object key, signed URL short-lived dibuat via Worker.
/// - Selama loading ditampilkan placeholder glass; saat gagal ditampilkan
///   ikon [fallbackIcon].
class SignedImage extends ConsumerWidget {
  const SignedImage({
    super.key,
    required this.value,
    this.width,
    this.height,
    this.fit = BoxFit.cover,
    this.borderRadius = 16,
    this.fallbackIcon = CupertinoIcons.music_note,
  });

  final String? value;
  final double? width;
  final double? height;
  final BoxFit fit;
  final double borderRadius;
  final IconData fallbackIcon;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final raw = value;
    final isUrl = raw != null && raw.startsWith('http');

    final Widget child;
    if (raw == null || raw.isEmpty) {
      child = _Placeholder(icon: fallbackIcon);
    } else if (isUrl) {
      child = Image.network(raw, width: width, height: height, fit: fit);
    } else {
      final signedUrl = ref.watch(signedImageUrlProvider(raw));
      child = signedUrl.when(
        loading: () => _Placeholder(icon: fallbackIcon),
        error: (_, __) => _Placeholder(icon: fallbackIcon),
        data: (url) =>
            Image.network(url, width: width, height: height, fit: fit),
      );
    }

    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: SizedBox(width: width, height: height, child: child),
    );
  }
}

class _Placeholder extends StatelessWidget {
  const _Placeholder({required this.icon});

  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      width: double.infinity,
      height: double.infinity,
      color: isDark
          ? Colors.white.withOpacity(0.05)
          : Colors.black.withOpacity(0.05),
      child: Icon(icon, color: Colors.grey, size: 28),
    );
  }
}
