import 'dart:ui';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// Glassmorphism bottom navigation bar built on `CupertinoTabBar`.
/// Subclasses CupertinoTabBar so it can be passed to `CupertinoTabScaffold`,
/// wrapping the bar in a frosted-glass container with backdrop blur.
class GlassBottomNavBar extends CupertinoTabBar {
  GlassBottomNavBar({
    super.key,
    required super.currentIndex,
    required super.onTap,
    required List<GlassNavItem> items,
    this.borderRadius = 28,
  }) : super(
          items: [
            for (final item in items)
              BottomNavigationBarItem(
                icon: Icon(item.icon, size: 24),
                activeIcon: Icon(item.activeIcon, size: 24),
                label: item.label,
              ),
          ],
          backgroundColor: Colors.transparent,
          border: const Border(),
        );

  final double borderRadius;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final child = super.build(context);

    return ClipRRect(
      borderRadius: BorderRadius.vertical(top: Radius.circular(borderRadius)),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
        child: DecoratedBox(
          decoration: BoxDecoration(
            gradient: isDark
                ? AppColors.glassGradientDark
                : AppColors.glassGradientLight,
            border: Border(
              top:
                  BorderSide(color: AppColors.getBorderGlass(isDark), width: 1),
            ),
          ),
          child: child,
        ),
      ),
    );
  }
}

class GlassNavItem {
  const GlassNavItem({
    required this.icon,
    required this.activeIcon,
    required this.label,
  });

  final IconData icon;
  final IconData activeIcon;
  final String label;
}
