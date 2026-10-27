package app.nase.companion.tasks

/** Decides whether a captured screen may be saved as a small on-phone history thumbnail. */
object ShotPolicy {
    /**
     * Two rules from phone testing:
     *  - Never while Nase itself is in the foreground. The Ask screen shows the
     *    step list, so a screenshot taken then captures earlier thumbnails and
     *    feeds back on itself.
     *  - Never a screen that belongs to another app. That content is private; it
     *    lives only in the live reply to the brain, not on disk.
     *
     * Only Nase's own screens, captured while Nase is in the background, may be
     * kept — and then only when the owner left that setting on.
     */
    fun keepThumbnail(keepSetting: Boolean, ponyForeground: Boolean, ofOtherApp: Boolean): Boolean =
        keepSetting && !ponyForeground && !ofOtherApp
}
