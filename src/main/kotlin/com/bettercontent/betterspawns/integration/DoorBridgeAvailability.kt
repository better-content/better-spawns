package com.bettercontent.betterspawns.integration

import net.minecraftforge.fml.ModList

/** The marker is common-side and safe to probe before touching optional Door client classes. */
object DoorBridgeAvailability {
    private val common by lazy {
        ModList.get().isLoaded("better_deaths_door") && runCatching {
            val marker = Class.forName("com.bettercontent.betterdeathsdoor.api.ScoutBridgeContract",
                false, DoorBridgeAvailability::class.java.classLoader)
            marker.getDeclaredMethod("version").invoke(null) == 1
        }.getOrDefault(false)
    }
    fun available() = common

    /** Called exclusively from Dist.CLIENT, never a dedicated-server initialization path. */
    private val client by lazy {
        available() && runCatching {
            Class.forName("com.bettercontent.betterdeathsdoor.client.ScoutDeathAction", false,
                DoorBridgeAvailability::class.java.classLoader)
            true
        }.getOrDefault(false)
    }
    fun clientAvailable() = client
}
