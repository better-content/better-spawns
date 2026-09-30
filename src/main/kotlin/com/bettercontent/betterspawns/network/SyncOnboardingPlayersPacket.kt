package com.bettercontent.betterspawns.network

import com.bettercontent.betterspawns.client.OnboardingPlayerVisibility
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.UUID
import java.util.function.Supplier

data class SyncOnboardingPlayersPacket(val playerIds: Set<UUID>) {
    companion object {
        fun encode(packet: SyncOnboardingPlayersPacket, buf: FriendlyByteBuf) {
            buf.writeVarInt(packet.playerIds.size)
            packet.playerIds.forEach(buf::writeUUID)
        }

        fun decode(buf: FriendlyByteBuf): SyncOnboardingPlayersPacket {
            val count = buf.readVarInt().coerceIn(0, 1024)
            return SyncOnboardingPlayersPacket(buildSet(count) {
                repeat(count) { add(buf.readUUID()) }
            })
        }

        fun handle(packet: SyncOnboardingPlayersPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork { OnboardingPlayerVisibility.replace(packet.playerIds) }
            ctx.packetHandled = true
        }
    }
}
