import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../../core/constants/app_routes.dart';
import '../../../shared/providers/supabase_providers.dart';

/// Authentication state & actions for the app
class AuthController extends StateNotifier<AsyncValue<User?>> {
  AuthController(this._supabase) : super(const AsyncValue.loading()) {
    _init();
  }

  final SupabaseClient _supabase;

  Future<void> _init() async {
    final session = _supabase.auth.currentSession;
    if (session != null) {
      state = AsyncValue.data(session.user);
    } else {
      state = const AsyncValue.data(null);
    }
  }

  Future<void> signUp({
    required String email,
    required String password,
    String? fullName,
  }) async {
    state = const AsyncValue.loading();
    try {
      final response = await _supabase.auth.signUp(
        email: email,
        password: password,
        data: fullName != null ? {'full_name': fullName} : null,
      );
      final user = response.user;
      if (user == null) {
        state = const AsyncValue.data(null);
        throw Exception('Sign up gagal. Silakan coba lagi.');
      }
      state = AsyncValue.data(user);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
      rethrow;
    }
  }

  Future<void> signIn({required String email, required String password}) async {
    state = const AsyncValue.loading();
    try {
      final response = await _supabase.auth.signInWithPassword(
        email: email,
        password: password,
      );
      state = AsyncValue.data(response.user);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
      rethrow;
    }
  }

  Future<void> signInWithGoogle() async {
    state = const AsyncValue.loading();
    try {
      await _supabase.auth.signInWithOAuth(
        OAuthProvider.google,
        redirectTo: _getRedirectUrl(),
      );
      state = AsyncValue.data(_supabase.auth.currentUser);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
      rethrow;
    }
  }

  Future<void> signInWithApple() async {
    state = const AsyncValue.loading();
    try {
      await _supabase.auth.signInWithOAuth(
        OAuthProvider.apple,
        redirectTo: _getRedirectUrl(),
      );
      state = AsyncValue.data(_supabase.auth.currentUser);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
      rethrow;
    }
  }

  Future<void> signOut() async {
    await _supabase.auth.signOut();
    state = const AsyncValue.data(null);
  }

  /// Web needs a redirect URL for OAuth flows
  String? _getRedirectUrl() {
    if (kIsWeb) {
      return AppRoutes.main;
    }
    return null;
  }
}

final authControllerProvider =
    StateNotifierProvider<AuthController, AsyncValue<User?>>((ref) {
  final supabase = ref.watch(supabaseProvider);
  return AuthController(supabase);
});
