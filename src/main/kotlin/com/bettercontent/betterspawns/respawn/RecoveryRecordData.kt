package com.bettercontent.betterspawns.respawn

import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.ContainerHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/** The only persisted recovery inventory. Blocks carry record/page references, never item copies. */
class RecoveryRecordData : SavedData() {
    /** Private recovery evidence; never served by CacheEntity or any public inventory. */
    data class AuditSlot(val source: String, val original: ItemStack)
    class Record(val id: UUID, val owner: UUID, val dimension: String, val pages: List<BlockPos>,
                 val inventories: List<NonNullList<ItemStack>>) {
        init { require(pages.isNotEmpty() && pages.size == inventories.size) }
        private var audit: List<AuditSlot>? = null
        var failure: String? = null
            private set
        var failureTick: Long = 0
            private set
        fun empty() = inventories.all { page -> page.all(ItemStack::isEmpty) }
        fun unresolved() = audit != null
        fun auditForSave(): List<AuditSlot>? = audit
        fun beginAudit(slots: List<AuditSlot>, tick: Long) {
            audit = slots.map { AuditSlot(it.source, it.original.copy()) }
            failure = "source transfer incomplete"
            failureTick = tick
        }
        fun failAudit(reason: String, tick: Long) {
            check(unresolved()) { "Cannot quarantine recovery without its original slot snapshot" }
            failure = reason.take(256)
            failureTick = tick
        }
        fun clearAudit() { audit = null; failure = null; failureTick = 0 }
    }
    val records = linkedMapOf<UUID, Record>()
    val emptied = linkedSetOf<UUID>() // receipt for a dead player if others extracted everything first

    override fun save(tag: CompoundTag): CompoundTag {
        val list = ListTag()
        records.values.forEach { record ->
            val entry = CompoundTag()
            entry.putUUID("Id", record.id)
            entry.putUUID("Owner", record.owner)
            entry.putString("Dimension", record.dimension)
            val pages = ListTag()
            record.pages.forEachIndexed { index, pos ->
                val page = CompoundTag()
                page.putInt("X", pos.x); page.putInt("Y", pos.y); page.putInt("Z", pos.z)
                ContainerHelper.saveAllItems(page, record.inventories[index])
                pages.add(page)
            }
            entry.put("Pages", pages)
            record.auditForSave()?.let { audit ->
                val originals = ListTag()
                audit.forEach { slot ->
                    val original = CompoundTag()
                    original.putString("Source", slot.source)
                    original.put("Stack", slot.original.save(CompoundTag()))
                    originals.add(original)
                }
                entry.put("Audit", originals)
                entry.putString("Failure", record.failure ?: "unresolved source transfer")
                entry.putLong("FailureTick", record.failureTick)
            }
            list.add(entry)
        }
        tag.put("Records", list)
        val receipts = ListTag()
        emptied.forEach { id ->
            val receipt = CompoundTag(); receipt.putUUID("Id", id); receipts.add(receipt)
        }
        tag.put("Emptied", receipts)
        return tag
    }

    fun keep(record: Record) { records[record.id] = record; setDirty() }
    fun touch() { setDirty() }
    fun forgetFailedCommit(id: UUID) { if (records.remove(id) != null) setDirty() }
    fun items(id: UUID?, page: Int): NonNullList<ItemStack>? =
        id?.let { records[it]?.inventories?.getOrNull(page) }

    /** No chunk loading. Unexpected missing blocks retain authoritative inventory until repaired. */
    fun retireEmpty(server: MinecraftServer) {
        for (record in records.values.toList()) {
            val key = ResourceLocation.tryParse(record.dimension)?.let {
                ResourceKey.create(Registries.DIMENSION, it)
            } ?: continue
            val level = server.getLevel(key) ?: continue
            if (record.empty() && !record.unresolved()) {
                if (!record.pages.all(level::hasChunkAt)) continue
                record.pages.forEachIndexed { index, pos ->
                    val cache = level.getBlockEntity(pos) as? RecoveryCache.CacheEntity
                    if (cache?.recoveryId == record.id && cache.page == index) level.removeBlock(pos, false)
                }
                records.remove(record.id)
                emptied += record.id
                setDirty()
            } else if (!record.empty()) {
                record.pages.forEachIndexed { index, pos ->
                    if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below())) return@forEachIndexed
                    val cache = level.getBlockEntity(pos) as? RecoveryCache.CacheEntity
                    if (cache?.recoveryId == record.id && cache.page == index) return@forEachIndexed
                    if (!level.getBlockState(pos).isAir || !level.getBlockState(pos.below())
                            .isFaceSturdy(level, pos.below(), Direction.UP)) return@forEachIndexed
                    if (level.setBlockAndUpdate(pos, RecoveryCache.BLOCK.get().defaultBlockState()))
                        (level.getBlockEntity(pos) as? RecoveryCache.CacheEntity)?.identify(record.id, index)
                }
            }
        }
    }

    companion object {
        fun get(server: MinecraftServer): RecoveryRecordData = server.overworld().dataStorage.computeIfAbsent(
            { tag -> load(tag) }, { RecoveryRecordData() }, "better_spawns_recoveries")
        private fun load(tag: CompoundTag): RecoveryRecordData = RecoveryRecordData().apply {
            val entries = tag.getList("Records", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until entries.size) {
                val entry = entries.getCompound(i)
                if (!entry.hasUUID("Id") || !entry.hasUUID("Owner")) continue
                val id = entry.getUUID("Id")
                val pageTags = entry.getList("Pages", Tag.TAG_COMPOUND.toInt())
                val pages = (0 until pageTags.size).map { pageTags.getCompound(it) }
                if (pages.isEmpty()) continue
                val positions = pages.map { BlockPos(it.getInt("X"), it.getInt("Y"), it.getInt("Z")) }
                val inventories = pages.map { page ->
                    NonNullList.withSize(54, ItemStack.EMPTY).also { ContainerHelper.loadAllItems(page, it) }
                }
                val record = Record(id, entry.getUUID("Owner"), entry.getString("Dimension"), positions, inventories)
                if (entry.contains("Audit", Tag.TAG_LIST.toInt())) {
                    val auditTags = entry.getList("Audit", Tag.TAG_COMPOUND.toInt())
                    val audit = (0 until auditTags.size).map { slotIndex ->
                        val slot = auditTags.getCompound(slotIndex)
                        AuditSlot(slot.getString("Source"), ItemStack.of(slot.getCompound("Stack")))
                    }
                    record.beginAudit(audit, entry.getLong("FailureTick"))
                    record.failAudit(entry.getString("Failure").ifBlank { "unresolved source transfer" },
                        entry.getLong("FailureTick"))
                }
                records[id] = record
            }
            val receipts = tag.getList("Emptied", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until receipts.size) if (receipts.getCompound(i).hasUUID("Id"))
                emptied += receipts.getCompound(i).getUUID("Id")
        }
    }
}
