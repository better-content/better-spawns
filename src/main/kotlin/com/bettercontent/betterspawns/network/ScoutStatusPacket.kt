package com.bettercontent.betterspawns.network

import com.bettercontent.betterspawns.integration.DoorBridgeAvailability
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.UUID
import java.util.function.Supplier

/** Server decides when this exact death episode may offer a re-scout. */
data class ScoutStatusPacket(val episode: UUID, val available: Boolean, val pending: Boolean,
                             val error: String = "", val location: String = "") {
    companion object {
        fun encode(packet: ScoutStatusPacket, buf: FriendlyByteBuf) {
            buf.writeUUID(packet.episode)
            buf.writeBoolean(packet.available)
            buf.writeBoolean(packet.pending)
            buf.writeUtf(packet.error, 256)
            buf.writeUtf(packet.location, 160)
        }
        fun decode(buf: FriendlyByteBuf): ScoutStatusPacket = ScoutStatusPacket(buf.readUUID(),
            buf.readBoolean(), buf.readBoolean(), buf.readUtf(256), buf.readUtf(160))
        fun handle(packet: ScoutStatusPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork { DoorBridgeAvailability.receiveClient(packet) }
            ctx.packetHandled = true
        }
    }
}
