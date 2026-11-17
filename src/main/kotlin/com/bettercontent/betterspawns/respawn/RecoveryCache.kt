package com.bettercontent.betterspawns.respawn

import com.bettercontent.betterspawns.ClassSelectorMod
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.ContainerHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import java.util.UUID

/** Public unbreakable access point. Its BE persists only a recovery UUID/page, never items. */
object RecoveryCache {
    private val blocks = DeferredRegister.create(ForgeRegistries.BLOCKS, ClassSelectorMod.MOD_ID)
    private val entities = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ClassSelectorMod.MOD_ID)
    val BLOCK = blocks.register("recovery_cache") { CacheBlock() }
    val ENTITY = entities.register("recovery_cache") {
        BlockEntityType.Builder.of({ pos, state -> CacheEntity(pos, state) }, BLOCK.get()).build(null)
    }
    fun register(bus: IEventBus) { blocks.register(bus); entities.register(bus) }

    class CacheBlock : BaseEntityBlock(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)
        .strength(-1f, 3_600_000f).noLootTable().pushReaction(PushReaction.BLOCK)) {
        override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = CacheEntity(pos, state)
        override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL
        override fun use(state: BlockState, level: Level, pos: BlockPos, player: Player,
                         hand: InteractionHand, hit: BlockHitResult): InteractionResult {
            if (!level.isClientSide) {
                val cache = level.getBlockEntity(pos) as? CacheEntity ?: return InteractionResult.FAIL
                if (!cache.canOpen(player)) return InteractionResult.FAIL
                player.openMenu(cache)
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }
    }

    class CacheEntity(pos: BlockPos, state: BlockState) : BaseContainerBlockEntity(ENTITY.get(), pos, state) {
        var recoveryId: UUID? = null
            private set
        var page: Int = -1
            private set
        fun identify(id: UUID, index: Int) { recoveryId = id; page = index; setChanged() }

        private fun inventory(): NonNullList<ItemStack>? {
            val serverLevel = level as? ServerLevel ?: return null
            val id = recoveryId ?: return null
            val record = RecoveryRecordData.get(serverLevel.server).records[id] ?: return null
            if (record.dimension != serverLevel.dimension().location().toString() ||
                record.pages.getOrNull(page) != blockPos) return null
            return record.inventories.getOrNull(page)
        }
        override fun canOpen(player: Player): Boolean = inventory() != null && super.canOpen(player)
        override fun getDefaultName(): Component = Component.translatable("container.better_spawns.recovery_cache")
        override fun createMenu(id: Int, inventory: Inventory): AbstractContainerMenu = ChestMenu.sixRows(id, inventory, this)
        override fun getContainerSize(): Int = 54
        override fun isEmpty(): Boolean = inventory()?.all(ItemStack::isEmpty) ?: true
        override fun getItem(slot: Int): ItemStack = inventory()?.getOrNull(slot) ?: ItemStack.EMPTY
        override fun removeItem(slot: Int, amount: Int): ItemStack = inventory()?.let {
            ContainerHelper.removeItem(it, slot, amount).also { removed -> if (!removed.isEmpty) setChanged() }
        } ?: ItemStack.EMPTY
        override fun removeItemNoUpdate(slot: Int): ItemStack = inventory()?.let {
            ContainerHelper.takeItem(it, slot).also { removed -> if (!removed.isEmpty) setChanged() }
        } ?: ItemStack.EMPTY
        override fun setItem(slot: Int, stack: ItemStack) {
            val contents = inventory() ?: return // never store a second local copy
            contents[slot] = stack
            setChanged()
        }
        override fun clearContent() {
            val contents = inventory() ?: return
            for (i in contents.indices) contents[i] = ItemStack.EMPTY
            setChanged()
        }
        override fun setChanged() {
            super.setChanged()
            (level as? ServerLevel)?.let { RecoveryRecordData.get(it.server).setDirty() }
        }
        override fun stillValid(player: Player): Boolean =
            inventory() != null && level?.getBlockEntity(blockPos) === this &&
                player.distanceToSqr(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5) <= 64

        override fun load(tag: CompoundTag) {
            super.load(tag)
            recoveryId = if (tag.hasUUID("RecoveryId")) tag.getUUID("RecoveryId") else null
            page = tag.getInt("Page")
        }
        override fun saveAdditional(tag: CompoundTag) {
            super.saveAdditional(tag)
            recoveryId?.let { tag.putUUID("RecoveryId", it) }
            tag.putInt("Page", page)
        }
    }
}
