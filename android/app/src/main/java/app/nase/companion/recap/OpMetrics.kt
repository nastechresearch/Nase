package app.nase.companion.recap

/**
 * In-memory latency and outcome counters for the actions Nase runs, keyed by the
 * tool name the brain called ("tap", "swipe", "type"…). Nothing is persisted: it
 * exists so a status or debug payload can say "taps usually take 380ms and one in
 * ten fails" after the fact, without keeping a history.
 *
 * Memory is bounded twice over. Each op retains at most [RECENT_LIMIT] durations
 * in one fixed [LongArray], and at most [MAX_OPS] distinct op names are tracked.
 * Once a cap is reached an old duration is overwritten, or an unseen op name is
 * ignored — the object cannot grow with uptime.
 *
 * Concurrency: every entry point synchronizes on a private lock. [record] is a
 * short critical section (an increment plus one array write), so it is cheap
 * enough for the action path; [snapshot] copies everything under the lock and
 * returns values the caller can hold without seeing later mutation.
 */
object OpMetrics {
    /** Durations retained per op. Latency stats are computed over just this window. */
    const val RECENT_LIMIT = 64

    /** Distinct op names tracked. Beyond this, [record] ignores new names. */
    const val MAX_OPS = 32

    /**
     * One op's counters and the latency window they describe. [minMs], [medianMs],
     * and [maxMs] cover [recent] only — keeping a lifetime median would mean
     * keeping every duration, which is exactly what the ring buffer avoids.
     */
    data class Stats(
        val count: Long,
        val ok: Long,
        val failed: Long,
        val minMs: Long,
        val medianMs: Long,
        val maxMs: Long,
        /** Durations in the order recorded, oldest first; at most [RECENT_LIMIT]. */
        val recent: List<Long>,
    ) {
        val empty: Boolean get() = count == 0L

        /** ok / count, or 0.0 before anything was recorded. */
        val successRate: Double get() = if (count == 0L) 0.0 else ok.toDouble() / count

        /**
         * The [p] percentile (0.0..1.0) of [recent], interpolating between the two
         * samples that bracket the position — so the median of an even window is
         * the mean of its middle two. 0 for an empty window; [p] is clamped.
         */
        fun percentile(p: Double): Long = percentileOf(recent, p)
    }

    /** A copy of every tracked op, safe to embed in a payload after the lock is released. */
    data class Snapshot(
        /** Durations retained per op at the time of the copy (always [RECENT_LIMIT]). */
        val window: Int,
        val ops: Map<String, Stats>,
    ) {
        val empty: Boolean get() = ops.isEmpty()

        fun of(op: String): Stats? = ops[op]
    }

    private class Row {
        var count = 0L
        var ok = 0L
        var failed = 0L
        private val ring = LongArray(RECENT_LIMIT)
        private var next = 0
        var size = 0
            private set

        /** Overwrites the oldest duration once the ring is full. */
        fun add(ok: Boolean, durationMs: Long) {
            count++
            if (ok) this.ok++ else failed++
            ring[next] = durationMs
            next = (next + 1) % RECENT_LIMIT
            if (size < RECENT_LIMIT) size++
        }

        /** The retained durations, oldest first. */
        fun recent(): List<Long> = List(size) { i ->
            // While filling, indices 0..size-1 are already in order; once full,
            // next points at the oldest slot.
            ring[if (size < RECENT_LIMIT) i else (next + i) % RECENT_LIMIT]
        }
    }

    private val lock = Any()
    private val rows = LinkedHashMap<String, Row>()

    /**
     * Record one finished run of [op]. [durationMs] is clamped at 0, so a clock
     * that stepped backwards cannot yield a negative latency. A blank [op] is
     * ignored, as is an unseen name once [MAX_OPS] are being tracked.
     */
    fun record(op: String, ok: Boolean, durationMs: Long) = synchronized(lock) {
        if (op.isBlank()) return
        val row = rows[op] ?: run {
            if (rows.size >= MAX_OPS) return
            Row().also { rows[op] = it }
        }
        row.add(ok, durationMs.coerceAtLeast(0L))
    }

    /** A copy of [op]'s stats, or null when it was never recorded. */
    fun stats(op: String): Stats? = synchronized(lock) { rows[op]?.stats() }

    /** A copy of everything, or an empty snapshot before the first record. */
    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(RECENT_LIMIT, rows.mapValues { it.value.stats() })
    }

    /** Forget every counter and duration, e.g. when the owner asks for a clean slate. */
    fun reset() = synchronized(lock) {
        rows.clear()
    }

    private fun Row.stats(): Stats {
        val recent = recent()
        return Stats(
            count = count,
            ok = ok,
            failed = failed,
            minMs = recent.minOrNull() ?: 0L,
            medianMs = percentileOf(recent, 0.5),
            maxMs = recent.maxOrNull() ?: 0L,
            recent = recent,
        )
    }
}

/**
 * Linear-interpolated percentile (R-7, the common stats default): the result sits
 * between the samples that bracket [p] * (n - 1). Durations are non-negative, so
 * rounding to the nearest millisecond with +0.5 is exact enough and keeps the
 * helper import-free. Returns 0 for an empty list.
 */
internal fun percentileOf(samples: List<Long>, p: Double): Long {
    if (samples.isEmpty()) return 0L
    val sorted = samples.sorted()
    val pos = p.coerceIn(0.0, 1.0) * (sorted.size - 1)
    val lo = pos.toInt()
    if (lo >= sorted.size - 1) return sorted.last()
    val weight = pos - lo
    return (sorted[lo] * (1.0 - weight) + sorted[lo + 1] * weight + 0.5).toLong()
}
