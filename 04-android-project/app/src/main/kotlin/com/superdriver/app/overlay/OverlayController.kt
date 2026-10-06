package com.superdriver.app.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.superdriver.app.R
import com.superdriver.engine.ArabicFormat
import com.superdriver.engine.Evaluation
import com.superdriver.engine.Verdict

object Palette {
    const val GOOD = 0xFF2FBF71.toInt()
    const val NEAR = 0xFFE2A33D.toInt()
    const val BAD = 0xFFE25555.toInt()
    const val UNREADABLE = 0xFF5B6470.toInt() // gray: not specified by the handoff, my choice
    const val ON_COLOR = 0xFF10151A.toInt()

    fun of(v: Verdict) = when (v) { Verdict.GOOD -> GOOD; Verdict.NEAR -> NEAR; Verdict.BAD -> BAD }
}

sealed interface OverlayState {
    data class Reading(val eval: Evaluation) : OverlayState
    data object Unreadable : OverlayState
}

/**
 * Floating badge shown by the accessibility service. Display only: it never performs actions on Uber.
 * The detail view on tap is deferred (handoff section 8), so a tap does nothing.
 */
class OverlayController(private val service: AccessibilityService) {
    companion object {
        /**
         * The ONE place that decides the window type.
         * TYPE_ACCESSIBILITY_OVERLAY needs no "display over other apps" permission, but it is NOT yet verified on a real device.
         * Fallback if it fails: WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, which additionally requires
         * <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/> and the user granting it via
         * Settings.ACTION_MANAGE_OVERLAY_PERMISSION (that would also add the onboarding step marked TODO in OnboardingActivity).
         */
        const val WINDOW_TYPE = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        /** The badge's bottom edge may never go below this fraction of the screen height (bottom card area). Tune on device. */
        const val MAX_BOTTOM_FRACTION = 0.38f
        const val START_TOP_FRACTION = 0.08f
    }

    private val wm = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private var view: LinearLayout? = null
    private var valueView: TextView? = null
    private var statusView: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var attached = false

    val isShowing: Boolean get() = attached

    fun show(state: OverlayState) {
        val v = view ?: build().also { view = it }
        val bg = v.background as GradientDrawable
        when (state) {
            is OverlayState.Reading -> {
                valueView!!.text = service.getString(R.string.overlay_value, ArabicFormat.number(state.eval.judgedPerKm))
                statusView!!.text = service.getString(
                    when (state.eval.verdict) {
                        Verdict.GOOD -> R.string.verdict_good
                        Verdict.NEAR -> R.string.verdict_near
                        Verdict.BAD -> R.string.verdict_bad
                    }
                )
                bg.setColor(Palette.of(state.eval.verdict))
                setTextColor(Palette.ON_COLOR)
            }
            OverlayState.Unreadable -> {
                valueView!!.text = service.getString(R.string.overlay_unreadable) // gray badge, never a number
                statusView!!.text = ""
                bg.setColor(Palette.UNREADABLE)
                setTextColor(Color.WHITE)
            }
        }
        statusView!!.visibility = if (state is OverlayState.Reading) View.VISIBLE else View.GONE
        if (!attached) {
            wm.addView(v, params)
            attached = true
        }
        v.post { clampAndApply() }
    }

    fun hide() {
        val v = view ?: return
        if (attached) {
            try { wm.removeView(v) } catch (_: IllegalArgumentException) { /* already gone */ }
            attached = false
        }
    }

    private fun setTextColor(c: Int) { valueView!!.setTextColor(c); statusView!!.setTextColor(c) }

    private fun dp(v: Int) = (v * density).toInt()

    private fun build(): LinearLayout {
        val value = TextView(service).apply { textSize = 22f; setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER }
        val status = TextView(service).apply { textSize = 14f; gravity = Gravity.CENTER }
        val box = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat() }
            addView(value)
            addView(status)
        }
        valueView = value
        statusView = status

        val dm = service.resources.displayMetrics
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WINDOW_TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT // explicit LEFT: x/y are absolute and independent of RTL
            x = (dm.widthPixels * 0.5f).toInt() - dp(60)
            y = (dm.heightPixels * START_TOP_FRACTION).toInt()
        }

        var downRawX = 0f; var downRawY = 0f; var startX = 0; var startY = 0
        box.setOnTouchListener { _, e ->
            val p = params ?: return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downRawX = e.rawX; downRawY = e.rawY; startX = p.x; startY = p.y }
                MotionEvent.ACTION_MOVE -> {
                    p.x = startX + (e.rawX - downRawX).toInt()
                    p.y = startY + (e.rawY - downRawY).toInt()
                    clampAndApply()
                }
            }
            true
        }
        return box
    }

    /** Keeps the badge inside the screen and above the bottom-card limit. */
    private fun clampAndApply() {
        val v = view ?: return
        val p = params ?: return
        if (!attached) return
        val dm = service.resources.displayMetrics
        val maxY = (dm.heightPixels * MAX_BOTTOM_FRACTION).toInt() - v.height
        val maxX = dm.widthPixels - v.width
        p.y = p.y.coerceIn(0, maxOf(0, maxY))
        p.x = p.x.coerceIn(0, maxOf(0, maxX))
        try { wm.updateViewLayout(v, p) } catch (_: IllegalArgumentException) { /* view removed meanwhile */ }
    }
}
