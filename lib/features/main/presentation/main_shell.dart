import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/constants/app_routes.dart';
import '../../../core/widgets/ad_banner.dart';
import '../../../core/widgets/glass_bottom_nav_bar.dart';
import '../../../core/widgets/glass_mini_player.dart';
import '../../../core/ads/interstitial_manager.dart';
import '../../player/providers/audio_handler_provider.dart';
import 'tabs/home_tab.dart';
import 'tabs/library_tab.dart';
import 'tabs/premium_tab.dart';
import 'tabs/profile_tab.dart';
import 'tabs/search_tab.dart';

/// Main app shell with CupertinoTabScaffold + Glassmorphism bottom nav.
/// 5 tabs: Home, Search, Library, Premium, Profile.
class MainShell extends ConsumerStatefulWidget {
  const MainShell({super.key});

  @override
  ConsumerState<MainShell> createState() => _MainShellState();
}

class _MainShellState extends ConsumerState<MainShell> {
  int _currentIndex = 0;

  static const List<GlassNavItem> _navItems = [
    GlassNavItem(
      icon: CupertinoIcons.house,
      activeIcon: CupertinoIcons.house_fill,
      label: 'Home',
    ),
    GlassNavItem(
      icon: CupertinoIcons.search,
      activeIcon: CupertinoIcons.search,
      label: 'Search',
    ),
    GlassNavItem(
      icon: CupertinoIcons.music_note_list,
      activeIcon: CupertinoIcons.music_note_list,
      label: 'Library',
    ),
    GlassNavItem(
      icon: CupertinoIcons.star,
      activeIcon: CupertinoIcons.star_fill,
      label: 'Premium',
    ),
    GlassNavItem(
      icon: CupertinoIcons.person,
      activeIcon: CupertinoIcons.person_fill,
      label: 'Profile',
    ),
  ];

  @override
  Widget build(BuildContext context) {
    // Trigger lazy initialization of audio handler untuk background playback.
    ref.watch(audioHandlerProvider);
    // 0.6.1: observer interstitial tiap N songs (free only, tidak ganggu queue).
    ref.watch(interstitialManagerProvider);

    return CupertinoTabScaffold(
      tabBar: _buildTabBar(),
      tabBuilder: (context, index) {
        return CupertinoTabView(
          builder: (context) => _buildTabContent(index),
        );
      },
    );
  }

  CupertinoTabBar _buildTabBar() {
    return GlassBottomNavBar(
      currentIndex: _currentIndex,
      onTap: (i) => setState(() => _currentIndex = i),
      items: _navItems,
    );
  }

  Widget _buildTabContent(int index) {
    final tabContent = switch (index) {
      0 => const HomeTab(),
      1 => const SearchTab(),
      2 => const LibraryTab(),
      3 => const PremiumTab(),
      4 => const ProfileTab(),
      _ => const HomeTab(),
    };

    return Column(
      children: [
        Expanded(child: tabContent),
        const AdBanner(),
        GlassMiniPlayer(
          onTapFullPlayer: () => context.push(AppRoutes.fullPlayer),
        ),
      ],
    );
  }
}
