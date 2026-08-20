import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/constants/app_routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_button.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../providers/auth_provider.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _obscurePassword = true;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;

    try {
      await ref.read(authControllerProvider.notifier).signIn(
            email: _emailController.text.trim(),
            password: _passwordController.text,
          );
      if (!mounted) return;
      context.go(AppRoutes.main);
    } on Exception catch (_) {
      if (!mounted) return;
      _showError('Login gagal. Periksa email dan password Anda.');
    }
  }

  Future<void> _handleGoogle() async {
    try {
      await ref.read(authControllerProvider.notifier).signInWithGoogle();
    } on Exception catch (_) {
      if (!mounted) return;
      _showError('Gagal masuk dengan Google. Silakan coba lagi.');
    }
  }

  void _showError(String message) {
    showCupertinoDialog<void>(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Error'),
        content: Text(message),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.pop(context),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authControllerProvider);
    final isLoading = authState.isLoading;

    return Scaffold(
      body: DecoratedBox(
        decoration: const BoxDecoration(
          gradient: AppColors.lightBackgroundGradient,
        ),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Form(
                key: _formKey,
                child: GlassCard(
                  padding: const EdgeInsets.all(28),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Icon(
                        CupertinoIcons.music_note_2,
                        size: 64,
                        color: AppColors.azureMistDeep,
                      ),
                      const SizedBox(height: 16),
                      Text(
                        'Rakyzu Music',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.headlineMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Your Sound, Your Vibe.',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                      const SizedBox(height: 32),
                      CupertinoTextField(
                        controller: _emailController,
                        keyboardType: TextInputType.emailAddress,
                        autocorrect: false,
                        autofillHints: const [AutofillHints.email],
                        placeholder: 'Email',
                        prefix: const Icon(
                          CupertinoIcons.mail,
                          color: AppColors.textSecondary,
                        ),
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: AppColors.ivorySoft,
                          borderRadius: BorderRadius.circular(16),
                          border:
                              Border.all(color: AppColors.borderGlassSubtle),
                        ),
                      ),
                      const SizedBox(height: 14),
                      CupertinoTextField(
                        controller: _passwordController,
                        obscureText: _obscurePassword,
                        placeholder: 'Password',
                        prefix: const Icon(
                          CupertinoIcons.lock_fill,
                          color: AppColors.textSecondary,
                        ),
                        suffix: GestureDetector(
                          onTap: () => setState(
                              () => _obscurePassword = !_obscurePassword,),
                          child: Icon(
                            _obscurePassword
                                ? CupertinoIcons.eye
                                : CupertinoIcons.eye_slash,
                            color: AppColors.textSecondary,
                          ),
                        ),
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: AppColors.ivorySoft,
                          borderRadius: BorderRadius.circular(16),
                          border:
                              Border.all(color: AppColors.borderGlassSubtle),
                        ),
                      ),
                      const SizedBox(height: 24),
                      GlassButton(
                        label: isLoading ? 'Loading...' : 'Masuk',
                        isPrimary: true,
                        onPressed: isLoading ? null : _handleLogin,
                      ),
                      const SizedBox(height: 16),
                      Row(
                        children: [
                          const Expanded(child: Divider()),
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 12),
                            child: Text(
                              'atau',
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                          ),
                          const Expanded(child: Divider()),
                        ],
                      ),
                      const SizedBox(height: 16),
                      GlassButton(
                        label: 'Masuk dengan Google',
                        leading: const _GoogleG(),
                        isPrimary: false,
                        onPressed: isLoading ? null : _handleGoogle,
                      ),
                      const SizedBox(height: 12),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(
                            'Belum punya akun? ',
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                          CupertinoButton(
                            padding: EdgeInsets.zero,
                            onPressed: () => context.go(AppRoutes.signup),
                            child: const Text(
                              'Daftar',
                              style: TextStyle(
                                color: AppColors.azureMistDeep,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Google 'G' brand mark used in the Google sign-in button.
class _GoogleG extends StatelessWidget {
  const _GoogleG();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 20,
      height: 20,
      alignment: Alignment.center,
      decoration: const BoxDecoration(
        shape: BoxShape.circle,
        color: Colors.white,
      ),
      child: const Text(
        'G',
        style: TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.w700,
          color: Color(0xFF4285F4),
        ),
      ),
    );
  }
}
