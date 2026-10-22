package com.eyeguardian.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DistanceMonitoringService : LifecycleService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val historyStore by lazy { EyeGuardianStore(this) }
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private lateinit var faceDetector: FaceDetector
    private var tooCloseSince = 0L
    private var warningSentForCurrentBlock = false
    private var twentyTwentyRunnable: Runnable? = null
    private var twentyTwentyWaitingForResponse = false
    private var breakSessionPackage: String? = null
    private var breakSessionStartedAt = 0L
    private var breakReminderPending = false
    private var breakCompletedForCurrentSession = false
    private var breakSkippedUntil = 0L
    private val breakCheckRunnable = object : Runnable {
        override fun run() {
            checkForLongAppSession()
            if (isRunning) mainHandler.postDelayed(this, BREAK_CHECK_INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        historyStore.setMonitoringActive(true)
        createNotificationChannels()
        startAsForegroundService()
        scheduleTwentyTwentyReminder()
        mainHandler.post(breakCheckRunnable)

        faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        cameraExecutor,
                        FaceDistanceAnalyzer(faceDetector) { result ->
                            mainHandler.post { handleDistanceResult(result) }
                        }
                    )
                }

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis
                )
                cameraProvider = provider
                imageAnalysis = analysis
            }.onFailure {
                postStatusNotification(
                    title = "Eye Guardian could not start protection",
                    text = "Open Eye Guardian and try starting background protection again."
                )
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // LifecycleService uses its superclass implementation to move the
        // LifecycleOwner to STARTED so CameraX can bind to this service.
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_TWENTY_TWENTY_ACKNOWLEDGED -> {
                dismissTwentyTwentyNotification()
                historyStore.recordTwentyTwentyCompleted()
                scheduleTwentyTwentyReminder()
                postStatusNotification(
                    title = "20/20 reset complete",
                    text = "Nice reset. +20 XP added.",
                    channelId = TWENTY_TWENTY_CHANNEL_ID,
                    iconRes = R.drawable.ic_notification_eye,
                    color = NOTIFICATION_GREEN
                )
            }
            ACTION_TWENTY_TWENTY_SNOOZED -> {
                dismissTwentyTwentyNotification()
                historyStore.recordTwentyTwentySnoozed()
                scheduleTwentyTwentyReminder(SNOOZE_MILLIS)
            }
            ACTION_BREAK_STARTED -> {
                dismissBreakNotification()
                breakReminderPending = false
                breakCompletedForCurrentSession = true
                breakSkippedUntil = 0L
                historyStore.recordBreakCompleted(breakSessionPackage ?: "unknown")
                postStatusNotification(
                    title = "Break started",
                    text = "Nice choice. Look away from the screen for a moment. +20 XP",
                    channelId = BREAK_CHANNEL_ID,
                    iconRes = R.drawable.ic_notification_break,
                    color = NOTIFICATION_BLUE
                )
            }
            ACTION_BREAK_SKIPPED -> {
                dismissBreakNotification()
                breakReminderPending = false
                breakSkippedUntil = System.currentTimeMillis() + breakIntervalMillis()
                historyStore.recordBreakSkipped(breakSessionPackage ?: "unknown")
            }
            ACTION_REFRESH_SETTINGS -> {
                if (intent.getBooleanExtra(EXTRA_SOUND_CHANGED, false)) {
                    recreateReminderChannels()
                }
                if (!twentyTwentyWaitingForResponse) {
                    scheduleTwentyTwentyReminder()
                } else if (!historyStore.isTwentyTwentyEnabled()) {
                    dismissTwentyTwentyNotification()
                    scheduleTwentyTwentyReminder()
                }
                if (!historyStore.isBreakEngineEnabled()) {
                    dismissBreakNotification()
                    breakReminderPending = false
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun scheduleTwentyTwentyReminder(delayMillis: Long = reminderIntervalMillis()) {
        twentyTwentyRunnable?.let(mainHandler::removeCallbacks)
        twentyTwentyRunnable = null
        twentyTwentyWaitingForResponse = false

        if (!historyStore.isTwentyTwentyEnabled()) {
            historyStore.setNextTwentyTwentyAt(0L)
            return
        }

        val triggerAt = System.currentTimeMillis() + delayMillis
        historyStore.setNextTwentyTwentyAt(triggerAt)
        twentyTwentyRunnable = Runnable {
            if (!isRunning || !historyStore.isTwentyTwentyEnabled()) return@Runnable
            twentyTwentyWaitingForResponse = true
            historyStore.setNextTwentyTwentyAt(0L)
            postTwentyTwentyReminder()
        }.also { mainHandler.postDelayed(it, delayMillis) }
    }

    private fun reminderIntervalMillis(): Long {
        return historyStore.getTwentyTwentyIntervalMinutes() * 60_000L
    }

    private fun dismissTwentyTwentyNotification() {
        getSystemService(NotificationManager::class.java)
            .cancel(TWENTY_TWENTY_NOTIFICATION_ID)
    }

    private fun postTwentyTwentyReminder() {
        val acknowledgeIntent = Intent(this, DistanceMonitoringService::class.java).apply {
            action = ACTION_TWENTY_TWENTY_ACKNOWLEDGED
        }
        val snoozeIntent = Intent(this, DistanceMonitoringService::class.java).apply {
            action = ACTION_TWENTY_TWENTY_SNOOZED
        }
        val acknowledgePendingIntent = PendingIntent.getService(
            this,
            TWENTY_TWENTY_ACK_REQUEST_CODE,
            acknowledgeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePendingIntent = PendingIntent.getService(
            this,
            TWENTY_TWENTY_SNOOZE_REQUEST_CODE,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, TWENTY_TWENTY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_eye)
            .setColor(NOTIFICATION_GREEN)
            .setContentTitle("20/20 reset")
            .setContentText("Look at something far away for a moment.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Your eyes have been focusing for a while. Look about 20 feet away and let your eyes relax."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent())
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_play,
                    "I'm on it",
                    acknowledgePendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Snooze 5 min",
                    snoozePendingIntent
                ).build()
            )
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(TWENTY_TWENTY_NOTIFICATION_ID, notification)
    }

    private fun checkForLongAppSession() {
        if (!historyStore.isBreakEngineEnabled() || !hasUsageAccess()) {
            resetBreakSession()
            return
        }

        val now = System.currentTimeMillis()
        val session = currentForegroundUserSession(now)
        if (session == null) {
            resetBreakSession()
            return
        }

        val sessionChanged = session.packageName != breakSessionPackage ||
            session.startedAt != breakSessionStartedAt
        if (sessionChanged) {
            dismissBreakNotification()
            breakSessionPackage = session.packageName
            breakSessionStartedAt = session.startedAt
            breakReminderPending = false
            breakCompletedForCurrentSession = false
            breakSkippedUntil = 0L
        }

        if (breakReminderPending || breakCompletedForCurrentSession) return
        if (breakSkippedUntil > now) return

        val sessionDuration = now - breakSessionStartedAt
        if (sessionDuration >= breakIntervalMillis()) {
            breakReminderPending = true
            postBreakReminder(session.packageName, sessionDuration)
        }
    }

    private fun resetBreakSession() {
        if (breakReminderPending) dismissBreakNotification()
        breakSessionPackage = null
        breakSessionStartedAt = 0L
        breakReminderPending = false
        breakCompletedForCurrentSession = false
        breakSkippedUntil = 0L
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private data class ForegroundUserSession(
        val packageName: String,
        val startedAt: Long
    )

    private fun currentForegroundUserSession(now: Long): ForegroundUserSession? {
        val usageStatsManager = getSystemService(UsageStatsManager::class.java)
        val usageEvents = runCatching {
            usageStatsManager.queryEvents(now - USAGE_EVENT_LOOKBACK_MILLIS, now)
        }.getOrNull() ?: return null

        val ignoredPackages = ignoredForegroundPackages()
        val event = UsageEvents.Event()
        var activePackage: String? = null
        var activeStartedAt = 0L
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val eventPackage = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (activePackage != eventPackage) {
                        activePackage = eventPackage
                        activeStartedAt = event.timeStamp
                    }
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (activePackage == eventPackage) {
                        activePackage = null
                        activeStartedAt = 0L
                    }
                }
            }
        }

        val userPackage = activePackage
        return if (userPackage != null && userPackage !in ignoredPackages && activeStartedAt > 0L) {
            ForegroundUserSession(userPackage, activeStartedAt)
        } else {
            null
        }
    }

    private fun ignoredForegroundPackages(): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homePackages = packageManager
            .queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
        return (homePackages + setOf(
            packageName,
            "android",
            "com.android.systemui",
            "com.android.settings"
        )).toSet()
    }

    private fun breakIntervalMillis(): Long {
        return historyStore.getBreakIntervalMinutes() * 60_000L
    }

    private fun postBreakReminder(appPackage: String, durationMillis: Long) {
        val appLabel = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(appPackage, PackageManager.GET_META_DATA)
            ).toString()
        }.getOrDefault("This app")
        val startBreakPendingIntent = PendingIntent.getService(
            this,
            BREAK_START_REQUEST_CODE,
            Intent(this, DistanceMonitoringService::class.java).apply {
                action = ACTION_BREAK_STARTED
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val skipBreakPendingIntent = PendingIntent.getService(
            this,
            BREAK_SKIP_REQUEST_CODE,
            Intent(this, DistanceMonitoringService::class.java).apply {
                action = ACTION_BREAK_SKIPPED
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val durationMinutes = (durationMillis / 60_000L).coerceAtLeast(1L)
        val notification = NotificationCompat.Builder(this, BREAK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_break)
            .setColor(NOTIFICATION_BLUE)
            .setContentTitle("A screen break would help")
            .setContentText("You have been using $appLabel for about $durationMinutes minutes.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Give your eyes and body a reset before continuing. Even a short break can help you return more comfortably."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent())
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_play,
                    "Start break",
                    startBreakPendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Skip",
                    skipBreakPendingIntent
                ).build()
            )
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(BREAK_NOTIFICATION_ID, notification)
    }

    private fun dismissBreakNotification() {
        getSystemService(NotificationManager::class.java)
            .cancel(BREAK_NOTIFICATION_ID)
    }

    private fun handleDistanceResult(result: DistanceResult) {
        val now = System.currentTimeMillis()
        if (result.status == DistanceStatus.TOO_CLOSE) {
            if (tooCloseSince == 0L) tooCloseSince = now
            if (!warningSentForCurrentBlock && now - tooCloseSince >= WARNING_DELAY_MILLIS) {
                warningSentForCurrentBlock = true
                historyStore.recordDistanceWarning(now)
                postDistanceWarning()
            }
        } else {
            tooCloseSince = 0L
            warningSentForCurrentBlock = false
        }
    }

    private fun startAsForegroundService() {
        val notification = NotificationCompat.Builder(this, MONITORING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Eye Guardian protection is active")
            .setContentText("Checking phone distance while you use other apps")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppPendingIntent())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                MONITORING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(MONITORING_NOTIFICATION_ID, notification)
        }
    }

    private fun postDistanceWarning() {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_distance)
            .setColor(NOTIFICATION_ORANGE)
            .setContentTitle("Take a step back")
            .setContentText("Your phone has been very close for several seconds.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Move the phone a little farther away to give your eyes more space."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent())
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(DISTANCE_WARNING_NOTIFICATION_ID, notification)

        runCatching {
            val vibrator = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(450L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(450L)
            }
        }
    }

    private fun postStatusNotification(
        title: String,
        text: String,
        channelId: String = ALERT_CHANNEL_ID,
        iconRes: Int = R.drawable.ic_notification_distance,
        color: Int = NOTIFICATION_ORANGE
    ) {
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(iconRes)
            .setColor(color)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent())
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(STATUS_NOTIFICATION_ID, notification)
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannels() {
        ensureNotificationChannels(this)
    }

    private fun recreateReminderChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.deleteNotificationChannel(TWENTY_TWENTY_CHANNEL_ID)
        notificationManager.deleteNotificationChannel(BREAK_CHANNEL_ID)
        notificationManager.deleteNotificationChannel(ALERT_CHANNEL_ID)
        createNotificationChannels()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(breakCheckRunnable)
        dismissBreakNotification()
        twentyTwentyRunnable?.let(mainHandler::removeCallbacks)
        twentyTwentyRunnable = null
        historyStore.setNextTwentyTwentyAt(0L)
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        if (::faceDetector.isInitialized) faceDetector.close()
        cameraExecutor.shutdown()
        historyStore.setMonitoringActive(false)
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val MONITORING_CHANNEL_ID = "background_protection"
        const val ALERT_CHANNEL_ID = "distance_alerts"
        const val TWENTY_TWENTY_CHANNEL_ID = "twenty_twenty_resets"
        const val BREAK_CHANNEL_ID = "break_coach"
        const val ACTION_REFRESH_SETTINGS = "com.eyeguardian.app.action.REFRESH_SETTINGS"
        const val EXTRA_SOUND_CHANGED = "com.eyeguardian.app.extra.SOUND_CHANGED"
        private const val ACTION_TWENTY_TWENTY_ACKNOWLEDGED = "com.eyeguardian.app.action.TWENTY_TWENTY_ACKNOWLEDGED"
        private const val ACTION_TWENTY_TWENTY_SNOOZED = "com.eyeguardian.app.action.TWENTY_TWENTY_SNOOZED"
        private const val ACTION_BREAK_STARTED = "com.eyeguardian.app.action.BREAK_STARTED"
        private const val ACTION_BREAK_SKIPPED = "com.eyeguardian.app.action.BREAK_SKIPPED"
        private const val MONITORING_NOTIFICATION_ID = 4101
        private const val DISTANCE_WARNING_NOTIFICATION_ID = 4102
        private const val STATUS_NOTIFICATION_ID = 4103
        private const val TWENTY_TWENTY_NOTIFICATION_ID = 4104
        private const val TWENTY_TWENTY_ACK_REQUEST_CODE = 4105
        private const val TWENTY_TWENTY_SNOOZE_REQUEST_CODE = 4106
        private const val BREAK_NOTIFICATION_ID = 4107
        private const val BREAK_START_REQUEST_CODE = 4108
        private const val BREAK_SKIP_REQUEST_CODE = 4109
        private const val WARNING_DELAY_MILLIS = 5_000L
        private const val SNOOZE_MILLIS = 5 * 60_000L
        private const val BREAK_CHECK_INTERVAL_MILLIS = 15_000L
        private const val USAGE_EVENT_LOOKBACK_MILLIS = 6 * 60 * 60_000L
        private const val NOTIFICATION_GREEN = 0xFF2E7D55.toInt()
        private const val NOTIFICATION_BLUE = 0xFF4F6FD8.toInt()
        private const val NOTIFICATION_ORANGE = 0xFFE28B35.toInt()

        fun ensureNotificationChannels(context: android.content.Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val reminderSound = EyeGuardianStore(context).getNotificationSoundUri()
                ?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    MONITORING_CHANNEL_ID,
                    "Background protection",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    TWENTY_TWENTY_CHANNEL_ID,
                    "20/20 eye resets",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setDescription("Friendly reminders to relax your eyes and look into the distance.")
                    enableVibration(true)
                    setSound(reminderSound, audioAttributes)
                }
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    BREAK_CHANNEL_ID,
                    "Break coach",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setDescription("Encouraging reminders to pause after a long app session.")
                    enableVibration(true)
                    setSound(reminderSound, audioAttributes)
                }
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Distance alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(true)
                    setSound(reminderSound, audioAttributes)
                }
            )
        }
        @Volatile
        var isRunning: Boolean = false
    }
}
