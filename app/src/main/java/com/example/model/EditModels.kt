package com.example.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

enum class EditTool {
  MARKER,
  HIGHLIGHTER,
  MOSAIC,
  CROP
}

enum class MosaicMode {
  PEN,
  RECTANGLE
}

sealed interface EditAction {
  val id: String

  data class MarkerStroke(
    val points: List<Offset>, // In relative coordinate (0f..1f)
    val color: Color,
    val strokeWidthRelative: Float, // Relative to image width
    override val id: String = java.util.UUID.randomUUID().toString()
  ) : EditAction

  data class HighlighterStroke(
    val points: List<Offset>, // In relative coordinate (0f..1f)
    val color: Color,
    val strokeWidthRelative: Float, // Relative to image width
    override val id: String = java.util.UUID.randomUUID().toString()
  ) : EditAction

  data class MosaicPen(
    val points: List<Offset>, // In relative coordinate (0f..1f)
    val strokeWidthRelative: Float,
    val pixelSizeRelative: Float,
    override val id: String = java.util.UUID.randomUUID().toString()
  ) : EditAction

  data class MosaicRect(
    val rectRelative: Rect, // In relative coordinate (0f..1f)
    val pixelSizeRelative: Float,
    override val id: String = java.util.UUID.randomUUID().toString()
  ) : EditAction
}

data class CropBounds(
  val left: Float = 0f,
  val top: Float = 0f,
  val right: Float = 1f,
  val bottom: Float = 1f
) {
  val isDefault: Boolean
    get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f

  val widthRatio: Float
    get() = (right - left).coerceIn(0.01f, 1f)

  val heightRatio: Float
    get() = (bottom - top).coerceIn(0.01f, 1f)
}
