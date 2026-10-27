package app.nase.companion.recap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapShotPolicyTest {
    @Test
    fun keepsAWorkScreenButNeverNasesOwn() {
        assertTrue(RecapShotPolicy.keep(naseForeground = false))
        assertFalse(RecapShotPolicy.keep(naseForeground = true))
    }
}
