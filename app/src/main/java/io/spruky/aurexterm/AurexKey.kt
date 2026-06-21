package io.spruky.aurexterm

import android.content.Context

/**
 * The single shared credential. ONE key authenticates the app, axcode, and the
 * `term` pip package — the scheme is locked to `aurex_term_<32 lowercase hex>`.
 *
 * Keys are ALWAYS minted server-side (issue_key, bound to a verified account)
 * and handed back over the wire — this object never generates one. It owns
 * format validation and on-device persistence; verification/issuance live in
 * [KeyClient] so the backend contract stays in one place.
 */
object AurexKey {

    const val PREFIX = "aurex_term_"

    /** aurex_term_ followed by exactly 32 lowercase hex chars. */
    private val FORMAT = Regex("^${Regex.escape(PREFIX)}[0-9a-f]{32}$")

    private const val PREFS = "aurex_term"
    private const val KEY_FIELD = "key"
    private const val EMAIL_FIELD = "email"

    fun isValid(key: String?): Boolean =
        key != null && FORMAT.matches(key.trim())

    /** Persisted key, or null if the user hasn't signed in / pasted one yet. */
    fun load(ctx: Context): String? =
        prefs(ctx).getString(KEY_FIELD, null)?.takeIf { isValid(it) }

    /** Owner email saved alongside the key, if known. */
    fun loadEmail(ctx: Context): String? =
        prefs(ctx).getString(EMAIL_FIELD, null)?.takeIf { it.isNotBlank() }

    fun save(ctx: Context, key: String, email: String? = null) {
        prefs(ctx).edit().apply {
            putString(KEY_FIELD, key.trim())
            if (email != null) putString(EMAIL_FIELD, email.trim())
            apply()
        }
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
