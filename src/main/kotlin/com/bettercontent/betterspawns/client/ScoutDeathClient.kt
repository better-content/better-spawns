package com.bettercontent.betterspawns.client

import com.bettercontent.betterdeathsdoor.client.DeathRecapOverlay
import com.bettercontent.betterdeathsdoor.client.ScoutDeathAction
import com.bettercontent.betterspawns.network.ClassSelectorNetwork
import com.bettercontent.betterspawns.network.ScoutRequestPacket
import com.bettercontent.betterspawns.network.ScoutStatusPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.network.chat.Component
import java.util.UUID

/** Optional Door UI provider. Its server status is the only source of availability. */
object ScoutDeathClient : ScoutDeathAction {
    private var registered = false
    private var episode: UUID? = null
    private var eligible = false
    private var pending = false
    private var failure = ""
    private var location = ""
    private val closed = LinkedHashSet<UUID>()

    fun register() {
        if (registered) return
        DeathRecapOverlay.registerScoutAction(this)
        registered = true
    }

    fun receive(packet: ScoutStatusPacket) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || !mc.player!!.isDeadOrDying || mc.level == null ||
            mc.level!!.levelData.isHardcore || packet.episode in closed) return
        if (episode != null && episode != packet.episode) return // stale ack from a different death
        episode = packet.episode
        eligible = packet.available
        pending = packet.pending
        failure = packet.error
        location = packet.location
    }

    fun disconnected() {
        episode = null; eligible = false; pending = false; failure = ""; location = ""; closed.clear()
    }

    override fun state(screen: DeathScreen): ScoutDeathAction.State {
        val mc = Minecraft.getInstance()
        if (mc.screen !== screen || mc.player?.isDeadOrDying != true || mc.level?.levelData?.isHardcore != false) {
            return ScoutDeathAction.State.hidden()
        }
        val error = when {
            failure.isNotBlank() -> Component.literal(failure)
            location.isNotBlank() -> Component.literal("Your recovery cache: $location")
            else -> null
        }
        return ScoutDeathAction.State(eligible && episode != null, pending, error)
    }

    override fun request(screen: DeathScreen) {
        if (!state(screen).available() || pending) return
        val current = episode ?: return
        pending = true // disable both actions before the server reply; never send twice
        failure = ""
        ClassSelectorNetwork.CHANNEL.sendToServer(ScoutRequestPacket(current))
    }

    override fun close(screen: DeathScreen) {
        episode?.let {
            closed += it
            if (closed.size > 64) closed.remove(closed.first())
        }
        episode = null; eligible = false; pending = false; failure = ""; location = ""
    }
}
