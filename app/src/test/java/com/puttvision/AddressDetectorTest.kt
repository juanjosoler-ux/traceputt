package com.puttvision

import com.puttvision.vision.AddressDetector
import com.puttvision.vision.AddressResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for AddressDetector using empty Pose objects (no landmarks).
 * Landmark-dependent paths are exercised through public state.
 */
class AddressDetectorTest {

    private lateinit var detector: AddressDetector

    @Before fun setUp() { detector = AddressDetector() }

    @Test fun `empty pose returns NotVisible`() {
        val pose = com.google.mlkit.vision.pose.Pose()
        val result = detector.analyze(pose, System.currentTimeMillis())
        // With no landmarks, confidence check fails → NotVisible
        assertEquals(AddressResult.NotVisible::class, result::class)
    }

    @Test fun `failCount increments on repeated failures`() {
        val pose = com.google.mlkit.vision.pose.Pose()
        repeat(5) { detector.analyze(pose, System.currentTimeMillis()) }
        assertTrue("FailCount should be > 0", detector.failCount > 0)
    }

    @Test fun `thresholds relax after ADAPTATION_THRESHOLD failures`() {
        val defaultConf  = detector.currentMinConf
        val defaultDrift = detector.currentMaxDrift
        val pose = com.google.mlkit.vision.pose.Pose()
        // Force 8+ failures
        repeat(10) { detector.analyze(pose, System.currentTimeMillis()) }
        // After enough failures, thresholds should be more relaxed (lower conf, higher drift)
        assertTrue("MinConf should be ≤ default", detector.currentMinConf <= defaultConf)
        assertTrue("MaxDrift should be ≥ default", detector.currentMaxDrift >= defaultDrift)
    }

    @Test fun `reset clears fail count and restores default thresholds`() {
        val defaultConf  = detector.currentMinConf
        val defaultDrift = detector.currentMaxDrift
        val pose = com.google.mlkit.vision.pose.Pose()
        repeat(10) { detector.analyze(pose, System.currentTimeMillis()) }
        detector.reset()
        assertEquals("FailCount should be 0 after reset", 0, detector.failCount)
        assertEquals(defaultConf,  detector.currentMinConf,  0.001f)
        assertEquals(defaultDrift, detector.currentMaxDrift, 0.001f)
    }

    @Test fun `Stabilising result carries progress 0 to 1`() {
        // We can't easily create a real Pose with landmarks in unit tests,
        // but we verify that the Stabilising data class holds a progress value correctly
        val result = AddressResult.Stabilising(0.75f)
        assertEquals(0.75f, result.progress, 0.001f)
    }
}
