package com.notiforge.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.notiforge.app.NotiEngineApp
import com.notiforge.app.data.local.NotiDatabase
import com.notiforge.app.data.repository.TemplateRepository
import com.notiforge.app.domain.engine.NotificationRenderEngine
import com.notiforge.app.domain.model.NotiCommand
import com.notiforge.app.domain.model.NotiPayload
import com.notiforge.app.domain.model.ProgressMode
import com.notiforge.app.ipc.NotiContract
import com.notiforge.app.service.TimerForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Exported BroadcastReceiver acting as the primary IPC entry point from MacroDroid
 * and external automation tools.
 *
 * Handles:
 * - `com.notiforge.action.SHOW`
 * - `com.notiforge.action.SHOW_NOTIFICATION`
 * - `com.notiforge.action.UPDATE`
 * - `com.notiforge.action.CANCEL`
 */
class NotiReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val hasJsonPayload = NotiContract.hasJsonExtra(intent)
        val command = if (hasJsonPayload) {
            NotiContract.parseJsonIntent(intent)
        } else {
            NotiContract.parseCommand(intent)
        } ?: return

        val appContext = context.applicationContext
        val actionName = intent.action ?: NotiContract.ACTION_SHOW
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                withTimeoutOrNull(BROADCAST_TIMEOUT_MS) {
                    NotificationRenderEngine.ensureChannelsCreated(appContext)
                    val repository = (appContext as? NotiEngineApp)?.repository ?: run {
                        val db = NotiDatabase.getInstance(appContext)
                        TemplateRepository(db.templateDao(), db.eventLogDao())
                    }

                    when (command) {
                        is NotiCommand.Cancel -> {
                            repository.logEvent(
                                direction = "INCOMING",
                                action = NotiContract.ACTION_CANCEL,
                                notificationTag = command.id,
                                summary = "Cancelled notification & stopped active timer"
                            )
                            TimerForegroundService.stopTimer(appContext, command.id, cancelNotification = true)
                            NotificationRenderEngine.cancelNotification(appContext, command.id)
                        }

                        is NotiCommand.ShowOrUpdate -> {
                            val existingPayload = NotificationRenderEngine.getActivePayload(appContext, command.id)
                            val resolvedPayload = if (existingPayload != null && (command.isUpdate || command.templateSlug == null)) {
                                NotificationRenderEngine.mergeUpdatePayload(existingPayload, command)
                            } else {
                                val template = repository.getTemplateBySlugOrFallback(command.templateSlug)
                                NotiPayload.fromTemplateAndCommand(template, command)
                            }

                            val payloadSource = if (hasJsonPayload) "json" else "extras"
                            repository.logEvent(
                                direction = "INCOMING",
                                action = actionName,
                                notificationTag = resolvedPayload.notificationTag,
                                summary = "source=$payloadSource | template=${resolvedPayload.templateSlug} | mode=${resolvedPayload.progressMode.wireValue} | title=\"${resolvedPayload.title}\""
                            )

                            if (resolvedPayload.progressMode == ProgressMode.AUTO_TIMER) {
                                TimerForegroundService.startOrUpdateTimer(appContext, resolvedPayload)
                            } else {
                                TimerForegroundService.stopTimer(
                                    appContext,
                                    resolvedPayload.notificationTag,
                                    cancelNotification = false
                                )
                                NotificationRenderEngine.showNotification(
                                    context = appContext,
                                    payload = resolvedPayload,
                                    isOngoingTimer = false
                                )
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Prevent malformed external broadcasts from crashing the receiver process
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    companion object {
        private const val BROADCAST_TIMEOUT_MS = 8_500L
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
