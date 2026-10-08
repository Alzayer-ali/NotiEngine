package com.notiforge.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notiforge.app.domain.model.NotiAction
import com.notiforge.app.domain.model.NotiTemplate
import com.notiforge.app.domain.model.ProgressMode
import com.notiforge.app.ui.components.LiveNotificationPreviewCard
import com.notiforge.app.ui.components.parseComposeColor
import com.notiforge.app.ui.components.resolvePreviewIcon
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

private val editorStateJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val NotiTemplateSaver: Saver<NotiTemplate, String> = Saver(
    save = { template -> editorStateJson.encodeToString(template) },
    restore = { raw ->
        try {
            editorStateJson.decodeFromString<NotiTemplate>(raw)
        } catch (_: Exception) {
            null
        }
    }
)

private fun sanitizeTemplateDraft(draft: NotiTemplate): NotiTemplate {
    val cleanName = draft.name.trim().ifBlank { "Custom Notification" }
    val cleanSlug = if (draft.isPreset) {
        draft.slug
    } else {
        draft.slug.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "custom_template" }
    }
    val cleanActions = draft.actions.take(3).mapIndexed { index, action ->
        NotiAction(
            id = action.id.trim().lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifBlank { "btn_${index + 1}" },
            label = action.label.trim().ifBlank { "Action ${index + 1}" }
        )
    }
    return draft.copy(
        name = cleanName,
        slug = cleanSlug,
        defaultTitle = draft.defaultTitle.trim().ifBlank { cleanName },
        defaultDurationMinutes = draft.defaultDurationMinutes.coerceIn(1, 1440),
        defaultProgress = draft.defaultProgress.coerceIn(0, 100),
        finishText = draft.finishText.trim().ifBlank { "Completed" },
        actions = cleanActions
    )
}

private val CuratedColorSwatches = listOf(
    "#4F46E5" to "Indigo",
    "#0D9488" to "Teal",
    "#2563EB" to "Blue",
    "#7C3AED" to "Violet",
    "#16A34A" to "Emerald",
    "#EA580C" to "Amber",
    "#DB2777" to "Rose",
    "#DC2626" to "Crimson"
)

private val CuratedIconOptions = listOf(
    "school" to "Lecture",
    "edit_note" to "Note",
    "sync" to "Sync",
    "tune" to "Controls",
    "verified" to "Status",
    "bolt" to "Alert",
    "notifications" to "Bell"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorScreen(
    initialTemplate: NotiTemplate,
    onBack: () -> Unit,
    onSave: (NotiTemplate) -> Unit,
    onTestLive: (NotiTemplate) -> Unit
) {
    var draft by rememberSaveable(
        initialTemplate.id,
        initialTemplate.slug,
        stateSaver = NotiTemplateSaver
    ) {
        mutableStateOf(initialTemplate)
    }
    var durationInputText by rememberSaveable(initialTemplate.id, initialTemplate.slug) {
        mutableStateOf(initialTemplate.defaultDurationMinutes.toString())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                title = {
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        Text(
                            text = if (initialTemplate.id == 0L && !initialTemplate.isPreset) {
                                "New Template"
                            } else {
                                "Edit Template"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = draft.name.ifBlank { "Untitled Template" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Back") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Test Live Notification") } },
                        state = rememberTooltipState()
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val sanitized = sanitizeTemplateDraft(draft)
                                draft = sanitized
                                onTestLive(sanitized)
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test", maxLines = 1)
                        }
                    }

                    Button(
                        onClick = {
                            val sanitized = sanitizeTemplateDraft(draft)
                            draft = sanitized
                            onSave(sanitized)
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", maxLines = 1)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Pinned Real-Time Notification Preview at the Top
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Live Preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Updates as you type",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LiveNotificationPreviewCard(template = draft)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Scrollable Modular Block Configuration
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Template Identity Card
                EditorSectionCard(
                    title = "General Identity",
                    subtitle = "Display name, description, and MacroDroid template key"
                ) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        label = { Text("Template Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = draft.slug,
                        onValueChange = { newSlug ->
                            draft = draft.copy(
                                slug = newSlug.lowercase().replace(Regex("[^a-z0-9_]+"), "_")
                            )
                        },
                        label = { Text("Template Key (MacroDroid extra: template)") },
                        singleLine = true,
                        enabled = !draft.isPreset,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = draft.description,
                        onValueChange = { draft = draft.copy(description = it) },
                        label = { Text("Short Description") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Section 2: Header & Styling Block
                EditorSectionCard(
                    title = "Header & Appearance",
                    subtitle = "Icon, status badge pill, and accent color"
                ) {
                    EditorToggleSurface(
                        title = "Show Header & Status Pill",
                        subtitle = "Displays top status badge and category icon",
                        checked = draft.showHeader,
                        onCheckedChange = { draft = draft.copy(showHeader = it) }
                    )

                    if (draft.showHeader) {
                        OutlinedTextField(
                            value = draft.statusBadge,
                            onValueChange = { draft = draft.copy(statusBadge = it) },
                            label = { Text("Status Badge Text (e.g. IN SESSION, LIVE)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text(
                        text = "Notification Icon",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CuratedIconOptions.forEach { (iconKey, label) ->
                            val selected = draft.iconName.equals(iconKey, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = { draft = draft.copy(iconName = iconKey) },
                                label = { Text(label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = resolvePreviewIcon(iconKey),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }

                    EditorToggleSurface(
                        title = "Use Dynamic System Accent",
                        subtitle = "Follows Android 12+ Material You wallpaper palette",
                        checked = draft.useDynamicColor,
                        onCheckedChange = { draft = draft.copy(useDynamicColor = it) }
                    )

                    if (!draft.useDynamicColor) {
                        Text(
                            text = "Accent Color",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CuratedColorSwatches.forEach { (hex, _) ->
                                val swatchColor = parseComposeColor(
                                    hex = hex,
                                    useDynamic = false,
                                    fallback = MaterialTheme.colorScheme.primary
                                )
                                val isSelected = draft.accentColorHex.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                Color.Transparent
                                            },
                                            shape = CircleShape
                                        )
                                        .clickable { draft = draft.copy(accentColorHex = hex) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected color",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = draft.accentColorHex,
                            onValueChange = { draft = draft.copy(accentColorHex = it) },
                            label = { Text("Custom Hex Color (#RRGGBB)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Section 3: Content & Metadata Detail Block
                EditorSectionCard(
                    title = "Content & Details",
                    subtitle = "Default headline, body text, and metadata box"
                ) {
                    OutlinedTextField(
                        value = draft.defaultTitle,
                        onValueChange = { draft = draft.copy(defaultTitle = it) },
                        label = { Text("Default Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = draft.defaultBody,
                        onValueChange = { draft = draft.copy(defaultBody = it) },
                        label = { Text("Default Body Text") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    EditorToggleSurface(
                        title = "Show Metadata Box",
                        subtitle = "Displays location, instructor, or sensor status container",
                        checked = draft.showDetailBlock,
                        onCheckedChange = { draft = draft.copy(showDetailBlock = it) }
                    )

                    if (draft.showDetailBlock) {
                        OutlinedTextField(
                            value = draft.defaultMetadata,
                            onValueChange = { draft = draft.copy(defaultMetadata = it) },
                            label = { Text("Default Metadata") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Section 4: Progress & Autonomous Timer Block
                EditorSectionCard(
                    title = "Progress & Timer",
                    subtitle = "Static card, manual percentage bar, or live countdown timer"
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProgressMode.entries.forEach { mode ->
                            FilterChip(
                                selected = draft.progressMode == mode,
                                onClick = { draft = draft.copy(progressMode = mode) },
                                label = { Text(mode.displayName) }
                            )
                        }
                    }

                    if (draft.progressMode == ProgressMode.MANUAL) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Default Progress: ${draft.defaultProgress}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Slider(
                                    value = draft.defaultProgress.toFloat(),
                                    onValueChange = {
                                        draft = draft.copy(defaultProgress = it.roundToInt().coerceIn(0, 100))
                                    },
                                    valueRange = 0f..100f
                                )
                            }
                        }
                    }

                    if (draft.progressMode == ProgressMode.AUTO_TIMER) {
                        OutlinedTextField(
                            value = durationInputText,
                            onValueChange = { text ->
                                durationInputText = text
                                val parsed = text.toIntOrNull()?.coerceIn(1, 1440)
                                if (parsed != null) {
                                    draft = draft.copy(defaultDurationMinutes = parsed)
                                }
                            },
                            label = { Text("Default Countdown Duration (Minutes)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = draft.finishText,
                            onValueChange = { draft = draft.copy(finishText = it) },
                            label = { Text("Completion Message") },
                            supportingText = {
                                Text("Shown when countdown finishes if Auto-Dismiss is off")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    EditorToggleSurface(
                        title = "Auto-Dismiss on Completion",
                        subtitle = "Automatically closes notification when timer ends or reply is sent",
                        checked = draft.autoDismiss,
                        onCheckedChange = { draft = draft.copy(autoDismiss = it) }
                    )
                }

                // Section 5: Action Buttons & Inline RemoteInput Block
                EditorSectionCard(
                    title = "Interactive Actions & Reply",
                    subtitle = "Up to 3 callback buttons and inline text reply"
                ) {
                    EditorToggleSurface(
                        title = "Enable Action Buttons",
                        subtitle = "Sends button click events back to MacroDroid",
                        checked = draft.showActionButtons,
                        onCheckedChange = { enabled ->
                            val seededActions = if (enabled && draft.actions.isEmpty()) {
                                listOf(NotiAction("btn_1", "Action 1"))
                            } else {
                                draft.actions
                            }
                            draft = draft.copy(showActionButtons = enabled, actions = seededActions)
                        }
                    )

                    if (draft.showActionButtons) {
                        draft.actions.forEachIndexed { index, actionItem ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = actionItem.label,
                                    onValueChange = { newLabel ->
                                        val updated = draft.actions.toMutableList()
                                        updated[index] = actionItem.copy(label = newLabel)
                                        draft = draft.copy(actions = updated)
                                    },
                                    label = { Text("Button #${index + 1} Label") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = actionItem.id,
                                    onValueChange = { newId ->
                                        val updated = draft.actions.toMutableList()
                                        updated[index] = actionItem.copy(
                                            id = newId.lowercase().replace(Regex("[^a-z0-9_]+"), "_")
                                        )
                                        draft = draft.copy(actions = updated)
                                    },
                                    label = { Text("Action ID") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val updated = draft.actions.toMutableList().apply { removeAt(index) }
                                        draft = draft.copy(actions = updated)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove button"
                                    )
                                }
                            }
                        }

                        if (draft.actions.size < 3) {
                            OutlinedButton(
                                onClick = {
                                    val nextNum = draft.actions.size + 1
                                    val updated = draft.actions + NotiAction(
                                        id = "btn_$nextNum",
                                        label = "Action $nextNum"
                                    )
                                    draft = draft.copy(actions = updated)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Button (${draft.actions.size}/3)")
                            }
                        }
                    }

                    EditorToggleSurface(
                        title = "Enable Inline Text Reply",
                        subtitle = "Captures text in the notification and forwards it to MacroDroid",
                        checked = draft.showRemoteInput,
                        onCheckedChange = { draft = draft.copy(showRemoteInput = it) }
                    )

                    if (draft.showRemoteInput) {
                        OutlinedTextField(
                            value = draft.inputHint,
                            onValueChange = { draft = draft.copy(inputHint = it) },
                            label = { Text("Reply Placeholder Hint") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun EditorSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun EditorToggleSurface(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
