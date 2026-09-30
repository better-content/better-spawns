package com.bettercontent.betterspawns.integration

import com.bettercontent.betterspawns.ClassSelectorScope
import com.bettercontent.betterspawns.network.ClassSelectorNetwork
import com.bettercontent.betterspawns.network.SyncOnboardingPlayersPacket
import net.minecraft.server.MinecraftServer
import net.minecraftforge.network.PacketDistributor

object OnboardingVisibilitySync {
    fun sync(server: MinecraftServer) {
        if (!ClassSelectorScope.isActiveIn(server)) return
        val onboarding = server.playerList.players
            .filterNot(OnboardingIntegration::hasCompletedOnboarding)
            .mapTo(linkedSetOf()) { it.uuid }
        server.playerList.players.forEach { player ->
            val visible = if (player.uuid in onboarding) onboarding else emptySet()
            ClassSelectorNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with { player },
                SyncOnboardingPlayersPacket(visible)
            )
        }
    }
}
