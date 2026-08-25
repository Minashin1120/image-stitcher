package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import com.example.model.CropBounds
import com.example.model.EditAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageEditEngine {

  /**
   * Applies editing actions (mosaic, marker, highlighter, crop) onto a bitmap preserving full resolution.
   */
  suspend fun applyEdits(
    sourceBitmap: Bitmap,
    actions: List<EditAction>,
    cropBounds: CropBounds
  ): Bitmap = withContext(Dispatchers.Default) {
    val width = sourceBitmap.width
    val height = sourceBitmap.height

    // 1. Create a mutable copy of the source bitmap for drawing
    var workingBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)

    // 2. Apply all actions in chronological order
    for (action in actions) {
      when (action) {
        is EditAction.MosaicRect -> {
          applyMosaicRect(workingBitmap, action)
        }
        is EditAction.MosaicPen -> {
          applyMosaicPen(workingBitmap, action)
        }
        is EditAction.MarkerStroke -> {
          applyMarkerStroke(workingBitmap, action)
        }
        is EditAction.HighlighterStroke -> {
          applyHighlighterStroke(workingBitmap, action)
        }
      }
    }

    // 3. Apply cropping if specified and not default
    if (!cropBounds.isDefault) {
      val cropX = (cropBounds.left * width).roundToInt().coerceIn(0, width - 1)
      val cropY = (cropBounds.top * height).roundToInt().coerceIn(0, height - 1)
      val cropW = ((cropBounds.right - cropBounds.left) * width).roundToInt().coerceIn(1, width - cropX)
      val cropH = ((cropBounds.bottom - cropBounds.top) * height).roundToInt().coerceIn(1, height - cropY)

      val cropped = Bitmap.createBitmap(workingBitmap, cropX, cropY, cropW, cropH)
      if (cropped != workingBitmap) {
        workingBitmap.recycle()
        workingBitmap = cropped
      }
    }

    workingBitmap
  }

  /**
   * Creates a pixelated/mosaic version of a specific rectangular area in-place.
   */
  private fun applyMosaicRect(bitmap: Bitmap, action: EditAction.MosaicRect) {
    val width = bitmap.width
    val height = bitmap.height

    val rectL = (action.rectRelative.left * width).roundToInt().coerceIn(0, width - 1)
    val rectT = (action.rectRelative.top * height).roundToInt().coerceIn(0, height - 1)
    val rectR = (action.rectRelative.right * width).roundToInt().coerceIn(0, width)
    val rectB = (action.rectRelative.bottom * height).roundToInt().coerceIn(0, height)

    val actualW = rectR - rectL
    val actualH = rectB - rectT
    if (actualW <= 0 || actualH <= 0) return

    val blockSize = max(6, (action.pixelSizeRelative * width).roundToInt())
    pixelateRegion(bitmap, rectL, rectT, rectR, rectB, blockSize)
  }

  /**
   * Applies mosaic along a freehand drawn pen stroke.
   */
  private fun applyMosaicPen(bitmap: Bitmap, action: EditAction.MosaicPen) {
    if (action.points.size < 2) return
    val width = bitmap.width
    val height = bitmap.height

    val blockSize = max(6, (action.pixelSizeRelative * width).roundToInt())
    val strokeWidthPx = max(4f, action.strokeWidthRelative * width)

    // Build the stroke path
    val path = Path()
    val first = action.points[0]
    path.moveTo(first.x * width, first.y * height)
    for (i in 1 until action.points.size) {
      val pt = action.points[i]
      path.lineTo(pt.x * width, pt.y * height)
    }

    // 1. Create a blurred/pixelated copy of the entire image or bounding box
    val pathBounds = android.graphics.RectF()
    path.computeBounds(pathBounds, true)
    val pad = strokeWidthPx * 1.5f
    val l = (pathBounds.left - pad).toInt().coerceIn(0, width - 1)
    val t = (pathBounds.top - pad).toInt().coerceIn(0, height - 1)
    val r = (pathBounds.right + pad).toInt().coerceIn(0, width)
    val b = (pathBounds.bottom + pad).toInt().coerceIn(0, height)

    val roiW = r - l
    val roiH = b - t
    if (roiW <= 0 || roiH <= 0) return

    // Extract ROI bitmap and pixelate it
    val roiBitmap = Bitmap.createBitmap(bitmap, l, t, roiW, roiH)
    pixelateRegion(roiBitmap, 0, 0, roiW, roiH, blockSize)

    // 2. Create mask bitmap for the path
    val maskBitmap = Bitmap.createBitmap(roiW, roiH, Bitmap.Config.ARGB_8888)
    val maskCanvas = Canvas(maskBitmap)
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = strokeWidthPx
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
      color = android.graphics.Color.WHITE
    }
    // Shift path to ROI coordinates
    val shiftedPath = Path(path)
    shiftedPath.offset(-l.toFloat(), -t.toFloat())
    maskCanvas.drawPath(shiftedPath, maskPaint)

    // 3. Mask the pixelated ROI with the path mask
    val maskedMosaicBitmap = Bitmap.createBitmap(roiW, roiH, Bitmap.Config.ARGB_8888)
    val maskedCanvas = Canvas(maskedMosaicBitmap)
    maskedCanvas.drawBitmap(roiBitmap, 0f, 0f, null)
    val blendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    maskedCanvas.drawBitmap(maskBitmap, 0f, 0f, blendPaint)

    // 4. Draw the masked mosaic back onto the main bitmap
    val mainCanvas = Canvas(bitmap)
    mainCanvas.drawBitmap(maskedMosaicBitmap, l.toFloat(), t.toFloat(), null)

    roiBitmap.recycle()
    maskBitmap.recycle()
    maskedMosaicBitmap.recycle()
  }

  /**
   * Helper function to pixelate a region within a bitmap in-place.
   */
  private fun pixelateRegion(
    bitmap: Bitmap,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    blockSize: Int
  ) {
    val width = bitmap.width
    val height = bitmap.height

    val actualL = left.coerceIn(0, width - 1)
    val actualT = top.coerceIn(0, height - 1)
    val actualR = right.coerceIn(0, width)
    val actualB = bottom.coerceIn(0, height)

    val regionW = actualR - actualL
    val regionH = actualB - actualT
    if (regionW <= 0 || regionH <= 0) return

    val pixels = IntArray(regionW * regionH)
    bitmap.getPixels(pixels, 0, regionW, actualL, actualT, regionW, regionH)

    for (by in 0 until regionH step blockSize) {
      val blockH = min(blockSize, regionH - by)
      for (bx in 0 until regionW step blockSize) {
        val blockW = min(blockSize, regionW - bx)

        // Calculate average color in block
        var totalA = 0
        var totalR = 0
        var totalG = 0
        var totalB = 0
        var count = 0

        for (y in 0 until blockH) {
          val rowOffset = (by + y) * regionW + bx
          for (x in 0 until blockW) {
            val c = pixels[rowOffset + x]
            totalA += (c ushr 24) and 0xFF
            totalR += (c ushr 16) and 0xFF
            totalG += (c ushr 8) and 0xFF
            totalB += c and 0xFF
            count++
          }
        }

        if (count > 0) {
          val avgColor = ((totalA / count) shl 24) or
            ((totalR / count) shl 16) or
            ((totalG / count) shl 8) or
            (totalB / count)

          for (y in 0 until blockH) {
            val rowOffset = (by + y) * regionW + bx
            for (x in 0 until blockW) {
              pixels[rowOffset + x] = avgColor
            }
          }
        }
      }
    }

    bitmap.setPixels(pixels, 0, regionW, actualL, actualT, regionW, regionH)
  }

  /**
   * Draws an opaque marker stroke.
   */
  private fun applyMarkerStroke(bitmap: Bitmap, action: EditAction.MarkerStroke) {
    if (action.points.size < 2) return
    val width = bitmap.width
    val height = bitmap.height

    val canvas = Canvas(bitmap)
    val strokeWidthPx = max(2f, action.strokeWidthRelative * width)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = strokeWidthPx
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
      color = action.color.toArgb()
    }

    val path = Path()
    val first = action.points[0]
    path.moveTo(first.x * width, first.y * height)
    for (i in 1 until action.points.size) {
      val pt = action.points[i]
      path.lineTo(pt.x * width, pt.y * height)
    }

    canvas.drawPath(path, paint)
  }

  /**
   * Draws a translucent highlighter stroke ensuring underlying text stays readable.
   */
  private fun applyHighlighterStroke(bitmap: Bitmap, action: EditAction.HighlighterStroke) {
    if (action.points.size < 2) return
    val width = bitmap.width
    val height = bitmap.height

    val strokeWidthPx = max(4f, action.strokeWidthRelative * width)

    // Build path
    val path = Path()
    val first = action.points[0]
    path.moveTo(first.x * width, first.y * height)
    for (i in 1 until action.points.size) {
      val pt = action.points[i]
      path.lineTo(pt.x * width, pt.y * height)
    }

    // Use a separate layer or buffer with alpha to avoid stroke overlapping dark spots
    val layerBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val layerCanvas = Canvas(layerBitmap)

    val baseColor = action.color.toArgb()
    // Force 100% alpha on the temporary path layer
    val solidColor = (0xFF shl 24) or (baseColor and 0x00FFFFFF)

    val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = strokeWidthPx
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
      color = solidColor
    }
    layerCanvas.drawPath(path, pathPaint)

    // Now composite the layer onto the main canvas with translucent alpha (~0.42)
    val mainCanvas = Canvas(bitmap)
    val compositePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
      alpha = 110 // ~43% translucency for crystal-clear highlighter effect
      xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
    }

    mainCanvas.drawBitmap(layerBitmap, 0f, 0f, compositePaint)
    layerBitmap.recycle()
  }

  /**
   * Saves a bitmap to a file with optimal compression quality.
   */
  suspend fun saveBitmapToFile(bitmap: Bitmap, outputFile: File, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, quality: Int = 100): Boolean =
    withContext(Dispatchers.IO) {
      try {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
          bitmap.compress(format, quality, out)
        }
        true
      } catch (e: Exception) {
        e.printStackTrace()
        false
      }
    }
}
