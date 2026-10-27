package app.nase.companion.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeStatusTest {
    private val id = "app.nase.companion.debug/app.nase.companion.input.NaseInputMethodService"

    @Test
    fun matchesFullIdShortIdAndSubtypeSuffix() {
        assertTrue(ImeStatus.listed(id, id, id))
        assertTrue(ImeStatus.listed("$id:com.samsung.android.honeyboard/.service.HoneyBoardService", id, id))
        assertTrue(ImeStatus.listed("$id;1234:com.samsung/.Other", id, ".input.NaseInputMethodService"))
        assertTrue(ImeStatus.listed("com.samsung/.Other:$id", "unused", id))
    }

    @Test
    fun naseUsableRequiresEnabledAndSelectedOrActive() {
        assertTrue(ImeSnapshot("gboard", naseEnabled = true, naseSelected = true, naseActive = false).naseUsable)
        assertTrue(ImeSnapshot("gboard", naseEnabled = true, naseSelected = false, naseActive = true).naseUsable)
        assertFalse(ImeSnapshot("gboard", naseEnabled = false, naseSelected = false, naseActive = false).naseUsable)
        assertFalse(ImeSnapshot("gboard", naseEnabled = true, naseSelected = false, naseActive = false).naseUsable)
    }

    @Test
    fun missesOtherKeyboardsAndBlankLists() {
        assertFalse(ImeStatus.listed("com.samsung.android.honeyboard/.service.HoneyBoardService", id, id))
        assertFalse(ImeStatus.listed(null, id, id))
        assertFalse(ImeStatus.listed("", id, id))
        assertFalse(ImeStatus.listed("$id.extra", id, id))
    }
}
