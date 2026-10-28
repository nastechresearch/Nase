package app.nase.companion.voice

/** Whether [AppAutonomy.autonomyVerdict] still needs the owner's explicit yes. */
enum class AutonomyOutcome { CONFIRM, ALLOW }

/**
 * One per-app autonomy answer. [reason] is a stable identifier: either a
 * [SafetyTable] label reason (`payment`, `send`, `delete`, `call`, …) or one of
 * [AppAutonomy.Reason].
 *
 * [ALLOW] means only that this layer adds no gate. It never means the action is
 * safe: callers must still take the whole [SafetyPolicy] verdict (password
 * fields, money screens, unknown tools) and confirm if either layer says so.
 */
data class AutonomyVerdict(
    val outcome: AutonomyOutcome,
    val reason: String,
) {
    val requiresConfirm: Boolean get() = outcome == AutonomyOutcome.CONFIRM
    val allows: Boolean get() = outcome == AutonomyOutcome.ALLOW
}

/**
 * The owner's standing choices about acting without a question, per app.
 *
 * The default is no exemptions at all. A package is exempt only when its exact
 * name was put in the list; there is no prefix or wildcard matching, and blank
 * or `*`-containing entries are dropped so a stray setting cannot exempt the
 * world. Construct with no arguments (or use [EMPTY]) and every app still asks.
 */
class AutonomySettings(packages: Set<String> = emptySet()) {
    private val packages: Set<String> = packages
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && '*' !in it }
        .toSet()

    /** The exact package names the owner listed. Read-only. */
    val allowedPackages: Set<String> get() = packages

    /**
     * True only for a non-blank package name that appears verbatim in the
     * owner's list. Unknown, blank, null, and wildcard-looking names are false.
     */
    fun allowWithoutConfirm(packageName: String?): Boolean {
        val pkg = packageName?.trim() ?: return false
        return pkg.isNotEmpty() && pkg in packages
    }

    companion object {
        /** The conservative default: no app is exempt. */
        val EMPTY: AutonomySettings = AutonomySettings()

        /** Builds a settings value from package names, for prefs or tests. */
        fun of(vararg packages: String): AutonomySettings = AutonomySettings(packages.toSet())
    }
}

/**
 * Per-app autonomy, layered on top of [SafetyTable]. It answers one question:
 * for this package and this op, does the owner's setting still leave a
 * confirmation in place?
 *
 * Conservative by construction:
 * - An empty (or absent) [AutonomySettings] confirms every app. There is no
 *   built-in trusted app and no default exemption.
 * - An op missing from [SafetyTable] confirms, exactly like
 *   [SafetyTable.default].
 * - A blank or null package confirms.
 * - A risky label (`pay`, `send`, `delete`, `call`, `post`, `security`, …)
 *   confirms before the allowlist is even read, so autonomy can never loosen
 *   [SafetyTable]'s label rules. That check also covers `open_app` security
 *   labels.
 * - A tap in an app [MoneyScreens] knows moves money confirms even when the
 *   label looks ordinary, mirroring [SafetyPolicy]'s payment-app gate.
 *
 * This object is pure and read-only: it does not run ops, write settings, or
 * weaken [SafetyPolicy]. Another change calls it; nothing here is wired into
 * `PhoneOps` or the session.
 */
object AppAutonomy {
    /** Reasons this layer adds; label reasons come from [SafetyTable]. */
    object Reason {
        const val APP_ALLOWED = "app_allowed"
        const val APP_NOT_ALLOWED = "app_not_allowed"
        const val UNKNOWN_PACKAGE = "unknown_package"

        /** Same identifier [SafetyPolicy] uses for a known money app. */
        const val PAYMENT_APP = "payment_app"
    }

    /**
     * The verdict for [op] in [packageName] with the tapped control's [label].
     *
     * @param label the control's own label or accessibility text, or null when
     *   there is none (a bare icon or coordinate tap).
     * @param settings the owner's allowlist; defaults to [AutonomySettings.EMPTY]
     *   so a caller that forgets it still confirms.
     */
    fun autonomyVerdict(
        packageName: String?,
        op: String,
        label: String? = null,
        settings: AutonomySettings = AutonomySettings.EMPTY,
    ): AutonomyVerdict {
        // 1. Existing label rules always win. Autonomy cannot override them.
        val labelText = label?.trim().orEmpty()
        val labelRule = if (labelText.isEmpty()) null else SafetyTable.labelRuleFor(op, labelText)
        if (labelRule != null) {
            return AutonomyVerdict(AutonomyOutcome.CONFIRM, labelRule.reason)
        }

        // 2. The op's own gate, including SafetyTable.default for unknown ops.
        val opRule = SafetyTable.forOp(op)
        if (opRule.confirm) {
            return AutonomyVerdict(AutonomyOutcome.CONFIRM, opRule.reason)
        }

        // 3. The app must be named explicitly. Unknown apps always ask.
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isEmpty()) {
            return AutonomyVerdict(AutonomyOutcome.CONFIRM, Reason.UNKNOWN_PACKAGE)
        }

        // 4. SafetyPolicy asks before every tap in a known money app. Autonomy
        // may add questions, never remove that one.
        if (op == "tap" && MoneyScreens.isKnownPackage(pkg)) {
            return AutonomyVerdict(AutonomyOutcome.CONFIRM, Reason.PAYMENT_APP)
        }

        if (!settings.allowWithoutConfirm(pkg)) {
            return AutonomyVerdict(AutonomyOutcome.CONFIRM, Reason.APP_NOT_ALLOWED)
        }

        return AutonomyVerdict(AutonomyOutcome.ALLOW, Reason.APP_ALLOWED)
    }
}
