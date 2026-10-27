package app.nase.companion.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyTableTest {
    private fun confirmReason(target: TapTarget): String? =
        (SafetyPolicy.forTap(target) as? Verdict.Confirm)?.reason

    @Test
    fun riskyVerbsStillRequireConfirmation() {
        val risky = mapOf(
            "Send" to "send",
            "Send money" to "payment",
            "Pay $10" to "payment",
            "Checkout" to "payment",
            "Post" to "post",
            "Delete" to "delete",
            "Call" to "call",
            "Transfer" to "transfer",
            "Buy" to "purchase",
            "Place order" to "purchase",
            "Subscribe" to "purchase",
        )
        for ((label, reason) in risky) {
            assertEquals(label, reason, confirmReason(TapTarget(label)))
        }
    }

    @Test
    fun settingsAndNavigationActionsNeverConfirm() {
        for (op in listOf(
            "open_settings",
            "swipe",
            "long_press",
            "drag",
            "pinch",
            "key",
            "wait_idle",
            "speak",
            "remember",
            "done",
        )) {
            assertFalse(op, SafetyTable.forOp(op).confirm)
        }
        // Settings and plain navigation labels are not risky verbs.
        assertNull(SafetyTable.labelRuleFor("tap", "Settings"))
        assertNull(SafetyTable.labelRuleFor("tap", "Display"))
        assertEquals(Verdict.Allow, SafetyPolicy.forTap(TapTarget("Settings")))
        assertEquals(Verdict.Allow, SafetyPolicy.forTap(TapTarget("Display")))
        assertEquals(Verdict.Allow, SafetyPolicy.forTap(TapTarget("Search")))
        // Opening the Settings app is normal work, even with its security screen listed.
        assertEquals(Verdict.Allow, SafetyPolicy.forOpenApp("com.android.settings", "Settings"))
    }

    @Test
    fun anOpAbsentFromTheTableFallsBackToConfirm() {
        val unknown = SafetyTable.forOp("book_flight")
        assertTrue(unknown.confirm)
        assertEquals("unknown_op", unknown.reason)
    }
}
