import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rakyzu_music/core/theme/app_theme.dart';
import 'package:rakyzu_music/features/auth/presentation/screens/login_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

void main() {
  setUpAll(() async {
    SharedPreferences.setMockInitialValues({});
    await Supabase.initialize(
      url: 'https://dummy-project.supabase.co',
      anonKey: 'dummy-anon-key',
    );
  });

  testWidgets('LoginScreen renders core elements', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: AppTheme.lightTheme,
          home: const LoginScreen(),
        ),
      ),
    );

    expect(find.text('Rakyzu Music'), findsOneWidget);
    expect(find.text('Your Sound, Your Vibe.'), findsOneWidget);
    expect(find.text('Masuk'), findsOneWidget);
    expect(find.text('Masuk dengan Google'), findsOneWidget);
    expect(find.text('Daftar'), findsOneWidget);
  });

  testWidgets('AppTheme provides light and dark themes', (tester) async {
    final light = AppTheme.lightTheme;
    final dark = AppTheme.darkTheme;

    expect(light.brightness, Brightness.light);
    expect(dark.brightness, Brightness.dark);
    expect(light.scaffoldBackgroundColor, isNotNull);
    expect(dark.scaffoldBackgroundColor, isNotNull);
  });
}