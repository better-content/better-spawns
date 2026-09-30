package com.bettercontent.betterspawns.network

import com.bettercontent.betterspawns.ClassSelectorScope
import com.bettercontent.betterspawns.embark.EmbarkPurchase
import com.bettercontent.betterspawns.embark.EmbarkPurchaseService
import com.bettercontent.betterspawns.embark.SelectionDataRepository
import com.bettercontent.betterspawns.embark.SelectionMode
import com.bettercontent.betterspawns.integration.OnboardingIntegration
import com.bettercontent.betterspawns.integration.OnboardingVisibilitySync
import com.bettercontent.betterspawns.kit.KitApplicator
import com.bettercontent.betterspawns.network.FinalizeSelectionPacket.Companion.MAX_DIMENSION_ID_LENGTH
import com.bettercontent.betterspawns.respawn.PersonalRespawnPoint
import com.bettercontent.betterspawns.respawn.PersonalRespawnService
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.PacketDistributor
import java.util.function.Supplier

class FinalizeSelectionPacket(
    private val selectionMode: String,
    private val classId: String,
    private val embarkPurchases: List<EmbarkPurchase>,
    private val dimensionId: String,
    private val x: Int,
    private val y: Int,
    private val z: Int
) {
    companion object {
        const val MAX_DIMENSION_ID_LENGTH: Int = 128
        private const val MAX_SELECTION_ID_LENGTH: Int = 128
        private const val MAX_PURCHASES: Int = 256

        private fun reject(player: ServerPlayer, message: String) {
            ClassSelectorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { player }, SelectionNoticePacket(message, true))
        }

        fun spawnOnly(dimensionId: String, x: Int, y: Int, z: Int): FinalizeSelectionPacket =
            FinalizeSelectionPacket(
                selectionMode = SelectionMode.NONE.wireName,
                classId = "",
                embarkPurchases = emptyList(),
                dimensionId = dimensionId,
                x = x,
                y = y,
                z = z
            )

        fun classSelection(classId: String, dimensionId: String, x: Int, y: Int, z: Int): FinalizeSelectionPacket =
            FinalizeSelectionPacket(
                selectionMode = SelectionMode.CLASS.wireName,
                classId = classId,
                embarkPurchases = emptyList(),
                dimensionId = dimensionId,
                x = x,
                y = y,
                z = z
            )

        fun embarkSelection(
            purchases: List<EmbarkPurchase>,
            dimensionId: String,
            x: Int,
            y: Int,
            z: Int
        ): FinalizeSelectionPacket =
            FinalizeSelectionPacket(
                selectionMode = SelectionMode.EMBARK_POINTS.wireName,
                classId = EmbarkPurchaseService.SELECTION_ID,
                embarkPurchases = purchases,
                dimensionId = dimensionId,
                x = x,
                y = y,
                z = z
            )

        fun encode(packet: FinalizeSelectionPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.selectionMode)
            buf.writeUtf(packet.classId)
            buf.writeVarInt(packet.embarkPurchases.size)
            packet.embarkPurchases.forEach { purchase ->
                buf.writeUtf(purchase.itemId)
                buf.writeVarInt(purchase.quantity)
            }
            buf.writeUtf(packet.dimensionId)
            buf.writeInt(packet.x)
            buf.writeInt(packet.y)
            buf.writeInt(packet.z)
        }

        fun decode(buf: FriendlyByteBuf): FinalizeSelectionPacket {
            val selectionMode = buf.readUtf(64)
            val classId = buf.readUtf(MAX_SELECTION_ID_LENGTH)
            val purchaseCount = buf.readVarInt()
            require(purchaseCount in 0..MAX_PURCHASES) { "Invalid embark purchase count $purchaseCount" }
            val purchases = List(purchaseCount) {
                EmbarkPurchase(
                    itemId = buf.readUtf(MAX_SELECTION_ID_LENGTH),
                    quantity = buf.readVarInt()
                )
            }

            return FinalizeSelectionPacket(
                selectionMode = selectionMode,
                classId = classId,
                embarkPurchases = purchases,
                dimensionId = buf.readUtf(MAX_DIMENSION_ID_LENGTH),
                x = buf.readInt(),
                y = buf.readInt(),
                z = buf.readInt()
            )
        }

        fun handle(packet: FinalizeSelectionPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                val player = ctx.sender ?: return@enqueueWork
                if (!ClassSelectorScope.isActiveIn(player.server)) return@enqueueWork
                if (PersonalRespawnService.isReselectingSpawn(player)) {
                    if (!OnboardingIntegration.hasCompletedOnboarding(player) || !player.isSpectator ||
                        ResourceLocation.tryParse(packet.dimensionId) == null
                    ) {
                        reject(player, "Spawn reselection is not available right now.")
                        return@enqueueWork
                    }
                    val approved = PersonalRespawnService.validateOnboardingRespawnPoint(
                        player, PersonalRespawnPoint(packet.dimensionId, packet.x, packet.y, packet.z)
                    ) ?: run {
                        reject(player, "Choose a solid block directly below your current position.")
                        return@enqueueWork
                    }
                    val committed = runCatching { PersonalRespawnService.completeSpawnReselection(player, approved) }
                        .getOrElse { error ->
                            reject(player, error.message ?: "The selected spawn is no longer safe.")
                            return@enqueueWork
                        }
                    OnboardingIntegration.recordStartingSite(player, OnboardingIntegration.buildSpawnId(committed.point))
                    PersonalRespawnService.releasePlayerFromSpectator(player)
                    OnboardingVisibilitySync.sync(player.server)
                    return@enqueueWork
                }
                if (OnboardingIntegration.hasCompletedOnboarding(player)) return@enqueueWork

                val selectionData = SelectionDataRepository.getOrLoad()
                val requestedMode = runCatching { SelectionMode.parse(packet.selectionMode) }.getOrNull()
                if (requestedMode == null || requestedMode != selectionData.mode) {
                    ClassSelectorNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with { player },
                        SyncClassesPacket.fromSelectionData(true, selectionData)
                    )
                    reject(player, "Starting selection mode changed. Open the menu and try again.")
                    return@enqueueWork
                }

                val kit = when (selectionData.mode) {
                    SelectionMode.CLASS -> selectionData.kits.firstOrNull { it.id == packet.classId } ?: run {
                        reject(player, "Invalid class selection.")
                        return@enqueueWork
                    }
                    else -> null
                }
                val purchases = when (selectionData.mode) {
                    SelectionMode.EMBARK_POINTS -> runCatching {
                        EmbarkPurchaseService.validate(selectionData.embarkSettings, packet.embarkPurchases)
                    }.getOrElse { error ->
                        reject(player, error.message ?: "Invalid embark purchases.")
                        return@enqueueWork
                    }
                    else -> null
                }
                if (ResourceLocation.tryParse(packet.dimensionId) == null) {
                    reject(player, "Invalid respawn dimension.")
                    return@enqueueWork
                }
                val approvedPoint = PersonalRespawnService.validateOnboardingRespawnPoint(
                    player, PersonalRespawnPoint(packet.dimensionId, packet.x, packet.y, packet.z)
                ) ?: run {
                    reject(player, "Choose a valid respawn site at your current position.")
                    return@enqueueWork
                }
                // All rejection paths above are side-effect free. Commit the site before grants.
                val preparedPoint = runCatching { PersonalRespawnService.commitPreparedRespawnPoint(player, approvedPoint) }
                    .getOrElse { error ->
                        reject(player, error.message ?: "Starting site is no longer safe.")
                        return@enqueueWork
                    }
                val resolvedPoint = preparedPoint.point
                val spawnId = OnboardingIntegration.buildSpawnId(resolvedPoint)

                when (selectionData.mode) {
                    SelectionMode.NONE -> {
                        if (selectionData.starterSchematicannon) KitApplicator.giveStarterSchematicannon(player)
                        OnboardingIntegration.finalizeOnboarding(player, "spawn_only", spawnId)
                    }

                    SelectionMode.CLASS -> {
                        val selectedKit = requireNotNull(kit)
                        KitApplicator.apply(player, selectedKit)
                        if (selectionData.starterSchematicannon) KitApplicator.giveStarterSchematicannon(player)
                        OnboardingIntegration.finalizeOnboarding(player, selectedKit.id, spawnId)
                    }

                    SelectionMode.EMBARK_POINTS -> {
                        KitApplicator.applyItems(
                            player,
                            purchases!!.selectedItems,
                            EmbarkPurchaseService.SELECTION_ID
                        )
                        if (selectionData.starterSchematicannon) KitApplicator.giveStarterSchematicannon(player)
                        OnboardingIntegration.finalizeOnboarding(
                            player,
                            EmbarkPurchaseService.SELECTION_ID,
                            spawnId
                        )
                    }

                    SelectionMode.PROGRESSION -> error("Progression mode must resolve before selection finalization")
                }

                if (!PersonalRespawnService.releasePlayerFromSpectator(player)) {
                    ClassSelectorNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with { player },
                        SelectionNoticePacket("Your starting site is being prepared. You will enter shortly.", false)
                    )
                }
                OnboardingVisibilitySync.sync(player.server)
            }
            ctx.packetHandled = true
        }
    }
}
