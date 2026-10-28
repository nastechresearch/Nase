package app.nase.companion.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAutonomyTest {
    private val notes = "com.example.notes"
    private val listed = AutonomySettings.of(notes)

    private fun verdict(
        packageName: String?,
        op: String,
        label: String?,
        settings: AutonomySettings = listed,
    ): AutonomyVerdict = AppAutonomy.autonomyVerdict(packageName, op, label, settings)

    @Test
    fun emptySettingsConfirmEverything() {
        assertFalse(AutonomySettings.EMPTY.allowWithoutConfirm(notes))
        assertTrue(AutonomySettings.EMPTY.allowedPackages.isEmpty())

        val v = verdict(notes, "tap", "New note", AutonomySettings.EMPTY)
        assertTrue(v.requiresConfirm)
        assertEquals(AutonomyOutcome.CONFIRM, v.outcome)
        assertEquals(AppAutonomy.Reason.APP_NOT_ALLOWED, v.reason)
    }

    @Test
    fun theDefaultArgumentIsAlsoConservative() {
        val v = AppAutonomy.autonomyVerdict(notes, "tap", "New note")
        assertTrue(v.requiresConfirm)
        assertEquals(AppAutonomy.Reason.APP_NOT_ALLOWED, v.reason)
    }

    @Test
    fun aListedPackageStillConfirmsEveryRiskyLabel() {
        val risky = mapOf(
            "Send" to "send",
            "Send money" to "payment",
            "Pay $10" to "payment",
            "Delete" to "delete",
            "Erase all notes" to "delete",
            "Call mom" to "call",
            "Post" to "post",
            "Transfer" to "transfer",
            "Buy now" to "purchase",
            "Change password" to "security",
        )
        for ((label, reason) in risky) {
            val v = verdict(notes, "tap", label)
            assertTrue("$label must still confirm", v.requiresConfirm)
            assertEquals(label, reason, v.reason)
        }
    }

    @Test
    fun aListedPackageCannotWaiveAnOpenAppSecurityLabel() {
        val v = verdict(notes, "open_app", "Change password")
        assertTrue(v.requiresConfirm)
        assertEquals("security", v.reason)
    }

    @Test
    fun aListedPackageAllowsOnlyOrdinaryKnownOps() {
        val tap = verdict(notes, "tap", "New note")
        assertEquals(AutonomyOutcome.ALLOW, tap.outcome)
        assertEquals(AppAutonomy.Reason.APP_ALLOWED, tap.reason)
        assertTrue(tap.allows)
        assertFalse(tap.requiresConfirm)

        // A blank label (an icon or coordinate tap) is not risky by itself.
        val icon = verdict(notes, "tap", null)
        assertEquals(AutonomyOutcome.ALLOW, icon.outcome)

        val swipe = verdict(notes, "swipe", null)
        assertEquals(AutonomyOutcome.ALLOW, swipe.outcome)
    }

    @Test
    fun anUnlistedOpConfirmsEvenForAListedPackage() {
        val unknown = verdict(notes, "book_flight", "Continue")
        assertTrue(unknown.requiresConfirm)
        assertEquals("unknown_op", unknown.reason)

        // The blank op is missing from the table too.
        val blank = verdict(notes, "", "Continue")
        assertTrue(blank.requiresConfirm)
        assertEquals("unknown_op", blank.reason)
    }

    @Test
    fun aBlankOrNullPackageAlwaysConfirms() {
        for (pkg in listOf(null, "", "   ")) {
            val v = verdict(pkg, "tap", "New note")
            assertTrue("package $pkg", v.requiresConfirm)
            assertEquals(AppAutonomy.Reason.UNKNOWN_PACKAGE, v.reason)
            assertFalse(listed.allowWithoutConfirm(pkg))
        }
    }

    @Test
    fun anUnlistedPackageConfirmsEvenForABenignOp() {
        val v = verdict("com.example.other", "tap", "New note")
        assertTrue(v.requiresConfirm)
        assertEquals(AppAutonomy.Reason.APP_NOT_ALLOWED, v.reason)
    }

    @Test
    fun matchingIsExactWithNoPrefixOrWildcardExemptions() {
        // A listed package does not carry over to a sibling or debug build.
        assertTrue(verdict("com.example.notes.debug", "tap", "New note").requiresConfirm)

        val wildcards = AutonomySettings(setOf("*", "com.example.*", "com.example."))
        assertFalse(wildcards.allowWithoutConfirm("*"))
        assertFalse(wildcards.allowWithoutConfirm("com.example.notes"))
        assertFalse(wildcards.allowWithoutConfirm("com.example"))
        assertTrue(verdict("com.example.notes", "tap", "New note", wildcards).requiresConfirm)
    }

    @Test
    fun settingsCopyTheListSoLaterMutationCannotGrantAutonomy() {
        val mutable = mutableSetOf(notes)
        val settings = AutonomySettings(mutable)
        mutable.add("com.example.other")

        assertTrue(settings.allowWithoutConfirm(notes))
        assertFalse(settings.allowWithoutConfirm("com.example.other"))
    }

    @Test
    fun aListedPaymentAppStillConfirmsTaps() {
        // SafetyPolicy asks before every tap in a known money app; listing it
        // for autonomy cannot remove that question.
        val venmo = AutonomySettings.of("com.venmo")
        val tap = verdict("com.venmo", "tap", "Friends", venmo)
        assertTrue(tap.requiresConfirm)
        assertEquals(AppAutonomy.Reason.PAYMENT_APP, tap.reason)
    }

    @Test
    fun allowingOneAppDoesNotAllowAnother() {
        val onlyNotes = AutonomySettings.of(notes)
        assertEquals(AutonomyOutcome.ALLOW, verdict(notes, "tap", "New note", onlyNotes).outcome)
        assertTrue(verdict("com.example.chat", "tap", "New note", onlyNotes).requiresConfirm)
    }
}
