package com.bettercontent.betterspawns.integration

import com.bettercontent.betterspawns.ClassSelectorScope
import com.bettercontent.betterspawns.network.ClassSelectorNetwork
import com.bettercontent.betterspawns.network.SyncOnboardingPlayersPacket
import com.bettercontent.betterspawns.network.SyncScoutTargetsPacket
import net.minecraft.server.MinecraftServer
import net.minecraftforge.network.PacketDistributor

object OnboardingVisibilitySync {
    fun sync(server: MinecraftServer) {
        if (!ClassSelectorScope.isActiveIn(server)) return
        val onboarding = server.playerList.players
            .filterNot(OnboardingIntegration::hasCompletedOnboarding)
            .mapTo(linkedSetOf()) { it.uuid }
        val byDimension = server.playerList.players.groupBy { it.level().dimension() }
        server.playerList.players.forEach { player ->
            val visible = if (player.uuid in onboarding) onboarding else emptySet()
            ClassSelectorNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with { player },
                SyncOnboardingPlayersPacket(visible)
            )
            if (player.isSpectator) {
                val others = byDimension[player.level().dimension()].orEmpty().asSequence()
                    .filter { it.uuid != player.uuid }
                    // All online players in this dimension, even when not entity-tracked.
                    .map { SyncScoutTargetsPacket.Target(it.uuid, it.displayName.string.take(64), it.x, it.y, it.z) }
                    .toList()
                ClassSelectorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { player },
                    SyncScoutTargetsPacket(player.level().dimension().location().toString(), others))
            }
        }
    }
}
