package app.nase.companion.display

import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Which installed apps to search. Production feeds this from PackageManager;
 * tests pass a fixed list, so resolution needs no Android runtime.
 */
fun interface InstalledApps {
    /** Launchable package names with their live labels, one entry per package. */
    fun listed(): List<Pair<String, String>>
}

/** One curated entry from assets/apps_seed.json. Metadata only: never proof that an app exists. */
data class SeedApp(
    val packageName: String,
    val label: String,
    val aliases: List<String>,
    val deeplinks: List<String>,
    val hiddenDisplay: String?,
    val safetyTags: List<String>,
)

/** The parsed seed. An empty seed is valid and means "resolve from installed labels only". */
data class Seed(val apps: List<SeedApp>) {
    val byPackage: Map<String, SeedApp> = apps.associateBy { it.packageName }

    companion object {
        val EMPTY = Seed(emptyList())
    }
}

/**
 * Resolves one spoken or typed app name against the installed apps on this phone.
 * Curated metadata can rank a result, but only PackageManager decides that an app
 * exists. Resolution returns data; every open still goes through the normal
 * consent, display and call-screen guards.
 */
class AppRegistry(
    private val installed: InstalledApps,
    private val seed: Seed = Seed.EMPTY,
) {
    private val seedByPackage: Map<String, SeedApp> = seed.byPackage

    fun resolve(query: String): AppResolution {
        val said = Forms.of(query)
        if (said.spaced.isEmpty()) return AppResolution.Unknown(query, emptyList())

        val apps = indexed()
        // Today's contract: an exact package string opens that package, untouched
        // by any alias that happens to spell the same thing.
        apps.firstOrNull { it.packageName == query.trim() }?.let {
            return AppResolution.Resolved(query, it.match(MatchSource.EXACT_PACKAGE))
        }
        // A package-shaped query is never reinterpreted as someone else's alias:
        // if it isn't installed, the caller keeps today's app_not_found path.
        val packageShaped = looksLikePackage(query)
        val ranked = apps.mapNotNull { app ->
            val rank = when {
                app.packageForms.spaced == said.spaced -> RANK_PACKAGE
                packageShaped -> return@mapNotNull null
                app.aliasForms.any { it.exact(said) } -> RANK_ALIAS
                app.aliasForms.any { it.startsWith(said) } -> RANK_ALIAS_PREFIX
                app.labelForms.any { it.contains(said) } -> RANK_LABEL
                else -> return@mapNotNull null
            }
            Ranked(app, rank, exactLabel = app.labelForms.any { it.exact(said) })
        }

        val best = ranked.minOfOrNull { it.rank }
        if (best != null) {
            val tied = ranked.filter { it.rank == best }.sortedWith(CANDIDATE_ORDER)
            if (tied.size == 1) {
                return AppResolution.Resolved(query, tied.first().app.match(sourceFor(best)))
            }
            return AppResolution.Ambiguous(
                query,
                tied.take(MAX_CANDIDATES).map { it.app.match(sourceFor(it.rank)) },
            )
        }

        // Nothing matched by name. Offer a few ranked near misses, but never
        // turn one into a guess: this stays Unknown.
        val close = apps
            .map { app -> app to fuzzyScore(app, said) }
            .sortedWith(
                compareByDescending<Pair<IndexedApp, Int>> { it.second }
                    .thenBy { it.first.label.lowercase(Locale.ROOT) }
                    .thenBy { it.first.packageName },
            )
            .take(MAX_CANDIDATES)
            .map { (app, _) -> app.match(MatchSource.LABEL) }
        return AppResolution.Unknown(query, close)
    }

    private fun indexed(): List<IndexedApp> {
        val out = ArrayList<IndexedApp>()
        val seen = HashSet<String>()
        for ((rawPackage, rawLabel) in installed.listed()) {
            val packageName = rawPackage.trim()
            if (packageName.isEmpty() || !seen.add(packageName)) continue
            val meta = seedByPackage[packageName]
            val liveLabel = rawLabel.trim().ifEmpty {
                packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
            val label = meta?.label?.takeIf { it.isNotBlank() } ?: liveLabel
            val labels = listOfNotNull(meta?.label?.takeIf { it.isNotBlank() }, liveLabel.takeIf { it.isNotBlank() })
                .map { Forms.of(it) }
                .filter { it.spaced.isNotEmpty() }
                .distinct()
            val aliases = meta?.aliases ?: emptyList()
            out += IndexedApp(
                packageName = packageName,
                label = label,
                aliases = aliases,
                deeplinks = meta?.deeplinks ?: emptyList(),
                hiddenDisplay = meta?.hiddenDisplay,
                safetyTags = meta?.safetyTags ?: emptyList(),
                packageForms = Forms.of(packageName),
                labelForms = labels,
                aliasForms = aliases.map { Forms.of(it) }.filter { it.spaced.isNotEmpty() }.distinct(),
            )
        }
        return out
    }

    private fun sourceFor(rank: Int): MatchSource = when (rank) {
        RANK_PACKAGE -> MatchSource.EXACT_PACKAGE
        RANK_ALIAS, RANK_ALIAS_PREFIX -> MatchSource.ALIAS
        else -> MatchSource.LABEL
    }

    private fun fuzzyScore(app: IndexedApp, said: Forms): Int {
        var best = 0
        val texts = ArrayList<String>(app.labelForms.size + app.aliasForms.size + 1)
        for (form in app.labelForms) texts += form.spaced
        for (form in app.aliasForms) texts += form.spaced
        texts += app.packageForms.spaced
        for (text in texts) {
            val prefix = commonPrefix(text, said.spaced)
            val shared = said.spaced.split(' ').count { token ->
                token.isNotEmpty() && text.split(' ').contains(token)
            }
            best = maxOf(best, prefix * 2 + shared * 3)
        }
        return best
    }

    private fun commonPrefix(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        var i = 0
        while (i < limit && a[i] == b[i]) i++
        return i
    }

    private fun looksLikePackage(query: String): Boolean {
        val trimmed = query.trim()
        return trimmed.contains('.') && trimmed.none { it.isWhitespace() } && PACKAGE_NAME.matches(trimmed)
    }

    private class IndexedApp(
        val packageName: String,
        val label: String,
        val aliases: List<String>,
        val deeplinks: List<String>,
        val hiddenDisplay: String?,
        val safetyTags: List<String>,
        val packageForms: Forms,
        val labelForms: List<Forms>,
        val aliasForms: List<Forms>,
    ) {
        fun match(source: MatchSource): AppMatch = AppMatch(
            packageName = packageName,
            label = label,
            source = source,
            hiddenDisplay = hiddenDisplay,
            safetyTags = safetyTags,
            aliases = aliases,
            deeplinks = deeplinks,
        )
    }

    private data class Ranked(val app: IndexedApp, val rank: Int, val exactLabel: Boolean)

    /** Lowercase, accents folded, punctuation and spacing collapsed (and a space-free twin). */
    private data class Forms(val spaced: String, val compact: String) {
        fun exact(other: Forms): Boolean = spaced == other.spaced || compact == other.compact
        fun startsWith(other: Forms): Boolean = spaced.startsWith(other.spaced) || compact.startsWith(other.compact)
        fun contains(other: Forms): Boolean = spaced.contains(other.spaced) || compact.contains(other.compact)

        companion object {
            private val MARKS = Regex("\\p{M}+")

            fun of(text: String): Forms {
                val folded = Normalizer.normalize(text, Normalizer.Form.NFD)
                    .replace(MARKS, "")
                    .lowercase(Locale.ROOT)
                val spaced = buildString(folded.length) {
                    var gap = false
                    for (ch in folded) {
                        if (ch.isLetterOrDigit()) {
                            if (gap && isNotEmpty()) append(' ')
                            gap = false
                            append(ch)
                        } else {
                            gap = true
                        }
                    }
                }
                return Forms(spaced, spaced.replace(" ", ""))
            }
        }
    }

    companion object {
        const val ASSET = "apps_seed.json"
        const val SCHEMA = 1
        const val MAX_CANDIDATES = 5

        private const val TAG = "AppRegistry"
        private const val RANK_PACKAGE = 0
        private const val RANK_ALIAS = 1
        private const val RANK_ALIAS_PREFIX = 2
        private const val RANK_LABEL = 3
        private val PACKAGE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")

        private val CANDIDATE_ORDER: Comparator<Ranked> =
            compareBy<Ranked> { it.rank }
                .thenByDescending { it.exactLabel }
                .thenBy { it.app.label.lowercase(Locale.ROOT) }
                .thenBy { it.app.packageName }

        @Volatile
        private var cachedSeed: Seed? = null
        private val seedWarned = AtomicBoolean(false)

        fun device(context: Context): AppRegistry =
            AppRegistry(PackageManagerApps(context.applicationContext), seedFor(context))

        /** Pure entry point for JVM tests: no Context, no assets. */
        fun from(installed: InstalledApps, seedJson: String? = null): AppRegistry =
            AppRegistry(installed, parseSeed(seedJson) ?: Seed.EMPTY)

        /** Null means missing, malformed, or a schema newer than this build understands. */
        fun parseSeed(json: String?): Seed? {
            return runCatching {
                if (json.isNullOrBlank()) return null
                val root = JSONObject(json)
                if (root.optInt("schema", -1) != SCHEMA) return null
                val rows = root.optJSONArray("apps") ?: return null
                val byPackage = LinkedHashMap<String, SeedApp>()
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val packageName = text(row, "package")
                    if (packageName.isEmpty()) continue
                    val entry = SeedApp(
                        packageName = packageName,
                        label = text(row, "label"),
                        aliases = strings(row.optJSONArray("aliases")),
                        deeplinks = strings(row.optJSONArray("deeplinks")),
                        hiddenDisplay = text(row, "hiddenDisplay").takeIf { it.isNotEmpty() },
                        safetyTags = strings(row.optJSONArray("safetyTags")),
                    )
                    byPackage[packageName] = byPackage[packageName]?.merge(entry) ?: entry
                }
                Seed(byPackage.values.toList())
            }.getOrNull()
        }

        private fun seedFor(context: Context): Seed {
            cachedSeed?.let { return it }
            synchronized(this) {
                cachedSeed?.let { return it }
                val parsed = parseSeed(readSeed(context))
                if (parsed == null) warnSeedOnce()
                val seed = parsed ?: Seed.EMPTY
                cachedSeed = seed
                return seed
            }
        }

        private fun readSeed(context: Context): String? = runCatching {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()

        private fun warnSeedOnce() {
            if (seedWarned.compareAndSet(false, true)) {
                Log.w(TAG, "$ASSET is missing or unusable; resolving apps by installed label only.")
            }
        }

        private fun text(row: JSONObject, key: String): String =
            if (row.isNull(key)) "" else row.optString(key, "").trim()

        private fun strings(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            val out = ArrayList<String>(array.length())
            for (i in 0 until array.length()) {
                if (array.isNull(i)) continue
                val value = array.optString(i, "").trim()
                if (value.isNotEmpty() && value !in out) out += value
            }
            return out
        }

        private fun SeedApp.merge(other: SeedApp): SeedApp = SeedApp(
            packageName = packageName,
            label = label.ifBlank { other.label },
            aliases = (aliases + other.aliases).distinct(),
            deeplinks = (deeplinks + other.deeplinks).distinct(),
            hiddenDisplay = hiddenDisplay ?: other.hiddenDisplay,
            safetyTags = (safetyTags + other.safetyTags).distinct(),
        )
    }
}

private class PackageManagerApps(private val context: Context) : InstalledApps {
    override fun listed(): List<Pair<String, String>> {
        val launchers = runCatching {
            context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0,
            )
        }.getOrDefault(emptyList())
        val out = ArrayList<Pair<String, String>>(launchers.size)
        val seen = HashSet<String>()
        for (info in launchers) {
            val packageName = info.activityInfo?.packageName ?: continue
            if (!seen.add(packageName)) continue
            val label = runCatching { info.loadLabel(context.packageManager).toString() }.getOrDefault("")
            out += packageName to label
        }
        return out
    }
}
