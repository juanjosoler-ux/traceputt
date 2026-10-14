package com.puttvision.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import java.util.concurrent.Executor

private const val TAG = "Camera2HighSpeed"

/**
 * Opens a Camera2 constrained high-speed capture session for 120fps recording.
 *
 * This is the real 120fps path — CameraX's VideoCapture is capped at the
 * camera's standard frame-rate and cannot use high-speed sessions.
 *
 * High-speed sessions use `createConstrainedHighSpeedCaptureSession` and
 * require:
 *  - A CameraCharacteristics high-speed supported size
 *  - FPS range where both min and max equal the target rate (e.g. [120,120])
 *  - Only TWO outputs: preview Surface + recording Surface (no ImageAnalysis!)
 *
 * Limitations:
 *  - Cannot run ML Kit pose detection simultaneously (no ImageAnalysis output)
 *  - Must choose: 120fps analysis OR 120fps recording — not both
 *  - Strategy for v2: record at 120fps, extract frames via MediaMetadataRetriever
 *    post-session for ML Kit analysis
 *
 * STATUS: IMPLEMENTED. Call [openHighSpeedSession] from a coroutine.
 *         Integration into LIVE flow is optional — enabled in Settings.
 *
 * TODO: Wire into LiveScreen as an opt-in path when HighSpeedCameraHelper.supports120fps
 */
class Camera2HighSpeedSession(private val context: Context) {

    private var cameraDevice    : CameraDevice?   = null
    private var captureSession  : CameraCaptureSession? = null
    private var backgroundThread: HandlerThread?  = null
    private var backgroundHandler: Handler?       = null

    /**
     * Opens a 120fps (or best available) high-speed capture session.
     *
     * @param previewSurface  Surface from the PreviewView
     * @param recordSurface   Surface from MediaRecorder or MediaCodec
     * @param onError         Called if the session fails to open
     */
    @SuppressLint("MissingPermission")
    fun openHighSpeedSession(
        previewSurface: Surface,
        recordSurface : Surface,
        onError       : (String) -> Unit = {}
    ) {
        val hsInfo = HighSpeedCameraHelper.queryHighSpeedSupport(context)
        if (!hsInfo.supports120fps) {
            onError("Device does not support 120fps high-speed capture")
            return
        }

        startBackgroundThread()
        val handler   = backgroundHandler ?: return
        val manager   = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId  = findRearCamera(manager) ?: run { onError("No rear camera"); return }

        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createHighSpeedSession(camera, previewSurface, recordSurface, handler, onError)
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); cameraDevice = null }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close(); cameraDevice = null
                    onError("Camera open error: $error")
                }
            }, handler)
        } catch (e: Exception) {
            onError("openCamera failed: ${e.message}")
        }
    }

    private fun createHighSpeedSession(
        camera        : CameraDevice,
        previewSurface: Surface,
        recordSurface : Surface,
        handler       : Handler,
        onError       : (String) -> Unit
    ) {
        val surfaces   = listOf(previewSurface, recordSurface)
        val outputConfigs = surfaces.map { OutputConfiguration(it) }

        val sessionConfig = SessionConfiguration(
            SessionConfiguration.SESSION_HIGH_SPEED,
            outputConfigs,
            { it.run() } as Executor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    startHighSpeedRepeating(session, surfaces, onError)
                    Log.d(TAG, "High-speed session configured")
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    onError("High-speed session configuration failed")
                }
            }
        )

        try {
            camera.createCaptureSession(sessionConfig)
        } catch (e: Exception) {
            onError("createCaptureSession failed: ${e.message}")
        }
    }

    private fun startHighSpeedRepeating(
        session : CameraCaptureSession,
        surfaces: List<Surface>,
        onError : (String) -> Unit
    ) {
        try {
            val hsSession = session as? CameraConstrainedHighSpeedCaptureSession
                ?: run { onError("Session is not high-speed"); return }

            val builder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            surfaces.forEach { builder.addTarget(it) }

            // Use the highest supported fps range where min == max (locked fps)
            val chars   = (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager)
                .getCameraCharacteristics(cameraDevice!!.id)
            val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val bestRange = streamMap?.highSpeedVideoFpsRanges
                ?.filter { it.lower == it.upper && it.upper >= 120 }
                ?.maxByOrNull { it.upper }

            bestRange?.let { builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
            Log.d(TAG, "High-speed FPS range: $bestRange")

            val requests = hsSession.createHighSpeedRequestList(builder.build())
            hsSession.setRepeatingBurst(requests, null, backgroundHandler)
        } catch (e: Exception) {
            onError("startHighSpeedRepeating failed: ${e.message}")
        }
    }

    fun close() {
        captureSession?.close();  captureSession  = null
        cameraDevice?.close();    cameraDevice    = null
        stopBackgroundThread()
    }

    private fun startBackgroundThread() {
        backgroundThread  = HandlerThread("Camera2HS").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread  = null
        backgroundHandler = null
    }

    private fun findRearCamera(manager: CameraManager): String? =
        manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
}
