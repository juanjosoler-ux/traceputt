package com.puttvision.di

import android.content.Context
import androidx.room.Room
import com.puttvision.audio.AudioImpactDetector
import com.puttvision.core.data.db.PuttVisionDatabase
import com.puttvision.core.data.db.dao.*
import com.puttvision.core.data.preferences.UserPreferencesDataStore
import com.puttvision.vision.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): PuttVisionDatabase =
        Room.databaseBuilder(ctx, PuttVisionDatabase::class.java, PuttVisionDatabase.DB_NAME)
            .addMigrations(*com.puttvision.core.data.db.migration.DatabaseMigrations.ALL)
            // Keep destructive fallback ONLY for debug builds until first field deployment
            .apply {
                if (com.puttvision.BuildConfig.DEBUG) fallbackToDestructiveMigration()
            }
            .build()

    @Provides fun provideSessionDao   (db: PuttVisionDatabase): SessionDao     = db.sessionDao()
    @Provides fun providePuttRecordDao(db: PuttVisionDatabase): PuttRecordDao  = db.puttRecordDao()
    @Provides fun providePuttMetricDao(db: PuttVisionDatabase): PuttMetricsDao = db.puttMetricsDao()
    @Provides fun provideUserConfigDao(db: PuttVisionDatabase): UserConfigDao  = db.userConfigDao()
    @Provides fun providePuttDeleteDao  (db: PuttVisionDatabase): com.puttvision.core.data.db.dao.PuttDeleteDao = db.puttDeleteDao()
}

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides @Singleton
    fun provideUserPreferencesDataStore(@ApplicationContext ctx: Context) =
        UserPreferencesDataStore(ctx)
}

@Module
@InstallIn(SingletonComponent::class)
object VisionModule {

    @Provides @Singleton fun provideAddressDetector()      = AddressDetector()
    @Provides @Singleton fun provideHeadStabilityTracker() = HeadStabilityTracker()
    @Provides @Singleton fun provideTempoAnalyzer()        = TempoAnalyzer()
    @Provides @Singleton fun provideColorMarkerDetector()  = ColorMarkerDetector()
    @Provides @Singleton fun providePutterPathTracker()    = PutterPathTracker()
    @Provides @Singleton fun provideBallLaunchDetector()   = BallLaunchDetector()

    @Provides @Singleton
    fun providePuttAnalysisEngine(
        head  : HeadStabilityTracker,
        tempo : TempoAnalyzer,
        color : ColorMarkerDetector,
        path  : PutterPathTracker,
        ball  : BallLaunchDetector
    ): PuttAnalysisEngine = PuttAnalysisEngine(head, tempo, color, path, ball)
}

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    @Provides @Singleton
    fun provideAdaptiveAudioThreshold() = com.puttvision.audio.AdaptiveAudioThreshold()

    @Provides @Singleton
    fun provideAudioImpactDetector(
        adaptive: com.puttvision.audio.AdaptiveAudioThreshold
    ) = AudioImpactDetector(adaptive)

    @Provides @Singleton
    fun provideAudioLevelMonitor() = com.puttvision.audio.AudioLevelMonitor()
}

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides @Singleton
    fun provideSettingsDataStore(@ApplicationContext ctx: Context) =
        com.puttvision.core.data.preferences.SettingsDataStore(ctx)

    // AdaptiveAudioThreshold is singleton in AudioModule;
    // re-exposed here for clarity (same instance via Hilt graph)
}

@Module
@InstallIn(SingletonComponent::class)
object ExportModule {

    @Provides @Singleton
    fun provideSessionExporter(
        @ApplicationContext ctx: Context,
        sessionRepo: com.puttvision.core.data.repository.SessionRepository,
        puttRepo   : com.puttvision.core.data.repository.PuttRepository
    ) = com.puttvision.core.data.export.SessionExporter(ctx, sessionRepo, puttRepo)

    @Provides @Singleton
    fun providePdfExporter(
        @ApplicationContext ctx: Context,
        sessionRepo: com.puttvision.core.data.repository.SessionRepository,
        puttRepo   : com.puttvision.core.data.repository.PuttRepository
    ) = com.puttvision.core.data.export.PdfExporter(ctx, sessionRepo, puttRepo)
}
