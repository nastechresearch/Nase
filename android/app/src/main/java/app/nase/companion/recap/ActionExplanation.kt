package app.nase.companion.recap

/**
 * How the target of a tap was chosen, strongest evidence first. A unique exact
 * label is the pick we trust; raw coordinates are the one we cannot justify.
 * [wire] is the stable name for a status or debug payload.
 */
enum class MatchStrategy(val wire: String) {
    /** A control's own text or content description equalled the requested label. */
    ExactLabel("exact_label"),

    /** No exact match; a control whose label contained the requested text was used. */
    ContainsLabel("contains_label"),

    /** Several controls matched (e.g. repeated rows); the one at this ordinal was picked. */
    Index("index"),

    /** No label match at all; the coordinates the brain supplied were tapped as-is. */
    CoordinateFallback("coordinate_fallback"),
}

/** A screen rectangle in pixels, Android-style: [right] and [bottom] are exclusive. */
data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    /** Never negative, so an inverted rectangle reports 0 rather than -x. */
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
}

/** Where on the screen an action actually landed. Doubles keep the input unscaled. */
data class TapPoint(val x: Double, val y: Double)

/**
 * Why an action touched what it did: the label Nase used, the target's rectangle,
 * the coordinates it delivered, the strategy that selected the target, and how
 * sure or ambiguous that pick was. Recorded at the moment of the action so a
 * "why did it tap there" question has an answer after the fact.
 *
 * Construction is cheap and side-effect free: no clock, no I/O, no trimming or
 * clamping — every field is exactly what the caller passed. Invariants (not
 * enforced) are: [confidence] in 0.0..1.0 when non-null, [candidates] >= 0, and
 * [index] in 0 until [candidates] when non-null.
 */
data class ActionExplanation(
    /** The tool that ran, e.g. "tap" or "long_press". */
    val action: String,
    /** The human label used to find the control; blank when there was none. */
    val label: String,
    val strategy: MatchStrategy,
    /** The target's on-screen rectangle, when accessibility knew it. */
    val bounds: Bounds? = null,
    /** The coordinates actually delivered to the screen, when there were any. */
    val point: TapPoint? = null,
    /** 0.0..1.0 when the picker scored its choice; null when it did not. */
    val confidence: Double? = null,
    /** How many controls matched the query. 0 means unknown; more than one is a tie. */
    val candidates: Int = 0,
    /** Zero-based rank of the chosen control among [candidates]; null when not ranked. */
    val index: Int? = null,
) {
    /** True when the pick was a tie that a rule (index, score) had to break. */
    val ambiguous: Boolean get() = candidates > 1
}
