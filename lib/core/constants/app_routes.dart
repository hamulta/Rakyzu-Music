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

  static const String catalogArtistEdit =
      '/catalog-management/artists/:id/edit';
  static const String catalogArtistEditName = 'catalog-artist-edit';

  static const String catalogAlbums = '/catalog-management/albums';
  static const String catalogAlbumsName = 'catalog-albums';

  static const String catalogAlbumAdd = '/catalog-management/albums/add';
  static const String catalogAlbumAddName = 'catalog-album-add';

  static const String catalogAlbumEdit = '/catalog-management/albums/:id/edit';
  static const String catalogAlbumEditName = 'catalog-album-edit';

  static const String catalogSongs = '/catalog-management/songs';
  static const String catalogSongsName = 'catalog-songs';

  static const String catalogSongAdd = '/catalog-management/songs/add';
  static const String catalogSongAddName = 'catalog-song-add';

  static const String catalogSongEdit = '/catalog-management/songs/:id/edit';
  static const String catalogSongEditName = 'catalog-song-edit';

  static const String catalogBulkUpload = '/catalog-management/bulk-upload';
  static const String catalogBulkUploadName = 'catalog-bulk-upload';

  static const String catalogAlbumDetail = '/catalog-management/albums/:id';
  static const String catalogAlbumDetailName = 'catalog-album-detail';

  static const String admin = '/admin';
  static const String adminName = 'admin';

  static const String devGallery = '/dev/widget-gallery';
  static const String devGalleryName = 'dev-widget-gallery';
}
