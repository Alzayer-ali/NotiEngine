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
import android.view.Gravity
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
import com.notiforge.app.domain.model.NotiBlock
import com.notiforge.app.domain.model.NotiCommand
import com.notiforge.app.domain.model.NotiPayload
import com.notiforge.app.domain.model.ProgressMode
import com.notiforge.app.domain.model.TextAlignment
import com.notiforge.app.domain.model.legacyToBlocks
import com.notiforge.app.ipc.NotiContract
import com.notiforge.app.receiver.NotiActionReceiver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Core Notification Render Engine responsible for:
 * - Creating NotificationChannels
 * - Dynamically inflating and assembling modular RemoteViews block components into `noti_blocks_container`
 * - Day/Night adaptive contrast tokens and Material You dynamic color tinting
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
     * Builds a complete [Notification] using dynamic custom collapsed and expanded [RemoteViews]
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

        // Attach inline RemoteInput if enabled or present in blocks
        val hasReplyBlock = payload.showRemoteInput || payload.blocks.any { it is NotiBlock.InlineReplyBlock }
        if (hasReplyBlock) {
            val replyLabel = payload.inputHint.ifBlank { appContext.getString(R.string.noti_reply_action_label) }
            val remoteInput = RemoteInput.Builder(NotiContract.REMOTE_INPUT_RESULT_KEY)
                .setLabel(replyLabel)
                .build()

            val replyIntent = Intent(appContext, NotiActionReceiver::class.java).apply {
                action = NotiContract.ACTION_INTERNAL_REMOTE_INPUT
                setPackage(appContext.packageName)
                putExtra(NotiContract.Extras.ID, payload.notificationTag)
            }

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

    /**
     * Iterates through the payload's ordered blocks list and injects each sub-view dynamically
     * into the parent container `@+id/noti_blocks_container` via `addView()`.
     */
    private fun buildExpandedRemoteViews(
        context: Context,
        payload: NotiPayload,
        @DrawableRes iconRes: Int,
        accentColorInt: Int
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.noti_expanded)
        rv.removeAllViews(R.id.noti_blocks_container)

        val blocksToRender = if (payload.blocks.isNotEmpty()) {
            payload.blocks
        } else {
            legacyToBlocks(
                showHeader = payload.showHeader,
                statusBadge = payload.statusBadge,
                iconName = payload.iconName,
                accentColorHex = payload.accentColorHex,
                useDynamicColor = payload.useDynamicColor,
                progressMode = payload.progressMode,
                defaultProgress = payload.progress,
                defaultDurationMinutes = payload.durationMinutes,
                autoDismiss = payload.autoDismiss,
                finishText = payload.finishText,
                showDetailBlock = payload.showDetailBlock,
                defaultTitle = payload.title,
                defaultBody = payload.body,
                defaultMetadata = payload.metadata,
                showActionButtons = payload.showActionButtons,
                actions = payload.actions,
                showRemoteInput = payload.showRemoteInput,
                inputHint = payload.inputHint
            )
        }

        val progressReadout = when (payload.progressMode) {
            ProgressMode.NONE -> ""
            ProgressMode.MANUAL -> "${payload.progress}%"
            ProgressMode.AUTO_TIMER -> payload.progressStatusText.ifBlank { "${payload.durationMinutes}m left" }
        }

        for (block in blocksToRender) {
            when (block) {
                is NotiBlock.HeaderBlock -> {
                    val headerRv = RemoteViews(context.packageName, R.layout.block_header)
                    val blockIconRes = resolveSmallIconRes(block.iconName)
                    val blockAccentColor = resolveAccentColor(context, block.accentColorHex, block.useDynamicColor)
                    headerRv.setImageViewResource(R.id.noti_header_icon, blockIconRes)
                    headerRv.setInt(R.id.noti_header_icon, "setColorFilter", blockAccentColor)

                    val badgeText = if (payload.isTimerCompleted) "DONE" else block.statusBadge
                    if (badgeText.isNotBlank()) {
                        headerRv.setViewVisibility(R.id.noti_status_badge, View.VISIBLE)
                        headerRv.setTextViewText(R.id.noti_status_badge, badgeText)
                    } else {
                        headerRv.setViewVisibility(R.id.noti_status_badge, View.GONE)
                    }

                    if (payload.progressMode != ProgressMode.NONE && progressReadout.isNotBlank()) {
                        headerRv.setViewVisibility(R.id.noti_progress_text, View.VISIBLE)
                        headerRv.setTextViewText(R.id.noti_progress_text, progressReadout)
                    } else {
                        headerRv.setViewVisibility(R.id.noti_progress_text, View.GONE)
                    }

                    rv.addView(R.id.noti_blocks_container, headerRv)
                }

                is NotiBlock.TextBlock -> {
                    val textRv = RemoteViews(context.packageName, R.layout.block_text)
                    val displayTitle = block.title.ifBlank { payload.title }
                    val displayBody = if (payload.isTimerCompleted && payload.finishText.isNotBlank()) {
                        payload.finishText
                    } else {
                        block.body.ifBlank { payload.body }
                    }

                    textRv.setTextViewText(R.id.noti_title, displayTitle)
                    textRv.setTextViewText(R.id.noti_body, displayBody)

                    if (block.subtext.isNotBlank()) {
                        textRv.setViewVisibility(R.id.noti_subtext, View.VISIBLE)
                        textRv.setTextViewText(R.id.noti_subtext, block.subtext)
                    } else {
                        textRv.setViewVisibility(R.id.noti_subtext, View.GONE)
                    }

                    val gravityInt = when (block.alignment) {
                        TextAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
                        TextAlignment.END -> Gravity.END
                        TextAlignment.START -> Gravity.START
                    }
                    textRv.setInt(R.id.noti_title, "setGravity", gravityInt)
                    textRv.setInt(R.id.noti_body, "setGravity", gravityInt)
                    textRv.setInt(R.id.noti_subtext, "setGravity", gravityInt)

                    rv.addView(R.id.noti_blocks_container, textRv)
                }

                is NotiBlock.DividerBlock -> {
                    val dividerRv = RemoteViews(context.packageName, R.layout.block_divider)
                    rv.addView(R.id.noti_blocks_container, dividerRv)
                }

                is NotiBlock.MetadataBlock -> {
                    val metaText = block.text.ifBlank { payload.metadata }
                    val hasReplyStatus = !payload.replyStatusText.isNullOrBlank()
                    if (metaText.isNotBlank() || hasReplyStatus) {
                        val metaRv = RemoteViews(context.packageName, R.layout.block_metadata)
                        val formatted = if (block.label.isNotBlank() && !metaText.startsWith(block.label)) {
                            "${block.label}: $metaText"
                        } else {
                            metaText
                        }

                        if (formatted.isNotBlank()) {
                            metaRv.setViewVisibility(R.id.noti_metadata, View.VISIBLE)
                            metaRv.setTextViewText(R.id.noti_metadata, formatted)
                        } else {
                            metaRv.setViewVisibility(R.id.noti_metadata, View.GONE)
                        }

                        if (hasReplyStatus) {
                            metaRv.setViewVisibility(R.id.noti_reply_status, View.VISIBLE)
                            metaRv.setTextViewText(R.id.noti_reply_status, payload.replyStatusText)
                        } else {
                            metaRv.setViewVisibility(R.id.noti_reply_status, View.GONE)
                        }

                        rv.addView(R.id.noti_blocks_container, metaRv)
                    }
                }

                is NotiBlock.ProgressBlock -> {
                    if (payload.progressMode != ProgressMode.NONE || block.progressMode != ProgressMode.NONE) {
                        val progressRv = RemoteViews(context.packageName, R.layout.block_progress)
                        progressRv.setProgressBar(
                            R.id.noti_progress_bar,
                            100,
                            payload.progress.coerceIn(0, 100),
                            false
                        )
                        rv.addView(R.id.noti_blocks_container, progressRv)
                    }
                }

                is NotiBlock.ActionsBlock -> {
                    val actionsToShow = (payload.actions.ifEmpty { block.actions }).take(3)
                    if (actionsToShow.isNotEmpty()) {
                        val actionsRv = RemoteViews(context.packageName, R.layout.block_actions)
                        val buttonIds = listOf(R.id.noti_btn_1, R.id.noti_btn_2, R.id.noti_btn_3)
                        buttonIds.forEachIndexed { index, viewId ->
                            val action: NotiAction? = actionsToShow.getOrNull(index)
                            if (action != null) {
                                actionsRv.setViewVisibility(viewId, View.VISIBLE)
                                actionsRv.setTextViewText(viewId, action.label)

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
                                actionsRv.setOnClickPendingIntent(viewId, clickPendingIntent)
                            } else {
                                actionsRv.setViewVisibility(viewId, View.GONE)
                            }
                        }
                        rv.addView(R.id.noti_blocks_container, actionsRv)
                    }
                }

                is NotiBlock.InlineReplyBlock -> {
                    val replyRv = RemoteViews(context.packageName, R.layout.block_inline_reply)
                    val hint = payload.inputHint.ifBlank { block.inputHint }
                    replyRv.setTextViewText(
                        R.id.noti_reply_hint,
                        hint.ifBlank { context.getString(R.string.noti_reply_action_label) }
                    )
                    if (!payload.replyStatusText.isNullOrBlank()) {
                        replyRv.setViewVisibility(R.id.noti_reply_status_text, View.VISIBLE)
                        replyRv.setTextViewText(R.id.noti_reply_status_text, payload.replyStatusText)
                    } else {
                        replyRv.setViewVisibility(R.id.noti_reply_status_text, View.GONE)
                    }
                    rv.addView(R.id.noti_blocks_container, replyRv)
                }
            }
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

        val updatedTitle = (command.title ?: existing.title)
            .trim()
            .take(NotiContract.MAX_TITLE_LENGTH)
            .ifEmpty { "NotiEngine Alert" }

        val updatedBody = (command.body ?: existing.body)
            .trim()
            .take(NotiContract.MAX_BODY_LENGTH)

        val updatedMetadata = (command.metadata ?: existing.metadata)
            .trim()
            .take(NotiContract.MAX_METADATA_LENGTH)

        val updatedBadge = (command.statusBadge ?: existing.statusBadge)
            .trim()
            .take(NotiContract.MAX_BADGE_LENGTH)

        val updatedAccent = (command.accentColorHex ?: existing.accentColorHex)
            .trim()
            .take(NotiContract.MAX_COLOR_HEX_LENGTH)
            .ifEmpty { "#4F46E5" }

        val updatedFinishText = (command.finishText ?: existing.finishText)
            .trim()
            .take(NotiContract.MAX_BODY_LENGTH)
            .ifEmpty { "Completed" }

        val updatedHint = (command.inputHint ?: existing.inputHint)
            .trim()
            .take(NotiContract.MAX_HINT_LENGTH)

        // Merge block representations
        val baseBlocks = command.blocks ?: existing.blocks
        val updatedBlocks = baseBlocks.map { block ->
            when (block) {
                is NotiBlock.HeaderBlock -> {
                    block.copy(
                        statusBadge = command.statusBadge ?: block.statusBadge,
                        accentColorHex = command.accentColorHex ?: block.accentColorHex
                    )
                }
                is NotiBlock.TextBlock -> {
                    block.copy(
                        title = command.title ?: block.title.ifBlank { updatedTitle },
                        body = command.body ?: block.body.ifBlank { updatedBody }
                    )
                }
                is NotiBlock.MetadataBlock -> {
                    block.copy(
                        text = command.metadata ?: block.text.ifBlank { updatedMetadata }
                    )
                }
                is NotiBlock.DividerBlock -> block
                is NotiBlock.ProgressBlock -> {
                    block.copy(
                        progressMode = updatedMode,
                        progress = updatedProgress,
                        durationMinutes = updatedDuration,
                        autoDismiss = command.autoDismiss ?: block.autoDismiss,
                        finishText = command.finishText ?: block.finishText
                    )
                }
                is NotiBlock.ActionsBlock -> {
                    block.copy(actions = resolvedActions)
                }
                is NotiBlock.InlineReplyBlock -> {
                    block.copy(inputHint = updatedHint)
                }
            }
        }.toMutableList()

        if (command.metadata != null && updatedBlocks.none { it is NotiBlock.MetadataBlock }) {
            updatedBlocks.add(NotiBlock.MetadataBlock(text = updatedMetadata))
        }
        if (command.actionOverrides != null && updatedBlocks.none { it is NotiBlock.ActionsBlock }) {
            updatedBlocks.add(NotiBlock.ActionsBlock(actions = resolvedActions))
        }
        if (command.showInput == true && updatedBlocks.none { it is NotiBlock.InlineReplyBlock }) {
            updatedBlocks.add(NotiBlock.InlineReplyBlock(inputHint = updatedHint))
        }

        return existing.copy(
            blocks = updatedBlocks,
            title = updatedTitle,
            body = updatedBody,
            metadata = updatedMetadata,
            statusBadge = updatedBadge,
            accentColorHex = updatedAccent,
            progressMode = updatedMode,
            progress = updatedProgress,
            progressStatusText = updatedStatusText,
            durationMinutes = updatedDuration,
            startTimeEpochMillis = updatedStartTime,
            autoDismiss = command.autoDismiss ?: existing.autoDismiss,
            finishText = updatedFinishText,
            showRemoteInput = command.showInput ?: existing.showRemoteInput,
            inputHint = updatedHint,
            actions = resolvedActions,
            showActionButtons = if (command.actionOverrides != null) resolvedActions.isNotEmpty() else existing.showActionButtons,
            isTimerCompleted = updatedTimerCompleted,
            replyStatusText = null
        )
    }
}
