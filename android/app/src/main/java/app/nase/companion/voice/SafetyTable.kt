package app.nase.companion.voice

/**
 * The safety table: every op Nase can run on the phone, and whether it must ask
 * the owner first. Ops are the exact tool names the brain sends
 * ([app.nase.companion.brain.AdapterRequests.tools]) and [PhoneOps.execute]
 * runs, plus the actions [VoiceRisk.promptFor] accepts.
 *
 * A tap is gated by its label rather than by the op, so [OpRule.labelRules] is
 * scanned in order and the first hit asks; money screens add their own gate in
 * [SafetyPolicy]. Any op missing from [ops] falls back to [default], which
 * confirms, so a newly added tool can never silently skip the check.
 */
object SafetyTable {
    /** A label phrase that asks before acting, with the question and short reason. */
    data class LabelRule(val pattern: Regex, val prompt: String, val reason: String)

    /**
     * One row per op. [confirm] is the unconditional op-level gate: true means
     * every call asks. [labelRules] add a gate for matching labels.
     */
    data class OpRule(
        val op: String,
        val confirm: Boolean,
        val reason: String,
        val labelRules: List<LabelRule> = emptyList(),
    )

    private fun words(vararg alternatives: String) =
        Regex("\\b(${alternatives.joinToString("|")})\\b", RegexOption.IGNORE_CASE)

    /** Risky verbs in match order. Keep this order: the first match wins. */
    val riskyLabels: List<LabelRule> = listOf(
        LabelRule(
            words(
                "send money", "request money", "send payment", "pay now", "pay", "payment", "checkout",
                "check out", "confirm payment", "slide to pay", "tap to pay", "complete purchase",
                "authori[sz]e payment",
            ),
            "Pay?",
            "payment",
        ),
        LabelRule(
            words(
                "buy", "buy now", "purchase", "place order", "order now", "subscribe", "start subscription",
                "start trial", "confirm purchase", "rent",
            ),
            "Buy this?",
            "purchase",
        ),
        LabelRule(words("send", "send message", "send now", "send email", "send sms", "reply all"), "Send this?", "send"),
        LabelRule(words("delete", "erase", "trash", "delete account", "remove account", "wipe", "empty trash"), "Delete this?", "delete"),
        LabelRule(words("call", "dial", "video call", "voice call", "call back"), "Place this call?", "call"),
        LabelRule(words("transfer", "wire", "withdraw"), "Transfer this?", "transfer"),
        LabelRule(words("post", "publish", "tweet", "share publicly", "go live"), "Post this?", "post"),
        LabelRule(
            Regex(
                "\\b(factory\\s+reset|change\\s+password|set\\s+password|reset\\s+password|screen\\s+lock|encryption|disable\\s+security|remove\\s+lock|turn\\s+off\\s+find\\s+my)\\b",
                RegexOption.IGNORE_CASE,
            ),
            "Change security settings?",
            "security",
        ),
    )

    /** Opening an app only asks when its name is a security change. */
    val securityLabels: List<LabelRule> = riskyLabels.filter { it.reason == "security" }

    /** Labels that turn a tap on a money screen into a confirmation. */
    val confirmWords: Regex = words("confirm", "continue", "next", "submit", "approve", "authori[sz]e", "ok", "done", "agree", "accept")

    /** An amount of money, used with [confirmWords] on checkout screens. */
    val amount: Regex = Regex("([$€£¥₹]\\s?\\d)|(\\d[\\d,.]*\\s?(usd|eur|gbp|dollars?|euros?))", RegexOption.IGNORE_CASE)

    /**
     * The ops the app actually sends, each with its safety decision. Settings
     * and navigation are normal work and never confirm (docs/0.6-plan.md);
     * sending, paying, buying, ordering, deleting, calling, and posting do.
     */
    val ops: Map<String, OpRule> = listOf(
        // Screen actions. A tap asks only for a risky label or on a money screen.
        OpRule("tap", confirm = false, reason = "tap", labelRules = riskyLabels),
        OpRule("swipe", confirm = false, reason = "navigation"),
        OpRule("long_press", confirm = false, reason = "navigation"),
        OpRule("drag", confirm = false, reason = "navigation"),
        OpRule("pinch", confirm = false, reason = "navigation"),
        OpRule("key", confirm = false, reason = "navigation"),
        OpRule("wait_idle", confirm = false, reason = "navigation"),
        // Typing is refused in password fields; it is not a send/post/pay gate.
        OpRule("type", confirm = false, reason = "type"),
        // Opening an app asks only when the destination is a security change.
        OpRule("open_app", confirm = false, reason = "open_app", labelRules = securityLabels),
        // Settings changes never confirm.
        OpRule("open_settings", confirm = false, reason = "settings"),
        OpRule("speak", confirm = false, reason = "speech"),
        OpRule("ask", confirm = false, reason = "speech"),
        OpRule("remember", confirm = false, reason = "memory"),
        OpRule("forget", confirm = false, reason = "memory"),
        OpRule("schedule_task", confirm = false, reason = "schedule"),
        OpRule("copy_text", confirm = false, reason = "scratch"),
        OpRule("recall_text", confirm = false, reason = "scratch"),
        // The AgentLoop finish verbs: they end the task, they don't act.
        OpRule("done", confirm = false, reason = "finish"),
        OpRule("answer", confirm = false, reason = "finish"),
        OpRule("finish", confirm = false, reason = "finish"),
    ).associateBy { it.op }

    /** Used for any op not in [ops]: ask, never silently allow. */
    val default: OpRule = OpRule(op = "unknown", confirm = true, reason = "unknown_op")

    fun forOp(op: String): OpRule = ops[op] ?: default.copy(op = op)

    fun labelRuleFor(op: String, label: String): LabelRule? =
        forOp(op).labelRules.firstOrNull { it.pattern.containsMatchIn(label) }
}
