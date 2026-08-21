/// Role aplikasi user (kolom `public.users.role`).
///
/// Catatan: claim `role` pada JWT Supabase selalu `authenticated`;
/// role aplikasi hanya tersedia lewat tabel `users`.
enum AppRole {
  free('free'),
  premium('premium'),
  staff('staff'),
  admin('admin'),
  owner('owner');

  const AppRole(this.value);

  final String value;

  static AppRole? fromString(String? value) {
    for (final role in AppRole.values) {
      if (role.value == value) {
        return role;
      }
    }
    return null;
  }

  /// Staff/admin/owner (dapat mengelola katalog).
  bool get canManageCatalog =>
      this == AppRole.staff || this == AppRole.admin || this == AppRole.owner;

  /// Admin/owner (dapat mengubah status verified & menghapus semua row).
  bool get isAdminOrOwner => this == AppRole.admin || this == AppRole.owner;

  bool get isOwner => this == AppRole.owner;
  bool get isStaff => this == AppRole.staff;
  bool get canViewAnalytics => isAdminOrOwner;
  bool get canViewRevenue => isAdminOrOwner;
  bool get canManageUsers => isAdminOrOwner;
  bool get canManagePricing => isOwner;
  bool get canViewAllCatalog => isAdminOrOwner;
}
