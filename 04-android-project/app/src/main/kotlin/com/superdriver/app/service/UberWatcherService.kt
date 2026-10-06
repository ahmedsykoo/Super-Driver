package com.superdriver.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.accessibility.AccessibilityEvent
import com.superdriver.app.graph
import com.superdriver.app.overlay.OverlayController
import com.superdriver.app.overlay.OverlayState
import com.superdriver.engine.ParseResult
import com.superdriver.engine.RideEvaluator
import com.superdriver.engine.RideOffer
import com.superdriver.engine.RideParser

/**
 * Read-only: reads the Uber driver screen, shows a badge. No performAction, no clicks, no gestures.
 * Logging of screen content exists only in the debug source set (TreeDumper).
 */
class UberWatcherService : AccessibilityService() {
    companion object {
        /** Must match android:packageNames in res/xml/accessibility_service_config.xml. Unverified on a device. */
        const val UBER_PACKAGE = "com.ubercab.driver"
        private const val DEBOUNCE_MS = 120L
        private const val POLL_MS = 400L
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlay: OverlayController
    private var queued = false
    private var lastState: OverlayState? = null
    private var lastOffer: RideOffer? = null

    private val tick = Runnable {
        queued = false
        evaluate()
        // While the badge is visible, keep re-checking: Uber may leave or the request may disappear
        // without a further event from the (package-filtered) Uber window.
        if (overlay.isShowing) schedule(POLL_MS)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != UBER_PACKAGE) return
        schedule(DEBOUNCE_MS)
    }

    override fun onInterrupt() = hideBadge()

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (::overlay.isInitialized) overlay.hide()
        super.onDestroy()
    }

    private fun schedule(delayMs: Long) {
        if (queued) return
        queued = true
        handler.postDelayed(tick, delayMs)
    }

    private fun hideBadge() {
        overlay.hide()
        lastState = null
        lastOffer = null
    }

    private fun evaluate() {
        val settings = graph.settingsRepo.settings.value
        if (!settings.privacyAccepted) { hideBadge(); return } // no partial work before onboarding

        val root = rootInActiveWindow
        if (root == null) { hideBadge(); return }
        try {
            if (root.packageName?.toString() != UBER_PACKAGE) { hideBadge(); return }

            TreeDumper.dump(this, root) // no-op in release

            when (val r = RideParser.parse(Nodes.collectTexts(root))) {
                ParseResult.NotRideScreen -> hideBadge()
                is ParseResult.Unreadable -> render(OverlayState.Unreadable, null)
                is ParseResult.Ok -> {
                    val eval = RideEvaluator.evaluate(r.offer, settings.thresholds, settings.basis)
                    render(OverlayState.Reading(eval), r.offer)
                }
            }
        } finally {
            Nodes.recycle(root)
        }
    }

    private fun render(state: OverlayState, offer: RideOffer?) {
        if (state != lastState || !overlay.isShowing) {
            overlay.show(state)
            lastState = state
        }
        if (offer != null && offer != lastOffer) {
            lastOffer = offer
            if (graph.settingsRepo.settings.value.vibrate) vibrateOnce()
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateOnce() {
        getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
