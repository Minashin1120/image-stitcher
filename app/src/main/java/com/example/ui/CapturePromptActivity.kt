package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.R
import com.example.service.OverlayService
import com.example.service.ScreenCaptureService

class CapturePromptActivity : ComponentActivity() {

  companion object {
    const val EXTRA_INTERVAL_MS = "extra_interval_ms"
    const val EXTRA_DEDUPLICATE = "extra_deduplicate"
    const val EXTRA_AUTO_SCROLL = "extra_auto_scroll"
    const val EXTRA_SCROLL_SPEED = "extra_scroll_speed"
    const val EXTRA_SHOW_OVERLAY = "extra_show_overlay"

    fun startDirectCapture(
      context: Context,
      intervalMs: Long = 1000L,
      autoDeduplicate: Boolean = true,
      autoScroll: Boolean = false,
      scrollSpeed: Float = 0.55f,
      showOverlay: Boolean = true
    ) {
      val intent = Intent(context, CapturePromptActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_INTERVAL_MS, intervalMs)
        putExtra(EXTRA_DEDUPLICATE, autoDeduplicate)
        putExtra(EXTRA_AUTO_SCROLL, autoScroll)
        putExtra(EXTRA_SCROLL_SPEED, scrollSpeed)
        putExtra(EXTRA_SHOW_OVERLAY, showOverlay)
      }
      context.startActivity(intent)
    }
  }

  private var intervalMs: Long = 1000L
  private var autoDeduplicate: Boolean = true
  private var autoScroll: Boolean = false
  private var scrollSpeed: Float = 0.55f
  private var showOverlay: Boolean = true

  private val screenCaptureLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
      startCaptureService(result.resultCode, result.data!!)
    } else {
      Toast.makeText(this, getString(R.string.msg_stitch_failed), Toast.LENGTH_SHORT).show()
    }
    finish()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
      @Suppress("DEPRECATION")
      overridePendingTransition(0, 0)
    }
  }

  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { _ ->
    launchMediaProjectionIntent()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, 1000L)
    autoDeduplicate = intent.getBooleanExtra(EXTRA_DEDUPLICATE, true)
    autoScroll = intent.getBooleanExtra(EXTRA_AUTO_SCROLL, false)
    scrollSpeed = intent.getFloatExtra(EXTRA_SCROLL_SPEED, 0.55f)
    showOverlay = intent.getBooleanExtra(EXTRA_SHOW_OVERLAY, true)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return
      }
    }

    launchMediaProjectionIntent()
  }

  private fun launchMediaProjectionIntent() {
    val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val captureIntent = mpManager.createScreenCaptureIntent()
    screenCaptureLauncher.launch(captureIntent)
  }

  private fun startCaptureService(resultCode: Int, data: Intent) {
    val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
      action = ScreenCaptureService.ACTION_START
      putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
      putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
      putExtra(ScreenCaptureService.EXTRA_INTERVAL_MS, intervalMs)
      putExtra(ScreenCaptureService.EXTRA_DEDUPLICATE, autoDeduplicate)
      putExtra(ScreenCaptureService.EXTRA_AUTO_SCROLL, autoScroll)
      putExtra(ScreenCaptureService.EXTRA_SCROLL_SPEED, scrollSpeed)
    }
    ContextCompat.startForegroundService(this, serviceIntent)

    if (showOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
      OverlayService.showOverlay(this, expandSettings = false)
    }
  }
}
