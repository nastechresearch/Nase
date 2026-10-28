package app.nase.companion.brain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fails when the hand-written brain tool list and descriptors/capabilities.yaml
 * disagree. Fix by editing the descriptor, then `npm run describe`.
 */
class GeneratedOpsTest {
    @Test
    fun kotlinToolListMatchesDescriptor() {
        assertEquals(
            "brain tools drifted from descriptors/capabilities.yaml",
            GeneratedOps.kotlinTools.sorted(),
            AdapterRequests.tools.map { it.name }.sorted(),
        )
    }

    @Test
    fun everySurfaceNameIsUnique() {
        assertEquals(GeneratedOps.kotlinTools.size, GeneratedOps.kotlinTools.toSet().size)
        assertEquals(GeneratedOps.wire.size, GeneratedOps.wire.toSet().size)
    }
}
