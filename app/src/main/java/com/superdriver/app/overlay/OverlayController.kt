package com.superdriver.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.superdriver.app.R
import com.superdriver.app.core.Format
import com.superdriver.app.core.TripOffer
import com.superdriver.app.core.Verdict
import com.superdriver.app.prefs.AppSettings
import kotlin.math.abs

/**
 * Small draggable card drawn above Uber Driver.
 *
 * It only shows what the parser extracted (price, distance, rate) and the
 * verdict. It never covers the whole screen and it is not focusable, so the
 * driver can still press Accept or Decline on the Uber card underneath.
 */
class OverlayController(
    private val context: Context,
    private val settings: AppSettings,
    private val onRefresh: () -> Unit
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        val savedX = settings.overlayX
        x = if (savedX == Int.MIN_VALUE) 16 else savedX
        y = settings.overlayY
    }

    private var view: View? = null
    private var tvWaiting: TextView? = null
    private var layoutDetails: View? = null
    private var tvService: TextView? = null
    private var tvPrice: TextView? = null
    private var tvDistance: TextView? = null
    private var tvRate: TextView? = null
    private var tvVerdict: TextView? = null
    private var btnRefresh: TextView? = null
    private var btnClose: TextView? = null

    private var showingKey: String? = null
    private var hiddenForKey: String? = null
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragStartTouchX = 0f
    private var dragStartTouchY = 0f
    private var dragged = false

    fun isAttached(): Boolean = view != null

    fun attach() {
        if (view != null) return
        if (!Settings.canDrawOverlays(context)) return

        val themed = ContextThemeWrapper(context, R.style.Theme_SuperDriver)
        val root = LayoutInflater.from(themed).inflate(R.layout.overlay_card, null)

        tvWaiting = root.findViewById(R.id.tvWaiting)
        layoutDetails = root.findViewById(R.id.layoutDetails)
        tvService = root.findViewById(R.id.tvService)
        tvPrice = root.findViewById(R.id.tvPrice)
        tvDistance = root.findViewById(R.id.tvDistance)
        tvRate = root.findViewById(R.id.tvRate)
        tvVerdict = root.findViewById(R.id.tvVerdict)
        btnRefresh = root.findViewById(R.id.btnRefresh)
        btnClose = root.findViewById(R.id.btnClose)

        root.setOnTouchListener { _, event -> handleTouch(event) }

        try {
            windowManager.addView(root, params)
            view = root
        } catch (t: Throwable) {
            view = null
        }
    }

    fun detach() {
        val current = view ?: return
        try {
            windowManager.removeView(current)
        } catch (t: Throwable) {
            // The window is already gone.
        }
        view = null
        tvWaiting = null
        layoutDetails = null
        tvService = null
        tvPrice = null
        tvDistance = null
        tvRate = null
        tvVerdict = null
        btnRefresh = null
        btnClose = null
    }

    /** Hides the card until a different offer shows up. */
    fun hideUntilNextOffer() {
        hiddenForKey = showingKey
        detach()
    }

    fun showWaiting() {
        if (isAttached() && showingKey == null) return
        attach()
        showingKey = null
        tvWaiting?.visibility = View.VISIBLE
        layoutDetails?.visibility = View.GONE
    }

    fun showOffer(offer: TripOffer, goodAtLeast: Double, nearAtLeast: Double) {
        if (offer.key == hiddenForKey) return
        hiddenForKey = null

        attach()
        val root = view ?: return
        showingKey = offer.key

        tvWaiting?.visibility = View.GONE
        layoutDetails?.visibility = View.VISIBLE

        val service = offer.serviceName
        if (service.isNullOrBlank()) {
            tvService?.visibility = View.GONE
        } else {
            tvService?.visibility = View.VISIBLE
            tvService?.text = service
        }

        val egp = context.getString(R.string.unit_egp)
        val km = context.getString(R.string.unit_km)
        tvPrice?.text = context.getString(
            R.string.overlay_price_label
        ) + ": " + Format.money(offer.priceEgp) + " " + egp

        val pickup = offer.pickupKm
        val trip = offer.tripKm
        val distanceValue = when {
            pickup != null && trip != null ->
                "${Format.km(pickup + trip)} $km (${Format.km(pickup)} + ${Format.km(trip)})"
            pickup != null -> "${Format.km(pickup)} $km"
            trip != null -> "${Format.km(trip)} $km"
            else -> "-"
        }
        tvDistance?.text = context.getString(R.string.overlay_distance_label) + ": " + distanceValue

        val rate = offer.egpPerKm
        tvRate?.text = if (rate == null) "-" else Format.rate(rate)

        val verdict = offer.verdict(goodAtLeast, nearAtLeast)
        val verdictView = tvVerdict
        if (verdictView != null) {
            verdictView.text = verdictLabel(verdict)
            verdictView.setTextColor(ContextCompat.getColor(context, verdictColor(verdict)))
        }
        root.invalidate()
    }

    private fun verdictLabel(verdict: Verdict?): String = when (verdict) {
        Verdict.SUITABLE -> context.getString(R.string.verdict_suitable)
        Verdict.NEAR -> context.getString(R.string.verdict_near)
        Verdict.NOT_SUITABLE, null -> context.getString(R.string.verdict_not_suitable)
    }

    private fun verdictColor(verdict: Verdict?): Int = when (verdict) {
        Verdict.SUITABLE -> R.color.verdict_good
        Verdict.NEAR -> R.color.verdict_near
        Verdict.NOT_SUITABLE, null -> R.color.verdict_bad
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = params.x
                dragStartY = params.y
                dragStartTouchX = event.rawX
                dragStartTouchY = event.rawY
                dragged = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragStartTouchX).toInt()
                val dy = (event.rawY - dragStartTouchY).toInt()
                if (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP) dragged = true
                params.x = dragStartX + dx
                params.y = dragStartY + dy
                val current = view
                if (current != null && current.isAttachedToWindow) {
                    windowManager.updateViewLayout(current, params)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragged) handleTap(event)
                settings.overlayX = params.x
                settings.overlayY = params.y
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                settings.overlayX = params.x
                settings.overlayY = params.y
                return true
            }
        }
        return false
    }

    /** The card is not focusable, so taps on the two buttons are dispatched here. */
    private fun handleTap(event: MotionEvent) {
        when {
            isInside(btnRefresh, event) -> onRefresh()
            isInside(btnClose, event) -> hideUntilNextOffer()
        }
    }

    private fun isInside(target: View?, event: MotionEvent): Boolean {
        val view = target ?: return false
        if (view.visibility != View.VISIBLE) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = event.rawX
        val y = event.rawY
        return x >= location[0] && x <= location[0] + view.width &&
            y >= location[1] && y <= location[1] + view.height
    }

    companion object {
        private const val TOUCH_SLOP = 6
    }
}
