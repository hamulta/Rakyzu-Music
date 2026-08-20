import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app_colors.dart';

/// Rakyzu Music Theme Configuration
/// Based on Master Prompt §5 - Glassmorphism "Azure Mist & Ivory"
class AppTheme {
  AppTheme._();

  // ============================================
  // TYPOGRAPHY
  // ============================================

  /// Font family fallback: SF Pro on iOS, Inter on Android/Web
  static const String _fontFamilyFallback = 'Inter';

  static const double _displayLarge = 40;
  static const double _displayMedium = 36;
  static const double _displaySmall = 32;
  static const double _headlineLarge = 28;
  static const double _headlineMedium = 24;
  static const double _headlineSmall = 20;
  static const double _titleLarge = 18;
  static const double _titleMedium = 16;
  static const double _titleSmall = 14;
  static const double _bodyLarge = 16;
  static const double _bodyMedium = 14;
  static const double _bodySmall = 12;
  static const double _labelLarge = 14;
  static const double _labelMedium = 12;
  static const double _labelSmall = 10;

  static TextTheme _buildTextTheme(Color textPrimary, Color textSecondary) {
    return TextTheme(
      displayLarge: TextStyle(
        fontSize: _displayLarge,
        fontWeight: FontWeight.w700,
        color: textPrimary,
        letterSpacing: -0.5,
      ),
      displayMedium: TextStyle(
        fontSize: _displayMedium,
        fontWeight: FontWeight.w700,
        color: textPrimary,
        letterSpacing: -0.5,
      ),
      displaySmall: TextStyle(
        fontSize: _displaySmall,
        fontWeight: FontWeight.w700,
        color: textPrimary,
        letterSpacing: -0.25,
      ),
      headlineLarge: TextStyle(
        fontSize: _headlineLarge,
        fontWeight: FontWeight.w600,
        color: textPrimary,
      ),
      headlineMedium: TextStyle(
        fontSize: _headlineMedium,
        fontWeight: FontWeight.w600,
        color: textPrimary,
      ),
      headlineSmall: TextStyle(
        fontSize: _headlineSmall,
        fontWeight: FontWeight.w600,
        color: textPrimary,
      ),
      titleLarge: TextStyle(
        fontSize: _titleLarge,
        fontWeight: FontWeight.w600,
        color: textPrimary,
      ),
      titleMedium: TextStyle(
        fontSize: _titleMedium,
        fontWeight: FontWeight.w600,
        color: textPrimary,
      ),
      titleSmall: TextStyle(
        fontSize: _titleSmall,
        fontWeight: FontWeight.w600,
        color: textPrimary,
      ),
      bodyLarge: TextStyle(
        fontSize: _bodyLarge,
        fontWeight: FontWeight.w400,
        color: textPrimary,
        height: 1.5,
      ),
      bodyMedium: TextStyle(
        fontSize: _bodyMedium,
        fontWeight: FontWeight.w400,
        color: textSecondary,
        height: 1.4,
      ),
      bodySmall: TextStyle(
        fontSize: _bodySmall,
        fontWeight: FontWeight.w400,
        color: textSecondary,
        height: 1.3,
      ),
      labelLarge: TextStyle(
        fontSize: _labelLarge,
        fontWeight: FontWeight.w600,
        color: textPrimary,
      ),
      labelMedium: TextStyle(
        fontSize: _labelMedium,
        fontWeight: FontWeight.w500,
        color: textPrimary,
      ),
      labelSmall: TextStyle(
        fontSize: _labelSmall,
        fontWeight: FontWeight.w500,
        color: textSecondary,
        letterSpacing: 0.5,
      ),
    );
  }

  // ============================================
  // LIGHT THEME
  // ============================================

  static ThemeData get lightTheme {
    const colorScheme = ColorScheme.light(
      primary: AppColors.azureMistDeep,
      onPrimary: AppColors.textOnPrimary,
      secondary: AppColors.ivorySoft,
      onSecondary: AppColors.textPrimary,
      surface: AppColors.ivoryBase,
      onSurface: AppColors.textPrimary,
      error: AppColors.accentError,
      onError: Colors.white,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: AppColors.azureMistBase,
      fontFamily: _fontFamilyFallback,
      textTheme:
          _buildTextTheme(AppColors.textPrimary, AppColors.textSecondary),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: true,
        systemOverlayStyle: SystemUiOverlayStyle.dark,
        iconTheme: IconThemeData(color: AppColors.textPrimary),
        titleTextStyle: TextStyle(
          color: AppColors.textPrimary,
          fontSize: _titleLarge,
          fontWeight: FontWeight.w600,
        ),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: AppColors.ivoryBase,
        selectedItemColor: AppColors.azureMistDeep,
        unselectedItemColor: AppColors.textSecondary,
        type: BottomNavigationBarType.fixed,
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.azureMistDeep,
          foregroundColor: AppColors.textOnPrimary,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: AppColors.azureMistDeep,
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.ivoryBase.withOpacity(0.8),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppColors.borderGlassSubtle),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppColors.borderGlassSubtle),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide:
              const BorderSide(color: AppColors.azureMistDeep, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppColors.accentError),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide:
              const BorderSide(color: AppColors.accentError, width: 1.5),
        ),
      ),
      dividerTheme: const DividerThemeData(
        color: AppColors.dividerLight,
        thickness: 0.5,
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: AppColors.textPrimary,
        contentTextStyle: const TextStyle(color: Colors.white),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
      pageTransitionsTheme: const PageTransitionsTheme(
        builders: {
          TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
          TargetPlatform.android: FadeUpwardsPageTransitionsBuilder(),
        },
      ),
    );
  }

  // ============================================
  // DARK THEME
  // ============================================

  static ThemeData get darkTheme {
    const colorScheme = ColorScheme.dark(
      primary: AppColors.azureMistDeep,
      onPrimary: AppColors.darkTextPrimary,
      secondary: AppColors.darkCard,
      onSecondary: AppColors.darkTextPrimary,
      surface: AppColors.darkSurface,
      onSurface: AppColors.darkTextPrimary,
      error: AppColors.darkAccentError,
      onError: Colors.white,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: AppColors.darkBase,
      fontFamily: _fontFamilyFallback,
      textTheme: _buildTextTheme(
          AppColors.darkTextPrimary, AppColors.darkTextSecondary,),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: true,
        systemOverlayStyle: SystemUiOverlayStyle.light,
        iconTheme: IconThemeData(color: AppColors.darkTextPrimary),
        titleTextStyle: TextStyle(
          color: AppColors.darkTextPrimary,
          fontSize: _titleLarge,
          fontWeight: FontWeight.w600,
        ),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: AppColors.darkCard,
        selectedItemColor: AppColors.azureMistDeep,
        unselectedItemColor: AppColors.darkTextSecondary,
        type: BottomNavigationBarType.fixed,
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.azureMistDeep,
          foregroundColor: AppColors.darkTextPrimary,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: AppColors.azureMistDeep,
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.darkCard.withOpacity(0.6),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppColors.darkBorderGlass),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppColors.darkBorderGlass),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide:
              const BorderSide(color: AppColors.azureMistDeep, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppColors.darkAccentError),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide:
              const BorderSide(color: AppColors.darkAccentError, width: 1.5),
        ),
      ),
      dividerTheme: const DividerThemeData(
        color: AppColors.dividerDark,
        thickness: 0.5,
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: AppColors.darkSurfaceElevated,
        contentTextStyle: const TextStyle(color: AppColors.darkTextPrimary),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
      pageTransitionsTheme: const PageTransitionsTheme(
        builders: {
          TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
          TargetPlatform.android: FadeUpwardsPageTransitionsBuilder(),
        },
      ),
    );
  }
}
