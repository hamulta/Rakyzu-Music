import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../../catalog/providers/catalog_providers.dart';

class FollowingPage extends ConsumerWidget {
  const FollowingPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final followedAsync = ref.watch(followedArtistsProvider);
    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
            gradient: isDark
                ? AppColors.darkBackgroundGradient
                : AppColors.lightBackgroundGradient,),
        child: SafeArea(
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
                child: Row(
                  children: [
                    IconButton(
                        onPressed: () => context.pop(),
                        icon: const Icon(CupertinoIcons.back),),
                    Text('Following',
                        style: Theme.of(context)
                            .textTheme
                            .titleLarge
                            ?.copyWith(fontWeight: FontWeight.w700),),
                  ],
                ),
              ),
              Expanded(
                child: followedAsync.when(
                  data: (list) {
                    if (list.isEmpty) {
                      return const Center(
                          child: Text('Not following anyone yet'),);
                    }
                    return ListView.separated(
                      padding: const EdgeInsets.all(16),
                      itemCount: list.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 8),
                      itemBuilder: (context, i) {
                        final a = list[i];
                        return GlassCard(
                          padding: const EdgeInsets.all(12),
                          borderRadius: 16,
                          onTap: () => context.push('/artist/${a.id}'),
                          child: Row(
                            children: [
                              ClipOval(
                                  child: SignedImage(
                                      value: a.imageUrl,
                                      width: 48,
                                      height: 48,
                                      fallbackIcon:
                                          CupertinoIcons.person_fill,),),
                              const SizedBox(width: 12),
                              Expanded(
                                  child: Text(a.name,
                                      style: const TextStyle(
                                          fontWeight: FontWeight.w600,),),),
                              const Icon(CupertinoIcons.chevron_right,
                                  size: 16, color: AppColors.textSecondary,),
                            ],
                          ),
                        );
                      },
                    );
                  },
                  loading: () =>
                      const Center(child: CupertinoActivityIndicator()),
                  error: (e, _) => Center(child: Text('Error: $e')),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
