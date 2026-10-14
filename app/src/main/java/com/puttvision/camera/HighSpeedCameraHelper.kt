package com.puttvision.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import android.util.Size
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider

private const val TAG = "HighSpeedCameraHelper"

/**
 * Queries the device for high-speed video capture capabilities.
 *
 * High-speed capture significantly improves analysis quality:
 *  - 120fps → better tempo timestamps, smoother face-angle detection
 *  - 240fps → (premium devices only) near-ideal for ball launch tracking
 *
 * Usage: call [queryHighSpeedSupport] once after camera is bound, then
 * set [CamcorderProfile] or [QualitySelector] accordingly.
 *
 * STATUS: FUNCTIONAL — reads Camera2 characteristics.
 *         CameraX does not expose a direct high-speed API; for true 120fps
 *         video capture, Camera2 `createConstrainedHighSpeedCaptureSession`
 *         is required. This helper provides the info needed to decide.
 *
 * TODO:
 *  - Implement Camera2 constrained high-speed session (breaks CameraX binding)
 *  - Surface 120fps frames to ML Kit analysis pipeline
 *  - Currently CameraX limits to 60fps; this is the next major upgrade.
 */
object HighSpeedCameraHelper {

    data class HighSpeedCapability(
        /** True if the rear camera supports ≥ 120fps at any resolution */
        val supports120fps : Boolean,
        /** True if ≥ 240fps is available */
        val supports240fps : Boolean,
        /** Best high-speed resolution available, null if none */
        val bestResolution : Size?,
        /** Supported FPS ranges above 60 */
        val highFpsRanges  : List<Range<Int>>
    )

    @OptIn(ExperimentalCamera2Interop::class)
    fun queryHighSpeedSupport(context: Context): HighSpeedCapability {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val rearCameraId  = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return noCapability()

            val chars     = cameraManager.getCameraCharacteristics(rearCameraId)
            val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return noCapability()

            // High-speed FPS ranges (constrained session)
            @Suppress("UNCHECKED_CAST")
            val hsRanges = streamMap.highSpeedVideoFpsRanges?.toList() ?: emptyList()
            val highFps  = hsRanges.filter { it.upper >= 120 }

            // Best high-speed resolution
            val hsSizes = streamMap.highSpeedVideoSizes?.toList() ?: emptyList()
            val bestRes = hsSizes.maxByOrNull { it.width * it.height }

            Log.d(TAG, "High-speed FPS ranges: $highFps, sizes: $hsSizes")

            HighSpeedCapability(
                supports120fps = highFps.any { it.upper >= 120 },
                supports240fps = highFps.any { it.upper >= 240 },
                bestResolution = bestRes,
                highFpsRanges  = highFps
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query high-speed capability: ${e.message}")
            noCapability()
        }
    }

    private fun noCapability() = HighSpeedCapability(
        supports120fps = false,
        supports240fps = false,
        bestResolution = null,
        highFpsRanges  = emptyList()
    )
}
