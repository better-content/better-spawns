package com.bettercontent.betterspawns.respawn

import com.bettercontent.betterspawns.ClassSelectorScope
import com.mojang.logging.LogUtils
import com.bettercontent.betterspawns.integration.OnboardingIntegration
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraftforge.fml.ModList
import top.theillusivec4.curios.api.CuriosApi
import java.util.UUID
import kotlin.math.abs

/** Scout-only transfer of items still held *after* native final-death/drop callbacks. */
object RecoveryCustody {
    const val EPISODE = "better_spawns:scout_episode"
    const val RECOVERY = "better_spawns:scout_recovery"
    const val REQUESTED = "better_spawns:scout_requested"
    const val READY = "better_spawns:scout_custody_ready"
    const val LOCATION = "better_spawns:recovery_location"
    const val DEATH_DIM = "better_spawns:death_dimension"
    const val DEATH_X = "better_spawns:death_x"
    const val DEATH_Y = "better_spawns:death_y"
    const val DEATH_Z = "better_spawns:death_z"
    const val FINAL_TICK = "better_spawns:final_tick"
    const val FAILED = "better_spawns:scout_custody_failed"
    const val LAST_ATTEMPT = "better_spawns:scout_last_attempt"
    const val SCOUT_ORIGIN = "better_spawns:scout_origin"

    private val log = LogUtils.getLogger()
    private data class Source(val label: String, val stack: ItemStack, val read: () -> ItemStack,
                              val clear: () -> Unit)
    data class Result(val record: RecoveryRecordData.Record?)

    fun eligible(player: ServerPlayer): Boolean =
        ClassSelectorScope.isActiveIn(player.server) && OnboardingIntegration.hasCompletedOnboarding(player) &&
            PersonalRespawnService.hasRespawnPoint(player) && !player.server.isHardcore &&
            !player.isSpectator && !player.isCreative

    /** FinalDeath notification only: no item/block/custody changes before native drops complete. */
    fun beginEpisode(player: ServerPlayer) {
        if (!eligible(player)) return
        val tag = player.persistentData
        tag.putUUID(EPISODE, UUID.randomUUID())
        tag.putString(DEATH_DIM, player.serverLevel().dimension().location().toString())
        tag.putInt(DEATH_X, player.blockX)
        tag.putInt(DEATH_Y, player.blockY)
        tag.putInt(DEATH_Z, player.blockZ)
        tag.putLong(FINAL_TICK, player.server.overworld().gameTime)
        val previous = PersonalRespawnService.getRespawnPoint(player) ?: return
        tag.put(SCOUT_ORIGIN, CompoundTag().apply {
            putString("dimension", previous.dim)
            putInt("x", previous.x); putInt("y", previous.y); putInt("z", previous.z)
        })
        tag.remove(RECOVERY); tag.remove(LOCATION); tag.remove(REQUESTED); tag.remove(READY)
        tag.remove(FAILED); tag.remove(LAST_ATTEMPT)
    }

    fun previousSpawn(player: ServerPlayer): PersonalRespawnPoint? {
        val tag = player.persistentData.getCompound(SCOUT_ORIGIN)
        if (!tag.contains("dimension", 8) || !tag.contains("x", 3) ||
            !tag.contains("y", 3) || !tag.contains("z", 3)) return null
        return PersonalRespawnPoint(tag.getString("dimension"), tag.getInt("x"), tag.getInt("y"), tag.getInt("z"))
    }

    fun nativeDeathSettled(player: ServerPlayer): Boolean =
        player.persistentData.hasUUID(EPISODE) &&
            player.server.overworld().gameTime - player.persistentData.getLong(FINAL_TICK) >= 20

    /** A failed site leaves the ordinary dead player and native Respawn path untouched. */
    fun secureForScout(player: ServerPlayer): Result? {
        val tag = player.persistentData
        if (!eligible(player) || !player.isDeadOrDying || !nativeDeathSettled(player) ||
            tag.getBoolean(FAILED) ||
            tag.getString(DEATH_DIM) != player.serverLevel().dimension().location().toString()) return null
        val previous = previousSpawn(player) ?: return null
        val dim = ResourceLocation.tryParse(previous.dim) ?: return null
        val level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dim)) ?: return null
        val origin = BlockPos(previous.x, previous.y, previous.z)
        if (!level.isInWorldBounds(origin) || !level.isInWorldBounds(origin.above())) return null
        val data = RecoveryRecordData.get(player.server)
        if (tag.getBoolean(READY)) {
            val record = if (tag.hasUUID(RECOVERY)) data.records[tag.getUUID(RECOVERY)] else null
            if (tag.hasUUID(RECOVERY) && record == null && tag.getUUID(RECOVERY) !in data.emptied) return null
            if (record != null && !repairShells(level, record)) return null
            return Result(record) // idempotent retry; never collect player slots again
        }
        val allSlots = gatherInventorySlots(player)
        val sourceIndices = allSlots.indices.filter { !allSlots[it].stack.isEmpty }
        val sources = sourceIndices.map(allSlots::get)
        if (sources.isEmpty()) { tag.putBoolean(READY, true); return Result(null) }
        // Load only the old spawn's chunk. Other pages may use already-loaded neighbors;
        // if no safe site exists, leave the retained inventory untouched and allow Respawn.
        level.getChunkAt(origin)
        val places = findPlaces(level, origin, (sources.size + 53) / 54) ?: return null
        val id = UUID.randomUUID()
        // Pages begin empty: never put still-held player items in authoritative SavedData.
        val inventoryPages = List(places.size) { NonNullList.withSize(54, ItemStack.EMPTY) }
        val record = RecoveryRecordData.Record(id, player.uuid, previous.dim, places, inventoryPages)
        val created = mutableListOf<BlockPos>()
        try {
            data.keep(record)
            places.forEachIndexed { index, pos ->
                check(level.getBlockState(pos).isAir && level.setBlockAndUpdate(pos,
                    RecoveryCache.BLOCK.get().defaultBlockState()))
                created += pos
                val cache = level.getBlockEntity(pos) as? RecoveryCache.CacheEntity ?: error("Missing recovery shell")
                cache.identify(id, index)
            }
        } catch (failure: Exception) {
            // No inventory source has been touched; removing an empty record is safe.
            created.forEach { pos -> if (level.getBlockEntity(pos) is RecoveryCache.CacheEntity)
                level.removeBlock(pos, false) }
            data.forgetFailedCommit(id)
            return null
        }

        // Persist non-extractable original-slot evidence before any debit. Minecraft's player
        // and SavedData files are not saved atomically; this does not promise crash safety.
        record.beginAudit(sources.map { RecoveryRecordData.AuditSlot(it.label, it.stack) },
            player.server.overworld().gameTime)
        data.touch()
        // This flag is set before the first debit; any uncertain mutation fails Scout closed.
        tag.putBoolean(FAILED, true)
        tag.putUUID(RECOVERY, id)
        val first = places.first()
        tag.putString(LOCATION, "${record.dimension} ${first.x} ${first.y} ${first.z}")
        val attempted = mutableSetOf<Int>()
        var complete = true
        for ((index, source) in sources.withIndex()) {
            try {
                if (!ItemStack.matches(source.read(), source.stack)) { complete = false; break }
                attempted += index
                // A setter can throw *after* changing a slot. Observe it regardless of its result.
                val cleared = runCatching { source.clear() }.isSuccess
                val remaining = source.read()
                val debit = removedFrom(source.stack, remaining)
                if (debit == null) { complete = false; break }
                record.inventories[index / 54][index % 54] = debit
                data.touch()
                if (!cleared || !remaining.isEmpty) { complete = false; break }
            } catch (failure: Exception) {
                complete = false
                break
            }
        }
        // Include initially empty slots: a Curios callback can move a cleared item into
        // another slot. Cap each item's stored debit by the observed global net deficit.
        val observed = runCatching { allSlots.map { it.read().copy() } }.getOrNull()
        if (observed == null) {
            complete = false
            sources.indices.forEach { index -> record.inventories[index / 54][index % 54] = ItemStack.EMPTY }
            data.touch()
            log.error("Cannot read back all scout slots for episode {}; manual recovery may be needed", tag.getUUID(EPISODE))
        } else {
            for (slot in allSlots.indices) {
                if (slot !in sourceIndices && !ItemStack.matches(allSlots[slot].stack, observed[slot]))
                    complete = false
            }
            val grouped = BooleanArray(sources.size)
            for (index in sources.indices) {
                if (grouped[index]) continue
                val original = sources[index].stack
                val members = sources.indices.filter {
                    !grouped[it] && ItemStack.isSameItemSameTags(original, sources[it].stack)
                }
                members.forEach { grouped[it] = true }
                val before = allSlots.sumOf {
                    if (!it.stack.isEmpty && ItemStack.isSameItemSameTags(original, it.stack)) it.stack.count else 0
                }
                val after = observed.sumOf {
                    if (!it.isEmpty && ItemStack.isSameItemSameTags(original, it)) it.count else 0
                }
                var allowance = (before - after).coerceAtLeast(0)
                if (after > before) complete = false
                for (member in members) {
                    val local = removedFrom(sources[member].stack, observed[sourceIndices[member]])
                    val amount = minOf(local?.count ?: 0, allowance)
                    record.inventories[member / 54][member % 54] = sources[member].stack.copy().also { it.count = amount }
                    data.touch()
                    allowance -= amount
                    if (local == null || member !in attempted || amount != sources[member].stack.count)
                        complete = false
                    if (local == null) log.error("Unverifiable scout custody slot {} for episode {}; manual recovery may be needed", member, tag.getUUID(EPISODE))
                }
            }
        }
        if (!complete) {
            // Never restore a debited slot or expose the audit. Retain only readback-confirmed
            // net losses publicly; preserve the original slot snapshot even if pages are empty.
            record.failAudit(if (observed == null) "unverifiable inventory readback"
                else "partial or unverifiable source transfer", player.server.overworld().gameTime)
            data.touch()
            tag.remove(READY)
            if (record.empty()) created.forEach { pos ->
                if ((level.getBlockEntity(pos) as? RecoveryCache.CacheEntity)?.recoveryId == id)
                    level.removeBlock(pos, false)
            }
            log.error("Scout custody FAILED CLOSED: episode {}, player {}, record {}, location {}; opaque audit retained for manual reconciliation; native Respawn remains available",
                tag.getUUID(EPISODE), player.uuid, id, tag.getString(LOCATION))
            return null
        }
        record.clearAudit()
        data.touch()
        tag.putBoolean(READY, true)
        tag.remove(FAILED)
        return Result(record)
    }

    /** Only unchanged item identity and observed count reduction can be credited. */
    private fun removedFrom(original: ItemStack, remaining: ItemStack): ItemStack? {
        if (remaining.isEmpty) return original.copy()
        if (!ItemStack.isSameItemSameTags(original, remaining) || remaining.count > original.count) return null
        return original.copy().also { it.count = original.count - remaining.count }
    }

    private fun repairShells(level: ServerLevel, record: RecoveryRecordData.Record): Boolean {
        if (record.dimension != level.dimension().location().toString()) return false
        record.pages.forEachIndexed { index, pos ->
            if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below())) return false
            val existing = level.getBlockEntity(pos) as? RecoveryCache.CacheEntity
            if (existing?.recoveryId == record.id && existing.page == index) return@forEachIndexed
            if (!level.getBlockState(pos).isAir || !level.getBlockState(pos.below())
                    .isFaceSturdy(level, pos.below(), Direction.UP)) return false
            if (!level.setBlockAndUpdate(pos, RecoveryCache.BLOCK.get().defaultBlockState())) return false
            val restored = level.getBlockEntity(pos) as? RecoveryCache.CacheEntity ?: return false
            restored.identify(record.id, index)
        }
        return true
    }

    private fun gatherInventorySlots(player: ServerPlayer): List<Source> {
        val sources = mutableListOf<Source>()
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            val original = inventory.getItem(slot)
            val copy = original.copy()
            sources += Source("inventory:$slot", copy,
                { inventory.getItem(slot) },
                { inventory.setItem(slot, ItemStack.EMPTY) })
        }
        if (ModList.get().isLoaded("curios")) {
            val curios = CuriosApi.getCuriosInventory(player).resolve().orElse(null)
                ?: error("Curios inventory unavailable; Scout cannot verify retained items")
            curios.curios.forEach { (identifier, handler) ->
                listOf("equipped" to handler.stacks, "cosmetic" to handler.cosmeticStacks).forEach { (kind, slots) ->
                    for (slot in 0 until slots.slots) {
                        val original = slots.getStackInSlot(slot)
                        val copy = original.copy()
                        sources += Source("curios:$identifier:$kind:$slot", copy,
                            { slots.getStackInSlot(slot) },
                            { slots.setStackInSlot(slot, ItemStack.EMPTY) })
                    }
                }
            }
        }
        return sources
    }

    private fun findPlaces(level: ServerLevel, origin: BlockPos, count: Int): List<BlockPos>? {
        val selected = ArrayList<BlockPos>(count)
        // Death column first, then rings. Visit nearest valid Y in both directions.
        val low = level.minBuildHeight + 1
        val high = level.maxBuildHeight - 2
        if (low > high) return null
        val start = origin.y.coerceIn(low, high)
        val heights = buildList {
            for (distance in 0..maxOf(start - low, high - start)) {
                if (start - distance >= low) add(start - distance)
                if (distance != 0 && start + distance <= high) add(start + distance)
            }
        }
        for (radius in 0..4) for (dx in -radius..radius) for (dz in -radius..radius) {
            if (maxOf(abs(dx), abs(dz)) != radius || radius == 0) continue
            // Do not put a cache in the old spawn's feet/head space: native respawn
            // site preparation could otherwise clear the recovery shell.
            val x = origin.x + dx; val z = origin.z + dz
            for (y in heights) {
                val feet = BlockPos(x, y, z)
                val below = feet.below()
                if (!level.hasChunkAt(feet) || !level.hasChunkAt(below)) break
                if (!level.getBlockState(feet).isAir || !level.getBlockState(feet.above()).isAir) continue
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue
                selected += feet
                if (selected.size == count) return selected
            }
        }
        return null
    }
}
