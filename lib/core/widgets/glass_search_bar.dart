import 'dart:ui';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// Glassmorphism search input with frosted blur and clear button.
class GlassSearchBar extends StatefulWidget {
  const GlassSearchBar({
    super.key,
    this.controller,
    this.hintText = 'Search songs, artists, albums...',
    this.onChanged,
    this.onSubmitted,
    this.autofocus = false,
    this.padding = const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
  });

  final TextEditingController? controller;
  final String hintText;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;
  final bool autofocus;
  final EdgeInsetsGeometry padding;

  @override
  State<GlassSearchBar> createState() => _GlassSearchBarState();
}

class _GlassSearchBarState extends State<GlassSearchBar> {
  late final TextEditingController _internalController;
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _internalController = TextEditingController();
    _controller = widget.controller ?? _internalController;
  }

  @override
  void dispose() {
    _internalController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Padding(
      padding: widget.padding,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(20),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
          child: CupertinoTextField(
            controller: _controller,
            autofocus: widget.autofocus,
            placeholder: widget.hintText,
            placeholderStyle: TextStyle(
              color: AppColors.getTextSecondary(isDark),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            style: TextStyle(color: AppColors.getTextPrimary(isDark)),
            decoration: BoxDecoration(
              gradient: isDark
                  ? AppColors.glassGradientDark
                  : AppColors.glassGradientLight,
              border: Border.all(color: AppColors.getBorderGlass(isDark)),
              borderRadius: BorderRadius.circular(20),
            ),
            prefix: const Padding(
              padding: EdgeInsets.only(right: 8),
              child: Icon(
                CupertinoIcons.search,
                size: 20,
                color: AppColors.textSecondary,
              ),
            ),
            suffix: ValueListenableBuilder<TextEditingValue>(
              valueListenable: _controller,
              builder: (context, value, _) {
                if (value.text.isEmpty) return const SizedBox.shrink();
                return GestureDetector(
                  onTap: () {
                    _controller.clear();
                    widget.onChanged?.call('');
                  },
                  child: const Padding(
                    padding: EdgeInsets.only(left: 8),
                    child: Icon(
                      CupertinoIcons.clear_circled_solid,
                      size: 18,
                      color: AppColors.textSecondary,
                    ),
                  ),
                );
              },
            ),
            onChanged: widget.onChanged,
            onSubmitted: widget.onSubmitted,
          ),
        ),
      ),
    );
  }
}
