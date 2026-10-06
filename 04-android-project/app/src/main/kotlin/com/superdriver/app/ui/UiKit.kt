package com.superdriver.app.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.superdriver.app.AppGraph
import com.superdriver.app.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/*
 * Visual language shared by every screen. Values mirror the approved design
 * (https://claude.ai/artifact/SsJQY1iByGPoxE9PRYQMDM) so the real app and the
 * design prototype read as the same product. Screens stay built in code (not
 * XML) as before — this file only replaces plain Views with styled ones.
 */
object Theme {
    const val BG = 0xFF0B1420.toInt()
    const val CARD = 0xFF121F33.toInt()
    const val CARD_2 = 0xFF17283F.toInt()
    const val LINE = 0xFF22364F.toInt()
    const val TEAL = 0xFF16B8A0.toInt()
    const val TEAL_DARK = 0xFF0E8C7C.toInt()
    const val GOLD = 0xFFD4AF37.toInt()
    const val RED = 0xFFEF4444.toInt()
    const val AMBER = 0xFFF2A93B.toInt()
    const val GREEN = 0xFF22C55E.toInt()
    const val INK = 0xFFF4F7FA.toInt()
    const val INK_DIM = 0xFF9FB0C4.toInt()
    const val INK_FAINT = 0xFF5E7390.toInt()
    const val ON_TEAL = 0xFF07251F.toInt()

    const val RADIUS_LG = 20
    const val RADIUS_MD = 14
}

enum class NavTab { HOME, SETTINGS }

/*
 * Screens are built in code (not XML) to keep the layer small and compile-safe without an SDK here.
 * All user-visible text still comes from strings.xml. The app is Arabic-only for now, so RTL is forced.
 */
abstract class BaseActivity : Activity() {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    protected val appGraph: AppGraph get() = graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun column(children: Array<out View>) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(22), dp(20), dp(24))
        children.forEach { addView(it) }
    }

    /** Plain scrolling page, no bottom nav — used for the setup flow and sub-screens reached from Home. */
    protected fun setPage(vararg children: View): LinearLayout {
        val col = column(children)
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Theme.BG)
                addView(col, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        )
        return col
    }

    /** Page with the persistent bottom tab bar — used for the three main destinations. */
    protected fun setPageWithNav(active: NavTab, vararg children: View): LinearLayout {
        val col = column(children)
        val scroll = ScrollView(this).apply {
            addView(col, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.BG)
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bottomNav(active))
        }
        setContentView(root)
        return col
    }

    protected fun bottomNav(active: NavTab): LinearLayout {
        fun item(tab: NavTab, label: CharSequence, target: Class<out Activity>?): LinearLayout {
            val on = tab == active
            val icon = View(this@BaseActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (on) Theme.TEAL else Theme.INK_FAINT)
                }
                layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply { bottomMargin = dp(5); gravity = Gravity.CENTER_HORIZONTAL }
            }
            val text = TextView(this@BaseActivity).apply {
                this.text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(typeface, if (on) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (on) Theme.TEAL else Theme.INK_FAINT)
            }
            return LinearLayout(this@BaseActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(10), dp(4), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(icon)
                addView(text)
                if (target != null && !on) {
                    isClickable = true
                    setOnClickListener { startActivity(android.content.Intent(this@BaseActivity, target)) }
                }
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF0E1A2C.toInt())
            addView(View(this@BaseActivity).apply { setBackgroundColor(Theme.LINE) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            addView(item(NavTab.HOME, "الرئيسية", MainActivity::class.java))
            addView(item(NavTab.SETTINGS, "الإعدادات", SettingsActivity::class.java))
        }
    }
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/** Brand mark + app name + an optional status line, used at the top of Home. */
fun Context.brandHeader(statusText: CharSequence? = null, statusColor: Int = Theme.GREEN) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(0, 0, 0, dp(18))
    val mark = TextView(this@brandHeader).apply {
        text = "SD"
        setTextColor(0xFFFFFFFF.toInt())
        setTypeface(typeface, Typeface.BOLD)
        textSize = 14f
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dp(11).toFloat()
            setColor(Theme.TEAL_DARK)
        }
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) }
    }
    val titleCol = LinearLayout(this@brandHeader).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addView(TextView(this@brandHeader).apply {
            text = "Super Driver"; setTextColor(Theme.INK); setTypeface(typeface, Typeface.BOLD); textSize = 16f
        })
        if (statusText != null) {
            addView(
                LinearLayout(this@brandHeader).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(2), 0, 0)
                    addView(dot(statusColor))
                    addView(TextView(this@brandHeader).apply {
                        text = statusText; setTextColor(Theme.INK_DIM); textSize = 11.5f
                    })
                }
            )
        }
    }
    addView(mark)
    addView(titleCol)
}

fun Context.heading(text: CharSequence) = TextView(this).apply {
    this.text = text; textSize = 20f; setTypeface(typeface, Typeface.BOLD); setTextColor(Theme.INK)
    setPadding(0, 0, 0, dp(14)); textAlignment = View.TEXT_ALIGNMENT_VIEW_START
}

fun Context.sectionLabel(text: CharSequence) = TextView(this).apply {
    this.text = "◆ $text"; textSize = 12.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(Theme.TEAL)
    setPadding(0, dp(4), 0, dp(9)); textAlignment = View.TEXT_ALIGNMENT_VIEW_START
}

fun Context.body(text: CharSequence) = TextView(this).apply {
    this.text = text; textSize = 13.5f; setTextColor(Theme.INK_DIM)
    setPadding(0, dp(2), 0, dp(10)); textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    setLineSpacing(dp(2).toFloat(), 1f)
}

/** Rounded, bordered container — the one repeating surface every screen is built from. */
fun Context.card(vararg children: View) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = GradientDrawable().apply {
        cornerRadius = dp(Theme.RADIUS_LG).toFloat()
        setColor(Theme.CARD)
        setStroke(dp(1), Theme.LINE)
    }
    setPadding(dp(18), dp(16), dp(18), dp(16))
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
    children.forEach { addView(it) }
}

fun Context.primaryButton(text: CharSequence, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    isAllCaps = false
    setTextColor(Theme.ON_TEAL)
    setTypeface(typeface, Typeface.BOLD)
    background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Theme.TEAL) }
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(10) }
}

fun Context.outlineButton(text: CharSequence, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    isAllCaps = false
    setTextColor(Theme.RED)
    background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Theme.CARD_2); setStroke(dp(1), 0x33EF4444) }
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(10) }
}

fun Context.dot(color: Int) = View(this).apply {
    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply { gravity = Gravity.CENTER_VERTICAL; marginEnd = dp(8) }
}

/** A labelled row inside a card: title (+ optional hint) on the start side, arbitrary control on the end side. */
fun Context.settingRow(title: CharSequence, hint: CharSequence? = null, control: View) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(0, dp(11), 0, dp(11))
    val textCol = LinearLayout(this@settingRow).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addView(TextView(this@settingRow).apply { text = title; setTextColor(Theme.INK); textSize = 13.5f })
        if (hint != null) addView(TextView(this@settingRow).apply { text = hint; setTextColor(Theme.INK_FAINT); textSize = 10.5f })
    }
    addView(textCol)
    addView(control)
}

fun Context.styledSwitch(checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
    text = ""
    isChecked = checked
    trackTintList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Theme.TEAL, Theme.LINE),
    )
    thumbTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
    setOnCheckedChangeListener { _: CompoundButton, on: Boolean -> onChange(on) }
}

fun Context.styledRadio(text: CharSequence, checked: Boolean) = RadioButton(this).apply {
    this.text = text
    isChecked = checked
    setTextColor(Theme.INK)
    textSize = 13.5f
    setPadding(dp(8), dp(6), 0, dp(6))
    buttonTintList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Theme.TEAL, Theme.INK_FAINT),
    )
}

/** One labelled text field, used by the login/profile form. */
fun Context.labeledField(
    label: CharSequence,
    hint: CharSequence = "",
    inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
    prefill: CharSequence = "",
): LinearLayout {
    val field = EditText(this).apply {
        this.hint = hint
        this.inputType = inputType
        setText(prefill)
        setTextColor(Theme.INK)
        setHintTextColor(Theme.INK_FAINT)
        textSize = 14.5f
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Theme.CARD_2); setStroke(dp(1), Theme.LINE) }
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(12))
        addView(TextView(this@labeledField).apply { text = label; setTextColor(Theme.INK_DIM); textSize = 11.5f; setPadding(0, 0, 0, dp(6)) })
        addView(field)
        tag = field // lets the caller pull the EditText back out with (row.tag as EditText)
    }
}

/** Inline error/status line under a form; hidden (empty) by default. */
fun Context.formNote(initial: CharSequence = "", color: Int = Theme.RED) = TextView(this).apply {
    text = initial
    setTextColor(color)
    textSize = 11.5f
    setPadding(0, dp(2), 0, dp(6))
    visibility = if (initial.isEmpty()) View.GONE else View.VISIBLE
}

/**
 * "التقدم n/total" + a row of n dots (green = done, amber = current, gray = not reached yet) — the
 * permissions-checklist header, modeled on the Captain Pro reference screenshots Ahmed sent.
 */
fun Context.checklistProgress(done: Int, total: Int): LinearLayout {
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(14))
    }
    col.addView(TextView(this).apply {
        text = "التقدم $done/$total"
        setTextColor(Theme.AMBER)
        setTypeface(typeface, Typeface.BOLD)
        textSize = 12f
        setPadding(0, 0, 0, dp(8))
    })
    val dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    for (i in 0 until total) {
        val color = when { i < done -> Theme.GREEN; i == done -> Theme.AMBER; else -> Theme.INK_FAINT }
        dots.addView(
            View(this@checklistProgress).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
                layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { if (i > 0) marginStart = dp(6) }
            }
        )
    }
    col.addView(dots)
    return col
}

/** One item in the permissions checklist: a ✓/✕ status line, its explanation, and an optional action button. */
fun Context.checklistItem(title: CharSequence, done: Boolean, hint: CharSequence? = null, actionLabel: CharSequence? = null, onAction: (() -> Unit)? = null) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(0, dp(10), 0, dp(10))
    addView(
        LinearLayout(this@checklistItem).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@checklistItem).apply {
                text = if (done) "✓" else "✕"
                setTextColor(if (done) Theme.GREEN else Theme.RED)
                setTypeface(typeface, Typeface.BOLD)
                textSize = 14f
                setPadding(0, 0, dp(8), 0)
            })
            addView(TextView(this@checklistItem).apply { text = title; setTextColor(Theme.INK); textSize = 13.5f; setTypeface(typeface, Typeface.BOLD) })
        }
    )
    if (hint != null) addView(TextView(this@checklistItem).apply { text = hint; setTextColor(Theme.INK_FAINT); textSize = 11f; setPadding(dp(22), dp(4), 0, 0) })
    if (!done && actionLabel != null && onAction != null) {
        addView(
            TextView(this@checklistItem).apply {
                text = actionLabel
                setTextColor(Theme.ON_TEAL)
                setTypeface(typeface, Typeface.BOLD)
                textSize = 12.5f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Theme.AMBER) }
                setPadding(dp(14), dp(10), dp(14), dp(10))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
                setOnClickListener { onAction() }
            }
        )
    }
}
