package com.superdriver.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.superdriver.app.core.ReadSource
import com.superdriver.app.core.TripOffer
import com.superdriver.app.core.TripOfferParser
import com.superdriver.app.ocr.ScreenOcr
import com.superdriver.app.overlay.OverlayController
import com.superdriver.app.prefs.AppSettings

/**
 * Reads the Uber Driver screen and shows the result card.
 *
 * Reading order, fastest first:
 *   1. the accessibility tree of the Uber window (exact texts, works in Arabic),
 *   2. a screenshot + on-device OCR, only when the tree gives nothing usable.
 *
 * Everything happens on the device. There is no network call and no Uber API.
 */
class RideAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var settings: AppSettings? = null
    private var overlay: OverlayController? = null
    private var ocr: ScreenOcr? = null

    private var lastReadAt = 0L
    private var lastOcrAt = 0L
    private var lastOfferAt = 0L
    private var lastUberEventAt = 0L
    private var reading = false

    private val readRunnable = Runnable { read() }
    private val hideRunnable = Runnable {
        if (System.currentTimeMillis() - lastUberEventAt >= LEAVE_UBER_GRACE_MS) {
            overlay?.detach()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val prefs = AppSettings(this)
        settings = prefs
        overlay = OverlayController(this, prefs) { forceRead() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ocr = ScreenOcr()
        }
        Log.d(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (UBER_PACKAGES.contains(pkg)) {
            lastUberEventAt = System.currentTimeMillis()
            handler.removeCallbacks(hideRunnable)
            if (settings?.overlayEnabled == true) {
                overlay?.attach()
                if (lastOfferAt == 0L) overlay?.showWaiting()
            } else {
                // The driver turned the card off from the settings screen.
                overlay?.detach()
            }
            scheduleRead()
        } else {
            // Another app is in front: leave the driver's screen alone.
            scheduleHide()
        }
    }

    // ----------------------------------------------------------------- reading

    private fun scheduleRead() {
        handler.removeCallbacks(readRunnable)
        handler.postDelayed(readRunnable, DEBOUNCE_MS)
    }

    private fun forceRead() {
        handler.removeCallbacks(readRunnable)
        read(force = true)
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, LEAVE_UBER_GRACE_MS)
    }

    private fun read(force: Boolean = false) {
        val prefs = settings ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastReadAt < MIN_READ_INTERVAL_MS) return
        lastReadAt = now
        if (reading) return
        reading = true

        // 1) Accessibility tree — exact texts, Arabic included.
        val texts = collectTexts()
        val offer = if (texts.isEmpty()) {
            null
        } else {
            TripOfferParser.parse(texts, ReadSource.ACCESSIBILITY)
        }

        if (offer != null) {
            reading = false
            onOffer(offer)
            return
        }

        // 2) Screenshot + OCR fallback.
        if (prefs.ocrEnabled && ocr != null && now - lastOcrAt >= OCR_MIN_INTERVAL_MS) {
            readWithOcr(now)
        } else {
            reading = false
            onNoOffer(now)
        }
    }

    private fun readWithOcr(now: Long) {
        val reader = ocr
        if (reader == null) {
            reading = false
            onNoOffer(now)
            return
        }
        lastOcrAt = now
        capture { screenshot ->
            if (screenshot == null) {
                reading = false
                onNoOffer(now)
                return@capture
            }
            val prepared = prepareForOcr(screenshot)
            screenshot.recycle()
            reader.read(prepared) { lines ->
                prepared.recycle()
                reading = false
                val offer = TripOfferParser.parse(lines, ReadSource.OCR)
                if (offer != null) onOffer(offer) else onNoOffer(System.currentTimeMillis())
            }
        }
    }

    private fun onOffer(offer: TripOffer) {
        val prefs = settings ?: return
        lastOfferAt = System.currentTimeMillis()
        Log.d(TAG, "offer ${offer.priceEgp} EGP / ${offer.totalKm} km = ${offer.egpPerKm}")
        if (prefs.overlayEnabled) {
            overlay?.showOffer(offer, prefs.goodAtLeast, prefs.nearAtLeast)
        }
    }

    private fun onNoOffer(now: Long) {
        if (lastOfferAt != 0L && now - lastOfferAt > OFFER_GONE_TIMEOUT_MS) {
            lastOfferAt = 0L
            overlay?.showWaiting()
        }
    }

    // ------------------------------------------------------ accessibility tree

    private fun collectTexts(): List<String> {
        val texts = LinkedHashSet<String>()
        val root = rootInActiveWindow
        if (root != null) {
            collectNode(root, texts, 0)
        } else {
            for (window in windows) {
                val windowRoot = window.root ?: continue
                collectNode(windowRoot, texts, 0)
            }
        }
        return texts.toList()
    }

    private fun collectNode(node: AccessibilityNodeInfo, out: MutableSet<String>, depth: Int) {
        if (depth > MAX_NODE_DEPTH) return
        addText(node.text, out)
        addText(node.contentDescription, out)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectNode(child, out, depth + 1)
            child.recycle()
        }
    }

    private fun addText(value: CharSequence?, out: MutableSet<String>) {
        val text = value?.toString()?.trim() ?: return
        if (text.isNotBlank()) out.add(text)
    }

    // ------------------------------------------------------------------- OCR

    private fun capture(onBitmap: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onBitmap(null)
            return
        }
        takeScreenshotInternal(onBitmap)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeScreenshotInternal(onBitmap: (Bitmap?) -> Unit) {
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        buffer.close()
                        if (hardware == null) {
                            onBitmap(null)
                            return
                        }
                        val software = hardware.copy(Bitmap.Config.ARGB_8888, false)
                        hardware.recycle()
                        onBitmap(software)
                    }

                    override fun onFailure(errorCode: Int) {
                        onBitmap(null)
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "screenshot failed: ${t.message}")
            onBitmap(null)
        }
    }

    /**
     * The offer card sits in the lower part of the screen. Cropping it keeps the
     * OCR small and fast, and the grayscale + contrast pass helps on dark cards.
     */
    private fun prepareForOcr(source: Bitmap): Bitmap {
        val startY = (source.height * ROI_TOP_RATIO).toInt()
        val roiHeight = (source.height - startY).coerceAtLeast(1)
        val roi = Bitmap.createBitmap(source, 0, startY, source.width, roiHeight)

        val width = (roi.width * OCR_SCALE).toInt().coerceAtLeast(1)
        val height = (roi.height * OCR_SCALE).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(roi, width, height, true)
        roi.recycle()

        val gray = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gray)
        val paint = Paint()
        val contrast = 1.4f
        val offset = -(128f * contrast) + 128f
        val matrix = ColorMatrix(
            floatArrayOf(
                0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, offset,
                0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, offset,
                0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        scaled.recycle()
        return gray
    }

    // --------------------------------------------------------------- lifecycle

    override fun onInterrupt() {
        // Nothing to interrupt: every read is a single shot.
    }

    override fun onDestroy() {
        handler.removeCallbacks(readRunnable)
        handler.removeCallbacks(hideRunnable)
        overlay?.detach()
        overlay = null
        ocr?.close()
        ocr = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SuperDriver"

        private val UBER_PACKAGES = setOf("com.ubercab.driver")

        /** Wait for the card to settle before reading it. */
        private const val DEBOUNCE_MS = 120L
        /** Never read more than ~4 times per second. */
        private const val MIN_READ_INTERVAL_MS = 220L
        /** OCR is much heavier than reading the tree: keep it rare. */
        private const val OCR_MIN_INTERVAL_MS = 700L
        /** An offer counts as gone after this delay without a re-read. */
        private const val OFFER_GONE_TIMEOUT_MS = 2_500L
        /** Grace period before removing the card when Uber is left. */
        private const val LEAVE_UBER_GRACE_MS = 1_500L

        private const val MAX_NODE_DEPTH = 24

        private const val ROI_TOP_RATIO = 0.45f
        private const val OCR_SCALE = 0.6f

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, RideAccessibilityService::class.java)
                .flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
