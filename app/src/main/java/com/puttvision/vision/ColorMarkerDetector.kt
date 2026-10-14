package com.puttvision.vision

import android.graphics.Bitmap
import com.puttvision.core.data.preferences.AppSettings
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import kotlin.math.atan2

/**
 * Detects fluorescent marker stickers on the putter face to compute:
 *  - Face angle at impact (degrees off square)
 *  - Putter path direction (via [PutterPathTracker])
 *
 * Physical setup:
 *  - GREEN  sticker on the toe end of the putter
 *  - ORANGE sticker on the heel end of the putter
 *
 * The vector from GREEN → ORANGE defines the face axis.
 * Its angle relative to horizontal gives face angle in degrees.
 *
 * HSV ranges are configurable at runtime via [applySettings].
 *
 * STATUS: Pipeline implemented. Requires calibration with real stickers
 *         under your typical lighting conditions.
 *
 * TODO:
 *  - Calibrate HSV ranges via Settings screen
 *  - Add temporal smoothing (Kalman filter on centroid positions)
 *  - Homography correction for cameras not exactly perpendicular to face
 */
class ColorMarkerDetector @Inject constructor() {

    companion object {
        // Default HSV ranges — override via applySettings()
        val DEFAULT_GREEN_LOWER  = Scalar(40.0, 150.0, 100.0)
        val DEFAULT_GREEN_UPPER  = Scalar(80.0, 255.0, 255.0)
        val DEFAULT_ORANGE_LOWER = Scalar(8.0,  200.0, 150.0)
        val DEFAULT_ORANGE_UPPER = Scalar(20.0, 255.0, 255.0)

        private const val MIN_BLOB_AREA = 30.0
        private const val MAX_BLOB_AREA = 5000.0
    }

    // Mutable HSV ranges — updated by applySettings()
    private var greenLower : Scalar = DEFAULT_GREEN_LOWER
    private var greenUpper : Scalar = DEFAULT_GREEN_UPPER
    private var orangeLower: Scalar = DEFAULT_ORANGE_LOWER
    private var orangeUpper: Scalar = DEFAULT_ORANGE_UPPER

    // Reusable OpenCV Mats
    private val hsvMat     = Mat()
    private val maskGreen  = Mat()
    private val maskOrange = Mat()
    private val hierarchy  = Mat()
    private val tempMat    = Mat()

    // ─────────────────────────────────────────────────────────────────────────
    // Detection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Detect both markers in [bitmap] and return the result.
     * Bitmap ownership is NOT transferred — caller must recycle after this call.
     */
    fun detect(bitmap: Bitmap): MarkerDetectionResult {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        Imgproc.cvtColor(src, tempMat, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(tempMat, hsvMat, Imgproc.COLOR_RGB2HSV)
        src.release()

        val greenCenter  = findMarkerCenter(hsvMat, maskGreen,  greenLower,  greenUpper)
        val orangeCenter = findMarkerCenter(hsvMat, maskOrange, orangeLower, orangeUpper)

        return if (greenCenter != null && orangeCenter != null) {
            MarkerDetectionResult.Found(
                greenCenter  = greenCenter,
                orangeCenter = orangeCenter,
                faceAngleDeg = computeFaceAngleDeg(greenCenter, orangeCenter),
                confidence   = 0.8f
            )
        } else {
            MarkerDetectionResult.NotFound(
                greenFound  = greenCenter  != null,
                orangeFound = orangeCenter != null
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Configuration
    // ─────────────────────────────────────────────────────────────────────────

    /** Apply user-calibrated HSV ranges from SettingsDataStore. */
    fun applySettings(s: AppSettings) {
        greenLower  = Scalar(s.greenHMin.toDouble(),  s.greenSMin.toDouble(),  s.greenVMin.toDouble())
        greenUpper  = Scalar(s.greenHMax.toDouble(),  255.0, 255.0)
        orangeLower = Scalar(s.orangeHMin.toDouble(), s.orangeSMin.toDouble(), s.orangeVMin.toDouble())
        orangeUpper = Scalar(s.orangeHMax.toDouble(), 255.0, 255.0)
    }

    /** Free all native OpenCV memory. Call from ViewModel.onCleared(). */
    fun release() {
        hsvMat.release()
        maskGreen.release()
        maskOrange.release()
        hierarchy.release()
        tempMat.release()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────────

    private fun findMarkerCenter(
        hsv  : Mat,
        mask : Mat,
        lower: Scalar,
        upper: Scalar
    ): Point? {
        Core.inRange(hsv, lower, upper, mask)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN,  kernel)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        kernel.release()

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        if (contours.isEmpty()) return null

        val best = contours
            .map  { c -> c to Imgproc.contourArea(c) }
            .filter { (_, area) -> area in MIN_BLOB_AREA..MAX_BLOB_AREA }
            .maxByOrNull { (_, area) -> area }
            ?.first ?: return null

        val M = Imgproc.moments(best)
        if (M.m00 == 0.0) return null
        return Point(M.m10 / M.m00, M.m01 / M.m00)
    }

    /**
     * Angle of the GREEN→ORANGE vector relative to horizontal.
     * + = open face,  − = closed face  (right-handed player, FRONT view).
     *
     * TODO: Convert pixel angle to real-world degrees via known putter length
     *       or homography calibration.
     */
    private fun computeFaceAngleDeg(green: Point, orange: Point): Float {
        val dx = orange.x - green.x
        val dy = orange.y - green.y
        return Math.toDegrees(atan2(dy, dx)).toFloat()
    }
}

// ─────────────────────────────────────────────────────────────────────────────

sealed class MarkerDetectionResult {
    data class Found(
        val greenCenter : Point,
        val orangeCenter: Point,
        val faceAngleDeg: Float,
        val confidence  : Float
    ) : MarkerDetectionResult()

    data class NotFound(
        val greenFound : Boolean,
        val orangeFound: Boolean
    ) : MarkerDetectionResult()
}
