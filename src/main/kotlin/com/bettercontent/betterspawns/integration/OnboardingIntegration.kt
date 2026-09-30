package com.bettercontent.betterspawns.integration

import com.bettercontent.betterspawns.respawn.PersonalRespawnPoint
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.player.PlayerEvent

class PlayerStartFinalizedEvent(
    player: ServerPlayer,
    val kitId: String,
    val spawnId: String
) : PlayerEvent(player)

class PlayerStartingKitSelectedEvent(
    player: ServerPlayer,
    val kitId: String
) : PlayerEvent(player)

class PlayerStartingSpawnSelectedEvent(
    player: ServerPlayer,
    val spawnId: String
) : PlayerEvent(player)

object OnboardingIntegration {
    const val ONBOARDING_COMPLETE_TAG: String = "tmp.onboarding_complete"
    const val STARTING_KIT_TAG: String = "tmp.starting_kit"
    const val STARTING_SITE_TAG: String = "tmp.starting_site"

    @JvmStatic
    fun hasCompletedOnboarding(player: ServerPlayer): Boolean = player.persistentData.getBoolean(ONBOARDING_COMPLETE_TAG)

    @JvmStatic
    fun getStartingKit(player: ServerPlayer): String? =
        player.persistentData.getString(STARTING_KIT_TAG).takeIf { it.isNotBlank() }

    @JvmStatic
    fun getStartingSite(player: ServerPlayer): String? =
        player.persistentData.getString(STARTING_SITE_TAG).takeIf { it.isNotBlank() }

    internal fun buildSpawnId(point: PersonalRespawnPoint): String = "${point.dim}@${point.x},${point.y},${point.z}"

    internal fun recordStartingKit(player: ServerPlayer, kitId: String) {
        val previous = getStartingKit(player)
        player.persistentData.putString(STARTING_KIT_TAG, kitId)
        if (previous != kitId) {
            MinecraftForge.EVENT_BUS.post(PlayerStartingKitSelectedEvent(player, kitId))
        }
    }

    internal fun recordStartingSite(player: ServerPlayer, spawnId: String) {
        val previous = getStartingSite(player)
        player.persistentData.putString(STARTING_SITE_TAG, spawnId)
        if (previous != spawnId) {
            MinecraftForge.EVENT_BUS.post(PlayerStartingSpawnSelectedEvent(player, spawnId))
        }
    }

    internal fun finalizeOnboarding(player: ServerPlayer, kitId: String, spawnId: String): Boolean {
        if (hasCompletedOnboarding(player)) return false
        recordStartingKit(player, kitId)
        recordStartingSite(player, spawnId)
        player.persistentData.putBoolean(ONBOARDING_COMPLETE_TAG, true)
        MinecraftForge.EVENT_BUS.post(PlayerStartFinalizedEvent(player, kitId, spawnId))
        return true
    }

    internal fun copyPersistentState(from: ServerPlayer, to: ServerPlayer) {
        if (from.persistentData.contains(ONBOARDING_COMPLETE_TAG)) {
            to.persistentData.putBoolean(ONBOARDING_COMPLETE_TAG, from.persistentData.getBoolean(ONBOARDING_COMPLETE_TAG))
        }

        getStartingKit(from)?.let { to.persistentData.putString(STARTING_KIT_TAG, it) }
        getStartingSite(from)?.let { to.persistentData.putString(STARTING_SITE_TAG, it) }
    }
}
