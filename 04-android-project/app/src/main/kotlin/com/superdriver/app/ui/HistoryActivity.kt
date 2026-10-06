package com.superdriver.app.ui

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.superdriver.app.R
import com.superdriver.app.data.TripEntity
import com.superdriver.app.overlay.Palette
import com.superdriver.engine.ArabicFormat
import com.superdriver.engine.Verdict
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Log of offers that were read and displayed (the app is read-only, so acceptance is unknown). Same data as the badge, no addresses. */
class HistoryActivity : BaseActivity() {
    private var rows: List<TripEntity> = emptyList()
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale("ar"))

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = rows[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val t = rows[position]
            val verdict = Verdict.valueOf(t.verdict)
            val label = getString(
                when (verdict) { Verdict.GOOD -> R.string.verdict_good; Verdict.NEAR -> R.string.verdict_near; Verdict.BAD -> R.string.verdict_bad }
            )
            val line1 = LinearLayout(this@HistoryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(badge(label, Palette.of(verdict)))
                addView(TextView(this@HistoryActivity).apply {
                    text = "  " + ArabicFormat.number(t.judgedPerKm) + " ج.م/كم"
                    textSize = 14.5f; setTextColor(Theme.INK); setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
            }
            val line2 = TextView(this@HistoryActivity).apply {
                text = getString(
                    R.string.hist_row_detail, dateFormat.format(Date(t.timeMillis)),
                    ArabicFormat.number(t.price, 2), ArabicFormat.number(t.pickupKm), ArabicFormat.number(t.tripKm),
                )
                textSize = 11.5f; setTextColor(Theme.INK_FAINT)
                setPadding(0, dp(4), 0, 0)
            }
            val col = LinearLayout(this@HistoryActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(line1); addView(line2)
            }
            return LinearLayout(this@HistoryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(11), 0, dp(11))
                addView(col)
            }
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val empty = body(getString(R.string.hist_empty))
        val list = ListView(this).apply {
            this.adapter = this@HistoryActivity.adapter
            divider = android.graphics.drawable.ColorDrawable(Theme.LINE)
            dividerHeight = 1
        }
        val clear = outlineButton(getString(R.string.hist_clear)) {
            AlertDialog.Builder(this)
                .setMessage(R.string.hist_clear_confirm)
                .setPositiveButton(R.string.hist_yes) { _, _ -> appGraph.tripLog.clear() }
                .setNegativeButton(R.string.hist_no, null)
                .show()
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.BG)
            setPadding(dp(20), dp(22), dp(20), 0)
            addView(brandHeader())
            addView(heading(getString(R.string.hist_title)))
            addView(empty)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(clear)
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.BG)
            addView(root, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bottomNav(NavTab.HISTORY))
        }
        setContentView(page)

        scope.launch {
            appGraph.db.tripDao().observeRecent(200).collect { list2 ->
                rows = list2
                empty.visibility = if (list2.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
            }
        }
    }
}
