package com.puttvision.core.data.export

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.util.Log
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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG    = "PdfExporter"
private const val PAGE_W = 595
private const val PAGE_H = 842
private const val MARGIN = 40f
private const val ROW_H  = 16f

/**
 * Generates a multi-page PDF session report using Android [PdfDocument].
 * Automatically paginates when the putt table exceeds one page.
 *
 * STATUS: FUNCTIONAL — multi-page support added.
 */
@Singleton
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepo: SessionRepository,
    private val puttRepo   : PuttRepository
) {
    suspend fun exportSession(sessionId: Long): Intent? = withContext(Dispatchers.IO) {
        try {
            val session = sessionRepo.getById(sessionId) ?: return@withContext null
            val putts   = puttRepo.getBySession(sessionId)
            if (putts.isEmpty()) return@withContext null
            val metricsMap = putts.associate { r -> r.id to puttRepo.getMetrics(r.id) }
            buildShareIntent(buildPdf(session, putts, metricsMap))
        } catch (e: Exception) { Log.e(TAG, "PDF failed", e); null }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun buildPdf(
        session   : Session,
        putts     : List<PuttRecord>,
        metricsMap: Map<Long, PuttMetrics?>
    ): File {
        val pdf    = PdfDocument()
        val paints = buildPaints()

        // Collect all "draw operations" as lambdas, then paginate
        val drawOps = mutableListOf<(Canvas, Float) -> Float>()

        drawOps += headerOps(session, putts, metricsMap, paints)
        drawOps += tableOps(putts, metricsMap, paints)
        drawOps += footerOp(paints)

        // Render pages
        var pageNum   = 1
        var yPos      = MARGIN
        var page      = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas    = page.canvas

        for (op in drawOps) {
            if (yPos > PAGE_H - MARGIN * 2) {
                // Finish current page, start new one
                pdf.finishPage(page)
                pageNum++
                yPos   = MARGIN
                page   = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
            }
            yPos = op(canvas, yPos)
        }
        pdf.finishPage(page)

        val dir  = File(context.filesDir, "exports").also { it.mkdirs() }
        val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(session.createdAt))
        val file = File(dir, "puttvision_report_$ts.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draw operation builders
    // ─────────────────────────────────────────────────────────────────────────

    private fun headerOps(
        session   : Session,
        putts     : List<PuttRecord>,
        metricsMap: Map<Long, PuttMetrics?>,
        p         : Paints
    ): List<(Canvas, Float) -> Float> = buildList {
        // Title
        add { canvas, y ->
            canvas.drawText("PUTTVISION", MARGIN, y + 22f, p.title)
            y + 28f
        }
        // Session info
        add { canvas, y ->
            val fmt = SimpleDateFormat("dd MMMM yyyy · HH:mm", Locale.getDefault())
            canvas.drawText(fmt.format(Date(session.createdAt)), MARGIN, y, p.sub)
            y + 14f
        }
        add { canvas, y ->
            canvas.drawText(
                "${session.perspectiveType.label}  ·  ${session.environmentType.label}",
                MARGIN, y, p.sub
            )
            y + 10f
        }
        // Divider
        add { canvas, y ->
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, p.divider)
            y + 14f
        }
        // Summary KPIs
        add { canvas, y ->
            val goodCount  = putts.count { it.quality.name == "GOOD" }
            val badCount   = putts.count { it.quality.name == "BAD"  }
            val avgMetrics = metricsMap.values.filterNotNull()
            val avgTempo   = avgMetrics.mapNotNull { it.tempoRatio    }.avgOrNull()
            val avgHead    = avgMetrics.mapNotNull { it.headStability }.avgOrNull()
            val colW       = (PAGE_W - 2 * MARGIN) / 5f

            fun summaryBox(label: String, value: String, x: Float) {
                canvas.drawText(value, x, y + 12f, p.kpiValue)
                canvas.drawText(label, x, y + 22f, p.kpiLabel)
            }
            summaryBox("PUTTS",   "${putts.size}", MARGIN)
            summaryBox("BUENOS",  "$goodCount",    MARGIN + colW)
            summaryBox("MALOS",   "$badCount",     MARGIN + colW * 2)
            summaryBox("Tempo Ø", avgTempo?.let { "%.2f".format(it) } ?: "—", MARGIN + colW * 3)
            summaryBox("Head Ø",  avgHead?.let  { "%.0f".format(it) } ?: "—", MARGIN + colW * 4)
            y + 34f
        }
        // Divider
        add { canvas, y ->
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, p.divider)
            y + 12f
        }
        // Table header
        add { canvas, y ->
            val bgRect = RectF(MARGIN, y, PAGE_W - MARGIN, y + ROW_H)
            canvas.drawRect(bgRect, p.colHeaderBg)
            val cols = columnDefs()
            var cx = MARGIN + 4f
            cols.forEach { (header, colW) ->
                canvas.drawText(header, cx, y + 11f, p.colHeader)
                cx += colW
            }
            y + ROW_H
        }
    }

    private fun tableOps(
        putts     : List<PuttRecord>,
        metricsMap: Map<Long, PuttMetrics?>,
        p         : Paints
    ): List<(Canvas, Float) -> Float> {
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return putts.mapIndexed { idx, record ->
            val m = metricsMap[record.id]
            { canvas: Canvas, y: Float ->
                if (idx % 2 == 0) {
                    canvas.drawRect(RectF(MARGIN, y, PAGE_W - MARGIN, y + ROW_H), p.rowBg)
                }
                var cx = MARGIN + 4f
                val cells = columnDefs().zip(listOf(
                    "${idx + 1}",
                    timeFmt.format(Date(record.createdAt)),
                    m?.tempoRatio?.let { "%.2f".format(it) } ?: "—",
                    m?.headStability?.let { "%.0f".format(it) } ?: "—",
                    m?.faceAngleDeg?.let  { "%.1f°".format(it) } ?: "—",
                    m?.pathAngleDeg?.let  { "%.1f°".format(it) } ?: "—",
                    record.quality.name
                ))
                cells.forEach { (col, text) ->
                    val paint = when (text) {
                        "GOOD" -> p.goodText
                        "BAD"  -> p.badText
                        else   -> p.body
                    }
                    canvas.drawText(text, cx, y + 11f, paint)
                    cx += col.second
                }
                y + ROW_H
            }
        }
    }

    private fun footerOp(p: Paints): List<(Canvas, Float) -> Float> = listOf(
        { canvas, _ ->
            canvas.drawText(
                "Generado por PuttVision · " +
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                MARGIN, PAGE_H - MARGIN, p.footer
            )
            PAGE_H.toFloat()
        }
    )

    // ─────────────────────────────────────────────────────────────────────────

    private fun columnDefs() = listOf(
        "#"      to 20f, "HORA"   to 50f, "TEMPO" to 50f,
        "HEAD"   to 45f, "FACE"   to 42f, "PATH"  to 42f, "TAG" to 40f
    )

    private fun List<Float>.avgOrNull(): Float? =
        if (isEmpty()) null else (sum() / size)

    private fun buildShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PuttVision — ${file.nameWithoutExtension}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Paint definitions
    // ─────────────────────────────────────────────────────────────────────────

    private data class Paints(
        val title      : Paint, val sub        : Paint, val body    : Paint,
        val colHeader  : Paint, val colHeaderBg: Paint, val rowBg   : Paint,
        val kpiValue   : Paint, val kpiLabel   : Paint,
        val divider    : Paint, val footer     : Paint,
        val goodText   : Paint, val badText    : Paint
    )

    private fun buildPaints(): Paints {
        fun paint(size: Float, color: Int, bold: Boolean = false) =
            Paint().apply {
                this.color       = color
                textSize         = size
                isAntiAlias      = true
                if (bold) typeface = Typeface.DEFAULT_BOLD
            }
        val green  = Color.rgb(0, 180, 100)
        val gray   = Color.rgb(100, 100, 100)
        val dark   = Color.rgb(20, 20, 20)
        val lightBg= Color.rgb(246, 249, 246)
        return Paints(
            title       = paint(22f, green, bold = true),
            sub         = paint(11f, gray),
            body        = paint(9f, dark),
            colHeader   = paint(8f, Color.WHITE, bold = true),
            colHeaderBg = Paint().apply { color = Color.rgb(40, 100, 60) },
            rowBg       = Paint().apply { color = lightBg },
            kpiValue    = paint(12f, green, bold = true),
            kpiLabel    = paint(8f, gray),
            divider     = Paint().apply { color = Color.rgb(210, 225, 210); strokeWidth = 0.5f },
            footer      = paint(8f, gray),
            goodText    = paint(9f, green, bold = true),
            badText     = paint(9f, Color.RED, bold = true)
        )
    }
}
