package com.bettercontent.betterspawns

import com.bettercontent.betterspawns.kit.KitApplicator
import com.bettercontent.betterspawns.integration.OnboardingIntegration
import com.bettercontent.betterspawns.integration.OnboardingVisibilitySync
import com.bettercontent.betterspawns.embark.SelectionDataRepository
import com.bettercontent.betterspawns.network.ClassSelectorNetwork
import com.bettercontent.betterspawns.network.RequestOpenMenuPacket
import com.bettercontent.betterspawns.network.SyncClassesPacket
import com.bettercontent.betterspawns.respawn.PersonalRespawnService
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraftforge.event.OnDatapackSyncEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent
import net.minecraftforge.event.server.ServerAboutToStartEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.network.PacketDistributor

@Mod(ClassSelectorMod.MOD_ID)
class ClassSelectorMod {
    init {
        ClassSelectorNetwork.register()
    }

    companion object {
        const val MOD_ID = "better_spawns"
    }
}

@Mod.EventBusSubscriber(modid = ClassSelectorMod.MOD_ID)
object ServerEvents {
    @JvmStatic
    @SubscribeEvent
    fun onServerAboutToStart(event: ServerAboutToStartEvent) {
        // Fail fast during initial startup if active selection config syntax is invalid.
        SelectionDataRepository.load(event.server)
    }

    @JvmStatic
    @SubscribeEvent
    fun onDatapackSync(event: OnDatapackSyncEvent) {
        val selectionData = SelectionDataRepository.load(event.playerList.server)
        val activeInWorld = ClassSelectorScope.isActiveIn(event.playerList.server)

        if (event.player != null) {
            ClassSelectorNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with { event.player },
                SyncClassesPacket.fromSelectionData(activeInWorld, selectionData)
            )
            return
        }

        event.playerList.players.forEach { online ->
            ClassSelectorNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with { online },
                SyncClassesPacket.fromSelectionData(activeInWorld, selectionData)
            )
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onPlayerJoin(event: PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val selectionData = SelectionDataRepository.getOrLoad()
        val activeInWorld = ClassSelectorScope.isActiveIn(player.server)
        ClassSelectorNetwork.CHANNEL.send(
            PacketDistributor.PLAYER.with { player },
            SyncClassesPacket.fromSelectionData(activeInWorld, selectionData)
        )
        OnboardingVisibilitySync.sync(player.server)

        if (!activeInWorld) {
            return
        }

        if (!OnboardingIntegration.hasCompletedOnboarding(player)) {
            player.setGameMode(GameType.SPECTATOR)
            ClassSelectorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { player }, RequestOpenMenuPacket())
            return
        }

        if (PersonalRespawnService.isReselectingSpawn(player)) {
            player.setGameMode(GameType.SPECTATOR)
            ClassSelectorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { player }, RequestOpenMenuPacket(true))
        } else {
            PersonalRespawnService.releasePlayerFromSpectator(player)
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onClone(event: PlayerEvent.Clone) {
        if (!event.isWasDeath) return
        val original = event.original as? ServerPlayer ?: return
        val cloned = event.entity as? ServerPlayer ?: return
        val selected = original.persistentData.getString(KitApplicator.SELECTED_CLASS_TAG)
        if (selected.isNotBlank()) {
            cloned.persistentData.putString(KitApplicator.SELECTED_CLASS_TAG, selected)
        }
        PersonalRespawnService.copyRespawnPoint(original, cloned)
        OnboardingIntegration.copyPersistentState(original, cloned)
        PersonalRespawnService.scheduleRespawnProtection(cloned)
    }

    @JvmStatic
    @SubscribeEvent
    fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        OnboardingVisibilitySync.sync(player.server)
    }
}
