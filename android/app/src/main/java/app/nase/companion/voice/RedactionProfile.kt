package app.nase.companion.voice

import java.util.Locale

/**
 * What to do with screenshots and UI-tree text from one app. Data only: this
 * profile tells a caller whether the screen must be handled as sensitive; it
 * never captures, stores, uploads, or replays anything itself.
 *
 * When [sensitive] is true, [persistAllowed] and [replayAllowed] are false:
 * treat a screenshot or UI tree like a password — do not write it to disk, do
 * not put it in a recap, and do not send it back to the model later.
 */
data class RedactionProfile(
    val packageName: String?,
    val sensitive: Boolean,
    val reason: String,
    /** Normalised (trimmed, lower-cased) safety tags the decision was based on. */
    val tags: Set<String> = emptySet(),
) {
    val screenshotsSensitive: Boolean get() = sensitive
    val uiTreeSensitive: Boolean get() = sensitive
    val persistAllowed: Boolean get() = !sensitive
    val replayAllowed: Boolean get() = !sensitive
}

/**
 * Decides whether an app's screenshots and UI tree are sensitive. Conservative
 * by default:
 * - A null, blank, or unclassified package is sensitive.
 * - A package with no tags is sensitive. There is no implicit safe list.
 * - Any protected tag (`payment`, `banking`, `health`, `authenticator`,
 *   `identity`, `private`, `secure`, …) makes the app sensitive.
 * - A package [MoneyScreens] already knows as a payment app is sensitive even
 *   if its tags are empty or claim otherwise.
 * - An unrecognised tag makes the app sensitive; unknown tags never grant an
 *   exemption.
 *
 * The only way to get a non-sensitive profile is to tag the app explicitly as
 * public via [SAFE_TAGS] ("public", "non_sensitive", "no_redaction"). Tags are
 * normalised to lower case and hyphens become underscores, so "Non-Sensitive"
 * is understood. Even that cannot override the payment-app check.
 */
object RedactionPolicy {
    object Reason {
        const val UNKNOWN_PACKAGE = "unknown_package"
        const val SENSITIVE_TAG = "sensitive_tag"
        const val PAYMENT_APP = "payment_app"
        const val EXPLICIT_SAFE_TAG = "explicit_safe_tag"
        const val UNKNOWN_TAG = "unknown_tag"
        const val UNCLASSIFIED = "unclassified"
    }

    /** Tags that always make a screen sensitive. Matching is exact after normalisation. */
    val PROTECTED_TAGS: Set<String> = setOf(
        "payment", "payments", "money", "wallet", "banking", "bank", "finance", "financial",
        "card", "credit_card", "creditcard", "checkout",
        "health", "medical", "hipaa", "patient",
        "authenticator", "auth", "2fa", "mfa", "otp", "one_time_password",
        "credentials", "password", "password_manager",
        "identity", "government", "passport", "id_document",
        "secure", "flag_secure", "private", "personal", "sensitive", "secret",
    )

    /** The only tags that can make a screen non-sensitive. Keep this list short. */
    val SAFE_TAGS: Set<String> = setOf(
        "public", "non_sensitive", "nonsensitive", "no_redaction", "redaction_none",
    )

    /**
     * The profile for [packageName] with its owner/app safety [safetyTags].
     * Passing no tags yields the conservative answer.
     */
    fun profileFor(
        packageName: String?,
        safetyTags: Collection<String> = emptySet(),
    ): RedactionProfile {
        val pkg = packageName?.trim()?.takeIf { it.isNotEmpty() }
        val tags = safetyTags
            .asSequence()
            .map { it.trim().lowercase(Locale.ROOT).replace('-', '_') }
            .filter { it.isNotEmpty() }
            .toSet()

        return when {
            pkg == null -> sensitive(null, Reason.UNKNOWN_PACKAGE, tags)
            tags.any { it in PROTECTED_TAGS } -> sensitive(pkg, Reason.SENSITIVE_TAG, tags)
            MoneyScreens.isKnownPackage(pkg) -> sensitive(pkg, Reason.PAYMENT_APP, tags)
            tags.isNotEmpty() && tags.all { it in SAFE_TAGS } ->
                RedactionProfile(pkg, sensitive = false, reason = Reason.EXPLICIT_SAFE_TAG, tags = tags)
            tags.isNotEmpty() -> sensitive(pkg, Reason.UNKNOWN_TAG, tags)
            else -> sensitive(pkg, Reason.UNCLASSIFIED, tags)
        }
    }

    /** Convenience for callers that only need the yes/no. */
    fun isSensitive(
        packageName: String?,
        safetyTags: Collection<String> = emptySet(),
    ): Boolean = profileFor(packageName, safetyTags).sensitive

    private fun sensitive(pkg: String?, reason: String, tags: Set<String>): RedactionProfile =
        RedactionProfile(pkg, sensitive = true, reason = reason, tags = tags)
}
