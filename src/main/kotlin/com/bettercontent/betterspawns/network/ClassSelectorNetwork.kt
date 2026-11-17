package com.bettercontent.betterspawns.network

import com.bettercontent.betterspawns.ClassSelectorMod
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.simple.SimpleChannel
import java.util.function.BiConsumer
import java.util.function.Function
import java.util.function.Supplier

object ClassSelectorNetwork {
    private const val PROTOCOL = "6"
    val CHANNEL: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(ClassSelectorMod.MOD_ID, "main"),
        { PROTOCOL },
        PROTOCOL::equals,
        PROTOCOL::equals
    )

    fun register() {
        var id = 0
        fun <T> registerMessage(
            type: Class<T>,
            encoder: BiConsumer<T, FriendlyByteBuf>,
            decoder: Function<FriendlyByteBuf, T>,
            handler: BiConsumer<T, Supplier<NetworkEvent.Context>>,
            direction: NetworkDirection? = null
        ) {
            val builder = if (direction == null) CHANNEL.messageBuilder(type, id++)
                else CHANNEL.messageBuilder(type, id++, direction)
            builder
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread(handler)
                .add()
        }

        registerMessage(RequestOpenMenuPacket::class.java, RequestOpenMenuPacket::encode, RequestOpenMenuPacket::decode, RequestOpenMenuPacket::handle)
        registerMessage(FinalizeSelectionPacket::class.java, FinalizeSelectionPacket::encode, FinalizeSelectionPacket::decode, FinalizeSelectionPacket::handle)
        registerMessage(SyncClassesPacket::class.java, SyncClassesPacket::encode, SyncClassesPacket::decode, SyncClassesPacket::handle)
        registerMessage(SyncOnboardingPlayersPacket::class.java, SyncOnboardingPlayersPacket::encode, SyncOnboardingPlayersPacket::decode, SyncOnboardingPlayersPacket::handle)
        registerMessage(SelectionNoticePacket::class.java, SelectionNoticePacket::encode, SelectionNoticePacket::decode, SelectionNoticePacket::handle)
        registerMessage(SyncScoutTargetsPacket::class.java, SyncScoutTargetsPacket::encode, SyncScoutTargetsPacket::decode, SyncScoutTargetsPacket::handle, NetworkDirection.PLAY_TO_CLIENT)
        registerMessage(ScoutStatusPacket::class.java, ScoutStatusPacket::encode, ScoutStatusPacket::decode, ScoutStatusPacket::handle, NetworkDirection.PLAY_TO_CLIENT)
        registerMessage(ScoutRequestPacket::class.java, ScoutRequestPacket::encode, ScoutRequestPacket::decode, ScoutRequestPacket::handle, NetworkDirection.PLAY_TO_SERVER)
    }
}
