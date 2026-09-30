package com.bettercontent.betterspawns

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpectatorDaylightPolicyTest {
    @Test
    fun pausesWhenEveryConnectedPlayerIsASpectator() {
        assertTrue(SpectatorDaylightPolicy.shouldPause(totalPlayerCount = 1, spectatorPlayerCount = 1))
        assertTrue(SpectatorDaylightPolicy.shouldPause(totalPlayerCount = 4, spectatorPlayerCount = 4))
    }

    @Test
    fun advancesWhenAnyConnectedPlayerIsNotASpectator() {
        assertFalse(SpectatorDaylightPolicy.shouldPause(totalPlayerCount = 1, spectatorPlayerCount = 0))
        assertFalse(SpectatorDaylightPolicy.shouldPause(totalPlayerCount = 4, spectatorPlayerCount = 3))
    }

    @Test
    fun advancesWhenTheServerIsEmpty() {
        assertFalse(SpectatorDaylightPolicy.shouldPause(totalPlayerCount = 0, spectatorPlayerCount = 0))
    }
}
