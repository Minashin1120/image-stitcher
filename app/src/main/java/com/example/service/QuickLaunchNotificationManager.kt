package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.CapturePromptActivity

object QuickLaunchNotificationManager {
  const val CHANNEL_ID = "quick_launch_persistent_channel"
  const val NOTIFICATION_ID = 2002

  fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.quick_launch_channel_name),
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = context.getString(R.string.quick_launch_channel_desc)
        setShowBadge(false)
        enableLights(false)
        enableVibration(false)
      }
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  fun updatePersistentNotification(context: Context, enabled: Boolean) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (!enabled) {
      manager.cancel(NOTIFICATION_ID)
      return
    }

    createNotificationChannel(context)

    // Tap action: open overlay if permitted, else open MainActivity
    val contentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
      val overlayTrampolineIntent = Intent(context, CapturePromptActivity::class.java).apply {
        action = CapturePromptActivity.ACTION_SHOW_OVERLAY
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        putExtra(CapturePromptActivity.EXTRA_EXPAND_SETTINGS, true)
      }
      PendingIntent.getActivity(
        context,
        10,
        overlayTrampolineIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    } else {
      val mainIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
      }
      PendingIntent.getActivity(
        context,
        10,
        mainIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    }

    // Action 1: ⚙️ Controls / Settings Overlay (uses Activity trampoline so notification shade closes automatically)
    val overlayActionIntent = Intent(context, CapturePromptActivity::class.java).apply {
      action = CapturePromptActivity.ACTION_SHOW_OVERLAY
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
      putExtra(CapturePromptActivity.EXTRA_EXPAND_SETTINGS, true)
    }
    val overlayPendingIntent = PendingIntent.getActivity(
      context,
      11,
      overlayActionIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Action 2: 📸 Quick Start Capture
    val promptIntent = Intent(context, CapturePromptActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(CapturePromptActivity.EXTRA_INTERVAL_MS, 1000L)
      putExtra(CapturePromptActivity.EXTRA_DEDUPLICATE, true)
      putExtra(CapturePromptActivity.EXTRA_AUTO_SCROLL, false)
      putExtra(CapturePromptActivity.EXTRA_SCROLL_SPEED, 0.55f)
      putExtra(CapturePromptActivity.EXTRA_SHOW_OVERLAY, true)
    }
    val promptPendingIntent = PendingIntent.getActivity(
      context,
      12,
      promptIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Action 3: 📱 Open Main App
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val openAppPendingIntent = PendingIntent.getActivity(
      context,
      13,
      openAppIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.app_icon_stitcher_1787673296680)
      .setContentTitle(context.getString(R.string.persistent_notification_title))
      .setContentText(context.getString(R.string.persistent_notification_desc))
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(contentIntent)
      .addAction(0, context.getString(R.string.persistent_notification_action_overlay), overlayPendingIntent)
      .addAction(0, context.getString(R.string.persistent_notification_action_start), promptPendingIntent)
      .addAction(0, context.getString(R.string.persistent_notification_action_open_app), openAppPendingIntent)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()

    manager.notify(NOTIFICATION_ID, notification)
  }
}
