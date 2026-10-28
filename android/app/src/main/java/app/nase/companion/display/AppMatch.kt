package app.nase.companion.display

/** Where a resolved package's name came from. */
enum class MatchSource(val wire: String) {
    EXACT_PACKAGE("exact"),
    ALIAS("alias"),
    LABEL("label"),
}

/**
 * One app the registry can name, plus the curated metadata a caller may need.
 *
 * @property hiddenDisplay the seed's hint about this app on the hidden display:
 *   "trusted", "bounces" or "unknown" (passed through verbatim when the seed uses
 *   another value). Null means the seed said nothing; callers must not read null
 *   as "trusted".
 * @property safetyTags seed-declared concerns such as "payment" or "drm"; empty
 *   means the seed declared none.
 * @property deeplinks custom URI schemes the seed declares for this package. They
 *   are offered as data only; the registry never opens them.
 */
data class AppMatch(
    val packageName: String,
    val label: String,
    val source: MatchSource,
    val hiddenDisplay: String? = null,
    val safetyTags: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val deeplinks: List<String> = emptyList(),
)

/**
 * The outcome of looking one name up. Ambiguous and Unknown both carry a short,
 * ranked list so the caller can ask instead of guessing; neither opens anything.
 */
sealed class AppResolution {
    abstract val query: String
    abstract val candidates: List<AppMatch>

    data class Resolved(
        override val query: String,
        val match: AppMatch,
    ) : AppResolution() {
        override val candidates: List<AppMatch> get() = emptyList()
    }

    data class Ambiguous(
        override val query: String,
        override val candidates: List<AppMatch>,
    ) : AppResolution()

    data class Unknown(
        override val query: String,
        override val candidates: List<AppMatch>,
    ) : AppResolution()
}
