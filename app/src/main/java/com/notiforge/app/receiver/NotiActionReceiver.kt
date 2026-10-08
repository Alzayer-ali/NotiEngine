package com.notiforge.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.notiforge.app.domain.engine.NotificationRenderEngine
import com.notiforge.app.domain.model.ProgressMode
import com.notiforge.app.ipc.MacroDroidEventDispatcher
import com.notiforge.app.ipc.NotiContract
import com.notiforge.app.service.TimerForegroundService

/**
 * Handles PendingIntent callbacks triggered directly from interactive elements inside
 * NotiEngine notifications:
 * 1. Action Button clicks (`ACTION_INTERNAL_BUTTON_CLICK`)
 * 2. Inline `RemoteInput` text submissions (`ACTION_INTERNAL_REMOTE_INPUT`)
 * 3. User swipe-to-dismiss / clear events (`ACTION_INTERNAL_NOTIFICATION_DISMISSED`)
 */
class NotiActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val appContext = context.applicationContext
        val notificationTag = try {
            intent.getStringExtra(NotiContract.Extras.ID)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        } ?: return

        NotificationRenderEngine.ensureChannelsCreated(appContext)

        when (intent.action) {
            NotiContract.ACTION_INTERNAL_NOTIFICATION_DISMISSED -> {
                // User swiped away or cleared the notification: immediately stop any running
                // countdown timer in TimerForegroundService so it does not resurrect on the next tick.
                TimerForegroundService.stopTimer(
                    context = appContext,
                    notificationTag = notificationTag,
                    cancelNotification = true
                )
                NotificationRenderEngine.removeActivePayload(appContext, notificationTag)
            }

            NotiContract.ACTION_INTERNAL_BUTTON_CLICK -> {
                val actionId = intent.getStringExtra(NotiContract.Extras.ACTION_ID)
                    ?.trim()
                    ?.take(NotiContract.MAX_ACTION_ID_LENGTH)
                    ?.takeIf { it.isNotEmpty() }
                    ?: "btn_default"

                MacroDroidEventDispatcher.dispatchActionClicked(
                    context = appContext,
                    notificationTag = notificationTag,
                    actionId = actionId
                )

                // If the notification payload has autoDismiss enabled (and is not an active countdown timer),
                // dismiss on action tap; otherwise keep it interactive.
                val existing = NotificationRenderEngine.getActivePayload(appContext, notificationTag)
                val isRunningTimer = existing?.progressMode == ProgressMode.AUTO_TIMER && !existing.isTimerCompleted
                if (existing != null && existing.autoDismiss && !isRunningTimer) {
                    TimerForegroundService.stopTimer(appContext, notificationTag, cancelNotification = true)
                    NotificationRenderEngine.cancelNotification(appContext, notificationTag)
                }
            }

            NotiContract.ACTION_INTERNAL_REMOTE_INPUT -> {
                val remoteInputBundle = try {
                    RemoteInput.getResultsFromIntent(intent)
                } catch (_: Throwable) {
                    null
                }
                val capturedText = remoteInputBundle
                    ?.getCharSequence(NotiContract.REMOTE_INPUT_RESULT_KEY)
                    ?.toString()
                    ?.trim()
                    ?.take(NotiContract.MAX_BODY_LENGTH)
                    .orEmpty()

                if (capturedText.isNotEmpty()) {
                    MacroDroidEventDispatcher.dispatchInputSubmitted(
                        context = appContext,
                        notificationTag = notificationTag,
                        userInput = capturedText
                    )
                }

                // Immediately update or dismiss the notification so the system RemoteInput
                // loading spinner completes cleanly.
                val existing = NotificationRenderEngine.getActivePayload(appContext, notificationTag)
                if (existing != null) {
                    val isRunningTimer = existing.progressMode == ProgressMode.AUTO_TIMER && !existing.isTimerCompleted
                    if (existing.autoDismiss && !isRunningTimer) {
                        val confirmedPayload = existing.copy(
                            statusBadge = "SENT",
                            replyStatusText = "✓ Sent to MacroDroid: \"$capturedText\"",
                            showRemoteInput = false
                        )
                        NotificationRenderEngine.showNotification(
                            context = appContext,
                            payload = confirmedPayload,
                            isOngoingTimer = false
                        )
                        TimerForegroundService.stopTimer(appContext, notificationTag, cancelNotification = true)
                        NotificationRenderEngine.cancelNotification(appContext, notificationTag)
                    } else {
                        val updatedPayload = existing.copy(
                            statusBadge = "CAPTURED",
                            replyStatusText = "✓ Sent: \"$capturedText\""
                        )
                        if (isRunningTimer) {
                            TimerForegroundService.startOrUpdateTimer(appContext, updatedPayload)
                        } else {
                            NotificationRenderEngine.showNotification(
                                context = appContext,
                                payload = updatedPayload,
                                isOngoingTimer = false
                            )
                        }
                    }
                } else {
                    TimerForegroundService.stopTimer(appContext, notificationTag, cancelNotification = true)
                    NotificationRenderEngine.cancelNotification(appContext, notificationTag)
                }
            }
        }
    }
}
