package com.superdriver.app.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import com.superdriver.app.data.SubPlan
import com.superdriver.app.data.SubResult
import com.superdriver.app.data.SubStatus
import com.superdriver.app.data.Subscription
import com.superdriver.engine.ArabicFormat
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Status comes from Supabase (`refresh()`), never computed locally — matches the handoff rule that the
 * server is the only source of truth for subscription state. Only Vodafone Cash is wired up for real;
 * Paymob and Google Play Billing need accounts Ahmed hasn't set up yet (see data/Subscription.kt).
 */
class SubscriptionActivity : BaseActivity() {
    private var paymentPlan: SubPlan? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            render(appGraph.subscriptionRepo.current(), loading = true)
            appGraph.subscriptionRepo.refresh()
            render(appGraph.subscriptionRepo.current())
        }
    }

    private fun render(sub: Subscription, loading: Boolean = false, note: String? = null) {
        val page = setPageWithNav(
            NavTab.SUBSCRIPTION,
            brandHeader(),
            heading("الاشتراك والدفع"),
            body("حالة الاشتراك بتتحقق دايماً من السيرفر — مش من الجهاز."),
            statusCard(sub, loading),
        )

        page.addView(sectionLabel("الخطط"))
        page.addView(
            card(
                planRow("سنوي", "٥٧٥ ج.م — بدل ٦٠٠ ج.م", SubPlan.YEARLY),
                planRow("شهري", "٥٠ ج.م", SubPlan.MONTHLY),
            )
        )

        if (paymentPlan != null) {
            page.addView(sectionLabel("فودافون كاش — تفعيل يدوي"))
            val phoneRow = labeledField("رقم المحفظة", inputType = InputType.TYPE_CLASS_PHONE)
            val refRow = labeledField("رقم عملية التحويل")
            val formErr = formNote(note ?: "")
            page.addView(
                card(
                    body("حوّل قيمة الخطة على رقم فودافون كاش بتاعنا، واكتب رقم العملية هنا — أحمد هيراجعها ويفعّل اشتراكك يدويًا."),
                    phoneRow, refRow, formErr,
                )
            )
            page.addView(primaryButton("إرسال طلب التفعيل") {
                if (busy) return@primaryButton
                val phone = (phoneRow.tag as EditText).text.toString().trim()
                val ref = (refRow.tag as EditText).text.toString().trim()
                if (phone.isEmpty() || ref.isEmpty()) { render(sub, note = "اكتب رقم المحفظة ورقم العملية"); return@primaryButton }
                busy = true
                scope.launch {
                    val result = appGraph.subscriptionRepo.submitVodafoneCash(paymentPlan!!, phone, ref)
                    busy = false
                    when (result) {
                        is SubResult.Ok -> { paymentPlan = null; render(sub, note = null) }
                        is SubResult.Error -> render(sub, note = result.message)
                    }
                }
            })
        } else {
            page.addView(sectionLabel("طرق الدفع الأخرى"))
            page.addView(
                card(
                    comingSoonRow("Paymob", "فيزا / ماستركارد / محافظ إلكترونية — محتاج حساب Paymob"),
                    comingSoonRow("Google Play Billing", "لو التطبيق نُشر على المتجر"),
                )
            )
        }
    }

    private fun statusCard(sub: Subscription, loading: Boolean) = card(
        heading(
            when {
                loading -> "جارٍ التحقق من الاشتراك..."
                sub.status == SubStatus.TRIAL && sub.isUsable() -> "فترة تجريبية"
                sub.status == SubStatus.ACTIVE && sub.isUsable() -> "اشتراك نشط"
                else -> "الاشتراك منتهي"
            }
        ),
        body(
            when {
                loading -> ""
                sub.status == SubStatus.TRIAL && sub.isUsable() -> {
                    val daysLeft = ((sub.trialEndsAt - System.currentTimeMillis()) / TimeUnit.DAYS.toMillis(1)).coerceAtLeast(0)
                    "باقي ${ArabicFormat.number(daysLeft.toDouble(), 0)} يوم على نهاية التجربة المجانية."
                }
                sub.status == SubStatus.ACTIVE && sub.isUsable() -> "مفعّل حتى تاريخه على حساب Supabase."
                else -> "اختار خطة وفعّلها عشان تكمل تستخدم التطبيق."
            }
        ),
    )

    private fun planRow(name: String, price: String, plan: SubPlan): android.widget.LinearLayout {
        val chip = android.widget.Button(this).apply {
            text = "فودافون كاش"
            isAllCaps = false
            textSize = 11.5f
            setTextColor(Theme.ON_TEAL)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat(); setColor(Theme.TEAL)
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setOnClickListener { paymentPlan = plan; scope.launch { render(appGraph.subscriptionRepo.current()) } }
        }
        return settingRow(name, hint = price, control = chip)
    }

    private fun comingSoonRow(name: String, hint: String) = settingRow(name, hint = hint, control = body("قريبًا"))
}
