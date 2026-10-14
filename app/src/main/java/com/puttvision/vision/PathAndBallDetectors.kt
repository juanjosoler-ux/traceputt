package com.puttvision.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import org.opencv.core.Point
import javax.inject.Inject
import kotlin.math.*

/**
 * Tracks the putter head path during the stroke using the GREEN marker centroid.
 *
 * Pipeline:
 *  1. Kalman-smoothed centroids accumulated during BUFFERING.
 *  2. Pre-impact segment (last ~250ms) isolated on [onImpact].
 *  3. OLS linear regression → path angle in degrees.
 *     + = outside-in  |  − = inside-out
 *
 * STATUS: FUNCTIONAL — Kalman + OLS implemented.
 * TODO: Homography correction for non-perpendicular cameras.
 */
class PutterPathTracker @Inject constructor() {

    private data class MarkerPos(val x: Float, val y: Float, val ts: Long)

    private val positions = mutableListOf<MarkerPos>()
    private var impactTs  : Long = -1L
    private val kfX = KalmanFilter1D(processNoise = 1e-3, measurementNoise = 0.5)
    private val kfY = KalmanFilter1D(processNoise = 1e-3, measurementNoise = 0.5)

    fun onFrame(markerCenter: Point?, timestampMs: Long) {
        markerCenter ?: return
        val sx = kfX.update(markerCenter.x.toFloat())
        val sy = kfY.update(markerCenter.y.toFloat())
        positions.add(MarkerPos(sx, sy, timestampMs))
        if (positions.size > 60) positions.removeAt(0)
    }

    fun onImpact(timestampMs: Long) { impactTs = timestampMs }

    fun computePath(): Float? {
        val seg = if (impactTs > 0)
            positions.filter { it.ts <= impactTs && it.ts >= impactTs - 250 }
        else
            positions.takeLast(8)
        if (seg.size < 4) return null
        val n   = seg.size.toFloat()
        val sx  = seg.sumOf { it.x.toDouble() }.toFloat()
        val sy  = seg.sumOf { it.y.toDouble() }.toFloat()
        val sxx = seg.sumOf { (it.x * it.x).toDouble() }.toFloat()
        val sxy = seg.sumOf { (it.x * it.y).toDouble() }.toFloat()
        val den = n * sxx - sx * sx
        if (abs(den) < 1e-6f) return null
        val slope = (n * sxy - sx * sy) / den
        return (-atan(slope.toDouble()) * (180.0 / PI)).toFloat()
    }

    fun reset() {
        positions.clear(); impactTs = -1L; kfX.reset(); kfY.reset()
    }
}

// ─────────────────────────────────────────────────────────────────────────────

/**
 * Detects ball launch direction using Lucas-Kanade sparse optical flow (OpenCV).
 *
 * Algorithm:
 *  1. Define a Region of Interest (ROI) 10-40px in front of the putter face
 *     at the impact frame, based on the putter-face axis from [ColorMarkerDetector].
 *  2. Detect good features to track in the ROI (Shi-Tomasi corners — the ball edge).
 *  3. Track those features across 3-8 post-impact frames using LK optical flow.
 *  4. Compute the mean flow vector → angle relative to horizontal.
 *  5. Correct for known camera angle relative to target line (requires calibration).
 *
 * Requirements for reliable results:
 *  - ≥ 60fps (120fps ideal). At 30fps the ball has already moved >15cm by the
 *    second frame and corner tracking fails.
 *  - White ball on a surface with reasonable contrast (not pure white carpet).
 *  - Known target-line direction (set via [setTargetLineAngleDeg]).
 *
 * STATUS: FUNCTIONAL SKELETON — LK tracking implemented.
 *         Results are unreliable at standard 30fps.
 *         At 120fps (HighSpeedCameraSession) this should work well.
 *
 * TODO:
 *  - Validate with real 120fps footage on a real green.
 *  - Add ball-colour-based ROI refinement (Hough circle on white blob).
 *  - Persist targetLineAngleDeg as a session parameter set during setup.
 */
class BallLaunchDetector @Inject constructor() {

    companion object {
        /** Size of the ROI square around the ball at impact, in pixels */
        private const val ROI_SIZE_PX        = 60
        /** LK window size for tracking */
        private val LK_WIN_SIZE              = Size(21.0, 21.0)
        /** Max LK pyramid levels */
        private const val LK_MAX_LEVEL       = 3
        /** Minimum number of tracked points to trust the result */
        private const val MIN_TRACKED_POINTS = 4
    }

    /** Degrees of the target line relative to horizontal in the image.
     *  0° = horizontal right.  Set via calibration or UI.
     *  TODO: Surface this as a session parameter. */
    var targetLineAngleDeg: Float = 0f

    // Reusable OpenCV Mats
    private val grayPrev = Mat()
    private val grayCurr = Mat()
    private val p0       = MatOfPoint2f()
    private val p1       = MatOfPoint2f()
    private val status   = MatOfByte()
    private val err      = MatOfFloat()

    /**
     * Analyse post-impact frames to compute ball launch direction.
     *
     * @param frames      List of bitmaps starting at [impactIdx - 1]
     * @param impactIdx   Index in [frames] of the impact frame
     * @param impactBallX Normalised (0..1) X position of the ball at impact
     * @param impactBallY Normalised (0..1) Y position of the ball at impact
     *
     * @return Launch direction in degrees off target line.
     *         Positive = right of target, Negative = left.
     *         Null if tracking failed or insufficient data.
     */
    fun analyze(
        frames      : List<Bitmap>,
        impactIdx   : Int,
        impactBallX : Float,
        impactBallY : Float
    ): Float? {
        if (frames.size < impactIdx + 3) return null  // need ≥ 3 post-impact frames

        val impactFrame = frames.getOrNull(impactIdx) ?: return null
        val imgW = impactFrame.width.toFloat()
        val imgH = impactFrame.height.toFloat()

        // ── 1. Build ROI around expected ball position ────────────────────────
        val cx    = (impactBallX * imgW).toInt()
        val cy    = (impactBallY * imgH).toInt()
        val half  = ROI_SIZE_PX / 2
        val roiX  = (cx - half).coerceIn(0, impactFrame.width  - ROI_SIZE_PX)
        val roiY  = (cy - half).coerceIn(0, impactFrame.height - ROI_SIZE_PX)
        val roiRect = org.opencv.core.Rect(roiX, roiY, ROI_SIZE_PX, ROI_SIZE_PX)

        // ── 2. Convert impact frame to grayscale, find features ────────────────
        val impactMat = Mat()
        Utils.bitmapToMat(impactFrame, impactMat)
        Imgproc.cvtColor(impactMat, grayPrev, Imgproc.COLOR_RGBA2GRAY)
        impactMat.release()

        val roiGray   = Mat(grayPrev, roiRect)
        val corners   = MatOfPoint()
        Imgproc.goodFeaturesToTrack(roiGray, corners, 30, 0.3, 3.0)
        roiGray.release()

        if (corners.toArray().isEmpty()) return null

        // Shift corner coords from ROI space to full-frame space
        val pts = corners.toArray().map { pt ->
            org.opencv.core.Point(pt.x + roiX, pt.y + roiY)
        }.toTypedArray()
        p0.fromArray(*pts)

        // ── 3. Track features across post-impact frames ────────────────────────
        val flowVectors = mutableListOf<Pair<Float, Float>>()

        for (i in impactIdx + 1 until minOf(frames.size, impactIdx + 6)) {
            val frame = frames[i] ?: continue
            val frameMat = Mat()
            Utils.bitmapToMat(frame, frameMat)
            Imgproc.cvtColor(frameMat, grayCurr, Imgproc.COLOR_RGBA2GRAY)
            frameMat.release()

            Video.calcOpticalFlowPyrLK(grayPrev, grayCurr, p0, p1,
                status, err, LK_WIN_SIZE, LK_MAX_LEVEL)

            val p0arr     = p0.toArray()
            val p1arr     = p1.toArray()
            val statusArr = status.toArray()

            var goodCount = 0
            for (j in statusArr.indices) {
                if (statusArr[j].toInt() == 1) {
                    flowVectors.add(
                        Pair(
                            (p1arr[j].x - p0arr[j].x).toFloat(),
                            (p1arr[j].y - p0arr[j].y).toFloat()
                        )
                    )
                    goodCount++
                }
            }

            grayCurr.copyTo(grayPrev)
            // Keep only good points for next iteration
            if (goodCount < MIN_TRACKED_POINTS) break
        }

        if (flowVectors.size < MIN_TRACKED_POINTS) return null

        // ── 4. Compute mean flow vector ────────────────────────────────────────
        val meanDx = flowVectors.map { it.first  }.average().toFloat()
        val meanDy = flowVectors.map { it.second }.average().toFloat()

        // Angle of flow vector relative to horizontal (image coords)
        val flowAngleDeg = Math.toDegrees(atan2(meanDy.toDouble(), meanDx.toDouble())).toFloat()

        // ── 5. Subtract target line angle to get launch direction ──────────────
        val launchDeg = flowAngleDeg - targetLineAngleDeg

        // Normalise to -180..180
        return ((launchDeg + 180f) % 360f - 180f)
    }

    fun release() {
        grayPrev.release(); grayCurr.release()
        p0.release(); p1.release(); status.release(); err.release()
    }
}
