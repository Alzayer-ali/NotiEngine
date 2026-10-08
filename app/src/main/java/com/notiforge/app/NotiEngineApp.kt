package com.notiforge.app

import android.app.Application
import com.notiforge.app.data.local.NotiDatabase
import com.notiforge.app.data.repository.TemplateRepository
import com.notiforge.app.domain.engine.NotificationRenderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotiEngineApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: NotiDatabase by lazy { NotiDatabase.getInstance(this) }
    val repository: TemplateRepository by lazy {
        TemplateRepository(database.templateDao(), database.eventLogDao())
    }

    override fun onCreate() {
        super.onCreate()
        NotificationRenderEngine.ensureChannelsCreated(this)
        appScope.launch {
            repository.ensurePresetsSeeded()
        }
    }
}
