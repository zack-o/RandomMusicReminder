package com.muyex.randommusicreminder.domain.ringing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SnoozePolicyTest {
    private val policy = SnoozePolicy()

    @Test
    fun `allows three snoozes and rejects the fourth`() {
        assertEquals(1, policy.nextCount(0, 10))
        assertEquals(2, policy.nextCount(1, 10))
        assertEquals(3, policy.nextCount(2, 10))
        assertNull(policy.nextCount(3, 10))
    }

    @Test
    fun `rejects unsupported duration`() {
        assertThrows(IllegalArgumentException::class.java) {
            policy.nextCount(0, 8)
        }
    }
}
