package com.notiforge.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.notiforge.app.R
import com.notiforge.app.domain.engine.NotificationRenderEngine
import com.notiforge.app.domain.model.NotiPayload
import com.notiforge.app.ipc.MacroDroidEventDispatcher
import com.notiforge.app.ipc.NotiContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Lightweight, autonomous ForegroundService that tracks active countdown notifications
 * without requiring MacroDroid polling loops or CPU wake-locks.
 *
 * Compliant with Android 14/15 (API 34/35) `specialUse` foregroundServiceType, immediate
 * 5-second `startForeground()` window, swipe-to-dismiss cancellation, and automatic
 * process-death recovery (`START_STICKY`).
 */
class TimerForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeTimers = ConcurrentHashMap<String, NotiPayload>()
    private var tickerJob: Job? = null

    @Volatile
    private var currentForegroundTag: String? = null

    @Volatile
    private var isInForeground: Boolean = false

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        NotificationRenderEngine.ensureChannelsCreated(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeInstance = this
        NotificationRenderEngine.ensureChannelsCreated(applicationContext)

        when (intent?.action) {
            ACTION_START_OR_UPDATE_TIMER -> {
                val tag = intent.getStringExtra(NotiContract.Extras.ID)
                val payloadJson = intent.getStringExtra(EXTRA_PAYLOAD_JSON)
                val payload = payloadJson?.let { decodePayload(it) }
                    ?: tag?.let { NotificationRenderEngine.getActivePayload(applicationContext, it) }

                if (payload != null) {
                    val evaluated = evaluateTimerState(payload, System.currentTimeMillis()).first
                    activeTimerTags.add(evaluated.notificationTag)
                    activeTimers[evaluated.notificationTag] = evaluated
                    // Call startForeground IMMEDIATELY before any disk persistence or async work
                    promoteToForegroundAndTick(evaluated)
                    persistActiveTimers()
                } else {
                    checkAndStopIfEmpty()
                }
            }

            ACTION_STOP_TIMER -> {
                val tag = intent.getStringExtra(NotiContract.Extras.ID)
                val shouldCancelNotification = intent.getBooleanExtra(EXTRA_CANCEL_NOTIFICATION, false)
                if (!tag.isNullOrBlank()) {
                    stopTimerInternal(tag, shouldCancelNotification)
                } else {
                    checkAndStopIfEmpty()
                }
            }

            else -> {
                // Process Death recovery (START_STICKY restart by Android OS where intent == null)
                val restored = loadPersistedTimers()
                if (restored.isEmpty() && activeTimers.isEmpty()) {
                    checkAndStopIfEmpty()
                } else {
                    restored.forEach { payload ->
                        activeTimerTags.add(payload.notificationTag)
                        activeTimers[payload.notificationTag] = payload
                    }
                    val firstPayload = activeTimers.values.firstOrNull()
                    if (firstPayload != null) {
                        promoteToForegroundAndTick(firstPayload)
                    } else {
                        checkAndStopIfEmpty()
                    }
                }
            }
        }

        return START_STICKY
    }

    internal fun stopTimerInternal(tag: String, shouldCancelNotification: Boolean) {
        activeTimerTags.remove(tag)
        activeTimers.remove(tag)
        removePersistedTimer(applicationContext, tag)

        if (activeTimers.isEmpty()) {
            tickerJob?.cancel()
            tickerJob = null
            if (!isInForeground) {
                satisfyForegroundRequirementBeforeStop()
            } else {
                try {
                    ServiceCompat.stopForeground(
                        this,
                        if (shouldCancelNotification) {
                            ServiceCompat.STOP_FOREGROUND_REMOVE
                        } else {
                            ServiceCompat.STOP_FOREGROUND_DETACH
                        }
                    )
                } catch (_: Exception) {
                    // Ignored
                }
                isInForeground = false
                currentForegroundTag = null
            }
            if (shouldCancelNotification) {
                NotificationRenderEngine.cancelNotification(applicationContext, tag)
            }
            stopSelf()
        } else {
            persistActiveTimers()
            if (currentForegroundTag == tag) {
                val nextPrimary = activeTimers.values.firstOrNull()
                if (nextPrimary != null) {
                    promoteToForegroundAndTick(nextPrimary)
                }
            }
            if (shouldCancelNotification) {
                NotificationRenderEngine.cancelNotification(applicationContext, tag)
            }
        }
    }

    private fun promoteToForegroundAndTick(primaryPayload: NotiPayload) {
        val evaluatedPrimary = evaluateTimerState(primaryPayload, System.currentTimeMillis()).first
        activeTimers[evaluatedPrimary.notificationTag] = evaluatedPrimary
        val notification = NotificationRenderEngine.buildNotification(
            context = applicationContext,
            payload = evaluatedPrimary,
            isOngoingTimer = !evaluatedPrimary.isTimerCompleted
        )

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        val notificationId = NotiPayload.notificationIdForTag(evaluatedPrimary.notificationTag)
        try {
            ServiceCompat.startForeground(
                this,
                notificationId,
                notification,
                fgsType
            )
            isInForeground = true
            currentForegroundTag = evaluatedPrimary.notificationTag
        } catch (_: Exception) {
            // Fallback to standard notification post if OS restricts FGS promotion
        }

        NotificationRenderEngine.showNotification(
            context = applicationContext,
            payload = evaluatedPrimary,
            isOngoingTimer = !evaluatedPrimary.isTimerCompleted
        )

        ensureTickerRunning()
    }

    private fun ensureTickerRunning() {
        if (tickerJob?.isActive == true) return

        tickerJob = serviceScope.launch {
            while (isActive && activeTimers.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val completedPayloads = mutableListOf<NotiPayload>()

                for ((tag, payload) in activeTimers.entries) {
                    if (!activeTimerTags.contains(tag)) {
                        activeTimers.remove(tag)
                        continue
                    }

                    val (updatedPayload, isFinished) = evaluateTimerState(payload, now)
                    if (isFinished) {
                        completedPayloads.add(updatedPayload)
                    } else {
                        val changed = updatedPayload.progress != payload.progress ||
                            updatedPayload.progressStatusText != payload.progressStatusText
                        activeTimers[tag] = updatedPayload
                        if (changed && activeTimerTags.contains(tag)) {
                            NotificationRenderEngine.showNotification(
                                context = applicationContext,
                                payload = updatedPayload,
                                isOngoingTimer = true
                            )
                        }
                    }
                }

                if (completedPayloads.isNotEmpty()) {
                    completedPayloads.forEach { finished ->
                        activeTimerTags.remove(finished.notificationTag)
                        activeTimers.remove(finished.notificationTag)
                    }
                    persistActiveTimers()

                    if (activeTimers.isEmpty()) {
                        // Detach/remove foreground state BEFORE handling finished notifications
                        // so FLAG_FOREGROUND_SERVICE does not block cancel or lock the completion card
                        try {
                            ServiceCompat.stopForeground(
                                this@TimerForegroundService,
                                ServiceCompat.STOP_FOREGROUND_REMOVE
                            )
                        } catch (_: Exception) {
                            // Ignored
                        }
                        isInForeground = false
                        currentForegroundTag = null

                        completedPayloads.forEach { finished ->
                            handleTimerFinished(finished)
                        }
                        stopSelf()
                        break
                    } else {
                        val currentFg = currentForegroundTag
                        if (currentFg != null && completedPayloads.any { it.notificationTag == currentFg }) {
                            activeTimers.values.firstOrNull()?.let { nextPrimary ->
                                promoteToForegroundAndTick(nextPrimary)
                            }
                        }
                        completedPayloads.forEach { finished ->
                            handleTimerFinished(finished)
                        }
                    }
                }

                if (activeTimers.isEmpty()) {
                    checkAndStopIfEmpty()
                    break
                }

                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun handleTimerFinished(completedPayload: NotiPayload) {
        // 1. Fire outgoing broadcast callback to MacroDroid
        MacroDroidEventDispatcher.dispatchTimerFinished(
            context = applicationContext,
            notificationTag = completedPayload.notificationTag
        )

        // 2. Auto-dismiss or persist with finish_text as a non-ongoing notification
        if (completedPayload.autoDismiss) {
            NotificationRenderEngine.cancelNotification(applicationContext, completedPayload.notificationTag)
        } else {
            NotificationRenderEngine.showNotification(
                context = applicationContext,
                payload = completedPayload,
                isOngoingTimer = false
            )
        }
    }

    private fun satisfyForegroundRequirementBeforeStop() {
        if (!isInForeground) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            val placeholder = NotificationCompat.Builder(applicationContext, NotiContract.CHANNEL_TIMER_ID)
                .setSmallIcon(R.drawable.ic_noti_default)
                .setContentTitle(getString(R.string.app_name))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .build()
            try {
                ServiceCompat.startForeground(this, PLACEHOLDER_FGS_ID, placeholder, fgsType)
                isInForeground = true
            } catch (_: Exception) {
                // Ignored if started via normal startService
            }
        }
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Ignored
        }
        isInForeground = false
        currentForegroundTag = null
    }

    private fun checkAndStopIfEmpty() {
        if (activeTimers.isEmpty()) {
            tickerJob?.cancel()
            tickerJob = null
            satisfyForegroundRequirementBeforeStop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        tickerJob?.cancel()
        tickerJob = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun persistActiveTimers() {
        val prefs = applicationContext.getSharedPreferences(PREFS_TIMERS, Context.MODE_PRIVATE)
        val serialized = json.encodeToString(activeTimers.values.toList())
        prefs.edit().putString(KEY_ACTIVE_LIST, serialized).apply()
    }

    private fun loadPersistedTimers(): List<NotiPayload> {
        val prefs = applicationContext.getSharedPreferences(PREFS_TIMERS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ACTIVE_LIST, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<NotiPayload>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val ACTION_START_OR_UPDATE_TIMER = "com.notiforge.action.START_OR_UPDATE_TIMER"
        private const val ACTION_STOP_TIMER = "com.notiforge.action.STOP_TIMER"
        private const val EXTRA_PAYLOAD_JSON = "extra_payload_json"
        private const val EXTRA_CANCEL_NOTIFICATION = "extra_cancel_notification"
        private const val PREFS_TIMERS = "notiengine_active_timers"
        private const val KEY_ACTIVE_LIST = "active_timers_json"
        private const val TICK_INTERVAL_MS = 1_000L
        private const val PLACEHOLDER_FGS_ID = 999_001

        private val activeTimerTags = ConcurrentHashMap.newKeySet<String>()

        @Volatile
        private var activeInstance: TimerForegroundService? = null

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private fun decodePayload(raw: String): NotiPayload? {
            return try {
                json.decodeFromString<NotiPayload>(raw)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Computes current progress (0..100) and human-readable remaining time string
         * based on `startTimeEpochMillis` and `durationMinutes`.
         */
        fun evaluateTimerState(
            payload: NotiPayload,
            nowMillis: Long
        ): Pair<NotiPayload, Boolean> {
            val totalDurationMillis = (payload.durationMinutes.coerceAtLeast(1) * 60_000L)
            val elapsedMillis = (nowMillis - payload.startTimeEpochMillis).coerceAtLeast(0L)

            if (elapsedMillis >= totalDurationMillis) {
                val doneStatus = payload.finishText.ifBlank { "Completed" }
                val finishedPayload = payload.copy(
                    progress = 100,
                    progressStatusText = "Done",
                    statusBadge = "DONE",
                    isTimerCompleted = true,
                    body = doneStatus
                )
                return finishedPayload to true
            }

            val fraction = (elapsedMillis.toDouble() / totalDurationMillis.toDouble()).coerceIn(0.0, 1.0)
            val progressPercent = (fraction * 100.0).roundToInt().coerceIn(0, 99)
            val remainingMillis = (totalDurationMillis - elapsedMillis).coerceAtLeast(0L)
            val totalRemainingSeconds = ceil(remainingMillis / 1_000.0).toInt().coerceAtLeast(1)
            val remainingMinutes = totalRemainingSeconds / 60
            val remainingSeconds = totalRemainingSeconds % 60

            val statusReadout = when {
                remainingMinutes >= 60 -> {
                    val hrs = remainingMinutes / 60
                    val mins = remainingMinutes % 60
                    if (mins == 0) "${hrs}h left" else "${hrs}h ${mins}m left"
                }
                remainingMinutes > 0 -> {
                    String.format(Locale.US, "%dm %02ds left", remainingMinutes, remainingSeconds)
                }
                else -> "${remainingSeconds}s left"
            }

            return payload.copy(
                progress = progressPercent,
                progressStatusText = statusReadout,
                isTimerCompleted = false
            ) to false
        }

        private fun removePersistedTimer(context: Context, notificationTag: String) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_TIMERS, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_ACTIVE_LIST, null) ?: return
            try {
                val current = json.decodeFromString<List<NotiPayload>>(raw)
                val filtered = current.filterNot { it.notificationTag == notificationTag }
                prefs.edit().putString(KEY_ACTIVE_LIST, json.encodeToString(filtered)).apply()
            } catch (_: Exception) {
                prefs.edit().remove(KEY_ACTIVE_LIST).apply()
            }
        }

        fun startOrUpdateTimer(context: Context, payload: NotiPayload) {
            val appContext = context.applicationContext
            NotificationRenderEngine.ensureChannelsCreated(appContext)
            val initialEvaluated = evaluateTimerState(payload, System.currentTimeMillis()).first
            activeTimerTags.add(initialEvaluated.notificationTag)

            // Immediately render initial timer state so the user sees zero latency
            NotificationRenderEngine.showNotification(
                context = appContext,
                payload = initialEvaluated,
                isOngoingTimer = !initialEvaluated.isTimerCompleted
            )

            val serviceIntent = Intent(appContext, TimerForegroundService::class.java).apply {
                action = ACTION_START_OR_UPDATE_TIMER
                putExtra(NotiContract.Extras.ID, initialEvaluated.notificationTag)
                putExtra(EXTRA_PAYLOAD_JSON, json.encodeToString(initialEvaluated))
            }
            try {
                ContextCompat.startForegroundService(appContext, serviceIntent)
            } catch (_: Exception) {
                try {
                    appContext.startService(serviceIntent)
                } catch (_: Exception) {
                    // Ignored under strict background restriction; initial notification is already posted
                }
            }
        }

        fun stopTimer(
            context: Context,
            notificationTag: String,
            cancelNotification: Boolean = false
        ) {
            val appContext = context.applicationContext
            val wasTracked = activeTimerTags.remove(notificationTag)
            removePersistedTimer(appContext, notificationTag)

            val runningService = activeInstance
            if (runningService != null) {
                runningService.stopTimerInternal(notificationTag, cancelNotification)
                return
            }

            if (cancelNotification) {
                NotificationRenderEngine.cancelNotification(appContext, notificationTag)
            }
            if (!wasTracked) {
                return
            }
            val serviceIntent = Intent(appContext, TimerForegroundService::class.java).apply {
                action = ACTION_STOP_TIMER
                putExtra(NotiContract.Extras.ID, notificationTag)
                putExtra(EXTRA_CANCEL_NOTIFICATION, cancelNotification)
            }
            try {
                appContext.startService(serviceIntent)
            } catch (_: Exception) {
                if (cancelNotification) {
                    NotificationRenderEngine.cancelNotification(appContext, notificationTag)
                }
            }
        }
    }
}
