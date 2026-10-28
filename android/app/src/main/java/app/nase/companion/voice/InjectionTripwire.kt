package app.nase.companion.voice

import java.text.Normalizer

/**
 * A prompt-injection warning for on-screen text. This is a *flag*, not a gate:
 * the caller shows [reason] and [excerpt] to the owner, and the owner decides.
 * Nothing in this file calls [SafetyPolicy], changes a [Verdict], or blocks an
 * action.
 */
data class InjectionFlag(
    val flagged: Boolean,
    val reason: String? = null,
    val excerpt: String? = null,
) {
    companion object {
        /** Text with no injection-shaped instruction. */
        val Clean: InjectionFlag = InjectionFlag(flagged = false)
    }
}

/**
 * Scans text an app makes visible for instructions aimed at the assistant
 * rather than at a person using the app: "ignore previous instructions",
 * "system:", "you are now …", "do not tell the user", hidden formatting, and
 * text that addresses an AI by name.
 *
 * Flag-only and deterministic:
 * - [scan] and [scanNodes] only read text and return data. They never throw on
 *   odd input, never touch the screen, and never affect confirmation.
 * - Only the first [MAX_CHARS] characters and the first [MAX_NODES] nodes are
 *   examined, so one hostile screen cannot make the scan unbounded.
 * - Rules are a fixed ordered list; the first match wins, and the same input
 *   always produces the same flag. There is no clock, randomness, or I/O.
 *
 * The rules deliberately do not match ordinary UI: "Continue", "Send", "Pay",
 * "Delete", "System requirements:", and product copy that merely mentions AI
 * ("Meet our new AI assistant") stay clean. Text with invisible characters is
 * flagged as [Reason.HIDDEN_TEXT] because that is a common way to hide an
 * instruction inside UI copy.
 */
object InjectionTripwire {
    /** Characters scanned from one string (or across all nodes). */
    const val MAX_CHARS: Int = 16_000

    /** Nodes read from a UI tree before the scan stops. */
    const val MAX_NODES: Int = 400

    /** Longest excerpt returned to the caller, in characters. */
    const val MAX_EXCERPT: Int = 80

    object Reason {
        const val HIDDEN_TEXT = "hidden_text"
        const val IGNORE_INSTRUCTIONS = "ignore_instructions"
        const val SECRECY = "secrecy"
        const val ROLE_REASSIGNMENT = "role_reassignment"
        const val ROLE_MARKER = "role_marker"
        const val ADDRESSED_AT_AI = "addressed_at_ai"
        const val AI_PROBE = "ai_probe"
        const val JAILBREAK = "jailbreak"
    }

    private data class Rule(val reason: String, val pattern: Regex)

    private fun regex(pattern: String): Regex =
        Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    /**
     * Invisible formatting characters: zero-width space/non-joiner/word
     * joiner, BOM, and the bidi overrides. The zero-width joiner (U+200D) is
     * left alone because emoji sequences use it legitimately.
     */
    private val hiddenFormatting = Regex("[\\u200B\\u200C\\u2060\\uFEFF\\u202A-\\u202E\\u2066-\\u2069]")

    /** Fixed order: first match wins, so the result is deterministic. */
    private val rules: List<Rule> = listOf(
        Rule(
            Reason.IGNORE_INSTRUCTIONS,
            regex(
                """\b(ignore|disregard|forget|override|bypass)\s+(all\s+|any\s+)?""" +
                    """(of\s+)?(the\s+|your\s+)?(previous|prior|above|earlier|preceding|system|safety)""" +
                    """\s+(instructions?|prompts?|rules?|messages?|guidelines?|guardrails?|directives?)\b""",
            ),
        ),
        Rule(
            Reason.IGNORE_INSTRUCTIONS,
            regex("""\b(ignore|disregard|forget)\s+(everything\s+|the\s+)?(above|before this)\b"""),
        ),
        Rule(
            Reason.IGNORE_INSTRUCTIONS,
            regex(
                """\b(override|bypass|ignore)\s+(your|the)\s+""" +
                    """(instructions?|guidelines?|guardrails?|safety|rules?|filters?)\b""",
            ),
        ),
        Rule(
            Reason.SECRECY,
            regex(
                """\b(do not|don[’']?t|never)\s+(tell|inform|notify|alert|warn)\s+""" +
                    """(the\s+)?(user|owner|human)\b""",
            ),
        ),
        Rule(
            Reason.SECRECY,
            regex(
                """\b(do not|don[’']?t|never)\s+(mention|reveal|disclose)\s+(this|it|that)\s+""" +
                    """to\s+(the\s+)?(user|owner|human)\b""",
            ),
        ),
        Rule(
            Reason.SECRECY,
            regex("""\bwithout\s+(telling|informing|notifying|alerting)\s+(the\s+)?(user|owner|human)\b"""),
        ),
        Rule(
            Reason.SECRECY,
            regex(
                """\b(hide|conceal|keep)\s+(this|it|that)\s+(from|secret from)\s+""" +
                    """(the\s+)?(user|owner|human)\b""",
            ),
        ),
        Rule(
            Reason.ROLE_REASSIGNMENT,
            regex(
                """\byou are now\s+(an?\s+)?(ai|a\.i\.|assistant|chatbot|chat bot|language model|llm|""" +
                    """system|developer|jailbroken|unrestricted|dan)\b""",
            ),
        ),
        Rule(
            Reason.ROLE_REASSIGNMENT,
            regex(
                """\byou are now\s+(in\s+)?(developer|admin|god|jailbreak|unrestricted|dan)\s+mode\b""",
            ),
        ),
        Rule(
            Reason.ROLE_REASSIGNMENT,
            regex(
                """\bfrom now on,?\s+you\s+(are|will\s+(act|behave|respond)|must\s+(act|behave|respond))\s+""" +
                    """(as\s+)?(an?\s+)?(ai|assistant|chatbot|language model|llm|system|unrestricted)\b""",
            ),
        ),
        Rule(
            Reason.ROLE_REASSIGNMENT,
            regex(
                """\b(act|behave|respond|answer)\s+as\s+(an?\s+)?(ai|assistant|chatbot|language model|""" +
                    """llm|unrestricted|jailbroken)\b""",
            ),
        ),
        Rule(
            Reason.ROLE_REASSIGNMENT,
            regex(
                """\bpretend\s+(to be|you are)\s+(an?\s+)?(ai|assistant|chatbot|language model|llm|unrestricted)\b""",
            ),
        ),
        Rule(
            Reason.ROLE_MARKER,
            regex("""^\s*(system|assistant)\s*:"""),
        ),
        Rule(
            Reason.ROLE_MARKER,
            regex(
                """(<\s*(system|assistant|instruction|prompt)\s*>|\[\s*(system|assistant|inst|/inst)\s*\]|""" +
                    """<<\s*sys\s*>>)""",
            ),
        ),
        Rule(
            Reason.ROLE_MARKER,
            regex("""^\s*#{2,}\s*(system|instruction|prompt)\b"""),
        ),
        Rule(
            Reason.ADDRESSED_AT_AI,
            regex(
                """\b(ai|a\.i\.|assistant|chatbot|language model|llm|nase)\s*[,:]\s*(please\s+)?""" +
                    """(send|pay|transfer|buy|order|delete|remove|call|open|click|tap|type|approve|confirm|""" +
                    """install|download|disable|turn off)\b""",
            ),
        ),
        Rule(
            Reason.ADDRESSED_AT_AI,
            regex(
                """\b(attention|dear|hey|note to|message to|message for|instructions? (to|for)|prompt (to|for))""" +
                    """[\s,]+(the\s+)?(ai|assistant|chatbot|language model|llm|agent|system)\b""",
            ),
        ),
        Rule(
            Reason.AI_PROBE,
            regex(
                """\b(if|when)\s+you\s+(are|were)\s+(an?\s+)?(ai|assistant|chatbot|language model|llm|""" +
                    """automated agent|bot)\b""",
            ),
        ),
        Rule(
            Reason.AI_PROBE,
            regex(
                """\b(this|the following)\s+(message|text|note|instruction|prompt)\s+is\s+""" +
                    """(for|intended for|meant for)\s+(the\s+|an?\s+)?(ai|assistant|chatbot|language model|llm|agent)\b""",
            ),
        ),
        Rule(
            Reason.AI_PROBE,
            regex("""\b(ai|assistant|language model|llm)\s+reading\s+(this|the screen|these instructions)\b"""),
        ),
        Rule(
            Reason.JAILBREAK,
            regex("""\b(jailbreak mode|jailbroken mode|dan mode|do anything now)\b"""),
        ),
    )

    /**
     * Scans one string. Only the first [maxChars] characters are read; a null,
     * blank, or empty string is clean.
     */
    fun scan(text: String?, maxChars: Int = MAX_CHARS): InjectionFlag {
        if (text == null || text.isEmpty() || maxChars <= 0) return InjectionFlag.Clean
        val clipped = if (text.length > maxChars) text.substring(0, maxChars) else text
        if (clipped.isBlank()) return InjectionFlag.Clean

        val hidden = hiddenFormatting.find(clipped)
        if (hidden != null) {
            return InjectionFlag(
                flagged = true,
                reason = Reason.HIDDEN_TEXT,
                excerpt = excerptAround(clipped, hidden.range.first),
            )
        }

        val normalized = normalize(clipped)
        for (rule in rules) {
            val match = rule.pattern.find(normalized) ?: continue
            return InjectionFlag(
                flagged = true,
                reason = rule.reason,
                excerpt = excerpt(normalized, match),
            )
        }
        return InjectionFlag.Clean
    }

    /**
     * Scans up to [maxNodes] visible strings (a UI tree's node texts) and at
     * most [maxChars] characters in total. Nodes are joined with newlines so a
     * role marker at the start of any node is still recognised. Stops at the
     * first bound reached; later nodes are not examined.
     */
    fun scanNodes(
        nodes: Iterable<String?>?,
        maxNodes: Int = MAX_NODES,
        maxChars: Int = MAX_CHARS,
    ): InjectionFlag {
        if (nodes == null || maxNodes <= 0 || maxChars <= 0) return InjectionFlag.Clean
        val joined = StringBuilder(minOf(maxChars, 4_096))
        var scanned = 0
        for (node in nodes) {
            if (scanned >= maxNodes || joined.length >= maxChars) break
            scanned++
            val text = node ?: continue
            if (text.isBlank()) continue
            if (joined.isNotEmpty()) joined.append('\n')
            if (joined.length >= maxChars) break
            val room = maxChars - joined.length
            if (text.length <= room) {
                joined.append(text)
            } else {
                joined.append(text, 0, room)
                break
            }
        }
        if (joined.length == 0) return InjectionFlag.Clean
        return scan(joined.toString(), maxChars)
    }

    private fun normalize(raw: String): String {
        val brokenLines = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u2028', '\n')
            .replace('\u2029', '\n')
        return Normalizer.normalize(brokenLines, Normalizer.Form.NFKC).replace(hiddenFormatting, "")
    }

    private fun excerpt(text: String, match: MatchResult): String? {
        val start = match.range.first.coerceIn(0, text.length)
        val end = (match.range.last + 1)
            .coerceAtMost(text.length)
            .coerceAtMost(start + MAX_EXCERPT)
        return text.substring(start, end)
            .trim()
            .replace('\n', ' ')
            .takeIf { it.isNotEmpty() }
    }

    /** A visible window around an invisible character, so the warning has context. */
    private fun excerptAround(raw: String, index: Int): String? {
        val start = (index - 20).coerceAtLeast(0)
        val end = (index + 60).coerceAtMost(raw.length)
        return normalize(raw.substring(start, end))
            .trim()
            .replace('\n', ' ')
            .take(MAX_EXCERPT)
            .takeIf { it.isNotEmpty() }
    }
}
