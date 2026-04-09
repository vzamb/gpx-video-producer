package com.gpxvideo.lib.strava

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.stravaDataStore: DataStore<Preferences> by preferencesDataStore(name = "strava_tokens")

data class StravaTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val athleteName: String,
    val athleteId: Long
) {
    val isExpired: Boolean get() = System.currentTimeMillis() / 1000 >= expiresAt
}

@Singleton
class StravaTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val KEY_EXPIRES_AT = longPreferencesKey("expires_at")
        val KEY_ATHLETE_NAME = stringPreferencesKey("athlete_name")
        val KEY_ATHLETE_ID = longPreferencesKey("athlete_id")
    }

    val tokens: Flow<StravaTokens?> = context.stravaDataStore.data.map { prefs ->
        val accessToken = prefs[KEY_ACCESS_TOKEN] ?: return@map null
        val refreshToken = prefs[KEY_REFRESH_TOKEN] ?: return@map null
        StravaTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = prefs[KEY_EXPIRES_AT] ?: 0L,
            athleteName = prefs[KEY_ATHLETE_NAME] ?: "Strava Athlete",
            athleteId = prefs[KEY_ATHLETE_ID] ?: 0L
        )
    }

    val isLinked: Flow<Boolean> = tokens.map { it != null }

    suspend fun getTokens(): StravaTokens? = tokens.first()

    suspend fun saveTokens(response: StravaTokenResponse) {
        context.stravaDataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = response.accessToken
            prefs[KEY_REFRESH_TOKEN] = response.refreshToken
            prefs[KEY_EXPIRES_AT] = response.expiresAt
            response.athlete?.let { athlete ->
                prefs[KEY_ATHLETE_NAME] = athlete.displayName
                prefs[KEY_ATHLETE_ID] = athlete.id
            }
        }
    }

    suspend fun updateAccessToken(accessToken: String, expiresAt: Long) {
        context.stravaDataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_EXPIRES_AT] = expiresAt
        }
    }

    suspend fun clear() {
        context.stravaDataStore.edit { it.clear() }
    }
}
