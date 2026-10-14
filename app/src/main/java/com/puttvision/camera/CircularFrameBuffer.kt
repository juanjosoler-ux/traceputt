package com.puttvision.camera

import android.graphics.Bitmap

/**
 * Fixed-capacity ring buffer storing decoded video frames and their timestamps.
 *
 * Used by the vision pipeline to access frames before/after impact without
 * keeping the entire recording in memory.
 *
 * Thread-safety: NOT thread-safe by design. All calls must come from the
 * same analysis executor thread (the ImageAnalysis executor in CameraX).
 *
 * Memory budget at 30fps / 720p RGBA:
 *   1280×720 × 4 bytes × 90 frames ≈ 330 MB  ← too much
 *   640×360  × 4 bytes × 90 frames ≈  83 MB  ← acceptable
 *
 * Strategy: CameraX ImageAnalysis delivers downscaled frames; store at
 * analysis resolution (typically 640×360). Full-quality clip is handled
 * separately by VideoCapture.
 *
 * STATUS: IMPLEMENTED — integrated into pose analysis path.
 * TODO: Expose [extractClipAroundImpact] to TempoAnalyzer / ColorMarkerDetector.
 */
class CircularFrameBuffer(val capacity: Int = 90) {

    data class FrameEntry(
        val bitmap     : Bitmap,
        val timestampMs: Long
    )

    private val buffer = arrayOfNulls<FrameEntry>(capacity)
    private var writeIndex = 0
    private var size       = 0

    /** Add a frame. Recycles the oldest bitmap when buffer is full. */
    fun push(bitmap: Bitmap, timestampMs: Long) {
        // Recycle the slot we're about to overwrite
        buffer[writeIndex]?.bitmap?.takeIf { !it.isRecycled }?.recycle()

        buffer[writeIndex] = FrameEntry(bitmap, timestampMs)
        writeIndex = (writeIndex + 1) % capacity
        if (size < capacity) size++
    }

    /**
     * Returns frames in chronological order within [windowMs] ms before
     * and [postWindowMs] ms after [impactMs].
     */
    fun extractClipAroundImpact(
        impactMs    : Long,
        windowMs    : Long = 2_000L,
        postWindowMs: Long = 1_000L
    ): List<FrameEntry> {
        val start = impactMs - windowMs
        val end   = impactMs + postWindowMs
        return toChronologicalList().filter { it.timestampMs in start..end }
    }

    /** Returns the most recent [n] frames in chronological order. */
    fun last(n: Int): List<FrameEntry> = toChronologicalList().takeLast(n)

    /** Returns all stored frames in chronological order. */
    fun toChronologicalList(): List<FrameEntry> {
        if (size == 0) return emptyList()
        return if (size < capacity) {
            // Buffer not yet full: entries are from 0 until writeIndex
            buffer.take(size).filterNotNull()
        } else {
            // Full: oldest entry is at writeIndex, newest at writeIndex-1
            val result = mutableListOf<FrameEntry>()
            for (i in 0 until capacity) {
                buffer[(writeIndex + i) % capacity]?.let { result.add(it) }
            }
            result
        }
    }

    val frameCount: Int get() = size

    /** Free all bitmap memory. Call when the session ends. */
    fun release() {
        buffer.forEach { it?.bitmap?.takeIf { b -> !b.isRecycled }?.recycle() }
        buffer.fill(null)
        writeIndex = 0
        size       = 0
    }
}
