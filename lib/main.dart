// GENERATED CODE - DO NOT MODIFY BY HAND

import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'config/env_config.dart';
import 'core/providers/app_providers.dart';
import 'core/router/app_router.dart';
import 'core/theme/app_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await dotenv.load(fileName: '.env');

  await Supabase.initialize(
    url: EnvConfig.supabaseUrl,
    publishableKey: EnvConfig.supabaseAnonKey,
  );

  runApp(
    const ProviderScope(
      child: RakyzuMusicApp(),
    ),
  );
}

class RakyzuMusicApp extends ConsumerWidget {
  const RakyzuMusicApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(appRouterProvider);
    final themeMode = ref.watch(themeModeProvider);

    return MaterialApp.router(
      title: 'Rakyzu Music',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: themeMode,
      routerConfig: router,
      localizationsDelegates: const [
        // TODO(rakyzu): Add localization delegates.
      ],
      supportedLocales: const [
        Locale('en', 'US'),
        Locale('id', 'ID'),
      ],
    );
  }
}
