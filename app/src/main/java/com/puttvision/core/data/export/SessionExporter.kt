package com.puttvision.core.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.puttvision.core.data.repository.PuttRepository
import com.puttvision.core.data.repository.SessionRepository
import com.puttvision.core.domain.model.PuttMetrics
import com.puttvision.core.domain.model.PuttRecord
import com.puttvision.core.domain.model.Session
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports a session's putts and metrics to a CSV file.
 *
 * Output format:
 *   putt_number, timestamp, tempo_ratio, backswing_ms, follow_ms,
 *   head_stability, head_move_px, face_angle_deg, path_angle_deg,
 *   launch_dir_deg, overall_confidence, quality, video_path
 *
 * Usage:
 *   val intent = exporter.exportSession(context, sessionId)
 *   if (intent != null) startActivity(Intent.createChooser(intent, "Exportar sesión"))
 */
@Singleton
class SessionExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepo: SessionRepository,
    private val puttRepo   : PuttRepository
) {
    /**
     * Builds a CSV file and returns a share [Intent], or null if the session
     * has no putts.
     */
    suspend fun exportSession(sessionId: Long): Intent? = withContext(Dispatchers.IO) {
        val session = sessionRepo.getById(sessionId) ?: return@withContext null
        val putts   = puttRepo.getBySession(sessionId)
        if (putts.isEmpty()) return@withContext null

        val file = buildCsv(session, putts)
        buildShareIntent(file)
    }

    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun buildCsv(session: Session, putts: List<PuttRecord>): File {
        val dateFmt   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val ts        = dateFmt.format(Date(session.createdAt))
        val fileName  = "puttvision_${ts}_${session.perspectiveType.name.lowercase()}.csv"
        val dir       = File(context.filesDir, "exports").also { it.mkdirs() }
        val file      = File(dir, fileName)

        val sb = StringBuilder()

        // Header
        sb.appendLine("# PuttVision Export")
        sb.appendLine("# Session: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(session.createdAt))}")
        sb.appendLine("# Environment: ${session.environmentType.label}")
        sb.appendLine("# Perspective: ${session.perspectiveType.label}")
        sb.appendLine("# Notes: ${session.notes}")
        sb.appendLine()
        sb.appendLine(
            "putt,timestamp,tempo_ratio,backswing_ms,follow_ms," +
            "head_stability,head_move_px,face_angle_deg,path_angle_deg," +
            "launch_dir_deg,overall_conf,quality"
        )

        // Rows
        putts.forEachIndexed { idx, record ->
            val m = puttRepo.getMetrics(record.id)
            sb.appendLine(buildRow(idx + 1, record, m))
        }

        file.writeText(sb.toString())
        return file
    }

    private fun buildRow(idx: Int, r: PuttRecord, m: PuttMetrics?): String {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(r.createdAt))
        fun f(v: Float?)  = v?.let { "%.3f".format(it) } ?: ""
        fun l(v: Long?)   = v?.toString() ?: ""
        return "$idx,$ts,${f(m?.tempoRatio)},${l(m?.backswingMs)},${l(m?.followMs)}," +
               "${f(m?.headStability)},${f(m?.headMovePx)},${f(m?.faceAngleDeg)}," +
               "${f(m?.pathAngleDeg)},${f(m?.launchDirDeg)},${f(m?.overallConf)}," +
               r.quality.name
    }

    private fun buildShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type         = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PuttVision — ${file.nameWithoutExtension}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
