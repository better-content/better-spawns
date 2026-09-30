package com.bettercontent.betterspawns.respawn

import net.minecraft.server.MinecraftServer
import java.util.TreeMap

object RespawnTaskScheduler {
    private val scheduledTasks: MutableMap<Long, MutableList<(MinecraftServer) -> Unit>> = TreeMap()

    fun schedule(server: MinecraftServer, delayTicks: Long, task: (MinecraftServer) -> Unit) {
        val executeAt = server.overworld().gameTime + delayTicks.coerceAtLeast(0)
        scheduledTasks.computeIfAbsent(executeAt) { mutableListOf() }.add(task)
    }

    fun tick(server: MinecraftServer) {
        val now = server.overworld().gameTime
        scheduledTasks.remove(now)?.forEach { it(server) }
    }
}
