package com.bettercontent.betterspawns.integration

import com.bettercontent.betterspawns.respawn.PersonalRespawnPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingIntegrationTest {
    @Test
    fun canBuildSpawnIdFromPoint() {
        val point = PersonalRespawnPoint("minecraft:overworld", 10, 64, -5)
        assertEquals("minecraft:overworld@10,64,-5", OnboardingIntegration.buildSpawnId(point))
    }
}
