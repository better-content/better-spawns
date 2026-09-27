package com.bettercontent.classselector.network

import com.bettercontent.classselector.client.ClassSelectionState
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class SelectionNoticePacket(private val message: String, private val retrySelection: Boolean) {
    companion object {
        fun encode(packet: SelectionNoticePacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.message.take(256), 256)
            buf.writeBoolean(packet.retrySelection)
        }

        fun decode(buf: FriendlyByteBuf): SelectionNoticePacket = SelectionNoticePacket(buf.readUtf(256), buf.readBoolean())

        fun handle(packet: SelectionNoticePacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                if (packet.retrySelection) {
                    ClassSelectionState.selectionRequired = true
                }
                ClassSelectionState.showNotice(packet.message)
            }
            ctx.packetHandled = true
        }
    }
}
