package com.bettercontent.classselector.respawn

import com.bettercontent.classselector.ClassSelectorMod
import com.bettercontent.classselector.ClassSelectorScope
import com.bettercontent.classselector.integration.OnboardingIntegration
import com.mojang.brigadier.Command
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.entity.monster.Enemy
import kotlin.math.abs

private const val FX_DURATION_TICKS = 60L
private const val FX_PULSE_EVERY_TICKS = 2L
private const val FX_SPREAD = 1.0

private const val FX_COUNT_SOUL_PER_PULSE = 70
private const val FX_COUNT_CHARGE_PER_PULSE = 30
private const val FX_COUNT_POP_PER_PULSE = 20
private const val FX_COUNT_BLUE_PER_PULSE = 18

private const val SOUND_VOL = 6.0
private const val SOUND_MINVOL = 1.0
private const val SOUND_PITCH_BELL = 0.75
private const val SOUND_PITCH_PORTAL = 0.9
private const val SOUND_PITCH_WARDEN = 0.8
private const val SOUND_PITCH_EVOKER = 0.9
private const val RESPAWN_REPEL_RADIUS = 64.0
private val RESPAWN_PROTECTION_DELAYS_TICKS = longArrayOf(1L, 3L, 7L)
const val RESPAWN_PURGE_TAG = "class_selector:respawn_purge"
private const val RESPAWN_PURGE_TAG_LEGACY = "classselector:respawn_purge"
internal fun isInsideRespawnPurge(dx: Double, dy: Double, dz: Double): Boolean =
    dx * dx + dy * dy + dz * dz <= RESPAWN_REPEL_RADIUS * RESPAWN_REPEL_RADIUS

data class PersonalRespawnPoint(val dim: String, val x: Int, val y: Int, val z: Int)
data class PreparedRespawnPoint(val point: PersonalRespawnPoint, val sitePrepared: Boolean, val locationAdjusted: Boolean)

object PersonalRespawnService {
    private const val RESELECT_SPAWN_TAG = "class_selector:reselect_spawn"
    private const val RESPAWN_DIM_TAG = "class_selector:respawn_dim"
    private const val RESPAWN_X_TAG = "class_selector:respawn_x"
    private const val RESPAWN_Y_TAG = "class_selector:respawn_y"
    private const val RESPAWN_Z_TAG = "class_selector:respawn_z"

    fun assignCurrentLocation(player: ServerPlayer): PersonalRespawnPoint {
        val point = PersonalRespawnPoint(
            dim = dimensionId(player.serverLevel()),
            x = player.blockX,
            y = player.blockY,
            z = player.blockZ
        )
        return setRespawnPoint(player, point).point
    }

    fun hasRespawnPoint(player: ServerPlayer): Boolean =
        player.persistentData.contains(RESPAWN_DIM_TAG) &&
            player.persistentData.contains(RESPAWN_X_TAG) &&
            player.persistentData.contains(RESPAWN_Y_TAG) &&
            player.persistentData.contains(RESPAWN_Z_TAG)

    fun getRespawnPoint(player: ServerPlayer): PersonalRespawnPoint? {
        if (!hasRespawnPoint(player)) return null
        return PersonalRespawnPoint(
            dim = player.persistentData.getString(RESPAWN_DIM_TAG),
            x = player.persistentData.getInt(RESPAWN_X_TAG),
            y = player.persistentData.getInt(RESPAWN_Y_TAG),
            z = player.persistentData.getInt(RESPAWN_Z_TAG)
        )
    }

    fun setRespawnPoint(player: ServerPlayer, point: PersonalRespawnPoint): PreparedRespawnPoint {
        val preparedPoint = prepareRespawnPoint(player.server, point)
        commitPreparedRespawnPoint(player, preparedPoint)
        return preparedPoint
    }

    /**
     * Resolves an onboarding site without changing blocks or player data.  The packet handler uses
     * this before it grants a kit, stores a permanent spawn, or marks onboarding complete.
     */
    fun validateOnboardingRespawnPoint(player: ServerPlayer, requestedPoint: PersonalRespawnPoint): PreparedRespawnPoint? {
        if (requestedPoint.dim != dimensionId(player.serverLevel())) return null
        if (abs(requestedPoint.x - player.blockX) > 1 || abs(requestedPoint.y - player.blockY) > 1 || abs(requestedPoint.z - player.blockZ) > 1) return null

        val level = player.serverLevel()
        val resolved = resolveRespawnPoint(level, requestedPoint) ?: return null
        return PreparedRespawnPoint(resolved, sitePrepared = false, locationAdjusted = resolved != requestedPoint)
    }

    fun commitPreparedRespawnPoint(player: ServerPlayer, approved: PreparedRespawnPoint): PreparedRespawnPoint {
        val level = resolveLevel(player.server, approved.point.dim)
            ?: error("Approved respawn dimension disappeared before commit")
        val feet = BlockPos(approved.point.x, approved.point.y, approved.point.z)
        check(hasStableBase(level, feet)) { "Approved respawn site lost its supporting block before commit" }
        val prepared = approved.copy(sitePrepared = prepareRespawnSite(level, feet))
        saveRespawnPoint(player, prepared.point)
        return prepared
    }

    fun clearRespawnPoint(player: ServerPlayer) {
        player.persistentData.remove(RESPAWN_DIM_TAG)
        player.persistentData.remove(RESPAWN_X_TAG)
        player.persistentData.remove(RESPAWN_Y_TAG)
        player.persistentData.remove(RESPAWN_Z_TAG)
        clearVanillaRespawnPosition(player)
    }

    fun isReselectingSpawn(player: ServerPlayer): Boolean = player.persistentData.getBoolean(RESELECT_SPAWN_TAG)

    fun beginSpawnReselection(player: ServerPlayer) {
        clearRespawnPoint(player)
        player.persistentData.putBoolean(RESELECT_SPAWN_TAG, true)
        player.setGameMode(GameType.SPECTATOR)
        val overworld = player.server.overworld()
        val spawn = overworld.sharedSpawnPos
        player.teleportTo(overworld, spawn.x + 0.5, spawn.y + 1.0, spawn.z + 0.5, player.yRot, player.xRot)
    }

    fun completeSpawnReselection(player: ServerPlayer, approved: PreparedRespawnPoint): PreparedRespawnPoint {
        check(isReselectingSpawn(player)) { "Player is not selecting a new spawn" }
        val committed = commitPreparedRespawnPoint(player, approved)
        player.persistentData.remove(RESELECT_SPAWN_TAG)
        return committed
    }

    fun releasePlayerFromSpectator(player: ServerPlayer): Boolean {
        if (!ClassSelectorScope.isActiveIn(player.server)) return false
        if (!OnboardingIntegration.hasCompletedOnboarding(player)) return false
        val point = getRespawnPoint(player) ?: return false
        if (!player.isSpectator) return true

        player.setGameMode(GameType.SURVIVAL)
        teleportPlayerToRespawnPoint(player.server, player, point)
        scheduleRespawnProtection(player.server, player.uuid)
        return true
    }

    fun handleRespawn(player: ServerPlayer) {
        if (!ClassSelectorScope.isActiveIn(player.server)) return
        if (!OnboardingIntegration.hasCompletedOnboarding(player)) return

        getRespawnPoint(player)?.let { point ->
            teleportPlayerToRespawnPoint(player.server, player, point)
        }

        scheduleRespawnProtection(player.server, player.uuid)
    }

    fun scheduleRespawnProtection(player: ServerPlayer) {
        if (!ClassSelectorScope.isActiveIn(player.server)) return
        if (!OnboardingIntegration.hasCompletedOnboarding(player)) return

        scheduleRespawnProtection(player.server, player.uuid)
    }

    fun copyRespawnPoint(from: ServerPlayer, to: ServerPlayer) {
        val point = getRespawnPoint(from) ?: return
        saveRespawnPoint(to, point)
    }

    fun refreshVanillaRespawnPosition(player: ServerPlayer): Boolean {
        val point = getRespawnPoint(player) ?: return false
        saveRespawnPoint(player, point)
        return true
    }

    private fun saveRespawnPoint(player: ServerPlayer, point: PersonalRespawnPoint) {
        player.persistentData.putString(RESPAWN_DIM_TAG, point.dim)
        player.persistentData.putInt(RESPAWN_X_TAG, point.x)
        player.persistentData.putInt(RESPAWN_Y_TAG, point.y)
        player.persistentData.putInt(RESPAWN_Z_TAG, point.z)

        val location = ResourceLocation.tryParse(point.dim) ?: return
        val levelKey = ResourceKey.create(Registries.DIMENSION, location)
        player.setRespawnPosition(levelKey, BlockPos(point.x, point.y, point.z), player.yRot, true, false)
    }

    internal fun prepareRespawnPoint(server: MinecraftServer, requestedPoint: PersonalRespawnPoint): PreparedRespawnPoint {
        val level = resolveLevel(server, requestedPoint.dim)
            ?: return PreparedRespawnPoint(requestedPoint, sitePrepared = false, locationAdjusted = false)
        val point = resolveRespawnPoint(level, requestedPoint)
            ?: error("No solid block below the requested respawn site")
        val targetFeetPos = BlockPos(point.x, point.y, point.z)
        val sitePrepared = prepareRespawnSite(level, targetFeetPos)

        return PreparedRespawnPoint(
            point = point,
            sitePrepared = sitePrepared,
            locationAdjusted = point != requestedPoint
        )
    }

    private fun resolveRespawnPoint(level: ServerLevel, requestedPoint: PersonalRespawnPoint): PersonalRespawnPoint? {
        val origin = clampFeetPos(level, BlockPos(requestedPoint.x, requestedPoint.y, requestedPoint.z))
        val target = findFirstSolidFeetPos(level, origin) ?: return null
        return PersonalRespawnPoint(dimensionId(level), target.x, target.y, target.z)
    }

    private fun clampFeetPos(level: ServerLevel, pos: BlockPos): BlockPos {
        val clampedY = pos.y.coerceIn(level.minBuildHeight + 1, level.maxBuildHeight - 2)
        return BlockPos(pos.x, clampedY, pos.z)
    }

    private fun isValidFeetPos(level: ServerLevel, feetPos: BlockPos): Boolean {
        val basePos = feetPos.below()
        val headPos = feetPos.above()
        if (feetPos.y < level.minBuildHeight + 1 || headPos.y >= level.maxBuildHeight) return false
        if (!level.isInWorldBounds(basePos) || !level.isInWorldBounds(feetPos) || !level.isInWorldBounds(headPos)) return false

        val baseState = level.getBlockState(basePos)
        val feetState = level.getBlockState(feetPos)
        val headState = level.getBlockState(headPos)

        return baseState.isFaceSturdy(level, basePos, Direction.UP) &&
            feetState.isAir &&
            headState.isAir
    }

    private fun hasStableBase(level: ServerLevel, feetPos: BlockPos): Boolean {
        val basePos = feetPos.below()
        return level.isInWorldBounds(basePos) && level.getBlockState(basePos).isFaceSturdy(level, basePos, Direction.UP)
    }

    private fun findFirstSolidFeetPos(level: ServerLevel, origin: BlockPos): BlockPos? {
        for (baseY in origin.y.coerceAtMost(level.maxBuildHeight - 3) downTo level.minBuildHeight) {
            val feet = BlockPos(origin.x, baseY + 1, origin.z)
            if (hasStableBase(level, feet)) return feet
        }
        return null
    }

    private fun prepareRespawnSite(level: ServerLevel, feetPos: BlockPos): Boolean {
        val basePos = feetPos.below()
        val headPos = feetPos.above()
        var changed = false

        if (!level.getBlockState(basePos).`is`(Blocks.CRYING_OBSIDIAN)) {
            level.setBlockAndUpdate(basePos, Blocks.CRYING_OBSIDIAN.defaultBlockState())
            changed = true
        }
        if (!level.getBlockState(feetPos).isAir) {
            level.setBlockAndUpdate(feetPos, Blocks.AIR.defaultBlockState())
            changed = true
        }
        if (!level.getBlockState(headPos).isAir) {
            level.setBlockAndUpdate(headPos, Blocks.AIR.defaultBlockState())
            changed = true
        }

        return changed
    }

    private fun clearVanillaRespawnPosition(player: ServerPlayer) {
        player.setRespawnPosition(null, null, 0f, false, false)
    }

    private fun teleportPlayerToRespawnPoint(server: MinecraftServer, player: ServerPlayer, point: PersonalRespawnPoint) {
        val level = resolveLevel(server, point.dim) ?: player.serverLevel()
        val feetPos = BlockPos(point.x, point.y, point.z)
        if (!isValidFeetPos(level, feetPos)) {
            prepareRespawnSite(level, feetPos)
        }
        player.teleportTo(level, point.x + 0.5, point.y.toDouble(), point.z + 0.5, player.yRot, player.xRot)
        RespawnTaskScheduler.schedule(server, 1) {
            playRespawnSoundForPlayer(server, player, point)
            spawnRespawnParticlesForPlayer(server, player, point)
        }
    }

    private fun scheduleRespawnProtection(server: MinecraftServer, playerId: java.util.UUID) {
        RESPAWN_PROTECTION_DELAYS_TICKS.forEach { delay ->
            RespawnTaskScheduler.schedule(server, delay) { scheduledServer ->
                val player = scheduledServer.playerList.getPlayer(playerId) ?: return@schedule
                applyRespawnProtection(player)
            }
        }
    }

    private fun applyRespawnProtection(player: ServerPlayer) {
        purgeHostileMobs(player)
    }

    private fun purgeHostileMobs(player: ServerPlayer) {
        val level = player.serverLevel()
        val hostiles = level.getEntities(player, player.boundingBox.inflate(RESPAWN_REPEL_RADIUS)) { entity ->
            entity is Enemy && isInsideRespawnPurge(entity.x - player.x, entity.y - player.y, entity.z - player.z)
        }
        hostiles.forEach { hostile ->
            hostile.persistentData.putBoolean(RESPAWN_PURGE_TAG, true)
            hostile.persistentData.putBoolean(RESPAWN_PURGE_TAG_LEGACY, true)
            hostile.discard()
        }
    }

    private fun playRespawnSoundForPlayer(server: MinecraftServer, player: ServerPlayer, point: PersonalRespawnPoint) {
        val x = point.x + 0.5
        val y = point.y + 1.0
        val z = point.z + 0.5
        val name = player.scoreboardName
        runSilentServerCommand(
            server,
            "playsound minecraft:block.bell.use master $name $x $y $z ${SOUND_VOL * 0.75} $SOUND_PITCH_BELL $SOUND_MINVOL"
        )
        runSilentServerCommand(
            server,
            "playsound minecraft:block.end_portal.spawn master $name $x $y $z ${SOUND_VOL * 0.85} $SOUND_PITCH_PORTAL $SOUND_MINVOL"
        )
        runSilentServerCommand(
            server,
            "playsound minecraft:entity.warden.ambient master $name $x $y $z ${SOUND_VOL * 0.45} $SOUND_PITCH_WARDEN $SOUND_MINVOL"
        )
        runSilentServerCommand(
            server,
            "playsound minecraft:entity.evoker.prepare_summon master $name $x $y $z ${SOUND_VOL * 0.55} $SOUND_PITCH_EVOKER $SOUND_MINVOL"
        )
    }

    private fun spawnRespawnParticlesForPlayer(server: MinecraftServer, player: ServerPlayer, point: PersonalRespawnPoint) {
        val pulses = (FX_DURATION_TICKS / FX_PULSE_EVERY_TICKS).coerceAtLeast(1)
        repeat(pulses.toInt()) { pulseIndex ->
            RespawnTaskScheduler.schedule(server, pulseIndex * FX_PULSE_EVERY_TICKS) {
                emitRespawnParticlesOnce(server, player, point)
            }
        }
    }

    private fun emitRespawnParticlesOnce(server: MinecraftServer, player: ServerPlayer, point: PersonalRespawnPoint) {
        val x = point.x + 0.5
        val y = point.y + 1.0
        val z = point.z + 0.5
        val targetName = player.scoreboardName
        val prefix = "execute in ${point.dim} run particle"
        runSilentServerCommand(
            server,
            "$prefix minecraft:sculk_soul $x $y $z $FX_SPREAD $FX_SPREAD $FX_SPREAD 0 $FX_COUNT_SOUL_PER_PULSE force $targetName"
        )
        runSilentServerCommand(
            server,
            "$prefix minecraft:sculk_charge $x $y $z $FX_SPREAD $FX_SPREAD $FX_SPREAD 0 $FX_COUNT_CHARGE_PER_PULSE force $targetName"
        )
        runSilentServerCommand(
            server,
            "$prefix minecraft:sculk_charge_pop $x $y $z $FX_SPREAD $FX_SPREAD $FX_SPREAD 0 $FX_COUNT_POP_PER_PULSE force $targetName"
        )
        runSilentServerCommand(
            server,
            "$prefix minecraft:soul_fire_flame $x $y $z $FX_SPREAD $FX_SPREAD $FX_SPREAD 0 $FX_COUNT_BLUE_PER_PULSE force $targetName"
        )
    }

    private fun runSilentServerCommand(server: MinecraftServer, command: String) {
        val source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput()
        server.commands.performPrefixedCommand(source, command)
    }

    private fun resolveLevel(server: MinecraftServer, dimensionId: String): ServerLevel? {
        val location = ResourceLocation.tryParse(dimensionId) ?: return null
        val key = ResourceKey.create(Registries.DIMENSION, location)
        return server.getLevel(key)
    }

    private fun dimensionId(level: ServerLevel): String = level.dimension().location().toString()
}
