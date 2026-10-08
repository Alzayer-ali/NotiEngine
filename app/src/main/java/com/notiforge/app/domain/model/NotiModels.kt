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
import java.util.UUID

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
 * Text alignment options for TextBlock.
 */
@Serializable
enum class TextAlignment(val wireValue: String, val displayName: String) {
    @SerialName("start")
    START("start", "Left"),
    @SerialName("center")
    CENTER("center", "Center"),
    @SerialName("end")
    END("end", "Right");

    companion object {
        fun fromWireValue(value: String?): TextAlignment {
            return when (value?.trim()?.lowercase()) {
                "center" -> CENTER
                "end", "right" -> END
                else -> START
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
 * Polymorphic, reorderable block component for building dynamic notifications.
 */
@Serializable
sealed interface NotiBlock {
    val blockId: String

    @Serializable
    @SerialName("header")
    data class HeaderBlock(
        override val blockId: String = "header",
        val statusBadge: String = "LIVE",
        val iconName: String = "notifications",
        val accentColorHex: String = "#6750A4",
        val useDynamicColor: Boolean = false
    ) : NotiBlock

    @Serializable
    @SerialName("text")
    data class TextBlock(
        override val blockId: String = "text",
        val title: String = "",
        val body: String = "",
        val subtext: String = "",
        val alignment: TextAlignment = TextAlignment.START
    ) : NotiBlock

    @Serializable
    @SerialName("metadata")
    data class MetadataBlock(
        override val blockId: String = UUID.randomUUID().toString().take(8),
        val label: String = "",
        val text: String = ""
    ) : NotiBlock

    @Serializable
    @SerialName("divider")
    data class DividerBlock(
        override val blockId: String = UUID.randomUUID().toString().take(8)
    ) : NotiBlock

    @Serializable
    @SerialName("progress")
    data class ProgressBlock(
        override val blockId: String = "progress",
        val progressMode: ProgressMode = ProgressMode.MANUAL,
        val progress: Int = 65,
        val durationMinutes: Int = 45,
        val autoDismiss: Boolean = false,
        val finishText: String = "Completed"
    ) : NotiBlock

    @Serializable
    @SerialName("actions")
    data class ActionsBlock(
        override val blockId: String = "actions",
        val actions: List<NotiAction> = emptyList()
    ) : NotiBlock

    @Serializable
    @SerialName("inline_reply")
    data class InlineReplyBlock(
        override val blockId: String = "reply",
        val inputHint: String = "Type a quick note..."
    ) : NotiBlock
}

/**
 * Helper to convert legacy flat notification fields into an ordered list of blocks.
 */
fun legacyToBlocks(
    showHeader: Boolean = true,
    statusBadge: String = "LIVE",
    iconName: String = "notifications",
    accentColorHex: String = "#6750A4",
    useDynamicColor: Boolean = false,
    progressMode: ProgressMode = ProgressMode.NONE,
    defaultProgress: Int = 65,
    defaultDurationMinutes: Int = 45,
    autoDismiss: Boolean = false,
    finishText: String = "Completed",
    showDetailBlock: Boolean = true,
    defaultTitle: String = "NotiEngine Alert",
    defaultBody: String = "Triggered via BroadcastIntent",
    defaultMetadata: String = "",
    showActionButtons: Boolean = false,
    actions: List<NotiAction> = emptyList(),
    showRemoteInput: Boolean = false,
    inputHint: String = "Type a quick note..."
): List<NotiBlock> {
    val list = mutableListOf<NotiBlock>()
    if (showHeader) {
        list.add(
            NotiBlock.HeaderBlock(
                statusBadge = statusBadge,
                iconName = iconName,
                accentColorHex = accentColorHex,
                useDynamicColor = useDynamicColor
            )
        )
    }
    list.add(
        NotiBlock.TextBlock(
            title = defaultTitle,
            body = defaultBody
        )
    )
    if (progressMode != ProgressMode.NONE) {
        list.add(
            NotiBlock.ProgressBlock(
                progressMode = progressMode,
                progress = defaultProgress,
                durationMinutes = defaultDurationMinutes,
                autoDismiss = autoDismiss,
                finishText = finishText
            )
        )
    }
    if (showDetailBlock && defaultMetadata.isNotBlank()) {
        list.add(
            NotiBlock.MetadataBlock(
                text = defaultMetadata
            )
        )
    }
    if (showActionButtons && actions.isNotEmpty()) {
        list.add(
            NotiBlock.ActionsBlock(
                actions = actions
            )
        )
    }
    if (showRemoteInput) {
        list.add(
            NotiBlock.InlineReplyBlock(
                inputHint = inputHint
            )
        )
    }
    return list
}

@Serializable
private data class NotiTemplateSurrogate(
    val id: Long = 0L,
    val slug: String = "",
    val name: String = "",
    val description: String = "",
    val isPreset: Boolean = false,
    val blocks: List<NotiBlock>? = null,
    val showHeader: Boolean? = null,
    val statusBadge: String? = null,
    val iconName: String? = null,
    val accentColorHex: String? = null,
    val useDynamicColor: Boolean? = null,
    val progressMode: ProgressMode? = null,
    val defaultProgress: Int? = null,
    val defaultDurationMinutes: Int? = null,
    val autoDismiss: Boolean? = null,
    val finishText: String? = null,
    val showDetailBlock: Boolean? = null,
    val defaultTitle: String? = null,
    val defaultBody: String? = null,
    val defaultMetadata: String? = null,
    val showActionButtons: Boolean? = null,
    val actions: List<NotiAction>? = null,
    val showRemoteInput: Boolean? = null,
    val inputHint: String? = null
)

object NotiTemplateSerializer : KSerializer<NotiTemplate> {
    override val descriptor: SerialDescriptor = NotiTemplateSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: NotiTemplate) {
        val surrogate = NotiTemplateSurrogate(
            id = value.id,
            slug = value.slug,
            name = value.name,
            description = value.description,
            isPreset = value.isPreset,
            blocks = value.blocks,
            showHeader = value.showHeader,
            statusBadge = value.statusBadge,
            iconName = value.iconName,
            accentColorHex = value.accentColorHex,
            useDynamicColor = value.useDynamicColor,
            progressMode = value.progressMode,
            defaultProgress = value.defaultProgress,
            defaultDurationMinutes = value.defaultDurationMinutes,
            autoDismiss = value.autoDismiss,
            finishText = value.finishText,
            showDetailBlock = value.showDetailBlock,
            defaultTitle = value.defaultTitle,
            defaultBody = value.defaultBody,
            defaultMetadata = value.defaultMetadata,
            showActionButtons = value.showActionButtons,
            actions = value.actions,
            showRemoteInput = value.showRemoteInput,
            inputHint = value.inputHint
        )
        encoder.encodeSerializableValue(NotiTemplateSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): NotiTemplate {
        val surrogate = decoder.decodeSerializableValue(NotiTemplateSurrogate.serializer())
        val resolvedBlocks = if (!surrogate.blocks.isNullOrEmpty()) {
            surrogate.blocks
        } else {
            legacyToBlocks(
                showHeader = surrogate.showHeader ?: true,
                statusBadge = surrogate.statusBadge ?: "LIVE",
                iconName = surrogate.iconName ?: "notifications",
                accentColorHex = surrogate.accentColorHex ?: "#6750A4",
                useDynamicColor = surrogate.useDynamicColor ?: false,
                progressMode = surrogate.progressMode ?: ProgressMode.NONE,
                defaultProgress = surrogate.defaultProgress ?: 65,
                defaultDurationMinutes = surrogate.defaultDurationMinutes ?: 45,
                autoDismiss = surrogate.autoDismiss ?: false,
                finishText = surrogate.finishText ?: "Completed",
                showDetailBlock = surrogate.showDetailBlock ?: true,
                defaultTitle = surrogate.defaultTitle ?: surrogate.name.ifBlank { "NotiEngine Alert" },
                defaultBody = surrogate.defaultBody ?: "",
                defaultMetadata = surrogate.defaultMetadata ?: "",
                showActionButtons = surrogate.showActionButtons ?: false,
                actions = surrogate.actions ?: emptyList(),
                showRemoteInput = surrogate.showRemoteInput ?: false,
                inputHint = surrogate.inputHint ?: "Type a quick note..."
            )
        }
        return NotiTemplate(
            id = surrogate.id,
            slug = surrogate.slug,
            name = surrogate.name,
            description = surrogate.description,
            isPreset = surrogate.isPreset,
            blocks = resolvedBlocks
        )
    }
}

/**
 * Domain model representing a modular Notification Template (either a curated preset or user-created).
 * Backed by an ordered list of [NotiBlock] canvas components with backward-compatible legacy getters.
 */
@Serializable(with = NotiTemplateSerializer::class)
data class NotiTemplate(
    val id: Long = 0L,
    val slug: String,
    val name: String,
    val description: String,
    val isPreset: Boolean = false,
    val blocks: List<NotiBlock> = emptyList()
) {
    // Backward compatibility helper properties
    val showHeader: Boolean get() = blocks.any { it is NotiBlock.HeaderBlock }
    val headerBlock: NotiBlock.HeaderBlock? get() = blocks.filterIsInstance<NotiBlock.HeaderBlock>().firstOrNull()
    val textBlock: NotiBlock.TextBlock? get() = blocks.filterIsInstance<NotiBlock.TextBlock>().firstOrNull()
    val progressBlock: NotiBlock.ProgressBlock? get() = blocks.filterIsInstance<NotiBlock.ProgressBlock>().firstOrNull()
    val actionsBlock: NotiBlock.ActionsBlock? get() = blocks.filterIsInstance<NotiBlock.ActionsBlock>().firstOrNull()
    val replyBlock: NotiBlock.InlineReplyBlock? get() = blocks.filterIsInstance<NotiBlock.InlineReplyBlock>().firstOrNull()

    val statusBadge: String get() = headerBlock?.statusBadge ?: "LIVE"
    val iconName: String get() = headerBlock?.iconName ?: "notifications"
    val accentColorHex: String get() = headerBlock?.accentColorHex ?: "#6750A4"
    val useDynamicColor: Boolean get() = headerBlock?.useDynamicColor ?: false
    val defaultTitle: String get() = textBlock?.title ?: name
    val defaultBody: String get() = textBlock?.body ?: ""
    val defaultMetadata: String get() = blocks.filterIsInstance<NotiBlock.MetadataBlock>().firstOrNull()?.text ?: ""
    val progressMode: ProgressMode get() = progressBlock?.progressMode ?: ProgressMode.NONE
    val defaultProgress: Int get() = progressBlock?.progress ?: 65
    val defaultDurationMinutes: Int get() = progressBlock?.durationMinutes ?: 45
    val autoDismiss: Boolean get() = progressBlock?.autoDismiss ?: false
    val finishText: String get() = progressBlock?.finishText ?: "Completed"
    val showActionButtons: Boolean get() = actionsBlock != null && (actionsBlock?.actions?.isNotEmpty() == true)
    val actions: List<NotiAction> get() = actionsBlock?.actions ?: emptyList()
    val showRemoteInput: Boolean get() = replyBlock != null
    val inputHint: String get() = replyBlock?.inputHint ?: "Type a quick note..."
    val showDetailBlock: Boolean get() = blocks.any { it is NotiBlock.MetadataBlock }
}

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
        val actionOverrides: List<NotiAction>? = null,
        val blocks: List<NotiBlock>? = null
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
    val blocks: List<NotiBlock> = emptyList(),
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

            // Resolve dynamic ordered blocks with runtime command overrides applied
            val baseBlocks = command.blocks ?: if (template.blocks.isNotEmpty()) {
                template.blocks
            } else {
                legacyToBlocks(
                    showHeader = template.showHeader,
                    statusBadge = safeBadge,
                    iconName = template.iconName,
                    accentColorHex = template.accentColorHex,
                    useDynamicColor = template.useDynamicColor,
                    progressMode = resolvedMode,
                    defaultProgress = resolvedProgress,
                    defaultDurationMinutes = resolvedDuration,
                    autoDismiss = command.autoDismiss ?: template.autoDismiss,
                    finishText = safeFinishText,
                    showDetailBlock = template.showDetailBlock,
                    defaultTitle = safeTitle,
                    defaultBody = safeBody,
                    defaultMetadata = safeMetadata,
                    showActionButtons = resolvedShowActions,
                    actions = resolvedActions,
                    showRemoteInput = resolvedShowInput,
                    inputHint = safeInputHint
                )
            }

            val mappedBlocks = baseBlocks.map { block ->
                when (block) {
                    is NotiBlock.HeaderBlock -> {
                        block.copy(
                            statusBadge = command.statusBadge ?: block.statusBadge,
                            accentColorHex = command.accentColorHex ?: block.accentColorHex
                        )
                    }
                    is NotiBlock.TextBlock -> {
                        block.copy(
                            title = command.title ?: block.title.ifBlank { safeTitle },
                            body = command.body ?: block.body.ifBlank { safeBody }
                        )
                    }
                    is NotiBlock.MetadataBlock -> {
                        block.copy(
                            text = command.metadata ?: block.text.ifBlank { safeMetadata }
                        )
                    }
                    is NotiBlock.DividerBlock -> block
                    is NotiBlock.ProgressBlock -> {
                        block.copy(
                            progressMode = resolvedMode,
                            progress = resolvedProgress,
                            durationMinutes = resolvedDuration,
                            autoDismiss = command.autoDismiss ?: block.autoDismiss,
                            finishText = command.finishText ?: block.finishText
                        )
                    }
                    is NotiBlock.ActionsBlock -> {
                        block.copy(actions = resolvedActions)
                    }
                    is NotiBlock.InlineReplyBlock -> {
                        block.copy(inputHint = safeInputHint)
                    }
                }
            }.toMutableList()

            // If command injected overrides for blocks not present in base template, append them
            if (command.metadata != null && mappedBlocks.none { it is NotiBlock.MetadataBlock }) {
                mappedBlocks.add(NotiBlock.MetadataBlock(text = safeMetadata))
            }
            if (command.actionOverrides != null && mappedBlocks.none { it is NotiBlock.ActionsBlock }) {
                mappedBlocks.add(NotiBlock.ActionsBlock(actions = resolvedActions))
            }
            if (command.showInput == true && mappedBlocks.none { it is NotiBlock.InlineReplyBlock }) {
                mappedBlocks.add(NotiBlock.InlineReplyBlock(inputHint = safeInputHint))
            }

            return NotiPayload(
                notificationTag = safeTag,
                notificationId = notificationIdForTag(safeTag),
                templateSlug = template.slug.trim().take(NotiContract.MAX_SLUG_LENGTH).ifEmpty { "custom" },
                blocks = mappedBlocks,
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
