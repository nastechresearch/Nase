package app.nase.companion.recap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapShotPolicyTest {
    @Test
    fun keepsAWorkScreenButNeverNasesOwn() {
        assertTrue(RecapShotPolicy.keep(ponyForeground = false))
        assertFalse(RecapShotPolicy.keep(ponyForeground = true))
    }
}
