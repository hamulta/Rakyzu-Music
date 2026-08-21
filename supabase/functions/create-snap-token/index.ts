// Supabase Edge Function: create-snap-token (Midtrans Snap Sandbox)
// Server key HANYA di secret MIDTRANS_SERVER_KEY — jangan expose ke client.
// Test dengan: curl -X POST -H "Authorization: Bearer <anon>" -d '{"plan_type":"monthly"}' https://<project>.supabase.co/functions/v1/create-snap-token
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const MIDTRANS_SANDBOX_URL = "https://app.sandbox.midtrans.com/snap/v1/transactions";
const PRICE: Record<string, number> = { monthly: 49000, yearly: 449000 };

serve(async (req) => {
  if (req.method !== "POST") return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405 });
  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const serverKey = Deno.env.get("MIDTRANS_SERVER_KEY");
  if (!serverKey) return new Response(JSON.stringify({ error: "MIDTRANS_SERVER_KEY not set (use Sandbox key)" }), { status: 500 });

  const auth = req.headers.get("Authorization") ?? "";
  const supabaseUser = createClient(supabaseUrl, anonKey, { global: { headers: { Authorization: auth } } });
  const { data: { user } } = await supabaseUser.auth.getUser();
  if (!user) return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401 });

  const body = await req.json().catch(() => ({}));
  const plan = (body.plan_type as string) ?? "monthly";
  if (!PRICE[plan]) return new Response(JSON.stringify({ error: "Invalid plan_type" }), { status: 400 });
  const gross = PRICE[plan];
  const orderId = `rakyzu-${user.id.slice(0, 8)}-${Date.now()}`;

  // Insert pending subscription row
  const supaAdmin = createClient(supabaseUrl, serviceKey);
  const { data: sub, error: insErr } = await supaAdmin.from("subscriptions").insert({
    user_id: user.id,
    plan_type: plan,
    status: "pending",
    payment_provider: "midtrans",
    transaction_id: orderId,
  }).select().single();
  if (insErr) return new Response(JSON.stringify({ error: insErr.message }), { status: 500 });

  // Call Midtrans Snap
  const midtransResp = await fetch(MIDTRANS_SANDBOX_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Accept": "application/json",
      "Authorization": "Basic " + btoa(serverKey + ":"),
    },
    body: JSON.stringify({
      transaction_details: { order_id: orderId, gross_amount: gross },
      customer_details: { email: user.email, first_name: user.email?.split("@")[0] ?? "Rakyzu User" },
      item_details: [{ id: plan, price: gross, quantity: 1, name: `Rakyzu Premium ${plan}` }],
      callbacks: { finish: "https://rakyzu-music.pages.dev/payment/finish" },
    }),
  });
  const midJson = await midtransResp.json().catch(() => ({}));
  if (!midtransResp.ok) {
    return new Response(JSON.stringify({ error: "Midtrans error", midtrans: midJson }), { status: 502 });
  }
  // midJson.token is snap_token, redirect_url
  await supaAdmin.from("subscriptions").update({ transaction_id: orderId }).eq("id", sub.id);
  return new Response(JSON.stringify({ snap_token: midJson.token, redirect_url: midJson.redirect_url, order_id: orderId, subscription_id: sub.id }), { headers: { "Content-Type": "application/json" } });
});
