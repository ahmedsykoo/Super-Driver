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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class SubStatus { TRIAL, ACTIVE, EXPIRED }
enum class SubPlan { NONE, MONTHLY, YEARLY }

data class Subscription(
    val status: SubStatus,
    val plan: SubPlan,
    val trialEndsAt: Long,
    val activeUntil: Long,
    val cachedAt: Long,
) {
    /** True while the driver may use the app (trial running or paid period not over). */
    fun isUsable(now: Long = System.currentTimeMillis()): Boolean = when (status) {
        SubStatus.TRIAL -> now < trialEndsAt
        SubStatus.ACTIVE -> activeUntil == 0L || now < activeUntil
        SubStatus.EXPIRED -> false
    }

    /** The 24h offline window from the handoff's acceptance criteria — past this, force a server check. */
    fun isCacheFresh(now: Long = System.currentTimeMillis()): Boolean = now - cachedAt < TimeUnit.HOURS.toMillis(24)

    companion object {
        val NONE = Subscription(SubStatus.EXPIRED, SubPlan.NONE, 0, 0, 0)
    }
}

sealed interface SubResult {
    data object Ok : SubResult
    data class Error(val message: String) : SubResult
}

private fun SubscriptionEntity.toModel() = Subscription(
    status = runCatching { SubStatus.valueOf(status) }.getOrDefault(SubStatus.EXPIRED),
    plan = runCatching { SubPlan.valueOf(plan) }.getOrDefault(SubPlan.NONE),
    trialEndsAt = trialEndsAtMillis,
    activeUntil = activeUntilMillis,
    cachedAt = cachedAtMillis,
)

/**
 * Talks to two Supabase tables (PostgREST), on top of the project created for T7 (see Auth.kt for the
 * URL/key setup this shares). Needs these created once, in the Supabase SQL editor:
 *
 *   create table subscriptions (
 *     user_id uuid primary key references auth.users(id),
 *     status text not null default 'trial',          -- 'trial' | 'active' | 'expired'
 *     plan text not null default '',                 -- '' | 'monthly' | 'yearly'
 *     trial_ends_at timestamptz not null,
 *     active_until timestamptz,
 *     updated_at timestamptz default now()
 *   );
 *   alter table subscriptions enable row level security;
 *   create policy "self_read" on subscriptions for select using (auth.uid() = user_id);
 *   create policy "self_insert_trial" on subscriptions for insert with check (auth.uid() = user_id);
 *   -- deliberately NO update policy for the client: a driver must never be able to grant their own
 *   -- subscription. Ahmed flips status/active_until to 'active' by hand in the Supabase table editor
 *   -- after approving a payment — matches the handoff's "تفعيل يدوي" rule for Vodafone Cash.
 *
 *   create table vodafone_cash_payments (
 *     id uuid primary key default gen_random_uuid(),
 *     user_id uuid references auth.users(id) not null,
 *     plan text not null,              -- 'monthly' | 'yearly'
 *     phone text not null,
 *     transaction_ref text not null,
 *     status text not null default 'pending',  -- 'pending' | 'approved' | 'rejected'
 *     created_at timestamptz default now()
 *   );
 *   alter table vodafone_cash_payments enable row level security;
 *   create policy "self_insert" on vodafone_cash_payments for insert with check (auth.uid() = user_id);
 *   create policy "self_read" on vodafone_cash_payments for select using (auth.uid() = user_id);
 *
 * Paymob and Google Play Billing (the other two payment methods in the handoff) are NOT implemented —
 * both need accounts/credentials only Ahmed can create (a Paymob merchant account; a Play Console
 * listing for Billing to even exist). SubscriptionActivity shows them as "قريبًا" until those exist,
 * the same pattern as Supabase's own setup in Auth.kt.
 *
 * Also unimplemented: uploading the Vodafone Cash transfer receipt image. The driver only submits the
 * transaction reference number as text; Ahmed cross-checks it against the real transfer by hand. Wiring
 * an actual image upload needs a Supabase Storage bucket, which isn't set up.
 */
class SupabaseRestApi(private val baseUrl: String, private val anonKey: String) {
    private val client = OkHttpClient()
    private val jsonMedia = "application/json".toMediaType()

    suspend fun getSubscription(accessToken: String, userId: String): SubscriptionEntity? {
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/subscriptions?user_id=eq.$userId&select=*")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        return when (val r = run(req)) {
            is RawResult.Error -> null
            is RawResult.Ok -> {
                val arr = runCatching { JSONArray(r.body) }.getOrNull() ?: return null
                if (arr.length() == 0) return null
                val o = arr.getJSONObject(0)
                SubscriptionEntity(
                    id = 1,
                    status = o.optString("status", "trial"),
                    plan = o.optString("plan", ""),
                    trialEndsAtMillis = parseTimestamp(o.optString("trial_ends_at")),
                    activeUntilMillis = parseTimestamp(o.optString("active_until")),
                    cachedAtMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    suspend fun startTrial(accessToken: String, userId: String, trialEndsAtIso: String): SubResult {
        val body = JSONObject()
            .put("user_id", userId)
            .put("status", "trial")
            .put("plan", "")
            .put("trial_ends_at", trialEndsAtIso)
            .toString()
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/subscriptions")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=ignore-duplicates") // already has a row: fine, keep it
            .post(body.toRequestBody(jsonMedia))
            .build()
        return when (run(req)) {
            is RawResult.Ok -> SubResult.Ok
            is RawResult.Error -> SubResult.Ok // trial row may already exist (ignore-duplicates races); never block login on this
        }
    }

    suspend fun submitVodafoneCashPayment(accessToken: String, userId: String, plan: String, phone: String, transactionRef: String): SubResult {
        val body = JSONObject()
            .put("user_id", userId)
            .put("plan", plan)
            .put("phone", phone)
            .put("transaction_ref", transactionRef)
            .toString()
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/vodafone_cash_payments")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMedia))
            .build()
        return when (val r = run(req)) {
            is RawResult.Ok -> SubResult.Ok
            is RawResult.Error -> SubResult.Error(r.message)
        }
    }

    private fun parseTimestamp(iso: String): Long =
        if (iso.isEmpty()) 0L else runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)

    private sealed interface RawResult {
        data class Ok(val body: String) : RawResult
        data class Error(val message: String) : RawResult
    }

    private fun run(req: Request): RawResult = try {
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    val j = JSONObject(text)
                    j.optString("message").ifEmpty { j.optString("msg") }
                }.getOrNull()
                RawResult.Error(msg?.takeIf { it.isNotEmpty() } ?: "تعذر الاتصال (${resp.code})")
            } else {
                RawResult.Ok(text)
            }
        }
    } catch (e: IOException) {
        RawResult.Error("لا يوجد اتصال بالإنترنت")
    }
}

class SubscriptionRepository(
    private val dao: SubscriptionDao,
    private val api: SupabaseRestApi,
    private val sessionDao: SessionDao,
    scope: CoroutineScope,
) {
    val subscription: StateFlow<Subscription> = dao.observe()
        .map { it?.toModel() ?: Subscription.NONE }
        .stateIn(scope, SharingStarted.Eagerly, Subscription.NONE)

    suspend fun current(): Subscription = dao.get()?.toModel() ?: Subscription.NONE

    /** Called once right after registration completes — a no-op if a row already exists. */
    suspend fun ensureTrialStarted() {
        val session = sessionDao.get() ?: return
        val trialEndsAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
        val iso = java.time.Instant.ofEpochMilli(trialEndsAt).toString()
        api.startTrial(session.accessToken, session.userId, iso)
        refresh()
    }

    /** Pulls the real status from Supabase and refreshes the local cache (source of truth stays the server). */
    suspend fun refresh(): SubResult {
        val session = sessionDao.get() ?: return SubResult.Error("محتاج تسجّل دخولك الأول")
        val remote = api.getSubscription(session.accessToken, session.userId)
            ?: return SubResult.Error("تعذر التحقق من الاشتراك")
        dao.upsert(remote)
        return SubResult.Ok
    }

    suspend fun submitVodafoneCash(plan: SubPlan, phone: String, transactionRef: String): SubResult {
        val session = sessionDao.get() ?: return SubResult.Error("محتاج تسجّل دخولك الأول")
        val planStr = when (plan) { SubPlan.MONTHLY -> "monthly"; SubPlan.YEARLY -> "yearly"; SubPlan.NONE -> "" }
        return api.submitVodafoneCashPayment(session.accessToken, session.userId, planStr, phone, transactionRef)
    }

    suspend fun clearCache() { dao.clear() }
}
