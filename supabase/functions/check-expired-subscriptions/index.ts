// Cron: check expired subscriptions -> downgrade free
// Deploy as cron: supabase functions deploy check-expired-subscriptions --no-verify-jwt
// Then schedule via pg_cron or external cron hitting this endpoint with service key.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

Deno.serve(async (req) => {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const supa = createClient(supabaseUrl, serviceKey);
  const now = new Date().toISOString();
  // Find active subs where end_date < now
  const { data: expired } = await supa.from("subscriptions").select("id, user_id").eq("status", "active").lt("end_date", now);
  if (!expired || expired.length === 0) return new Response(JSON.stringify({ ok: true, expired: 0 }), { headers: { "Content-Type": "application/json" } });

  for (const s of expired) {
    await supa.from("subscriptions").update({ status: "expired" }).eq("id", s.id);
    // Downgrade if no other actives
    const { data: actives } = await supa.from("subscriptions").select("id").eq("user_id", s.user_id).eq("status", "active").limit(1);
    if (!actives || actives.length === 0) {
      await supa.from("users").update({ role: "free", subscription_status: "expired" }).eq("id", s.user_id);
    }
  }
  return new Response(JSON.stringify({ ok: true, expired: expired.length }), { headers: { "Content-Type": "application/json" } });
});
