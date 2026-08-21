import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/widgets/glass_app_bar.dart';
import '../../core/widgets/glass_bottom_nav_bar.dart';
import '../../core/widgets/glass_button.dart';
import '../../core/widgets/glass_card.dart';
import '../../core/widgets/glass_search_bar.dart';
import '../../core/widgets/signed_audio_player.dart';

/// Internal development gallery to preview all Glassmorphism widgets.
/// This screen can be removed before the v1.0.0 release.
class DevGalleryScreen extends StatefulWidget {
  const DevGalleryScreen({super.key});

  @override
  State<DevGalleryScreen> createState() => _DevGalleryScreenState();
}

class _DevGalleryScreenState extends State<DevGalleryScreen> {
  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: const GlassAppBar(
        title: Text('Widget Gallery'),
        centerTitle: true,
      ),
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: isDark
              ? AppColors.darkBackgroundGradient
              : AppColors.lightBackgroundGradient,
        ),
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            const _SectionLabel('GlassCard'),
            const GlassCard(
              padding: EdgeInsets.all(20),
              child: Text(
                'Frosted glass card dengan backdrop blur, border tipis, '
                'dan shadow lembut. Radius 24px.',
              ),
            ),
            const SizedBox(height: 24),
            const _SectionLabel('GlassButton'),
            GlassButton(
              label: 'Primary Button',
              isPrimary: true,
              onPressed: () {},
            ),
            const SizedBox(height: 12),
            GlassButton(
              label: 'Secondary Button',
              isPrimary: false,
              onPressed: () {},
            ),
            const SizedBox(height: 12),
            GlassButton(
              label: 'Button with Icon',
              icon: CupertinoIcons.play_fill,
              isPrimary: false,
              onPressed: () {},
            ),
            const SizedBox(height: 12),
            const GlassButton(
              label: 'Loading Button',
              loading: true,
              isPrimary: true,
            ),
            const SizedBox(height: 12),
            GlassButton(
              label: 'Destructive Button',
              isDestructive: true,
              onPressed: () {},
            ),
            const SizedBox(height: 24),
            const _SectionLabel('GlassSearchBar'),
            const GlassSearchBar(),
            const SizedBox(height: 24),
            const _SectionLabel('SignedAudioPlayer'),
            const SignedAudioPlayer(audioKey: null, title: 'Demo'),
            const SizedBox(height: 24),
            const _SectionLabel('GlassBottomNavBar'),
            const _NavPreview(),
          ],
        ),
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Text(label, style: Theme.of(context).textTheme.titleLarge),
    );
  }
}

class _NavPreview extends StatelessWidget {
  const _NavPreview();

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      height: 70,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          // GlassBottomNavBar rendered inline for preview
          _InlineNav(),
        ],
      ),
    );
  }
}

class _InlineNav extends StatefulWidget {
  const _InlineNav();

  @override
  State<_InlineNav> createState() => _InlineNavState();
}

class _InlineNavState extends State<_InlineNav> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    return GlassBottomNavBar(
      currentIndex: _index,
      onTap: (i) => setState(() => _index = i),
      items: const [
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
      ],
    );
  }
}
