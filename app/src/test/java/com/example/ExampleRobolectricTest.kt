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
}
