package com.puttvision.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsDataStore
        by preferencesDataStore(name = "putt_app_settings")

data class AppSettings(
    val audioThreshold : Float = 3_500f,
    // Green marker HSV
    val greenHMin      : Float = 40f,
    val greenHMax      : Float = 80f,
    val greenSMin      : Float = 150f,
    val greenVMin      : Float = 100f,
    // Orange marker HSV
    val orangeHMin     : Float = 8f,
    val orangeHMax     : Float = 20f,
    val orangeSMin     : Float = 200f,
    val orangeVMin     : Float = 150f
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val AUDIO_THRESH  = floatPreferencesKey("audio_thresh")
        val GREEN_H_MIN   = floatPreferencesKey("g_h_min")
        val GREEN_H_MAX   = floatPreferencesKey("g_h_max")
        val GREEN_S_MIN   = floatPreferencesKey("g_s_min")
        val GREEN_V_MIN   = floatPreferencesKey("g_v_min")
        val ORANGE_H_MIN  = floatPreferencesKey("o_h_min")
        val ORANGE_H_MAX  = floatPreferencesKey("o_h_max")
        val ORANGE_S_MIN  = floatPreferencesKey("o_s_min")
        val ORANGE_V_MIN  = floatPreferencesKey("o_v_min")
    }

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                audioThreshold = p[Keys.AUDIO_THRESH] ?: 3_500f,
                greenHMin      = p[Keys.GREEN_H_MIN]  ?: 40f,
                greenHMax      = p[Keys.GREEN_H_MAX]  ?: 80f,
                greenSMin      = p[Keys.GREEN_S_MIN]  ?: 150f,
                greenVMin      = p[Keys.GREEN_V_MIN]  ?: 100f,
                orangeHMin     = p[Keys.ORANGE_H_MIN] ?: 8f,
                orangeHMax     = p[Keys.ORANGE_H_MAX] ?: 20f,
                orangeSMin     = p[Keys.ORANGE_S_MIN] ?: 200f,
                orangeVMin     = p[Keys.ORANGE_V_MIN] ?: 150f
            )
        }

    suspend fun save(s: AppSettings) {
        context.appSettingsDataStore.edit { p ->
            p[Keys.AUDIO_THRESH] = s.audioThreshold
            p[Keys.GREEN_H_MIN]  = s.greenHMin
            p[Keys.GREEN_H_MAX]  = s.greenHMax
            p[Keys.GREEN_S_MIN]  = s.greenSMin
            p[Keys.GREEN_V_MIN]  = s.greenVMin
            p[Keys.ORANGE_H_MIN] = s.orangeHMin
            p[Keys.ORANGE_H_MAX] = s.orangeHMax
            p[Keys.ORANGE_S_MIN] = s.orangeSMin
            p[Keys.ORANGE_V_MIN] = s.orangeVMin
        }
    }
}
