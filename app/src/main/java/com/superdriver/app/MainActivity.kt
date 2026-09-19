package com.superdriver.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.superdriver.app.core.Format
import com.superdriver.app.core.TextNormalizer
import com.superdriver.app.core.TripOfferParser
import com.superdriver.app.core.Verdict
import com.superdriver.app.prefs.AppSettings
import com.superdriver.app.service.RideAccessibilityService
import com.superdriver.app.util.AppLocales

/**
 * Settings screen: permissions, language, verdict thresholds and a small box to
 * test the parser without opening Uber. Nothing else — no history, no stats.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    private lateinit var tvAccessibilityState: TextView
    private lateinit var tvOverlayState: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button

    private lateinit var rgLanguage: RadioGroup
    private lateinit var etGoodThreshold: EditText
    private lateinit var etNearThreshold: EditText
    private lateinit var switchOverlay: SwitchCompat
    private lateinit var switchOcr: SwitchCompat

    private lateinit var etTest: EditText
    private lateinit var tvTestResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        setContentView(R.layout.activity_main)

        tvAccessibilityState = findViewById(R.id.tvAccessibilityState)
        tvOverlayState = findViewById(R.id.tvOverlayState)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnOverlay = findViewById(R.id.btnOverlay)
        rgLanguage = findViewById(R.id.rgLanguage)
        etGoodThreshold = findViewById(R.id.etGoodThreshold)
        etNearThreshold = findViewById(R.id.etNearThreshold)
        switchOverlay = findViewById(R.id.switchOverlay)
        switchOcr = findViewById(R.id.switchOcr)
        etTest = findViewById(R.id.etTest)
        tvTestResult = findViewById(R.id.tvTestResult)

        setupPermissions()
        setupLanguage()
        setupThresholds()
        setupOptions()
        setupTest()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ------------------------------------------------------------- permissions

    private fun setupPermissions() {
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
    }

    private fun refreshStatus() {
        val accessibilityOn = RideAccessibilityService.isEnabled(this)
        setState(tvAccessibilityState, btnAccessibility, accessibilityOn)

        val overlayOn = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        setState(tvOverlayState, btnOverlay, overlayOn)
    }

    private fun setState(label: TextView, button: Button, enabled: Boolean) {
        label.text = getString(if (enabled) R.string.state_on else R.string.state_off)
        label.setTextColor(
            ContextCompat.getColor(
                this,
                if (enabled) R.color.verdict_good else R.color.verdict_bad
            )
        )
        button.isEnabled = !enabled
    }

    // ---------------------------------------------------------------- language

    private fun setupLanguage() {
        val tag = settings.languageTag
        rgLanguage.check(if (tag == AppLocales.ENGLISH) R.id.rbEnglish else R.id.rbArabic)
        rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val next = if (checkedId == R.id.rbEnglish) AppLocales.ENGLISH else AppLocales.ARABIC
            if (next != settings.languageTag) {
                settings.languageTag = next
                AppLocales.apply(next)
            }
        }
    }

    // -------------------------------------------------------------- thresholds

    private fun setupThresholds() {
        etGoodThreshold.setText(Format.money(settings.goodAtLeast))
        etNearThreshold.setText(Format.money(settings.nearAtLeast))

        findViewById<Button>(R.id.btnSaveThresholds).setOnClickListener {
            val good = readNumber(etGoodThreshold)
            val near = readNumber(etNearThreshold)
            if (good == null || near == null || good <= 0.0 || near <= 0.0 || good <= near) {
                Toast.makeText(this, R.string.msg_invalid_thresholds, Toast.LENGTH_LONG).show()
            } else {
                settings.goodAtLeast = good
                settings.nearAtLeast = near
                Toast.makeText(this, R.string.msg_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Accepts "10", "10.5" and Arabic-Indic digits such as "١٠". */
    private fun readNumber(field: EditText): Double? {
        val raw = TextNormalizer.digitsToAscii(field.text.toString().trim())
        if (raw.isBlank()) return null
        return TextNormalizer.number(raw)
    }

    // ----------------------------------------------------------------- options

    private fun setupOptions() {
        switchOverlay.isChecked = settings.overlayEnabled
        switchOverlay.setOnCheckedChangeListener { _, checked ->
            settings.overlayEnabled = checked
        }

        switchOcr.isChecked = settings.ocrEnabled
        switchOcr.setOnCheckedChangeListener { _, checked ->
            settings.ocrEnabled = checked
        }
    }

    // -------------------------------------------------------------- offline test

    private fun setupTest() {
        findViewById<Button>(R.id.btnAnalyze).setOnClickListener {
            val lines = etTest.text.toString()
                .split("\n", "\r")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val offer = TripOfferParser.parse(lines)
            if (offer == null) {
                tvTestResult.text = getString(R.string.test_result_nothing)
                return@setOnClickListener
            }

            val rate = offer.egpPerKm
            val verdict = offer.verdict(settings.goodAtLeast, settings.nearAtLeast)
            val verdictText = when (verdict) {
                Verdict.SUITABLE -> getString(R.string.verdict_suitable)
                Verdict.NEAR -> getString(R.string.verdict_near)
                Verdict.NOT_SUITABLE, null -> getString(R.string.verdict_not_suitable)
            }

            val parts = mutableListOf(
                getString(R.string.test_result_price, Format.money(offer.priceEgp)),
                getString(R.string.test_result_distance, Format.km(offer.totalKm))
            )
            if (rate != null) {
                parts.add(getString(R.string.test_result_rate, Format.rate(rate)))
                parts.add(getString(R.string.test_result_verdict, verdictText))
            }
            tvTestResult.text = parts.joinToString("\n")
        }
    }
}
