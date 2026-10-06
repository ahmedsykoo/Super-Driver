// Fired by a Supabase Database Webhook on INSERT into vodafone_cash_payments.
// Emails Ahmed the driver's details + transaction ref so he can check the transfer
// and manually flip the subscription to 'active' in the Supabase table editor.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const NOTIFY_EMAIL = "ahmed.sykooo@gmail.com"

Deno.serve(async (req) => {
  const payload = await req.json()
  const record = payload.record

  const supabaseAdmin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  )

  const { data: userData } = await supabaseAdmin.auth.admin.getUserById(record.user_id)
  const driverEmail = userData?.user?.email ?? "غير معروف"

  const { data: profile } = await supabaseAdmin
    .from("profiles")
    .select("name, city")
    .eq("id", record.user_id)
    .maybeSingle()

  const planLabel = record.plan === "yearly" ? "سنوي (٥٧٥ ج.م)" : "شهري (٥٠ ج.م)"

  const resendResponse = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${Deno.env.get("RESEND_API_KEY")}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: "Super Driver <onboarding@resend.dev>",
      to: [NOTIFY_EMAIL],
      subject: "طلب اشتراك جديد - فودافون كاش",
      html: `
        <div dir="rtl" style="font-family: sans-serif;">
          <h2>طلب دفع جديد</h2>
          <p><b>السائق:</b> ${profile?.name ?? "—"} (${driverEmail})</p>
          <p><b>المدينة:</b> ${profile?.city ?? "—"}</p>
          <p><b>الباقة:</b> ${planLabel}</p>
          <p><b>رقم الموبايل المحوّل منه:</b> ${record.phone}</p>
          <p><b>رقم العملية:</b> ${record.transaction_ref}</p>
          <p>راجع الدفع وفعّل الاشتراك من جدول subscriptions في Supabase.</p>
        </div>
      `,
    }),
  })

  if (!resendResponse.ok) {
    console.error(await resendResponse.text())
    return new Response("email failed", { status: 500 })
  }

  return new Response("ok", { status: 200 })
})
