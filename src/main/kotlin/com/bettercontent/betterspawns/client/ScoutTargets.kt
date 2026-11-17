package com.bettercontent.betterspawns.client

import com.bettercontent.betterspawns.network.SyncScoutTargetsPacket
import net.minecraft.client.Minecraft
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos
import java.util.UUID

/** Client cache of server positions, not client entity tracking or chunk queries. */
object ScoutTargets {
    private var dimension: String? = null
    private var receivedTick: Int = Int.MIN_VALUE
    private var targets: List<SyncScoutTargetsPacket.Target> = emptyList()

    fun replace(packet: SyncScoutTargetsPacket) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (!player.isSpectator || mc.level?.dimension()?.location()?.toString() != packet.dimension) {
            clear(); return
        }
        dimension = packet.dimension
        receivedTick = player.tickCount
        targets = packet.targets.filter { it.id != player.uuid }
    }

    fun current(mc: Minecraft): List<SyncScoutTargetsPacket.Target> {
        val player = mc.player
        if (player == null || !player.isSpectator || mc.level?.dimension()?.location()?.toString() != dimension ||
            player.tickCount - receivedTick >= 60 || player.tickCount < receivedTick) {
            clear(); return emptyList()
        }
        return targets
    }

    fun glowIds(mc: Minecraft): Set<UUID> = current(mc).mapTo(hashSetOf()) { it.id }
    fun clear() { targets = emptyList(); dimension = null; receivedTick = Int.MIN_VALUE }

    fun draw(mc: Minecraft, graphics: net.minecraft.client.gui.GuiGraphics, width: Int, height: Int) {
        val viewer = mc.player ?: return
        if (width < 100 || height < 90) return
        val targets = current(mc)
        val font = mc.font
        val maxX = width / 2 - 66
        val maxY = height / 2 - 36
        for (target in targets) {
            val dx = target.x - viewer.x
            val dz = target.z - viewer.z
            val heading = Math.toRadians(Math.toDegrees(atan2(-dx, dz)) - viewer.yRot)
            val east = sin(heading)
            val north = -cos(heading)
            val factor = 1.0 / maxOf(kotlin.math.abs(east) / maxX.coerceAtLeast(1),
                kotlin.math.abs(north) / maxY.coerceAtLeast(1), 0.0001)
            val x = (width / 2 + east * factor).roundToInt().coerceIn(4, width - 4)
            val y = (height / 2 + north * factor).roundToInt().coerceIn(18, height - 20)
            val arrow = if (kotlin.math.abs(east) > kotlin.math.abs(north)) {
                if (east < 0) "←" else "→"
            } else if (north < 0) "↑" else "↓"
            val distance = hypot(hypot(dx, dz), target.y - viewer.y).roundToInt()
            val text = "$arrow ${target.name} · ${distance}m"
            val clipped = font.plainSubstrByWidth(text, (width - 16).coerceAtMost(190))
            val left = (x - font.width(clipped) / 2).coerceIn(4, (width - 4 - font.width(clipped)).coerceAtLeast(4))
            graphics.fill(left - 3, y - 2, left + font.width(clipped) + 3, y + font.lineHeight + 2, 0xB810201A.toInt())
            graphics.drawString(font, clipped, left, y, 0xFFE9D9AD.toInt(), false)
        }
    }
}
