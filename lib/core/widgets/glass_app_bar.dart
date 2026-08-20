import 'dart:ui';
import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// Glassmorphism app bar with frosted blur effect.
class GlassAppBar extends StatelessWidget implements PreferredSizeWidget {
  const GlassAppBar({
    super.key,
    this.title,
    this.leading,
    this.actions = const [],
    this.centerTitle = false,
    this.blurSigma = 16,
    this.bottom,
  });

  final Widget? title;
  final Widget? leading;
  final List<Widget> actions;
  final bool centerTitle;
  final double blurSigma;
  final PreferredSizeWidget? bottom;

  @override
  Size get preferredSize =>
      Size.fromHeight(kToolbarHeight + (bottom?.preferredSize.height ?? 0));

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return ClipRRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: blurSigma, sigmaY: blurSigma),
        child: DecoratedBox(
          decoration: BoxDecoration(
            gradient: isDark
                ? AppColors.glassGradientDark
                : AppColors.glassGradientLight,
            border: Border(
              bottom: BorderSide(
                color: AppColors.getBorderGlassSubtle(isDark),
                width: 0.5,
              ),
            ),
          ),
          child: AppBar(
            title: title,
            leading: leading,
            actions: actions,
            centerTitle: centerTitle,
            backgroundColor: Colors.transparent,
            elevation: 0,
            scrolledUnderElevation: 0,
            bottom: bottom,
          ),
        ),
      ),
    );
  }
}
