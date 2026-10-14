package com.puttvision.feature.live

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.video.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

private const val TAG = "VideoRecordingManager"

/**
 * Manages CameraX [VideoCapture] recording for a single session.
 *
 * Responsibilities:
 *  - Start/stop individual putt clips
 *  - Emit [VideoEvent] when a clip is finalized
 *  - Store clips under [sessionDir]
 *
 * Owns no lifecycle — must be created and released by its caller (LiveScreen).
 *
 * Usage:
 *  1. Create with [videoCapture] obtained from CameraX bind.
 *  2. Call [startClip] before each putt.
 *  3. Call [stopClip] after impact + follow-through.
 *  4. Observe [events] for [VideoEvent.Saved] / [VideoEvent.Error].
 *  5. Call [release] when session ends.
 */
class VideoRecordingManager(
    private val context      : Context,
    private val videoCapture : VideoCapture<Recorder>,
    private val executor     : Executor,
    sessionId                : Long
) {
    private val sessionDir = File(context.filesDir, "sessions/$sessionId").also { it.mkdirs() }

    // Events emitted to the screen/ViewModel
    sealed class VideoEvent {
        data class Saved(val path: String) : VideoEvent()
        data class Error(val code: Int, val message: String) : VideoEvent()
    }

    private val _events = Channel<VideoEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var activeRecording: Recording? = null
    private var isRecording = false

    /** Begin recording a new clip. Safe to call even if already recording (no-op). */
    @SuppressLint("MissingPermission")  // permission checked in LiveScreen before binding
    fun startClip() {
        if (isRecording) return
        val ts   = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(sessionDir, "putt_$ts.mp4")
        val opts = FileOutputOptions.Builder(file).build()

        activeRecording = videoCapture.output
            .prepareRecording(context, opts)
            .withAudioEnabled()
            .start(executor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        Log.d(TAG, "Clip recording started: ${file.name}")
                    }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        activeRecording = null
                        if (event.hasError()) {
                            Log.e(TAG, "Clip error code=${event.error}")
                            _events.trySend(VideoEvent.Error(event.error, "Recording failed"))
                        } else {
                            Log.d(TAG, "Clip saved: ${file.absolutePath}")
                            _events.trySend(VideoEvent.Saved(file.absolutePath))
                        }
                    }
                    else -> {}
                }
            }
    }

    /**
     * Stop recording and finalize the current clip.
     * [VideoEvent.Saved] will be emitted once the file is written.
     */
    fun stopClip() {
        activeRecording?.stop()
    }

    /** Immediately stop and discard the current clip (e.g. false trigger). */
    fun discardClip() {
        activeRecording?.stop()
        // TODO: delete the file once Finalize fires
    }

    /** Release all resources. Call from DisposableEffect in the composable. */
    fun release() {
        activeRecording?.stop()
        activeRecording = null
    }
}
