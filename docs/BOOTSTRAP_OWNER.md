# Bootstrap Owner — Rakyzu Music

Dokumen ini menjelaskan **satu-satunya cara resmi** membuat akun Owner pertama.

## Kenapa tidak bisa self-promote dari app?

Trigger `prevent_role_escalation` di DB (`20260822000100_admin_phase.sql`) memblokir:
- `auth.uid() = id` + `NEW.role != OLD.role` → `Cannot change own role`
- `free/premium/staff` → `admin/owner` hanya boleh `owner`

Jadi tidak ada endpoint di app yang bisa mengubah `role` sendiri menjadi `owner`. Harus lewat Supabase Dashboard (service_role).

## Langkah untuk PM/Owner

1. **Buat user normal via app** (Signup) atau **Supabase Auth Dashboard** → Authentication → Users → Add user (email+password, auto-confirm).
2. **Buka Supabase Dashboard** → Table Editor → `public.users` → cari row dengan `id = auth.uid()` (atau `email`).
3. **Edit kolom `role`** → ubah dari `free` → `owner`, simpan.
4. **Verifikasi**: login kembali di app → `currentAppRoleProvider` akan yield `owner`, `AdsGate` bypass, sidebar `/admin` tampil lengkap termasuk `/admin/pricing`.
5. **Jangan commit .env** — `SUPABASE_SERVICE_ROLE_KEY` hanya dipakai di Edge Functions, bukan di client.

## Alternatif via SQL (psql / SQL Editor)

```sql
update public.users set role = 'owner' where email = 'owner@rakyzu.com';
```

Hanya `service_role` atau `owner` existing yang bisa eksekusi ini. Setelah itu buat admin lain via UI `/admin/users` (Owner dapat promote ke admin, Admin tidak bisa promote ke owner).

## Catatan

- Admin dapat manage `free/premium/staff` tapi **tidak** bisa promote ke `admin/owner` (dicegah trigger).
- Untuk banned, gunakan kolom `is_banned` via `/admin/users`.
