package com.eduardo.horarios

import android.app.Application
import com.eduardo.horarios.alarm.AlarmScheduler
import com.eduardo.horarios.alarm.DailyWork
import com.eduardo.horarios.alarm.Notifications
import com.eduardo.horarios.alarm.WeeklySummary
import com.eduardo.horarios.data.AppDatabase
import com.eduardo.horarios.data.HorariosRepository
import com.eduardo.horarios.data.SettingsStore
import com.eduardo.horarios.data.WeekCalibration
import com.eduardo.horarios.pro.Pro
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
        WeeklySummary.createChannel(this)
        // Antes de nada: las letras de las semanas alternas (A, B…) según las ajustó el usuario
        WeekCalibration.load(this)
        database = AppDatabase.get(this)
        scheduler = AlarmScheduler(this, database)
        repository = HorariosRepository(this, database, scheduler)
        // Horarios Pro: lo último que se supo y la tienda (en GitHub, todo desbloqueado)
        Pro.init(this)
        appScope.launch {
            // Al empezar o terminar Pro: bloquear o desbloquear horarios y reprogramar avisos
            Pro.state.collect { repository.enforceFreeLimits() }
        }
        // Quien ya usaba la app (versiones anteriores) no ve la bienvenida
        if (!settings.onboarded && getSharedPreferences("app", MODE_PRIVATE).getBoolean("seeded", false)) {
            settings.setOnboarded()
        }
        appScope.launch {
            repository.applyTrackingDefaultsOnce()
            repository.cleanOldOverrides()
            // Cambio automático de horario, copia del día y avisos
            DailyWork.run(this@HorariosApp)
            WeeklySummary.schedule(this@HorariosApp)
        }
    }
}
