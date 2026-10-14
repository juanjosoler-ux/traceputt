package com.puttvision.feature.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.onboardingStore by preferencesDataStore(name = "onboarding")

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context
) : ViewModel() {

    private val KEY_DONE = booleanPreferencesKey("done")

    /** True once the user has tapped "EMPEZAR" */
    val isOnboardingDone: StateFlow<Boolean?> = ctx.onboardingStore.data
        .map { it[KEY_DONE] ?: false }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    fun markDone() {
        viewModelScope.launch {
            ctx.onboardingStore.edit { prefs -> prefs[KEY_DONE] = true }
        }
    }
}
