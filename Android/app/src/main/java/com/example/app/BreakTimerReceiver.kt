package com.eyeguardian.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import androidx.core.app.NotificationCompat

/** Keeps the manual 20-minute reset alive when the app is no longer visible. */
class BreakTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = EyeGuardianStore(context)
        when (intent?.action) {
            ACTION_START -> {
                store.setManualBreakActive(true)
                store.setManualBreakStartedAt(System.currentTimeMillis())
                store.setManualBreakAwardedBlocks(0)
                scheduleCheck(context)
            }
            ACTION_STOP -> {
                if (store.isManualBreakActive()) {
                    val startedAt = store.getManualBreakStartedAt()
                    val elapsed = System.currentTimeMillis() - startedAt
                    val targetBlocks = BreakRewardPolicy.creditedBlocks(
                        elapsedMillis = elapsed,
                        alreadyCreditedBlocks = store.getManualBreakAwardedBlocks(),
                        continuing = false
                    )
                    if (targetBlocks > store.getManualBreakAwardedBlocks()) {
                        // Stopping directly after the reminder earns the first
                        // block only. Extra blocks require an explicit Continue.
                        awardBlocks(store, startedAt, targetBlocks)
                    } else {
                        store.recordBreakSkipped("manual reset")
                    }
                    clearBreakState(store)
                }
                cancelCheck(context)
                notificationManager(context).cancel(NOTIFICATION_ID)
            }
            ACTION_CONTINUE -> {
                if (store.isManualBreakActive()) {
                    val startedAt = store.getManualBreakStartedAt()
                    val elapsed = System.currentTimeMillis() - startedAt
                    val targetBlocks = BreakRewardPolicy.creditedBlocks(
                        elapsedMillis = elapsed,
                        alreadyCreditedBlocks = store.getManualBreakAwardedBlocks(),
                        continuing = true
                    )
                    awardBlocks(store, startedAt, targetBlocks)
                    scheduleCheck(context)
                }
                notificationManager(context).cancel(NOTIFICATION_ID)
            }
            ACTION_CHECK -> {
                if (store.isManualBreakActive()) postCheckNotification(context)
            }
        }
    }

    private fun scheduleCheck(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + BREAK_DURATION_MILLIS
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(context, ACTION_CHECK, REQUEST_CHECK)
        )
    }

    private fun cancelCheck(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            ?.cancel(pendingIntent(context, ACTION_CHECK, REQUEST_CHECK))
    }

    private fun postCheckNotification(context: Context) {
        DistanceMonitoringService.ensureNotificationChannels(context)
        val stopIntent = pendingIntent(context, ACTION_STOP, REQUEST_STOP)
        val continueIntent = pendingIntent(context, ACTION_CONTINUE, REQUEST_CONTINUE)
        val notification = NotificationCompat.Builder(
            context,
            DistanceMonitoringService.BREAK_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification_break)
            .setColor(0xFF4F6FD8.toInt())
            .setContentTitle("Break check-in")
            .setContentText("Are you still taking your 20-minute eye reset?")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Your reset timer is complete. Stop the break when you are ready, or continue for a little longer."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_play, "I'm still on it", continueIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "I'm back", stopIntent)
            .build()
        notificationManager(context).notify(NOTIFICATION_ID, notification)
    }

    private fun awardBlocks(
        store: EyeGuardianStore,
        startedAt: Long,
        targetBlocks: Int
    ) {
        val target = targetBlocks.coerceAtLeast(0)
        val alreadyAwarded = store.getManualBreakAwardedBlocks()
        if (target <= alreadyAwarded) return
        for (block in (alreadyAwarded + 1)..target) {
            store.recordBreakCompleted(
                "manual reset",
                startedAt + block * BREAK_DURATION_MILLIS
            )
        }
        store.setManualBreakAwardedBlocks(target)
    }

    private fun clearBreakState(store: EyeGuardianStore) {
        store.setManualBreakActive(false)
        store.setManualBreakStartedAt(0L)
        store.setManualBreakAwardedBlocks(0)
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, BreakTimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notificationManager(context: Context): NotificationManager {
        return context.getSystemService(NotificationManager::class.java)
    }

    companion object {
        const val ACTION_START = "com.eyeguardian.app.action.MANUAL_BREAK_START"
        const val ACTION_STOP = "com.eyeguardian.app.action.MANUAL_BREAK_STOP"
        const val ACTION_CONTINUE = "com.eyeguardian.app.action.MANUAL_BREAK_CONTINUE"
        const val ACTION_CHECK = "com.eyeguardian.app.action.MANUAL_BREAK_CHECK"
        private const val REQUEST_CHECK = 4120
        private const val REQUEST_STOP = 4121
        private const val REQUEST_CONTINUE = 4122
        private const val NOTIFICATION_ID = 4123
        private const val BREAK_DURATION_MILLIS = BreakRewardPolicy.BLOCK_MILLIS

        fun start(context: Context) {
            context.sendBroadcast(Intent(context, BreakTimerReceiver::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.sendBroadcast(Intent(context, BreakTimerReceiver::class.java).setAction(ACTION_STOP))
        }
    }
}
