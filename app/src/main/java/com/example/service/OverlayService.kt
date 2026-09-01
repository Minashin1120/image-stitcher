package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.CapturePromptActivity
import com.example.ui.components.OverlayContentView
import com.example.ui.theme.MyApplicationTheme

class OverlayService : Service() {

  companion object {
    const val ACTION_SHOW_OVERLAY = "com.example.service.action.SHOW_OVERLAY"
    const val ACTION_HIDE_OVERLAY = "com.example.service.action.HIDE_OVERLAY"
    const val EXTRA_EXPAND_SETTINGS = "extra_expand_settings"

    private var instance: OverlayService? = null

    fun showOverlay(context: Context, expandSettings: Boolean = false) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
        return
      }
      val intent = Intent(context, OverlayService::class.java).apply {
        action = ACTION_SHOW_OVERLAY
        putExtra(EXTRA_EXPAND_SETTINGS, expandSettings)
      }
      context.startService(intent)
    }

    fun hideOverlay(context: Context) {
      val intent = Intent(context, OverlayService::class.java).apply {
        action = ACTION_HIDE_OVERLAY
      }
      context.startService(intent)
    }

    fun setOverlayHiddenForCapture(hidden: Boolean) {
      instance?.let { service ->
        service.mainHandler.post {
          service.floatingComposeView?.alpha = if (hidden) 0f else 1f
        }
      }
    }
  }

  private val mainHandler = Handler(Looper.getMainLooper())
  private var windowManager: WindowManager? = null
  private var floatingComposeView: ComposeView? = null
  private var windowLayoutParams: WindowManager.LayoutParams? = null
  private val overlayLifecycleOwner = OverlayLifecycleOwner()

  private var isOverlayAttached = false
  private var isExpanded = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    instance = this
    overlayLifecycleOwner.performCreate()
    overlayLifecycleOwner.performStart()
    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_SHOW_OVERLAY -> {
        val expand = intent.getBooleanExtra(EXTRA_EXPAND_SETTINGS, false)
        isExpanded = expand
        ScreenCaptureStateHolder.updateState {
          it.copy(isOverlayVisible = true, isOverlayExpanded = expand)
        }
        showFloatingView()
      }
      ACTION_HIDE_OVERLAY -> {
        hideFloatingView()
        stopSelf()
      }
      else -> {
        showFloatingView()
      }
    }
    return START_NOT_STICKY
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun showFloatingView() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
      stopSelf()
      return
    }

    if (isOverlayAttached && floatingComposeView != null) {
      return
    }

    val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    }

    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      layoutFlag,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = 24
      y = 220
    }
    windowLayoutParams = params

    val composeView = ComposeView(this).apply {
      setViewTreeLifecycleOwner(overlayLifecycleOwner)
      setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
      setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

      setContent {
        MyApplicationTheme {
          val sessionState by ScreenCaptureStateHolder.sessionState.collectAsState()

          OverlayContentView(
            sessionState = sessionState,
            isExpanded = isExpanded,
            onToggleExpand = {
              isExpanded = !isExpanded
              ScreenCaptureStateHolder.updateState { it.copy(isOverlayExpanded = isExpanded) }
            },
            onStartCapture = { intervalMs, deduplicate, autoScroll, scrollSpeed ->
              CapturePromptActivity.startDirectCapture(
                context = this@OverlayService,
                intervalMs = intervalMs,
                autoDeduplicate = deduplicate,
                autoScroll = autoScroll,
                scrollSpeed = scrollSpeed,
                showOverlay = true
              )
              isExpanded = false
              ScreenCaptureStateHolder.updateState { it.copy(isOverlayExpanded = false) }
            },
            onCaptureNow = {
              val captureIntent = Intent(this@OverlayService, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_CAPTURE_NOW
              }
              startService(captureIntent)
            },
            onTogglePause = {
              val pauseIntent = Intent(this@OverlayService, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_TOGGLE_PAUSE
              }
              startService(pauseIntent)
            },
            onToggleAutoScroll = {
              val scrollIntent = Intent(this@OverlayService, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_TOGGLE_AUTO_SCROLL
              }
              startService(scrollIntent)
            },
            onFinishAndStitch = {
              val finishIntent = Intent(this@OverlayService, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_FINISH_AND_STITCH
              }
              startService(finishIntent)
              hideFloatingView()
              stopSelf()
            },
            onCancelCapture = {
              val cancelIntent = Intent(this@OverlayService, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_CANCEL
              }
              startService(cancelIntent)
              hideFloatingView()
              stopSelf()
            },
            onCloseOverlay = {
              hideFloatingView()
              stopSelf()
            }
          )
        }
      }
    }

    // Touch Dragging Handler
    var initialX = 0
    var initialY = 0
    var initialTouchX = 0f
    var initialTouchY = 0f
    var isDragging = false

    composeView.setOnTouchListener { view, event ->
      val p = windowLayoutParams ?: return@setOnTouchListener false
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          initialX = p.x
          initialY = p.y
          initialTouchX = event.rawX
          initialTouchY = event.rawY
          isDragging = false
          false
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = (event.rawX - initialTouchX).toInt()
          val dy = (event.rawY - initialTouchY).toInt()
          if (!isDragging && (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10)) {
            isDragging = true
          }
          if (isDragging) {
            p.x = initialX + dx
            p.y = initialY + dy
            windowManager?.updateViewLayout(composeView, p)
            true
          } else {
            false
          }
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          val dragged = isDragging
          isDragging = false
          dragged
        }
        else -> false
      }
    }

    try {
      windowManager?.addView(composeView, params)
      floatingComposeView = composeView
      isOverlayAttached = true
      ScreenCaptureStateHolder.updateState { it.copy(isOverlayVisible = true) }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun hideFloatingView() {
    if (isOverlayAttached && floatingComposeView != null) {
      try {
        windowManager?.removeView(floatingComposeView)
      } catch (e: Exception) {
        e.printStackTrace()
      }
      floatingComposeView = null
      isOverlayAttached = false
      ScreenCaptureStateHolder.updateState { it.copy(isOverlayVisible = false) }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (instance == this) {
      instance = null
    }
    hideFloatingView()
    overlayLifecycleOwner.performStop()
    overlayLifecycleOwner.performDestroy()
    ScreenCaptureStateHolder.updateState { it.copy(isOverlayVisible = false) }
  }
}
