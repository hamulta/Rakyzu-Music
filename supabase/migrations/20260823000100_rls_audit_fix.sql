-- 0.9.0 RLS hardening
-- Fix subscriptions: staff should NOT see all revenue
DROP POLICY IF EXISTS "subscriptions_select_staff" ON public.subscriptions;
CREATE POLICY "subscriptions_select_staff" ON public.subscriptions FOR SELECT TO authenticated USING (
  exists (select 1 from public.users u where u.id = auth.uid() and u.role in ('admin','owner'))
);

-- Enforce is_published only admin/owner can toggle (staff can still edit own songs but not publish flag)
CREATE OR REPLACE FUNCTION public.prevent_song_publish_escalation() RETURNS trigger AS $$
DECLARE
  requester_role public.user_role;
BEGIN
  SELECT role INTO requester_role FROM public.users WHERE id = auth.uid();
  IF (OLD.is_published IS DISTINCT FROM NEW.is_published) AND requester_role NOT IN ('admin','owner') THEN
    RAISE EXCEPTION 'Only admin/owner can change is_published';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
DROP TRIGGER IF EXISTS trg_song_publish ON public.songs;
CREATE TRIGGER trg_song_publish BEFORE UPDATE OF is_published ON public.songs FOR EACH ROW EXECUTE FUNCTION public.prevent_song_publish_escalation();

-- Ensure pricing_plans public read already, owner write already — no change.
