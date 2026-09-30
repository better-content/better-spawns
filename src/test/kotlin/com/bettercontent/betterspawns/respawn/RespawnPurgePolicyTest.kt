package com.bettercontent.betterspawns.respawn

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RespawnPurgePolicyTest {
    @Test
    fun `purge uses an exact sphere including its boundary`() {
        assertTrue(isInsideRespawnPurge(64.0, 0.0, 0.0))
        assertTrue(isInsideRespawnPurge(0.0, 0.0, 0.0))
        assertFalse(isInsideRespawnPurge(64.0, 1.0, 0.0))
        assertFalse(isInsideRespawnPurge(48.0, 48.0, 0.0))
    }
}
