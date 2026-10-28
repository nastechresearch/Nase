package app.nase.companion.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
              "aliases": ["gallery"]
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

    private fun registry(seed: String? = seedJson, apps: List<Pair<String, String>> = installed): AppRegistry =
        AppRegistry.from(InstalledApps { apps }, seed)

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
    fun accentsCasePunctuationAndSpacingAreFolded() {
        val uber = registry().resolve("  ÜBER-EATS!  ") as AppResolution.Resolved
        assertEquals("com.ubercab.eats", uber.match.packageName)
        assertEquals(MatchSource.ALIAS, uber.match.source)

        val cafe = registry().resolve("CAFÉ") as AppResolution.Resolved
        assertEquals("com.example.cafe", cafe.match.packageName)
        assertEquals(MatchSource.LABEL, cafe.match.source)
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
    }

    @Test
    fun aBlankNameNeverDumpsInstalledApps() {
        val result = registry().resolve("   ")
        assertTrue(result is AppResolution.Unknown)
        assertTrue(result.candidates.isEmpty())
    }
}
