import 'dart:ui';
import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// Glassmorphism button with frosted background, blur, and gradient accent.
class GlassButton extends StatelessWidget {
  const GlassButton({
    super.key,
    required this.label,
    this.onPressed,
    this.icon,
    this.leading,
    this.loading = false,
    this.isPrimary = false,
    this.isDestructive = false,
    this.isFullWidth = true,
    this.borderRadius = 16,
    this.height = 52,
    this.padding = const EdgeInsets.symmetric(horizontal: 24),
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;

  /// Custom leading widget (takes precedence over [icon]), e.g. brand logos.
  final Widget? leading;
  final bool loading;
  final bool isPrimary;
  final bool isDestructive;
  final bool isFullWidth;
  final double borderRadius;
  final double height;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final enabled = onPressed != null && !loading;

    // Determine background gradient
    Widget background;
    if (isPrimary) {
      background = const DecoratedBox(
        decoration: BoxDecoration(
          gradient: AppColors.primaryButtonGradient,
        ),
      );
    } else if (isDestructive) {
      background = DecoratedBox(
        decoration: BoxDecoration(
          color: AppColors.accentError.withOpacity(0.9),
        ),
      );
    } else {
      background = DecoratedBox(
        decoration: BoxDecoration(
          gradient: isDark
              ? AppColors.glassGradientDark
              : AppColors.glassGradientLight,
          border: Border.all(color: AppColors.getBorderGlass(isDark)),
        ),
      );
    }

    final content = loading
        ? SizedBox(
            width: 24,
            height: 24,
            child: CircularProgressIndicator(
              strokeWidth: 2.5,
              color: isPrimary
                  ? Colors.white
                  : (isDark
                      ? AppColors.darkTextPrimary
                      : AppColors.textPrimary),
            ),
          )
        : Row(
            mainAxisSize: MainAxisSize.min,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (leading != null) ...[
                leading!,
                const SizedBox(width: 8),
              ] else if (icon != null) ...[
                Icon(
                  icon,
                  color: isPrimary
                      ? Colors.white
                      : (isDark
                          ? AppColors.darkTextPrimary
                          : AppColors.textPrimary),
                  size: 20,
                ),
                const SizedBox(width: 8),
              ],
              Flexible(
                child: Text(
                  label,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: isPrimary
                        ? Colors.white
                        : (isDark
                            ? AppColors.darkTextPrimary
                            : AppColors.textPrimary),
                  ),
                ),
              ),
            ],
          );

    final glass = ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
        child: SizedBox(
          height: height,
          child: Material(
            color: Colors.transparent,
            child: InkWell(
              onTap: enabled ? onPressed : null,
              borderRadius: BorderRadius.circular(borderRadius),
              child: Container(
                padding: padding,
                alignment: Alignment.center,
                child: content,
              ),
            ),
          ),
        ),
      ),
    );

    return Opacity(
      opacity: enabled ? 1.0 : 0.5,
      child: SizedBox(
        width: isFullWidth ? double.infinity : null,
        child: Stack(
          children: [
            Positioned.fill(child: background),
            glass,
          ],
        ),
      ),
    );
  }
}
