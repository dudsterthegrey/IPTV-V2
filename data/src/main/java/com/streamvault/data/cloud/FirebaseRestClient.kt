package com.streamvault.data.cloud

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Firebase over its REST APIs (Fire TV has no Google Play Services for the Android SDK):
 * anonymous Authentication for a token, and Cloud Firestore documents under
 * `syncGroups/{group}/history/{item}`. Configured by the project id and web API key.
 */
@Singleton
class FirebaseRestClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    @param:Named("firebaseProjectId") private val projectId: String,
    @param:Named("firebaseApiKey") private val apiKey: String
) {
    val isConfigured: Boolean get() = projectId.isNotBlank() && apiKey.isNotBlank()

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val tokenMutex = Mutex()
    private var idToken: String? = null
    private var idTokenExpiresAt = 0L

    /** Writes (creates or replaces) one history document of [group]. */
    suspend fun putHistoryDocument(group: String, documentId: String, fields: JSONObject) {
        val url = "$documentsBase/syncGroups/$group/history/$documentId"
        val body = JSONObject().put("fields", fields).toString().toRequestBody(JSON)
        execute(Request.Builder().url(url).patch(body))
    }

    /** History documents of [group] whose `updatedAt` is after [sinceMillis], oldest first. */
    suspend fun historyUpdatedSince(group: String, sinceMillis: Long, limit: Int = 500): List<JSONObject> {
        val query = JSONObject().put(
            "structuredQuery",
            JSONObject()
                .put("from", JSONArray().put(JSONObject().put("collectionId", "history")))
                .put(
                    "where",
                    JSONObject().put(
                        "fieldFilter",
                        JSONObject()
                            .put("field", JSONObject().put("fieldPath", "updatedAt"))
                            .put("op", "GREATER_THAN")
                            .put("value", JSONObject().put("integerValue", sinceMillis.toString()))
                    )
                )
                .put("orderBy", JSONArray().put(JSONObject().put("field", JSONObject().put("fieldPath", "updatedAt"))))
                .put("limit", limit)
        )
        val url = "$documentsBase/syncGroups/$group:runQuery"
        val response = execute(Request.Builder().url(url).post(query.toString().toRequestBody(JSON)))
        val results = JSONArray(response)
        return (0 until results.length()).mapNotNull { index ->
            results.optJSONObject(index)?.optJSONObject("document")?.optJSONObject("fields")
        }
    }

    private val documentsBase get() = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"

    private suspend fun execute(builder: Request.Builder): String = withContext(Dispatchers.IO) {
        val token = token()
        okHttpClient.newCall(builder.header("Authorization", "Bearer $token").build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                tokenMutex.withLock { idToken = null }
            }
            if (!response.isSuccessful) throw IOException("Firestore HTTP ${response.code}")
            text
        }
    }

    /** A Firebase ID token, signing in anonymously the first time and refreshing it hourly. */
    private suspend fun token(): String = tokenMutex.withLock {
        check(isConfigured) { "Cloud sync is not configured in this build" }
        val now = System.currentTimeMillis()
        idToken?.takeIf { now < idTokenExpiresAt }?.let { return it }
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        val json = if (refreshToken != null) {
            runCatching {
                postForm(
                    "https://securetoken.googleapis.com/v1/token?key=$apiKey",
                    FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", refreshToken).build()
                )
            }.getOrNull()?.let { refreshed ->
                JSONObject()
                    .put("idToken", refreshed.optString("id_token"))
                    .put("refreshToken", refreshed.optString("refresh_token"))
                    .put("expiresIn", refreshed.optString("expires_in"))
            }
        } else {
            null
        } ?: postJson(
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey",
            JSONObject().put("returnSecureToken", true)
        )
        val token = json.optString("idToken").takeIf { it.isNotBlank() } ?: throw IOException("Firebase sign-in failed")
        prefs.edit().putString(KEY_REFRESH_TOKEN, json.optString("refreshToken")).apply()
        val lifetimeSeconds = json.optString("expiresIn").toLongOrNull() ?: 3600L
        idToken = token
        idTokenExpiresAt = now + (lifetimeSeconds - 120L).coerceAtLeast(60L) * 1000L
        token
    }

    private suspend fun postJson(url: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        okHttpClient.newCall(Request.Builder().url(url).post(body.toString().toRequestBody(JSON)).build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Firebase auth HTTP ${response.code}")
            JSONObject(text)
        }
    }

    private suspend fun postForm(url: String, body: FormBody): JSONObject = withContext(Dispatchers.IO) {
        okHttpClient.newCall(Request.Builder().url(url).post(body).build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Firebase token HTTP ${response.code}")
            JSONObject(text)
        }
    }

    private companion object {
        const val PREFS_NAME = "firebase_rest"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
