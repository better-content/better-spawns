package com.bettercontent.betterspawns.respawn

import com.bettercontent.betterspawns.ClassSelectorMod
import com.bettercontent.betterspawns.ClassSelectorScope
import com.bettercontent.betterspawns.integration.OnboardingIntegration
import com.bettercontent.betterspawns.integration.OnboardingVisibilitySync
import com.bettercontent.betterspawns.embark.SelectionDataRepository
import com.bettercontent.betterspawns.network.ClassSelectorNetwork
import com.bettercontent.betterspawns.network.RequestOpenMenuPacket
import com.bettercontent.betterspawns.network.SyncClassesPacket
import com.bettercontent.betterspawns.network.SelectionNoticePacket
import com.mojang.brigadier.Command
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.network.PacketDistributor

@Mod.EventBusSubscriber(modid = ClassSelectorMod.MOD_ID)
object PersonalRespawnEvents {
    @JvmStatic
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onLivingDeath(event: LivingDeathEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (!ClassSelectorScope.isActiveIn(player.server)) return
        if (!OnboardingIntegration.hasCompletedOnboarding(player) || !PersonalRespawnService.hasRespawnPoint(player)) return

        PersonalRespawnService.refreshVanillaRespawnPosition(player)
    }

    @JvmStatic
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onPlayerSetSpawn(event: PlayerSetSpawnEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (!ClassSelectorScope.isActiveIn(player.server)) return
        if (!OnboardingIntegration.hasCompletedOnboarding(player)) return
        if (!PersonalRespawnService.hasRespawnPoint(player)) return

        // Bed and respawn-anchor style updates are non-forced; keep the class-locked respawn authoritative.
        if (!event.isForced && event.newSpawn != null) {
            event.isCanceled = true
        }
    }

    @JvmStatic
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        val player = event.entity as? ServerPlayer ?: return
        PersonalRespawnService.handleRespawn(player)
    }

    @JvmStatic
    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        RespawnTaskScheduler.tick(event.server)
        if (event.server.tickCount % 20 == 0) {
            OnboardingVisibilitySync.sync(event.server)
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("better_spawns")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.literal("open")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .executes { ctx ->
                                    val targets = EntityArgument.getPlayers(ctx, "targets")
                                    val data = SelectionDataRepository.getOrLoad()
                                    targets.forEach { player ->
                                        ClassSelectorNetwork.CHANNEL.send(
                                            PacketDistributor.PLAYER.with { player },
                                            SyncClassesPacket.fromSelectionData(ClassSelectorScope.isActiveIn(player.server), data)
                                        )
                                        ClassSelectorNetwork.CHANNEL.send(
                                            PacketDistributor.PLAYER.with { player }, RequestOpenMenuPacket()
                                        )
                                    }
                                    Command.SINGLE_SUCCESS
                                }
                        )
                )
                .then(
                    Commands.literal("resetrespawn")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .executes { ctx ->
                                    val targets = EntityArgument.getPlayers(ctx, "targets")
                                    targets.forEach { player ->
                                        PersonalRespawnService.beginSpawnReselection(player)
                                        ClassSelectorNetwork.CHANNEL.send(
                                            PacketDistributor.PLAYER.with { player },
                                            RequestOpenMenuPacket(true)
                                        )
                                        ClassSelectorNetwork.CHANNEL.send(
                                            PacketDistributor.PLAYER.with { player },
                                            SelectionNoticePacket("Scout in spectator, then press Shift+K to set your new spawn.", false)
                                        )
                                    }
                                    Command.SINGLE_SUCCESS
                                }
                        )
                )
        )
    }
}
