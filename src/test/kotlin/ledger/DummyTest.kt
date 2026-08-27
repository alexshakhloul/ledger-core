package ledger

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Binary floating point for ledger amounts are not used. That is not a
 * style preference: the assertions below are the reason. `Money` will store integer
 * minor units, and every intermediate interest calculation runs through BigDecimal.
 * This test is only to assert the spec
 */
class DummyTest {

    @Test
    fun `binary floating point is not exact for decimal fractions`() {
        assertNotEquals(0.3, 0.1 + 0.2)
    }

    @Test
    fun `BigDecimal is exact for the same arithmetic`() {
        assertEquals(BigDecimal("0.3"), BigDecimal("0.1") + BigDecimal("0.2"))
    }
}
