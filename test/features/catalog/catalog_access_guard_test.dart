import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rakyzu_music/core/models/app_role.dart';
import 'package:rakyzu_music/features/catalog/presentation/widgets/catalog_access_guard.dart';
import 'package:rakyzu_music/features/catalog/providers/role_provider.dart';

void main() {
  Widget buildGuard(AppRole? role, Widget child) {
    return ProviderScope(
      overrides: [
        currentAppRoleProvider.overrideWith((ref) async* {
          yield role;
        }),
      ],
      child: MaterialApp(
        home: CatalogAccessGuard(child: child),
      ),
    );
  }

  testWidgets('menampilkan child untuk staff/admin/owner', (tester) async {
    await tester.pumpWidget(
      buildGuard(AppRole.staff, const Text('KONTEN-RAHASIA')),
    );
    await tester.pumpAndSettle();

    expect(find.text('KONTEN-RAHASIA'), findsOneWidget);
    expect(find.text('Akses Terbatas'), findsNothing);
  });

  testWidgets('menampilkan akses terbatas untuk free/premium', (tester) async {
    await tester.pumpWidget(
      buildGuard(AppRole.premium, const Text('KONTEN-RAHASIA')),
    );
    await tester.pumpAndSettle();

    expect(find.text('Akses Terbatas'), findsOneWidget);
    expect(find.text('KONTEN-RAHASIA'), findsNothing);
  });

  testWidgets('menampilkan loading saat role belum tersedia', (tester) async {
    await tester.pumpWidget(
      buildGuard(null, const Text('KONTEN-RAHASIA')),
    );
    await tester.pump();

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.text('KONTEN-RAHASIA'), findsNothing);
  });
}
