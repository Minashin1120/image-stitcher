package com.example.service

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class CaptureSessionState(
  val isRunning: Boolean = false,
  val isPaused: Boolean = false,
  val capturedCount: Int = 0,
  val intervalSeconds: Float = 1.0f,
  val autoDeduplicate: Boolean = true,
  val autoScrollEnabled: Boolean = false,
  val scrollSpeedRatio: Float = 0.55f,
  val isAccessibilityEnabled: Boolean = false,
  val lastCapturedThumbnail: Bitmap? = null
)

object ScreenCaptureStateHolder {
  private val _sessionState = MutableStateFlow(CaptureSessionState())
  val sessionState: StateFlow<CaptureSessionState> = _sessionState.asStateFlow()

  private val _captureCompletedEvent = MutableSharedFlow<List<Uri>>(extraBufferCapacity = 1)
  val captureCompletedEvent: SharedFlow<List<Uri>> = _captureCompletedEvent.asSharedFlow()

  fun updateState(transform: (CaptureSessionState) -> CaptureSessionState) {
    _sessionState.value = transform(_sessionState.value)
  }

  fun notifyCaptureCompleted(uris: List<Uri>) {
    _captureCompletedEvent.tryEmit(uris)
  }

  fun reset() {
    _sessionState.value = CaptureSessionState()
  }
}
