package com.notiforge.app.ipc

import android.content.Context
import android.content.Intent
import com.notiforge.app.NotiEngineApp
import com.notiforge.app.data.local.NotiDatabase
import com.notiforge.app.data.repository.TemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Outgoing broadcast dispatcher firing `com.notiforge.action.EVENT` back to MacroDroid
 * or any automation tool listening for NotiEngine callbacks.
 */
object MacroDroidEventDispatcher {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun dispatchActionClicked(
        context: Context,
        notificationTag: String,
        actionId: String
    ) {
        val appContext = context.applicationContext
        val safeTag = notificationTag.take(NotiContract.MAX_ID_LENGTH)
        val safeActionId = actionId.take(NotiContract.MAX_ACTION_ID_LENGTH)
        val intent = Intent(NotiContract.ACTION_EVENT).apply {
            putExtra(NotiContract.Extras.ID, safeTag)
            putExtra(NotiContract.Extras.EVENT_TYPE, NotiContract.EventTypes.ACTION_CLICKED)
            putExtra(NotiContract.Extras.ACTION_ID, safeActionId)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        appContext.sendBroadcast(intent)

        logOutgoingEvent(
            context = appContext,
            notificationTag = safeTag,
            summary = "EVENT=${NotiContract.EventTypes.ACTION_CLICKED} | action_id=$safeActionId"
        )
    }

    fun dispatchInputSubmitted(
        context: Context,
        notificationTag: String,
        userInput: String
    ) {
        val appContext = context.applicationContext
        val safeTag = notificationTag.take(NotiContract.MAX_ID_LENGTH)
        val safeInput = userInput.take(NotiContract.MAX_BODY_LENGTH)
        val intent = Intent(NotiContract.ACTION_EVENT).apply {
            putExtra(NotiContract.Extras.ID, safeTag)
            putExtra(NotiContract.Extras.EVENT_TYPE, NotiContract.EventTypes.INPUT_SUBMITTED)
            putExtra(NotiContract.Extras.USER_INPUT, safeInput)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        appContext.sendBroadcast(intent)

        logOutgoingEvent(
            context = appContext,
            notificationTag = safeTag,
            summary = "EVENT=${NotiContract.EventTypes.INPUT_SUBMITTED} | user_input=\"$safeInput\""
        )
    }

    fun dispatchTimerFinished(
        context: Context,
        notificationTag: String
    ) {
        val appContext = context.applicationContext
        val safeTag = notificationTag.take(NotiContract.MAX_ID_LENGTH)
        val intent = Intent(NotiContract.ACTION_EVENT).apply {
            putExtra(NotiContract.Extras.ID, safeTag)
            putExtra(NotiContract.Extras.EVENT_TYPE, NotiContract.EventTypes.TIMER_FINISHED)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        appContext.sendBroadcast(intent)

        logOutgoingEvent(
            context = appContext,
            notificationTag = safeTag,
            summary = "EVENT=${NotiContract.EventTypes.TIMER_FINISHED}"
        )
    }

    private fun logOutgoingEvent(
        context: Context,
        notificationTag: String,
        summary: String
    ) {
        val appContext = context.applicationContext
        ioScope.launch {
            val repository = (appContext as? NotiEngineApp)?.repository ?: run {
                val db = NotiDatabase.getInstance(appContext)
                TemplateRepository(db.templateDao(), db.eventLogDao())
            }
            repository.logEvent(
                direction = "OUTGOING",
                action = NotiContract.ACTION_EVENT,
                notificationTag = notificationTag,
                summary = summary
            )
        }
    }
}
