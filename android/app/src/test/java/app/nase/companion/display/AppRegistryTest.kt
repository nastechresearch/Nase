package app.nase.companion.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppRegistryTest {
    private val seedJson = """
        {
          "schema": 1,
          "apps": [
            {
              "package": "com.ubercab.eats",
              "label": "Uber Eats",
              "aliases": ["ubereats", "uber eats", "food delivery"],
              "deeplinks": ["ubereats://"],
              "hiddenDisplay": "bounces",
              "safetyTags": ["payment"]
            },
            {
              "package": "com.ubercab.rider",
              "label": "Uber",
              "aliases": ["uber ride", "ride"],
              "hiddenDisplay": "ok",
              "safetyTags": ["location", "payment"]
            },
            {
              "package": "com.google.android.apps.photos",
              "label": "Google Photos",
              "aliases": ["gallery"],
              "deeplinks": ["photos://"],
              "hiddenDisplay": "unknown",
              "safetyTags": ["media"]
            },
            {
              "package": "com.instagram.android",
              "label": "Instagram",
              "aliases": ["insta", "ig"]
            },
            {
              "package": "com.example.shortcut",
              "label": "Shortcut",
              "aliases": ["com.ubercab.eats", "com.missing.app"]
            }
          ]
        }
    """.trimIndent()

    private val installed = listOf(
        "com.ubercab.eats" to "Uber Eats",
        "com.ubercab.rider" to "Uber",
        "com.google.android.apps.photos" to "Google Photos",
        "com.google.android.apps.photos.scan" to "Photo Scanner",
        "com.instagram.android" to "Instagram",
        "com.spotify.music" to "Spotify",
        "com.example.cafe" to "Café",
        "com.example.shortcut" to "Shortcut",
        "com.example.notes.one" to "Notes One",
        "com.example.notes.two" to "Notes Two",
        "com.example.notes.three" to "Notes Three",
        "com.example.notes.four" to "Notes Four",
        "com.example.notes.five" to "Notes Five",
        "com.example.notes.six" to "Notes Six",
    )

    private val browsers = listOf(
        "com.example.browser.alpha" to "Browser Alpha",
        "com.example.browser.beta" to "Browser Beta",
    )

    private fun registry(seed: String? = seedJson, apps: List<Pair<String, String>> = installed): AppRegistry =
        AppRegistry.from(InstalledApps { apps }, seed)

    /** A fixed fake: proves the registry only depends on the injected interface. */
    private class FakeLaunchHistory(vararg recent: String) : LaunchHistory {
        private val entries = recent.toMutableList()

        override fun recent(): List<String> = entries.toList()

        override fun record(packageName: String) {
            entries.remove(packageName)
            entries.add(0, packageName)
        }
    }

    @Test
    fun anExactPackageWinsOverAnAliasForAnotherApp() {
        val hit = registry().resolve("com.ubercab.eats") as AppResolution.Resolved
        assertEquals("com.ubercab.eats", hit.match.packageName)
        assertEquals(MatchSource.EXACT_PACKAGE, hit.match.source)
    }

    @Test
    fun aMissingPackageIsNotReinterpretedAsAnotherAppsAlias() {
        // Shortcut carries a package-shaped alias, but a package query must keep
        // today's app_not_found path instead of silently opening Shortcut.
        assertTrue(registry().resolve("com.missing.app") is AppResolution.Unknown)
    }

    @Test
    fun aCuratedAliasResolvesAndCarriesItsMetadata() {
        val hit = registry().resolve("food delivery") as AppResolution.Resolved
        assertEquals("com.ubercab.eats", hit.match.packageName)
        assertEquals("Uber Eats", hit.match.label)
        assertEquals(MatchSource.ALIAS, hit.match.source)
        assertEquals("bounces", hit.match.hiddenDisplay)
        assertEquals(listOf("payment"), hit.match.safetyTags)
        assertEquals(listOf("ubereats://"), hit.match.deeplinks)
    }

    @Test
    fun anExactPackageMatchCarriesSeedMetadata() {
        val hit = registry().resolve("com.ubercab.eats") as AppResolution.Resolved
        assertEquals("bounces", hit.match.hiddenDisplay)
        assertEquals(listOf("payment"), hit.match.safetyTags)
        assertEquals(listOf("ubereats://"), hit.match.deeplinks)
    }

    @Test
    fun hiddenDisplayIsPassedThroughVerbatim() {
        // The seed vocabulary is trusted|bounces|unknown; the registry does not
        // validate or rewrite it, so an unrecognised value must survive unchanged.
        val hit = registry().resolve("Uber ride") as AppResolution.Resolved
        assertEquals("ok", hit.match.hiddenDisplay)
        assertEquals(listOf("location", "payment"), hit.match.safetyTags)
    }

    @Test
    fun everyAmbiguousCandidateCarriesItsOwnAnnotations() {
        val result = registry().resolve("photo") as AppResolution.Ambiguous
        val photos = result.candidates.first { it.packageName == "com.google.android.apps.photos" }
        assertEquals("unknown", photos.hiddenDisplay)
        assertEquals(listOf("media"), photos.safetyTags)
        assertEquals(listOf("photos://"), photos.deeplinks)

        val scanner = result.candidates.first { it.packageName == "com.google.android.apps.photos.scan" }
        assertNull(scanner.hiddenDisplay)
        assertTrue(scanner.safetyTags.isEmpty())
        assertTrue(scanner.deeplinks.isEmpty())
    }

    @Test
    fun aSeedEntryForAnUninstalledAppNeverResolves() {
        // The seed only annotates: metadata for a package PackageManager did not
        // list must not create a match, even when the alias looks plausible.
        val seed = """
            {"schema":1,"apps":[{
              "package":"com.example.absent","label":"Absent","aliases":["ghost"],
              "deeplinks":["ghost://"],"hiddenDisplay":"trusted","safetyTags":["payment"]
            }]}
        """.trimIndent()
        val result = registry(seed = seed).resolve("ghost")
        assertTrue(result is AppResolution.Unknown)
        assertTrue(result.candidates.none { it.packageName == "com.example.absent" })
    }

    @Test
    fun accentsCasePunctuationAndSpacingAreFolded() {
        val uber = registry().resolve("  ÜBER-EATS!  ") as AppResolution.Resolved
        assertEquals("com.ubercab.eats", uber.match.packageName)
        assertEquals(MatchSource.ALIAS, uber.match.source)

        val cafe = registry().resolve("CAFÉ") as AppResolution.Resolved
        assertEquals("com.example.cafe", cafe.match.packageName)
        assertEquals(MatchSource.LABEL, cafe.match.source)
    }

    @Test
    fun latinLettersThatNfdDoesNotDecomposeAreAlsoFolded() {
        val lookup = AppRegistry.from(
            InstalledApps { listOf("com.example.lodz" to "Łódź Transit") },
            seedJson = null,
        )
        val hit = lookup.resolve("lodz transit") as AppResolution.Resolved
        assertEquals("com.example.lodz", hit.match.packageName)
        assertEquals(MatchSource.LABEL, hit.match.source)
    }

    @Test
    fun labelsWithPunctuationAndEmojiMatchOnTheirWords() {
        val apps = listOf(
            "com.example.notes" to "Notes 📝",
            "com.example.mcdonalds" to "McDonald's",
        )
        val lookup = AppRegistry.from(InstalledApps { apps }, seedJson = null)

        assertEquals(
            "com.example.notes",
            (lookup.resolve("notes") as AppResolution.Resolved).match.packageName,
        )
        assertEquals(
            "com.example.notes",
            (lookup.resolve("notes 📝") as AppResolution.Resolved).match.packageName,
        )
        assertEquals(
            "com.example.mcdonalds",
            (lookup.resolve("mcdonalds") as AppResolution.Resolved).match.packageName,
        )
        assertEquals(
            "com.example.mcdonalds",
            (lookup.resolve("McDonald's") as AppResolution.Resolved).match.packageName,
        )
    }

    @Test
    fun aPunctuationOrEmojiOnlyQueryNeverDumpsInstalledApps() {
        val result = registry().resolve("📝 !!!")
        assertTrue(result is AppResolution.Unknown)
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun turkishDottedAndDotlessIAreFolded() {
        val apps = listOf(
            "com.example.istanbul" to "İstanbul Rehberi",
            "com.example.isik" to "Işık",
        )
        val lookup = AppRegistry.from(InstalledApps { apps }, seedJson = null)

        // İ (U+0130) decomposes to I + combining dot, so it meets "istanbul".
        assertEquals(
            "com.example.istanbul",
            (lookup.resolve("istanbul rehberi") as AppResolution.Resolved).match.packageName,
        )
        // Işık and the dotless-ı query both fold to the same "isik" form.
        assertEquals(
            "com.example.isik",
            (lookup.resolve("isik") as AppResolution.Resolved).match.packageName,
        )
        assertEquals(
            "com.example.isik",
            (lookup.resolve("ışık") as AppResolution.Resolved).match.packageName,
        )
    }

    @Test
    fun matchingIsStableUnderATurkishDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val apps = listOf(
                "com.instagram.android" to "Instagram",
                "com.example.ifsa" to "Ifşa",
            )
            val lookup = AppRegistry.from(InstalledApps { apps }, seedJson = null)

            assertEquals(
                "com.instagram.android",
                (lookup.resolve("INSTAGRAM") as AppResolution.Resolved).match.packageName,
            )
            assertEquals(
                "com.example.ifsa",
                (lookup.resolve("IFŞA") as AppResolution.Resolved).match.packageName,
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun anAliasPrefixRanksAboveLabels() {
        val hit = registry().resolve("food del") as AppResolution.Resolved
        assertEquals("com.ubercab.eats", hit.match.packageName)
        assertEquals(MatchSource.ALIAS, hit.match.source)
    }

    @Test
    fun anAmbiguousNameReturnsCandidatesAndNeverGuesses() {
        val result = registry().resolve("photo")
        assertTrue(result is AppResolution.Ambiguous)
        val ambiguous = result as AppResolution.Ambiguous
        assertEquals(2, ambiguous.candidates.size)
        assertEquals(
            setOf("com.google.android.apps.photos", "com.google.android.apps.photos.scan"),
            ambiguous.candidates.map { it.packageName }.toSet(),
        )
    }

    @Test
    fun launchHistoryOrdersTiedCandidatesButStillReturnsAllOfThem() {
        val cold = AppRegistry.from(InstalledApps { browsers }, seedJson = null)
        val coldResult = cold.resolve("browser") as AppResolution.Ambiguous
        assertEquals(
            listOf("com.example.browser.alpha", "com.example.browser.beta"),
            coldResult.candidates.map { it.packageName },
        )

        val warm = AppRegistry.from(
            InstalledApps { browsers },
            seedJson = null,
            history = FakeLaunchHistory("com.example.browser.beta"),
        )
        val warmResult = warm.resolve("browser") as AppResolution.Ambiguous
        // Recency only orders the tie-break; it must not collapse the choice.
        assertEquals(2, warmResult.candidates.size)
        assertEquals("com.example.browser.beta", warmResult.candidates.first().packageName)
    }

    @Test
    fun recordLaunchFeedsTheSameOrderingAndIsReadLive() {
        val history = InMemoryLaunchHistory()
        val lookup = AppRegistry.from(InstalledApps { browsers }, seedJson = null, history = history)

        val cold = lookup.resolve("browser") as AppResolution.Ambiguous
        assertEquals("com.example.browser.alpha", cold.candidates.first().packageName)

        lookup.recordLaunch("com.example.browser.beta")

        val warm = lookup.resolve("browser") as AppResolution.Ambiguous
        assertEquals(2, warm.candidates.size)
        assertEquals("com.example.browser.beta", warm.candidates.first().packageName)
        assertEquals(listOf("com.example.browser.beta"), history.recent())
    }

    @Test
    fun launchHistoryDoesNotOverrideAMorePreciseMatch() {
        val apps = listOf(
            "com.example.food" to "Food Delivery",
            "com.example.shop" to "Shop",
        )
        val seed = """
            {"schema":1,"apps":[{"package":"com.example.shop","aliases":["food delivery"]}]}
        """.trimIndent()
        val lookup = AppRegistry.from(
            InstalledApps { apps },
            seedJson = seed,
            history = FakeLaunchHistory("com.example.food"),
        )
        val hit = lookup.resolve("food delivery") as AppResolution.Resolved
        assertEquals("com.example.shop", hit.match.packageName)
        assertEquals(MatchSource.ALIAS, hit.match.source)
    }

    @Test
    fun inMemoryHistoryIsBoundedDeduplicatedAndMostRecentFirst() {
        val history = InMemoryLaunchHistory(capacity = 3)
        history.record("a")
        history.record("b")
        history.record("c")
        assertEquals(listOf("c", "b", "a"), history.recent())

        history.record("b")
        assertEquals(listOf("b", "c", "a"), history.recent())

        history.record("d")
        assertEquals(listOf("d", "b", "c"), history.recent())
        assertEquals(3, history.recent().size)

        history.record("   ")
        assertEquals(listOf("d", "b", "c"), history.recent())

        assertThrows(IllegalArgumentException::class.java) { InMemoryLaunchHistory(0) }
    }

    @Test
    fun invalidateRefreshesTheMergedIndexAfterInstallAndRemoval() {
        val apps = mutableListOf("com.example.alpha" to "Alpha")
        val lookup = AppRegistry.from(InstalledApps { apps.toList() }, seedJson = null)

        assertTrue(lookup.resolve("alpha") is AppResolution.Resolved)
        assertTrue(lookup.resolve("beta") is AppResolution.Unknown)

        apps += "com.example.beta" to "Beta"
        // The cached index is intentionally stale until the service refreshes it.
        assertTrue(lookup.resolve("beta") is AppResolution.Unknown)

        lookup.invalidate()
        assertTrue(lookup.resolve("beta") is AppResolution.Resolved)

        apps.removeAll { it.first == "com.example.alpha" }
        assertTrue(lookup.resolve("alpha") is AppResolution.Resolved)

        lookup.invalidate()
        lookup.invalidate()
        assertTrue(lookup.resolve("alpha") is AppResolution.Unknown)
    }

    @Test
    fun candidatesAreCappedAtFive() {
        val result = registry().resolve("notes") as AppResolution.Ambiguous
        assertEquals(AppRegistry.MAX_CANDIDATES, result.candidates.size)
        assertTrue(result.candidates.all { it.label.startsWith("Notes") })
    }

    @Test
    fun anUnknownNameReturnsRankedNearMisses() {
        val result = registry().resolve("instgram")
        assertTrue(result is AppResolution.Unknown)
        val unknown = result as AppResolution.Unknown
        assertEquals("com.instagram.android", unknown.candidates.first().packageName)
        assertTrue(unknown.candidates.size <= AppRegistry.MAX_CANDIDATES)
    }

    @Test
    fun aMissingSeedStillResolvesInstalledLabels() {
        val hit = registry(seed = null).resolve("spotify") as AppResolution.Resolved
        assertEquals("com.spotify.music", hit.match.packageName)
        assertEquals(MatchSource.LABEL, hit.match.source)
        assertTrue(registry(seed = null).resolve("food delivery") is AppResolution.Unknown)
    }

    @Test
    fun aCorruptSeedIsIgnored() {
        val broken = registry(seed = "{ \"schema\": 1, \"apps\": [")
        assertTrue(broken.resolve("food delivery") is AppResolution.Unknown)
        val hit = broken.resolve("spotify") as AppResolution.Resolved
        assertEquals("com.spotify.music", hit.match.packageName)
    }

    @Test
    fun aNewerSeedSchemaIsIgnored() {
        val future = """{"schema":2,"apps":[{"package":"com.spotify.music","aliases":["tunes"]}]}"""
        val lookup = registry(seed = future)
        assertTrue(lookup.resolve("tunes") is AppResolution.Unknown)
        val hit = lookup.resolve("spotify") as AppResolution.Resolved
        assertEquals("com.spotify.music", hit.match.packageName)
        assertEquals(MatchSource.LABEL, hit.match.source)
    }

    @Test
    fun anInstalledAppWithNoSeedEntryResolvesByItsLiveLabel() {
        val hit = registry().resolve("spotify") as AppResolution.Resolved
        assertEquals("com.spotify.music", hit.match.packageName)
        assertEquals("Spotify", hit.match.label)
        assertEquals(MatchSource.LABEL, hit.match.source)
        assertNull(hit.match.hiddenDisplay)
        assertTrue(hit.match.safetyTags.isEmpty())
        assertTrue(hit.match.deeplinks.isEmpty())
    }

    @Test
    fun aBlankNameNeverDumpsInstalledApps() {
        val result = registry().resolve("   ")
        assertTrue(result is AppResolution.Unknown)
        assertTrue(result.candidates.isEmpty())
    }
}
