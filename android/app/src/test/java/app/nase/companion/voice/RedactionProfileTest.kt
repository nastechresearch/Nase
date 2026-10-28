package app.nase.companion.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactionProfileTest {
    private val notes = "com.example.notes"

    @Test
    fun anUnclassifiedPackageIsSensitive() {
        val profile = RedactionPolicy.profileFor(notes)
        assertTrue(profile.sensitive)
        assertEquals(RedactionPolicy.Reason.UNCLASSIFIED, profile.reason)
        assertTrue(profile.screenshotsSensitive)
        assertTrue(profile.uiTreeSensitive)
        assertFalse(profile.persistAllowed)
        assertFalse(profile.replayAllowed)
        assertTrue(profile.tags.isEmpty())
        assertTrue(RedactionPolicy.isSensitive(notes))
    }

    @Test
    fun aNullOrBlankPackageIsSensitive() {
        for (pkg in listOf(null, "", "   ")) {
            val profile = RedactionPolicy.profileFor(pkg)
            assertTrue("package $pkg", profile.sensitive)
            assertEquals(RedactionPolicy.Reason.UNKNOWN_PACKAGE, profile.reason)
            assertFalse(profile.persistAllowed)
            assertFalse(profile.replayAllowed)
        }
        // Tags do not make a missing package classifiable.
        assertEquals(
            RedactionPolicy.Reason.UNKNOWN_PACKAGE,
            RedactionPolicy.profileFor(null, setOf("public")).reason,
        )
    }

    @Test
    fun aProtectedTagIsSensitive() {
        for (tag in listOf("payment", "banking", "health", "authenticator", "identity", "private", "secure")) {
            val profile = RedactionPolicy.profileFor(notes, setOf(tag))
            assertTrue(tag, profile.sensitive)
            assertEquals(tag, RedactionPolicy.Reason.SENSITIVE_TAG, profile.reason)
            assertFalse(tag, profile.persistAllowed)
            assertFalse(tag, profile.replayAllowed)
        }
    }

    @Test
    fun tagsAreTrimmedLowerCasedAndHyphenNormalised() {
        val profile = RedactionPolicy.profileFor(notes, setOf("  PAYMENT  "))
        assertTrue(profile.sensitive)
        assertEquals(setOf("payment"), profile.tags)
        assertEquals(RedactionPolicy.Reason.SENSITIVE_TAG, profile.reason)

        val safe = RedactionPolicy.profileFor(notes, setOf("Non-Sensitive"))
        assertFalse(safe.sensitive)
        assertEquals(RedactionPolicy.Reason.EXPLICIT_SAFE_TAG, safe.reason)
    }

    @Test
    fun aKnownPaymentAppIsSensitiveWithoutTags() {
        for (pkg in listOf("com.venmo", "com.chase.sig.android", "com.squareup.cash")) {
            val profile = RedactionPolicy.profileFor(pkg)
            assertTrue(pkg, profile.sensitive)
            assertEquals(pkg, RedactionPolicy.Reason.PAYMENT_APP, profile.reason)
            assertFalse(profile.persistAllowed)
            assertFalse(profile.replayAllowed)
        }
        // Package matching trims outer whitespace.
        assertEquals(RedactionPolicy.Reason.PAYMENT_APP, RedactionPolicy.profileFor(" com.venmo ").reason)
    }

    @Test
    fun aSafeTagCannotOverrideThePaymentAppCheck() {
        val profile = RedactionPolicy.profileFor("com.venmo", setOf("public"))
        assertTrue(profile.sensitive)
        assertEquals(RedactionPolicy.Reason.PAYMENT_APP, profile.reason)
        assertFalse(profile.persistAllowed)
    }

    @Test
    fun anUnknownTagIsSensitiveRatherThanSafe() {
        val profile = RedactionPolicy.profileFor(notes, setOf("mystery_category"))
        assertTrue(profile.sensitive)
        assertEquals(RedactionPolicy.Reason.UNKNOWN_TAG, profile.reason)
        assertFalse(profile.persistAllowed)

        // One unknown tag is enough even beside an explicit safe tag.
        val mixed = RedactionPolicy.profileFor(notes, setOf("public", "mystery_category"))
        assertTrue(mixed.sensitive)
        assertEquals(RedactionPolicy.Reason.UNKNOWN_TAG, mixed.reason)
    }

    @Test
    fun descriptorStyleTagListsAreAccepted() {
        // AppRegistry exposes safetyTags as a List<String>; the policy takes any Collection.
        val payment = RedactionPolicy.profileFor("com.ubercab", listOf("payment"))
        assertTrue(payment.sensitive)
        assertEquals(RedactionPolicy.Reason.SENSITIVE_TAG, payment.reason)

        // An unknown descriptor tag (for example "drm") is sensitive, not safe.
        val drm = RedactionPolicy.profileFor("com.netflix.mediaclient", listOf("drm"))
        assertTrue(drm.sensitive)
        assertEquals(RedactionPolicy.Reason.UNKNOWN_TAG, drm.reason)
    }

    @Test
    fun onlyAnExplicitPublicTagGrantsANonSensitiveProfile() {
        for (tags in listOf(
            setOf("public"),
            setOf("non_sensitive"),
            setOf("nonsensitive"),
            setOf("no_redaction"),
            setOf("PUBLIC", "Non-Sensitive"),
        )) {
            val profile = RedactionPolicy.profileFor(notes, tags)
            assertFalse(tags.toString(), profile.sensitive)
            assertEquals(RedactionPolicy.Reason.EXPLICIT_SAFE_TAG, profile.reason)
            assertFalse(profile.screenshotsSensitive)
            assertFalse(profile.uiTreeSensitive)
            assertTrue(profile.persistAllowed)
            assertTrue(profile.replayAllowed)
        }
    }

    @Test
    fun anEmptyOrBlankTagSetIsNotAnExplicitSafeDecision() {
        for (tags in listOf(emptySet(), setOf(""), setOf("   "))) {
            val profile = RedactionPolicy.profileFor(notes, tags)
            assertTrue(tags.toString(), profile.sensitive)
            assertEquals(RedactionPolicy.Reason.UNCLASSIFIED, profile.reason)
        }
    }
}
