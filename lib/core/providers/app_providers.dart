import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Global provider for theme mode (light/dark/system)
final themeModeProvider = StateProvider<ThemeMode>((ref) => ThemeMode.system);
