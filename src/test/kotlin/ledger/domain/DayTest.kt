package ledger.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DayTest {

    @Test
    fun `the window boundaries are valid days`() {
        assertEquals(1, Day(1).value)
        assertEquals(6, Day(6).value)
    }

    @Test
    fun `a day outside the six-day window cannot be constructed`() {
        assertFailsWith<IllegalArgumentException> { Day(0) }
        assertFailsWith<IllegalArgumentException> { Day(7) }
        assertFailsWith<IllegalArgumentException> { Day(-1) }
    }

    @Test
    fun `days compare by value`() {
        assertTrue(Day(2) < Day(5))
        assertTrue(Day(6) > Day(1))
        assertEquals(0, Day(3).compareTo(Day(3)))
    }

    @Test
    fun `the window is the six days in order`() {
        assertEquals(listOf(Day(1), Day(2), Day(3), Day(4), Day(5), Day(6)), Day.WINDOW)
    }

    @Test
    fun `a day renders as its label`() {
        assertEquals("Day 2", Day(2).toString())
    }
}
