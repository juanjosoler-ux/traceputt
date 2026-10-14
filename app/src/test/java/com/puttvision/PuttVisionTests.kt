package com.puttvision

import com.puttvision.audio.AdaptiveAudioThreshold
import com.puttvision.vision.KalmanFilter1D
import com.puttvision.vision.TempoAnalyzer
import com.puttvision.vision.TempoQuality
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// ─────────────────────────────────────────────────────────────────────────────
// TempoAnalyzer
// ─────────────────────────────────────────────────────────────────────────────

class TempoAnalyzerTest {

    private lateinit var analyzer: TempoAnalyzer

    @Before fun setUp() { analyzer = TempoAnalyzer() }

    @Test fun `ideal 2to1 tempo ratio is classified as GOOD`() {
        analyzer.onStrokeStart(0)
        analyzer.onImpact(1000)      // backswing 1000ms
        analyzer.onFollowEnd(1500)   // follow    500ms  → ratio 2.0
        val result = analyzer.finalize()
        assertNotNull(result)
        assertEquals(2.0f, result!!.ratio, 0.05f)
        assertEquals(TempoQuality.GOOD, result.quality)
    }

    @Test fun `fast backswing ratio below 1_4 is classified TOO_FAST`() {
        analyzer.onStrokeStart(0)
        analyzer.onImpact(500)       // backswing 500ms
        analyzer.onFollowEnd(1000)   // follow    500ms  → ratio 1.0
        val result = analyzer.finalize()
        assertEquals(TempoQuality.TOO_FAST_BACKSWING, result!!.quality)
    }

    @Test fun `slow tempo ratio above 3_2 is classified TOO_SLOW`() {
        analyzer.onStrokeStart(0)
        analyzer.onImpact(2000)
        analyzer.onFollowEnd(2500)   // ratio 4.0
        val result = analyzer.finalize()
        assertEquals(TempoQuality.TOO_SLOW_BACKSWING, result!!.quality)
    }

    @Test fun `finalize without impact returns null`() {
        analyzer.onStrokeStart(0)
        assertNull(analyzer.finalize())
    }

    @Test fun `finalize without stroke start returns null`() {
        analyzer.onImpact(500)
        assertNull(analyzer.finalize())
    }

    @Test fun `reset allows reuse`() {
        analyzer.onStrokeStart(0); analyzer.onImpact(1000); analyzer.onFollowEnd(1500)
        analyzer.finalize()
        // Second putt
        analyzer.onStrokeStart(2000); analyzer.onImpact(3000); analyzer.onFollowEnd(3500)
        val result = analyzer.finalize()
        assertNotNull(result)
        assertEquals(2.0f, result!!.ratio, 0.05f)
    }

    @Test fun `backswingMs and followMs are stored correctly`() {
        analyzer.onStrokeStart(0)
        analyzer.onImpact(800)
        analyzer.onFollowEnd(1200)
        val result = analyzer.finalize()!!
        assertEquals(800L, result.backswingMs)
        assertEquals(400L, result.followMs)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// KalmanFilter1D
// ─────────────────────────────────────────────────────────────────────────────

class KalmanFilter1DTest {

    @Test fun `first update returns the measurement directly`() {
        val kf = KalmanFilter1D()
        assertEquals(5.0f, kf.update(5.0f), 0.001f)
    }

    @Test fun `filter smooths noisy signal toward true value`() {
        val kf = KalmanFilter1D(processNoise = 1e-4, measurementNoise = 1.0)
        val trueVal = 100f
        val noise   = 5f
        var output  = 0f
        // Feed 50 noisy measurements around 100
        repeat(50) { i ->
            val noisy = trueVal + (if (i % 2 == 0) noise else -noise)
            output = kf.update(noisy)
        }
        // After 50 steps the filter should be within 1 unit of true value
        assertEquals(trueVal, output, 1.0f)
    }

    @Test fun `reset clears state for next sequence`() {
        val kf = KalmanFilter1D()
        kf.update(100f); kf.update(100f); kf.update(100f)
        kf.reset()
        // After reset, next reading should match the raw measurement
        assertEquals(0f, kf.update(0f), 0.001f)
    }

    @Test fun `filter output is always finite`() {
        val kf = KalmanFilter1D()
        repeat(1000) {
            val v = kf.update((it % 200 - 100).toFloat())
            assertTrue("Output must be finite", v.isFinite())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AdaptiveAudioThreshold
// ─────────────────────────────────────────────────────────────────────────────

@RunWith(RobolectricTestRunner::class)
class AdaptiveAudioThresholdTest {

    private lateinit var adaptive: AdaptiveAudioThreshold

    @Before fun setUp() { adaptive = AdaptiveAudioThreshold() }

    @Test fun `not calibrated before calibration window`() {
        adaptive.startCalibration()
        assertFalse(adaptive.isCalibrated)
        assertEquals(AdaptiveAudioThreshold.DEFAULT_THRESHOLD, adaptive.computedThreshold, 1f)
    }

    @Test fun `calibrated after 200 samples`() {
        adaptive.startCalibration()
        repeat(200) { adaptive.onRmsSample(500f) }
        assertTrue(adaptive.isCalibrated)
    }

    @Test fun `threshold is above mean plus several sigma`() {
        adaptive.startCalibration()
        // Feed noisy ambient: mean ~500, stddev ~50
        var rng = 42L
        repeat(200) {
            rng = rng * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
            val noise = (rng and 0xFF).toFloat() - 128f  // -128..127
            adaptive.onRmsSample(500f + noise)
        }
        val threshold = adaptive.computedThreshold
        // Threshold must be noticeably above 500 (at least 3σ above mean)
        assertTrue("Threshold $threshold should be > 600", threshold > 600f)
    }

    @Test fun `threshold is clamped to 10000 for very loud environments`() {
        adaptive.startCalibration()
        repeat(200) { adaptive.onRmsSample(9_000f) }
        val threshold = adaptive.computedThreshold
        assertTrue("Threshold should be clamped to ≤ 10000", threshold <= 10_000f)
    }

    @Test fun `threshold is clamped to 800 minimum for quiet environments`() {
        adaptive.startCalibration()
        repeat(200) { adaptive.onRmsSample(10f) }   // near-silence
        val threshold = adaptive.computedThreshold
        assertTrue("Threshold should be at least 800", threshold >= 800f)
    }

    @Test fun `reset clears calibration`() {
        adaptive.startCalibration()
        repeat(200) { adaptive.onRmsSample(500f) }
        assertTrue(adaptive.isCalibrated)
        adaptive.reset()
        assertFalse(adaptive.isCalibrated)
    }
}
