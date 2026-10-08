package com.notiforge.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.notiforge.app.domain.model.NotiAction
import com.notiforge.app.domain.model.NotiTemplate
import com.notiforge.app.domain.model.ProgressMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "templates",
    indices = [Index(value = ["slug"], unique = true)]
)
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val slug: String,
    val name: String,
    val description: String,
    val isPreset: Boolean,
    val showHeader: Boolean,
    val statusBadge: String,
    val iconName: String,
    val accentColorHex: String,
    val useDynamicColor: Boolean,
    val progressMode: String,
    val defaultProgress: Int,
    val defaultDurationMinutes: Int,
    val autoDismiss: Boolean,
    val finishText: String,
    val showDetailBlock: Boolean,
    val defaultTitle: String,
    val defaultBody: String,
    val defaultMetadata: String,
    val showActionButtons: Boolean,
    val actionsJson: String,
    val showRemoteInput: Boolean,
    val inputHint: String,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
) {
    fun toDomain(json: Json = defaultJson): NotiTemplate {
        val parsedActions = try {
            if (actionsJson.isBlank()) emptyList()
            else json.decodeFromString<List<NotiAction>>(actionsJson)
        } catch (_: Exception) {
            emptyList()
        }

        return NotiTemplate(
            id = id,
            slug = slug,
            name = name,
            description = description,
            isPreset = isPreset,
            showHeader = showHeader,
            statusBadge = statusBadge,
            iconName = iconName,
            accentColorHex = accentColorHex,
            useDynamicColor = useDynamicColor,
            progressMode = ProgressMode.fromWireValue(progressMode),
            defaultProgress = defaultProgress,
            defaultDurationMinutes = defaultDurationMinutes,
            autoDismiss = autoDismiss,
            finishText = finishText,
            showDetailBlock = showDetailBlock,
            defaultTitle = defaultTitle,
            defaultBody = defaultBody,
            defaultMetadata = defaultMetadata,
            showActionButtons = showActionButtons,
            actions = parsedActions,
            showRemoteInput = showRemoteInput,
            inputHint = inputHint
        )
    }

    companion object {
        private val defaultJson = Json { ignoreUnknownKeys = true }

        fun fromDomain(template: NotiTemplate, json: Json = defaultJson): TemplateEntity {
            return TemplateEntity(
                id = template.id,
                slug = template.slug,
                name = template.name,
                description = template.description,
                isPreset = template.isPreset,
                showHeader = template.showHeader,
                statusBadge = template.statusBadge,
                iconName = template.iconName,
                accentColorHex = template.accentColorHex,
                useDynamicColor = template.useDynamicColor,
                progressMode = template.progressMode.wireValue,
                defaultProgress = template.defaultProgress,
                defaultDurationMinutes = template.defaultDurationMinutes,
                autoDismiss = template.autoDismiss,
                finishText = template.finishText,
                showDetailBlock = template.showDetailBlock,
                defaultTitle = template.defaultTitle,
                defaultBody = template.defaultBody,
                defaultMetadata = template.defaultMetadata,
                showActionButtons = template.showActionButtons,
                actionsJson = json.encodeToString(template.actions),
                showRemoteInput = template.showRemoteInput,
                inputHint = template.inputHint,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
    }
}
