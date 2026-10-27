package app.nase.companion.recap

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OpMetricsTest {
    @Before
    fun setUp() = OpMetrics.reset()

    @After
    fun tearDown() = OpMetrics.reset()

    @Test
    fun countsOutcomesPerOpWithoutMixingOps() {
        OpMetrics.record("tap", true, 10)
        OpMetrics.record("tap", true, 20)
        OpMetrics.record("tap", false, 30)
        OpMetrics.record("swipe", true, 50)

        val tap = OpMetrics.stats("tap")!!
        assertEquals(3L, tap.count)
        assertEquals(2L, tap.ok)
        assertEquals(1L, tap.failed)
        assertEquals(10L, tap.minMs)
        assertEquals(20L, tap.medianMs)
        assertEquals(30L, tap.maxMs)
        assertEquals(2.0 / 3.0, tap.successRate, 1e-9)

        val swipe = OpMetrics.stats("swipe")!!
        assertEquals(1L, swipe.count)
        assertEquals(50L, swipe.medianMs)
    }

    @Test
    fun medianOfAnEvenWindowAveragesTheMiddleTwo() {
        listOf(10L, 20L, 30L, 40L).forEach { OpMetrics.record("tap", true, it) }
        assertEquals(25L, OpMetrics.stats("tap")!!.medianMs)
    }

    @Test
    fun medianOfAnOddWindowIsTheMiddleSample() {
        listOf(10L, 20L, 100L).forEach { OpMetrics.record("tap", true, it) }
        val tap = OpMetrics.stats("tap")!!
        assertEquals(20L, tap.medianMs)
        assertEquals(10L, tap.minMs)
        assertEquals(100L, tap.maxMs)
    }

    @Test
    fun percentileInterpolatesAndSortsTheWindow() {
        // Deliberately out of order: the stats must sort before slicing.
        listOf(40L, 10L, 30L, 20L).forEach { OpMetrics.record("tap", true, it) }
        val tap = OpMetrics.stats("tap")!!
        assertEquals(10L, tap.percentile(0.0))
        assertEquals(18L, tap.percentile(0.25)) // 10 + 0.75 * (20 - 10) = 17.5, rounded
        assertEquals(25L, tap.percentile(0.5))
        assertEquals(33L, tap.percentile(0.75)) // 30 + 0.25 * (40 - 30) = 32.5, rounded
        assertEquals(40L, tap.percentile(1.0))
        // Out-of-range p is clamped rather than throwing.
        assertEquals(10L, tap.percentile(-1.0))
        assertEquals(40L, tap.percentile(2.0))
    }

    @Test
    fun ringBufferKeepsOnlyTheMostRecentDurations() {
        val total = OpMetrics.RECENT_LIMIT + 10
        for (i in 1..total) OpMetrics.record("tap", true, i.toLong())

        val tap = OpMetrics.stats("tap")!!
        assertEquals(total.toLong(), tap.count)
        assertEquals(OpMetrics.RECENT_LIMIT, tap.recent.size)
        assertEquals(11L, tap.recent.first())
        assertEquals(total.toLong(), tap.recent.last())
        // The latency stats describe the retained window, so the aged-out 1ms sample is gone.
        assertEquals(11L, tap.minMs)
        assertEquals(total.toLong(), tap.maxMs)
        assertEquals(43L, tap.medianMs) // middle of 11..74
    }

    @Test
    fun zeroLatencyIsRecordedNotTreatedAsMissing() {
        OpMetrics.record("tap", true, 0)
        OpMetrics.record("tap", false, 0)

        val tap = OpMetrics.stats("tap")!!
        assertEquals(2L, tap.count)
        assertEquals(1L, tap.ok)
        assertEquals(1L, tap.failed)
        assertEquals(0L, tap.minMs)
        assertEquals(0L, tap.medianMs)
        assertEquals(0L, tap.maxMs)
        assertEquals(listOf(0L, 0L), tap.recent)
    }

    @Test
    fun aNegativeDurationIsClampedToZero() {
        OpMetrics.record("tap", true, -25)
        val tap = OpMetrics.stats("tap")!!
        assertEquals(0L, tap.minMs)
        assertEquals(0L, tap.maxMs)
        assertEquals(listOf(0L), tap.recent)
    }

    @Test
    fun countersCannotGoNegativeEvenUnderFailures() {
        repeat(1_000) { OpMetrics.record("tap", false, 5) }
        val tap = OpMetrics.stats("tap")!!
        assertEquals(1_000L, tap.failed)
        assertEquals(0L, tap.ok)
        assertTrue(tap.ok >= 0 && tap.failed >= 0 && tap.count >= 0)
        assertEquals(0.0, tap.successRate, 0.0)
    }

    @Test
    fun emptyAndUnknownCasesAreQuiet() {
        assertNull(OpMetrics.stats("never-ran"))
        assertTrue(OpMetrics.snapshot().empty)

        val empty = OpMetrics.Stats(0L, 0L, 0L, 0L, 0L, 0L, emptyList())
        assertTrue(empty.empty)
        assertEquals(0L, empty.percentile(0.5))
        assertEquals(0.0, empty.successRate, 0.0)

        OpMetrics.record("  ", true, 1)
        assertTrue(OpMetrics.snapshot().empty)
    }

    @Test
    fun snapshotIsACopyThatLaterRecordsCannotChange() {
        OpMetrics.record("tap", true, 10)
        val before = OpMetrics.snapshot()
        OpMetrics.record("tap", false, 1_000)

        assertEquals(1L, before.of("tap")!!.count)
        assertEquals(1L, before.of("tap")!!.ok)
        assertEquals(listOf(10L), before.of("tap")!!.recent)
        assertEquals(OpMetrics.RECENT_LIMIT, before.window)

        val after = OpMetrics.stats("tap")!!
        assertEquals(2L, after.count)
        assertEquals(2, after.recent.size)
    }

    @Test
    fun distinctOpNamesAreCapped() {
        repeat(OpMetrics.MAX_OPS + 5) { OpMetrics.record("op$it", true, 1) }

        val snapshot = OpMetrics.snapshot()
        assertEquals(OpMetrics.MAX_OPS, snapshot.ops.size)
        assertEquals(OpMetrics.RECENT_LIMIT, snapshot.window)
        // Insertion order decides who is kept: the first MAX_OPS names, no more.
        assertTrue(snapshot.ops.containsKey("op0"))
        assertTrue(snapshot.ops.containsKey("op${OpMetrics.MAX_OPS - 1}"))
        assertFalse(snapshot.ops.containsKey("op${OpMetrics.MAX_OPS}"))
    }

    @Test
    fun resetForgetsEverythingIncludingCounts() {
        OpMetrics.record("tap", true, 10)
        assertNotNull(OpMetrics.stats("tap"))

        OpMetrics.reset()

        assertNull(OpMetrics.stats("tap"))
        assertTrue(OpMetrics.snapshot().empty)
    }

    @Test
    fun concurrentRecordsAreNotLost() {
        val threads = 4
        val perThread = 250
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)

        repeat(threads) {
            Thread {
                start.await()
                repeat(perThread) { OpMetrics.record("tap", true, 1) }
                done.countDown()
            }.start()
        }
        start.countDown()

        assertTrue("worker threads did not finish", done.await(10, TimeUnit.SECONDS))
        val tap = OpMetrics.stats("tap")!!
        assertEquals((threads * perThread).toLong(), tap.count)
        assertEquals(tap.count, tap.ok + tap.failed)
        assertEquals(OpMetrics.RECENT_LIMIT, tap.recent.size)
    }
}
