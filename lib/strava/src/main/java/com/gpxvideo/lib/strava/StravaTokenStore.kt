package com.gpxvideo.lib.strava

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

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
        const val PREF_NAME = "strava_tokens"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_ATHLETE_NAME = "athlete_name"
        const val KEY_ATHLETE_ID = "athlete_id"
    }

    private val prefs: SharedPreferences by lazy { openEncryptedPrefs() }

    private val _tokens = MutableStateFlow(readTokens())
    val tokens: Flow<StravaTokens?> = _tokens.asStateFlow()
    val isLinked: Flow<Boolean> = tokens.map { it != null }

    fun getTokens(): StravaTokens? = _tokens.value

    fun saveTokens(response: StravaTokenResponse) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, response.accessToken)
            putString(KEY_REFRESH_TOKEN, response.refreshToken)
            putLong(KEY_EXPIRES_AT, response.expiresAt)
            response.athlete?.let { athlete ->
                putString(KEY_ATHLETE_NAME, athlete.displayName)
                putLong(KEY_ATHLETE_ID, athlete.id)
            }
        }.apply()
        _tokens.value = readTokens()
    }

    fun updateAccessToken(accessToken: String, expiresAt: Long) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
        _tokens.value = readTokens()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _tokens.value = null
    }

    private fun readTokens(): StravaTokens? {
        val access = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        return StravaTokens(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L),
            athleteName = prefs.getString(KEY_ATHLETE_NAME, null) ?: "Strava Athlete",
            athleteId = prefs.getLong(KEY_ATHLETE_ID, 0L)
        )
    }

    private fun openEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return try {
            EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (t: Throwable) {
            // If the keystore is corrupted (key rotation, restored backup, etc.)
            // wipe the file and re-create rather than crash.
            context.deleteSharedPreferences(PREF_NAME)
            EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
