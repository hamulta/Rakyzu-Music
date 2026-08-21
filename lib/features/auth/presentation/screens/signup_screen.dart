import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/constants/app_routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_button.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../providers/auth_provider.dart';

class SignUpScreen extends ConsumerStatefulWidget {
  const SignUpScreen({super.key});

  @override
  ConsumerState<SignUpScreen> createState() => _SignUpScreenState();
}

class _SignUpScreenState extends ConsumerState<SignUpScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  bool _obscurePassword = true;
  bool _obscureConfirm = true;
  bool _agreeTos = false;

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _handleSignUp() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    if (!_agreeTos) {
      _showError('Anda harus menyetujui TOS & Privacy Policy');
      return;
    }

    try {
      await ref.read(authControllerProvider.notifier).signUp(
            email: _emailController.text.trim(),
            password: _passwordController.text,
            fullName: _nameController.text.trim(),
          );
      if (!mounted) return;
      // New user -> onboarding flow
      context.go(AppRoutes.onboarding);
    } on Exception catch (_) {
      if (!mounted) return;
      _showError('Pendaftaran gagal. Email mungkin sudah terdaftar.');
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

  String? _validateEmail(String? value) {
    final email = value?.trim() ?? '';
    if (email.isEmpty) return 'Email wajib diisi';
    final regex = RegExp(r'^[\w\.-]+@[\w-]+(\.[\w-]+)+$');
    if (!regex.hasMatch(email)) return 'Format email tidak valid';
    return null;
  }

  String? _validatePassword(String? value) {
    if (value == null || value.isEmpty) return 'Password wajib diisi';
    if (value.length < 8) return 'Password minimal 8 karakter';
    return null;
  }

  String? _validateConfirm(String? value) {
    if (value != _passwordController.text) return 'Password tidak sama';
    return null;
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
                        size: 56,
                        color: AppColors.azureMistDeep,
                      ),
                      const SizedBox(height: 12),
                      Text(
                        'Buat Akun',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.headlineMedium,
                      ),
                      const SizedBox(height: 28),
                      _buildTextField(
                        controller: _nameController,
                        placeholder: 'Nama Lengkap',
                        icon: CupertinoIcons.person_fill,
                        validator: (v) => (v == null || v.trim().isEmpty)
                            ? 'Nama wajib diisi'
                            : null,
                      ),
                      const SizedBox(height: 12),
                      _buildTextField(
                        controller: _emailController,
                        placeholder: 'Email',
                        icon: CupertinoIcons.mail,
                        keyboardType: TextInputType.emailAddress,
                        validator: _validateEmail,
                      ),
                      const SizedBox(height: 12),
                      _buildTextField(
                        controller: _passwordController,
                        placeholder: 'Password',
                        icon: CupertinoIcons.lock_fill,
                        obscureText: _obscurePassword,
                        onSuffixTap: () => setState(
                          () => _obscurePassword = !_obscurePassword,
                        ),
                        suffixIcon: _obscurePassword
                            ? CupertinoIcons.eye
                            : CupertinoIcons.eye_slash,
                        validator: _validatePassword,
                      ),
                      const SizedBox(height: 12),
                      _buildTextField(
                        controller: _confirmPasswordController,
                        placeholder: 'Konfirmasi Password',
                        icon: CupertinoIcons.lock_fill,
                        obscureText: _obscureConfirm,
                        onSuffixTap: () =>
                            setState(() => _obscureConfirm = !_obscureConfirm),
                        suffixIcon: _obscureConfirm
                            ? CupertinoIcons.eye
                            : CupertinoIcons.eye_slash,
                        validator: _validateConfirm,
                      ),
                      Row(
                        children: [
                          CupertinoSwitch(
                              value: _agreeTos,
                              onChanged: (v) => setState(() => _agreeTos = v)),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Wrap(
                              children: [
                                const Text('Saya setuju dengan ',
                                    style: TextStyle(fontSize: 12)),
                                GestureDetector(
                                    onTap: () => context.push('/terms'),
                                    child: const Text('TOS',
                                        style: TextStyle(
                                            fontSize: 12,
                                            color: AppColors.azureMistDeep,
                                            decoration:
                                                TextDecoration.underline))),
                                const Text(' & ',
                                    style: TextStyle(fontSize: 12)),
                                GestureDetector(
                                    onTap: () => context.push('/privacy'),
                                    child: const Text('Privacy Policy',
                                        style: TextStyle(
                                            fontSize: 12,
                                            color: AppColors.azureMistDeep,
                                            decoration:
                                                TextDecoration.underline))),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      GlassButton(
                        label: isLoading ? 'Loading...' : 'Daftar',
                        isPrimary: true,
                        onPressed: isLoading ? null : _handleSignUp,
                      ),
                      const SizedBox(height: 12),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(
                            'Sudah punya akun? ',
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                          CupertinoButton(
                            padding: EdgeInsets.zero,
                            onPressed: () => context.go(AppRoutes.login),
                            child: const Text(
                              'Masuk',
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

  Widget _buildTextField({
    required TextEditingController controller,
    required String placeholder,
    required IconData icon,
    TextInputType? keyboardType,
    bool obscureText = false,
    IconData? suffixIcon,
    VoidCallback? onSuffixTap,
    String? Function(String?)? validator,
  }) {
    return CupertinoTextField(
      controller: controller,
      keyboardType: keyboardType,
      obscureText: obscureText,
      placeholder: placeholder,
      autocorrect: false,
      prefix: Icon(icon, color: AppColors.textSecondary),
      suffix: suffixIcon != null
          ? GestureDetector(
              onTap: onSuffixTap,
              child: Icon(suffixIcon, color: AppColors.textSecondary),
            )
          : null,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.ivorySoft,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.borderGlassSubtle),
      ),
      // Basic validation feedback via error message below
      onChanged: (_) => _formKey.currentState?.validate(),
    );
  }
}
