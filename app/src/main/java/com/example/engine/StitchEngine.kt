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
import com.example.model.StitchOrientation
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
   * High-accuracy overlap detection for ImageItems with support for headers, collapsible app bars,
   * static bottom navigation bars, and transparent gesture navigation bars.
   */
  suspend fun detectOverlap(
    context: Context,
    topItem: ImageItem,
    bottomItem: ImageItem,
    settings: StitchGlobalSettings
  ): SeamConfig = withContext(Dispatchers.Default) {
    if (topItem.width == 0 || topItem.height == 0 || bottomItem.width == 0 || bottomItem.height == 0) {
      return@withContext SeamConfig(autoOverlap = 0, confidence = 0f, isAutoDetected = true)
    }

    if (settings.orientation == StitchOrientation.HORIZONTAL) {
      val matchHeight = 360
      val leftThumb = topItem.thumbnail ?: loadThumbnail(context, topItem.uri, matchHeight)
      val rightThumb = bottomItem.thumbnail ?: loadThumbnail(context, bottomItem.uri, matchHeight)

      if (leftThumb == null || rightThumb == null) {
        return@withContext SeamConfig(autoOverlap = 0, confidence = 0f, isAutoDetected = true)
      }

      return@withContext detectHorizontalOverlapInternal(
        leftBitmap = leftThumb,
        rightBitmap = rightThumb,
        settings = settings,
        origWidth1 = topItem.width,
        origHeight1 = topItem.height,
        origWidth2 = bottomItem.width,
        origHeight2 = bottomItem.height
      )
    }

    val matchWidth = 360
    val topThumb = topItem.thumbnail ?: loadThumbnail(context, topItem.uri, matchWidth)
    val bottomThumb = bottomItem.thumbnail ?: loadThumbnail(context, bottomItem.uri, matchWidth)

    if (topThumb == null || bottomThumb == null) {
      return@withContext SeamConfig(autoOverlap = 0, confidence = 0f, isAutoDetected = true)
    }

    detectOverlapInternal(
      topBitmap = topThumb,
      bottomBitmap = bottomThumb,
      settings = settings,
      origWidth1 = topItem.width,
      origHeight1 = topItem.height,
      origWidth2 = bottomItem.width,
      origHeight2 = bottomItem.height
    )
  }

  /**
   * Overlap detection fallback using direct Bitmaps.
   */
  suspend fun detectOverlap(
    topBitmap: Bitmap,
    bottomBitmap: Bitmap,
    settings: StitchGlobalSettings
  ): SeamConfig = withContext(Dispatchers.Default) {
    if (topBitmap.width == 0 || topBitmap.height == 0 || bottomBitmap.width == 0 || bottomBitmap.height == 0) {
      return@withContext SeamConfig(autoOverlap = 0, confidence = 0f, isAutoDetected = true)
    }

    if (settings.orientation == StitchOrientation.HORIZONTAL) {
      return@withContext detectHorizontalOverlapInternal(
        leftBitmap = topBitmap,
        rightBitmap = bottomBitmap,
        settings = settings,
        origWidth1 = topBitmap.width,
        origHeight1 = topBitmap.height,
        origWidth2 = bottomBitmap.width,
        origHeight2 = bottomBitmap.height
      )
    }

    detectOverlapInternal(
      topBitmap = topBitmap,
      bottomBitmap = bottomBitmap,
      settings = settings,
      origWidth1 = topBitmap.width,
      origHeight1 = topBitmap.height,
      origWidth2 = bottomBitmap.width,
      origHeight2 = bottomBitmap.height
    )
  }

  private fun detectHorizontalOverlapInternal(
    leftBitmap: Bitmap,
    rightBitmap: Bitmap,
    settings: StitchGlobalSettings,
    origWidth1: Int,
    origHeight1: Int,
    origWidth2: Int,
    origHeight2: Int
  ): SeamConfig {
    val matchHeight = 360
    val scale1 = matchHeight.toFloat() / leftBitmap.height
    val scale2 = matchHeight.toFloat() / rightBitmap.height

    val w1 = (leftBitmap.width * scale1).roundToInt().coerceAtLeast(20)
    val w2 = (rightBitmap.width * scale2).roundToInt().coerceAtLeast(20)

    val scaled1 = if (leftBitmap.height == matchHeight && leftBitmap.width == w1) {
      leftBitmap
    } else {
      Bitmap.createScaledBitmap(leftBitmap, w1, matchHeight, true)
    }
    val scaled2 = if (rightBitmap.height == matchHeight && rightBitmap.width == w2) {
      rightBitmap
    } else {
      Bitmap.createScaledBitmap(rightBitmap, w2, matchHeight, true)
    }

    val gray1 = extractGrayscaleMatrix(scaled1)
    val gray2 = extractGrayscaleMatrix(scaled2)

    val scaleRatioX1 = origWidth1.toFloat() / w1

    val minShift = max(4, (min(w1, w2) * 0.03f).roundToInt())
    val maxShift = (w1 - 4).coerceAtLeast(minShift + 1)

    var bestShiftScaled = 0
    var bestScore = Double.MAX_VALUE
    var bestAvgDiff = Double.MAX_VALUE

    for (shift in minShift..maxShift) {
      val x2Start = 0
      val x2End = min(w2, w1 - shift)
      val overlapCols = x2End - x2Start

      if (overlapCols < 14) continue

      var totalDiff = 0.0
      var varianceSum = 0.0
      var samples = 0

      val stepX = if (overlapCols > 70) 2 else 1
      val stepY = 4

      for (x2 in x2Start until x2End step stepX) {
        val x1 = x2 + shift
        for (y in 0 until matchHeight step stepY) {
          val v1 = gray1[y][x1]
          val v2 = gray2[y][x2]
          totalDiff += abs(v1 - v2)
          val dev = v2 - 128
          varianceSum += dev * dev
          samples++
        }
      }

      if (samples > 0) {
        val avgDiff = totalDiff / samples
        val avgVar = varianceSum / samples
        val score = avgDiff / (1.0 + min(avgVar / 2000.0, 3.0))

        if (score < bestScore) {
          bestScore = score
          bestShiftScaled = shift
          bestAvgDiff = avgDiff
        }
      }
    }

    if (bestShiftScaled <= 0 || bestAvgDiff > 45.0) {
      return SeamConfig(
        autoOverlap = 0,
        confidence = 0f,
        isAutoDetected = true,
        leftTrim = 0,
        rightTrim = 0
      )
    }

    val overlapColumnsScaled = w1 - bestShiftScaled
    val confidence = (1.0 - (bestAvgDiff / 45.0)).toFloat().coerceIn(0.2f, 1f)
    val autoOverlapOrig = (overlapColumnsScaled * scaleRatioX1).roundToInt()

    return SeamConfig(
      autoOverlap = autoOverlapOrig.coerceAtLeast(0),
      confidence = confidence,
      isAutoDetected = true,
      leftTrim = 0,
      rightTrim = 0
    )
  }

  private fun detectOverlapInternal(
    topBitmap: Bitmap,
    bottomBitmap: Bitmap,
    settings: StitchGlobalSettings,
    origWidth1: Int,
    origHeight1: Int,
    origWidth2: Int,
    origHeight2: Int
  ): SeamConfig {
    val matchWidth = 360
    val scale1 = matchWidth.toFloat() / topBitmap.width
    val scale2 = matchWidth.toFloat() / bottomBitmap.width

    val h1 = (topBitmap.height * scale1).roundToInt().coerceAtLeast(20)
    val h2 = (bottomBitmap.height * scale2).roundToInt().coerceAtLeast(20)

    val scaled1 = if (topBitmap.width == matchWidth && topBitmap.height == h1) {
      topBitmap
    } else {
      Bitmap.createScaledBitmap(topBitmap, matchWidth, h1, true)
    }
    val scaled2 = if (bottomBitmap.width == matchWidth && bottomBitmap.height == h2) {
      bottomBitmap
    } else {
      Bitmap.createScaledBitmap(bottomBitmap, matchWidth, h2, true)
    }

    val gray1 = extractGrayscaleMatrix(scaled1)
    val gray2 = extractGrayscaleMatrix(scaled2)

    val scaleRatioY1 = origHeight1.toFloat() / h1
    val scaleRatioY2 = origHeight2.toFloat() / h2

    // 1. Detect static top headers (status bar + app bar identical in both images)
    val maxHeaderCheckRows = (min(h1, h2) * 0.48f).roundToInt()
    val staticTopRows = detectStaticTopRows(gray1, gray2, matchWidth, maxHeaderCheckRows)

    // 2. Detect static bottom navigation bar (e.g. YouTube bottom 5-tab bar, Chrome bottom bar)
    val maxFooterCheckRows = (min(h1, h2) * 0.32f).roundToInt()
    val staticBottomRows = detectStaticBottomRows(gray1, gray2, matchWidth, maxFooterCheckRows)

    // 3. Detect transparent navigation bar with gesture pill / handle
    val gesturePillRows1 = detectTransparentNavBarPill(gray1, matchWidth, h1)
    val gesturePillRows2 = detectTransparentNavBarPill(gray2, matchWidth, h2)

    val statusBarTrimScaled = if (settings.removeStatusBar) {
      (settings.statusBarHeightPx / scaleRatioY2).roundToInt()
    } else {
      (h2 * 0.035f).roundToInt()
    }

    val navBarTrimScaled = if (settings.removeNavBar) {
      (settings.navBarHeightPx / scaleRatioY1).roundToInt()
    } else 0

    val topInset1 = maxOf(staticTopRows, statusBarTrimScaled)
    val bottomInset1 = maxOf(staticBottomRows, gesturePillRows1, navBarTrimScaled)
    val topInset2 = maxOf(staticTopRows, statusBarTrimScaled)
    val bottomInset2 = maxOf(staticBottomRows, gesturePillRows2, navBarTrimScaled)

    // 4. Search for vertical scroll displacement `shift` where y1 = y2 + shift
    // For collapsible headers (e.g. YouTube top bar collapsing on scroll):
    // In image 1, top header can be topInset1 or larger if an app bar collapsed in image 2.
    val minShift = max(4, (min(h1, h2) * 0.02f).roundToInt())
    val maxShift = (h1 - topInset1 - bottomInset1 - 4).coerceAtLeast(minShift + 1)

    // Candidate header start heights in image 1 (to handle collapsible headers)
    val header1Candidates = mutableListOf(topInset1)
    val appHeaderExtra1 = (h1 * 0.055f).roundToInt()
    val appHeaderExtra2 = (h1 * 0.095f).roundToInt()
    val appHeaderExtra3 = (h1 * 0.140f).roundToInt()
    if (topInset1 + appHeaderExtra1 < h1 / 2) header1Candidates.add(topInset1 + appHeaderExtra1)
    if (topInset1 + appHeaderExtra2 < h1 / 2) header1Candidates.add(topInset1 + appHeaderExtra2)
    if (topInset1 + appHeaderExtra3 < h1 / 2) header1Candidates.add(topInset1 + appHeaderExtra3)

    var bestShiftScaled = 0
    var bestScore = Double.MAX_VALUE
    var bestHeader1 = topInset1
    var bestAvgDiff = Double.MAX_VALUE

    val testStartX = (matchWidth * 0.04f).roundToInt()
    val testEndX = (matchWidth * 0.93f).roundToInt()

    for (head1 in header1Candidates) {
      for (shift in minShift..maxShift) {
        val y2Start = max(topInset2, head1 - shift)
        val y2End = min(h2 - bottomInset2, h1 - bottomInset1 - shift)
        val overlapRows = y2End - y2Start

        if (overlapRows < 14) continue

        var totalDiff = 0.0
        var varianceSum = 0.0
        var samples = 0

        val stepY = if (overlapRows > 70) 2 else 1
        val stepX = 4

        for (y2 in y2Start until y2End step stepY) {
          val y1 = y2 + shift
          val r1 = gray1[y1]
          val r2 = gray2[y2]

          for (x in testStartX until testEndX step stepX) {
            // Give lower weight to bottom-right floating buttons so they don't bias alignment
            if (y1 > h1 * 0.72f && x > matchWidth * 0.65f) {
              continue
            }
            val v1 = r1[x]
            val v2 = r2[x]
            totalDiff += abs(v1 - v2)
            val dev = v2 - 128
            varianceSum += dev * dev
            samples++
          }
        }

        if (samples > 0) {
          val avgDiff = totalDiff / samples
          val avgVar = varianceSum / samples
          // Favor low difference and penalize low-variance/flat zones
          val score = avgDiff / (1.0 + min(avgVar / 2000.0, 3.0))

          if (score < bestScore) {
            bestScore = score
            bestShiftScaled = shift
            bestHeader1 = head1
            bestAvgDiff = avgDiff
          }
        }
      }
    }

    if (bestShiftScaled <= 0 || bestAvgDiff > 45.0) {
      // Fallback: No robust overlap detected
      val fallbackTopTrim = if (settings.removeStatusBar) settings.statusBarHeightPx else 0
      val fallbackBottomTrim = if (settings.removeNavBar) settings.navBarHeightPx else 0
      return SeamConfig(
        autoOverlap = 0,
        confidence = 0f,
        isAutoDetected = true,
        topTrim = fallbackTopTrim,
        bottomTrim = fallbackBottomTrim
      )
    }

    // Post-shift sticky header refinement:
    // With bestShiftScaled known, verify where the static sticky header truly ends.
    // A row y in Image 2 is part of the static header ONLY if it matches Image 1 at offset 0 (static)
    // significantly better than it matches Image 1 at the scrolled offset (y + bestShiftScaled).
    val maxRefineRows = minOf((h2 * 0.48f).roundToInt(), (h1 - bestShiftScaled - 4))
    var verifiedStaticHeader = staticTopRows

    for (y in staticTopRows until maxRefineRows) {
      val r1Static = gray1[y]
      val r2 = gray2[y]
      val r1Shifted = gray1[y + bestShiftScaled]

      var diff0 = 0
      var diffShift = 0
      var count = 0
      for (x in testStartX until testEndX step 2) {
        diff0 += abs(r1Static[x] - r2[x])
        diffShift += abs(r1Shifted[x] - r2[x])
        count++
      }
      val d0 = diff0.toDouble() / count
      val dShift = diffShift.toDouble() / count

      // A row belongs to the static header ONLY if it matches offset 0 closely
      // AND matches offset 0 noticeably better than the shifted scrolled content.
      if (d0 < 8.5 && dShift > d0 + 6.0) {
        verifiedStaticHeader = y + 1
      } else {
        // The moment moving scrolled content is reached, the static header has ended. Stop.
        break
      }
    }

    val confirmedTopInset2 = maxOf(topInset2, verifiedStaticHeader, statusBarTrimScaled)
    val confirmedTopInset1 = maxOf(bestHeader1, verifiedStaticHeader, statusBarTrimScaled)

    // Convert to original full-resolution space
    val fullShift = (bestShiftScaled * scaleRatioY1).roundToInt()
    val fullTopInset1 = (confirmedTopInset1 * scaleRatioY1).roundToInt()
    var fullBottomInset1 = (bottomInset1 * scaleRatioY1).roundToInt()
    var fullTopInset2 = (confirmedTopInset2 * scaleRatioY2).roundToInt()
    var fullBottomInset2 = (bottomInset2 * scaleRatioY2).roundToInt()

    if (settings.removeStatusBar) {
      fullTopInset2 = max(fullTopInset2, settings.statusBarHeightPx)
    }
    if (settings.removeNavBar) {
      fullBottomInset1 = max(fullBottomInset1, settings.navBarHeightPx)
      fullBottomInset2 = max(fullBottomInset2, settings.navBarHeightPx)
    }

    // Calculate exact seamless cut positions:
    // The scrolled content in Image 2 begins strictly at confirmedTopInset2.
    // In Image 1, scrolled content ends at (h1 - bottomInset1), which corresponds to
    // (h1 - bottomInset1 - bestShiftScaled) in Image 2.
    // Any seam row y2Cut MUST be at or below confirmedTopInset2 to guarantee that:
    // 1) The sticky header is NOT duplicated from Image 2.
    // 2) Content in Image 1 that scrolled under the header (between confirmedTopInset1 and
    //    confirmedTopInset2 + bestShiftScaled) is 100% PRESERVED without being cut off!
    val scaledMinY2 = confirmedTopInset2 + 1
    val maxSafeY2 = minOf(h2 - bottomInset2 - 1, h1 - bottomInset1 - bestShiftScaled - 1)
    val overlapScaled = (maxSafeY2 - scaledMinY2).coerceAtLeast(1)
    val scaledMaxY2 = (scaledMinY2 + (overlapScaled * 0.40f).roundToInt()).coerceIn(scaledMinY2, maxSafeY2)

    val bestScaledY2Seam = findOptimalSeamRow(
      gray1 = gray1,
      gray2 = gray2,
      shift = bestShiftScaled,
      minY2 = scaledMinY2,
      maxY2 = scaledMaxY2,
      width = matchWidth
    )

    var y2Cut = (bestScaledY2Seam * scaleRatioY2).roundToInt()
    var y1Cut = y2Cut + fullShift

    // Ensure y2Cut is at or below the header in image 2
    y2Cut = y2Cut.coerceIn(fullTopInset2, origHeight2 - fullBottomInset2 - 1)
    y1Cut = y2Cut + fullShift

    // Ensure y1Cut doesn't cut into bottom nav bar or beyond image 1
    y1Cut = y1Cut.coerceIn(fullTopInset1 + 1, origHeight1 - fullBottomInset1)
    y2Cut = (y1Cut - fullShift).coerceIn(fullTopInset2, origHeight2 - 1)

    val topTrim = fullTopInset2
    val autoOverlap = (y2Cut - topTrim).coerceAtLeast(0)
    val bottomTrim = (origHeight1 - y1Cut).coerceAtLeast(fullBottomInset1)

    val confidence = when {
      bestAvgDiff < 7.0 -> 0.98f
      bestAvgDiff < 14.0 -> 0.92f
      bestAvgDiff < 24.0 -> 0.80f
      bestAvgDiff < 36.0 -> 0.60f
      else -> 0.35f
    }

    return SeamConfig(
      autoOverlap = autoOverlap,
      confidence = confidence,
      isAutoDetected = true,
      topTrim = topTrim,
      bottomTrim = bottomTrim
    )
  }

  /**
   * Searches for the optimal seam cut row within the safe overlap window that
   * minimizes horizontal pixel difference and avoids cutting across text glyphs or
   * blurred / translucent header boundaries.
   */
  private fun findOptimalSeamRow(
    gray1: Array<IntArray>,
    gray2: Array<IntArray>,
    shift: Int,
    minY2: Int,
    maxY2: Int,
    width: Int
  ): Int {
    val h1 = gray1.size
    val h2 = gray2.size
    val startY = minY2.coerceIn(0, h2 - 1)
    val endY = maxY2.coerceIn(startY, h2 - 1)
    if (startY >= endY) return startY

    var bestY = startY
    var bestRowDiff = Double.MAX_VALUE
    val sampleStep = 4
    val startX = (width * 0.04f).roundToInt()
    val endX = (width * 0.93f).roundToInt()

    for (y2 in startY..endY) {
      val y1 = y2 + shift
      if (y1 !in 0 until h1) continue

      val r1 = gray1[y1]
      val r2 = gray2[y2]
      var diffSum = 0
      var edgeSum = 0
      var samples = 0

      for (x in startX until endX step sampleStep) {
        val v1 = r1[x]
        val v2 = r2[x]
        diffSum += abs(v1 - v2)

        val prevY = (y2 - 1).coerceAtLeast(0)
        val nextY = (y2 + 1).coerceAtMost(h2 - 1)
        edgeSum += abs(gray2[nextY][x] - gray2[prevY][x])
        samples++
      }

      if (samples > 0) {
        val avgDiff = diffSum.toDouble() / samples
        val avgEdge = edgeSum.toDouble() / samples
        val score = avgDiff * 1.5 + avgEdge * 0.5
        if (score < bestRowDiff) {
          bestRowDiff = score
          bestY = y2
        }
      }
    }

    return bestY
  }

  /**
   * Detects contiguous static header rows (e.g. status bar + app bar)
   * that remain identical at the top of both screenshots.
   * Tolerates slight status bar clock / battery icon differences.
   */
  private fun detectStaticTopRows(
    gray1: Array<IntArray>,
    gray2: Array<IntArray>,
    width: Int,
    maxRows: Int
  ): Int {
    val h1 = gray1.size
    val h2 = gray2.size
    val maxCheck = minOf(maxRows, (h1 * 0.48f).roundToInt(), (h2 * 0.48f).roundToInt())
    if (maxCheck <= 0) return 0

    val rowDiffs = DoubleArray(maxCheck)
    val startX = (width * 0.04f).roundToInt()
    val endX = (width * 0.93f).roundToInt()
    val count = (endX - startX) / 2
    if (count <= 0) return 0

    for (y in 0 until maxCheck) {
      val r1 = gray1[y]
      val r2 = gray2[y]
      var diffSum = 0
      for (x in startX until endX step 2) {
        diffSum += abs(r1[x] - r2[x])
      }
      rowDiffs[y] = diffSum.toDouble() / count
    }

    var staticBoundary = 0
    var nonStaticCountInStatusBar = 0

    for (y in 0 until maxCheck) {
      val diff = rowDiffs[y]
      if (diff < 9.0) {
        staticBoundary = y + 1
      } else {
        // In status bar region (top 15 rows), tolerate small 1-2 row blips (clock / battery / notification)
        if (y < 15 && nonStaticCountInStatusBar < 2 && diff < 18.0) {
          nonStaticCountInStatusBar++
          staticBoundary = y + 1
          continue
        }
        // As soon as a non-static row is encountered outside the status bar,
        // we have reached scrolled content! Stop immediately!
        break
      }
    }

    if (staticBoundary >= 4) {
      return staticBoundary
    }
    return 0
  }

  /**
   * Highly robust detection of static bottom navigation bars (e.g. YouTube bottom 5-tab bar,
   * Chrome bottom toolbar, 3-button system nav bar, social media tab bars).
   *
   * Noise-tolerant against transparent gesture navigation handles and compression artifacts.
   */
  private fun detectStaticBottomRows(
    gray1: Array<IntArray>,
    gray2: Array<IntArray>,
    width: Int,
    maxRows: Int
  ): Int {
    val h1 = gray1.size
    val h2 = gray2.size
    val maxCheck = minOf(maxRows, (h1 * 0.35f).roundToInt(), (h2 * 0.35f).roundToInt())
    if (maxCheck <= 0) return 0

    val rowDiffs = DoubleArray(maxCheck)
    val startX = (width * 0.04f).roundToInt()
    val endX = (width * 0.93f).roundToInt()
    val count = (endX - startX) / 2
    if (count <= 0) return 0

    for (i in 0 until maxCheck) {
      val y1 = h1 - 1 - i
      val y2 = h2 - 1 - i
      if (y1 < 0 || y2 < 0) break

      val r1 = gray1[y1]
      val r2 = gray2[y2]
      var diffSum = 0
      for (x in startX until endX step 2) {
        diffSum += abs(r1[x] - r2[x])
      }
      rowDiffs[i] = diffSum.toDouble() / count
    }

    // Step 1: Search for contiguous static blocks (tab bar icons + background)
    var staticBoundary = 0

    for (i in 0 until maxCheck) {
      val diff = rowDiffs[i]
      if (diff < 9.5) {
        staticBoundary = i + 1
      } else {
        // Tolerates faint transparent gesture bar at the very bottom
        if (i < 4 && diff < 16.0) {
          staticBoundary = i + 1
          continue
        }
        break
      }
    }

    // Step 2: Also detect horizontal hairline divider line at the top of the nav bar
    // In YouTube and Android Material Design, there is often a sharp contrast divider at the top of the nav bar.
    if (staticBoundary in 6 until maxCheck - 2) {
      // Check 4 rows above and below staticBoundary for a sharp difference jump into scroll content
      for (testIdx in (staticBoundary - 2).coerceAtLeast(4)..(staticBoundary + 4).coerceAtMost(maxCheck - 1)) {
        if (testIdx > 0 && rowDiffs[testIdx] > 16.0 && rowDiffs[testIdx - 1] < 9.0) {
          staticBoundary = testIdx
          break
        }
      }
    }

    if (staticBoundary >= 6) {
      return staticBoundary
    }

    // Step 3: Check standard YouTube / Tab bar height range if static region was slightly noisy at bottom
    // YouTube bottom bar is typically 48-60dp (~48-60px in 360-width space)
    val typicalNavMin = (h1 * 0.055f).roundToInt()
    val typicalNavMax = (h1 * 0.125f).roundToInt().coerceAtMost(maxCheck - 1)
    for (cand in typicalNavMax downTo typicalNavMin) {
      var lowDiffCount = 0
      for (r in 0 until cand) {
        if (rowDiffs[r] < 10.0) lowDiffCount++
      }
      if (lowDiffCount >= (cand * 0.75f).toInt() && rowDiffs[cand] > 15.0) {
        return cand
      }
    }

    return 0
  }

  /**
   * Detects transparent navigation bar overlay (gesture navigation handle / pill)
   * in modern Android edge-to-edge screenshots.
   */
  private fun detectTransparentNavBarPill(
    gray: Array<IntArray>,
    width: Int,
    height: Int
  ): Int {
    val checkRangeStart = (height * 0.92f).toInt().coerceAtLeast(0)
    val checkRangeEnd = (height * 0.985f).toInt().coerceAtMost(height - 1)
    if (checkRangeStart >= checkRangeEnd) return 0

    val pillMinLen = (width * 0.14f).toInt()
    val pillMaxLen = (width * 0.45f).toInt()

    for (y in checkRangeStart..checkRangeEnd) {
      val row = gray[y]
      var inPill = false
      var pillStart = 0
      var pillEnd = 0
      var pillLumSum = 0

      for (x in (width * 0.20f).toInt()..(width * 0.80f).toInt()) {
        val lum = row[x]
        val isPillCandidate = lum >= 165 || lum <= 85
        if (isPillCandidate) {
          if (!inPill) {
            inPill = true
            pillStart = x
            pillLumSum = lum
          } else {
            pillLumSum += lum
          }
          pillEnd = x
        } else {
          if (inPill) {
            val len = pillEnd - pillStart + 1
            if (len in pillMinLen..pillMaxLen) {
              val midX = (pillStart + pillEnd) / 2
              if (abs(midX - width / 2) < width * 0.08f) {
                val prevY = (y - 4).coerceAtLeast(0)
                val nextY = (y + 4).coerceAtMost(height - 1)
                val avgPillLum = pillLumSum.toDouble() / len
                var prevSum = 0
                var nextSum = 0
                for (px in pillStart..pillEnd) {
                  prevSum += gray[prevY][px]
                  nextSum += gray[nextY][px]
                }
                val prevLum = prevSum.toDouble() / len
                val nextLum = nextSum.toDouble() / len

                if (abs(avgPillLum - prevLum) > 16.0 || abs(avgPillLum - nextLum) > 16.0) {
                  return (height - y + 6).coerceAtLeast(6)
                }
              }
            }
            inPill = false
          }
        }
      }
    }

    // Default gesture nav bar inset on tall modern smartphones (aspect ratio >= 1.85)
    if (height.toFloat() / width.toFloat() >= 1.85f) {
      return (height * 0.024f).roundToInt().coerceIn(6, 18)
    }

    return 0
  }

  private fun extractGrayscaleMatrix(bitmap: Bitmap): Array<IntArray> {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val matrix = Array(height) { IntArray(width) }
    for (y in 0 until height) {
      val rowOffset = y * width
      val row = matrix[y]
      for (x in 0 until width) {
        val c = pixels[rowOffset + x]
        val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        row[x] = lum
      }
    }
    return matrix
  }

  /**
   * Stitches multiple images in sequence into a single seamless output file.
   * Completely eliminates leftover bottom navigation bars and duplicate headers.
   */
  suspend fun stitchImages(
    context: Context,
    images: List<ImageItem>,
    seams: List<SeamConfig>,
    settings: StitchGlobalSettings,
    removeOverlap: Boolean = true,
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

      if (settings.orientation == StitchOrientation.HORIZONTAL) {
        val targetHeight = bitmaps.maxOf { it.height }
        var totalWidth = 0
        var totalOverlapRemoved = 0
        val drawOperations = mutableListOf<DrawOp>()

        for (i in bitmaps.indices) {
          val bmp = bitmaps[i]
          val cropLeft: Int
          val cropRight: Int
          val sliceWidth: Int
          val renderedWidth: Int

          if (!removeOverlap) {
            cropLeft = 0
            cropRight = 0
            sliceWidth = bmp.width
            renderedWidth = if (bmp.height > 0 && bmp.height != targetHeight) {
              (sliceWidth.toFloat() * targetHeight / bmp.height).roundToInt()
            } else {
              sliceWidth
            }
          } else {
            val seamBefore = if (i > 0) seams.getOrNull(i - 1) ?: SeamConfig() else null
            val seamAfter = if (i < bitmaps.size - 1) seams.getOrNull(i) ?: SeamConfig() else null

            cropLeft = if (i == 0) {
              0
            } else {
              val overlap = seamBefore?.totalOverlap ?: 0
              val leftTrim = seamBefore?.leftTrim ?: 0
              (overlap + leftTrim).coerceIn(0, bmp.width - 1)
            }

            cropRight = if (i == bitmaps.size - 1) {
              0
            } else {
              val rightTrim = seamAfter?.rightTrim ?: 0
              rightTrim.coerceIn(0, bmp.width - cropLeft - 1)
            }

            sliceWidth = (bmp.width - cropLeft - cropRight).coerceAtLeast(1)
            renderedWidth = if (bmp.height > 0 && bmp.height != targetHeight) {
              (sliceWidth.toFloat() * targetHeight / bmp.height).roundToInt()
            } else {
              sliceWidth
            }

            if (seamBefore != null) {
              totalOverlapRemoved += seamBefore.totalOverlap
            }
          }

          val srcRect = Rect(cropLeft, 0, cropLeft + sliceWidth, bmp.height)
          val dstRect = Rect(totalWidth, 0, totalWidth + renderedWidth, targetHeight)

          drawOperations.add(DrawOp(bitmap = bmp, srcRect = srcRect, dstRect = dstRect))
          totalWidth += renderedWidth
        }

        onProgress(
          0.6f,
          context.getString(R.string.progress_composing_canvas, totalWidth, targetHeight)
        )

        val maxAllowedWidth = 32768
        val finalOutputBitmap: Bitmap = if (totalWidth > maxAllowedWidth) {
          val scale = maxAllowedWidth.toFloat() / totalWidth
          val scaledWidth = maxAllowedWidth
          val scaledHeight = (targetHeight * scale).roundToInt()
          Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        } else {
          Bitmap.createBitmap(totalWidth, targetHeight, Bitmap.Config.ARGB_8888)
        }

        val canvas = Canvas(finalOutputBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val isScaled = totalWidth > maxAllowedWidth
        val globalScale = if (isScaled) maxAllowedWidth.toFloat() / totalWidth else 1f

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

        if (removeOverlap) {
          val blendRadius = 3
          val blendHeight = finalOutputBitmap.height
          val blendPixels = IntArray(blendHeight)
          val srcPixels1 = IntArray(blendHeight)
          val srcPixels2 = IntArray(blendHeight)

          for (i in 0 until drawOperations.size - 1) {
            val op1 = drawOperations[i]
            val op2 = drawOperations[i + 1]
            val seamX = if (isScaled) (op1.dstRect.right * globalScale).roundToInt() else op1.dstRect.right

            for (offset in -blendRadius until blendRadius) {
              val currentX = seamX + offset
              if (currentX <= 0 || currentX >= finalOutputBitmap.width - 1) continue

              val t = (offset + blendRadius + 0.5f) / (2f * blendRadius)
              val xInBmp1 = op1.srcRect.right + offset
              val xInBmp2 = op2.srcRect.left + offset

              if (xInBmp1 in 0 until op1.bitmap.width && xInBmp2 in 0 until op2.bitmap.width) {
                val copyH = minOf(blendHeight, op1.bitmap.height, op2.bitmap.height)
                op1.bitmap.getPixels(srcPixels1, 0, 1, xInBmp1, 0, 1, copyH)
                op2.bitmap.getPixels(srcPixels2, 0, 1, xInBmp2, 0, 1, copyH)

                for (y in 0 until copyH) {
                  val c1 = srcPixels1[y]
                  val c2 = srcPixels2[y]

                  val a1 = Color.alpha(c1); val r1 = Color.red(c1); val g1 = Color.green(c1); val b1 = Color.blue(c1)
                  val a2 = Color.alpha(c2); val r2 = Color.red(c2); val g2 = Color.green(c2); val b2 = Color.blue(c2)

                  val a = (a1 * (1f - t) + a2 * t).roundToInt().coerceIn(0, 255)
                  val r = (r1 * (1f - t) + r2 * t).roundToInt().coerceIn(0, 255)
                  val g = (g1 * (1f - t) + g2 * t).roundToInt().coerceIn(0, 255)
                  val b = (b1 * (1f - t) + b2 * t).roundToInt().coerceIn(0, 255)

                  blendPixels[y] = Color.argb(a, r, g, b)
                }

                finalOutputBitmap.setPixels(blendPixels, 0, 1, currentX, 0, 1, copyH)
              }
            }
          }
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
          totalOverlapRemoved = if (removeOverlap) totalOverlapRemoved else 0,
          isDirectJoin = !removeOverlap,
          orientation = StitchOrientation.HORIZONTAL
        )

        onProgress(1.0f, context.getString(R.string.progress_complete))
        return@withContext Result.success(result)
      }

      val targetWidth = bitmaps.maxOf { it.width }

      // Compute slice rectangles and final canvas height
      var totalHeight = 0
      var totalOverlapRemoved = 0
      val drawOperations = mutableListOf<DrawOp>()

      for (i in bitmaps.indices) {
        val bmp = bitmaps[i]
        val cropTop: Int
        val cropBottom: Int
        val sliceHeight: Int
        val renderedHeight: Int

        if (!removeOverlap) {
          cropTop = 0
          cropBottom = 0
          sliceHeight = bmp.height
          renderedHeight = if (bmp.width > 0 && bmp.width != targetWidth) {
            (sliceHeight.toFloat() * targetWidth / bmp.width).roundToInt()
          } else {
            sliceHeight
          }
        } else {
          val seamBefore = if (i > 0) seams.getOrNull(i - 1) ?: SeamConfig() else null
          val seamAfter = if (i < bitmaps.size - 1) seams.getOrNull(i) ?: SeamConfig() else null

          // Crop top:
          // For image 0: remove status bar if enabled or if auto-detected.
          // For image i > 0: crop seamBefore.topTrim + seamBefore.totalOverlap!
          cropTop = if (i == 0) {
            if (settings.removeStatusBar) {
              settings.statusBarHeightPx.coerceIn(0, bmp.height / 6)
            } else 0
          } else {
            val overlap = seamBefore?.totalOverlap ?: 0
            val topTrim = seamBefore?.topTrim ?: 0
            (overlap + topTrim).coerceIn(0, bmp.height - 1)
          }

          // Crop bottom:
          // For intermediate images (i < bitmaps.size - 1): crop seamAfter.bottomTrim (strictly excludes bottom nav bar from intermediate seams).
          // For the last image (i == bitmaps.size - 1): keep the bottom navigation bar so the final image has the authentic app layout.
          // Only crop if the user explicitly enabled removeNavBar in settings.
          val detectedBottomNav = seams.map { it.bottomTrim }.filter { it > 0 }.maxOrNull() ?: 0
          cropBottom = if (i == bitmaps.size - 1) {
            if (settings.removeNavBar) {
              maxOf(settings.navBarHeightPx, detectedBottomNav).coerceAtMost(bmp.height / 4)
            } else {
              0
            }
          } else {
            val bottomTrim = seamAfter?.bottomTrim ?: 0
            val effectiveBottomTrim = if (bottomTrim > 0) bottomTrim else detectedBottomNav
            effectiveBottomTrim.coerceIn(0, bmp.height - cropTop - 1)
          }

          sliceHeight = (bmp.height - cropTop - cropBottom).coerceAtLeast(1)
          renderedHeight = if (bmp.width > 0 && bmp.width != targetWidth) {
            (sliceHeight.toFloat() * targetWidth / bmp.width).roundToInt()
          } else {
            sliceHeight
          }

          if (seamBefore != null) {
            totalOverlapRemoved += seamBefore.totalOverlap
          }
        }

        val srcRect = Rect(0, cropTop, bmp.width, cropTop + sliceHeight)
        val dstRect = Rect(0, totalHeight, targetWidth, totalHeight + renderedHeight)

        drawOperations.add(DrawOp(bitmap = bmp, srcRect = srcRect, dstRect = dstRect))
        totalHeight += renderedHeight
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

      // Micro-alpha blending across seams for 100% artifact-free seamless blending
      // (Only when removing overlaps, so images in direct-join mode preserve edge pixels completely)
      if (removeOverlap) {
        val blendRadius = 3
        val blendWidth = finalOutputBitmap.width
        val blendPixels = IntArray(blendWidth)
        val srcPixels1 = IntArray(blendWidth)
        val srcPixels2 = IntArray(blendWidth)

        for (i in 0 until drawOperations.size - 1) {
          val op1 = drawOperations[i]
          val op2 = drawOperations[i + 1]
          val seamY = if (isScaled) (op1.dstRect.bottom * globalScale).roundToInt() else op1.dstRect.bottom

          for (offset in -blendRadius until blendRadius) {
            val currentY = seamY + offset
            if (currentY <= 0 || currentY >= finalOutputBitmap.height - 1) continue

            val t = (offset + blendRadius + 0.5f) / (2f * blendRadius)
            val yInBmp1 = op1.srcRect.bottom + offset
            val yInBmp2 = op2.srcRect.top + offset

            if (yInBmp1 in 0 until op1.bitmap.height && yInBmp2 in 0 until op2.bitmap.height) {
              val copyW = minOf(blendWidth, op1.bitmap.width, op2.bitmap.width)
              op1.bitmap.getPixels(srcPixels1, 0, op1.bitmap.width, 0, yInBmp1, copyW, 1)
              op2.bitmap.getPixels(srcPixels2, 0, op2.bitmap.width, 0, yInBmp2, copyW, 1)

              for (x in 0 until copyW) {
                val c1 = srcPixels1[x]
                val c2 = srcPixels2[x]

                val a1 = Color.alpha(c1); val r1 = Color.red(c1); val g1 = Color.green(c1); val b1 = Color.blue(c1)
                val a2 = Color.alpha(c2); val r2 = Color.red(c2); val g2 = Color.green(c2); val b2 = Color.blue(c2)

                val a = (a1 * (1f - t) + a2 * t).roundToInt().coerceIn(0, 255)
                val r = (r1 * (1f - t) + r2 * t).roundToInt().coerceIn(0, 255)
                val g = (g1 * (1f - t) + g2 * t).roundToInt().coerceIn(0, 255)
                val b = (b1 * (1f - t) + b2 * t).roundToInt().coerceIn(0, 255)

                blendPixels[x] = Color.argb(a, r, g, b)
              }

              finalOutputBitmap.setPixels(blendPixels, 0, blendWidth, 0, currentY, copyW, 1)
            }
          }
        }
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
        totalOverlapRemoved = if (removeOverlap) totalOverlapRemoved else 0,
        isDirectJoin = !removeOverlap,
        orientation = StitchOrientation.VERTICAL
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
