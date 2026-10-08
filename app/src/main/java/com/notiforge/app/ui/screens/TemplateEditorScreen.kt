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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notiforge.app.domain.model.NotiAction
import com.notiforge.app.domain.model.NotiBlock
import com.notiforge.app.domain.model.NotiTemplate
import com.notiforge.app.domain.model.ProgressMode
import com.notiforge.app.domain.model.TextAlignment
import com.notiforge.app.ui.components.LiveNotificationPreviewCard
import com.notiforge.app.ui.components.parseComposeColor
import com.notiforge.app.ui.components.resolvePreviewIcon
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
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
    val cleanBlocks = draft.blocks.map { block ->
        when (block) {
            is NotiBlock.HeaderBlock -> block.copy(
                statusBadge = block.statusBadge.trim().ifBlank { "LIVE" },
                accentColorHex = block.accentColorHex.trim().ifBlank { "#4F46E5" }
            )
            is NotiBlock.TextBlock -> block.copy(
                title = block.title.trim().ifBlank { cleanName },
                body = block.body.trim(),
                subtext = block.subtext.trim()
            )
            is NotiBlock.MetadataBlock -> block.copy(
                label = block.label.trim(),
                text = block.text.trim()
            )
            is NotiBlock.DividerBlock -> block
            is NotiBlock.ProgressBlock -> block.copy(
                progress = block.progress.coerceIn(0, 100),
                durationMinutes = block.durationMinutes.coerceIn(1, 1440),
                finishText = block.finishText.trim().ifBlank { "Completed" }
            )
            is NotiBlock.ActionsBlock -> {
                val cleanActions = block.actions.take(3).mapIndexed { idx, act ->
                    NotiAction(
                        id = act.id.trim().lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifBlank { "btn_${idx + 1}" },
                        label = act.label.trim().ifBlank { "Action ${idx + 1}" }
                    )
                }
                block.copy(actions = cleanActions)
            }
            is NotiBlock.InlineReplyBlock -> block.copy(
                inputHint = block.inputHint.trim().ifBlank { "Type a quick note..." }
            )
        }
    }
    return draft.copy(
        name = cleanName,
        slug = cleanSlug,
        blocks = cleanBlocks
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

private data class AvailableBlockType(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isSingleton: Boolean,
    val createDefault: () -> NotiBlock
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

    var showAddBlockSheet by rememberSaveable { mutableStateOf(false) }

    fun moveBlock(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in draft.blocks.indices || toIndex !in draft.blocks.indices) return
        val updated = draft.blocks.toMutableList()
        val item = updated.removeAt(fromIndex)
        updated.add(toIndex, item)
        draft = draft.copy(blocks = updated)
    }

    fun deleteBlock(index: Int) {
        if (index in draft.blocks.indices) {
            val updated = draft.blocks.toMutableList().apply { removeAt(index) }
            draft = draft.copy(blocks = updated)
        }
    }

    fun updateBlock(index: Int, newBlock: NotiBlock) {
        if (index in draft.blocks.indices) {
            val updated = draft.blocks.toMutableList()
            updated[index] = newBlock
            draft = draft.copy(blocks = updated)
        }
    }

    fun addBlock(newBlock: NotiBlock) {
        draft = draft.copy(blocks = draft.blocks + newBlock)
        showAddBlockSheet = false
    }

    val availableBlockTypes = remember {
        listOf(
            AvailableBlockType(
                title = "Header & Status Pill",
                description = "Category icon, uppercase status badge pill, and accent color tint.",
                icon = Icons.Default.Notifications,
                isSingleton = true,
                createDefault = {
                    NotiBlock.HeaderBlock(
                        statusBadge = "LIVE",
                        iconName = "notifications",
                        accentColorHex = "#4F46E5"
                    )
                }
            ),
            AvailableBlockType(
                title = "Headline & Body",
                description = "Bold headline title and multi-line body with alignment and optional subtext.",
                icon = Icons.Default.TextFields,
                isSingleton = false,
                createDefault = {
                    NotiBlock.TextBlock(
                        title = "Alert Title",
                        body = "Detailed contextual notification message."
                    )
                }
            ),
            AvailableBlockType(
                title = "Metadata Box",
                description = "Context chip container for room, instructor, or telemetry. Can add multiple.",
                icon = Icons.Default.Info,
                isSingleton = false,
                createDefault = {
                    NotiBlock.MetadataBlock(
                        blockId = UUID.randomUUID().toString().take(8),
                        label = "Info",
                        text = "Contextual note or location"
                    )
                }
            ),
            AvailableBlockType(
                title = "Horizontal Divider",
                description = "Subtle separator rule line to create visual breathing room between sections.",
                icon = Icons.Default.HorizontalRule,
                isSingleton = false,
                createDefault = {
                    NotiBlock.DividerBlock(blockId = UUID.randomUUID().toString().take(8))
                }
            ),
            AvailableBlockType(
                title = "Progress & Countdown",
                description = "Live 0–100% progress bar or autonomous countdown timer with completion text.",
                icon = Icons.Default.LinearScale,
                isSingleton = true,
                createDefault = {
                    NotiBlock.ProgressBlock(
                        progressMode = ProgressMode.MANUAL,
                        progress = 65,
                        durationMinutes = 30
                    )
                }
            ),
            AvailableBlockType(
                title = "Action Buttons",
                description = "Up to 3 custom pill buttons firing click callbacks directly back to MacroDroid.",
                icon = Icons.Default.SmartButton,
                isSingleton = true,
                createDefault = {
                    NotiBlock.ActionsBlock(
                        actions = listOf(
                            NotiAction("btn_1", "Action 1"),
                            NotiAction("btn_2", "Action 2")
                        )
                    )
                }
            ),
            AvailableBlockType(
                title = "Inline Quick Reply",
                description = "Native RemoteInput quick reply field capturing text directly back to MacroDroid.",
                icon = Icons.Default.EditNote,
                isSingleton = true,
                createDefault = {
                    NotiBlock.InlineReplyBlock(
                        inputHint = "Type a quick note..."
                    )
                }
            )
        )
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
            // Pinned Real-Time Notification Preview at the Top (Reactively updates with blocks)
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
                            text = "${draft.blocks.size} active block(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LiveNotificationPreviewCard(template = draft)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Scrollable Block Canvas Container
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Card (Core Identity): Minimal card containing only Template Name and MacroDroid Slug
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Template Identity",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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
                            label = { Text("MacroDroid Slug (extra: template)") },
                            singleLine = true,
                            enabled = !draft.isPreset,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Canvas of Active Blocks (Elevated M3 cards with reordering & trash controls)
                draft.blocks.forEachIndexed { index, block ->
                    BlockCardContainer(
                        block = block,
                        index = index,
                        totalBlocks = draft.blocks.size,
                        onMoveUp = { moveBlock(index, index - 1) },
                        onMoveDown = { moveBlock(index, index + 1) },
                        onDelete = { deleteBlock(index) }
                    ) {
                        when (block) {
                            is NotiBlock.HeaderBlock -> {
                                HeaderBlockEditor(
                                    block = block,
                                    onUpdate = { updateBlock(index, it) }
                                )
                            }
                            is NotiBlock.TextBlock -> {
                                TextBlockEditor(
                                    block = block,
                                    onUpdate = { updateBlock(index, it) }
                                )
                            }
                            is NotiBlock.MetadataBlock -> {
                                MetadataBlockEditor(
                                    block = block,
                                    onUpdate = { updateBlock(index, it) }
                                )
                            }
                            is NotiBlock.DividerBlock -> {
                                DividerBlockEditor()
                            }
                            is NotiBlock.ProgressBlock -> {
                                ProgressBlockEditor(
                                    block = block,
                                    onUpdate = { updateBlock(index, it) }
                                )
                            }
                            is NotiBlock.ActionsBlock -> {
                                ActionsBlockEditor(
                                    block = block,
                                    onUpdate = { updateBlock(index, it) }
                                )
                            }
                            is NotiBlock.InlineReplyBlock -> {
                                InlineReplyBlockEditor(
                                    block = block,
                                    onUpdate = { updateBlock(index, it) }
                                )
                            }
                        }
                    }
                }

                // "+ Add Block" Floating/Outlined Button at bottom of the list
                OutlinedButton(
                    onClick = { showAddBlockSheet = true },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Block to Canvas",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    // "+ Add Block" Material 3 ModalBottomSheet
    if (showAddBlockSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddBlockSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Add Block Component",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Choose a modular component to insert into your notification canvas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                availableBlockTypes.forEach { blockType ->
                    val isAlreadyPresent = blockType.isSingleton && draft.blocks.any {
                        when (blockType.title) {
                            "Header & Status Pill" -> it is NotiBlock.HeaderBlock
                            "Progress & Countdown" -> it is NotiBlock.ProgressBlock
                            "Action Buttons" -> it is NotiBlock.ActionsBlock
                            "Inline Quick Reply" -> it is NotiBlock.InlineReplyBlock
                            else -> false
                        }
                    }

                    OutlinedCard(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isAlreadyPresent) {
                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isAlreadyPresent) {
                                addBlock(blockType.createDefault())
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isAlreadyPresent) {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = blockType.icon,
                                    contentDescription = null,
                                    tint = if (isAlreadyPresent) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = blockType.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isAlreadyPresent) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (isAlreadyPresent) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Text(
                                                text = "Already Added",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = blockType.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Elevated Material 3 card container wrapping each active block on the canvas with
 * block title, type icon, sequence shift up/down buttons, and delete button.
 */
@Composable
private fun BlockCardContainer(
    block: NotiBlock,
    index: Int,
    totalBlocks: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val (title, icon) = when (block) {
        is NotiBlock.HeaderBlock -> "Header & Appearance" to Icons.Default.Notifications
        is NotiBlock.TextBlock -> "Headline & Body" to Icons.Default.TextFields
        is NotiBlock.MetadataBlock -> "Metadata Box" to Icons.Default.Info
        is NotiBlock.DividerBlock -> "Horizontal Divider" to Icons.Default.HorizontalRule
        is NotiBlock.ProgressBlock -> "Progress & Countdown" to Icons.Default.LinearScale
        is NotiBlock.ActionsBlock -> "Action Buttons (Pills)" to Icons.Default.SmartButton
        is NotiBlock.InlineReplyBlock -> "Inline Quick Reply" to Icons.Default.EditNote
    }

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Header with Type Icon, Block Title, Sequence Shift Arrows, and Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(6.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Move Up Button
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Move Block Up",
                        tint = if (index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Move Down Button
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalBlocks - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Move Block Down",
                        tint = if (index < totalBlocks - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete (Trash) Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Block",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderBlockEditor(
    block: NotiBlock.HeaderBlock,
    onUpdate: (NotiBlock.HeaderBlock) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = block.statusBadge,
            onValueChange = { onUpdate(block.copy(statusBadge = it)) },
            label = { Text("Status Badge Text (e.g. IN SESSION, LIVE)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

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
                val selected = block.iconName.equals(iconKey, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = { onUpdate(block.copy(iconName = iconKey)) },
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
            checked = block.useDynamicColor,
            onCheckedChange = { onUpdate(block.copy(useDynamicColor = it)) }
        )

        if (!block.useDynamicColor) {
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
                    val isSelected = block.accentColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onUpdate(block.copy(accentColorHex = hex)) },
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
                value = block.accentColorHex,
                onValueChange = { onUpdate(block.copy(accentColorHex = it)) },
                label = { Text("Custom Hex Color (#RRGGBB)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TextBlockEditor(
    block: NotiBlock.TextBlock,
    onUpdate: (NotiBlock.TextBlock) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = block.title,
            onValueChange = { onUpdate(block.copy(title = it)) },
            label = { Text("Headline / Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = block.body,
            onValueChange = { onUpdate(block.copy(body = it)) },
            label = { Text("Multi-line Body Text") },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = block.subtext,
            onValueChange = { onUpdate(block.copy(subtext = it)) },
            label = { Text("Optional Subtext / Note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Text Alignment",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextAlignment.entries.forEach { align ->
                FilterChip(
                    selected = block.alignment == align,
                    onClick = { onUpdate(block.copy(alignment = align)) },
                    label = { Text(align.displayName) }
                )
            }
        }
    }
}

@Composable
private fun MetadataBlockEditor(
    block: NotiBlock.MetadataBlock,
    onUpdate: (NotiBlock.MetadataBlock) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = block.label,
            onValueChange = { onUpdate(block.copy(label = it)) },
            label = { Text("Label / Tag (e.g. Location, Instructor, Target)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = block.text,
            onValueChange = { onUpdate(block.copy(text = it)) },
            label = { Text("Metadata Content (e.g. Hall B-204 • Prof. Turing)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DividerBlockEditor() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = "Horizontal divider line creating visual separation between neighboring blocks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressBlockEditor(
    block: NotiBlock.ProgressBlock,
    onUpdate: (NotiBlock.ProgressBlock) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Progress Mode",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProgressMode.entries.forEach { mode ->
                FilterChip(
                    selected = block.progressMode == mode,
                    onClick = { onUpdate(block.copy(progressMode = mode)) },
                    label = { Text(mode.displayName) }
                )
            }
        }

        if (block.progressMode == ProgressMode.MANUAL) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Default Progress: ${block.progress}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = block.progress.toFloat(),
                        onValueChange = {
                            onUpdate(block.copy(progress = it.roundToInt().coerceIn(0, 100)))
                        },
                        valueRange = 0f..100f
                    )
                }
            }
        }

        if (block.progressMode == ProgressMode.AUTO_TIMER) {
            var durationText by rememberSaveable(block.durationMinutes) {
                mutableStateOf(block.durationMinutes.toString())
            }

            OutlinedTextField(
                value = durationText,
                onValueChange = { text ->
                    durationText = text
                    val parsed = text.toIntOrNull()?.coerceIn(1, 1440)
                    if (parsed != null) {
                        onUpdate(block.copy(durationMinutes = parsed))
                    }
                },
                label = { Text("Default Countdown Duration (Minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = block.finishText,
                onValueChange = { onUpdate(block.copy(finishText = it)) },
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
            subtitle = "Automatically closes notification when countdown finishes",
            checked = block.autoDismiss,
            onCheckedChange = { onUpdate(block.copy(autoDismiss = it)) }
        )
    }
}

@Composable
private fun ActionsBlockEditor(
    block: NotiBlock.ActionsBlock,
    onUpdate: (NotiBlock.ActionsBlock) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Callback Pill Buttons (${block.actions.size}/3)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Cleanly stacked layout that never clips on narrow screens
        block.actions.forEachIndexed { index, actionItem ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Button #${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val updated = block.actions.toMutableList().apply { removeAt(index) }
                                onUpdate(block.copy(actions = updated))
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Remove button",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = actionItem.label,
                        onValueChange = { newLabel ->
                            val updated = block.actions.toMutableList()
                            updated[index] = actionItem.copy(label = newLabel)
                            onUpdate(block.copy(actions = updated))
                        },
                        label = { Text("Button Label (e.g. Pause, Mute)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = actionItem.id,
                        onValueChange = { newId ->
                            val updated = block.actions.toMutableList()
                            updated[index] = actionItem.copy(
                                id = newId.lowercase().replace(Regex("[^a-z0-9_]+"), "_")
                            )
                            onUpdate(block.copy(actions = updated))
                        },
                        label = { Text("Callback Action ID (e.g. btn_pause)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (block.actions.size < 3) {
            OutlinedButton(
                onClick = {
                    val nextNum = block.actions.size + 1
                    val updated = block.actions + NotiAction(
                        id = "btn_$nextNum",
                        label = "Action $nextNum"
                    )
                    onUpdate(block.copy(actions = updated))
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Button (${block.actions.size}/3)")
            }
        }
    }
}

@Composable
private fun InlineReplyBlockEditor(
    block: NotiBlock.InlineReplyBlock,
    onUpdate: (NotiBlock.InlineReplyBlock) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = block.inputHint,
            onValueChange = { onUpdate(block.copy(inputHint = it)) },
            label = { Text("Reply Placeholder Hint") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Adds an inline RemoteInput text field to the notification and sends typed responses to MacroDroid.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
