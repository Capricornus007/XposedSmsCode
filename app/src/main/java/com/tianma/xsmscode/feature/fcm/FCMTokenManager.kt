package com.tianma.xsmscode.feature.fcm

import android.content.Context
import com.google.auth.oauth2.GoogleCredentials
import com.tianma.xsmscode.common.utils.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Manages OAuth 2.0 Access Tokens for FCM v1 API
 */
object FCMTokenManager {
    private const val TAG = "FCMTokenManager"
    private const val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
    
    // Token cache with 55-minute validity (Google tokens expire in 1 hour)
    private var cachedToken: String? = null
    private var tokenExpiryMs: Long = 0
    private val mutex = Mutex()
    
    /**
     * Get a valid Access Token from service account JSON.
     * Refreshes if expired or missing.
     * Thread-safe with mutex lock.
     */
    suspend fun getAccessToken(context: Context, serviceAccountJson: String): Result<String> {
        return try {
            mutex.withLock {
                val currentTime = System.currentTimeMillis()
                
                // Return cached token if still valid
                if (cachedToken != null && currentTime < tokenExpiryMs) {
                    XLog.d(TAG, "Using cached access token")
                    return@withLock Result.success(cachedToken!!)
                }
                
                // Refresh token
                XLog.i(TAG, "Refreshing access token...")
                val newToken = refreshToken(serviceAccountJson)
                
                cachedToken = newToken
                tokenExpiryMs = currentTime + 55 * 60 * 1000 // 55 minutes
                
                XLog.d(TAG, "Access token refreshed successfully")
                Result.success(newToken)
            }
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to get access token", e)
            Result.failure(e)
        }
    }
    
    /**
     * Extract project ID from service account JSON
     */
    fun extractProjectId(serviceAccountJson: String): String? {
        return try {
            val json = JSONObject(serviceAccountJson)
            json.getString("project_id")
        } catch (e: Exception) {
            XLog.e(TAG, "Failed to extract project ID", e)
            null
        }
    }
    
    /**
     * Refresh the access token using Google Auth library
     */
    private suspend fun refreshToken(serviceAccountJson: String): String {
        return withContext(Dispatchers.IO) {
            val inputStream = ByteArrayInputStream(serviceAccountJson.toByteArray(Charsets.UTF_8))
            val credentials = GoogleCredentials.fromStream(inputStream)
                .createScoped(listOf(SCOPE))
            
            credentials.refreshIfExpired()
            credentials.accessToken.tokenValue
        }
    }
    
    /**
     * Clear cached token (for testing or manual refresh)
     */
    suspend fun clearCache() {
        mutex.withLock {
            XLog.d(TAG, "Clearing token cache")
            cachedToken = null
            tokenExpiryMs = 0
        }
    }
}
