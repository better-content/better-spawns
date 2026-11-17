package com.bettercontent.betterspawns.respawn

import com.bettercontent.betterdeathsdoor.RevivalManager
import com.bettercontent.betterdeathsdoor.api.event.InjuryEvent
import com.bettercontent.betterspawns.integration.DoorBridgeAvailability
import com.bettercontent.betterspawns.network.ClassSelectorNetwork
import com.bettercontent.betterspawns.network.RequestOpenMenuPacket
import com.bettercontent.betterspawns.network.ScoutStatusPacket
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.network.PacketDistributor
import java.util.UUID

/** Loaded only with Better Death's Door; it owns no native death/drop interception. */
object DeathScoutIntegration {
    fun register() {
        // An installed older Door may lack the bridge; leave native deaths unchanged.
        if (DoorBridgeAvailability.available()) MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun finalDeath(event: InjuryEvent.FinalDeath) {
        val player = event.entity as? ServerPlayer ?: return
        // Door publishes this before vanilla death drops: record ONLY the episode and location.
        RecoveryCustody.beginEpisode(player)
    }

    @SubscribeEvent
    fun tick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END || event.server.tickCount % 20 != 0) return
        event.server.playerList.players.forEach { if (RevivalManager.isFinalized(it)) sendEligibility(it) }
    }

    @SubscribeEvent
    fun login(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.persistentData.getBoolean(RecoveryCustody.REQUESTED)) {
            if (player.isDeadOrDying) player.persistentData.remove(RecoveryCustody.REQUESTED)
            else beginScout(player) // disconnect after native respawn, before the scout transition
        }
        sendEligibility(player)
    }

    @SubscribeEvent
    fun clone(event: PlayerEvent.Clone) {
        if (!event.isWasDeath) return
        val old = event.original as? ServerPlayer ?: return
        val next = event.entity as? ServerPlayer ?: return
        if (old.persistentData.getBoolean(RecoveryCustody.REQUESTED)) {
            next.persistentData.putBoolean(RecoveryCustody.REQUESTED, true)
            val location = old.persistentData.getString(RecoveryCustody.LOCATION)
            if (location.isNotEmpty()) next.persistentData.putString(RecoveryCustody.LOCATION, location)
        }
        // Ordinary respawn gets no old episode, death coordinates or custody flag.
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun respawn(event: PlayerEvent.PlayerRespawnEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.persistentData.getBoolean(RecoveryCustody.REQUESTED)) beginScout(player)
    }

    private fun beginScout(player: ServerPlayer) {
        // PersonalRespawnService remains the sole authority for committing the new spawn.
        PersonalRespawnService.beginSpawnReselection(player)
        player.persistentData.remove(RecoveryCustody.REQUESTED)
        ClassSelectorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { player }, RequestOpenMenuPacket(true))
        val location = player.persistentData.getString(RecoveryCustody.LOCATION)
        if (location.isNotEmpty()) player.sendSystemMessage(Component.literal("Public recovery cache: $location"))
        player.persistentData.remove(RecoveryCustody.LOCATION)
    }

    private fun sendEligibility(player: ServerPlayer) {
        if (!RevivalManager.isFinalized(player) || !RecoveryCustody.eligible(player) ||
            !player.isDeadOrDying || !RecoveryCustody.nativeDeathSettled(player)) return
        val tag = player.persistentData
        if (tag.getBoolean(RecoveryCustody.REQUESTED) || tag.getBoolean(RecoveryCustody.FAILED)) return
        val recovery = RecoveryRecordData.get(player.server)
        if (tag.hasUUID(RecoveryCustody.RECOVERY) &&
            tag.getUUID(RecoveryCustody.RECOVERY) !in recovery.records &&
            tag.getUUID(RecoveryCustody.RECOVERY) !in recovery.emptied) return
        ClassSelectorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { player },
            ScoutStatusPacket(tag.getUUID(RecoveryCustody.EPISODE), true, false,
                location = tag.getString(RecoveryCustody.LOCATION)))
    }

    fun request(player: ServerPlayer, episode: UUID) {
        val tag = player.persistentData
        if (!RevivalManager.isFinalized(player) || !player.isDeadOrDying ||
            !RecoveryCustody.eligible(player) || !RecoveryCustody.nativeDeathSettled(player) ||
            !tag.hasUUID(RecoveryCustody.EPISODE) || tag.getUUID(RecoveryCustody.EPISODE) != episode) {
            reject(player, episode, "Scout request expired; normal Respawn remains available.", false)
            return
        }
        if (tag.getBoolean(RecoveryCustody.REQUESTED)) return // one request per episode
        if (tag.getBoolean(RecoveryCustody.FAILED)) {
            reject(player, episode, "Scout custody failed; normal Respawn remains available. See recovery location in chat.", false)
            return
        }
        // A client can retry placement, but at most once every 20 server ticks per episode.
        val now = player.server.overworld().gameTime
        val last = tag.getLong(RecoveryCustody.LAST_ATTEMPT)
        if (tag.contains(RecoveryCustody.LAST_ATTEMPT) && now >= last && now - last < 20) {
            reject(player, episode, "Please wait before retrying Scout; normal Respawn remains available.")
            return
        }
        tag.putLong(RecoveryCustody.LAST_ATTEMPT, now)
        if (runCatching { RecoveryCustody.secureForScout(player) }.getOrNull() == null) {
            if (tag.getBoolean(RecoveryCustody.FAILED)) {
                val location = tag.getString(RecoveryCustody.LOCATION)
                if (location.isNotEmpty()) player.sendSystemMessage(Component.literal(
                    "Scout stopped safely. Any items already moved are in the public cache: $location"))
                reject(player, episode, "Scout custody failed closed; normal Respawn remains available.", false)
            } else {
                reject(player, episode, "No safe recovery site yet; normal Respawn remains available.")
            }
            return
        }
        val connection = player.connection ?: run {
            reject(player, episode, "Connection unavailable; recovery remains at the death site.")
            return
        }
        tag.putBoolean(RecoveryCustody.REQUESTED, true)
        ClassSelectorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { player }, ScoutStatusPacket(episode, true, true))
        try {
            // This is the native client-command path. It assigns the returned replacement to
            // ServerGamePacketListenerImpl.player and fires Clone/Respawn through PlayerList.
            connection.handleClientCommand(ServerboundClientCommandPacket(
                ServerboundClientCommandPacket.Action.PERFORM_RESPAWN))
        } catch (failure: Exception) {
            if (connection.player === player) { // respawn did not replace the connection's player
                tag.remove(RecoveryCustody.REQUESTED)
                reject(player, episode, "Scouting could not begin; custody remains safe. Retry or use Respawn.")
            }
        }
    }

    private fun reject(player: ServerPlayer, episode: UUID, message: String, available: Boolean = true) {
        ClassSelectorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { player },
            ScoutStatusPacket(episode, available, false, message,
                location = player.persistentData.getString(RecoveryCustody.LOCATION)))
    }
}
