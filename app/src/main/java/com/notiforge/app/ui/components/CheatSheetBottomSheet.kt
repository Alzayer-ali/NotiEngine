package com.notiforge.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notiforge.app.domain.model.NotiTemplate
import com.notiforge.app.domain.model.ProgressMode
import com.notiforge.app.ipc.NotiContract

data class ExtraSpecItem(
    val key: String,
    val value: String,
    val typeLabel: String,
    val description: String,
    val isRequired: Boolean = false
)

/**
 * Clean, compact MacroDroid Integration Reference Sheet with:
 * - Prominent "Copy MacroDroid Quick Setup" button
 * - Compact Intent Routing Card
 * - Grouped "Required" (id, template) and "Optional Overrides" (title, duration, etc.) tables
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheatSheetBottomSheet(
    template: NotiTemplate,
    onDismiss: () -> Unit,
    onFireBroadcastTest: (NotiTemplate) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    val jsonExtraPayload = remember(template) { NotiContract.buildTemplateJsonExtra(template) }
    val requiredExtras = remember(template) { buildRequiredExtras(template) }
    val optionalExtras = remember(template) { buildOptionalExtras(template) }

    fun copyText(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        copiedLabel = label
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MacroDroid Quick Reference",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${template.name} • ${template.slug}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = { onFireBroadcastTest(template) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test IPC", maxLines = 1)
                }
            }

            // Prominent "Copy MacroDroid JSON Extra" Button
            Button(
                onClick = {
                    copyText("MacroDroid JSON Extra", jsonExtraPayload)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = if (copiedLabel == "MacroDroid JSON Extra") {
                        Icons.Default.Check
                    } else {
                        Icons.Default.ContentCopy
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (copiedLabel == "MacroDroid JSON Extra") {
                        "Copied MacroDroid JSON Extra!"
                    } else {
                        "Copy MacroDroid JSON Extra"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (copiedLabel != null && copiedLabel != "MacroDroid JSON Extra") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copied $copiedLabel to clipboard",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 1. Single-Payload JSON Extra Card (Recommended)
            ReferenceSectionCard(
                title = "Single-Payload JSON Extra (Recommended)",
                subtitle = "In MacroDroid, just add one Extra: key = 'json', value = [paste copied JSON]"
            ) {
                CompactCopyRow(
                    label = "Key",
                    value = NotiContract.Extras.JSON,
                    onCopy = { copyText("extra key 'json'", NotiContract.Extras.JSON) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                CompactCopyRow(
                    label = "Value",
                    value = jsonExtraPayload,
                    onCopy = { copyText("MacroDroid JSON Extra", jsonExtraPayload) }
                )
            }

            // 2. Compact Broadcast Routing Reference Card
            ReferenceSectionCard(
                title = "Send Intent Routing",
                subtitle = "MacroDroid → Connectivity → Send Intent (Broadcast)"
            ) {
                CompactCopyRow(
                    label = "Action",
                    value = NotiContract.ACTION_SHOW_NOTIFICATION,
                    onCopy = { copyText("Action", NotiContract.ACTION_SHOW_NOTIFICATION) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                CompactCopyRow(
                    label = "Package",
                    value = NotiContract.PACKAGE_NAME,
                    onCopy = { copyText("Package", NotiContract.PACKAGE_NAME) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                CompactCopyRow(
                    label = "Class",
                    value = NotiContract.RECEIVER_CLASS,
                    onCopy = { copyText("Class", NotiContract.RECEIVER_CLASS) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                CompactCopyRow(
                    label = "Cancel",
                    value = NotiContract.ACTION_CANCEL,
                    onCopy = { copyText("Cancel Action", NotiContract.ACTION_CANCEL) }
                )
            }

            // 3. Individual Extras Fallback (id, template)
            ReferenceSectionCard(
                title = "Individual Extras (Fallback if 'json' is omitted)",
                subtitle = "Only needed if you prefer separate key-value Intent Extras instead of 'json'"
            ) {
                requiredExtras.forEachIndexed { index, extra ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                    CompactExtraTableRow(
                        extra = extra,
                        onCopyKey = { copyText("extra key '${extra.key}'", extra.key) },
                        onCopyValue = { copyText("value '${extra.value}'", extra.value) }
                    )
                }
            }

            // 4. Optional Parameter Reference (works inside JSON or as individual extras)
            ReferenceSectionCard(
                title = "Supported Parameters Reference",
                subtitle = "All keys below can be customized inside the 'json' payload or passed as extras"
            ) {
                optionalExtras.forEachIndexed { index, extra ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                    CompactExtraTableRow(
                        extra = extra,
                        onCopyKey = { copyText("extra key '${extra.key}'", extra.key) },
                        onCopyValue = { copyText("value for '${extra.key}'", extra.value) }
                    )
                }
            }

            // 5. Compact Outgoing Callback Reference
            ReferenceSectionCard(
                title = "Receiving Callbacks (Optional)",
                subtitle = "Use MacroDroid 'Intent Received' trigger to handle button clicks, replies, or timer end"
            ) {
                CompactCopyRow(
                    label = "Action",
                    value = NotiContract.ACTION_EVENT,
                    onCopy = { copyText("Event Action", NotiContract.ACTION_EVENT) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(
                    text = "Extras returned: id, event_type (ACTION_CLICKED | INPUT_SUBMITTED | TIMER_FINISHED), action_id, user_input",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ReferenceSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun CompactCopyRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onCopy)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun CompactExtraTableRow(
    extra: ExtraSpecItem,
    onCopyKey: () -> Unit,
    onCopyValue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (extra.isRequired) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onCopyKey)
                ) {
                    Text(
                        text = extra.key,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (extra.isRequired) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = extra.typeLabel,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "${extra.value}  •  ${extra.description}",
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onCopyValue,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy ${extra.key} value",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

private fun buildRequiredExtras(template: NotiTemplate): List<ExtraSpecItem> {
    return listOf(
        ExtraSpecItem(
            key = NotiContract.Extras.ID,
            value = "noti_${template.slug}",
            typeLabel = "String",
            description = "Unique notification ID for updates or cancelling",
            isRequired = true
        ),
        ExtraSpecItem(
            key = NotiContract.Extras.TEMPLATE,
            value = template.slug,
            typeLabel = "String",
            description = "Template key to render",
            isRequired = true
        )
    )
}

private fun buildOptionalExtras(template: NotiTemplate): List<ExtraSpecItem> {
    return buildList {
        add(
            ExtraSpecItem(
                key = NotiContract.Extras.TITLE,
                value = template.defaultTitle,
                typeLabel = "String",
                description = "Headline override"
            )
        )
        add(
            ExtraSpecItem(
                key = NotiContract.Extras.BODY,
                value = template.defaultBody,
                typeLabel = "String",
                description = "Body text override"
            )
        )
        if (template.progressMode == ProgressMode.AUTO_TIMER) {
            add(
                ExtraSpecItem(
                    key = NotiContract.Extras.DURATION,
                    value = template.defaultDurationMinutes.toString(),
                    typeLabel = "Int / HH:mm",
                    description = "Countdown duration in minutes"
                )
            )
        } else if (template.progressMode == ProgressMode.MANUAL) {
            add(
                ExtraSpecItem(
                    key = NotiContract.Extras.PROGRESS,
                    value = template.defaultProgress.toString(),
                    typeLabel = "Int (0..100)",
                    description = "Progress bar percentage"
                )
            )
        } else {
            add(
                ExtraSpecItem(
                    key = NotiContract.Extras.DURATION,
                    value = template.defaultDurationMinutes.toString(),
                    typeLabel = "Int",
                    description = "Optional timer duration (if progress_mode=auto_timer)"
                )
            )
        }
        if (template.showDetailBlock || template.defaultMetadata.isNotBlank()) {
            add(
                ExtraSpecItem(
                    key = NotiContract.Extras.METADATA,
                    value = template.defaultMetadata.ifBlank { "Room B-204 • Live" },
                    typeLabel = "String",
                    description = "Detail box subtext"
                )
            )
        }
        if (template.showHeader) {
            add(
                ExtraSpecItem(
                    key = NotiContract.Extras.STATUS_BADGE,
                    value = template.statusBadge.ifBlank { "LIVE" },
                    typeLabel = "String",
                    description = "Status badge text"
                )
            )
        }
        if (template.showRemoteInput) {
            add(
                ExtraSpecItem(
                    key = NotiContract.Extras.INPUT_HINT,
                    value = template.inputHint.ifBlank { "Write a quick note..." },
                    typeLabel = "String",
                    description = "Inline reply hint"
                )
            )
        }
        add(
            ExtraSpecItem(
                key = NotiContract.Extras.AUTO_DISMISS,
                value = template.autoDismiss.toString(),
                typeLabel = "Boolean",
                description = "Auto-dismiss when done"
            )
        )
    }
}
