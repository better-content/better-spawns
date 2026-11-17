package com.bettercontent.betterspawns.client

import net.minecraft.client.Minecraft
import java.util.UUID

object OnboardingPlayerVisibility {
    private var onboardingIds: Set<UUID> = emptySet()
    private val forcedGlowIds: MutableMap<UUID, Boolean> = mutableMapOf()

    fun replace(ids: Set<UUID>) {
        onboardingIds = ids.toSet()
    }

    fun tick(minecraft: Minecraft) {
        val level = minecraft.level ?: run {
            clear(minecraft)
            return
        }
        val localId = minecraft.player?.uuid
        val onboarding = if (localId != null && localId in onboardingIds) onboardingIds - localId else emptySet()
        val desired = onboarding + ScoutTargets.glowIds(minecraft)

        forcedGlowIds.toMap().forEach { (id, originallyGlowing) ->
            if (id !in desired || level.getPlayerByUUID(id) == null) {
                level.getPlayerByUUID(id)?.setGlowingTag(originallyGlowing)
                forcedGlowIds.remove(id)
            }
        }
        desired.forEach { id ->
            level.getPlayerByUUID(id)?.let { player ->
                forcedGlowIds.putIfAbsent(id, player.hasGlowingTag())
                player.setGlowingTag(true)
            }
        }
    }

    fun clear(minecraft: Minecraft) {
        minecraft.level?.let { level ->
            forcedGlowIds.forEach { (id, originallyGlowing) ->
                level.getPlayerByUUID(id)?.setGlowingTag(originallyGlowing)
            }
        }
        forcedGlowIds.clear()
        onboardingIds = emptySet()
    }
}
