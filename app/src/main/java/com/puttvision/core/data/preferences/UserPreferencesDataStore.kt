package com.puttvision.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.puttvision.core.domain.model.EnvironmentType
import com.puttvision.core.domain.model.PerspectiveType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Top-level delegate: ensures a single DataStore per Context per name
private val Context.puttPrefsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "putt_user_prefs")

data class UserPreferences(
    val environment : EnvironmentType = EnvironmentType.HOME,
    val perspective : PerspectiveType = PerspectiveType.BEHIND
)

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENVIRONMENT  = stringPreferencesKey("env")
        val PERSPECTIVE  = stringPreferencesKey("perspective")
    }

    val preferences: Flow<UserPreferences> = context.puttPrefsDataStore.data
        .catch { e ->
            // Emit empty prefs on IO error so the app doesn't crash
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            UserPreferences(
                environment = prefs[Keys.ENVIRONMENT]
                    ?.runCatching { EnvironmentType.valueOf(this) }?.getOrNull()
                    ?: EnvironmentType.HOME,
                perspective = prefs[Keys.PERSPECTIVE]
                    ?.runCatching { PerspectiveType.valueOf(this) }?.getOrNull()
                    ?: PerspectiveType.BEHIND
            )
        }

    suspend fun save(prefs: UserPreferences) {
        context.puttPrefsDataStore.edit { store ->
            store[Keys.ENVIRONMENT] = prefs.environment.name
            store[Keys.PERSPECTIVE] = prefs.perspective.name
        }
    }
}
