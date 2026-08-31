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
  }
}
