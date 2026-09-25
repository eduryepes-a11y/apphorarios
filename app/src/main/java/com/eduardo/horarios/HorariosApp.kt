package com.eduardo.horarios

import android.app.Application
import com.eduardo.horarios.alarm.AlarmScheduler
import com.eduardo.horarios.alarm.Notifications
import com.eduardo.horarios.data.AppDatabase
import com.eduardo.horarios.data.HorariosRepository
import com.eduardo.horarios.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HorariosApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsStore
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var scheduler: AlarmScheduler
        private set
    lateinit var repository: HorariosRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        Notifications.createChannel(this)
        database = AppDatabase.get(this)
        scheduler = AlarmScheduler(this, database)
        repository = HorariosRepository(this, database, scheduler)
        // Quien ya usaba la app (versiones anteriores) no ve la bienvenida
        if (!settings.onboarded && getSharedPreferences("app", MODE_PRIVATE).getBoolean("seeded", false)) {
            settings.setOnboarded()
        }
        appScope.launch {
            repository.applyTrackingDefaultsOnce()
            repository.cleanOldOverrides()
            scheduler.rescheduleAll()
        }
    }
}
