package com.puttvision

import android.graphics.Bitmap
import com.puttvision.camera.CircularFrameBuffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CircularFrameBufferTest {

    private fun mockBitmap(): Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    @Test fun `empty buffer returns empty list`() {
        val buf = CircularFrameBuffer(10)
        assertTrue(buf.toChronologicalList().isEmpty())
        assertEquals(0, buf.frameCount)
    }

    @Test fun `frames are stored in insertion order`() {
        val buf = CircularFrameBuffer(5)
        for (i in 0..2) buf.push(mockBitmap(), i.toLong())
        val list = buf.toChronologicalList()
        assertEquals(3, list.size)
        assertEquals(0L, list[0].timestampMs)
        assertEquals(2L, list[2].timestampMs)
    }

    @Test fun `oldest frame is evicted when buffer is full`() {
        val buf = CircularFrameBuffer(3)
        for (i in 0..4) buf.push(mockBitmap(), i.toLong())
        val list = buf.toChronologicalList()
        assertEquals(3, list.size)
        // Should contain frames 2, 3, 4
        assertEquals(2L, list[0].timestampMs)
        assertEquals(4L, list[2].timestampMs)
    }

    @Test fun `extractClipAroundImpact returns correct window`() {
        val buf = CircularFrameBuffer(20)
        for (i in 0..19) buf.push(mockBitmap(), (i * 100).toLong())  // 0..1900ms
        val impactMs = 1000L
        val clip = buf.extractClipAroundImpact(impactMs, windowMs = 500L, postWindowMs = 300L)
        // Should include 500ms before → frame at 500ms  AND  300ms after → frame at 1300ms
        assertTrue(clip.isNotEmpty())
        assertTrue(clip.all { it.timestampMs in 500L..1300L })
    }

    @Test fun `last(n) returns n most recent frames`() {
        val buf = CircularFrameBuffer(10)
        for (i in 0..9) buf.push(mockBitmap(), i.toLong())
        val last3 = buf.last(3)
        assertEquals(3, last3.size)
        assertEquals(7L, last3[0].timestampMs)
        assertEquals(9L, last3[2].timestampMs)
    }

    @Test fun `release recycles all bitmaps`() {
        val buf = CircularFrameBuffer(5)
        val bitmaps = (0..4).map { mockBitmap().also { bmp -> buf.push(bmp, it.toLong()) } }
        buf.release()
        // After release, buffer should be empty
        assertEquals(0, buf.frameCount)
        assertTrue(buf.toChronologicalList().isEmpty())
    }
}
