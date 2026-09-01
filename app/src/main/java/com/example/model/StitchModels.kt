package com.example.model

import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.util.UUID

enum class OutputFormat(val extension: String, val mimeType: String) {
  PNG("png", "image/png"),
  JPEG("jpg", "image/jpeg"),
  WEBP("webp", "image/webp")
}

data class ImageItem(
  val id: String = UUID.randomUUID().toString(),
  val uri: Uri,
  val name: String = "Screenshot",
  val width: Int = 0,
  val height: Int = 0,
  val fileSizeBytes: Long = 0L,
  val thumbnail: Bitmap? = null
)

data class SeamConfig(
  val autoOverlap: Int = 0,
  val manualOffset: Int = 0,
  val confidence: Float = 0f,
  val isAutoDetected: Boolean = false,
  val topTrim: Int = 0,
  val bottomTrim: Int = 0
) {
  val totalOverlap: Int
    get() = (autoOverlap + manualOffset).coerceAtLeast(0)
}

data class StitchGlobalSettings(
  val autoDetectOverlap: Boolean = true,
  val removeStatusBar: Boolean = false,
  val statusBarHeightPx: Int = 80,
  val removeNavBar: Boolean = true,
  val navBarHeightPx: Int = 160,
  val edgeBlending: Boolean = true,
  val outputFormat: OutputFormat = OutputFormat.PNG,
  val outputQuality: Int = 95,
  val preserveOriginalResolution: Boolean = false
)

data class StitchResult(
  val uri: Uri,
  val file: File,
  val width: Int,
  val height: Int,
  val fileSizeBytes: Long,
  val sourceCount: Int = 1,
  val totalOverlapRemoved: Int = 0
)

sealed interface StitchUiState {
  data object Idle : StitchUiState
  data class Detecting(val currentPair: Int, val totalPairs: Int) : StitchUiState
  data class Stitching(val progress: Float, val statusMessage: String) : StitchUiState
  data class Success(val result: StitchResult) : StitchUiState
  data class Error(val message: String) : StitchUiState
}
