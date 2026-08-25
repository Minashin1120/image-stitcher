package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.example.R
import com.example.model.ImageItem
import com.example.model.OutputFormat
import com.example.model.SeamConfig
import com.example.model.StitchGlobalSettings
import com.example.model.StitchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object StitchEngine {

  /**
   * Load image bounds and file size without decoding full bitmap into memory.
   */
  fun getImageMetadata(context: Context, uri: Uri): ImageItem {
    var width = 0
    var height = 0
    var sizeBytes = 0L

    try {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        val options = BitmapFactory.Options().apply {
          inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(stream, null, options)
        width = options.outWidth
        height = options.outHeight
      }

      context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        sizeBytes = pfd.statSize
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    val name = getFileName(context, uri) ?: context.getString(R.string.screenshot_default_name)
    return ImageItem(
      uri = uri,
      name = name,
      width = width,
      height = height,
      fileSizeBytes = sizeBytes
    )
  }

  /**
   * Load a downscaled thumbnail for UI rendering and seam previews.
   */
  suspend fun loadThumbnail(context: Context, uri: Uri, maxDimension: Int = 480): Bitmap? =
    withContext(Dispatchers.IO) {
      try {
        var sampleSize = 1
        context.contentResolver.openInputStream(uri)?.use { stream ->
          val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
          }
          BitmapFactory.decodeStream(stream, null, options)
          val maxDim = max(options.outWidth, options.outHeight)
          while (maxDim / sampleSize > maxDimension * 2) {
            sampleSize *= 2
          }
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
          val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
          }
          BitmapFactory.decodeStream(stream, null, options)
        }
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    }

  /**
   * Decode full bitmap with memory protection.
   * If maxWidth <= 0, decode at 100% original full resolution without downscaling.
   */
  suspend fun loadFullBitmap(context: Context, uri: Uri, maxWidth: Int = 2160): Bitmap? =
    withContext(Dispatchers.IO) {
      try {
        var sampleSize = 1
        if (maxWidth > 0) {
          context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply {
              inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(stream, null, options)
            if (options.outWidth > maxWidth) {
              sampleSize = (options.outWidth.toFloat() / maxWidth).roundToInt().coerceAtLeast(1)
            }
          }
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
          val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
          }
          BitmapFactory.decodeStream(stream, null, options)
        }
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    }

  /**
   * Automatically detect overlap between top image (img1) and bottom image (img2).
   * Returns SeamConfig with autoOverlap in original pixel coordinates.
   */
  suspend fun detectOverlap(
    topBitmap: Bitmap,
    bottomBitmap: Bitmap,
    settings: StitchGlobalSettings
  ): SeamConfig = withContext(Dispatchers.Default) {
    if (topBitmap.width == 0 || topBitmap.height == 0 || bottomBitmap.width == 0 || bottomBitmap.height == 0) {
      return@withContext SeamConfig(autoOverlap = 0, confidence = 0f, isAutoDetected = true)
    }

    // Step 1: Normalize scale for comparison
    val matchWidth = 180
    val scale1 = matchWidth.toFloat() / topBitmap.width
    val scale2 = matchWidth.toFloat() / bottomBitmap.width

    val h1 = (topBitmap.height * scale1).roundToInt()
    val h2 = (bottomBitmap.height * scale2).roundToInt()

    if (h1 < 20 || h2 < 20) {
      return@withContext SeamConfig(autoOverlap = 0, confidence = 0f, isAutoDetected = true)
    }

    val scaled1 = Bitmap.createScaledBitmap(topBitmap, matchWidth, h1, true)
    val scaled2 = Bitmap.createScaledBitmap(bottomBitmap, matchWidth, h2, true)

    val gray1 = extractGrayscaleMatrix(scaled1)
    val gray2 = extractGrayscaleMatrix(scaled2)

    val topTrimScaled = if (settings.removeStatusBar) (settings.statusBarHeightPx * scale2).roundToInt() else 0
    val bottomTrimScaled = if (settings.removeNavBar) (settings.navBarHeightPx * scale1).roundToInt() else 0

    val minOverlap = 15
    val maxOverlap = min((h1 - bottomTrimScaled), (h2 - topTrimScaled)) * 85 / 100

    var bestOverlapScaled = 0
    var minDifference = Double.MAX_VALUE
    var bestVariance = 0.0

    // Compare horizontal rows in downscaled space
    val step = 1
    for (overlap in minOverlap..maxOverlap step step) {
      val y1Start = h1 - bottomTrimScaled - overlap
      val y2Start = topTrimScaled

      if (y1Start < 0 || y2Start + overlap > h2) continue

      var totalDiff = 0.0
      var varianceSum = 0.0
      var samples = 0

      // Sample every 2nd row for performance
      for (dy in 0 until overlap step 2) {
        val r1 = gray1[y1Start + dy]
        val r2 = gray2[y2Start + dy]

        // Compare row luminance
        var rowMean1 = 0.0
        var rowMean2 = 0.0
        for (x in 0 until matchWidth step 4) {
          rowMean1 += r1[x]
          rowMean2 += r2[x]
        }
        val count = matchWidth / 4
        rowMean1 /= count
        rowMean2 /= count

        var rowVar = 0.0
        for (x in 0 until matchWidth step 4) {
          val v1 = r1[x]
          val v2 = r2[x]
          val d = abs(v1 - v2)
          totalDiff += d
          val dev = v1 - rowMean1
          rowVar += dev * dev
          samples++
        }
        varianceSum += rowVar / count
      }

      if (samples > 0) {
        val avgDiff = totalDiff / samples
        val avgVar = varianceSum / (overlap / 2 + 1)
        // Score favors low difference and penalizes flat/blank regions
        val score = avgDiff / (1.0 + min(avgVar / 100.0, 5.0))

        if (score < minDifference) {
          minDifference = score
          bestOverlapScaled = overlap
          bestVariance = avgVar
        }
      }
    }

    if (bestOverlapScaled <= 0) {
      return@withContext SeamConfig(autoOverlap = 0, confidence = 0f, isAutoDetected = true)
    }

    // Map back to original topBitmap scale
    val initialEstimatedOverlap = (bestOverlapScaled / scale1).roundToInt()

    // Step 2: Full-Resolution Fine Verification within +/- 20 pixels
    val fineOffset = refineOverlapFullRes(
      topBitmap = topBitmap,
      bottomBitmap = bottomBitmap,
      initialOverlap = initialEstimatedOverlap,
      topTrim = if (settings.removeStatusBar) settings.statusBarHeightPx else 0,
      bottomTrim = if (settings.removeNavBar) settings.navBarHeightPx else 0
    )

    val finalOverlap = fineOffset.first
    val confidence = fineOffset.second

    SeamConfig(
      autoOverlap = finalOverlap,
      confidence = confidence,
      isAutoDetected = true,
      topTrim = if (settings.removeStatusBar) settings.statusBarHeightPx else 0,
      bottomTrim = if (settings.removeNavBar) settings.navBarHeightPx else 0
    )
  }

  private fun refineOverlapFullRes(
    topBitmap: Bitmap,
    bottomBitmap: Bitmap,
    initialOverlap: Int,
    topTrim: Int,
    bottomTrim: Int
  ): Pair<Int, Float> {
    val searchRadius = 24
    val minCandidate = max(10, initialOverlap - searchRadius)
    val maxCandidate = min(topBitmap.height - bottomTrim - 10, initialOverlap + searchRadius)

    val sampleWidth = min(topBitmap.width, bottomBitmap.width)
    if (sampleWidth <= 0) return Pair(initialOverlap, 0.5f)

    var bestOverlap = initialOverlap
    var minDiff = Double.MAX_VALUE
    var matchedVariance = 0.0

    val strideX = max(1, sampleWidth / 64)
    val strideY = 2

    for (overlap in minCandidate..maxCandidate) {
      val y1Start = topBitmap.height - bottomTrim - overlap
      val y2Start = topTrim

      if (y1Start < 0 || y2Start + overlap > bottomBitmap.height) continue

      var totalDiff = 0.0
      var samples = 0
      var varAcc = 0.0

      for (dy in 0 until overlap step strideY) {
        val y1 = y1Start + dy
        val y2 = y2Start + dy

        for (x in 0 until sampleWidth step strideX) {
          val p1 = topBitmap.getPixel(x, y1)
          val p2 = bottomBitmap.getPixel(x, y2)

          val lum1 = (Color.red(p1) * 299 + Color.green(p1) * 587 + Color.blue(p1) * 114) / 1000
          val lum2 = (Color.red(p2) * 299 + Color.green(p2) * 587 + Color.blue(p2) * 114) / 1000

          val diff = abs(lum1 - lum2)
          totalDiff += diff
          varAcc += abs(lum1 - 128)
          samples++
        }
      }

      if (samples > 0) {
        val avgDiff = totalDiff / samples
        if (avgDiff < minDiff) {
          minDiff = avgDiff
          bestOverlap = overlap
          matchedVariance = varAcc / samples
        }
      }
    }

    val confidence = when {
      minDiff < 8.0 && matchedVariance > 15.0 -> 0.98f
      minDiff < 15.0 -> 0.88f
      minDiff < 25.0 -> 0.70f
      minDiff < 40.0 -> 0.50f
      else -> 0.25f
    }

    return Pair(bestOverlap, confidence)
  }

  private fun extractGrayscaleMatrix(bitmap: Bitmap): Array<IntArray> {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val matrix = Array(height) { IntArray(width) }
    for (y in 0 until height) {
      val rowOffset = y * width
      for (x in 0 until width) {
        val c = pixels[rowOffset + x]
        val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        matrix[y][x] = lum
      }
    }
    return matrix
  }

  /**
   * Stitches multiple images in sequence into a single seamless output file.
   */
  suspend fun stitchImages(
    context: Context,
    images: List<ImageItem>,
    seams: List<SeamConfig>,
    settings: StitchGlobalSettings,
    onProgress: (Float, String) -> Unit
  ): Result<StitchResult> = withContext(Dispatchers.IO) {
    if (images.size < 2) {
      return@withContext Result.failure(IllegalArgumentException("At least 2 images are required to stitch"))
    }

    try {
      onProgress(0.1f, context.getString(R.string.progress_loading_screenshots))

      // Decode full bitmaps
      val maxWidth = if (settings.preserveOriginalResolution) 0 else 2160
      val bitmaps = mutableListOf<Bitmap>()
      for (i in images.indices) {
        onProgress(
          0.1f + 0.2f * (i.toFloat() / images.size),
          context.getString(R.string.progress_loading_image, i + 1, images.size)
        )
        val bmp = loadFullBitmap(context, images[i].uri, maxWidth = maxWidth)
          ?: return@withContext Result.failure(IllegalStateException("Failed to load image: ${images[i].name}"))
        bitmaps.add(bmp)
      }

      val targetWidth = bitmaps.maxOf { it.width }

      // Compute slice rectangles and final canvas height
      var totalHeight = 0
      var totalOverlapRemoved = 0
      val drawOperations = mutableListOf<DrawOp>()

      for (i in bitmaps.indices) {
        val bmp = bitmaps[i]
        val seamBefore = if (i > 0) seams.getOrNull(i - 1) ?: SeamConfig() else null
        val seamAfter = if (i < bitmaps.size - 1) seams.getOrNull(i) ?: SeamConfig() else null

        // Crop top if following previous image with overlap
        val cropTop: Int = if (i == 0) {
          if (settings.removeStatusBar) settings.statusBarHeightPx.coerceAtMost(bmp.height / 4) else 0
        } else {
          val overlap = seamBefore?.totalOverlap ?: 0
          val topTrim = seamBefore?.topTrim ?: 0
          (overlap + topTrim).coerceAtMost(bmp.height - 1)
        }

        // Crop bottom if preceding next image
        val cropBottom: Int = if (i == bitmaps.size - 1) {
          if (settings.removeNavBar) settings.navBarHeightPx.coerceAtMost(bmp.height / 4) else 0
        } else {
          val bottomTrim = seamAfter?.bottomTrim ?: 0
          bottomTrim.coerceAtMost(bmp.height - 1)
        }

        val sliceHeight = (bmp.height - cropTop - cropBottom).coerceAtLeast(1)
        val srcRect = Rect(0, cropTop, bmp.width, bmp.height - cropBottom)
        val dstRect = Rect(0, totalHeight, targetWidth, totalHeight + sliceHeight)

        drawOperations.add(DrawOp(bitmap = bmp, srcRect = srcRect, dstRect = dstRect))
        totalHeight += sliceHeight

        if (seamBefore != null) {
          totalOverlapRemoved += seamBefore.totalOverlap
        }
      }

      onProgress(
        0.6f,
        context.getString(R.string.progress_composing_canvas, targetWidth, totalHeight)
      )

      // Verify max height texture limit (safe bounds)
      val maxAllowedHeight = 32768
      val finalOutputBitmap: Bitmap = if (totalHeight > maxAllowedHeight) {
        val scale = maxAllowedHeight.toFloat() / totalHeight
        val scaledWidth = (targetWidth * scale).roundToInt()
        val scaledHeight = maxAllowedHeight
        Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
      } else {
        Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
      }

      val canvas = Canvas(finalOutputBitmap)
      val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

      val isScaled = totalHeight > maxAllowedHeight
      val globalScale = if (isScaled) maxAllowedHeight.toFloat() / totalHeight else 1f

      for ((index, op) in drawOperations.withIndex()) {
        onProgress(
          0.6f + 0.25f * (index.toFloat() / drawOperations.size),
          context.getString(R.string.progress_rendering_slice, index + 1)
        )
        val targetDst = if (isScaled) {
          Rect(
            (op.dstRect.left * globalScale).roundToInt(),
            (op.dstRect.top * globalScale).roundToInt(),
            (op.dstRect.right * globalScale).roundToInt(),
            (op.dstRect.bottom * globalScale).roundToInt()
          )
        } else {
          op.dstRect
        }
        canvas.drawBitmap(op.bitmap, op.srcRect, targetDst, paint)
      }

      onProgress(0.9f, context.getString(R.string.progress_saving_screenshot))

      val outputDir = File(context.cacheDir, "stitched").apply { mkdirs() }
      val fileName = "stitched_${System.currentTimeMillis()}.${settings.outputFormat.extension}"
      val outputFile = File(outputDir, fileName)

      FileOutputStream(outputFile).use { out ->
        val compressFormat = when (settings.outputFormat) {
          OutputFormat.PNG -> Bitmap.CompressFormat.PNG
          OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
          OutputFormat.WEBP -> Bitmap.CompressFormat.WEBP
        }
        finalOutputBitmap.compress(compressFormat, settings.outputQuality, out)
      }

      val result = StitchResult(
        uri = Uri.fromFile(outputFile),
        file = outputFile,
        width = finalOutputBitmap.width,
        height = finalOutputBitmap.height,
        fileSizeBytes = outputFile.length(),
        sourceCount = images.size,
        totalOverlapRemoved = totalOverlapRemoved
      )

      onProgress(1.0f, context.getString(R.string.progress_complete))
      Result.success(result)
    } catch (e: Throwable) {
      e.printStackTrace()
      Result.failure(e)
    }
  }

  private data class DrawOp(
    val bitmap: Bitmap,
    val srcRect: Rect,
    val dstRect: Rect
  )

  private fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    try {
      context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
          if (index != -1) {
            name = cursor.getString(index)
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return name ?: uri.lastPathSegment
  }
}
