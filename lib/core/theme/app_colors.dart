import 'package:flutter/material.dart';

/// Rakyzu Music Design Tokens - Azure Mist & Ivory Palette
/// Based on Master Prompt §5.2
class AppColors {
  // ============================================
  // LIGHT MODE TOKENS
  // ============================================

  // Primary Gradient Background
  static const Color azureMistBase = Color(0xFFDCEEFB);
  static const Color azureMistDeep = Color(0xFF7FB3E8);

  // Surface / Card Colors
  static const Color ivoryBase = Color(0xFFFAF7F0);
  static const Color ivorySoft = Color(0xFFFFFDF8);

  // Text Colors
  static const Color textPrimary = Color(0xFF1E2A38);
  static const Color textSecondary = Color(0xFF5C6B7A);
  static const Color textOnPrimary = Color(0xFFFFFFFF);

  // Glassmorphism Tokens
  static const Color glassOverlay = Color(0x40FFFFFF); // rgba(255,255,255,0.25)
  static const Color glassOverlayStrong =
      Color(0x66FFFFFF); // rgba(255,255,255,0.4)
  static const Color borderGlass = Color(0x66FFFFFF); // rgba(255,255,255,0.4)
  static const Color borderGlassSubtle =
      Color(0x33FFFFFF); // rgba(255,255,255,0.2)

  // Accent Colors
  static const Color accentSuccess = Color(0xFF8FD9A8);
  static const Color accentWarning = Color(0xFFF4C68A);
  static const Color accentError = Color(0xFFE88D8D);
  static const Color accentInfo = Color(0xFF7FB3E8);

  // Interactive States
  static const Color primaryButton = Color(0xFF7FB3E8);
  static const Color primaryButtonPressed = Color(0xFF5A9BD8);
  static const Color secondaryButton = Color(0xFFE8E8E8);
  static const Color destructiveButton = Color(0xFFE88D8D);

  // Status
  static const Color onlineIndicator = Color(0xFF8FD9A8);
  static const Color offlineIndicator = Color(0xFF9CA3AF);

  // Shadows
  static const Color shadowLight = Color(0x1A000000); // 10% opacity
  static const Color shadowMedium = Color(0x33000000); // 20% opacity
  static const Color shadowDark = Color(0x4D000000); // 30% opacity

  // Dividers
  static const Color dividerLight = Color(0x1A000000);
  static const Color dividerDark = Color(0x33FFFFFF);

  // ============================================
  // DARK MODE TOKENS
  // ============================================

  // Primary Gradient Background (Dark)
  static const Color darkBase = Color(0xFF0F1A24);
  static const Color darkSurface = Color(0xFF172330);
  static const Color darkSurfaceElevated = Color(0xFF1E2D3D);
  static const Color darkCard = Color(0xFF1A2735);

  // Text Colors (Dark)
  static const Color darkTextPrimary = Color(0xFFF5F5F5);
  static const Color darkTextSecondary = Color(0xFFB0BEC5);
  static const Color darkTextTertiary = Color(0xFF78909C);

  // Glassmorphism Tokens (Dark)
  static const Color darkGlassOverlay = Color(0x33000000); // rgba(0,0,0,0.2)
  static const Color darkGlassOverlayStrong =
      Color(0x4D000000); // rgba(0,0,0,0.3)
  static const Color darkBorderGlass =
      Color(0x33FFFFFF); // rgba(255,255,255,0.2)
  static const Color darkBorderGlassStrong =
      Color(0x4DFFFFFF); // rgba(255,255,255,0.3)

  // Accent Colors (Dark - same as light for brand consistency)
  static const Color darkAccentSuccess = Color(0xFF8FD9A8);
  static const Color darkAccentWarning = Color(0xFFF4C68A);
  static const Color darkAccentError = Color(0xFFE88D8D);
  static const Color darkAccentInfo = Color(0xFF7FB3E8);

  // Interactive States (Dark)
  static const Color darkPrimaryButton = Color(0xFF7FB3E8);
  static const Color darkPrimaryButtonPressed = Color(0xFF5A9BD8);
  static const Color darkSecondaryButton = Color(0xFF2D3A47);
  static const Color darkDestructiveButton = Color(0xFFE88D8D);

  // Shadows (Dark)
  static const Color darkShadowLight = Color(0x33000000);
  static const Color darkShadowMedium = Color(0x4D000000);
  static const Color darkShadowDark = Color(0x66000000);

  // Gradients
  static const LinearGradient lightBackgroundGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [
      azureMistBase,
      Color(0xFFE8F4FC),
      ivoryBase,
    ],
    stops: [0.0, 0.5, 1.0],
  );

  static const LinearGradient darkBackgroundGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [
      darkBase,
      Color(0xFF132030),
      darkSurface,
    ],
    stops: [0.0, 0.5, 1.0],
  );

  static const LinearGradient glassGradientLight = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [
      glassOverlay,
      glassOverlayStrong,
    ],
  );

  static const LinearGradient glassGradientDark = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [
      darkGlassOverlay,
      darkGlassOverlayStrong,
    ],
  );

  static const LinearGradient primaryButtonGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [
      azureMistDeep,
      Color(0xFF5A9BD8),
    ],
  );

  static const LinearGradient premiumGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [
      Color(0xFFFFD700),
      Color(0xFFFFA500),
      Color(0xFFFF8C00),
    ],
  );

  // Helper methods
  static Color getGlassOverlay(bool isDark) =>
      isDark ? darkGlassOverlay : glassOverlay;
  static Color getGlassOverlayStrong(bool isDark) =>
      isDark ? darkGlassOverlayStrong : glassOverlayStrong;
  static Color getBorderGlass(bool isDark) =>
      isDark ? darkBorderGlass : borderGlass;
  static Color getBorderGlassSubtle(bool isDark) =>
      isDark ? darkBorderGlass : borderGlassSubtle;
  static Color getTextPrimary(bool isDark) =>
      isDark ? darkTextPrimary : textPrimary;
  static Color getTextSecondary(bool isDark) =>
      isDark ? darkTextSecondary : textSecondary;
  static Color getSurface(bool isDark) => isDark ? darkCard : ivoryBase;
  static LinearGradient getBackgroundGradient(bool isDark) =>
      isDark ? darkBackgroundGradient : lightBackgroundGradient;
  static LinearGradient getGlassGradient(bool isDark) =>
      isDark ? glassGradientDark : glassGradientLight;
}
