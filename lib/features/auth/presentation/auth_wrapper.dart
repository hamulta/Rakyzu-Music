import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/constants/app_routes.dart';
import '../../../core/theme/app_colors.dart';
import '../../onboarding/providers/onboarding_provider.dart';
import '../providers/auth_provider.dart';

/// Decides where to route the user based on auth + onboarding state.
/// Acts as the splash / session gate.
class AuthWrapper extends ConsumerStatefulWidget {
  const AuthWrapper({super.key});

  @override
  ConsumerState<AuthWrapper> createState() => _AuthWrapperState();
}

class _AuthWrapperState extends ConsumerState<AuthWrapper> {
  @override
  void initState() {
    super.initState();
    _decideRoute();
  }

  Future<void> _decideRoute() async {
    // Let the auth controller resolve its initial state first.
    await Future<void>.delayed(const Duration(milliseconds: 300));

    final authState = ref.read(authControllerProvider);
    final user = authState.value;

    if (!mounted) return;

    if (user == null) {
      context.go(AppRoutes.login);
      return;
    }

    final onboarding = ref.read(onboardingProvider);
    if (onboarding.isCompleted) {
      context.go(AppRoutes.main);
    } else {
      context.go(AppRoutes.onboarding);
    }
  }

  @override
  Widget build(BuildContext context) {
    return const CupertinoPageScaffold(
      backgroundColor: AppColors.azureMistBase,
      child: Center(
        child: CupertinoActivityIndicator(radius: 16),
      ),
    );
  }
}
