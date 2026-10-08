package com.notiforge.app.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notiforge.app.NotiEngineApp
import com.notiforge.app.data.local.EventLogEntity
import com.notiforge.app.domain.engine.NotificationRenderEngine
import com.notiforge.app.domain.model.NotiCommand
import com.notiforge.app.domain.model.NotiPayload
import com.notiforge.app.domain.model.NotiTemplate
import com.notiforge.app.domain.model.PresetCatalog
import com.notiforge.app.domain.model.ProgressMode
import com.notiforge.app.ipc.NotiContract
import com.notiforge.app.receiver.NotiReceiver
import com.notiforge.app.service.TimerForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotiUiState(
    val templates: List<NotiTemplate> = PresetCatalog.defaultPresets,
    val recentLogs: List<EventLogEntity> = emptyList(),
    val editingTemplate: NotiTemplate? = null,
    val cheatSheetTemplate: NotiTemplate? = null,
    val snackbarMessage: String? = null
)

class NotiViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NotiEngineApp
    private val repository = app.repository

    private val editingTemplateFlow = MutableStateFlow<NotiTemplate?>(null)
    private val cheatSheetTemplateFlow = MutableStateFlow<NotiTemplate?>(null)
    private val snackbarMessageFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NotiUiState> = combine(
        repository.templatesFlow,
        repository.recentLogsFlow,
        editingTemplateFlow,
        cheatSheetTemplateFlow,
        snackbarMessageFlow
    ) { templates, logs, editing, cheatSheet, snackbar ->
        NotiUiState(
            templates = templates.ifEmpty { PresetCatalog.defaultPresets },
            recentLogs = logs,
            editingTemplate = editing,
            cheatSheetTemplate = cheatSheet,
            snackbarMessage = snackbar
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotiUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensurePresetsSeeded()
        }
    }

    fun openNewTemplateEditor() {
        val blankTemplate = NotiTemplate(
            id = 0L,
            slug = "custom_tracker_${(100..999).random()}",
            name = "Custom Notification",
            description = "Personalized modular notification template for MacroDroid.",
            isPreset = false,
            blocks = listOf(
                com.notiforge.app.domain.model.NotiBlock.HeaderBlock(
                    statusBadge = "ACTIVE",
                    iconName = "notifications",
                    accentColorHex = "#4F46E5"
                ),
                com.notiforge.app.domain.model.NotiBlock.TextBlock(
                    title = "Custom Automation Task",
                    body = "Triggered dynamically from MacroDroid via BroadcastIntent."
                ),
                com.notiforge.app.domain.model.NotiBlock.DividerBlock(),
                com.notiforge.app.domain.model.NotiBlock.MetadataBlock(
                    text = "Workspace • Ready"
                ),
                com.notiforge.app.domain.model.NotiBlock.ProgressBlock(
                    progressMode = ProgressMode.MANUAL,
                    progress = 50,
                    durationMinutes = 25
                ),
                com.notiforge.app.domain.model.NotiBlock.ActionsBlock(
                    actions = listOf(
                        com.notiforge.app.domain.model.NotiAction("btn_primary", "Run Action"),
                        com.notiforge.app.domain.model.NotiAction("btn_dismiss", "Dismiss")
                    )
                )
            )
        )
        editingTemplateFlow.value = blankTemplate
    }

    fun openTemplateEditor(template: NotiTemplate) {
        editingTemplateFlow.value = template
    }

    fun closeTemplateEditor() {
        editingTemplateFlow.value = null
    }

    fun saveTemplate(template: NotiTemplate) {
        viewModelScope.launch {
            repository.saveTemplate(template)
            editingTemplateFlow.value = null
            snackbarMessageFlow.value = "Saved template \"${template.name}\" (${template.slug})"
        }
    }

    fun duplicateTemplate(template: NotiTemplate) {
        viewModelScope.launch {
            repository.duplicateTemplate(template)
            snackbarMessageFlow.value = "Cloned \"${template.name}\" into Custom Templates"
        }
    }

    fun deleteTemplate(template: NotiTemplate) {
        viewModelScope.launch {
            repository.deleteTemplate(template)
            snackbarMessageFlow.value = "Deleted template \"${template.name}\""
        }
    }

    fun openCheatSheet(template: NotiTemplate) {
        cheatSheetTemplateFlow.value = template
    }

    fun closeCheatSheet() {
        cheatSheetTemplateFlow.value = null
    }

    /**
     * Fires a live test notification using the exact [NotiTemplate] configuration,
     * routing through [NotificationRenderEngine] or [TimerForegroundService] and logging the event.
     */
    fun triggerTestNotification(template: NotiTemplate) {
        viewModelScope.launch {
            NotificationRenderEngine.ensureChannelsCreated(app)

            val tag = "test_${template.slug}"
            val command = NotiCommand.ShowOrUpdate(
                id = tag,
                templateSlug = template.slug
            )
            val payload = NotiPayload.fromTemplateAndCommand(template, command)

            repository.logEvent(
                direction = "INCOMING",
                action = "${NotiContract.ACTION_SHOW_NOTIFICATION} (In-App Test)",
                notificationTag = tag,
                summary = "template=${template.slug} | mode=${template.progressMode.wireValue} | title=\"${template.defaultTitle}\""
            )

            if (!NotificationRenderEngine.canPostNotifications(app)) {
                snackbarMessageFlow.value = "Notification permission required. Please grant permission to test notifications."
                return@launch
            }

            if (payload.progressMode == ProgressMode.AUTO_TIMER) {
                TimerForegroundService.startOrUpdateTimer(app, payload)
                snackbarMessageFlow.value = "Started live countdown notification (\"${template.name}\")"
            } else {
                TimerForegroundService.stopTimer(app, tag, cancelNotification = false)
                NotificationRenderEngine.showNotification(app, payload, isOngoingTimer = false)
                snackbarMessageFlow.value = "Posted test notification (\"${template.name}\")"
            }
        }
    }

    /**
     * Sends an actual explicit BroadcastIntent to [NotiReceiver] using the single `"json"` extra
     * to verify the end-to-end JSON IPC pipeline.
     */
    fun fireSimulatedBroadcastIntent(template: NotiTemplate) {
        NotificationRenderEngine.ensureChannelsCreated(app)
        val tag = "ipc_${template.slug}"
        val jsonPayload = NotiContract.buildTemplateJsonExtra(template, idOverride = tag)
        val broadcastIntent = Intent(app, NotiReceiver::class.java).apply {
            action = NotiContract.ACTION_SHOW_NOTIFICATION
            putExtra(NotiContract.Extras.JSON, jsonPayload)
        }
        app.sendBroadcast(broadcastIntent)
        snackbarMessageFlow.value = "Dispatched JSON BroadcastIntent for \"${template.name}\""
    }

    fun cancelNotificationForTemplate(template: NotiTemplate) {
        val tagsToCancel = listOf(
            "test_${template.slug}",
            "ipc_${template.slug}",
            "noti_${template.slug}",
            template.slug
        )
        tagsToCancel.forEach { tag ->
            TimerForegroundService.stopTimer(app, tag, cancelNotification = true)
            NotificationRenderEngine.cancelNotification(app, tag)
        }
        snackbarMessageFlow.value = "Dismissed active notifications for \"${template.name}\""
    }

    fun exportAllTemplatesJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val exported = repository.exportTemplatesJson()
            onResult(exported)
        }
    }

    fun importTemplatesFromJson(rawJson: String) {
        viewModelScope.launch {
            try {
                val count = repository.importTemplatesJson(rawJson)
                snackbarMessageFlow.value = "Successfully imported $count template(s)"
            } catch (e: Exception) {
                snackbarMessageFlow.value = "Invalid JSON format: ${e.localizedMessage ?: "Parse error"}"
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            snackbarMessageFlow.value = "Cleared IPC event history"
        }
    }

    fun clearSnackbar() {
        snackbarMessageFlow.value = null
    }

    fun postSnackbar(message: String) {
        snackbarMessageFlow.value = message
    }
}
