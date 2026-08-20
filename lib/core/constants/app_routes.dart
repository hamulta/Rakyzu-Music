/// Central route path & name definitions
class AppRoutes {
  AppRoutes._();

  static const String splash = '/';
  static const String splashName = 'splash';

  static const String login = '/login';
  static const String loginName = 'login';

  static const String signup = '/signup';
  static const String signupName = 'signup';

  static const String onboarding = '/onboarding';
  static const String onboardingName = 'onboarding';

  static const String main = '/main';
  static const String mainName = 'main';

  static const String catalogManagement = '/catalog-management';
  static const String catalogManagementName = 'catalog-management';

  static const String catalogArtists = '/catalog-management/artists';
  static const String catalogArtistsName = 'catalog-artists';

  static const String catalogArtistAdd = '/catalog-management/artists/add';
  static const String catalogArtistAddName = 'catalog-artist-add';

  static const String catalogArtistEdit = '/catalog-management/artists/:id/edit';
  static const String catalogArtistEditName = 'catalog-artist-edit';

  static const String devGallery = '/dev/widget-gallery';
  static const String devGalleryName = 'dev-widget-gallery';
}
