package app.nase.companion.display

/** Where a resolved package's name came from. */
enum class MatchSource(val wire: String) {
    EXACT_PACKAGE("exact"),
    ALIAS("alias"),
    LABEL("label"),
}

/** One app the registry can name, plus the curated metadata a caller may need. */
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
