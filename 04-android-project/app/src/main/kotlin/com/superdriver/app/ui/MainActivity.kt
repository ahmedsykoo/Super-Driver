package com.superdriver.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.superdriver.app.R
import com.superdriver.app.data.SubStatus
import com.superdriver.app.service.UberWatcherService
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

fun Context.isWatcherServiceEnabled(): Boolean {
    val me = ComponentName(this, UberWatcherService::class.java)
    val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
}

class MainActivity : BaseActivity() {
    override fun onResume() {
        super.onResume()
        scope.launch {
            val loggedIn = appGraph.sessionRepo.current()?.profile?.isComplete == true
            if (!loggedIn) {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                return@launch
            }

            // Cached status only — never block Home on a network call. Kick a background refresh when
            // the 24h offline window (handoff acceptance criteria) has expired.
            val sub = appGraph.subscriptionRepo.current()
            if (!sub.isCacheFresh()) appGraph.appScope.launch { appGraph.subscriptionRepo.refresh() }
            if (!sub.isUsable()) {
                setPageWithNav(
                    NavTab.HOME,
                    brandHeader(statusText = "الاشتراك منتهي", statusColor = Theme.RED),
                    card(
                        heading("الاشتراك منتهي"),
                        body("فعّل اشتراكك عشان تقدر تستخدم Super Driver."),
                    ),
                ).addView(primaryButton("الاشتراك والدفع") {
                    startActivity(Intent(this@MainActivity, SubscriptionActivity::class.java))
                })
                return@launch
            }

            val settings = appGraph.settingsRepo.current()
            val ready = settings.privacyAccepted && settings.autoDetectEnabled && isWatcherServiceEnabled()
            val statusColor = if (ready) Theme.GREEN else Theme.RED
            val statusText = getString(if (ready) R.string.main_status_on else R.string.main_status_off)

            val statusCard = card(
                heading(if (ready) "الخدمة نشطة الآن" else "الخدمة متوقفة"),
                body(statusText),
            )

            val page = setPageWithNav(
                NavTab.HOME,
                brandHeader(statusText = if (ready) "تعمل الآن" else "متوقف", statusColor = statusColor),
                statusCard,
            )

            if (sub.status == SubStatus.TRIAL) {
                val daysLeft = ((sub.trialEndsAt - System.currentTimeMillis()) / TimeUnit.DAYS.toMillis(1)).coerceAtLeast(0)
                page.addView(sectionLabel("التجربة المجانية"))
                page.addView(card(body("باقي $daysLeft يوم على نهاية الفترة التجريبية.")))
            }

            if (!ready) {
                page.addView(primaryButton(getString(R.string.main_finish_setup)) {
                    startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                })
            } else {
                page.addView(sectionLabel("تذكير سريع"))
                page.addView(
                    card(
                        body("Super Driver بيقرأ شاشة طلب الرحلة في تطبيق أوبر للسائق بس، ويعرض سعر الكيلومتر فوق الخريطة — من غير أي ضغط أو تدخل في أوبر."),
                    )
                )
            }
        }
    }
}
