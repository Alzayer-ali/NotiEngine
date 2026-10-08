package com.notiforge.app.domain.model

/**
 * Curated library of 6 pre-built notification presets designed for MacroDroid automation workflows.
 */
object PresetCatalog {

    val defaultPresets: List<NotiTemplate> = listOf(
        // 1. Lecture / Meeting Tracker
        NotiTemplate(
            slug = "lecture_tracker",
            name = "Lecture / Meeting Tracker",
            description = "Autonomous countdown timer with live progress bar, room metadata, and status badge.",
            isPreset = true,
            showHeader = true,
            statusBadge = "IN SESSION",
            iconName = "school",
            accentColorHex = "#4F46E5",
            useDynamicColor = false,
            progressMode = ProgressMode.AUTO_TIMER,
            defaultProgress = 0,
            defaultDurationMinutes = 50,
            autoDismiss = false,
            finishText = "Session Completed • Dismiss or move to next room",
            showDetailBlock = true,
            defaultTitle = "CS-301: Operating Systems",
            defaultBody = "Kernel scheduling, IPC mechanisms, and virtual memory management.",
            defaultMetadata = "Hall B-204 • Prof. A. Turing",
            showActionButtons = true,
            actions = listOf(
                NotiAction(id = "btn_mute_mic", label = "Mute Device"),
                NotiAction(id = "btn_open_notes", label = "Open Notes")
            ),
            showRemoteInput = false,
            inputHint = ""
        ),

        // 2. Quick Note
        NotiTemplate(
            slug = "quick_note",
            name = "Quick Note",
            description = "Inline RemoteInput notification that forwards captured text directly back to MacroDroid.",
            isPreset = true,
            showHeader = true,
            statusBadge = "QUICK CAPTURE",
            iconName = "edit_note",
            accentColorHex = "#0D9488",
            useDynamicColor = false,
            progressMode = ProgressMode.NONE,
            defaultProgress = 0,
            defaultDurationMinutes = 15,
            autoDismiss = true,
            finishText = "Note Saved",
            showDetailBlock = true,
            defaultTitle = "Inbox Quick Capture",
            defaultBody = "Tap Reply below to append a thought, task, or timestamped log to your vault.",
            defaultMetadata = "Target: Daily Log • Instant Sync",
            showActionButtons = false,
            actions = emptyList(),
            showRemoteInput = true,
            inputHint = "Write a quick note or task..."
        ),

        // 3. Task / Percentage Tracker
        NotiTemplate(
            slug = "task_tracker",
            name = "Task / Percentage Tracker",
            description = "Manually controlled 0–100% progress bar with live percentage readout for batch jobs or uploads.",
            isPreset = true,
            showHeader = true,
            statusBadge = "SYNCING",
            iconName = "sync",
            accentColorHex = "#2563EB",
            useDynamicColor = false,
            progressMode = ProgressMode.MANUAL,
            defaultProgress = 68,
            defaultDurationMinutes = 30,
            autoDismiss = false,
            finishText = "100% Complete",
            showDetailBlock = true,
            defaultTitle = "Cloud Backup & Media Sync",
            defaultBody = "Uploading encrypted archive chunks to local NAS storage.",
            defaultMetadata = "Batch #42 • 34 of 50 files processed",
            showActionButtons = true,
            actions = listOf(
                NotiAction(id = "btn_pause_job", label = "Pause"),
                NotiAction(id = "btn_cancel_job", label = "Abort")
            ),
            showRemoteInput = false,
            inputHint = ""
        ),

        // 4. Action Deck
        NotiTemplate(
            slug = "action_deck",
            name = "Action Deck",
            description = "Interactive control card with 3 customizable quick action buttons firing callbacks to MacroDroid.",
            isPreset = true,
            showHeader = true,
            statusBadge = "CONTROL DECK",
            iconName = "tune",
            accentColorHex = "#7C3AED",
            useDynamicColor = false,
            progressMode = ProgressMode.NONE,
            defaultProgress = 0,
            defaultDurationMinutes = 15,
            autoDismiss = false,
            finishText = "",
            showDetailBlock = true,
            defaultTitle = "Desk & Focus Environment",
            defaultBody = "Trigger smart home scenes or toggle study automation profiles with one tap.",
            defaultMetadata = "Profile: Deep Work • Desk Hub Connected",
            showActionButtons = true,
            actions = listOf(
                NotiAction(id = "btn_focus_mode", label = "Focus Mode"),
                NotiAction(id = "btn_lights_warm", label = "Warm Lights"),
                NotiAction(id = "btn_pomodoro", label = "Start 25m")
            ),
            showRemoteInput = false,
            inputHint = ""
        ),

        // 5. Status Card
        NotiTemplate(
            slug = "status_card",
            name = "Status Card",
            description = "Clean, expressive card with bold status chips and rich contextual metadata.",
            isPreset = true,
            showHeader = true,
            statusBadge = "ARMED • HOME",
            iconName = "verified",
            accentColorHex = "#16A34A",
            useDynamicColor = false,
            progressMode = ProgressMode.NONE,
            defaultProgress = 0,
            defaultDurationMinutes = 30,
            autoDismiss = false,
            finishText = "",
            showDetailBlock = true,
            defaultTitle = "Smart Security & Sensors",
            defaultBody = "All entry points secured. Garage door closed and EV charging scheduled for 01:00.",
            defaultMetadata = "Battery: 82% • Wi-Fi: Home_5G",
            showActionButtons = true,
            actions = listOf(
                NotiAction(id = "btn_disarm", label = "Disarm"),
                NotiAction(id = "btn_refresh_status", label = "Refresh")
            ),
            showRemoteInput = false,
            inputHint = ""
        ),

        // 6. Minimal Alert
        NotiTemplate(
            slug = "minimal_alert",
            name = "Minimal Alert",
            description = "Compact, high-contrast styled alert notification without extra visual clutter.",
            isPreset = true,
            showHeader = true,
            statusBadge = "ALERT",
            iconName = "bolt",
            accentColorHex = "#EA580C",
            useDynamicColor = true,
            progressMode = ProgressMode.NONE,
            defaultProgress = 0,
            defaultDurationMinutes = 10,
            autoDismiss = true,
            finishText = "",
            showDetailBlock = false,
            defaultTitle = "Automation Trigger Executed",
            defaultBody = "MacroDroid completed your scheduled workflow without errors.",
            defaultMetadata = "",
            showActionButtons = false,
            actions = emptyList(),
            showRemoteInput = false,
            inputHint = ""
        )
    )
}
