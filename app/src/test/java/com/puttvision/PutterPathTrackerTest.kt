package com.puttvision

import com.puttvision.vision.PutterPathTracker
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.opencv.core.Point

/**
 * Unit tests for PutterPathTracker.
 *
 * Note: OpenCV native library is not loaded in unit tests.
 * We test the tracker using the org.opencv.core.Point class which is pure JVM
 * (no native dependency for basic construction).
 */
class PutterPathTrackerTest {

    private lateinit var tracker: PutterPathTracker

    @Before fun setUp() { tracker = PutterPathTracker() }

    @Test fun `computePath returns null with no frames`() {
        assertNull(tracker.computePath())
    }

    @Test fun `computePath returns null with fewer than 4 frames`() {
        val ts = System.currentTimeMillis()
        tracker.onFrame(Point(10.0, 5.0), ts)
        tracker.onFrame(Point(20.0, 5.0), ts + 33)
        tracker.onFrame(Point(30.0, 5.0), ts + 66)
        assertNull(tracker.computePath())
    }

    @Test fun `perfectly horizontal path returns 0 degrees`() {
        val ts = System.currentTimeMillis()
        // 6 points on a flat horizontal line y=50
        for (i in 0..5) {
            tracker.onFrame(Point((i * 10).toDouble(), 50.0), ts + i * 33L)
        }
        tracker.onImpact(ts + 5 * 33)
        val angle = tracker.computePath()
        assertNotNull(angle)
        // Horizontal line → slope = 0 → angle = atan(0) = 0°
        assertEquals(0f, angle!!, 1.5f)   // 1.5° tolerance for Kalman smoothing
    }

    @Test fun `reset clears all frames`() {
        val ts = System.currentTimeMillis()
        for (i in 0..5) tracker.onFrame(Point(i * 10.0, 50.0), ts + i * 33L)
        tracker.reset()
        assertNull(tracker.computePath())
    }

    @Test fun `null frame is ignored`() {
        val ts = System.currentTimeMillis()
        tracker.onFrame(null, ts)
        assertNull(tracker.computePath())
    }

    @Test fun `path angle sign convention - descending slope gives positive angle (outside-in)`() {
        // In image coords, Y increases downward.
        // A stroke moving right-to-left with decreasing Y (going up in image)
        // represents outside-in for a right-handed player viewed from behind.
        val ts = System.currentTimeMillis()
        for (i in 0..5) {
            // x increases, y decreases (line going up-right in image)
            tracker.onFrame(Point((i * 10).toDouble(), (50 - i * 3).toDouble()), ts + i * 33L)
        }
        tracker.onImpact(ts + 5 * 33)
        val angle = tracker.computePath()
        assertNotNull(angle)
        // Negative slope → atan(negative) → negated → positive angle (outside-in)
        assertTrue("Expected positive angle for outside-in path, got $angle", angle!! > 0f)
    }
}
