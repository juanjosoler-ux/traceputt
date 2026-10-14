package com.puttvision.core.data.db

import com.puttvision.core.data.db.entity.PuttMetricsEntity
import com.puttvision.core.data.db.entity.PuttRecordEntity
import com.puttvision.core.data.db.entity.SessionEntity
import com.puttvision.core.data.db.entity.UserConfigEntity
import com.puttvision.core.domain.model.*

// ── Session ──────────────────────────────────────────────────────────────────

fun SessionEntity.toDomain() = Session(
    id              = id,
    createdAt       = createdAt,
    environmentType = EnvironmentType.valueOf(environmentType),
    perspectiveType = PerspectiveType.valueOf(perspectiveType),
    notes           = notes
)

fun Session.toEntity() = SessionEntity(
    id              = id,
    createdAt       = createdAt,
    environmentType = environmentType.name,
    perspectiveType = perspectiveType.name,
    notes           = notes
)

// ── PuttRecord ───────────────────────────────────────────────────────────────

fun PuttRecordEntity.toDomain() = PuttRecord(
    id                  = id,
    sessionId           = sessionId,
    createdAt           = createdAt,
    videoPath           = videoPath,
    thumbnailPath       = thumbnailPath,
    quality             = PuttQuality.valueOf(quality),
    detectionConfidence = detectionConfidence
)

fun PuttRecord.toEntity() = PuttRecordEntity(
    id                  = id,
    sessionId           = sessionId,
    createdAt           = createdAt,
    videoPath           = videoPath,
    thumbnailPath       = thumbnailPath,
    quality             = quality.name,
    detectionConfidence = detectionConfidence
)

// ── PuttMetrics ──────────────────────────────────────────────────────────────

fun PuttMetricsEntity.toDomain() = PuttMetrics(
    puttId        = puttId,
    backswingMs   = backswingMs,
    followMs      = followMs,
    tempoRatio    = tempoRatio,
    headMovePx    = headMovePx,
    headStability = headStability,
    faceAngleDeg  = faceAngleDeg,
    pathAngleDeg  = pathAngleDeg,
    launchDirDeg  = launchDirDeg,
    overallConf   = overallConf
)

fun PuttMetrics.toEntity() = PuttMetricsEntity(
    puttId        = puttId,
    backswingMs   = backswingMs,
    followMs      = followMs,
    tempoRatio    = tempoRatio,
    headMovePx    = headMovePx,
    headStability = headStability,
    faceAngleDeg  = faceAngleDeg,
    pathAngleDeg  = pathAngleDeg,
    launchDirDeg  = launchDirDeg,
    overallConf   = overallConf
)
