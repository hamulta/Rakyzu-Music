import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/models/app_role.dart';
import '../../../shared/providers/supabase_providers.dart'
    show supabaseProvider;

/// Role aplikasi user saat ini (dari tabel `users`), reaktif terhadap
/// perubahan session auth. `null` saat belum login.
final currentAppRoleProvider = StreamProvider<AppRole?>((ref) async* {
  final supabase = ref.watch(supabaseProvider);
  final authState = supabase.auth.onAuthStateChange;

  await for (final event in authState) {
    final user = event.session?.user;
    if (user == null) {
      yield null;
      continue;
    }
    AppRole? role;
    try {
      final row = await supabase
          .from('users')
          .select('role')
          .eq('id', user.id)
          .maybeSingle();
      role = AppRole.fromString(row?['role'] as String?);
    } on Exception {
      role = null;
    }
    yield role;
  }
});
