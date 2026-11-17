package com.bettercontent.betterspawns.network

import com.bettercontent.betterspawns.respawn.DeathScoutIntegration
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.UUID
import java.util.function.Supplier

/** A request, never client permission to change spawn or handle items. */
data class ScoutRequestPacket(val episode: UUID) {
    companion object {
        fun encode(packet: ScoutRequestPacket, buf: FriendlyByteBuf) { buf.writeUUID(packet.episode) }
        fun decode(buf: FriendlyByteBuf) = ScoutRequestPacket(buf.readUUID())
        fun handle(packet: ScoutRequestPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork { ctx.sender?.let { DeathScoutIntegration.request(it, packet.episode) } }
            ctx.packetHandled = true
        }
    }
}
