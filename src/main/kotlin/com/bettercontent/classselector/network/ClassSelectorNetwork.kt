package com.bettercontent.classselector.network

import com.bettercontent.classselector.ClassSelectorMod
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.simple.SimpleChannel
import java.util.function.BiConsumer
import java.util.function.Function
import java.util.function.Supplier

object ClassSelectorNetwork {
    private const val PROTOCOL = "5"
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
            handler: BiConsumer<T, Supplier<NetworkEvent.Context>>
        ) {
            CHANNEL.messageBuilder(type, id++)
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
    }
}
