package com.puttvision.vision

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.pose.Pose
import com.puttvision.camera.CircularFrameBuffer
import com.puttvision.core.domain.model.PuttMetrics
import com.puttvision.core.domain.model.PerspectiveType
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PuttAnalysisEngine"

/**
 * Central orchestrator for a single putt analysis cycle.
 *
 *   onSessionStart()
 *   onAddressConfirmed()
 *   onStrokeStart()
 *   onFrame() × N          ← fills CircularFrameBuffer
 *   onImpact()
 *   onFollowEnd()
 *   analyze() → PuttMetrics
 *
 * Detectors:
 *  - HeadStabilityTracker   nose displacement → score 0–100
 *  - TempoAnalyzer          timestamps → ratio + quality
 *  - ColorMarkerDetector    HSV blobs → face angle (FRONT/BEHIND)
 *  - PutterPathTracker      OLS + Kalman → path angle (BEHIND)
 *  - BallLaunchDetector     LK optical flow → launch direction (120fps)
 */
@Singleton
class PuttAnalysisEngine @Inject constructor(
    private val headTracker        : HeadStabilityTracker,
    private val tempoAnalyzer      : TempoAnalyzer,
    private val colorMarkerDetector: ColorMarkerDetector,
    private val pathTracker        : PutterPathTracker,
    private val ballDetector       : BallLaunchDetector
) {
    private var perspective  : PerspectiveType = PerspectiveType.BEHIND
    private var imageWidth   : Int             = 1
    private var strokeStartMs: Long            = 0L
    private var impactMs     : Long            = 0L
    private var impactBallX  : Float           = 0.5f
    private var impactBallY  : Float           = 0.5f
    private var isArmed      : Boolean         = false

    // Frame buffer for ball-launch optical flow analysis
    private val frameBuffer  = CircularFrameBuffer(capacity = 30)  // last 1s at 30fps

    // Accumulate face-angle detections across frames
    private val faceAngleFrames = mutableListOf<Float>()

    // ─────────────────────────────────────────────────────────────────────────

    fun onSessionStart(perspective: PerspectiveType, imageWidth: Int) {
        this.perspective = perspective
        this.imageWidth  = imageWidth
        reset()
    }

    fun onAddressConfirmed() {
        isArmed = true
        faceAngleFrames.clear()
        frameBuffer.release()
        Log.d(TAG, "Armed [perspective=$perspective]")
    }

    fun onStrokeStart(timestampMs: Long) {
        if (!isArmed) return
        strokeStartMs = timestampMs
        headTracker.startTracking(imageWidth)
        tempoAnalyzer.onStrokeStart(timestampMs)
        pathTracker.reset()
        Log.d(TAG, "Stroke start @ $timestampMs")
    }

    /**
     * Feed one analysis frame.
     * @param bitmap  Analysis-resolution bitmap (nullable — only during BUFFERING/IMPACT)
     */
    fun onFrame(pose: Pose, bitmap: Bitmap?, timestampMs: Long) {
        if (!isArmed || strokeStartMs == 0L) return

        headTracker.update(pose)

        if (bitmap != null) {
            // Push into frame buffer for ball launch analysis
            frameBuffer.push(bitmap, timestampMs)

            // Color marker detection (FRONT and BEHIND perspectives)
            if (perspective == PerspectiveType.FRONT || perspective == PerspectiveType.BEHIND) {
                runCatching {
                    val result = colorMarkerDetector.detect(bitmap)
                    if (result is MarkerDetectionResult.Found) {
                        faceAngleFrames.add(result.faceAngleDeg)
                        pathTracker.onFrame(result.greenCenter, timestampMs)
                    }
                }.onFailure { Log.w(TAG, "Marker detection error: ${it.message}") }
            }
        }
    }

    fun onImpact(timestampMs: Long) {
        impactMs = timestampMs
        tempoAnalyzer.onImpact(timestampMs)
        pathTracker.onImpact(timestampMs)
        Log.d(TAG, "Impact @ $timestampMs  faceFrames=${faceAngleFrames.size}")
    }

    fun onFollowEnd(timestampMs: Long) {
        tempoAnalyzer.onFollowEnd(timestampMs)
    }

    /**
     * Set the estimated ball position at impact (normalised 0..1).
     * Used by BallLaunchDetector to define the optical-flow ROI.
     * Call after [onImpact] with the nose/wrist midpoint as an approximation
     * until a dedicated ball-detection step exists.
     */
    fun setImpactBallPosition(normX: Float, normY: Float) {
        impactBallX = normX
        impactBallY = normY
    }

    // ─────────────────────────────────────────────────────────────────────────

    fun analyze(puttId: Long = 0L): PuttMetrics {
        // A timer-triggered follow-end and a posture-triggered stroke start do NOT
        // identify backswing reversal. Do not publish a fabricated tempo ratio.
        val tempo: TempoResult? = null
        val head   = headTracker.finalize()
        // Marker pair currently produces an image-plane angle without calibration.
        // Keep collecting detections but do not report it as face angle at impact.
        val face: Float? = null
        // Image-plane path is not a target-referenced, perspective-corrected angle.
        val path: Float? = null

        // Ball launch — only attempt with adequate frame rate
        // Ball launch detector lacks verified ROI, fps and perspective calibration.
        val launch: Float? = null

        val conf = computeConf(tempo, head, face, path, launch)

        Log.d(TAG, "Metrics — tempo=${"%.2f".format(tempo?.ratio ?: 0f)} " +
                "head=${"%.0f".format(head.score ?: 0f)} " +
                "face=${"%.1f°".format(face ?: 0f)} " +
                "path=${"%.1f°".format(path ?: 0f)} " +
                "launch=${"%.1f°".format(launch ?: 0f)} conf=${"%.2f".format(conf)}")

        reset()
        return PuttMetrics(
            puttId        = puttId,
            backswingMs   = tempo?.backswingMs,
            followMs      = tempo?.followMs,
            tempoRatio    = tempo?.ratio,
            headMovePx    = head.maxPx.takeIf { it > 0f },
            headStability = head.score,
            faceAngleDeg  = face,
            pathAngleDeg  = path,
            launchDirDeg  = launch,
            overallConf   = conf
        )
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun analyzeBallLaunch(): Float? {
        if (impactMs <= 0) return null
        val frames = frameBuffer
            .extractClipAroundImpact(impactMs, windowMs = 200L, postWindowMs = 500L)
        if (frames.size < 5) return null

        // Find the impact frame index within the clip
        val impactIdx = frames.indexOfFirst { it.timestampMs >= impactMs }
            .takeIf { it >= 0 } ?: return null

        val bitmaps = frames.map { it.bitmap }
        return ballDetector.analyze(bitmaps, impactIdx, impactBallX, impactBallY)
    }

    private fun medianFaceAngle(): Float? {
        if (faceAngleFrames.size < 3) return null
        val sorted = faceAngleFrames.sorted()
        return sorted[sorted.size / 2]
    }

    private fun computeConf(
        tempo : TempoResult?,
        head  : HeadStabilityResult,
        face  : Float?,
        path  : Float?,
        launch: Float?
    ): Float {
        var sum = 0f; var cnt = 0f
        if (tempo  != null)     { sum += 1f;                   cnt++ }
        if (head.score != null) { sum += head.score!! / 100f;  cnt++ }
        if (face   != null)     { sum += 0.8f;                 cnt++ }
        if (path   != null)     { sum += 0.7f;                 cnt++ }
        if (launch != null)     { sum += 0.9f;                 cnt++ }
        return if (cnt > 0f) (sum / cnt).coerceIn(0f, 1f) else 0f
    }

    fun reset() {
        strokeStartMs   = 0L
        impactMs        = 0L
        isArmed         = false
        faceAngleFrames.clear()
        frameBuffer.release()
        headTracker.reset()
        tempoAnalyzer.reset()
        pathTracker.reset()
    }
}
