/// App-wide constants
class AppConstants {
  AppConstants._();

  // App Identity
  static const String appName = 'Rakyzu Music';
  static const String tagline = 'Your Sound, Your Vibe.';
  static const String packageName = 'com.rakyzu.music';
  static const String appVersion = '0.1.0';
  static const int appBuildNumber = 1;

  // Layout & Spacing
  static const double pagePadding = 20;
  static const double cardRadius = 24;
  static const double buttonRadius = 16;
  static const double inputRadius = 16;
  static const double sheetRadius = 28;
  static const double chipRadius = 20;

  // Player
  static const double miniPlayerHeight = 64;
  static const double bottomNavHeight = 56;

  // Limits
  static const int maxGenreSelection = 5;
  static const int minGenreSelection = 3;
  static const int freeSkipLimitPerHour = 6;

  // Storage Keys
  static const String themeModeKey = 'theme_mode';
  static const String onboardingDoneKey = 'onboarding_done';
  static const String selectedGenresKey = 'selected_genres';
  static const String authSessionKey = 'auth_session';
}
