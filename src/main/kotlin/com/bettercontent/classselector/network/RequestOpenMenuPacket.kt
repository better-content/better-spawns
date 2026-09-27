package com.bettercontent.classselector.network

import com.bettercontent.classselector.client.ClassSelectionState
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class RequestOpenMenuPacket(private val spawnOnly: Boolean = false) {
    companion object {
        fun encode(packet: RequestOpenMenuPacket, buf: FriendlyByteBuf) { buf.writeBoolean(packet.spawnOnly) }
        fun decode(buf: FriendlyByteBuf): RequestOpenMenuPacket = RequestOpenMenuPacket(buf.readBoolean())
        fun handle(packet: RequestOpenMenuPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                if (!ClassSelectionState.activeInCurrentWorld) {
                    return@enqueueWork
                }
                ClassSelectionState.selectionRequired = true
                ClassSelectionState.reselectingSpawn = packet.spawnOnly
                if (packet.spawnOnly) {
                    ClassSelectionState.selectionMode = com.bettercontent.classselector.embark.SelectionMode.NONE
                    ClassSelectionState.clearPendingLocks()
                }
                ClassSelectionState.promptOpen = !packet.spawnOnly
            }
            ctx.packetHandled = true
        }
    }
}
