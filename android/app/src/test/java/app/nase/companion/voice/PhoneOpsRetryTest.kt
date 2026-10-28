package app.nase.companion.voice

import app.nase.companion.display.ActionResult
import app.nase.companion.display.ScreenTarget
import app.nase.companion.recap.MatchStrategy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneOpsRetryTest {
    private fun acted(
        ok: Boolean = true,
        error: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) = ActionResult(ok, error, ScreenTarget("main", 0, 1080, 2400, null), fields)

    @Test
    fun retriesOnlyFailuresThatReportedNothingWasTouched() {
        listOf("accessibility_off", "screen_changed", "covered_by_popup", "call_ui_foreground", "background_empty")
            .forEach { assertTrue(it, PhoneOps.isTransientScreenFailure(it)) }
    }

    @Test
    fun gestureAndPermissionFailuresAreNeverRetried() {
        listOf(
            "tap_failed",
            "gesture_failed",
            "swipe_failed",
            "type_failed",
            "call_ui_timeout",
            "background_refused",
            "unknown_key",
            "unknown_settings",
            "ambiguous_app",
            "password_field",
            null,
        ).forEach { assertFalse(it ?: "null", PhoneOps.isTransientScreenFailure(it)) }
    }

    @Test
    fun backoffDoublesAndIsCapped() {
        assertEquals(200L, PhoneOps.retryDelayMs(1))
        assertEquals(400L, PhoneOps.retryDelayMs(2))
        assertEquals(800L, PhoneOps.retryDelayMs(3))
        assertEquals(PhoneOps.RETRY_MAX_MS, PhoneOps.retryDelayMs(4))
        assertEquals(PhoneOps.RETRY_MAX_MS, PhoneOps.retryDelayMs(30))
        assertEquals(2, PhoneOps.SCREEN_RETRIES)
    }

    @Test
    fun aCoordinateTapExplainsTheDeliveredPointAndWindow() {
        val acted = acted(
            fields = mapOf("x" to 300.0, "y" to 480.0, "via" to "gesture", "window" to "0,0,1080,2400"),
        )

        val why = PhoneOps.explain("tap", "Send", 0.5, 0.25, acted)

        assertEquals("tap", why.action)
        assertEquals("Send", why.label)
        assertEquals(MatchStrategy.CoordinateFallback, why.strategy)
        assertEquals(300.0, why.point!!.x, 0.0)
        assertEquals(480.0, why.point!!.y, 0.0)
        assertEquals(0, why.bounds!!.left)
        assertEquals(1080, why.bounds!!.right)
        assertEquals(2400, why.bounds!!.bottom)
        assertFalse(why.ambiguous)
    }

    @Test
    fun aLabelSearchIsReportedAsAnExactMatch() {
        val why = PhoneOps.explain("tap", "Send", 10.0, 20.0, acted(fields = mapOf("via" to "search")))

        assertEquals(MatchStrategy.ExactLabel, why.strategy)
        assertEquals(10.0, why.point!!.x, 0.0)
        assertEquals(20.0, why.point!!.y, 0.0)
        assertNull(why.bounds)
    }

    @Test
    fun explanationTravelsOnTheActionResultAsJson() {
        val base = acted(fields = mapOf("x" to 12.0, "y" to 34.0))
        val why = PhoneOps.explain("long_press", "Row", 1.0, 2.0, base)

        val explained = PhoneOps.withExplanation(base, why)
        val json = explained.fields["explanation"] as JSONObject

        assertEquals("long_press", json.getString("action"))
        assertEquals("Row", json.getString("label"))
        assertEquals("coordinate_fallback", json.getString("strategy"))
        assertEquals(12.0, json.getJSONObject("point").getDouble("x"), 0.0)
        assertFalse(json.getBoolean("ambiguous"))
    }
}
