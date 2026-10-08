package com.notiforge.app.ipc

import android.content.Intent
import android.os.Bundle
import com.notiforge.app.domain.model.NotiAction
import com.notiforge.app.domain.model.NotiCommand
import com.notiforge.app.domain.model.NotiPayload
import com.notiforge.app.domain.model.NotiTemplate
import com.notiforge.app.domain.model.ProgressMode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Bidirectional IPC & BroadcastIntent specification between MacroDroid (or any automation tool)
 * and NotiEngine.
 */
object NotiContract {

    const val PACKAGE_NAME = "com.notiforge.app"
    const val RECEIVER_CLASS = "com.notiforge.app.receiver.NotiReceiver"
    const val ACTION_RECEIVER_CLASS = "com.notiforge.app.receiver.NotiActionReceiver"

    // =========================================================================
    // 1. Incoming Broadcast Actions (MacroDroid -> NotiEngine)
    // =========================================================================
    const val ACTION_SHOW = "com.notiforge.action.SHOW"
    const val ACTION_SHOW_NOTIFICATION = "com.notiforge.action.SHOW_NOTIFICATION"
    const val ACTION_UPDATE = "com.notiforge.action.UPDATE"
    const val ACTION_CANCEL = "com.notiforge.action.CANCEL"

    // =========================================================================
    // 2. Internal Notification PendingIntent Actions
    // =========================================================================
    const val ACTION_INTERNAL_BUTTON_CLICK = "com.notiforge.action.INTERNAL_BUTTON_CLICK"
    const val ACTION_INTERNAL_REMOTE_INPUT = "com.notiforge.action.INTERNAL_REMOTE_INPUT"
    const val ACTION_INTERNAL_NOTIFICATION_DISMISSED = "com.notiforge.action.INTERNAL_NOTIFICATION_DISMISSED"
    const val REMOTE_INPUT_RESULT_KEY = "noti_remote_input_reply"

    // =========================================================================
    // 3. Outgoing Broadcast Actions (NotiEngine -> MacroDroid)
    // =========================================================================
    const val ACTION_EVENT = "com.notiforge.action.EVENT"

    // =========================================================================
    // 4. Notification Channels
    // =========================================================================
    const val CHANNEL_DEFAULT_ID = "notiengine_rich_channel"
    const val CHANNEL_TIMER_ID = "notiengine_timer_channel_v2"
    const val CHANNEL_HIGH_ALERT_ID = "notiengine_alert_channel"

    // =========================================================================
    // 5. Payload Size Bounds (Prevents TransactionTooLargeException over Binder)
    // =========================================================================
    const val MAX_ID_LENGTH = 128
    const val MAX_SLUG_LENGTH = 64
    const val MAX_TITLE_LENGTH = 250
    const val MAX_BODY_LENGTH = 1_000
    const val MAX_METADATA_LENGTH = 300
    const val MAX_BADGE_LENGTH = 40
    const val MAX_COLOR_HEX_LENGTH = 16
    const val MAX_ACTION_ID_LENGTH = 64
    const val MAX_ACTION_LABEL_LENGTH = 48
    const val MAX_HINT_LENGTH = 120
    const val MAX_DURATION_MINUTES = 10_080 // 7 days
    const val MAX_JSON_LENGTH = 8_192

    @OptIn(ExperimentalSerializationApi::class)
    val compactJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = false
        explicitNulls = false
        prettyPrint = false
    }

    // =========================================================================
    // 6. Incoming & Outgoing Intent Extra Keys
    // =========================================================================
    object Extras {
        // Single-Payload JSON Extra keys
        const val JSON = "json"
        const val PAYLOAD = "payload"

        const val ID = "id"
        const val TEMPLATE = "template"
        const val TITLE = "title"
        const val BODY = "body"
        const val METADATA = "metadata"
        const val STATUS_BADGE = "status_badge"
        const val ACCENT_COLOR = "accent_color"

        const val PROGRESS_MODE = "progress_mode"
        const val PROGRESS = "progress"
        const val DURATION = "duration"
        const val START_TIME = "start_time"
        const val AUTO_DISMISS = "auto_dismiss"
        const val FINISH_TEXT = "finish_text"

        const val SHOW_INPUT = "show_input"
        const val INPUT_HINT = "input_hint"

        // Optional inline overrides for up to 3 action buttons
        const val ACTION_1_ID = "action_1_id"
        const val ACTION_1_LABEL = "action_1_label"
        const val ACTION_2_ID = "action_2_id"
        const val ACTION_2_LABEL = "action_2_label"
        const val ACTION_3_ID = "action_3_id"
        const val ACTION_3_LABEL = "action_3_label"

        // Outgoing Event Callback Extras
        const val EVENT_TYPE = "event_type"
        const val ACTION_ID = "action_id"
        const val USER_INPUT = "user_input"
    }

    object ProgressModes {
        const val MANUAL = "manual"
        const val AUTO_TIMER = "auto_timer"
        const val NONE = "none"
    }

    object EventTypes {
        const val ACTION_CLICKED = "ACTION_CLICKED"
        const val INPUT_SUBMITTED = "INPUT_SUBMITTED"
        const val TIMER_FINISHED = "TIMER_FINISHED"
    }

    /**
     * Builds a compact, single-line JSON string containing all active parameters for [template],
     * ready to be pasted into MacroDroid's single `json` Intent Extra.
     */
    fun buildTemplateJsonExtra(
        template: NotiTemplate,
        idOverride: String = "noti_${template.slug}"
    ): String {
        val command = NotiCommand.ShowOrUpdate(
            id = idOverride,
            templateSlug = template.slug,
            title = template.defaultTitle,
            body = template.defaultBody,
            metadata = if (template.showDetailBlock || template.defaultMetadata.isNotBlank()) {
                template.defaultMetadata
            } else {
                null
            },
            statusBadge = if (template.showHeader) template.statusBadge else null,
            accentColorHex = if (!template.useDynamicColor) template.accentColorHex else null,
            progressMode = template.progressMode,
            progress = if (template.progressMode == ProgressMode.MANUAL) template.defaultProgress else null,
            durationMinutes = if (template.progressMode == ProgressMode.AUTO_TIMER) template.defaultDurationMinutes else null,
            autoDismiss = template.autoDismiss,
            finishText = if (template.progressMode == ProgressMode.AUTO_TIMER && template.finishText.isNotBlank()) {
                template.finishText
            } else {
                null
            },
            showInput = if (template.showRemoteInput) true else null,
            inputHint = if (template.showRemoteInput) template.inputHint else null,
            actionOverrides = if (template.showActionButtons && template.actions.isNotEmpty()) {
                template.actions.take(3)
            } else {
                null
            }
        )
        return compactJson.encodeToString(command)
    }

    /**
     * Returns true if the [Intent] contains a single-payload `"json"` or `"payload"` Extra.
     */
    fun hasJsonExtra(intent: Intent?): Boolean {
        if (intent == null) return false
        return try {
            intent.hasExtra(Extras.JSON) || intent.hasExtra(Extras.PAYLOAD)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Safely parses an incoming [Intent] into a strongly-typed [NotiCommand].
     * - If `intent.hasExtra("json")` or `intent.hasExtra("payload")` is present, parses the JSON payload
     *   directly into [NotiCommand] / [NotiPayload] using Kotlinx Serialization.
     * - Falls back to standard individual Intent Extras only if the `"json"` / `"payload"` extra is absent.
     */
    fun parseCommand(intent: Intent?): NotiCommand? {
        if (intent == null) return null
        return try {
            val action = intent.action?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (hasJsonExtra(intent)) {
                return parseJsonIntent(intent, action)
            }
            parseIndividualExtrasIntent(intent, action)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Parses a single-payload JSON string from `intent.getStringExtra("json")` or `"payload"`
     * using Kotlinx Serialization.
     */
    fun parseJsonIntent(intent: Intent, action: String = intent.action ?: ACTION_SHOW_NOTIFICATION): NotiCommand? {
        val extras = try {
            intent.extras ?: Bundle.EMPTY
        } catch (_: Throwable) {
            Bundle.EMPTY
        }
        val rawJson = (intent.getStringExtra(Extras.JSON)
            ?: intent.getStringExtra(Extras.PAYLOAD)
            ?: extras.getSafeString(Extras.JSON, MAX_JSON_LENGTH)
            ?: extras.getSafeString(Extras.PAYLOAD, MAX_JSON_LENGTH))
            ?.trim()
            ?.take(MAX_JSON_LENGTH)
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return parseJsonCommand(rawJson, action)
    }

    /**
     * Parses a JSON payload string directly into [NotiCommand] (supporting both [NotiCommand.ShowOrUpdate]
     * and full [NotiPayload] JSON schemas) via Kotlinx Serialization.
     */
    fun parseJsonCommand(rawJson: String, action: String = ACTION_SHOW_NOTIFICATION): NotiCommand? {
        val trimmed = rawJson.trim().take(MAX_JSON_LENGTH)
        if (trimmed.isEmpty()) return null

        return try {
            when (action) {
                ACTION_CANCEL -> {
                    val cancelCmd = runCatching {
                        compactJson.decodeFromString<NotiCommand.Cancel>(trimmed)
                    }.getOrNull()
                    val fallbackShow = if (cancelCmd?.id.isNullOrBlank()) {
                        runCatching { compactJson.decodeFromString<NotiCommand.ShowOrUpdate>(trimmed) }.getOrNull()
                    } else {
                        null
                    }
                    val resolvedId = cancelCmd?.id?.trim()?.take(MAX_ID_LENGTH)?.takeIf { it.isNotEmpty() }
                        ?: fallbackShow?.id?.trim()?.take(MAX_ID_LENGTH)?.takeIf { it.isNotEmpty() }
                        ?: fallbackShow?.templateSlug?.let { "noti_${sanitizeKey(it)}" }
                        ?: "noti_default"
                    NotiCommand.Cancel(id = resolvedId)
                }

                ACTION_SHOW, ACTION_SHOW_NOTIFICATION, ACTION_UPDATE -> {
                    val isUpdateOnly = action == ACTION_UPDATE

                    // Support full NotiPayload JSON if serialized directly
                    if (trimmed.contains("\"notificationTag\"")) {
                        val directPayload = runCatching {
                            compactJson.decodeFromString<NotiPayload>(trimmed)
                        }.getOrNull()
                        if (directPayload != null) {
                            return NotiCommand.ShowOrUpdate(
                                id = directPayload.notificationTag.trim().take(MAX_ID_LENGTH).ifEmpty { "noti_default" },
                                isUpdate = isUpdateOnly,
                                templateSlug = directPayload.templateSlug.trim().take(MAX_SLUG_LENGTH),
                                title = directPayload.title.trim().take(MAX_TITLE_LENGTH),
                                body = directPayload.body.trim().take(MAX_BODY_LENGTH),
                                metadata = directPayload.metadata.trim().take(MAX_METADATA_LENGTH),
                                statusBadge = directPayload.statusBadge.trim().take(MAX_BADGE_LENGTH),
                                accentColorHex = directPayload.accentColorHex.trim().take(MAX_COLOR_HEX_LENGTH),
                                progressMode = directPayload.progressMode,
                                progress = directPayload.progress.coerceIn(0, 100),
                                durationMinutes = directPayload.durationMinutes.coerceIn(1, MAX_DURATION_MINUTES),
                                startTimeEpochMillis = directPayload.startTimeEpochMillis,
                                autoDismiss = directPayload.autoDismiss,
                                finishText = directPayload.finishText.trim().take(MAX_BODY_LENGTH),
                                showInput = directPayload.showRemoteInput,
                                inputHint = directPayload.inputHint.trim().take(MAX_HINT_LENGTH),
                                actionOverrides = directPayload.actions.take(3).takeIf { directPayload.showActionButtons && it.isNotEmpty() }
                            )
                        }
                    }

                    // Primary path: direct Kotlinx Serialization decoding into NotiCommand.ShowOrUpdate
                    val decoded = runCatching {
                        compactJson.decodeFromString<NotiCommand.ShowOrUpdate>(trimmed)
                    }.getOrElse {
                        decodeFlexibleJsonObject(trimmed) ?: return null
                    }

                    // Also inspect JsonObject for optional inline action_1_label..action_3_label keys if actions list is omitted
                    val inlineActions = if (decoded.actionOverrides == null && trimmed.contains("action_")) {
                        runCatching {
                            extractInlineActionsFromJsonObject(compactJson.parseToJsonElement(trimmed).jsonObject)
                        }.getOrNull()
                    } else {
                        null
                    }

                    sanitizeDecodedCommand(
                        decoded = decoded.copy(
                            isUpdate = isUpdateOnly,
                            actionOverrides = decoded.actionOverrides ?: inlineActions
                        ),
                        isUpdateOnly = isUpdateOnly
                    )
                }

                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun sanitizeDecodedCommand(
        decoded: NotiCommand.ShowOrUpdate,
        isUpdateOnly: Boolean
    ): NotiCommand.ShowOrUpdate {
        val cleanSlug = decoded.templateSlug?.trim()?.take(MAX_SLUG_LENGTH)?.takeIf { it.isNotEmpty() }
        val cleanTitle = decoded.title?.trim()?.take(MAX_TITLE_LENGTH)?.takeIf { it.isNotEmpty() }
        val cleanId = decoded.id.trim().take(MAX_ID_LENGTH).ifEmpty {
            cleanSlug?.let { "noti_${sanitizeKey(it)}" }
                ?: cleanTitle?.let { "noti_${it.hashCode() and 0x7FFFFFFF}" }
                ?: "noti_default"
        }

        return decoded.copy(
            id = cleanId,
            isUpdate = isUpdateOnly,
            templateSlug = cleanSlug,
            title = cleanTitle,
            body = decoded.body?.trim()?.take(MAX_BODY_LENGTH),
            metadata = decoded.metadata?.trim()?.take(MAX_METADATA_LENGTH),
            statusBadge = decoded.statusBadge?.trim()?.take(MAX_BADGE_LENGTH),
            accentColorHex = decoded.accentColorHex?.trim()?.take(MAX_COLOR_HEX_LENGTH),
            progress = decoded.progress?.coerceIn(0, 100),
            durationMinutes = decoded.durationMinutes?.coerceIn(1, MAX_DURATION_MINUTES),
            finishText = decoded.finishText?.trim()?.take(MAX_BODY_LENGTH),
            inputHint = decoded.inputHint?.trim()?.take(MAX_HINT_LENGTH),
            actionOverrides = decoded.actionOverrides?.take(3)?.mapIndexed { index, act ->
                NotiAction(
                    id = act.id.trim().take(MAX_ACTION_ID_LENGTH).ifEmpty { "btn_${index + 1}" },
                    label = act.label.trim().take(MAX_ACTION_LABEL_LENGTH).ifEmpty { "Action ${index + 1}" }
                )
            }?.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Fallback Kotlinx Serialization `JsonObject` parser that handles MacroDroid variable expressions
     * inside JSON strings (e.g., `"duration": "01:30"`, `"progress": "75%"`, `"auto_dismiss": "yes"`).
     */
    private fun decodeFlexibleJsonObject(rawJson: String): NotiCommand.ShowOrUpdate? {
        val obj = compactJson.parseToJsonElement(rawJson).jsonObject
        fun str(vararg keys: String, maxLen: Int = MAX_BODY_LENGTH): String? {
            for (k in keys) {
                val prim = obj[k] as? JsonPrimitive ?: continue
                val content = prim.contentOrNull?.trim() ?: continue
                if (content.isNotEmpty() && !content.equals("null", ignoreCase = true)) {
                    return content.take(maxLen)
                }
            }
            return null
        }

        val tempBundle = Bundle().apply {
            str(Extras.PROGRESS)?.let { putString(Extras.PROGRESS, it) }
            str(Extras.DURATION, "durationMinutes", "duration_minutes")?.let { putString(Extras.DURATION, it) }
            str(Extras.START_TIME, "startTimeEpochMillis", "start_time_epoch_millis")?.let { putString(Extras.START_TIME, it) }
            str(Extras.AUTO_DISMISS, "autoDismiss")?.let { putString(Extras.AUTO_DISMISS, it) }
            str(Extras.SHOW_INPUT, "showInput", "showRemoteInput")?.let { putString(Extras.SHOW_INPUT, it) }
        }

        val actionsList = (obj["actions"] as? JsonArray ?: obj["actionOverrides"] as? JsonArray)?.mapNotNull { el ->
            runCatching { compactJson.decodeFromJsonElement(NotiAction.serializer(), el) }.getOrNull()
        }?.takeIf { it.isNotEmpty() } ?: extractInlineActionsFromJsonObject(obj)

        return NotiCommand.ShowOrUpdate(
            id = str(Extras.ID, maxLen = MAX_ID_LENGTH) ?: "",
            templateSlug = str(Extras.TEMPLATE, "templateSlug", "template_slug", maxLen = MAX_SLUG_LENGTH),
            title = str(Extras.TITLE, maxLen = MAX_TITLE_LENGTH),
            body = str(Extras.BODY, maxLen = MAX_BODY_LENGTH),
            metadata = str(Extras.METADATA, maxLen = MAX_METADATA_LENGTH),
            statusBadge = str(Extras.STATUS_BADGE, "statusBadge", maxLen = MAX_BADGE_LENGTH),
            accentColorHex = str(Extras.ACCENT_COLOR, "accentColorHex", "accentColor", maxLen = MAX_COLOR_HEX_LENGTH),
            progressMode = str(Extras.PROGRESS_MODE, "progressMode", maxLen = 32)?.let { ProgressMode.fromWireValue(it) },
            progress = tempBundle.getSafeInt(Extras.PROGRESS)?.coerceIn(0, 100),
            durationMinutes = tempBundle.getSafeDurationMinutes(Extras.DURATION),
            startTimeEpochMillis = tempBundle.getSafeStartTimeMillis(Extras.START_TIME),
            autoDismiss = tempBundle.getSafeBoolean(Extras.AUTO_DISMISS),
            finishText = str(Extras.FINISH_TEXT, "finishText", maxLen = MAX_BODY_LENGTH),
            showInput = tempBundle.getSafeBoolean(Extras.SHOW_INPUT),
            inputHint = str(Extras.INPUT_HINT, "inputHint", maxLen = MAX_HINT_LENGTH),
            actionOverrides = actionsList
        )
    }

    private fun extractInlineActionsFromJsonObject(obj: JsonObject): List<NotiAction>? {
        fun str(key: String, maxLen: Int): String? {
            val content = (obj[key] as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
            return content.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }?.take(maxLen)
        }
        return buildList {
            val a1Label = str(Extras.ACTION_1_LABEL, MAX_ACTION_LABEL_LENGTH)
            if (!a1Label.isNullOrBlank()) {
                add(NotiAction(str(Extras.ACTION_1_ID, MAX_ACTION_ID_LENGTH) ?: "btn_1", a1Label))
            }
            val a2Label = str(Extras.ACTION_2_LABEL, MAX_ACTION_LABEL_LENGTH)
            if (!a2Label.isNullOrBlank()) {
                add(NotiAction(str(Extras.ACTION_2_ID, MAX_ACTION_ID_LENGTH) ?: "btn_2", a2Label))
            }
            val a3Label = str(Extras.ACTION_3_LABEL, MAX_ACTION_LABEL_LENGTH)
            if (!a3Label.isNullOrBlank()) {
                add(NotiAction(str(Extras.ACTION_3_ID, MAX_ACTION_ID_LENGTH) ?: "btn_3", a3Label))
            }
        }.takeIf { it.isNotEmpty() }
    }

    /**
     * Standard individual Intent Extras parser (used only when the `"json"` / `"payload"` extra is absent).
     */
    fun parseIndividualExtrasIntent(
        intent: Intent,
        action: String = intent.action ?: ACTION_SHOW_NOTIFICATION
    ): NotiCommand? {
        val extras = try {
            intent.extras ?: Bundle.EMPTY
        } catch (_: Throwable) {
            Bundle.EMPTY
        }

        val templateSlug = extras.getSafeString(Extras.TEMPLATE, MAX_SLUG_LENGTH)
        val rawTitle = extras.getSafeString(Extras.TITLE, MAX_TITLE_LENGTH)

        // Deterministic fallback ID if `id` is omitted or blank
        val id = extras.getSafeString(Extras.ID, MAX_ID_LENGTH)
            ?: templateSlug?.let { "noti_${sanitizeKey(it)}" }
            ?: rawTitle?.let { "noti_${it.hashCode() and 0x7FFFFFFF}" }
            ?: "noti_default"

        return when (action) {
            ACTION_CANCEL -> NotiCommand.Cancel(id = id)
            ACTION_SHOW, ACTION_SHOW_NOTIFICATION, ACTION_UPDATE -> {
                val isUpdateOnly = action == ACTION_UPDATE
                val customActions = buildList {
                    val a1Label = extras.getSafeString(Extras.ACTION_1_LABEL, MAX_ACTION_LABEL_LENGTH)
                    if (!a1Label.isNullOrBlank()) {
                        val a1Id = extras.getSafeString(Extras.ACTION_1_ID, MAX_ACTION_ID_LENGTH) ?: "btn_1"
                        add(NotiAction(a1Id, a1Label))
                    }
                    val a2Label = extras.getSafeString(Extras.ACTION_2_LABEL, MAX_ACTION_LABEL_LENGTH)
                    if (!a2Label.isNullOrBlank()) {
                        val a2Id = extras.getSafeString(Extras.ACTION_2_ID, MAX_ACTION_ID_LENGTH) ?: "btn_2"
                        add(NotiAction(a2Id, a2Label))
                    }
                    val a3Label = extras.getSafeString(Extras.ACTION_3_LABEL, MAX_ACTION_LABEL_LENGTH)
                    if (!a3Label.isNullOrBlank()) {
                        val a3Id = extras.getSafeString(Extras.ACTION_3_ID, MAX_ACTION_ID_LENGTH) ?: "btn_3"
                        add(NotiAction(a3Id, a3Label))
                    }
                }.takeIf { it.isNotEmpty() }

                NotiCommand.ShowOrUpdate(
                    id = id,
                    isUpdate = isUpdateOnly,
                    templateSlug = templateSlug,
                    title = rawTitle,
                    body = extras.getSafeString(Extras.BODY, MAX_BODY_LENGTH),
                    metadata = extras.getSafeString(Extras.METADATA, MAX_METADATA_LENGTH),
                    statusBadge = extras.getSafeString(Extras.STATUS_BADGE, MAX_BADGE_LENGTH),
                    accentColorHex = extras.getSafeString(Extras.ACCENT_COLOR, MAX_COLOR_HEX_LENGTH),
                    progressMode = extras.getSafeString(Extras.PROGRESS_MODE, 32)?.let {
                        ProgressMode.fromWireValue(it)
                    },
                    progress = extras.getSafeInt(Extras.PROGRESS)?.coerceIn(0, 100),
                    durationMinutes = extras.getSafeDurationMinutes(Extras.DURATION),
                    startTimeEpochMillis = extras.getSafeStartTimeMillis(Extras.START_TIME),
                    autoDismiss = extras.getSafeBoolean(Extras.AUTO_DISMISS),
                    finishText = extras.getSafeString(Extras.FINISH_TEXT, MAX_BODY_LENGTH),
                    showInput = extras.getSafeBoolean(Extras.SHOW_INPUT),
                    inputHint = extras.getSafeString(Extras.INPUT_HINT, MAX_HINT_LENGTH),
                    actionOverrides = customActions
                )
            }
            else -> null
        }
    }

    private fun sanitizeKey(raw: String): String {
        return raw.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .take(MAX_SLUG_LENGTH)
            .ifEmpty { "default" }
    }

    @Suppress("DEPRECATION")
    private fun Bundle.getSafeRaw(key: String): Any? {
        return try {
            if (!containsKey(key)) null else get(key)
        } catch (_: Throwable) {
            null
        }
    }

    private fun Bundle.getSafeString(key: String, maxLength: Int = MAX_BODY_LENGTH): String? {
        val raw = getSafeRaw(key) ?: return null
        val trimmed = raw.toString().trim()
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) return null
        return if (trimmed.length > maxLength) trimmed.take(maxLength) else trimmed
    }

    private fun Bundle.getSafeInt(key: String): Int? {
        return when (val raw = getSafeRaw(key)) {
            is Number -> {
                val d = raw.toDouble()
                if (d.isNaN() || d.isInfinite()) null else d.roundToInt()
            }
            is CharSequence -> {
                val cleaned = raw.toString()
                    .trim()
                    .removeSuffix("%")
                    .trim()
                cleaned.toIntOrNull() ?: cleaned.toDoubleOrNull()
                    ?.takeIf { !it.isNaN() && !it.isInfinite() }
                    ?.roundToInt()
            }
            else -> null
        }
    }

    private fun Bundle.getSafeBoolean(key: String): Boolean? {
        return when (val raw = getSafeRaw(key)) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is CharSequence -> when (raw.toString().trim().lowercase(Locale.ROOT)) {
                "true", "1", "yes", "y", "on", "enabled", "t" -> true
                "false", "0", "no", "n", "off", "disabled", "f" -> false
                else -> null
            }
            else -> null
        }
    }

    /**
     * Supports duration in minutes as an Int/Double (e.g. 45 or 45.0), a numeric String ("45", "45m"),
     * or an "HH:mm" formatted String (e.g. "01:30" -> 90 minutes).
     */
    private fun Bundle.getSafeDurationMinutes(key: String): Int? {
        return when (val raw = getSafeRaw(key)) {
            is Number -> {
                val d = raw.toDouble()
                if (d.isNaN() || d.isInfinite()) null else d.roundToInt().coerceIn(1, MAX_DURATION_MINUTES)
            }
            is CharSequence -> {
                val trimmed = raw.toString().trim()
                if (trimmed.contains(":")) {
                    val parts = trimmed.split(":")
                    val hours = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
                    val minutes = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                    (hours * 60 + minutes).takeIf { it > 0 }?.coerceIn(1, MAX_DURATION_MINUTES)
                } else {
                    val numericPart = trimmed
                        .lowercase(Locale.ROOT)
                        .removeSuffix("minutes")
                        .removeSuffix("minute")
                        .removeSuffix("mins")
                        .removeSuffix("min")
                        .removeSuffix("m")
                        .trim()
                    val parsed = numericPart.toIntOrNull()
                        ?: numericPart.toDoubleOrNull()
                            ?.takeIf { !it.isNaN() && !it.isInfinite() }
                            ?.roundToInt()
                    parsed?.coerceIn(1, MAX_DURATION_MINUTES)
                }
            }
            else -> null
        }
    }

    /**
     * Parses `start_time` in epoch millis, epoch seconds, `HH:mm` (today's date), or ISO-8601 local date-time.
     * Returns null if omitted or unparseable, which defaults to `System.currentTimeMillis()`.
     */
    private fun Bundle.getSafeStartTimeMillis(key: String): Long? {
        val rawObj = getSafeRaw(key) ?: return null
        if (rawObj is Number) {
            val value = rawObj.toLong()
            return if (value in 1_000_000_000L..9_999_999_999L) value * 1_000L else value.takeIf { it > 0L }
        }
        val raw = rawObj.toString().trim().takeIf { it.isNotEmpty() } ?: return null
        raw.toLongOrNull()?.let { value ->
            return if (value in 1_000_000_000L..9_999_999_999L) value * 1_000L else value.takeIf { it > 0L }
        }
        return try {
            if (raw.length <= 8 && raw.contains(":")) {
                val pattern = if (raw.count { it == ':' } == 2) "H:mm:ss" else "H:mm"
                val localTime = LocalTime.parse(raw, DateTimeFormatter.ofPattern(pattern))
                LocalDateTime.of(LocalDate.now(), localTime)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } else {
                LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        } catch (_: Exception) {
            null
        }
    }
}

