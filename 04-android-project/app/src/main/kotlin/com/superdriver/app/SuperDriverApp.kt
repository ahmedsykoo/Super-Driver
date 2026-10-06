package com.superdriver.app

import android.app.Application
import android.content.Context
import com.superdriver.app.data.AppDatabase
import com.superdriver.app.data.SessionRepository
import com.superdriver.app.data.SettingsRepository
import com.superdriver.app.data.SupabaseAuthApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppGraph(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase = AppDatabase.create(context)
    val settingsRepo = SettingsRepository(db.settingsDao(), appScope)
    private val authApi = SupabaseAuthApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    val sessionRepo = SessionRepository(db.sessionDao(), authApi, appScope)
}

class SuperDriverApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

val Context.graph: AppGraph get() = (applicationContext as SuperDriverApp).graph
