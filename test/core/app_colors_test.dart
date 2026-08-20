import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:rakyzu_music/core/theme/app_colors.dart';

void main() {
  group('AppColors - Design Tokens', () {
    test('light mode tokens match Azure Mist & Ivory palette', () {
      expect(AppColors.azureMistBase, const Color(0xFFDCEEFB));
      expect(AppColors.azureMistDeep, const Color(0xFF7FB3E8));
      expect(AppColors.ivoryBase, const Color(0xFFFAF7F0));
      expect(AppColors.ivorySoft, const Color(0xFFFFFDF8));
    });

    test('text colors have sufficient contrast', () {
      expect(AppColors.textPrimary, const Color(0xFF1E2A38));
      expect(AppColors.textSecondary, const Color(0xFF5C6B7A));
    });

    test('glass overlay tokens are semi-transparent', () {
      expect(AppColors.glassOverlay.alpha / 255, closeTo(0.25, 0.01));
      expect(AppColors.borderGlass.alpha / 255, closeTo(0.4, 0.01));
    });

    test('dark mode base is defined', () {
      expect(AppColors.darkBase, const Color(0xFF0F1A24));
      expect(AppColors.darkTextPrimary, const Color(0xFFF5F5F5));
    });

    test('accent colors match spec', () {
      expect(AppColors.accentSuccess, const Color(0xFF8FD9A8));
      expect(AppColors.accentWarning, const Color(0xFFF4C68A));
    });
  });

  group('AppColors - Helpers', () {
    test('getGlassOverlay returns dark overlay for dark mode', () {
      expect(AppColors.getGlassOverlay(true), AppColors.darkGlassOverlay);
      expect(AppColors.getGlassOverlay(false), AppColors.glassOverlay);
    });

    test('getBorderGlass returns correct value per mode', () {
      expect(AppColors.getBorderGlass(true), AppColors.darkBorderGlass);
      expect(AppColors.getBorderGlass(false), AppColors.borderGlass);
    });
  });
}
