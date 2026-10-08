package com.notiforge.app.domain.model

import com.notiforge.app.ipc.NotiContract
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonNames

object ProgressModeSerializer : KSerializer<ProgressMode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ProgressMode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ProgressMode) {
        encoder.encodeString(value.wireValue)
    }

    override fun deserialize(decoder: Decoder): ProgressMode {
        return ProgressMode.fromWireValue(decoder.decodeString())
    }
}

/**
 * Progress bar rendering mode for NotiEngine notifications.
 */
@Serializable(with = ProgressModeSerializer::class)
enum class ProgressMode(val wireValue: String, val displayName: String) {
    NONE(NotiContract.ProgressModes.NONE, "None"),
    MANUAL(NotiContract.ProgressModes.MANUAL, "Manual (0–100%)"),
    AUTO_TIMER(NotiContract.ProgressModes.AUTO_TIMER, "Autonomous Countdown Timer");

    companion object {
        fun fromWireValue(value: String?): ProgressMode {
            return when (value?.trim()?.lowercase()) {
                NotiContract.ProgressModes.MANUAL -> MANUAL
                NotiContract.ProgressModes.AUTO_TIMER, "timer", "auto" -> AUTO_TIMER
                else -> NONE
            }
        }
    }
}

/**
 * Represents a customizable action button displayed in the notification's Action Block.
 */
@Serializable
data class NotiAction(
    val id: String,
    val label: String
)

/**
 * Domain model representing a modular Notification Template (either a curated preset or user-created).
 */
@Serializable
data class NotiTemplate(
    val id: Long = 0L,
    val slug: String,
    val name: String,
    val description: String,
    val isPreset: Boolean = false,

    // Header Block
    val showHeader: Boolean = true,
    val statusBadge: String = "LIVE",
    val iconName: String = "notifications",
    val accentColorHex: String = "#6750A4",
    val useDynamicColor: Boolean = false,

    // Progress Block
    val progressMode: ProgressMode = ProgressMode.NONE,
    val defaultProgress: Int = 65,
    val defaultDurationMinutes: Int = 45,
    val autoDismiss: Boolean = false,
    val finishText: String = "Completed",

    // Detail Block
    val showDetailBlock: Boolean = true,
    val defaultTitle: String = "NotiEngine Alert",
    val defaultBody: String = "Triggered via BroadcastIntent",
    val defaultMetadata: String = "",

    // Action & Input Block
    val showActionButtons: Boolean = false,
    val actions: List<NotiAction> = emptyList(),
    val showRemoteInput: Boolean = false,
    val inputHint: String = "Type a quick note..."
)

/**
 * Parsed incoming command from `NotiReceiver`.
 */
@Serializable
sealed interface NotiCommand {
    val id: String

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class ShowOrUpdate(
        override val id: String = "",
        @Transient val isUpdate: Boolean = false,
        @SerialName("template")
        @JsonNames("templateSlug", "template_slug")
        val templateSlug: String? = null,
        val title: String? = null,
        val body: String? = null,
        val metadata: String? = null,
        @SerialName("status_badge")
        @JsonNames("statusBadge")
        val statusBadge: String? = null,
        @SerialName("accent_color")
        @JsonNames("accentColorHex", "accentColor")
        val accentColorHex: String? = null,
        @SerialName("progress_mode")
        @JsonNames("progressMode")
        val progressMode: ProgressMode? = null,
        val progress: Int? = null,
        @SerialName("duration")
        @JsonNames("durationMinutes", "duration_minutes")
        val durationMinutes: Int? = null,
        @SerialName("start_time")
        @JsonNames("startTimeEpochMillis", "start_time_epoch_millis")
        val startTimeEpochMillis: Long? = null,
        @SerialName("auto_dismiss")
        @JsonNames("autoDismiss")
        val autoDismiss: Boolean? = null,
        @SerialName("finish_text")
        @JsonNames("finishText")
        val finishText: String? = null,
        @SerialName("show_input")
        @JsonNames("showInput", "showRemoteInput", "show_remote_input")
        val showInput: Boolean? = null,
        @SerialName("input_hint")
        @JsonNames("inputHint")
        val inputHint: String? = null,
        @SerialName("actions")
        @JsonNames("actionOverrides", "action_overrides")
        val actionOverrides: List<NotiAction>? = null
    ) : NotiCommand

    @Serializable
    data class Cancel(override val id: String = "") : NotiCommand
}

/**
 * Resolved notification payload ready to be rendered by the Notification Render Engine
 * or tracked by `TimerForegroundService`.
 */
@Serializable
data class NotiPayload(
    val notificationTag: String,
    val notificationId: Int,
    val templateSlug: String,
    val showHeader: Boolean,
    val statusBadge: String,
    val iconName: String,
    val accentColorHex: String,
    val useDynamicColor: Boolean,
    val title: String,
    val body: String,
    val showDetailBlock: Boolean,
    val metadata: String,
    val progressMode: ProgressMode,
    val progress: Int,
    val progressStatusText: String,
    val durationMinutes: Int,
    val startTimeEpochMillis: Long,
    val autoDismiss: Boolean,
    val finishText: String,
    val showActionButtons: Boolean,
    val actions: List<NotiAction>,
    val showRemoteInput: Boolean,
    val inputHint: String,
    val isTimerCompleted: Boolean = false,
    val replyStatusText: String? = null
) {
    companion object {
        fun notificationIdForTag(tag: String): Int {
            val normalized = tag.trim().ifEmpty { "noti_default" }
            val rawHash = normalized.hashCode() and 0x7FFFFFFF
            return if (rawHash < 1001) rawHash + 1001 else rawHash
        }

        fun fromTemplateAndCommand(
            template: NotiTemplate,
            command: NotiCommand.ShowOrUpdate
        ): NotiPayload {
            val safeTag = command.id.trim().take(NotiContract.MAX_ID_LENGTH).ifEmpty {
                "noti_${template.slug.ifBlank { "default" }}"
            }
            val resolvedMode = command.progressMode ?: template.progressMode
            val resolvedProgress = (command.progress ?: template.defaultProgress).coerceIn(0, 100)
            val resolvedDuration = (command.durationMinutes ?: template.defaultDurationMinutes)
                .coerceIn(1, NotiContract.MAX_DURATION_MINUTES)
            val resolvedStartTime = command.startTimeEpochMillis ?: System.currentTimeMillis()

            val rawActions = command.actionOverrides ?: template.actions
            val resolvedActions = rawActions.take(3).mapIndexed { idx, action ->
                NotiAction(
                    id = action.id.trim().take(NotiContract.MAX_ACTION_ID_LENGTH).ifEmpty { "btn_${idx + 1}" },
                    label = action.label.trim().take(NotiContract.MAX_ACTION_LABEL_LENGTH).ifEmpty { "Action ${idx + 1}" }
                )
            }
            val resolvedShowInput = command.showInput ?: template.showRemoteInput
            val resolvedShowActions = if (command.actionOverrides != null) {
                resolvedActions.isNotEmpty()
            } else {
                template.showActionButtons && resolvedActions.isNotEmpty()
            }

            val initialProgressText = when (resolvedMode) {
                ProgressMode.NONE -> ""
                ProgressMode.MANUAL -> "$resolvedProgress%"
                ProgressMode.AUTO_TIMER -> "${resolvedDuration}m left"
            }

            val safeTitle = (command.title ?: template.defaultTitle)
                .trim()
                .take(NotiContract.MAX_TITLE_LENGTH)
                .ifEmpty { template.name.trim().take(NotiContract.MAX_TITLE_LENGTH).ifEmpty { "NotiEngine Alert" } }

            val safeBody = (command.body ?: template.defaultBody)
                .trim()
                .take(NotiContract.MAX_BODY_LENGTH)

            val safeMetadata = (command.metadata ?: template.defaultMetadata)
                .trim()
                .take(NotiContract.MAX_METADATA_LENGTH)

            val safeBadge = (command.statusBadge ?: template.statusBadge)
                .trim()
                .take(NotiContract.MAX_BADGE_LENGTH)

            val safeFinishText = (command.finishText ?: template.finishText)
                .trim()
                .take(NotiContract.MAX_BODY_LENGTH)
                .ifEmpty { "Completed" }

            val safeInputHint = (command.inputHint ?: template.inputHint)
                .trim()
                .take(NotiContract.MAX_HINT_LENGTH)

            return NotiPayload(
                notificationTag = safeTag,
                notificationId = notificationIdForTag(safeTag),
                templateSlug = template.slug.trim().take(NotiContract.MAX_SLUG_LENGTH).ifEmpty { "custom" },
                showHeader = template.showHeader,
                statusBadge = safeBadge,
                iconName = template.iconName.trim().ifEmpty { "notifications" },
                accentColorHex = (command.accentColorHex ?: template.accentColorHex)
                    .trim()
                    .take(NotiContract.MAX_COLOR_HEX_LENGTH)
                    .ifEmpty { "#4F46E5" },
                useDynamicColor = template.useDynamicColor,
                title = safeTitle,
                body = safeBody,
                showDetailBlock = template.showDetailBlock,
                metadata = safeMetadata,
                progressMode = resolvedMode,
                progress = if (resolvedMode == ProgressMode.AUTO_TIMER) 0 else resolvedProgress,
                progressStatusText = initialProgressText,
                durationMinutes = resolvedDuration,
                startTimeEpochMillis = resolvedStartTime,
                autoDismiss = command.autoDismiss ?: template.autoDismiss,
                finishText = safeFinishText,
                showActionButtons = resolvedShowActions,
                actions = resolvedActions,
                showRemoteInput = resolvedShowInput,
                inputHint = safeInputHint
            )
        }
    }
}
