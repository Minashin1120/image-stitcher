package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AutoScrollAccessibilityService : AccessibilityService() {

  companion object {
    private var instance: AutoScrollAccessibilityService? = null

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected = _isServiceConnected.asStateFlow()

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
      if (instance != null) return true
      val expectedComponentName = "${context.packageName}/${AutoScrollAccessibilityService::class.java.name}"
      val simpleComponentName = "${context.packageName}/.service.AutoScrollAccessibilityService"
      val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
      ) ?: return false
      val colonSplitter = TextUtils.SimpleStringSplitter(':')
      colonSplitter.setString(enabledServices)
      while (colonSplitter.hasNext()) {
        val componentName = colonSplitter.next()
        if (componentName.equals(expectedComponentName, ignoreCase = true) ||
          componentName.equals(simpleComponentName, ignoreCase = true)
        ) {
          return true
        }
      }
      return false
    }

    suspend fun performScroll(scrollDistanceRatio: Float = 0.55f, durationMs: Long = 300L): Boolean {
      val service = instance ?: return false
      return service.executeScrollGesture(scrollDistanceRatio, durationMs)
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    _isServiceConnected.value = true
    ScreenCaptureStateHolder.updateState { it.copy(isAccessibilityEnabled = true) }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // No-op
  }

  override fun onInterrupt() {
    // No-op
  }

  override fun onDestroy() {
    super.onDestroy()
    if (instance == this) {
      instance = null
      _isServiceConnected.value = false
      ScreenCaptureStateHolder.updateState { it.copy(isAccessibilityEnabled = false) }
    }
  }

  suspend fun executeScrollGesture(scrollDistanceRatio: Float, durationMs: Long): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
    val displayMetrics = resources.displayMetrics
    val width = displayMetrics.widthPixels.toFloat()
    val height = displayMetrics.heightPixels.toFloat()

    val startX = width / 2f
    val endX = width / 2f
    // For scrolling downward through a page (swiping finger upward)
    val startY = height * 0.70f
    val clampedRatio = scrollDistanceRatio.coerceIn(0.2f, 0.8f)
    val endY = startY - (height * clampedRatio)

    val path = Path().apply {
      moveTo(startX, startY)
      lineTo(endX, endY)
    }

    val gesture = GestureDescription.Builder()
      .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
      .build()

    return suspendCancellableCoroutine { continuation ->
      val callback = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
          if (continuation.isActive) {
            continuation.resume(true)
          }
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
          if (continuation.isActive) {
            continuation.resume(false)
          }
        }
      }
      val dispatched = dispatchGesture(gesture, callback, Handler(Looper.getMainLooper()))
      if (!dispatched) {
        if (continuation.isActive) {
          continuation.resume(false)
        }
      }
    }
  }
}
