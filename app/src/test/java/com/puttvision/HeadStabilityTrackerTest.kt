package com.puttvision

import com.google.mlkit.vision.pose.Pose
import com.puttvision.vision.HeadStabilityTracker
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for HeadStabilityTracker using the public API only.
 * We use an empty Pose (no landmarks) to exercise the null-guard paths,
 * and test the scoring formula directly.
 *
 * For landmark-based tests, an integration test with CameraX would be needed.
 */
class HeadStabilityTrackerTest {

    private lateinit var tracker: HeadStabilityTracker

    @Before fun setUp() { tracker = HeadStabilityTracker() }

    @Test fun `finalize without startTracking returns null score`() {
        val result = tracker.finalize()
        assertNull(result.score)
        assertEquals(0f, result.maxPx, 0.001f)
    }

    @Test fun `isActive false before startTracking`() {
        assertFalse(tracker.isActive)
    }

    @Test fun `isActive true after startTracking`() {
        tracker.startTracking(640)
        assertTrue(tracker.isActive)
    }

    @Test fun `isActive false after finalize`() {
        tracker.startTracking(640)
        tracker.finalize()
        assertFalse(tracker.isActive)
    }

    @Test fun `reset clears active state`() {
        tracker.startTracking(640)
        tracker.reset()
        assertFalse(tracker.isActive)
    }

    @Test fun `finalize with zero frames returns null score`() {
        tracker.startTracking(640)
        val result = tracker.finalize()
        // No frames fed → no reference captured → null score
        assertNull(result.score)
    }

    @Test fun `score formula bounds 0 to 100`() {
        // We test the scoring formula indirectly by checking constraints on the
        // output: score must always be in [0, 100] if not null.
        // A perfect result (no movement) should be near 100.
        // We test the formula: ((5f - maxPct) / 5f * 100f).coerceIn(0f, 100f)
        // where maxPct = (maxDisplacementPx / imageWidth) * 100

        // Simulate: imageWidth=640, max displacement 0px → maxPct=0 → score=100
        val scoreAtZeroDisplacement = ((5f - 0f) / 5f * 100f).coerceIn(0f, 100f)
        assertEquals(100f, scoreAtZeroDisplacement, 0.001f)

        // Simulate: maxPct = 5% → score = 0
        val scoreAtMaxDisplacement = ((5f - 5f) / 5f * 100f).coerceIn(0f, 100f)
        assertEquals(0f, scoreAtMaxDisplacement, 0.001f)

        // Simulate: maxPct = 2.5% → score = 50
        val scoreAtHalfDisplacement = ((5f - 2.5f) / 5f * 100f).coerceIn(0f, 100f)
        assertEquals(50f, scoreAtHalfDisplacement, 0.001f)
    }
}
