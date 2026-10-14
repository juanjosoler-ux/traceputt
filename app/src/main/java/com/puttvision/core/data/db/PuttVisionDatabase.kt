package com.puttvision.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.puttvision.core.data.db.dao.PuttMetricsDao
import com.puttvision.core.data.db.dao.PuttRecordDao
import com.puttvision.core.data.db.dao.SessionDao
import com.puttvision.core.data.db.dao.UserConfigDao
import com.puttvision.core.data.db.dao.PuttDeleteDao
import com.puttvision.core.data.db.entity.PuttMetricsEntity
import com.puttvision.core.data.db.entity.PuttRecordEntity
import com.puttvision.core.data.db.entity.SessionEntity
import com.puttvision.core.data.db.entity.UserConfigEntity

@Database(
    entities = [
        SessionEntity::class,
        PuttRecordEntity::class,
        PuttMetricsEntity::class,
        UserConfigEntity::class
    ],
    version  = 1,
    exportSchema = true   // set to true so migrations can be verified in CI
)
abstract class PuttVisionDatabase : RoomDatabase() {
    abstract fun sessionDao()    : SessionDao
    abstract fun puttRecordDao() : PuttRecordDao
    abstract fun puttMetricsDao(): PuttMetricsDao
    abstract fun userConfigDao() : UserConfigDao
    abstract fun puttDeleteDao() : PuttDeleteDao

    companion object {
        const val DB_NAME = "puttvision.db"
    }
}
