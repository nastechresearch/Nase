package app.nase.companion.session

import app.nase.companion.recap.OpMetrics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OpsHealthTest {
    @Before
    fun setUp() = OpMetrics.reset()

    @After
    fun tearDown() = OpMetrics.reset()

    @Test
    fun snapshotBecomesTheHealthOpsPayload() {
        OpMetrics.record("tap", true, 10)
        OpMetrics.record("tap", false, 30)

        val json = opsJson(OpMetrics.snapshot())

        assertEquals(OpMetrics.RECENT_LIMIT, json.getInt("window"))
        val tap = json.getJSONObject("ops").getJSONObject("tap")
        assertEquals(2L, tap.getLong("count"))
        assertEquals(1L, tap.getLong("ok"))
        assertEquals(1L, tap.getLong("failed"))
        assertEquals(10L, tap.getLong("minMs"))
        assertEquals(30L, tap.getLong("maxMs"))
        assertEquals(0.5, tap.getDouble("successRate"), 0.0)
        assertEquals(2, tap.getJSONArray("recent").length())
    }

    @Test
    fun noOpsMeansAnEmptyObjectNotAMissingKey() {
        val json = opsJson(OpMetrics.snapshot())

        assertEquals(OpMetrics.RECENT_LIMIT, json.getInt("window"))
        assertEquals(0, json.getJSONObject("ops").length())
    }
}
