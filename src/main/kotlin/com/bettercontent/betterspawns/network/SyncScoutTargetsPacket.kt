package com.bettercontent.betterspawns.network

import com.bettercontent.betterspawns.client.ScoutTargets
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.UUID
import java.util.function.Supplier

/** Server-authored same-dimension snapshot; does not depend on client entity tracking. */
data class SyncScoutTargetsPacket(val dimension: String, val targets: List<Target>) {
    data class Target(val id: UUID, val name: String, val x: Double, val y: Double, val z: Double)

    companion object {
        fun encode(packet: SyncScoutTargetsPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.dimension, 128)
            buf.writeVarInt(packet.targets.size)
            packet.targets.forEach { target ->
                buf.writeUUID(target.id)
                buf.writeUtf(target.name, 64)
                buf.writeDouble(target.x)
                buf.writeDouble(target.y)
                buf.writeDouble(target.z)
            }
        }

        fun decode(buf: FriendlyByteBuf): SyncScoutTargetsPacket {
            val dimension = buf.readUtf(128)
            val size = buf.readVarInt()
            require(size in 0..16384) { "Invalid scout target count: $size" }
            val targets = List(size) {
                Target(buf.readUUID(), buf.readUtf(64), buf.readDouble(), buf.readDouble(), buf.readDouble())
            }
            return SyncScoutTargetsPacket(dimension, targets)
        }

        fun handle(packet: SyncScoutTargetsPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork { ScoutTargets.replace(packet) }
            ctx.packetHandled = true
        }
    }
}
