package com.puttvision.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.puttvision.core.domain.model.EnvironmentType
import com.puttvision.core.domain.model.PerspectiveType
import com.puttvision.core.domain.model.PuttQuality

// ─────────────────────────────────────────────────────────────────────────────
// sessions
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id             : Long   = 0,
    @ColumnInfo(name = "created_at")
    val createdAt      : Long   = System.currentTimeMillis(),
    @ColumnInfo(name = "environment_type")
    val environmentType: String = EnvironmentType.HOME.name,
    @ColumnInfo(name = "perspective_type")
    val perspectiveType: String = PerspectiveType.BEHIND.name,
    val notes          : String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// putt_records
// ─────────────────────────────────────────────────────────────────────────────

@Entity(
    tableName = "putt_records",
    foreignKeys = [
        ForeignKey(
            entity        = SessionEntity::class,
            parentColumns = ["id"],
            childColumns  = ["session_id"],
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id")]
)
data class PuttRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id                  : Long    = 0,
    @ColumnInfo(name = "session_id")
    val sessionId           : Long    = 0,
    @ColumnInfo(name = "created_at")
    val createdAt           : Long    = System.currentTimeMillis(),
    @ColumnInfo(name = "video_path")
    val videoPath           : String? = null,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath       : String? = null,
    val quality             : String  = PuttQuality.UNTAGGED.name,
    @ColumnInfo(name = "detection_confidence")
    val detectionConfidence : Float   = 0f
)

// ─────────────────────────────────────────────────────────────────────────────
// putt_metrics  (1-to-1 with putt_records)
// ─────────────────────────────────────────────────────────────────────────────

@Entity(
    tableName = "putt_metrics",
    foreignKeys = [
        ForeignKey(
            entity        = PuttRecordEntity::class,
            parentColumns = ["id"],
            childColumns  = ["putt_id"],
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices = [Index("putt_id", unique = true)]
)
data class PuttMetricsEntity(
    @PrimaryKey
    @ColumnInfo(name = "putt_id")
    val puttId        : Long   = 0,

    @ColumnInfo(name = "backswing_ms")
    val backswingMs   : Long?  = null,
    @ColumnInfo(name = "follow_ms")
    val followMs      : Long?  = null,
    @ColumnInfo(name = "tempo_ratio")
    val tempoRatio    : Float? = null,

    @ColumnInfo(name = "head_move_px")
    val headMovePx    : Float? = null,
    @ColumnInfo(name = "head_stability")
    val headStability : Float? = null,

    @ColumnInfo(name = "face_angle_deg")
    val faceAngleDeg  : Float? = null,
    @ColumnInfo(name = "path_angle_deg")
    val pathAngleDeg  : Float? = null,
    @ColumnInfo(name = "launch_dir_deg")
    val launchDirDeg  : Float? = null,

    @ColumnInfo(name = "overall_conf")
    val overallConf   : Float  = 0f
)

// ─────────────────────────────────────────────────────────────────────────────
// user_config  (single row, id always = 1)
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "user_config")
data class UserConfigEntity(
    @PrimaryKey
    val id                    : Int    = 1,
    @ColumnInfo(name = "preferred_environment")
    val preferredEnvironment  : String = EnvironmentType.HOME.name,
    @ColumnInfo(name = "preferred_perspective")
    val preferredPerspective  : String = PerspectiveType.BEHIND.name
)
