package com.superdriver.app.service

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** Read-only helpers over the accessibility tree. */
object Nodes {
    private const val MAX_DEPTH = 60
    private const val MAX_NODES = 600

    /** Text and contentDescription of every node in tree order (non-blank only). */
    fun collectTexts(root: AccessibilityNodeInfo): List<String> {
        val out = ArrayList<String>()
        walk(root, 0, IntArray(1)) { n, _ ->
            n.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
            n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        }
        return out
    }

    fun walk(node: AccessibilityNodeInfo, depth: Int, counter: IntArray, visit: (AccessibilityNodeInfo, Int) -> Unit) {
        if (depth > MAX_DEPTH || counter[0] >= MAX_NODES) return
        counter[0]++
        visit(node, depth)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, depth + 1, counter, visit)
            recycle(child)
        }
    }

    /** recycle() is deprecated and a no-op from API 33; still recommended below that. */
    @Suppress("DEPRECATION")
    fun recycle(n: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < 33) n.recycle()
    }
}
