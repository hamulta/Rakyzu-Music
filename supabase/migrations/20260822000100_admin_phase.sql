-- 0.8.x Admin Dashboard — core schema hardening
-- is_banned, is_published, pricing_plans, ad_impressions + RLS privilege escalation fix

-- users.is_banned
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS is_banned boolean NOT NULL DEFAULT false;

-- songs.is_published (default true agar tidak breaking existing behavior)
ALTER TABLE public.songs ADD COLUMN IF NOT EXISTS is_published boolean NOT NULL DEFAULT true;
CREATE INDEX IF NOT EXISTS idx_songs_published ON public.songs(is_published);

-- pricing_plans (owner-only write, public read)
CREATE TABLE IF NOT EXISTS public.pricing_plans (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL UNIQUE, -- 'monthly' | 'yearly'
  price_idr integer NOT NULL,
  interval text NOT NULL CHECK (interval IN ('month','year')),
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.pricing_plans ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "pricing_select_public" ON public.pricing_plans;
CREATE POLICY "pricing_select_public" ON public.pricing_plans FOR SELECT TO anon, authenticated USING (true);
DROP POLICY IF EXISTS "pricing_modify_owner" ON public.pricing_plans;
CREATE POLICY "pricing_modify_owner" ON public.pricing_plans FOR ALL TO authenticated USING (
  exists (select 1 from public.users u where u.id = auth.uid() and u.role = 'owner')
) WITH CHECK (
  exists (select 1 from public.users u where u.id = auth.uid() and u.role = 'owner')
);
INSERT INTO public.pricing_plans (name, price_idr, interval) VALUES ('monthly', 49000, 'month'), ('yearly', 449000, 'year') ON CONFLICT (name) DO NOTHING;

-- ad_impressions (for 0.6.7 logger + 0.8.7 analytics)
CREATE TABLE IF NOT EXISTS public.ad_impressions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES public.users(id) ON DELETE SET NULL,
  ad_type text NOT NULL CHECK (ad_type IN ('banner','interstitial')),
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.ad_impressions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "ad_imp_insert_auth" ON public.ad_impressions;
CREATE POLICY "ad_imp_insert_auth" ON public.ad_impressions FOR INSERT TO authenticated WITH CHECK (true);
DROP POLICY IF EXISTS "ad_imp_select_staff" ON public.ad_impressions;
CREATE POLICY "ad_imp_select_staff" ON public.ad_impressions FOR SELECT TO authenticated USING (
  exists (select 1 from public.users u where u.id = auth.uid() and u.role IN ('admin','owner'))
);
CREATE INDEX IF NOT EXISTS idx_ad_imp_created ON public.ad_impressions(created_at desc);

-- RLS fix: privilege escalation on users.role / is_banned
-- Trigger yang cegah free/premium/staff mengubah role/is_banned milik sendiri atau orang lain ke admin/owner
CREATE OR REPLACE FUNCTION public.prevent_role_escalation() RETURNS trigger AS $$
DECLARE
  requester_role public.user_role;
BEGIN
  SELECT role INTO requester_role FROM public.users WHERE id = auth.uid();
  -- Jika updater bukan owner, larang perubahan ke admin/owner atau mengubah is_banned
  IF requester_role IS DISTINCT FROM 'owner' THEN
    -- Larang set role ke admin/owner
    IF NEW.role IN ('admin','owner') AND OLD.role NOT IN ('admin','owner') THEN
      RAISE EXCEPTION 'Only owner can promote to admin/owner';
    END IF;
    IF NEW.role IS DISTINCT FROM OLD.role AND OLD.role IN ('admin','owner') THEN
      RAISE EXCEPTION 'Only owner can change admin/owner roles';
    END IF;
    IF NEW.is_banned IS DISTINCT FROM OLD.is_banned AND requester_role NOT IN ('admin','owner') THEN
      RAISE EXCEPTION 'Only admin/owner can ban';
    END IF;
    -- Non-owner tidak boleh mengubah role orang lain sama sekali (staff/admin limited)
    IF auth.uid() <> OLD.id AND NEW.role IS DISTINCT FROM OLD.role AND requester_role <> 'owner' THEN
      -- Admin boleh ubah free<->premium<->staff saja
      IF NEW.role NOT IN ('free','premium','staff') OR OLD.role NOT IN ('free','premium','staff') THEN
        RAISE EXCEPTION 'Admin can only manage free/premium/staff';
      END IF;
    END IF;
    -- Self-promotion block
    IF auth.uid() = OLD.id AND NEW.role IS DISTINCT FROM OLD.role THEN
      RAISE EXCEPTION 'Cannot change own role';
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_prevent_role_escalation ON public.users;
CREATE TRIGGER trg_prevent_role_escalation BEFORE UPDATE OF role, is_banned ON public.users FOR EACH ROW EXECUTE FUNCTION public.prevent_role_escalation();
