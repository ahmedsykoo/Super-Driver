package com.superdriver.app.ui

import android.os.Bundle
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.superdriver.app.R
import com.superdriver.engine.ArabicFormat
import com.superdriver.engine.Basis
import com.superdriver.engine.Thresholds
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {
    private companion object {
        const val MIN = 1.0
        const val STEP = 0.5
        const val MAX = 20.0
    }

    private var good = Thresholds.DEFAULT_GOOD
    private var near = Thresholds.DEFAULT_NEAR
    private lateinit var goodLabel: TextView
    private lateinit var nearLabel: TextView
    private lateinit var goodBar: SeekBar
    private lateinit var nearBar: SeekBar

    private fun toProgress(v: Double) = ((v - MIN) / STEP).toInt()
    private fun fromProgress(p: Int) = MIN + p * STEP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            val s = appGraph.settingsRepo.current()
            good = s.thresholds.good
            near = s.thresholds.near

            goodLabel = body("")
            nearLabel = body("")
            goodBar = bar(good) { p -> good = maxOf(fromProgress(p), MIN + STEP); if (near > good - STEP) near = good - STEP; sync() }
            nearBar = bar(near) { p -> near = minOf(fromProgress(p), good - STEP); sync() }

            val group = RadioGroup(this@SettingsActivity).apply {
                addView(styledRadio(getString(R.string.set_basis_inclusive), s.basis == Basis.INCLUSIVE).apply { id = 1 })
                addView(styledRadio(getString(R.string.set_basis_trip), s.basis != Basis.INCLUSIVE).apply { id = 2 })
                setOnCheckedChangeListener { _, checked ->
                    save { it.copy(basis = if (checked == 1) Basis.INCLUSIVE else Basis.TRIP_ONLY) }
                }
            }
            val vibrate = settingRow(
                getString(R.string.set_vibrate),
                control = styledSwitch(s.vibrate) { on -> save { it.copy(vibrate = on) } },
            )
            val autoDetect = settingRow(
                getString(R.string.onb_step1_title),
                control = styledSwitch(s.autoDetectEnabled) { on -> save { it.copy(autoDetectEnabled = on) } },
            )

            val page = setPageWithNav(
                NavTab.SETTINGS,
                brandHeader(),
                heading(getString(R.string.set_title)),
                sectionLabel("حدود التقييم"),
                card(goodLabel, goodBar, nearLabel, nearBar),
                sectionLabel("أساس الحساب"),
                card(group),
                sectionLabel("عام"),
                card(vibrate, autoDetect),
            )
            page.addView(outlineButton(getString(R.string.set_logout)) {
                scope.launch {
                    appGraph.sessionRepo.logout() // awaited: MainActivity checks the session right on resume
                    finish()
                }
            })
            sync()
        }
    }

    private fun bar(value: Double, onChange: (Int) -> Unit) = SeekBar(this).apply {
        max = toProgress(MAX)
        progress = toProgress(value)
        progressTintList = android.content.res.ColorStateList.valueOf(Theme.TEAL)
        thumbTintList = android.content.res.ColorStateList.valueOf(Theme.TEAL)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) { save { it.copy(thresholds = Thresholds(good, near)) } }
        })
    }

    private fun sync() {
        goodBar.progress = toProgress(good)
        nearBar.progress = toProgress(near)
        goodLabel.text = getString(R.string.set_good, ArabicFormat.number(good))
        nearLabel.text = getString(R.string.set_near, ArabicFormat.number(near))
    }

    /** Written through the app-level scope so the last change survives leaving the screen immediately. */
    private fun save(change: (com.superdriver.app.data.Settings) -> com.superdriver.app.data.Settings) {
        appGraph.appScope.launch { appGraph.settingsRepo.update(change) }
    }
}
