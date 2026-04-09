package com.gpxvideo.lib.strava

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StravaAuth @Inject constructor(
    private val httpClient: OkHttpClient,
    private val tokenStore: StravaTokenStore
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun buildAuthUri(): Uri {
        return Uri.parse(StravaConfig.AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", StravaConfig.CLIENT_ID)
            .appendQueryParameter("redirect_uri", StravaConfig.REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("approval_prompt", "auto")
            .appendQueryParameter("scope", StravaConfig.SCOPE)
            .build()
    }

    suspend fun exchangeCode(code: String): Result<StravaTokenResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", StravaConfig.CLIENT_ID)
                .add("client_secret", StravaConfig.CLIENT_SECRET)
                .add("code", code)
                .add("grant_type", "authorization_code")
                .build()

            val request = Request.Builder()
                .url(StravaConfig.TOKEN_URL)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw IllegalStateException("Empty response body")

            if (!response.isSuccessful) {
                throw IllegalStateException("Token exchange failed (${response.code}): $responseBody")
            }

            val tokenResponse = json.decodeFromString<StravaTokenResponse>(responseBody)
            tokenStore.saveTokens(tokenResponse)
            tokenResponse
        }
    }

    suspend fun refreshTokenIfNeeded(): String? = withContext(Dispatchers.IO) {
        val tokens = tokenStore.getTokens() ?: return@withContext null

        if (!tokens.isExpired) return@withContext tokens.accessToken

        val body = FormBody.Builder()
            .add("client_id", StravaConfig.CLIENT_ID)
            .add("client_secret", StravaConfig.CLIENT_SECRET)
            .add("refresh_token", tokens.refreshToken)
            .add("grant_type", "refresh_token")
            .build()

        val request = Request.Builder()
            .url(StravaConfig.TOKEN_URL)
            .post(body)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) return@withContext null

            val tokenResponse = json.decodeFromString<StravaTokenResponse>(responseBody)
            tokenStore.updateAccessToken(tokenResponse.accessToken, tokenResponse.expiresAt)
            tokenResponse.accessToken
        } catch (e: Exception) {
            null
        }
    }

    suspend fun disconnect() {
        tokenStore.clear()
    }
}
