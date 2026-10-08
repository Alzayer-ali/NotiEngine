package com.notiforge.app.domain.model

/**
 * Curated library of 6 pre-built notification presets designed for MacroDroid automation workflows.
 * Structured as modular, reorderable NotiBlock canvases.
 */
object PresetCatalog {

    val defaultPresets: List<NotiTemplate> = listOf(
        // 1. Lecture / Meeting Tracker
        NotiTemplate(
            slug = "lecture_tracker",
            name = "Lecture / Meeting Tracker",
            description = "Autonomous countdown timer with live progress bar, room metadata, and status badge.",
            isPreset = true,
            blocks = listOf(
                NotiBlock.HeaderBlock(
                    statusBadge = "IN SESSION",
                    iconName = "school",
                    accentColorHex = "#4F46E5",
                    useDynamicColor = false
                ),
                NotiBlock.TextBlock(
                    title = "CS-301: Operating Systems",
                    body = "Kernel scheduling, IPC mechanisms, and virtual memory management."
                ),
                NotiBlock.DividerBlock(),
                NotiBlock.MetadataBlock(
                    label = "Location",
                    text = "Hall B-204 • Prof. A. Turing"
                ),
                NotiBlock.ProgressBlock(
                    progressMode = ProgressMode.AUTO_TIMER,
                    progress = 0,
                    durationMinutes = 50,
                    autoDismiss = false,
                    finishText = "Session Completed • Dismiss or move to next room"
                ),
                NotiBlock.ActionsBlock(
                    actions = listOf(
                        NotiAction(id = "btn_mute_mic", label = "Mute Device"),
                        NotiAction(id = "btn_open_notes", label = "Open Notes")
                    )
                )
            )
        ),

        // 2. Quick Note
        NotiTemplate(
            slug = "quick_note",
            name = "Quick Note",
            description = "Inline RemoteInput notification that forwards captured text directly back to MacroDroid.",
            isPreset = true,
            blocks = listOf(
                NotiBlock.HeaderBlock(
                    statusBadge = "QUICK CAPTURE",
                    iconName = "edit_note",
                    accentColorHex = "#0D9488",
                    useDynamicColor = false
                ),
                NotiBlock.TextBlock(
                    title = "Inbox Quick Capture",
                    body = "Tap Reply below to append a thought, task, or timestamped log to your vault."
                ),
                NotiBlock.MetadataBlock(
                    label = "Target",
                    text = "Target: Daily Log • Instant Sync"
                ),
                NotiBlock.DividerBlock(),
                NotiBlock.InlineReplyBlock(
                    inputHint = "Write a quick note or task..."
                )
            )
        ),

        // 3. Task / Percentage Tracker
        NotiTemplate(
            slug = "task_tracker",
            name = "Task / Percentage Tracker",
            description = "Manually controlled 0–100% progress bar with live percentage readout for batch jobs or uploads.",
            isPreset = true,
            blocks = listOf(
                NotiBlock.HeaderBlock(
                    statusBadge = "SYNCING",
                    iconName = "sync",
                    accentColorHex = "#2563EB",
                    useDynamicColor = false
                ),
                NotiBlock.TextBlock(
                    title = "Cloud Backup & Media Sync",
                    body = "Uploading encrypted archive chunks to local NAS storage."
                ),
                NotiBlock.ProgressBlock(
                    progressMode = ProgressMode.MANUAL,
                    progress = 68,
                    durationMinutes = 30,
                    autoDismiss = false,
                    finishText = "100% Complete"
                ),
                NotiBlock.DividerBlock(),
                NotiBlock.MetadataBlock(
                    label = "Batch",
                    text = "Batch #42 • 34 of 50 files processed"
                ),
                NotiBlock.ActionsBlock(
                    actions = listOf(
                        NotiAction(id = "btn_pause_job", label = "Pause"),
                        NotiAction(id = "btn_cancel_job", label = "Abort")
                    )
                )
            )
        ),

        // 4. Action Deck
        NotiTemplate(
            slug = "action_deck",
            name = "Action Deck",
            description = "Interactive control card with 3 customizable quick action buttons firing callbacks to MacroDroid.",
            isPreset = true,
            blocks = listOf(
                NotiBlock.HeaderBlock(
                    statusBadge = "CONTROL DECK",
                    iconName = "tune",
                    accentColorHex = "#7C3AED",
                    useDynamicColor = false
                ),
                NotiBlock.TextBlock(
                    title = "Desk & Focus Environment",
                    body = "Trigger smart home scenes or toggle study automation profiles with one tap."
                ),
                NotiBlock.MetadataBlock(
                    label = "Profile",
                    text = "Profile: Deep Work • Desk Hub Connected"
                ),
                NotiBlock.DividerBlock(),
                NotiBlock.ActionsBlock(
                    actions = listOf(
                        NotiAction(id = "btn_focus_mode", label = "Focus Mode"),
                        NotiAction(id = "btn_lights_warm", label = "Warm Lights"),
                        NotiAction(id = "btn_pomodoro", label = "Start 25m")
                    )
                )
            )
        ),

        // 5. Status Card
        NotiTemplate(
            slug = "status_card",
            name = "Status Card",
            description = "Clean, expressive card with bold status chips and rich contextual metadata.",
            isPreset = true,
            blocks = listOf(
                NotiBlock.HeaderBlock(
                    statusBadge = "ARMED • HOME",
                    iconName = "verified",
                    accentColorHex = "#16A34A",
                    useDynamicColor = false
                ),
                NotiBlock.TextBlock(
                    title = "Smart Security & Sensors",
                    body = "All entry points secured. Garage door closed and EV charging scheduled for 01:00."
                ),
                NotiBlock.MetadataBlock(
                    label = "Status",
                    text = "Battery: 82% • Wi-Fi: Home_5G"
                ),
                NotiBlock.DividerBlock(),
                NotiBlock.ActionsBlock(
                    actions = listOf(
                        NotiAction(id = "btn_disarm", label = "Disarm"),
                        NotiAction(id = "btn_refresh_status", label = "Refresh")
                    )
                )
            )
        ),

        // 6. Minimal Alert
        NotiTemplate(
            slug = "minimal_alert",
            name = "Minimal Alert",
            description = "Compact, high-contrast styled alert notification without extra visual clutter.",
            isPreset = true,
            blocks = listOf(
                NotiBlock.HeaderBlock(
                    statusBadge = "ALERT",
                    iconName = "bolt",
                    accentColorHex = "#EA580C",
                    useDynamicColor = true
                ),
                NotiBlock.TextBlock(
                    title = "Automation Trigger Executed",
                    body = "MacroDroid completed your scheduled workflow without errors."
                )
            )
        )
    )
}
