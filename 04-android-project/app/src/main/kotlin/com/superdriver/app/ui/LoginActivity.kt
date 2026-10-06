package com.superdriver.app.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import com.superdriver.app.R
import com.superdriver.app.data.AuthResult
import kotlinx.coroutines.launch

/**
 * First screen the app shows (before the Accessibility/overlay onboarding — Ahmed's decision). Three
 * steps in one Activity, same state-machine style as OnboardingActivity:
 *   1. email -> "إرسال الكود" (Supabase email OTP)
 *   2. code -> "تأكيد" (verifies against Supabase, opens a session)
 *   3. name/city/birth date, shown only while the profile is incomplete -> saved to Supabase + cached locally
 * Each network call can fail (no Supabase project configured yet, no internet, wrong code) — every
 * path below shows a message instead of crashing or silently continuing.
 */
class LoginActivity : BaseActivity() {
    private enum class Step { EMAIL, CODE, PROFILE }

    private var step = Step.EMAIL
    private var email = ""
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            val existing = appGraph.sessionRepo.current()
            if (existing != null) {
                if (existing.profile.isComplete) { finish(); return@launch } // already fully set up
                email = existing.profile.email
                step = Step.PROFILE
            }
            render()
        }
    }

    private fun render(error: String? = null) {
        when (step) {
            Step.EMAIL -> renderEmail(error)
            Step.CODE -> renderCode(error)
            Step.PROFILE -> renderProfile(error)
        }
    }

    private fun renderEmail(error: String?) {
        val emailRow = labeledField(getString(R.string.login_email_hint), inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, prefill = email)
        val note = formNote(error ?: "")
        val page = setPage(
            brandHeader(),
            heading(getString(R.string.login_email_title)),
            card(emailRow, note),
        )
        page.addView(primaryButton(getString(R.string.login_send_code)) {
            if (busy) return@primaryButton
            val typed = (emailRow.tag as EditText).text.toString().trim()
            if (typed.isEmpty() || !typed.contains("@")) { render(getString(R.string.login_error_empty_email)); return@primaryButton }
            email = typed
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
                val result = appGraph.sessionRepo.verifyCode(email, code)
                busy = false
                when (result) {
                    is AuthResult.Ok -> { step = Step.PROFILE; render() }
                    is AuthResult.Error -> render(result.message)
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
        page.addView(outlineButton(getString(R.string.login_change_email)) { step = Step.EMAIL; render() })
    }

    private fun renderProfile(error: String?) {
        val nameRow = labeledField(getString(R.string.login_name_hint))
        val cityRow = labeledField(getString(R.string.login_city_hint))
        val birthRow = labeledField(getString(R.string.login_birth_hint), inputType = InputType.TYPE_CLASS_DATETIME)
        val note = formNote(error ?: "")
        val page = setPage(
            brandHeader(),
            heading(getString(R.string.login_profile_title)),
            card(nameRow, cityRow, birthRow, note),
        )
        page.addView(primaryButton(getString(R.string.login_save_continue)) {
            if (busy) return@primaryButton
            val name = (nameRow.tag as EditText).text.toString().trim()
            val city = (cityRow.tag as EditText).text.toString().trim()
            val birth = (birthRow.tag as EditText).text.toString().trim()
            if (name.isEmpty() || city.isEmpty() || birth.isEmpty()) { render(getString(R.string.login_error_empty_profile)); return@primaryButton }
            busy = true
            scope.launch {
                val result = appGraph.sessionRepo.saveProfile(name, city, birth)
                busy = false
                when (result) {
                    is AuthResult.Ok -> finish()
                    is AuthResult.Error -> render(result.message)
                }
            }
        })
    }
}
