// Supabase Edge Function: midtrans-webhook
// Verifikasi signature sesuai docs Midtrans: SHA512(order_id+status_code+gross_amount+serverKey)
// Update subscriptions + users.role atomically.
// Set MIDTRANS_SERVER_KEY sebagai secret.
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

async function sha512Hex(s: string): Promise<string> {
  const data = new TextEncoder().encode(s);
  const buf = await crypto.subtle.digest("SHA-512", data);
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

serve(async (req) => {
  if (req.method !== "POST") return new Response("ok", { status: 200 });
  const serverKey = Deno.env.get("MIDTRANS_SERVER_KEY");
  if (!serverKey) return new Response("server key missing", { status: 500 });
  const body = await req.json().catch(() => null);
  if (!body) return new Response("invalid json", { status: 400 });

  const orderId: string = body.order_id;
  const statusCode: string = body.status_code;
  const grossAmount: string = body.gross_amount;
  const signature: string = body.signature_key;
  const transactionStatus: string = body.transaction_status;
  const fraudStatus: string = body.fraud_status;

  const expected = await sha512Hex(orderId + statusCode + grossAmount + serverKey);
  if (expected !== signature) {
    console.error("Invalid signature", { orderId, expected, signature });
    return new Response("invalid signature", { status: 403 });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const supa = createClient(supabaseUrl, serviceKey);

  // Find subscription by transaction_id (order_id)
  const { data: sub } = await supa.from("subscriptions").select("id, user_id, plan_type").eq("transaction_id", orderId).maybeSingle();
  if (!sub) {
    console.warn("No subscription for order", orderId);
    return new Response(JSON.stringify({ ok: true, note: "no sub" }), { headers: { "Content-Type": "application/json" } });
  }

  let newStatus = "pending";
  let start: string | null = null;
  let end: string | null = null;

  if ((transactionStatus === "capture" && fraudStatus === "accept") || transactionStatus === "settlement") {
    newStatus = "active";
    const now = new Date();
    start = now.toISOString();
    const months = sub.plan_type === "yearly" ? 12 : 1;
    const e = new Date(now);
    e.setMonth(e.getMonth() + months);
    end = e.toISOString();
  } else if (transactionStatus === "pending") {
    newStatus = "pending";
  } else if (["deny", "expire", "cancel", "failure"].includes(transactionStatus)) {
    newStatus = transactionStatus === "expire" ? "expired" : "cancelled";
  }

  await supa.from("subscriptions").update({ status: newStatus, start_date: start, end_date: end }).eq("id", sub.id);

  if (newStatus === "active" && end) {
    await supa.from("users").update({ role: "premium", subscription_status: "active", subscription_expiry: end }).eq("id", sub.user_id);
  } else if (newStatus === "expired" || newStatus === "cancelled") {
    // Jika semua subs tidak active, downgrade (logic sederhana: cek ada active lain)
    const { data: actives } = await supa.from("subscriptions").select("id").eq("user_id", sub.user_id).eq("status", "active").limit(1);
    if (!actives || actives.length === 0) {
      await supa.from("users").update({ role: "free", subscription_status: newStatus }).eq("id", sub.user_id);
    }
  }

  return new Response(JSON.stringify({ ok: true }), { headers: { "Content-Type": "application/json" } });
});
