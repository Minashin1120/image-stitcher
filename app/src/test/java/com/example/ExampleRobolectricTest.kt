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

    com.example.service.ScreenCaptureStateHolder.updateState {
      it.copy(isRunning = true, capturedCount = 3, intervalSeconds = 1.5f)
    }

    val updatedState = com.example.service.ScreenCaptureStateHolder.sessionState.value
    assertEquals(true, updatedState.isRunning)
    assertEquals(3, updatedState.capturedCount)
    assertEquals(1.5f, updatedState.intervalSeconds)

    com.example.service.ScreenCaptureStateHolder.updateState {
      it.copy(autoScrollEnabled = true, scrollSpeedRatio = 0.7f)
    }
    assertEquals(true, com.example.service.ScreenCaptureStateHolder.sessionState.value.autoScrollEnabled)
    assertEquals(0.7f, com.example.service.ScreenCaptureStateHolder.sessionState.value.scrollSpeedRatio)

    com.example.service.ScreenCaptureStateHolder.reset()
    assertEquals(false, com.example.service.ScreenCaptureStateHolder.sessionState.value.isRunning)
    assertEquals(false, com.example.service.ScreenCaptureStateHolder.sessionState.value.autoScrollEnabled)
  }
}

