package com.superdriver.app.ui

import android.content.Intent
import android.provider.Settings
import com.superdriver.app.R
import kotlinx.coroutines.launch

/**
 * Step 1: privacy explanation (shown before Accessibility is requested). Step 2: send the user to Accessibility settings.
 *
 * TODO(T2/T3 result, not built on purpose): step "display over other apps" (SYSTEM_ALERT_WINDOW).
 *   Needed only if TYPE_ACCESSIBILITY_OVERLAY does not work on a real device (see OverlayController.WINDOW_TYPE).
 * TODO(T2 result, not built on purpose): third permission "auto-detect rides". Unknown whether Uber needs it; decide after T2.
 */
class OnboardingActivity : BaseActivity() {
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        scope.launch {
            val privacyAccepted = appGraph.settingsRepo.current().privacyAccepted
            when {
                !privacyAccepted -> {
                    val page = setPage(
                        brandHeader(),
                        heading(getString(R.string.onb_privacy_title)),
                        card(body(getString(R.string.onb_privacy_body))),
                    )
                    page.addView(primaryButton(getString(R.string.onb_accept)) {
                        appGraph.appScope.launch {
                            appGraph.settingsRepo.update { it.copy(privacyAccepted = true) }
                            render()
                        }
                    })
                }
                !isWatcherServiceEnabled() -> {
                    val page = setPage(
                        brandHeader(),
                        heading(getString(R.string.onb_a11y_title)),
                        card(body(getString(R.string.onb_a11y_body))),
                    )
                    page.addView(primaryButton(getString(R.string.onb_open_a11y)) {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    })
                }
                else -> {
                    val page = setPage(
                        brandHeader(statusText = "جاهز", statusColor = Theme.GREEN),
                        heading(getString(R.string.onb_done_title)),
                        card(body(getString(R.string.onb_done_body))),
                    )
                    page.addView(primaryButton(getString(R.string.onb_continue)) { finish() })
                }
            }
        }
    }
}
