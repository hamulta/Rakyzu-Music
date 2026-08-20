import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../dev/gallery/dev_gallery_screen.dart';
import '../../features/auth/presentation/auth_wrapper.dart';
import '../../features/auth/presentation/screens/login_screen.dart';
import '../../features/auth/presentation/screens/signup_screen.dart';
import '../../features/main/presentation/main_shell.dart';
import '../../features/onboarding/presentation/screens/onboarding_screen.dart';
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
    ],
  );
});
