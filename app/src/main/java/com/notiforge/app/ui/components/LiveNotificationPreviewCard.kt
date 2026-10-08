package com.notiforge.app.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notiforge.app.domain.model.NotiBlock
import com.notiforge.app.domain.model.NotiTemplate
import com.notiforge.app.domain.model.ProgressMode
import com.notiforge.app.domain.model.TextAlignment

/**
 * Real-time WYSIWYG Compose preview card that visually mirrors the custom `RemoteViews`
 * (`noti_expanded.xml` and `noti_collapsed.xml`) rendered dynamically by NotiEngine.
 */
@Composable
fun LiveNotificationPreviewCard(
    template: NotiTemplate,
    modifier: Modifier = Modifier,
    allowCollapseToggle: Boolean = true,
    onActionClickPreview: ((String) -> Unit)? = null
) {
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    val accentColor = parseComposeColor(
        hex = template.accentColorHex,
        useDynamic = template.useDynamicColor,
        fallback = MaterialTheme.colorScheme.primary
    )
    val iconVector = resolvePreviewIcon(template.iconName)

    val progressFraction = when (template.progressMode) {
        ProgressMode.NONE -> 0f
        ProgressMode.MANUAL -> (template.defaultProgress.coerceIn(0, 100)) / 100f
        ProgressMode.AUTO_TIMER -> 0.38f // Illustrative live timer progress in preview
    }

    val progressReadout = when (template.progressMode) {
        ProgressMode.NONE -> ""
        ProgressMode.MANUAL -> "${template.defaultProgress.coerceIn(0, 100)}%"
        ProgressMode.AUTO_TIMER -> "${template.defaultDurationMinutes}m left"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(14.dp)
        ) {
            // Android System Notification Chrome Header (Small Icon + App Name + Expand Chevron)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "NotiEngine • now",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (allowCollapseToggle) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { isExpanded = !isExpanded }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isExpanded) "Expanded" else "Collapsed",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle preview state",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedContent(
                targetState = isExpanded,
                label = "RemoteViewsPreviewState"
            ) { expanded ->
                if (!expanded) {
                    // Mirrors res/layout/noti_collapsed.xml
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = template.defaultTitle.ifBlank { "Notification Title" },
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (template.progressMode != ProgressMode.NONE && progressReadout.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = progressReadout,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (template.showHeader && template.statusBadge.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                StatusBadgePill(text = template.statusBadge)
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = template.defaultBody.ifBlank { "Notification body text..." },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (template.progressMode != ProgressMode.NONE) {
                            Spacer(modifier = Modifier.height(6.dp))
                            RemoteViewsProgressBar(
                                fraction = progressFraction,
                                accentColor = accentColor,
                                heightDp = 5
                            )
                        }
                    }
                } else {
                    // Mirrors res/layout/noti_expanded.xml with dynamically ordered blocks
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        template.blocks.forEach { block ->
                            when (block) {
                                is NotiBlock.HeaderBlock -> {
                                    val blockAccent = parseComposeColor(
                                        hex = block.accentColorHex,
                                        useDynamic = block.useDynamicColor,
                                        fallback = accentColor
                                    )
                                    val blockIcon = resolvePreviewIcon(block.iconName)
                                    val hasProgress = template.progressMode != ProgressMode.NONE && progressReadout.isNotBlank()

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = blockIcon,
                                            contentDescription = null,
                                            tint = blockAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))

                                        if (block.statusBadge.isNotBlank()) {
                                            StatusBadgePill(text = block.statusBadge)
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        if (hasProgress) {
                                            Text(
                                                text = progressReadout,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                is NotiBlock.TextBlock -> {
                                    val textAlign = when (block.alignment) {
                                        TextAlignment.CENTER -> TextAlign.Center
                                        TextAlignment.END -> TextAlign.End
                                        TextAlignment.START -> TextAlign.Start
                                    }
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = when (block.alignment) {
                                            TextAlignment.CENTER -> Alignment.CenterHorizontally
                                            TextAlignment.END -> Alignment.End
                                            TextAlignment.START -> Alignment.Start
                                        }
                                    ) {
                                        if (block.title.isNotBlank()) {
                                            Text(
                                                text = block.title,
                                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = textAlign,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        if (block.body.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = block.body,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = textAlign,
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        if (block.subtext.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = block.subtext,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                textAlign = textAlign,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                is NotiBlock.DividerBlock -> {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    )
                                }

                                is NotiBlock.MetadataBlock -> {
                                    val fullText = if (block.label.isNotBlank() && !block.text.startsWith(block.label)) {
                                        "${block.label}: ${block.text}"
                                    } else {
                                        block.text
                                    }
                                    if (fullText.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = fullText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                is NotiBlock.ProgressBlock -> {
                                    if (block.progressMode != ProgressMode.NONE) {
                                        val blockFraction = when (block.progressMode) {
                                            ProgressMode.NONE -> 0f
                                            ProgressMode.MANUAL -> block.progress.coerceIn(0, 100) / 100f
                                            ProgressMode.AUTO_TIMER -> 0.38f
                                        }
                                        RemoteViewsProgressBar(
                                            fraction = blockFraction,
                                            accentColor = accentColor,
                                            heightDp = 8
                                        )
                                    }
                                }

                                is NotiBlock.ActionsBlock -> {
                                    val visibleActions = block.actions.take(3)
                                    if (visibleActions.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            visibleActions.forEach { action ->
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(34.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f))
                                                        .clickable(enabled = onActionClickPreview != null) {
                                                            onActionClickPreview?.invoke(action.id)
                                                        }
                                                        .padding(horizontal = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = action.label,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                is NotiBlock.InlineReplyBlock -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(50)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.EditNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = block.inputHint.ifBlank { "Reply / Capture..." },
                                            fontSize = 12.sp,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send RemoteInput",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadgePill(
    text: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RemoteViewsProgressBar(
    fraction: Float,
    accentColor: Color,
    heightDp: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(accentColor)
        )
    }
}

@Composable
fun parseComposeColor(
    hex: String,
    useDynamic: Boolean,
    fallback: Color
): Color {
    if (useDynamic) {
        return MaterialTheme.colorScheme.primary
    }
    return try {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        Color(AndroidColor.parseColor(clean))
    } catch (_: Exception) {
        fallback
    }
}

fun resolvePreviewIcon(iconName: String): ImageVector {
    return when (iconName.trim().lowercase()) {
        "school", "lecture" -> Icons.Default.School
        "edit_note", "note", "edit" -> Icons.Default.EditNote
        "sync", "progress", "task" -> Icons.Default.Sync
        "tune", "deck", "controls" -> Icons.Default.Tune
        "verified", "status", "shield" -> Icons.Default.Verified
        "bolt", "alert", "minimal" -> Icons.Default.Bolt
        else -> Icons.Default.Notifications
    }
}
