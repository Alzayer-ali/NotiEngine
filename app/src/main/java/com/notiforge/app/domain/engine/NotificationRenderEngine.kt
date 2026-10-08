package com.notiforge.app.domain.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.notiforge.app.MainActivity
import com.notiforge.app.R
import com.notiforge.app.domain.model.NotiAction
import com.notiforge.app.domain.model.NotiCommand
import com.notiforge.app.domain.model.NotiPayload
import com.notiforge.app.domain.model.ProgressMode
import com.notiforge.app.ipc.NotiContract
import com.notiforge.app.receiver.NotiActionReceiver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Core Notification Render Engine responsible for:
 * - Creating NotificationChannels
 * - Building Day/Night adaptive custom `RemoteViews` (collapsed + expanded)
 * - Wiring `FLAG_IMMUTABLE` PendingIntents for action buttons
 * - Wiring `FLAG_MUTABLE` explicit PendingIntents for inline `RemoteInput`
 * - Managing active payload state so partial `UPDATE` commands and `RemoteInput` replies preserve layout state
 */
object NotificationRenderEngine {

    private const val PREFS_ACTIVE_PAYLOADS = "notiengine_active_payloads"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun ensureChannelsCreated(context: Context) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val defaultChannel = NotificationChannel(
            NotiContract.CHANNEL_DEFAULT_ID,
            appContext.getString(R.string.channel_default_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = appContext.getString(R.string.channel_default_desc)
            enableVibration(true)
        }

        val timerChannel = NotificationChannel(
            NotiContract.CHANNEL_TIMER_ID,
            appContext.getString(R.string.channel_timer_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = appContext.getString(R.string.channel_timer_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val alertChannel = NotificationChannel(
            NotiContract.CHANNEL_HIGH_ALERT_ID,
            appContext.getString(R.string.channel_alert_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = appContext.getString(R.string.channel_alert_desc)
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(defaultChannel, timerChannel, alertChannel))
    }

    /**
     * Generates a deterministic, collision-resistant positive request code combining
     * the notification tag and a distinct action/intent slot identifier.
     */
    fun uniqueRequestCode(notificationTag: String, slot: String): Int {
        var result = 17
        result = 31 * result + notificationTag.hashCode()
        result = 31 * result + slot.hashCode()
        return (result and 0x7FFFFFFF).coerceAtLeast(1)
    }

    /**
     * Builds a complete [Notification] using custom collapsed and expanded [RemoteViews]
     * styled with adaptive Day/Night contrast, swipe-to-dismiss `deleteIntent`, and optional inline [RemoteInput].
     */
    fun buildNotification(
        context: Context,
        payload: NotiPayload,
        isOngoingTimer: Boolean = false
    ): Notification {
        val appContext = context.applicationContext
        ensureChannelsCreated(appContext)

        val channelId = when {
            isOngoingTimer || payload.progressMode == ProgressMode.AUTO_TIMER -> NotiContract.CHANNEL_TIMER_ID
            payload.templateSlug == "minimal_alert" -> NotiContract.CHANNEL_HIGH_ALERT_ID
            else -> NotiContract.CHANNEL_DEFAULT_ID
        }

        val accentColorInt = resolveAccentColor(appContext, payload.accentColorHex, payload.useDynamicColor)
        val iconRes = resolveSmallIconRes(payload.iconName)

        val collapsedView = buildCollapsedRemoteViews(appContext, payload)
        val expandedView = buildExpandedRemoteViews(appContext, payload, iconRes, accentColorInt)

        val launchAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotiContract.Extras.ID, payload.notificationTag)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            uniqueRequestCode(payload.notificationTag, "content_click"),
            launchAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // DeleteIntent ensures swiping away an active timer or notification immediately stops
        // TimerForegroundService and cleans up state instead of resurrecting on the next tick.
        val dismissIntent = Intent(appContext, NotiActionReceiver::class.java).apply {
            action = NotiContract.ACTION_INTERNAL_NOTIFICATION_DISMISSED
            setPackage(appContext.packageName)
            putExtra(NotiContract.Extras.ID, payload.notificationTag)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            appContext,
            uniqueRequestCode(payload.notificationTag, "delete_dismiss"),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val safeTitle = payload.title.ifBlank { appContext.getString(R.string.app_name) }
        val safeBody = if (payload.isTimerCompleted && payload.finishText.isNotBlank()) {
            payload.finishText
        } else {
            payload.body
        }

        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(safeTitle)
            .setContentText(safeBody)
            .setColor(accentColorInt)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setOnlyAlertOnce(isOngoingTimer || payload.progressMode != ProgressMode.NONE || payload.replyStatusText != null)
            .setOngoing(isOngoingTimer && !payload.isTimerCompleted)
            .setAutoCancel(!isOngoingTimer && payload.autoDismiss)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(
                if (channelId == NotiContract.CHANNEL_HIGH_ALERT_ID) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )

        // Attach inline RemoteInput if enabled
        if (payload.showRemoteInput) {
            val replyLabel = payload.inputHint.ifBlank { appContext.getString(R.string.noti_reply_action_label) }
            val remoteInput = RemoteInput.Builder(NotiContract.REMOTE_INPUT_RESULT_KEY)
                .setLabel(replyLabel)
                .build()

            val replyIntent = Intent(appContext, NotiActionReceiver::class.java).apply {
                action = NotiContract.ACTION_INTERNAL_REMOTE_INPUT
                setPackage(appContext.packageName)
                putExtra(NotiContract.Extras.ID, payload.notificationTag)
            }

            // RemoteInput requires FLAG_MUTABLE on Android 12+ (API 31+) so the system can attach user input
            val mutableFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val replyPendingIntent = PendingIntent.getBroadcast(
                appContext,
                uniqueRequestCode(payload.notificationTag, "remote_input"),
                replyIntent,
                mutableFlags
            )

            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_noti_edit_note,
                replyLabel,
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(false)
                .build()

            builder.addAction(replyAction)
        }

        return builder.build()
    }

    /**
     * Renders and posts the notification if `POST_NOTIFICATIONS` permission is granted (or API < 33).
     * Also persists the active payload for subsequent partial updates or RemoteInput callbacks.
     */
    fun showNotification(
        context: Context,
        payload: NotiPayload,
        isOngoingTimer: Boolean = false
    ): Boolean {
        val appContext = context.applicationContext
        ensureChannelsCreated(appContext)
        saveActivePayload(appContext, payload)
        if (!canPostNotifications(appContext)) {
            return false
        }
        val notification = buildNotification(appContext, payload, isOngoingTimer)
        val resolvedId = NotiPayload.notificationIdForTag(payload.notificationTag)
        return try {
            NotificationManagerCompat.from(appContext).notify(
                resolvedId,
                notification
            )
            true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    /**
     * Cancels an active notification by tag and removes it from the active payload store.
     */
    fun cancelNotification(context: Context, notificationTag: String) {
        val appContext = context.applicationContext
        removeActivePayload(appContext, notificationTag)
        val nm = NotificationManagerCompat.from(appContext)
        nm.cancel(NotiPayload.notificationIdForTag(notificationTag))
        nm.cancel(notificationTag, notificationTag.hashCode())
    }

    fun canPostNotifications(context: Context): Boolean {
        val appContext = context.applicationContext
        val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return runtimeGranted && NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    private fun buildCollapsedRemoteViews(
        context: Context,
        payload: NotiPayload
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.noti_collapsed)
        rv.setTextViewText(R.id.noti_collapsed_title, payload.title)
        rv.setTextViewText(
            R.id.noti_collapsed_body,
            if (payload.isTimerCompleted && payload.finishText.isNotBlank()) payload.finishText else payload.body
        )

        if (payload.showHeader && payload.statusBadge.isNotBlank()) {
            rv.setViewVisibility(R.id.noti_collapsed_badge, View.VISIBLE)
            rv.setTextViewText(
                R.id.noti_collapsed_badge,
                if (payload.isTimerCompleted) "DONE" else payload.statusBadge
            )
        } else {
            rv.setViewVisibility(R.id.noti_collapsed_badge, View.GONE)
        }

        val showProgress = payload.progressMode != ProgressMode.NONE
        if (showProgress) {
            rv.setViewVisibility(R.id.noti_collapsed_progress_bar, View.VISIBLE)
            rv.setProgressBar(R.id.noti_collapsed_progress_bar, 100, payload.progress.coerceIn(0, 100), false)
            if (payload.progressStatusText.isNotBlank()) {
                rv.setViewVisibility(R.id.noti_collapsed_progress_text, View.VISIBLE)
                rv.setTextViewText(R.id.noti_collapsed_progress_text, payload.progressStatusText)
            } else {
                rv.setViewVisibility(R.id.noti_collapsed_progress_text, View.GONE)
            }
        } else {
            rv.setViewVisibility(R.id.noti_collapsed_progress_bar, View.GONE)
            rv.setViewVisibility(R.id.noti_collapsed_progress_text, View.GONE)
        }

        return rv
    }

    private fun buildExpandedRemoteViews(
        context: Context,
        payload: NotiPayload,
        @DrawableRes iconRes: Int,
        accentColorInt: Int
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.noti_expanded)

        // 1. Header Block (strictly uses system-safe text colors from XML; only small icon uses contrast-safe tint)
        val showProgressText = payload.progressMode != ProgressMode.NONE && payload.progressStatusText.isNotBlank()
        if (payload.showHeader || showProgressText) {
            rv.setViewVisibility(R.id.noti_header_container, View.VISIBLE)
            rv.setImageViewResource(R.id.noti_header_icon, iconRes)
            rv.setInt(R.id.noti_header_icon, "setColorFilter", accentColorInt)

            if (payload.showHeader && payload.statusBadge.isNotBlank()) {
                rv.setViewVisibility(R.id.noti_status_badge, View.VISIBLE)
                rv.setTextViewText(
                    R.id.noti_status_badge,
                    if (payload.isTimerCompleted) "DONE" else payload.statusBadge
                )
            } else {
                rv.setViewVisibility(R.id.noti_status_badge, View.GONE)
            }

            if (showProgressText) {
                rv.setViewVisibility(R.id.noti_progress_text, View.VISIBLE)
                rv.setTextViewText(R.id.noti_progress_text, payload.progressStatusText)
            } else {
                rv.setViewVisibility(R.id.noti_progress_text, View.GONE)
            }
        } else {
            rv.setViewVisibility(R.id.noti_header_container, View.GONE)
        }

        // 2. Title & Body
        rv.setTextViewText(R.id.noti_title, payload.title)
        val displayBody = if (payload.isTimerCompleted && payload.finishText.isNotBlank()) {
            payload.finishText
        } else {
            payload.body
        }
        rv.setTextViewText(R.id.noti_body, displayBody)

        // 3. Progress Block
        if (payload.progressMode != ProgressMode.NONE) {
            rv.setViewVisibility(R.id.noti_progress_container, View.VISIBLE)
            rv.setProgressBar(R.id.noti_progress_bar, 100, payload.progress.coerceIn(0, 100), false)
        } else {
            rv.setViewVisibility(R.id.noti_progress_container, View.GONE)
        }

        // 4. Detail / Metadata & RemoteInput Reply Status Block
        val hasMetadata = payload.showDetailBlock && payload.metadata.isNotBlank()
        val hasReplyStatus = !payload.replyStatusText.isNullOrBlank()
        if (hasMetadata || hasReplyStatus) {
            rv.setViewVisibility(R.id.noti_detail_container, View.VISIBLE)
            if (hasMetadata) {
                rv.setViewVisibility(R.id.noti_metadata, View.VISIBLE)
                rv.setTextViewText(R.id.noti_metadata, payload.metadata)
            } else {
                rv.setViewVisibility(R.id.noti_metadata, View.GONE)
            }

            if (hasReplyStatus) {
                rv.setViewVisibility(R.id.noti_reply_status, View.VISIBLE)
                rv.setTextViewText(R.id.noti_reply_status, payload.replyStatusText)
            } else {
                rv.setViewVisibility(R.id.noti_reply_status, View.GONE)
            }
        } else {
            rv.setViewVisibility(R.id.noti_detail_container, View.GONE)
        }

        // 5. Action Buttons Block (uses FLAG_IMMUTABLE PendingIntents)
        val buttonIds = listOf(R.id.noti_btn_1, R.id.noti_btn_2, R.id.noti_btn_3)
        val actionsToShow = if (payload.showActionButtons) payload.actions.take(3) else emptyList()

        if (actionsToShow.isNotEmpty()) {
            rv.setViewVisibility(R.id.noti_actions_container, View.VISIBLE)
            buttonIds.forEachIndexed { index, viewId ->
                val action: NotiAction? = actionsToShow.getOrNull(index)
                if (action != null) {
                    rv.setViewVisibility(viewId, View.VISIBLE)
                    rv.setTextViewText(viewId, action.label)

                    val clickIntent = Intent(context, NotiActionReceiver::class.java).apply {
                        this.action = NotiContract.ACTION_INTERNAL_BUTTON_CLICK
                        setPackage(context.packageName)
                        putExtra(NotiContract.Extras.ID, payload.notificationTag)
                        putExtra(NotiContract.Extras.ACTION_ID, action.id)
                    }
                    val clickPendingIntent = PendingIntent.getBroadcast(
                        context,
                        uniqueRequestCode(payload.notificationTag, "btn_${index}_${action.id}"),
                        clickIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    rv.setOnClickPendingIntent(viewId, clickPendingIntent)
                } else {
                    rv.setViewVisibility(viewId, View.GONE)
                }
            }
        } else {
            rv.setViewVisibility(R.id.noti_actions_container, View.GONE)
        }

        return rv
    }

    @DrawableRes
    fun resolveSmallIconRes(iconName: String): Int {
        return when (iconName.trim().lowercase()) {
            "school", "lecture" -> R.drawable.ic_noti_school
            "edit_note", "note", "edit" -> R.drawable.ic_noti_edit_note
            "sync", "progress", "task" -> R.drawable.ic_noti_sync
            "tune", "deck", "controls" -> R.drawable.ic_noti_tune
            "verified", "status", "shield" -> R.drawable.ic_noti_verified
            "bolt", "alert", "minimal" -> R.drawable.ic_noti_bolt
            else -> R.drawable.ic_noti_default
        }
    }

    private fun resolveAccentColor(
        context: Context,
        accentColorHex: String,
        useDynamicColor: Boolean
    ): Int {
        val isNightMode = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.getColor(
                context,
                if (isNightMode) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
            )
        }
        val rawColor = try {
            val formatted = if (accentColorHex.startsWith("#")) accentColorHex else "#$accentColorHex"
            Color.parseColor(formatted)
        } catch (_: Exception) {
            return ContextCompat.getColor(context, R.color.noti_progress_fill)
        }

        // Ensure high contrast against dark or light system notification backgrounds
        val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(rawColor)
        return when {
            isNightMode && luminance < 0.35 -> {
                androidx.core.graphics.ColorUtils.blendARGB(rawColor, Color.WHITE, 0.45f)
            }
            !isNightMode && luminance > 0.65 -> {
                androidx.core.graphics.ColorUtils.blendARGB(rawColor, Color.BLACK, 0.35f)
            }
            else -> rawColor
        }
    }

    // =========================================================================
    // Active Notification Payload State Store (Supports Partial Updates & Reply State)
    // =========================================================================
    fun saveActivePayload(context: Context, payload: NotiPayload) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_ACTIVE_PAYLOADS, Context.MODE_PRIVATE)
        prefs.edit().putString(payload.notificationTag, json.encodeToString(payload)).apply()
    }

    fun getActivePayload(context: Context, notificationTag: String): NotiPayload? {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_ACTIVE_PAYLOADS, Context.MODE_PRIVATE)
        val raw = prefs.getString(notificationTag, null) ?: return null
        return try {
            json.decodeFromString<NotiPayload>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun removeActivePayload(context: Context, notificationTag: String) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_ACTIVE_PAYLOADS, Context.MODE_PRIVATE)
        prefs.edit().remove(notificationTag).apply()
    }

    /**
     * Merges an incoming [NotiCommand.ShowOrUpdate] onto an existing [NotiPayload] when
     * MacroDroid triggers a partial update (e.g. updating `progress` from 40 to 80).
     */
    fun mergeUpdatePayload(
        existing: NotiPayload,
        command: NotiCommand.ShowOrUpdate
    ): NotiPayload {
        val updatedMode = command.progressMode ?: existing.progressMode
        val updatedProgress = (command.progress ?: existing.progress).coerceIn(0, 100)
        val updatedDuration = (command.durationMinutes ?: existing.durationMinutes)
            .coerceIn(1, NotiContract.MAX_DURATION_MINUTES)

        val shouldRestartTimer = updatedMode == ProgressMode.AUTO_TIMER &&
            (command.startTimeEpochMillis != null || (existing.isTimerCompleted && command.durationMinutes != null))
        val updatedStartTime = when {
            command.startTimeEpochMillis != null -> command.startTimeEpochMillis
            shouldRestartTimer -> System.currentTimeMillis()
            else -> existing.startTimeEpochMillis
        }
        val updatedTimerCompleted = if (shouldRestartTimer || updatedMode != ProgressMode.AUTO_TIMER) {
            false
        } else {
            existing.isTimerCompleted
        }

        val updatedStatusText = when (updatedMode) {
            ProgressMode.NONE -> ""
            ProgressMode.MANUAL -> "$updatedProgress%"
            ProgressMode.AUTO_TIMER -> if (shouldRestartTimer) {
                "${updatedDuration}m left"
            } else {
                existing.progressStatusText.ifBlank { "${updatedDuration}m left" }
            }
        }

        val resolvedActions = (command.actionOverrides ?: existing.actions).take(3).mapIndexed { idx, act ->
            NotiAction(
                id = act.id.trim().take(NotiContract.MAX_ACTION_ID_LENGTH).ifEmpty { "btn_${idx + 1}" },
                label = act.label.trim().take(NotiContract.MAX_ACTION_LABEL_LENGTH).ifEmpty { "Action ${idx + 1}" }
            )
        }

        return existing.copy(
            title = (command.title ?: existing.title)
                .trim()
                .take(NotiContract.MAX_TITLE_LENGTH)
                .ifEmpty { "NotiEngine Alert" },
            body = (command.body ?: existing.body)
                .trim()
                .take(NotiContract.MAX_BODY_LENGTH),
            metadata = (command.metadata ?: existing.metadata)
                .trim()
                .take(NotiContract.MAX_METADATA_LENGTH),
            statusBadge = (command.statusBadge ?: existing.statusBadge)
                .trim()
                .take(NotiContract.MAX_BADGE_LENGTH),
            accentColorHex = (command.accentColorHex ?: existing.accentColorHex)
                .trim()
                .take(NotiContract.MAX_COLOR_HEX_LENGTH)
                .ifEmpty { "#4F46E5" },
            progressMode = updatedMode,
            progress = updatedProgress,
            progressStatusText = updatedStatusText,
            durationMinutes = updatedDuration,
            startTimeEpochMillis = updatedStartTime,
            autoDismiss = command.autoDismiss ?: existing.autoDismiss,
            finishText = (command.finishText ?: existing.finishText)
                .trim()
                .take(NotiContract.MAX_BODY_LENGTH)
                .ifEmpty { "Completed" },
            showRemoteInput = command.showInput ?: existing.showRemoteInput,
            inputHint = (command.inputHint ?: existing.inputHint)
                .trim()
                .take(NotiContract.MAX_HINT_LENGTH),
            actions = resolvedActions,
            showActionButtons = if (command.actionOverrides != null) resolvedActions.isNotEmpty() else existing.showActionButtons,
            isTimerCompleted = updatedTimerCompleted,
            replyStatusText = null
        )
    }
}
