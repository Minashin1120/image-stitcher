package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.engine.StitchEngine
import com.example.model.StitchGlobalSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Screenshot Stitcher", appName)
  }

  @Test
  fun `detectOverlap successfully matches scrolled content with static header`() = runBlocking {
    val width = 200
    val height = 400
    val headerHeight = 60
    val scrollShift = 100

    val bmp1 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val bmp2 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    val canvas1 = Canvas(bmp1)
    val canvas2 = Canvas(bmp2)
    val paint = Paint()

    // Draw static header on both images
    paint.color = Color.RED
    canvas1.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), paint)
    canvas2.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), paint)

    // Draw unique pattern content on continuous canvas
    for (pageRow in 0 until 1000) {
      val rowColor = Color.rgb((pageRow * 13) % 255, (pageRow * 29) % 255, (pageRow * 47) % 255)
      paint.color = rowColor

      // In Image 1: content starts at headerHeight
      val yInImage1 = headerHeight + pageRow
      if (yInImage1 < height) {
        canvas1.drawLine(0f, yInImage1.toFloat(), width.toFloat(), yInImage1.toFloat(), paint)
      }

      // In Image 2: page content shifted by scrollShift
      val yInImage2 = headerHeight + (pageRow - scrollShift)
      if (yInImage2 in headerHeight until height) {
        canvas2.drawLine(0f, yInImage2.toFloat(), width.toFloat(), yInImage2.toFloat(), paint)
      }
    }

    val seam = StitchEngine.detectOverlap(bmp1, bmp2, StitchGlobalSettings(autoDetectOverlap = true))
    assertTrue("Should detect overlap with confidence >= 0.7", seam.confidence >= 0.7f)
    assertTrue("autoOverlap should be greater than 0", seam.autoOverlap > 0)
  }

  @Test
  fun `detectOverlap successfully detects and crops static bottom navigation bar`() = runBlocking {
    val width = 360
    val height = 800
    val headerHeight = 80
    val footerHeight = 120
    val scrollShift = 200

    val bmp1 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val bmp2 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    val canvas1 = Canvas(bmp1)
    val canvas2 = Canvas(bmp2)
    val paint = Paint()

    // Static header on both
    paint.color = Color.RED
    canvas1.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), paint)
    canvas2.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), paint)

    // Static bottom nav bar (like YouTube bottom tabs) on both
    paint.color = Color.BLUE
    canvas1.drawRect(0f, (height - footerHeight).toFloat(), width.toFloat(), height.toFloat(), paint)
    canvas2.drawRect(0f, (height - footerHeight).toFloat(), width.toFloat(), height.toFloat(), paint)

    // Distinct content
    for (pageRow in 0 until 1500) {
      val rowColor = Color.rgb((pageRow * 17) % 255, (pageRow * 31) % 255, (pageRow * 53) % 255)
      paint.color = rowColor

      val y1 = headerHeight + pageRow
      if (y1 < height - footerHeight) {
        canvas1.drawLine(0f, y1.toFloat(), width.toFloat(), y1.toFloat(), paint)
      }

      val y2 = headerHeight + (pageRow - scrollShift)
      if (y2 in headerHeight until (height - footerHeight)) {
        canvas2.drawLine(0f, y2.toFloat(), width.toFloat(), y2.toFloat(), paint)
      }
    }

    val seam = StitchEngine.detectOverlap(bmp1, bmp2, StitchGlobalSettings(autoDetectOverlap = true))
    assertTrue("Should detect overlap with confidence >= 0.7", seam.confidence >= 0.7f)
    assertTrue("Bottom trim should be at least footer height", seam.bottomTrim >= footerHeight - 10)
  }

  @Test
  fun `ScreenCaptureStateHolder updates and notifies correctly`() {
    com.example.service.ScreenCaptureStateHolder.reset()
    val initialState = com.example.service.ScreenCaptureStateHolder.sessionState.value
    assertEquals(false, initialState.isRunning)
    assertEquals(0, initialState.capturedCount)
    assertEquals(0.5f, initialState.intervalSeconds)
    assertEquals(0.40f, initialState.scrollSpeedRatio)

    com.example.service.ScreenCaptureStateHolder.updateState {
      it.copy(isRunning = true, capturedCount = 3, intervalSeconds = 0.5f)
    }

    val updatedState = com.example.service.ScreenCaptureStateHolder.sessionState.value
    assertEquals(true, updatedState.isRunning)
    assertEquals(3, updatedState.capturedCount)
    assertEquals(0.5f, updatedState.intervalSeconds)

    com.example.service.ScreenCaptureStateHolder.updateState {
      it.copy(autoScrollEnabled = true, scrollSpeedRatio = 0.40f)
    }
    assertEquals(true, com.example.service.ScreenCaptureStateHolder.sessionState.value.autoScrollEnabled)
    assertEquals(0.40f, com.example.service.ScreenCaptureStateHolder.sessionState.value.scrollSpeedRatio)

    com.example.service.ScreenCaptureStateHolder.reset()
    assertEquals(false, com.example.service.ScreenCaptureStateHolder.sessionState.value.isRunning)
    assertEquals(false, com.example.service.ScreenCaptureStateHolder.sessionState.value.autoScrollEnabled)
  }

  @Test
  fun `StitchViewModel handles incoming shared URIs properly`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.viewmodel.StitchViewModel(application)

    val dummyUri = android.net.Uri.parse("file:///dummy/path/screenshot.png")
    viewModel.handleIncomingSharedUris(listOf(dummyUri))

    // Verify clear method works
    viewModel.clearIncomingSharedUris()
    assertEquals(null, viewModel.incomingSharedUris.value)
  }

  @Test
  fun `BatteryOptimizationUtil correctly reads and writes prompted state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    
    // Set to false and verify
    com.example.util.BatteryOptimizationUtil.setPrompted(context, false)
    assertEquals(false, com.example.util.BatteryOptimizationUtil.hasPrompted(context))

    // Set to true and verify
    com.example.util.BatteryOptimizationUtil.setPrompted(context, true)
    assertEquals(true, com.example.util.BatteryOptimizationUtil.hasPrompted(context))
  }

  @Test
  fun `ImageEditEngine applies moved and resized MosaicRect correctly`() = runBlocking {
    val width = 200
    val height = 200
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    
    // Fill with a sharp vertical gradient/alternating colors so mosaic pixelates it
    for (y in 0 until height) {
      for (x in 0 until width) {
        bmp.setPixel(x, y, if ((x + y) % 2 == 0) Color.BLACK else Color.WHITE)
      }
    }

    // Create an initial MosaicRect at (0.1, 0.1) to (0.4, 0.4)
    val initialMosaic = com.example.model.EditAction.MosaicRect(
      rectRelative = androidx.compose.ui.geometry.Rect(0.1f, 0.1f, 0.4f, 0.4f),
      pixelSizeRelative = 0.1f
    )

    // Simulate moving and resizing to (0.5, 0.5) to (0.9, 0.9)
    val movedResizedMosaic = initialMosaic.copy(
      rectRelative = androidx.compose.ui.geometry.Rect(0.5f, 0.5f, 0.9f, 0.9f)
    )

    val edited = com.example.engine.ImageEditEngine.applyEdits(
      sourceBitmap = bmp,
      actions = listOf(movedResizedMosaic),
      cropBounds = com.example.model.CropBounds()
    )

    // The old area (0.1 to 0.4) should remain intact (sharp alternating pixels)
    // Pixel (20, 20) was (20+20)%2 == 0 => Color.BLACK
    assertEquals(Color.BLACK, edited.getPixel(20, 20))

    // The new moved/resized area (0.5 to 0.9) -> e.g. pixel (120, 120) should now be pixelated (averaged grey)
    val pixelInMosaic = edited.getPixel(120, 120)
    // In ARGB, it should not be pure black or pure white because it was pixelated
    assertTrue(pixelInMosaic != Color.BLACK && pixelInMosaic != Color.WHITE)
  }

  @Test
  fun `stitchImages with removeOverlap false performs direct concatenation without removing overlap`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val width = 200
    val height1 = 300
    val height2 = 400

    val bmp1 = Bitmap.createBitmap(width, height1, Bitmap.Config.ARGB_8888)
    val bmp2 = Bitmap.createBitmap(width, height2, Bitmap.Config.ARGB_8888)

    val file1 = java.io.File(context.cacheDir, "test_stitch_1.png")
    val file2 = java.io.File(context.cacheDir, "test_stitch_2.png")
    java.io.FileOutputStream(file1).use { bmp1.compress(Bitmap.CompressFormat.PNG, 100, it) }
    java.io.FileOutputStream(file2).use { bmp2.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val item1 = com.example.model.ImageItem(
      uri = android.net.Uri.fromFile(file1),
      name = "img1.png",
      width = width,
      height = height1,
      fileSizeBytes = file1.length()
    )
    val item2 = com.example.model.ImageItem(
      uri = android.net.Uri.fromFile(file2),
      name = "img2.png",
      width = width,
      height = height2,
      fileSizeBytes = file2.length()
    )

    val result = StitchEngine.stitchImages(
      context = context,
      images = listOf(item1, item2),
      seams = emptyList(),
      settings = StitchGlobalSettings(removeStatusBar = true, removeNavBar = true),
      removeOverlap = false
    ) { _, _ -> }

    assertTrue("Stitch should succeed", result.isSuccess)
    val stitchResult = result.getOrThrow()
    assertEquals(true, stitchResult.isDirectJoin)
    assertEquals(0, stitchResult.totalOverlapRemoved)
    assertEquals(width, stitchResult.width)
    assertEquals(height1 + height2, stitchResult.height)
  }

  @Test
  fun `stitchImages with HORIZONTAL orientation stitches images side by side`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val width1 = 250
    val width2 = 350
    val height = 400

    val bmp1 = Bitmap.createBitmap(width1, height, Bitmap.Config.ARGB_8888)
    val bmp2 = Bitmap.createBitmap(width2, height, Bitmap.Config.ARGB_8888)

    val file1 = java.io.File(context.cacheDir, "test_h_stitch_1.png")
    val file2 = java.io.File(context.cacheDir, "test_h_stitch_2.png")
    java.io.FileOutputStream(file1).use { bmp1.compress(Bitmap.CompressFormat.PNG, 100, it) }
    java.io.FileOutputStream(file2).use { bmp2.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val item1 = com.example.model.ImageItem(
      uri = android.net.Uri.fromFile(file1),
      name = "h_img1.png",
      width = width1,
      height = height,
      fileSizeBytes = file1.length()
    )
    val item2 = com.example.model.ImageItem(
      uri = android.net.Uri.fromFile(file2),
      name = "h_img2.png",
      width = width2,
      height = height,
      fileSizeBytes = file2.length()
    )

    val result = StitchEngine.stitchImages(
      context = context,
      images = listOf(item1, item2),
      seams = emptyList(),
      settings = StitchGlobalSettings(orientation = com.example.model.StitchOrientation.HORIZONTAL),
      removeOverlap = false
    ) { _, _ -> }

    assertTrue("Horizontal stitch should succeed", result.isSuccess)
    val stitchResult = result.getOrThrow()
    assertEquals(com.example.model.StitchOrientation.HORIZONTAL, stitchResult.orientation)
    assertEquals(width1 + width2, stitchResult.width)
    assertEquals(height, stitchResult.height)
  }

  @Test
  fun `stitchImages preserves bottom navigation bar on final screenshot by default`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val width = 360
    val height = 600
    val navBarHeight = 80
    val scrollShift = 150

    val bmp1 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val bmp2 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    val canvas1 = Canvas(bmp1)
    val canvas2 = Canvas(bmp2)
    val paint = Paint()

    // Top status bar (Color.GREEN)
    paint.color = Color.GREEN
    canvas1.drawRect(0f, 0f, width.toFloat(), 40f, paint)
    canvas2.drawRect(0f, 0f, width.toFloat(), 40f, paint)

    // Bottom navigation bar (Color.BLUE) on both screenshots
    paint.color = Color.BLUE
    canvas1.drawRect(0f, (height - navBarHeight).toFloat(), width.toFloat(), height.toFloat(), paint)
    canvas2.drawRect(0f, (height - navBarHeight).toFloat(), width.toFloat(), height.toFloat(), paint)

    // Distinct scrollable content in the middle
    for (pageRow in 0 until 1200) {
      val rowColor = Color.rgb((pageRow * 19) % 250, (pageRow * 37) % 250, (pageRow * 61) % 250)
      paint.color = rowColor
      val y1 = 40 + pageRow
      if (y1 < height - navBarHeight) {
        canvas1.drawLine(0f, y1.toFloat(), width.toFloat(), y1.toFloat(), paint)
      }
      val y2 = 40 + (pageRow - scrollShift)
      if (y2 in 40 until (height - navBarHeight)) {
        canvas2.drawLine(0f, y2.toFloat(), width.toFloat(), y2.toFloat(), paint)
      }
    }

    val file1 = java.io.File(context.cacheDir, "test_navbar_1.png")
    val file2 = java.io.File(context.cacheDir, "test_navbar_2.png")
    java.io.FileOutputStream(file1).use { bmp1.compress(Bitmap.CompressFormat.PNG, 100, it) }
    java.io.FileOutputStream(file2).use { bmp2.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val item1 = com.example.model.ImageItem(
      uri = android.net.Uri.fromFile(file1),
      name = "navbar1.png",
      width = width,
      height = height,
      fileSizeBytes = file1.length()
    )
    val item2 = com.example.model.ImageItem(
      uri = android.net.Uri.fromFile(file2),
      name = "navbar2.png",
      width = width,
      height = height,
      fileSizeBytes = file2.length()
    )

    val seam = StitchEngine.detectOverlap(context, item1, item2, StitchGlobalSettings(autoDetectOverlap = true))
    assertTrue("Overlap detected", seam.confidence > 0.5f)

    // Default settings: removeNavBar is false (preserves bottom nav bar on final image)
    val resultKeep = StitchEngine.stitchImages(
      context = context,
      images = listOf(item1, item2),
      seams = listOf(seam),
      settings = StitchGlobalSettings(removeNavBar = false),
      removeOverlap = true
    ) { _, _ -> }

    // Explicitly trimming nav bar
    val resultTrim = StitchEngine.stitchImages(
      context = context,
      images = listOf(item1, item2),
      seams = listOf(seam),
      settings = StitchGlobalSettings(removeNavBar = true),
      removeOverlap = true
    ) { _, _ -> }

    assertTrue("Keep stitch succeeded", resultKeep.isSuccess)
    assertTrue("Trim stitch succeeded", resultTrim.isSuccess)

    val keepHeight = resultKeep.getOrThrow().height
    val trimHeight = resultTrim.getOrThrow().height

    // When removeNavBar is false (default), bottom navigation bar on last image is retained
    assertTrue("Kept nav bar height should be taller than trimmed nav bar height", keepHeight > trimHeight)
    val expectedTrim = maxOf(StitchGlobalSettings().navBarHeightPx, seam.bottomTrim).coerceAtMost(height / 4)
    assertEquals("Difference should equal the bottom navigation bar trim", expectedTrim, keepHeight - trimHeight)
  }

  @Test
  fun `stitchImages with sticky app header preserves all scrolled content without missing junction rows`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val width = 360
    val height = 700
    val headerHeight = 140
    val scrollShift = 180

    val bmp1 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val bmp2 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    val canvas1 = Canvas(bmp1)
    val canvas2 = Canvas(bmp2)
    val paint = Paint()

    // 1. Draw static header (status bar + app bar + tabs) on both images
    // Header pattern has distinctive bands so it's clearly a real app header
    paint.color = Color.rgb(30, 30, 30) // Dark status bar
    canvas1.drawRect(0f, 0f, width.toFloat(), 40f, paint)
    canvas2.drawRect(0f, 0f, width.toFloat(), 40f, paint)

    paint.color = Color.rgb(50, 50, 60) // App bar
    canvas1.drawRect(0f, 40f, width.toFloat(), 100f, paint)
    canvas2.drawRect(0f, 40f, width.toFloat(), 100f, paint)

    paint.color = Color.rgb(70, 70, 80) // Tab bar with divider line
    canvas1.drawRect(0f, 100f, width.toFloat(), headerHeight.toFloat(), paint)
    canvas2.drawRect(0f, 100f, width.toFloat(), headerHeight.toFloat(), paint)

    // 2. Draw continuous scrolled content where each row Y has a unique color encoding its global row index
    // pageRow 0 is the first row right below the header in image 1.
    for (pageRow in 0 until 1200) {
      val r = (pageRow % 250) + 5
      val g = ((pageRow * 3) % 250) + 5
      val b = ((pageRow * 7) % 250) + 5
      paint.color = Color.rgb(r, g, b)

      // In Image 1: content starts at headerHeight
      val y1 = headerHeight + pageRow
      if (y1 < height) {
        canvas1.drawRect(0f, y1.toFloat(), width.toFloat(), (y1 + 1f), paint)
      }

      // In Image 2: content shifted by scrollShift
      val y2 = headerHeight + (pageRow - scrollShift)
      if (y2 in headerHeight until height) {
        canvas2.drawRect(0f, y2.toFloat(), width.toFloat(), (y2 + 1f), paint)
      }
    }

    // Also draw a simulated vertical scrollbar on the right edge of image 2 (common in Android scroll views)
    paint.color = Color.rgb(180, 180, 180)
    canvas2.drawRect((width - 6).toFloat(), 200f, width.toFloat(), 450f, paint)

    val file1 = java.io.File(context.cacheDir, "test_sticky_1.png")
    val file2 = java.io.File(context.cacheDir, "test_sticky_2.png")
    java.io.FileOutputStream(file1).use { bmp1.compress(Bitmap.CompressFormat.PNG, 100, it) }
    java.io.FileOutputStream(file2).use { bmp2.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val item1 = com.example.model.ImageItem(
      uri = android.net.Uri.fromFile(file1),
      name = "sticky1.png",
      width = width,
      height = height,
      fileSizeBytes = file1.length(),
      thumbnail = bmp1
    )
    val item2 = com.example.model.ImageItem(
      uri = android.net.Uri.fromFile(file2),
      name = "sticky2.png",
      width = width,
      height = height,
      fileSizeBytes = file2.length(),
      thumbnail = bmp2
    )

    val seam = StitchEngine.detectOverlap(context, item1, item2, StitchGlobalSettings(autoDetectOverlap = true))
    assertTrue("High confidence overlap detection", seam.confidence >= 0.70f)
    // topTrim should have accurately identified the sticky header
    assertTrue("Top trim should be at least header height: ${seam.topTrim}", seam.topTrim >= headerHeight - 10)

    val stitchResult = StitchEngine.stitchImages(
      context = context,
      images = listOf(item1, item2),
      seams = listOf(seam),
      settings = StitchGlobalSettings(autoDetectOverlap = true, removeStatusBar = false),
      removeOverlap = true
    ) { _, _ -> }

    assertTrue("Stitch should succeed", stitchResult.isSuccess)
    val result = stitchResult.getOrThrow()

    // The stitched image must contain the header (140px) + all scrolled content from image 1 and image 2
    // Image 1 contains rows 0..560 of page content (height 700 - header 140)
    // Image 2 is shifted by 180px, containing rows 180..740 of page content
    // Total combined page height = header (140) + total page content (740) = 880px!
    val expectedHeight = height + scrollShift
    assertEquals("Stitched width must match", width, result.width)
    assertEquals("Stitched height must be exactly height + scrollShift with zero missing rows", expectedHeight, result.height)
    assertEquals("2 source images stitched", 2, result.sourceCount)
  }
}

