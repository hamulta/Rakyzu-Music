import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../dev/gallery/dev_gallery_screen.dart';
import '../../features/admin/presentation/layout/admin_shell.dart';
import '../../features/admin/presentation/pages/ad_analytics_page.dart';
import '../../features/admin/presentation/pages/analytics_page.dart';
import '../../features/admin/presentation/pages/catalog_admin_page.dart';
import '../../features/admin/presentation/pages/pricing_page.dart';
import '../../features/admin/presentation/pages/revenue_page.dart';
import '../../features/admin/presentation/pages/users_page.dart';
import '../../features/admin/presentation/widgets/admin_role_guard.dart';
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
import '../../features/discovery/presentation/pages/album_detail_page.dart';
import '../../features/discovery/presentation/pages/artist_detail_page.dart';
import '../../features/discovery/presentation/pages/genre_browse_page.dart';
import '../../features/discovery/presentation/pages/genre_detail_page.dart';
import '../../features/library/presentation/pages/following_page.dart';
import '../../features/library/presentation/pages/liked_songs_page.dart';
import '../../features/library/presentation/pages/playlist_detail_page.dart';
import '../../features/library/presentation/pages/playlist_form_page.dart';
import '../../features/library/presentation/pages/recently_played_page.dart';
import '../../features/main/presentation/main_shell.dart';
import '../../features/onboarding/presentation/screens/onboarding_screen.dart';
import '../../features/player/presentation/screens/full_player_screen.dart';
import '../../features/premium/presentation/pages/premium_upgrade_placeholder_page.dart';
import '../../features/subscription/presentation/pages/checkout_page.dart';
import '../../features/subscription/presentation/pages/transaction_history_page.dart';
import '../../features/legal/presentation/pages/privacy_policy_page.dart';
import '../../features/legal/presentation/pages/terms_of_service_page.dart';
import '../constants/app_routes.dart';
import '../models/app_role.dart';

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
      ShellRoute(
        builder: (context, state, child) => AdminShell(child: child),
        routes: [
          GoRoute(
            path: AppRoutes.admin,
            name: AppRoutes.adminName,
            redirect: (_, __) => '/admin/analytics',
          ),
          GoRoute(
            path: '/admin/analytics',
            builder: (context, state) => const AdminRoleGuard(
                allowed: [AppRole.admin, AppRole.owner],
                child: AnalyticsPage()),
          ),
          GoRoute(
            path: '/admin/revenue',
            builder: (context, state) => const AdminRoleGuard(
                allowed: [AppRole.admin, AppRole.owner], child: RevenuePage()),
          ),
          GoRoute(
            path: '/admin/users',
            builder: (context, state) => const AdminRoleGuard(
                allowed: [AppRole.admin, AppRole.owner], child: UsersPage()),
          ),
          GoRoute(
            path: '/admin/catalog',
            builder: (context, state) =>
                const CatalogAccessGuard(child: CatalogAdminPage()),
          ),
          GoRoute(
            path: '/admin/pricing',
            builder: (context, state) => const AdminRoleGuard(
                allowed: [AppRole.owner], child: PricingPage()),
          ),
          GoRoute(
            path: '/admin/ads',
            builder: (context, state) => const AdminRoleGuard(
                allowed: [AppRole.admin, AppRole.owner],
                child: AdAnalyticsPage()),
          ),
        ],
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
      GoRoute(
        path: '/genres',
        name: 'genres',
        builder: (context, state) => const GenreBrowsePage(),
      ),
      GoRoute(
        path: '/genres/:name',
        name: 'genre-detail',
        builder: (context, state) {
          final name = Uri.decodeComponent(state.pathParameters['name'] ?? '');
          return GenreDetailPage(genreName: name);
        },
      ),
      GoRoute(
        path: '/artist/:id',
        name: 'artist-detail',
        builder: (context, state) {
          final id = state.pathParameters['id'] ?? '';
          return ArtistDetailPage(artistId: id);
        },
      ),
      GoRoute(
        path: '/album/:id',
        name: 'album-detail',
        builder: (context, state) {
          final id = state.pathParameters['id'] ?? '';
          return AlbumDetailPage(albumId: id);
        },
      ),
      GoRoute(
        path: '/library/playlist/create',
        name: 'playlist-create',
        builder: (context, state) => const PlaylistFormPage(),
      ),
      GoRoute(
        path: '/library/playlist/:id/edit',
        name: 'playlist-edit',
        builder: (context, state) {
          final id = state.pathParameters['id'] ?? '';
          return PlaylistFormPage(playlistId: id);
        },
      ),
      GoRoute(
        path: '/library/playlist/:id',
        name: 'playlist-detail',
        builder: (context, state) {
          final id = state.pathParameters['id'] ?? '';
          return PlaylistDetailPage(playlistId: id);
        },
      ),
      GoRoute(
        path: '/library/liked',
        name: 'liked-songs',
        builder: (context, state) => const LikedSongsPage(),
      ),
      GoRoute(
        path: '/library/following',
        name: 'following',
        builder: (context, state) => const FollowingPage(),
      ),
      GoRoute(
        path: '/library/history',
        name: 'recent-history',
        builder: (context, state) => const RecentlyPlayedPage(),
      ),
      GoRoute(
        path: '/premium/upgrade',
        name: 'premium-upgrade',
        builder: (context, state) => const PremiumUpgradePlaceholderPage(),
      ),
      GoRoute(
        path: '/premium/checkout',
        name: 'premium-checkout',
        builder: (context, state) {
          final extra = state.extra as Map<String, dynamic>? ?? {};
          return CheckoutPage(
              snapToken: extra['snap_token'] as String?,
              redirectUrl: extra['redirect_url'] as String?,
              plan: extra['plan'] as String? ?? 'monthly');
        },
      ),
      GoRoute(
        path: '/checkout/result',
        name: 'checkout-result',
        builder: (context, state) => CheckoutResultPage(
            status: state.uri.queryParameters['status'] ?? 'pending'),
      ),
      GoRoute(
          path: '/profile/transactions',
          name: 'transactions',
          builder: (context, state) => const TransactionHistoryPage()),
      GoRoute(
          path: '/privacy',
          name: 'privacy',
          builder: (context, state) => const PrivacyPolicyPage()),
      GoRoute(
          path: '/terms',
          name: 'terms',
          builder: (context, state) => const TermsOfServicePage()),
    ],
  );
});
