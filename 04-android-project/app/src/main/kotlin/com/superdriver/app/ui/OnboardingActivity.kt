package com.superdriver.app.ui

import android.content.Intent
import android.provider.Settings
import com.superdriver.app.R
import kotlinx.coroutines.launch

/**
 * Runs after login (Ahmed's decision — see LoginActivity). Step 1: privacy explanation. Step 2: a
 * 3-item permissions checklist, same shape as the Captain Pro reference screenshots Ahmed sent
 * (progress dots, ✓/✕ per item, one action button per unfinished item):
 *   1. "اكتشاف الرحلات تلقائياً" — in-app only, no OS permission behind it (Settings.autoDetectEnabled).
 *   2. "متابعة الرحلات" — the real Accessibility permission (same as before).
 *   3. "النافذة العائمة" — informational only: our overlay uses TYPE_ACCESSIBILITY_OVERLAY, which needs
 *      no separate "display over other apps" grant (see OverlayController.WINDOW_TYPE) — unverified on
 *      a real device; if T2/T3 on Ahmed's phone show otherwise, this step needs its own permission.
 */
class OnboardingActivity : BaseActivity() {
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        scope.launch {
            val settings = appGraph.settingsRepo.current()
            when {
                !settings.privacyAccepted -> renderPrivacy()
                else -> renderChecklist(settings.autoDetectEnabled, isWatcherServiceEnabled())
            }
        }
    }

    private fun renderPrivacy() {
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

    private fun renderChecklist(autoDetectOn: Boolean, a11yOn: Boolean) {
        val overlayOn = a11yOn // see class doc: no separate permission in our design, confirm on device
        val done = listOf(autoDetectOn, a11yOn, overlayOn).count { it }

        if (done == 3) { renderDone(); return }

        setPage(
            brandHeader(),
            heading(getString(R.string.onb_checklist_title)),
            body(getString(R.string.onb_checklist_note)),
            checklistProgress(done, 3),
            card(
                checklistItem(
                    getString(R.string.onb_step1_title), autoDetectOn,
                    hint = getString(R.string.onb_step1_hint),
                    actionLabel = getString(R.string.onb_step1_action),
                ) {
                    appGraph.appScope.launch {
                        appGraph.settingsRepo.update { it.copy(autoDetectEnabled = true) }
                        render()
                    }
                },
                checklistItem(
                    getString(R.string.onb_step2_title), a11yOn,
                    hint = getString(R.string.onb_step2_hint),
                    actionLabel = getString(R.string.onb_step2_action),
                ) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                checklistItem(
                    getString(R.string.onb_step3_title), overlayOn,
                    hint = getString(R.string.onb_step3_hint),
                ),
            ),
        )
    }

    private fun renderDone() {
        val page = setPage(
            brandHeader(statusText = "جاهز", statusColor = Theme.GREEN),
            heading(getString(R.string.onb_done_title)),
            card(body(getString(R.string.onb_done_body))),
        )
        page.addView(primaryButton(getString(R.string.onb_continue)) { finish() })
    }
}
