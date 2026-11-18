package com.bettercontent.betterspawns.integration

import com.bettercontent.betterspawns.client.TypedDoorClientBridge
import com.bettercontent.betterspawns.network.ScoutStatusPacket
import net.minecraftforge.fml.ModList

/** No optional provider types in this eagerly loaded common dispatcher. */
object DoorBridgeAvailability {
    private val common by lazy {
        ModList.get().isLoaded("better_deaths_door") && try {
            TypedDoorBridge.compatible()
        } catch (_: LinkageError) {
            false // Older Door or missing common contract: leave native deaths unchanged.
        }
    }
    fun available() = common

    /** These methods are only called from Dist.CLIENT; never from common initialization. */
    private val client by lazy {
        available() && try {
            TypedDoorClientBridge.compatible()
        } catch (_: LinkageError) {
            false
        }
    }
    @Volatile private var clientFailed = false
    fun clientAvailable(): Boolean = client && !clientFailed

    fun registerClient() {
        if (!clientAvailable()) return
        try {
            TypedDoorClientBridge.register()
        } catch (_: LinkageError) {
            // An older client API must not leave a tick loop repeatedly trying to register.
            clientFailed = true
        }
    }

    fun disconnectedClient() {
        if (clientAvailable()) TypedDoorClientBridge.disconnected()
    }

    fun receiveClient(packet: ScoutStatusPacket) {
        if (clientAvailable()) TypedDoorClientBridge.receive(packet)
    }
}
