package app.nase.companion.tasks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotPolicyTest {
    @Test
    fun keepsNasesOwnScreenInTheBackgroundWhenTheOwnerAllowsIt() {
        assertTrue(ShotPolicy.keepThumbnail(keepSetting = true, ponyForeground = false, ofOtherApp = false))
    }

    @Test
    fun dropsAnotherAppsScreenEvenWhenTheOwnerAllowsScreenshots() {
        // Private content from the app Nase is driving never lands on disk.
        assertFalse(ShotPolicy.keepThumbnail(keepSetting = true, ponyForeground = false, ofOtherApp = true))
    }

    @Test
    fun dropsAnythingCapturedWhileNaseIsInFront() {
        // Nase's Ask screen shows the step list; a shot of it would feed back.
        assertFalse(ShotPolicy.keepThumbnail(keepSetting = true, ponyForeground = true, ofOtherApp = false))
    }

    @Test
    fun honoursTheOwnerTurningScreenshotsOff() {
        assertFalse(ShotPolicy.keepThumbnail(keepSetting = false, ponyForeground = false, ofOtherApp = false))
    }
}
