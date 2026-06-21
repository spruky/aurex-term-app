package io.spruky.aurexterm

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the aurafarmer backend for key issuance and verification.
 *
 * Both flows hit ONE endpoint (newsclipt.py → aurafarmer_endpoint), switching on
 * an `action` field — the same contract the Go TUI (axcode) and the `term` pip
 * package use. The terminal key is ALWAYS minted server-side and bound to a
 * verified account; the client never generates it.
 *
 *   POST https://spruky.qzz.io/aurafarmer/endpoint
 *
 *   issue:  {"action":"issue_key","email":..,"password":..}
 *           ok 200 {"ok":true,"key":"aurex_term_<32hex>","email":..,"username":..}
 *           bad   {"ok":false,"error":".."}  (401 bad creds, 403 unverified)
 *
 *   verify: {"action":"verify_key","key":"aurex_term_<32hex>"}
 *           ok 200 {"ok":true,"email":..,"username":..}
 *           bad 401 {"ok":false,"error":"not authorized"}
 */
object KeyClient {

    private const val BASE = "https://spruky.qzz.io"
    private const val ENDPOINT = "/aurafarmer/endpoint"
    private const val TIMEOUT_MS = 12_000

    sealed class Result {
        /** active=true means the key is recognized; email is the owner when known. */
        data class Ok(val active: Boolean, val email: String? = null) : Result()
        data class Error(val message: String) : Result()
    }

    /** Issue (or fetch) the canonical terminal key from email+password. */
    sealed class IssueResult {
        data class Ok(val key: String, val email: String) : IssueResult()
        data class Error(val message: String) : IssueResult()
    }

    /**
     * Exchange [email]/[password] for the account's terminal key. Idempotent on
     * the server — returns the existing key if one was already minted. Network/IO
     * runs on the calling thread; call off the main thread.
     */
    fun issueKey(email: String, password: String): IssueResult {
        val body = JSONObject()
            .put("action", "issue_key")
            .put("email", email)
            .put("password", password)
            .toString()
        return try {
            val (code, text) = post(body)
            val json = runCatching { JSONObject(text) }.getOrNull()
            val ok = json?.optBoolean("ok", false) == true
            if (ok) {
                val key = json?.optString("key").orEmpty()
                if (AurexKey.isValid(key)) {
                    IssueResult.Ok(key, json?.optString("email").orEmpty())
                } else {
                    IssueResult.Error("Server returned no key.")
                }
            } else {
                IssueResult.Error(json?.optString("error").orEmpty().ifBlank { "Sign-in failed ($code)." })
            }
        } catch (e: Exception) {
            IssueResult.Error(e.message ?: "Network error.")
        }
    }

    /**
     * Verify [key] against the backend. Network/IO runs on the calling thread.
     */
    fun verify(key: String): Result {
        if (!AurexKey.isValid(key)) {
            return Result.Error("Bad key format.")
        }
        return try {
            val body = JSONObject()
                .put("action", "verify_key")
                .put("key", key)
                .toString()
            val (code, text) = post(body)
            when (code) {
                in 200..299 -> {
                    val json = JSONObject(text)
                    Result.Ok(json.optBoolean("ok", false), json.optString("email", null))
                }
                401, 403, 404 -> Result.Ok(false)   // key not recognized
                else -> Result.Error("Server returned $code.")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error.")
        }
    }

    /** POST a JSON [body] to the aurafarmer endpoint; return (status, responseText). */
    private fun post(body: String): Pair<Int, String> {
        val conn = (URL(BASE + ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "aurex-term-android/${BuildConfig.VERSION_NAME}")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText)
                ?: ""
            code to text
        } finally {
            conn.disconnect()
        }
    }
}
