import 'dart:ui';
import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// A frosted-glass card with backdrop blur, subtle border, and soft shadows.
/// Core building block of the Rakyzu Music Glassmorphism design system.
class GlassCard extends StatelessWidget {
  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.borderRadius = 24,
    this.blurSigma = 20,
    this.margin,
    this.onTap,
    this.width,
    this.height,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final double borderRadius;
  final double blurSigma;
  final EdgeInsetsGeometry? margin;
  final VoidCallback? onTap;
  final double? width;
  final double? height;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final radius = BorderRadius.circular(borderRadius);

    final glass = ClipRRect(
      borderRadius: radius,
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: blurSigma, sigmaY: blurSigma),
        child: Container(
          width: width,
          height: height,
          padding: padding,
          decoration: BoxDecoration(
            gradient: isDark
                ? AppColors.glassGradientDark
                : AppColors.glassGradientLight,
            borderRadius: radius,
            border: Border.all(
              color: AppColors.getBorderGlass(isDark),
              width: 1,
            ),
          ),
          child: child,
        ),
      ),
    );

    return Container(
      margin: margin,
      child: onTap != null
          ? GestureDetector(
              onTap: onTap,
              child: glass,
            )
          : glass,
    );
  }
}
