package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.service.AutoScrollAccessibilityService
import com.example.service.ScreenCaptureService
import com.example.service.ScreenCaptureStateHolder
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.StitchViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val viewModel: StitchViewModel by viewModels()

  private var pendingIntervalMs: Long = 1000L
  private var pendingDeduplicate: Boolean = true
  private var pendingAutoScroll: Boolean = false
  private var pendingScrollSpeed: Float = 0.55f

  // MediaProjection permission launcher
  private val screenCaptureLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
      startScreenCaptureService(result.resultCode, result.data!!)
    } else {
      Toast.makeText(this, getString(R.string.msg_stitch_failed), Toast.LENGTH_SHORT).show()
    }
  }

  // Notification permission launcher (Android 13+)
  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    launchMediaProjectionIntent()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Handle initial intent if shared from another app
    handleIncomingIntent(intent)

    // Listen for completed capture sessions to automatically load into StitchViewModel
    lifecycleScope.launch {
      ScreenCaptureStateHolder.captureCompletedEvent.collectLatest { uris ->
        if (uris.isNotEmpty()) {
          viewModel.addImages(uris)
          Toast.makeText(
            this@MainActivity,
            getString(R.string.msg_capture_completed, uris.size),
            Toast.LENGTH_LONG
          ).show()
        }
      }
    }

    // Set up persistent quick launch notification
    com.example.service.QuickLaunchNotificationManager.updatePersistentNotification(this, true)

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          MainScreen(
            viewModel = viewModel,
            onRequestStartCapture = { intervalMs, deduplicate, autoScroll, scrollSpeed ->
              pendingIntervalMs = intervalMs
              pendingDeduplicate = deduplicate
              pendingAutoScroll = autoScroll
              pendingScrollSpeed = scrollSpeed
              checkNotificationAndRequestCapture()
            },
            onCaptureNow = {
              val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_CAPTURE_NOW
              }
              startService(intent)
            },
            onTogglePause = {
              val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_TOGGLE_PAUSE
              }
              startService(intent)
            },
            onToggleAutoScroll = {
              val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_TOGGLE_AUTO_SCROLL
              }
              startService(intent)
            },
            onFinishAndStitch = {
              val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_FINISH_AND_STITCH
              }
              startService(intent)
            },
            onCancelCapture = {
              val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_CANCEL
              }
              startService(intent)
            }
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    val isAccEnabled = AutoScrollAccessibilityService.isAccessibilityServiceEnabled(this)
    ScreenCaptureStateHolder.updateState { it.copy(isAccessibilityEnabled = isAccEnabled) }
    com.example.service.QuickLaunchNotificationManager.updatePersistentNotification(
      this,
      viewModel.settings.value.persistentNotification
    )
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  private fun handleIncomingIntent(intent: Intent?) {
    if (intent == null) return
    val action = intent.action ?: return

    val uris = mutableListOf<android.net.Uri>()

    if (Intent.ACTION_SEND == action) {
      val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
      }
      if (streamUri != null) {
        uris.add(streamUri)
      } else if (intent.clipData != null && intent.clipData!!.itemCount > 0) {
        intent.clipData!!.getItemAt(0)?.uri?.let { uris.add(it) }
      } else if (intent.data != null) {
        uris.add(intent.data!!)
      }
    } else if (Intent.ACTION_SEND_MULTIPLE == action) {
      val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
      }
      if (!streamUris.isNullOrEmpty()) {
        uris.addAll(streamUris.filterNotNull())
      } else if (intent.clipData != null) {
        for (i in 0 until intent.clipData!!.itemCount) {
          intent.clipData!!.getItemAt(i)?.uri?.let { uris.add(it) }
        }
      }
    }

    if (uris.isNotEmpty()) {
      viewModel.handleIncomingSharedUris(uris)
    }
  }

  private fun checkNotificationAndRequestCapture() {
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

  private fun startScreenCaptureService(resultCode: Int, data: Intent) {
    val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
      action = ScreenCaptureService.ACTION_START
      putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
      putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
      putExtra(ScreenCaptureService.EXTRA_INTERVAL_MS, pendingIntervalMs)
      putExtra(ScreenCaptureService.EXTRA_DEDUPLICATE, pendingDeduplicate)
      putExtra(ScreenCaptureService.EXTRA_AUTO_SCROLL, pendingAutoScroll)
      putExtra(ScreenCaptureService.EXTRA_SCROLL_SPEED, pendingScrollSpeed)
    }
    ContextCompat.startForegroundService(this, serviceIntent)

    if (viewModel.settings.value.floatingOverlayEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && android.provider.Settings.canDrawOverlays(this)) {
      com.example.service.OverlayService.showOverlay(this, expandSettings = false)
    }
  }
}
