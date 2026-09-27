package com.bettercontent.classselector.network

import com.bettercontent.classselector.client.ClassSelectionState
import com.bettercontent.classselector.embark.EmbarkPoolItem
import com.bettercontent.classselector.embark.SelectionData
import com.bettercontent.classselector.embark.SelectionMode
import com.bettercontent.classselector.kit.ClassKit
import com.google.gson.Gson
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class SyncClassesPacket(
    private val activeInCurrentWorld: Boolean,
    private val selectionMode: String,
    private val pointQuota: Int,
    private val kitsJsonPayload: String,
    private val embarkItemsJsonPayload: String
) {
    companion object {
        private val gson = Gson()

        fun fromSelectionData(activeInCurrentWorld: Boolean, selectionData: SelectionData) =
            SyncClassesPacket(
                activeInCurrentWorld = activeInCurrentWorld,
                selectionMode = selectionData.mode.wireName,
                pointQuota = selectionData.embarkSettings.pointQuota,
                kitsJsonPayload = gson.toJson(selectionData.kits),
                embarkItemsJsonPayload = gson.toJson(selectionData.embarkSettings.items)
            )

        fun encode(packet: SyncClassesPacket, buf: FriendlyByteBuf) {
            buf.writeBoolean(packet.activeInCurrentWorld)
            buf.writeUtf(packet.selectionMode)
            buf.writeInt(packet.pointQuota)
            buf.writeUtf(packet.kitsJsonPayload)
            buf.writeUtf(packet.embarkItemsJsonPayload)
        }

        fun decode(buf: FriendlyByteBuf): SyncClassesPacket = SyncClassesPacket(
            buf.readBoolean(),
            buf.readUtf(64),
            buf.readInt(),
            buf.readUtf(32767),
            buf.readUtf(32767)
        )

        fun handle(packet: SyncClassesPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                val previousMode = ClassSelectionState.selectionMode
                val nextMode = runCatching { SelectionMode.parse(packet.selectionMode) }
                    .getOrDefault(SelectionMode.NONE)
                ClassSelectionState.activeInCurrentWorld = packet.activeInCurrentWorld
                ClassSelectionState.selectionMode = if (ClassSelectionState.reselectingSpawn) SelectionMode.NONE else nextMode
                ClassSelectionState.pointQuota = packet.pointQuota
                ClassSelectionState.kits = runCatching {
                    gson.fromJson(packet.kitsJsonPayload, Array<ClassKit>::class.java)?.toList() ?: emptyList()
                }
                    .getOrDefault(emptyList())
                ClassSelectionState.embarkItems = runCatching {
                    gson.fromJson(packet.embarkItemsJsonPayload, Array<EmbarkPoolItem>::class.java)?.toList() ?: emptyList()
                }
                    .getOrDefault(emptyList())
                if (previousMode != nextMode) {
                    ClassSelectionState.clearPendingLocks()
                }
                ClassSelectionState.reconcileEmbarkPurchases()
                if (!packet.activeInCurrentWorld) {
                    ClassSelectionState.promptOpen = false
                    ClassSelectionState.selectionRequired = false
                }
            }
            ctx.packetHandled = true
        }
    }
}
