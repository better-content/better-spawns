package com.bettercontent.betterspawns.client

import net.minecraft.client.Minecraft
import java.util.UUID

object OnboardingPlayerVisibility {
    private var onboardingIds: Set<UUID> = emptySet()
    private val forcedGlowIds: MutableSet<UUID> = mutableSetOf()

    fun replace(ids: Set<UUID>) {
        onboardingIds = ids.toSet()
    }

    fun tick(minecraft: Minecraft) {
        val level = minecraft.level ?: run {
            clear(minecraft)
            return
        }
        val localId = minecraft.player?.uuid
        val desired = if (localId != null && localId in onboardingIds) onboardingIds - localId else emptySet()

        forcedGlowIds.toList().forEach { id ->
            if (id !in desired) {
                level.getPlayerByUUID(id)?.setGlowingTag(false)
                forcedGlowIds.remove(id)
            }
        }
        desired.forEach { id ->
            level.getPlayerByUUID(id)?.let { player ->
                player.setGlowingTag(true)
                forcedGlowIds += id
            }
        }
    }

    fun clear(minecraft: Minecraft) {
        minecraft.level?.let { level ->
            forcedGlowIds.forEach { id -> level.getPlayerByUUID(id)?.setGlowingTag(false) }
        }
        forcedGlowIds.clear()
        onboardingIds = emptySet()
    }
}
