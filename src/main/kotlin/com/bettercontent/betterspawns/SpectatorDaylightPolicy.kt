package com.bettercontent.betterspawns

object SpectatorDaylightPolicy {
    @JvmStatic
    fun shouldPause(totalPlayerCount: Int, spectatorPlayerCount: Int): Boolean =
        totalPlayerCount > 0 && spectatorPlayerCount == totalPlayerCount
}
