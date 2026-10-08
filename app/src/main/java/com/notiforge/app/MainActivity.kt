package com.notiforge.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notiforge.app.domain.engine.NotificationRenderEngine
import com.notiforge.app.ui.NotiViewModel
import com.notiforge.app.ui.components.CheatSheetBottomSheet
import com.notiforge.app.ui.screens.TemplateEditorScreen
import com.notiforge.app.ui.screens.TemplatesScreen
import com.notiforge.app.ui.theme.NotiEngineTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NotiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appCtx = applicationContext
        NotificationRenderEngine.ensureChannelsCreated(appCtx)

        setContent {
            NotiEngineTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current

                var hasNotiPermission by remember {
                    mutableStateOf(NotificationRenderEngine.canPostNotifications(appCtx))
                }
                var pendingPermissionAction by remember {
                    mutableStateOf<(() -> Unit)?>(null)
                }

                // Refresh notification permission whenever user returns from system settings
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            NotificationRenderEngine.ensureChannelsCreated(appCtx)
                            hasNotiPermission = NotificationRenderEngine.canPostNotifications(appCtx)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    NotificationRenderEngine.ensureChannelsCreated(appCtx)
                    hasNotiPermission = isGranted && NotificationRenderEngine.canPostNotifications(appCtx)
                    val actionToRun = pendingPermissionAction
                    pendingPermissionAction = null

                    if (hasNotiPermission) {
                        if (actionToRun != null) {
                            actionToRun.invoke()
                        } else {
                            viewModel.postSnackbar("Notification permission granted")
                        }
                    } else {
                        viewModel.postSnackbar("Notification permission denied. Enable notifications in System Settings.")
                    }
                }

                fun executeWithNotificationPermission(action: () -> Unit) {
                    NotificationRenderEngine.ensureChannelsCreated(appCtx)
                    val currentlyAllowed = NotificationRenderEngine.canPostNotifications(appCtx)
                    hasNotiPermission = currentlyAllowed
                    if (currentlyAllowed) {
                        action()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pendingPermissionAction = action
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        try {
                            val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            }
                            startActivity(settingsIntent)
                        } catch (_: Exception) {
                            viewModel.postSnackbar("Please enable notifications for NotiEngine in System Settings.")
                        }
                    }
                }

                // Prompt for POST_NOTIFICATIONS on first launch on Android 13+ (API 33+)
                LaunchedEffect(Unit) {
                    NotificationRenderEngine.ensureChannelsCreated(appCtx)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotiPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val editingTemplate = uiState.editingTemplate
                BackHandler(enabled = editingTemplate != null) {
                    viewModel.closeTemplateEditor()
                }

                Crossfade(
                    targetState = editingTemplate,
                    label = "MainScreenTransition"
                ) { activeEditorTemplate ->
                    if (activeEditorTemplate != null) {
                        TemplateEditorScreen(
                            initialTemplate = activeEditorTemplate,
                            onBack = { viewModel.closeTemplateEditor() },
                            onSave = { updated -> viewModel.saveTemplate(updated) },
                            onTestLive = { draft ->
                                executeWithNotificationPermission {
                                    viewModel.triggerTestNotification(draft)
                                }
                            }
                        )
                    } else {
                        TemplatesScreen(
                            uiState = uiState,
                            hasNotificationPermission = hasNotiPermission,
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    try {
                                        val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                        }
                                        startActivity(settingsIntent)
                                    } catch (_: Exception) {
                                        // Ignored
                                    }
                                }
                            },
                            onCreateNewTemplate = { viewModel.openNewTemplateEditor() },
                            onEditTemplate = { template -> viewModel.openTemplateEditor(template) },
                            onTestNotification = { template ->
                                executeWithNotificationPermission {
                                    viewModel.triggerTestNotification(template)
                                }
                            },
                            onOpenCheatSheet = { template -> viewModel.openCheatSheet(template) },
                            onDuplicateTemplate = { template -> viewModel.duplicateTemplate(template) },
                            onCancelNotification = { template -> viewModel.cancelNotificationForTemplate(template) },
                            onDeleteTemplate = { template -> viewModel.deleteTemplate(template) },
                            onExportJson = { callback -> viewModel.exportAllTemplatesJson(callback) },
                            onImportJson = { rawJson -> viewModel.importTemplatesFromJson(rawJson) },
                            onClearLogs = { viewModel.clearLogs() },
                            onSnackbarConsumed = { viewModel.clearSnackbar() }
                        )
                    }
                }

                val activeCheatSheet = uiState.cheatSheetTemplate
                if (activeCheatSheet != null) {
                    CheatSheetBottomSheet(
                        template = activeCheatSheet,
                        onDismiss = { viewModel.closeCheatSheet() },
                        onFireBroadcastTest = { template ->
                            executeWithNotificationPermission {
                                viewModel.fireSimulatedBroadcastIntent(template)
                            }
                        }
                    )
                }
            }
        }
    }
}
