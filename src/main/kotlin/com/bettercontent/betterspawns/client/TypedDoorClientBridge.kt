package com.bettercontent.betterspawns.client

import com.bettercontent.betterdeathsdoor.client.ScoutDeathAction
import com.bettercontent.betterspawns.network.ScoutStatusPacket

/** Client-only optional Door API references; never load from a dedicated-server entry point. */
internal object TypedDoorClientBridge {
    fun compatible(): Boolean = ScoutDeathAction::class.java.isAssignableFrom(ScoutDeathClient::class.java)
    fun register() = ScoutDeathClient.register()
    fun disconnected() = ScoutDeathClient.disconnected()
    fun receive(packet: ScoutStatusPacket) = ScoutDeathClient.receive(packet)
}
