import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../dev/gallery/dev_gallery_screen.dart';
import '../../features/admin/presentation/screens/admin_dashboard_screen.dart';
import '../../features/auth/presentation/auth_wrapper.dart';
import '../../features/auth/presentation/screens/login_screen.dart';
import '../../features/auth/presentation/screens/signup_screen.dart';
import '../../features/catalog/presentation/screens/album_detail_screen.dart';
import '../../features/catalog/presentation/screens/album_form_screen.dart';
import '../../features/catalog/presentation/screens/album_list_screen.dart';
import '../../features/catalog/presentation/screens/artist_form_screen.dart';
import '../../features/catalog/presentation/screens/artist_list_screen.dart';
import '../../features/catalog/presentation/screens/bulk_upload_screen.dart';
import '../../features/catalog/presentation/screens/catalog_management_home_screen.dart';
import '../../features/catalog/presentation/screens/song_form_screen.dart';
import '../../features/catalog/presentation/screens/song_list_screen.dart';
import '../../features/catalog/presentation/widgets/catalog_access_guard.dart';
import '../../features/main/presentation/main_shell.dart';
import '../../features/onboarding/presentation/screens/onboarding_screen.dart';
import '../../features/player/presentation/screens/full_player_screen.dart';
import '../constants/app_routes.dart';

/// Root app router
final appRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: AppRoutes.splash,
    routes: [
      GoRoute(
        path: AppRoutes.splash,
        name: AppRoutes.splashName,
        builder: (context, state) => const AuthWrapper(),
      ),
      GoRoute(
        path: AppRoutes.login,
        name: AppRoutes.loginName,
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: AppRoutes.signup,
        name: AppRoutes.signupName,
        builder: (context, state) => const SignUpScreen(),
      ),
      GoRoute(
        path: AppRoutes.onboarding,
        name: AppRoutes.onboardingName,
        builder: (context, state) => const OnboardingScreen(),
      ),
      GoRoute(
        path: AppRoutes.devGallery,
        name: AppRoutes.devGalleryName,
        builder: (context, state) => const DevGalleryScreen(),
      ),
      GoRoute(
        path: AppRoutes.main,
        name: AppRoutes.mainName,
        builder: (context, state) => const MainShell(),
      ),
      GoRoute(
        path: AppRoutes.catalogManagement,
        name: AppRoutes.catalogManagementName,
        builder: (context, state) => const CatalogManagementHomeScreen(),
      ),
      GoRoute(
        path: AppRoutes.catalogArtists,
        name: AppRoutes.catalogArtistsName,
        builder: (context, state) => const ArtistListScreen(),
      ),
      GoRoute(
        path: AppRoutes.catalogArtistAdd,
        name: AppRoutes.catalogArtistAddName,
        builder: (context, state) => const ArtistFormScreen(),
      ),
      GoRoute(
        path: AppRoutes.catalogArtistEdit,
        name: AppRoutes.catalogArtistEditName,
        builder: (context, state) {
          final id = state.pathParameters['id'];
          return ArtistFormScreen(artistId: id);
        },
      ),
      GoRoute(
        path: AppRoutes.catalogAlbums,
        name: AppRoutes.catalogAlbumsName,
        builder: (context, state) => const AlbumListScreen(),
      ),
      GoRoute(
        path: AppRoutes.catalogAlbumAdd,
        name: AppRoutes.catalogAlbumAddName,
        builder: (context, state) => const AlbumFormScreen(),
      ),
      GoRoute(
        path: AppRoutes.catalogAlbumEdit,
        name: AppRoutes.catalogAlbumEditName,
        builder: (context, state) {
          final id = state.pathParameters['id'];
          return AlbumFormScreen(albumId: id);
        },
      ),
      GoRoute(
        path: AppRoutes.catalogSongs,
        name: AppRoutes.catalogSongsName,
        builder: (context, state) => const SongListScreen(),
      ),
      GoRoute(
        path: AppRoutes.catalogSongAdd,
        name: AppRoutes.catalogSongAddName,
        builder: (context, state) => const SongFormScreen(),
      ),
      GoRoute(
        path: AppRoutes.catalogSongEdit,
        name: AppRoutes.catalogSongEditName,
        builder: (context, state) {
          final id = state.pathParameters['id'];
          return SongFormScreen(songId: id);
        },
      ),
      GoRoute(
        path: AppRoutes.catalogBulkUpload,
        name: AppRoutes.catalogBulkUploadName,
        builder: (context, state) => const BulkUploadScreen(),
      ),
      GoRoute(
        path: AppRoutes.catalogAlbumDetail,
        name: AppRoutes.catalogAlbumDetailName,
        builder: (context, state) {
          final id = state.pathParameters['id'];
          return AlbumDetailScreen(albumId: id ?? '');
        },
      ),
      GoRoute(
        path: AppRoutes.admin,
        name: AppRoutes.adminName,
        builder: (context, state) => const CatalogAccessGuard(
          child: AdminDashboardScreen(),
        ),
      ),
      GoRoute(
        path: AppRoutes.fullPlayer,
        name: AppRoutes.fullPlayerName,
        pageBuilder: (context, state) => CustomTransitionPage<void>(
          key: state.pageKey,
          child: const FullPlayerScreen(),
          transitionsBuilder: (context, animation, secondaryAnimation, child) {
            return SlideTransition(
              position: Tween<Offset>(
                begin: const Offset(0, 1),
                end: Offset.zero,
              ).animate(
                CurvedAnimation(
                  parent: animation,
                  curve: Curves.easeOutCubic,
                ),
              ),
              child: child,
            );
          },
        ),
      ),
    ],
  );
});
