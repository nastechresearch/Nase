package app.nase.companion.recap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExplanationTest {
    @Test
    fun keepsEveryInputExactlyAsGiven() {
        val bounds = Bounds(left = 10, top = 20, right = 110, bottom = 220)
        val point = TapPoint(60.5, 120.25)
        val why = ActionExplanation(
            action = "tap",
            label = "Send",
            strategy = MatchStrategy.ContainsLabel,
            bounds = bounds,
            point = point,
            confidence = 0.62,
            candidates = 3,
            index = 1,
        )

        assertEquals("tap", why.action)
        assertEquals("Send", why.label)
        assertEquals(MatchStrategy.ContainsLabel, why.strategy)
        assertEquals(bounds, why.bounds)
        assertEquals(point, why.point)
        assertEquals(0.62, why.confidence!!, 0.0)
        assertEquals(3, why.candidates)
        assertEquals(1, why.index)
        assertTrue(why.ambiguous)
        assertEquals(why, why.copy())
    }

    @Test
    fun defaultsDescribeASingleUnscoredTarget() {
        val why = ActionExplanation("tap", "OK", MatchStrategy.ExactLabel)

        assertNull(why.bounds)
        assertNull(why.point)
        assertNull(why.confidence)
        assertNull(why.index)
        assertEquals(0, why.candidates)
        assertFalse(why.ambiguous)
    }

    @Test
    fun doesNotTrimOrRewriteTheLabel() {
        val why = ActionExplanation("tap", "  Send  ", MatchStrategy.CoordinateFallback)

        assertEquals("  Send  ", why.label)
        assertEquals("", ActionExplanation("tap", "", MatchStrategy.Index).label)
    }

    @Test
    fun ambiguityStartsOnlyAboveOneCandidate() {
        assertFalse(ActionExplanation("tap", "x", MatchStrategy.ExactLabel, candidates = 0).ambiguous)
        assertFalse(ActionExplanation("tap", "x", MatchStrategy.ExactLabel, candidates = 1).ambiguous)
        assertTrue(ActionExplanation("tap", "x", MatchStrategy.Index, candidates = 2).ambiguous)
        assertTrue(ActionExplanation("tap", "x", MatchStrategy.Index, candidates = 9, index = 8).ambiguous)
    }

    @Test
    fun boundsReportSizeAndCentre() {
        val even = Bounds(10, 20, 110, 220)

        assertEquals(100, even.width)
        assertEquals(200, even.height)
        assertEquals(60, even.centerX)
        assertEquals(120, even.centerY)
    }

    @Test
    fun invertedOrFlatBoundsNeverReportNegativeSize() {
        val inverted = Bounds(100, 100, 10, 5)

        assertEquals(0, inverted.width)
        assertEquals(0, inverted.height)
        assertEquals(100, inverted.centerX)
        assertEquals(100, inverted.centerY)
    }

    @Test
    fun everyMatchStrategyIsUsable() {
        val seen = mutableSetOf<MatchStrategy>()
        for (strategy in MatchStrategy.entries) {
            val why = ActionExplanation("tap", "x", strategy)
            assertEquals(strategy, why.strategy)
            seen += why.strategy
        }
        assertEquals(MatchStrategy.entries.toSet(), seen)
    }

    @Test
    fun copyChangesOneVerdictWithoutTouchingTheOriginal() {
        val original = ActionExplanation("tap", "Buy", MatchStrategy.ExactLabel, confidence = 0.9)
        val replayed = original.copy(strategy = MatchStrategy.CoordinateFallback, confidence = null)

        assertEquals(MatchStrategy.ExactLabel, original.strategy)
        assertEquals(0.9, original.confidence!!, 0.0)
        assertEquals(MatchStrategy.CoordinateFallback, replayed.strategy)
        assertNull(replayed.confidence)
        assertEquals(original.label, replayed.label)
    }
}
