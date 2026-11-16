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

/** A Firebase account error, with Firebase's code (e.g. EMAIL_EXISTS, INVALID_LOGIN_CREDENTIALS). */
class FirebaseAuthException(val code: String) : IOException("Firebase auth: $code")

class NotSignedInException : IOException("Not signed in")

/**
 * Firebase over its REST APIs (Fire TV has no Google Play Services for the Android SDK):
 * email/password Authentication, and Cloud Firestore documents under `users/{uid}/history/{item}`,
 * which the security rules let only that account read and write.
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

    /** The signed-in account's email, or null. */
    val signedInEmail: String? get() = prefs.getString(KEY_EMAIL, null)?.takeIf { prefs.contains(KEY_REFRESH_TOKEN) }

    suspend fun signIn(email: String, password: String) =
        authenticate("accounts:signInWithPassword", email, password)

    suspend fun createAccount(email: String, password: String) =
        authenticate("accounts:signUp", email, password)

    /**
     * Signs in with a Google ID token that a phone left at `pairings/{code}` (see firebase/public),
     * then deletes that pairing. Returns false while the phone has not finished yet.
     */
    suspend fun signInFromPairing(code: String): Boolean {
        check(isConfigured) { "Cloud sync is not configured in this build" }
        val fields = withContext(Dispatchers.IO) {
            okHttpClient.newCall(Request.Builder().url("$documentsBase/pairings/$code?key=$apiKey").get().build()).execute().use { response ->
                if (response.code == 404) return@withContext null
                if (!response.isSuccessful) throw IOException("Firestore HTTP ${response.code}")
                JSONObject(response.body?.string().orEmpty()).optJSONObject("fields")
            }
        } ?: return false
        val googleIdToken = fields.optJSONObject("idToken")?.optString("stringValue").orEmpty()
        val googleAccessToken = fields.optJSONObject("accessToken")?.optString("stringValue").orEmpty()
        if (googleIdToken.isBlank() && googleAccessToken.isBlank()) return false
        val postBody = buildString {
            if (googleIdToken.isNotBlank()) append("id_token=").append(googleIdToken).append('&')
            if (googleAccessToken.isNotBlank()) append("access_token=").append(googleAccessToken).append('&')
            append("providerId=google.com")
        }
        val json = postJson(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$apiKey",
            JSONObject().put("postBody", postBody).put("requestUri", "http://localhost")
                .put("returnSecureToken", true).put("returnIdpCredential", true)
        )
        storeSession(json, fallbackEmail = "")
        runCatching { execute(Request.Builder().url("$documentsBase/pairings/$code").delete()) }
        return true
    }

    /** Emails a password-reset link to [email]. */
    suspend fun sendPasswordReset(email: String) {
        postJson(
            "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$apiKey",
            JSONObject().put("requestType", "PASSWORD_RESET").put("email", email.trim())
        )
    }

    suspend fun signOut() = tokenMutex.withLock {
        prefs.edit().clear().apply()
        idToken = null
        idTokenExpiresAt = 0L
    }

    /** Writes (creates or replaces) one history document of the signed-in account. */
    suspend fun putHistoryDocument(documentId: String, fields: JSONObject) {
        val url = "$documentsBase/users/${uid()}/history/$documentId"
        val body = JSONObject().put("fields", fields).toString().toRequestBody(JSON)
        execute(Request.Builder().url(url).patch(body))
    }

    /** The account's history documents whose `updatedAt` is after [sinceMillis], oldest first. */
    suspend fun historyUpdatedSince(sinceMillis: Long, limit: Int = 500): List<JSONObject> {
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
        val url = "$documentsBase/users/${uid()}:runQuery"
        val response = execute(Request.Builder().url(url).post(query.toString().toRequestBody(JSON)))
        val results = JSONArray(response)
        return (0 until results.length()).mapNotNull { index ->
            results.optJSONObject(index)?.optJSONObject("document")?.optJSONObject("fields")
        }
    }

    /** The project's Firebase Hosting site, which serves the phone's Google sign-in page. */
    val hostingUrl: String get() = "https://$projectId.firebaseapp.com"

    private val documentsBase get() = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"

    private fun uid(): String = prefs.getString(KEY_UID, null) ?: throw NotSignedInException()

    private suspend fun authenticate(endpoint: String, email: String, password: String) {
        check(isConfigured) { "Cloud sync is not configured in this build" }
        val json = postJson(
            "https://identitytoolkit.googleapis.com/v1/$endpoint?key=$apiKey",
            JSONObject().put("email", email.trim()).put("password", password).put("returnSecureToken", true)
        )
        storeSession(json, fallbackEmail = email.trim())
    }

    private suspend fun storeSession(json: JSONObject, fallbackEmail: String) {
        tokenMutex.withLock {
            prefs.edit()
                .putString(KEY_REFRESH_TOKEN, json.optString("refreshToken"))
                .putString(KEY_UID, json.optString("localId"))
                .putString(KEY_EMAIL, json.optString("email").ifBlank { fallbackEmail })
                .apply()
            idToken = json.optString("idToken").takeIf { it.isNotBlank() }
            idTokenExpiresAt = System.currentTimeMillis() + tokenLifetimeMs(json.optString("expiresIn"))
        }
    }

    private suspend fun execute(builder: Request.Builder): String = withContext(Dispatchers.IO) {
        val token = token()
        okHttpClient.newCall(builder.header("Authorization", "Bearer $token").build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) {
                tokenMutex.withLock { idToken = null }
            }
            if (!response.isSuccessful) throw IOException("Firestore HTTP ${response.code}")
            text
        }
    }

    /** A Firebase ID token for the signed-in account, refreshed hourly. */
    private suspend fun token(): String = tokenMutex.withLock {
        check(isConfigured) { "Cloud sync is not configured in this build" }
        val now = System.currentTimeMillis()
        idToken?.takeIf { now < idTokenExpiresAt }?.let { return it }
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: throw NotSignedInException()
        val refreshed = postForm(
            "https://securetoken.googleapis.com/v1/token?key=$apiKey",
            FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", refreshToken).build()
        )
        val token = refreshed.optString("id_token").takeIf { it.isNotBlank() } ?: throw NotSignedInException()
        prefs.edit().putString(KEY_REFRESH_TOKEN, refreshed.optString("refresh_token")).apply()
        idToken = token
        idTokenExpiresAt = now + tokenLifetimeMs(refreshed.optString("expires_in"))
        token
    }

    private fun tokenLifetimeMs(expiresIn: String): Long =
        ((expiresIn.toLongOrNull() ?: 3600L) - 120L).coerceAtLeast(60L) * 1000L

    private suspend fun postJson(url: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        okHttpClient.newCall(Request.Builder().url(url).post(body.toString().toRequestBody(JSON)).build()).execute().use { response ->
            parseAuthResponse(response.isSuccessful, response.body?.string().orEmpty())
        }
    }

    private suspend fun postForm(url: String, body: FormBody): JSONObject = withContext(Dispatchers.IO) {
        okHttpClient.newCall(Request.Builder().url(url).post(body).build()).execute().use { response ->
            parseAuthResponse(response.isSuccessful, response.body?.string().orEmpty())
        }
    }

    private fun parseAuthResponse(successful: Boolean, text: String): JSONObject {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: JSONObject()
        if (!successful) {
            // e.g. "EMAIL_EXISTS", "INVALID_LOGIN_CREDENTIALS", "WEAK_PASSWORD : Password should be…"
            val code = json.optJSONObject("error")?.optString("message").orEmpty().substringBefore(' ').ifBlank { "UNKNOWN" }
            throw FirebaseAuthException(code)
        }
        return json
    }

    private companion object {
        const val PREFS_NAME = "firebase_rest"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_UID = "uid"
        const val KEY_EMAIL = "email"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
