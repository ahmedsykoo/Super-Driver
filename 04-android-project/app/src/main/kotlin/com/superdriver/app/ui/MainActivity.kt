package com.superdriver.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.superdriver.app.R
import com.superdriver.app.service.UberWatcherService
import kotlinx.coroutines.launch

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
