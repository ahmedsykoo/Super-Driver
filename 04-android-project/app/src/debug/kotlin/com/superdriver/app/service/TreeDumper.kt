package com.superdriver.app.service

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.io.IOException

/**
 * T2 diagnostics. Exists ONLY in the debug source set: the release source set has an empty stub with the same signature.
 * The dump contains whatever Uber shows (prices, street names). It stays on the device under
 * Android/data/com.superdriver.app/files/tree-dump.txt and in logcat tag SDTree; the release build never writes it.
 */
object TreeDumper {
    private const val TAG = "SDTree"
    private const val MAX_FILE_BYTES = 2_000_000L
    private var lastHash = 0
    private var lastAt = 0L

    fun dump(context: Context, root: AccessibilityNodeInfo) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAt < 1000) return
        lastAt = now

        val sb = StringBuilder()
        Nodes.walk(root, 0, IntArray(1)) { n, depth ->
            val r = Rect()
            n.getBoundsInScreen(r)
            sb.append("  ".repeat(depth))
                .append(n.className ?: "?")
                .append(" id=").append(n.viewIdResourceName ?: "-")
                .append(" text=").append(n.text?.toString()?.let { "\"$it\"" } ?: "-")
                .append(" desc=").append(n.contentDescription?.toString()?.let { "\"$it\"" } ?: "-")
                .append(" bounds=").append(r.flattenToString())
                .append('\n')
        }
        val dump = sb.toString()
        if (dump.hashCode() == lastHash) return
        lastHash = dump.hashCode()

        dump.chunked(3500).forEach { Log.d(TAG, it) }
        try {
            val f = File(context.getExternalFilesDir(null), "tree-dump.txt")
            if (f.length() > MAX_FILE_BYTES) f.delete()
            f.appendText("=== ${System.currentTimeMillis()} pkg=${root.packageName} ===\n$dump\n")
        } catch (_: IOException) { /* diagnostics are best-effort */ }
    }
}
