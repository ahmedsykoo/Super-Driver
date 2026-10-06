package com.superdriver.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

data class UserProfile(val email: String, val name: String, val city: String, val birthDate: String) {
    val isComplete: Boolean get() = name.isNotBlank() && city.isNotBlank() && birthDate.isNotBlank()
}
data class Session(val profile: UserProfile)

sealed interface AuthResult {
    data object Ok : AuthResult
    data class Error(val message: String) : AuthResult
}

// Not private: SupabaseAuthApi.verifyEmailCode is a public member and can't expose a
// narrower type than itself (that's what broke the build — a real Kotlin visibility error).
data class Verified(val accessToken: String, val refreshToken: String, val userId: String)
sealed interface VerifyResult {
    data class Ok(val v: Verified) : VerifyResult
    data class Error(val message: String) : VerifyResult
}

private fun SessionEntity.toSession() = Session(UserProfile(email, name, city, birthDate))

/**
 * Talks to Supabase Auth + a `profiles` table (T7), so Ahmed can see every registered driver from the
 * Supabase side, not just on the driver's own phone. Needs three things only Ahmed can set up — until
 * they are, every call below returns AuthResult.Error, not a crash:
 *
 * 1. A Supabase project, its URL and anon key put in 04-android-project/local.properties as
 *    `supabaseUrl=` / `supabaseAnonKey=` (read in app/build.gradle.kts into BuildConfig; the file is
 *    git-ignored, never commit real keys).
 * 2. Email auth enabled, and its OTP template edited (Auth -> Email Templates -> Magic Link) to
 *    include `{{ .Token }}` in the body. Supabase's DEFAULT template is a clickable link with no
 *    typable code — without this edit the email sends fine but has no code to type into step 2.
 * 3. A `profiles` table, created once in the Supabase SQL editor:
 *      create table profiles (
 *        id uuid primary key references auth.users(id),
 *        email text, name text, city text, birth_date text,
 *        updated_at timestamptz default now()
 *      );
 *      alter table profiles enable row level security;
 *      create policy "self" on profiles for all using (auth.uid() = id) with check (auth.uid() = id);
 *
 * Not verified against a real project (no credentials here) — the REST calls follow Supabase's
 * documented Auth/PostgREST endpoints as of this writing (POST /auth/v1/otp, POST /auth/v1/verify,
 * POST /rest/v1/profiles); flag it to Ahmed if any has changed.
 */
class SupabaseAuthApi(private val baseUrl: String, private val anonKey: String) {
    private val client = OkHttpClient()
    private val jsonMedia = "application/json".toMediaType()

    suspend fun requestEmailCode(email: String): AuthResult {
        if (baseUrl.isEmpty() || anonKey.isEmpty()) return AuthResult.Error("إعداد Supabase لسه مش مكتمل")
        val body = JSONObject().put("email", email).put("create_user", true).toString()
        return when (val r = post("$baseUrl/auth/v1/otp", body)) {
            is RawResult.Ok -> AuthResult.Ok
            is RawResult.Error -> AuthResult.Error(r.message)
        }
    }

    suspend fun verifyEmailCode(email: String, code: String): VerifyResult {
        val body = JSONObject().put("type", "email").put("email", email).put("token", code).toString()
        return when (val r = post("$baseUrl/auth/v1/verify", body)) {
            is RawResult.Error -> VerifyResult.Error(r.message)
            is RawResult.Ok -> {
                val access = r.json.optString("access_token")
                val refresh = r.json.optString("refresh_token")
                val userId = r.json.optJSONObject("user")?.optString("id").orEmpty()
                if (access.isEmpty() || userId.isEmpty()) VerifyResult.Error("كود غير صحيح أو منتهي")
                else VerifyResult.Ok(Verified(access, refresh, userId))
            }
        }
    }

    suspend fun upsertProfile(accessToken: String, userId: String, profile: UserProfile): AuthResult {
        val body = JSONObject()
            .put("id", userId)
            .put("email", profile.email)
            .put("name", profile.name)
            .put("city", profile.city)
            .put("birth_date", profile.birthDate)
            .toString()
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/profiles")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates")
            .post(body.toRequestBody(jsonMedia))
            .build()
        return when (val r = run(req)) {
            is RawResult.Ok -> AuthResult.Ok
            is RawResult.Error -> AuthResult.Error(r.message)
        }
    }

    private sealed interface RawResult {
        data class Ok(val json: JSONObject) : RawResult
        data class Error(val message: String) : RawResult
    }

    private fun post(url: String, body: String): RawResult {
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMedia))
            .build()
        return run(req)
    }

    private fun run(req: Request): RawResult = try {
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    val j = JSONObject(text)
                    j.optString("msg").ifEmpty { j.optString("message") }
                }.getOrNull()
                RawResult.Error(msg?.takeIf { it.isNotEmpty() } ?: "تعذر الاتصال (${resp.code})")
            } else {
                RawResult.Ok(runCatching { JSONObject(text) }.getOrDefault(JSONObject()))
            }
        }
    } catch (e: IOException) {
        RawResult.Error("لا يوجد اتصال بالإنترنت")
    }
}

class SessionRepository(private val dao: SessionDao, private val api: SupabaseAuthApi, scope: CoroutineScope) {
    val session: StateFlow<Session?> = dao.observe()
        .map { it?.toSession() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun current(): Session? = dao.get()?.toSession()

    suspend fun requestCode(email: String): AuthResult = api.requestEmailCode(email)

    /** Verifies the emailed code and opens a session; the profile step (if needed) comes after, via saveProfile. */
    suspend fun verifyCode(email: String, code: String): AuthResult {
        val existing = dao.get()
        return when (val v = api.verifyEmailCode(email, code)) {
            is VerifyResult.Error -> AuthResult.Error(v.message)
            is VerifyResult.Ok -> {
                dao.upsert(
                    SessionEntity(
                        1, email, v.v.userId, v.v.accessToken, v.v.refreshToken,
                        name = existing?.takeIf { it.email == email }?.name.orEmpty(),
                        city = existing?.takeIf { it.email == email }?.city.orEmpty(),
                        birthDate = existing?.takeIf { it.email == email }?.birthDate.orEmpty(),
                    )
                )
                AuthResult.Ok
            }
        }
    }

    /** Saves the profile to Supabase (so Ahmed sees it) and to the local cache, using the session opened by verifyCode. */
    suspend fun saveProfile(name: String, city: String, birthDate: String): AuthResult {
        val row = dao.get() ?: return AuthResult.Error("محتاج تسجّل دخولك الأول")
        val profile = UserProfile(row.email, name, city, birthDate)
        val result = api.upsertProfile(row.accessToken, row.userId, profile)
        if (result is AuthResult.Ok) dao.upsert(row.copy(name = name, city = city, birthDate = birthDate))
        return result
    }

    suspend fun logout() { dao.clear() }
}
