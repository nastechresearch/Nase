package app.nase.companion.display

/**
 * Nase must never read its own UI as the task. When the task runs on the main
 * screen and Nase itself is in front — the Ask chat the owner just typed into —
 * the agent goes home first so the very first screenshot is the real phone, not
 * Nase's chat. The hidden screen never shows Nase, so it's left alone.
 */
object OwnUi {
    /** True when the main screen is showing Nase's own app and the agent should leave before it looks. */
    fun inFront(onMainScreen: Boolean, foreground: String?, ownPackage: String): Boolean {
        if (!onMainScreen) return false
        if (foreground.isNullOrBlank()) return false
        return foreground == ownPackage
    }
}
