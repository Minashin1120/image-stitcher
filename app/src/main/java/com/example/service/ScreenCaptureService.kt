package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class ScreenCaptureService : Service() {

  companion object {
    const val CHANNEL_ID = "screen_capture_service_channel"
    const val NOTIFICATION_ID = 2001

    const val ACTION_START = "com.example.service.action.START"
    const val ACTION_CAPTURE_NOW = "com.example.service.action.CAPTURE_NOW"
    const val ACTION_TOGGLE_PAUSE = "com.example.service.action.TOGGLE_PAUSE"
    const val ACTION_TOGGLE_AUTO_SCROLL = "com.example.service.action.TOGGLE_AUTO_SCROLL"
    const val ACTION_FINISH_AND_STITCH = "com.example.service.action.FINISH_AND_STITCH"
    const val ACTION_CANCEL = "com.example.service.action.CANCEL"

    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"
    const val EXTRA_INTERVAL_MS = "extra_interval_ms"
    const val EXTRA_DEDUPLICATE = "extra_deduplicate"
    const val EXTRA_AUTO_SCROLL = "extra_auto_scroll"
    const val EXTRA_SCROLL_SPEED = "extra_scroll_speed"
  }

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var captureLoopJob: Job? = null

  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null

  private var screenWidth = 1080
  private var screenHeight = 2400
  private var screenDensity = DisplayMetrics.DENSITY_DEFAULT

  private var intervalMs = 500L
  private var autoDeduplicate = true
  private var autoScrollEnabled = false
  private var scrollSpeedRatio = 0.40f
  private var isPaused = false

  private val capturedFiles = mutableListOf<File>()
  private var lastSampleBitmap: Bitmap? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START -> {
        cleanup()
        capturedFiles.clear()
        lastSampleBitmap?.recycle()
        lastSampleBitmap = null

        // Clean up old temporary captures from disk
        val captureDir = File(cacheDir, "screen_captures")
        if (captureDir.exists()) {
          captureDir.listFiles()?.forEach { oldFile ->
            try { oldFile.delete() } catch (_: Exception) {}
          }
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, 500L).coerceIn(100L, 500L)
        autoDeduplicate = intent.getBooleanExtra(EXTRA_DEDUPLICATE, true)
        autoScrollEnabled = intent.getBooleanExtra(EXTRA_AUTO_SCROLL, false)
        scrollSpeedRatio = intent.getFloatExtra(EXTRA_SCROLL_SPEED, 0.40f).coerceIn(0.1f, 0.40f)

        if (resultCode != 0 && resultData != null) {
          startForegroundWithNotification()
          initMediaProjection(resultCode, resultData)
        } else {
          stopSelf()
        }
      }
      ACTION_CAPTURE_NOW -> {
        serviceScope.launch {
          captureCurrentFrame()
        }
      }
      ACTION_TOGGLE_PAUSE -> {
        isPaused = !isPaused
        ScreenCaptureStateHolder.updateState {
          it.copy(isPaused = isPaused)
        }
        updateNotification()
      }
      ACTION_TOGGLE_AUTO_SCROLL -> {
        autoScrollEnabled = !autoScrollEnabled
        ScreenCaptureStateHolder.updateState {
          it.copy(autoScrollEnabled = autoScrollEnabled)
        }
        updateNotification()
      }
      ACTION_FINISH_AND_STITCH -> {
        finishAndStitch()
      }
      ACTION_CANCEL -> {
        cancelAndCleanup()
      }
    }
    return START_NOT_STICKY
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        getString(R.string.capture_channel_name),
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = getString(R.string.capture_channel_desc)
        setShowBadge(false)
        enableLights(false)
        enableVibration(false)
      }
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  private fun startForegroundWithNotification() {
    val notification = buildNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun buildNotification(): Notification {
    val openAppIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val openAppPendingIntent = PendingIntent.getActivity(
      this, 0, openAppIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Action: 📸 Capture Now
    val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
      action = ACTION_CAPTURE_NOW
    }
    val capturePendingIntent = PendingIntent.getService(
      this, 1, captureIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Action: 📜 Auto-Scroll toggle
    val scrollToggleIntent = Intent(this, ScreenCaptureService::class.java).apply {
      action = ACTION_TOGGLE_AUTO_SCROLL
    }
    val scrollTogglePendingIntent = PendingIntent.getService(
      this, 2, scrollToggleIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Action: ⏸️ Pause / ▶️ Resume
    val pauseIntent = Intent(this, ScreenCaptureService::class.java).apply {
      action = ACTION_TOGGLE_PAUSE
    }
    val pausePendingIntent = PendingIntent.getService(
      this, 3, pauseIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Action: ⏹️ Finish and Stitch
    val finishIntent = Intent(this, ScreenCaptureService::class.java).apply {
      action = ACTION_FINISH_AND_STITCH
    }
    val finishPendingIntent = PendingIntent.getService(
      this, 4, finishIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val count = capturedFiles.size
    val intervalSec = intervalMs / 1000f

    val scrollStatusBadge = if (autoScrollEnabled) {
      getString(R.string.capture_autoscroll_active_badge)
    } else {
      ""
    }

    val statusText = if (isPaused) {
      getString(R.string.capture_status_paused, count) + scrollStatusBadge
    } else {
      getString(R.string.capture_status_running, String.format(java.util.Locale.US, "%.1f", intervalSec), count) + scrollStatusBadge
    }

    val pauseActionTitle = if (isPaused) {
      getString(R.string.capture_action_resume)
    } else {
      getString(R.string.capture_action_pause)
    }

    val scrollActionTitle = if (autoScrollEnabled) {
      getString(R.string.capture_action_autoscroll_on)
    } else {
      getString(R.string.capture_action_autoscroll_off)
    }

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.app_icon_stitcher_1787673296680)
      .setContentTitle(getString(R.string.capture_notification_title))
      .setContentText(statusText)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(openAppPendingIntent)
      .addAction(0, getString(R.string.capture_action_capture_now), capturePendingIntent)
      .addAction(0, scrollActionTitle, scrollTogglePendingIntent)
      .addAction(0, pauseActionTitle, pausePendingIntent)
      .addAction(0, getString(R.string.capture_action_finish_stitch), finishPendingIntent)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  private fun updateNotification() {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(NOTIFICATION_ID, buildNotification())
  }

  private fun initMediaProjection(resultCode: Int, resultData: Intent) {
    capturedFiles.clear()
    lastSampleBitmap?.recycle()
    lastSampleBitmap = null

    val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    windowManager.defaultDisplay.getRealMetrics(metrics)

    screenWidth = metrics.widthPixels
    screenHeight = metrics.heightPixels
    screenDensity = metrics.densityDpi

    val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

    if (mediaProjection == null) {
      stopSelf()
      return
    }

    mediaProjection?.registerCallback(object : MediaProjection.Callback() {
      override fun onStop() {
        cleanup()
      }
    }, Handler(Looper.getMainLooper()))

    imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 3)
    virtualDisplay = mediaProjection?.createVirtualDisplay(
      "ScreenCaptureService",
      screenWidth,
      screenHeight,
      screenDensity,
      DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
      imageReader?.surface,
      null,
      null
    )

    val isAccEnabled = AutoScrollAccessibilityService.isAccessibilityServiceEnabled(this)

    ScreenCaptureStateHolder.updateState {
      it.copy(
        isRunning = true,
        isPaused = false,
        capturedCount = 0,
        intervalSeconds = intervalMs / 1000f,
        autoDeduplicate = autoDeduplicate,
        autoScrollEnabled = autoScrollEnabled,
        scrollSpeedRatio = scrollSpeedRatio,
        isAccessibilityEnabled = isAccEnabled,
        lastCapturedThumbnail = null
      )
    }

    startCaptureLoop()
  }

  private fun startCaptureLoop() {
    captureLoopJob?.cancel()
    captureLoopJob = serviceScope.launch {
      // Allow user initial time to switch to destination app
      delay(600)
      
      // First frame capture
      if (isActive && !isPaused) {
        captureCurrentFrame()
      }

      while (isActive) {
        if (!isPaused) {
          if (autoScrollEnabled) {
            // Auto scroll down content
            AutoScrollAccessibilityService.performScroll(scrollSpeedRatio, 180L)
            // Wait for scroll animation to finish and UI to render
            delay(120L)
          }
          captureCurrentFrame()
        }
        delay(intervalMs)
      }
    }
  }

  private suspend fun captureCurrentFrame(): Boolean {
    val reader = imageReader ?: return false
    var image: Image? = null
    try {
      // Hide floating overlay so it does not appear in the screenshot
      OverlayService.setOverlayHiddenForCapture(true)
      delay(60L) // Allow display compositor to render clean frame

      image = reader.acquireLatestImage()
      if (image == null) {
        // Retry a few times if buffer is in flight
        for (retry in 0 until 5) {
          delay(60L)
          image = reader.acquireLatestImage()
          if (image != null) break
        }
      }
      OverlayService.setOverlayHiddenForCapture(false)

      if (image == null) return false

      val planes = image.planes
      val buffer = planes[0].buffer
      val pixelStride = planes[0].pixelStride
      val rowStride = planes[0].rowStride
      val rowPadding = rowStride - pixelStride * screenWidth

      val fullBitmap = Bitmap.createBitmap(
        screenWidth + rowPadding / pixelStride,
        screenHeight,
        Bitmap.Config.ARGB_8888
      )
      fullBitmap.copyPixelsFromBuffer(buffer)

      val croppedBitmap = if (rowPadding == 0) {
        fullBitmap
      } else {
        val cropped = Bitmap.createBitmap(fullBitmap, 0, 0, screenWidth, screenHeight)
        fullBitmap.recycle()
        cropped
      }

      // Check deduplication if enabled and we already have at least 1 image
      if (autoDeduplicate && capturedFiles.isNotEmpty() && isDuplicate(croppedBitmap)) {
        croppedBitmap.recycle()
        return false
      }

      // Save sample for next duplicate check
      val sample = Bitmap.createScaledBitmap(croppedBitmap, 64, 64, true)
      lastSampleBitmap?.recycle()
      lastSampleBitmap = sample

      // Save to disk
      val captureDir = File(cacheDir, "screen_captures").apply { mkdirs() }
      val file = File(captureDir, "capture_${System.currentTimeMillis()}_${capturedFiles.size + 1}.png")

      withContext(Dispatchers.IO) {
        FileOutputStream(file).use { out ->
          croppedBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
      }

      val thumb = Bitmap.createScaledBitmap(croppedBitmap, 180, (180f * screenHeight / screenWidth).toInt(), true)
      croppedBitmap.recycle()

      capturedFiles.add(file)

      withContext(Dispatchers.Main) {
        ScreenCaptureStateHolder.updateState {
          it.copy(
            capturedCount = capturedFiles.size,
            lastCapturedThumbnail = thumb
          )
        }
        updateNotification()
      }
      return true
    } catch (e: Exception) {
      e.printStackTrace()
      return false
    } finally {
      OverlayService.setOverlayHiddenForCapture(false)
      image?.close()
    }
  }

  private fun isDuplicate(newBitmap: Bitmap): Boolean {
    val prevSample = lastSampleBitmap ?: return false
    val newSample = Bitmap.createScaledBitmap(newBitmap, 64, 64, true)

    var diffPixels = 0
    val total = 64 * 64
    for (y in 0 until 64) {
      for (x in 0 until 64) {
        val p1 = prevSample.getPixel(x, y)
        val p2 = newSample.getPixel(x, y)

        val rDiff = abs(((p1 shr 16) and 0xFF) - ((p2 shr 16) and 0xFF))
        val gDiff = abs(((p1 shr 8) and 0xFF) - ((p2 shr 8) and 0xFF))
        val bDiff = abs((p1 and 0xFF) - (p2 and 0xFF))

        if (rDiff + gDiff + bDiff > 40) {
          diffPixels++
        }
      }
    }
    newSample.recycle()
    val diffRatio = diffPixels.toFloat() / total
    // If difference is less than 1.5%, consider it unchanged / duplicate
    return diffRatio < 0.015f
  }

  private fun finishAndStitch() {
    serviceScope.launch {
      captureLoopJob?.cancel()

      // If no frames captured yet, capture the current screen frame immediately
      if (capturedFiles.isEmpty()) {
        captureCurrentFrame()
      }

      val uris = capturedFiles.map { Uri.fromFile(it) }
      ScreenCaptureStateHolder.notifyCaptureCompleted(uris)
      ScreenCaptureStateHolder.reset()

      // Bring MainActivity to front with captured URIs
      val mainIntent = Intent(this@ScreenCaptureService, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putParcelableArrayListExtra("extra_captured_uris", ArrayList(uris))
      }
      startActivity(mainIntent)

      capturedFiles.clear()
      stopSelf()
    }
  }

  private fun cancelAndCleanup() {
    capturedFiles.forEach { file ->
      try {
        file.delete()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
    capturedFiles.clear()
    ScreenCaptureStateHolder.reset()
    stopSelf()
  }

  private fun cleanup() {
    captureLoopJob?.cancel()
    lastSampleBitmap?.recycle()
    lastSampleBitmap = null

    try {
      virtualDisplay?.release()
      virtualDisplay = null
      imageReader?.close()
      imageReader = null
      mediaProjection?.stop()
      mediaProjection = null
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    cleanup()
    serviceScope.cancel()
    ScreenCaptureStateHolder.reset()
  }
}
