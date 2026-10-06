package com.superdriver.app.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.superdriver.app.R
import com.superdriver.app.data.AuthResult
import kotlinx.coroutines.launch

/**
 * First screen the app shows (before the Accessibility/overlay onboarding — Ahmed's decision). Four
 * steps, same state-machine style as OnboardingActivity. Ahmed's requirement: nothing in the app is
 * reachable until the emailed code is confirmed — the form below only ever *collects* data; it is
 * saved to Supabase (and the local session opens) exclusively inside the CODE step's success path.
 *   1. SPLASH — logo + "تسجيل", tap to continue.
 *   2. FORM — email + name + city + birth date together, one screen -> "إرسال الكود" (Supabase email OTP).
 *   3. CODE — the emailed code -> "تأكيد": verifies, THEN saves the profile. Either failing leaves the
 *      app exactly as before (no partial session, matching rule 7 in the handoff: no partial grants).
 *   4. RESUME_PROFILE — only reached if the app was closed after a successful code verify but before the
 *      profile save finished (e.g. connection drop): the session already exists, so this just retries
 *      the save, with no second code needed.
 */
class LoginActivity : BaseActivity() {
    private enum class Step { SPLASH, FORM, CODE, RESUME_PROFILE }

    private var step = Step.SPLASH
    private var email = ""
    private var name = ""
    private var city = ""
    private var birth = ""
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            val existing = appGraph.sessionRepo.current()
            if (existing != null) {
                if (existing.profile.isComplete) { finish(); return@launch } // already fully set up
                email = existing.profile.email
                step = Step.RESUME_PROFILE
            }
            render()
        }
    }

    private fun render(error: String? = null) {
        when (step) {
            Step.SPLASH -> renderSplash()
            Step.FORM -> renderForm(error)
            Step.CODE -> renderCode(error)
            Step.RESUME_PROFILE -> renderProfileFields(error)
        }
    }

    private fun renderSplash() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Theme.BG)
            setPadding(dp(24), 0, dp(24), 0)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(TextView(this@LoginActivity).apply {
                text = "SD"
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(typeface, Typeface.BOLD)
                textSize = 26f
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(22).toFloat()
                    setColor(Theme.TEAL_DARK)
                }
                layoutParams = LinearLayout.LayoutParams(dp(84), dp(84)).apply { bottomMargin = dp(16) }
            })
            addView(TextView(this@LoginActivity).apply {
                text = getString(R.string.app_name); setTextColor(Theme.INK); setTypeface(typeface, Typeface.BOLD); textSize = 20f
            })
            addView(TextView(this@LoginActivity).apply {
                text = getString(R.string.login_splash_label)
                setTextColor(Theme.TEAL); setTypeface(typeface, Typeface.BOLD); textSize = 14f
                setPadding(0, dp(10), 0, 0)
            })
        }
        col.addView(top)
        col.addView(primaryButton(getString(R.string.login_splash_continue)) { step = Step.FORM; render() }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(40) }
        })
        setContentView(col)
    }

    private fun renderForm(error: String?) {
        val emailRow = labeledField(getString(R.string.login_email_hint), inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, prefill = email)
        val nameRow = labeledField(getString(R.string.login_name_hint), prefill = name)
        val cityRow = labeledField(getString(R.string.login_city_hint), prefill = city)
        val birthRow = labeledField(getString(R.string.login_birth_hint), inputType = InputType.TYPE_CLASS_DATETIME, prefill = birth)
        val note = formNote(error ?: "")
        val page = setPage(
            brandHeader(),
            heading(getString(R.string.login_form_title)),
            body(getString(R.string.login_form_note)),
            card(emailRow, nameRow, cityRow, birthRow, note),
        )
        page.addView(primaryButton(getString(R.string.login_send_code)) {
            if (busy) return@primaryButton
            val typedEmail = (emailRow.tag as EditText).text.toString().trim()
            val typedName = (nameRow.tag as EditText).text.toString().trim()
            val typedCity = (cityRow.tag as EditText).text.toString().trim()
            val typedBirth = (birthRow.tag as EditText).text.toString().trim()
            if (typedEmail.isEmpty() || !typedEmail.contains("@")) { render(getString(R.string.login_error_empty_email)); return@primaryButton }
            if (typedName.isEmpty() || typedCity.isEmpty() || typedBirth.isEmpty()) { render(getString(R.string.login_error_empty_profile)); return@primaryButton }
            email = typedEmail; name = typedName; city = typedCity; birth = typedBirth
            busy = true
            scope.launch {
                val result = appGraph.sessionRepo.requestCode(email)
                busy = false
                when (result) {
                    is AuthResult.Ok -> { step = Step.CODE; render() }
                    is AuthResult.Error -> render(result.message)
                }
            }
        })
    }

    private fun renderCode(error: String?) {
        val codeRow = labeledField(getString(R.string.login_code_hint), inputType = InputType.TYPE_CLASS_NUMBER)
        val note = formNote(error ?: "")
        val page = setPage(
            brandHeader(),
            heading(getString(R.string.login_code_title)),
            card(
                body(getString(R.string.login_code_sent_to, email)),
                codeRow,
                note,
            ),
        )
        page.addView(primaryButton(getString(R.string.login_verify)) {
            if (busy) return@primaryButton
            val code = (codeRow.tag as EditText).text.toString().trim()
            if (code.isEmpty()) { render(getString(R.string.login_error_empty_code)); return@primaryButton }
            busy = true
            scope.launch {
                // Nothing in the app is reachable before this succeeds — verify, then save the
                // profile collected back in FORM. Either step failing leaves no session behind.
                val verified = appGraph.sessionRepo.verifyCode(email, code)
                if (verified is AuthResult.Error) { busy = false; render(verified.message); return@launch }
                val saved = appGraph.sessionRepo.saveProfile(name, city, birth)
                busy = false
                when (saved) {
                    is AuthResult.Ok -> { appGraph.subscriptionRepo.ensureTrialStarted(); finish() }
                    is AuthResult.Error -> render(saved.message) // session exists; next launch resumes at RESUME_PROFILE
                }
            }
        })
        page.addView(outlineButton(getString(R.string.login_resend)) {
            if (busy) return@outlineButton
            busy = true
            scope.launch {
                val result = appGraph.sessionRepo.requestCode(email)
                busy = false
                render((result as? AuthResult.Error)?.message)
            }
        })
        page.addView(outlineButton(getString(R.string.login_change_email)) { step = Step.FORM; render() })
    }

    /** Only reached via RESUME_PROFILE — the session already exists, no code needed again. */
    private fun renderProfileFields(error: String?) {
        val nameRow = labeledField(getString(R.string.login_name_hint), prefill = name)
        val cityRow = labeledField(getString(R.string.login_city_hint), prefill = city)
        val birthRow = labeledField(getString(R.string.login_birth_hint), inputType = InputType.TYPE_CLASS_DATETIME, prefill = birth)
        val note = formNote(error ?: "")
        val page = setPage(
            brandHeader(),
            heading(getString(R.string.login_profile_title)),
            body(getString(R.string.login_resume_note, email)),
            card(nameRow, cityRow, birthRow, note),
        )
        page.addView(primaryButton(getString(R.string.login_save_continue)) {
            if (busy) return@primaryButton
            val typedName = (nameRow.tag as EditText).text.toString().trim()
            val typedCity = (cityRow.tag as EditText).text.toString().trim()
            val typedBirth = (birthRow.tag as EditText).text.toString().trim()
            if (typedName.isEmpty() || typedCity.isEmpty() || typedBirth.isEmpty()) { render(getString(R.string.login_error_empty_profile)); return@primaryButton }
            busy = true
            scope.launch {
                val result = appGraph.sessionRepo.saveProfile(typedName, typedCity, typedBirth)
                busy = false
                when (result) {
                    is AuthResult.Ok -> { appGraph.subscriptionRepo.ensureTrialStarted(); finish() }
                    is AuthResult.Error -> render(result.message)
                }
            }
        })
    }
}
