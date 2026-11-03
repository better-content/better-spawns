package com.bettercontent.betterspawns.respawn

import com.bettercontent.betterspawns.ClassSelectorMod
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CryingObsidianBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries

/** Same native appearance/properties/ambient particles, but a distinct physics identity. */
object RespawnMarker {
    private val blocks = DeferredRegister.create(ForgeRegistries.BLOCKS, ClassSelectorMod.MOD_ID)
    private val items = DeferredRegister.create(ForgeRegistries.ITEMS, ClassSelectorMod.MOD_ID)
    val BLOCK = blocks.register<Block>("respawn_marker") {
        CryingObsidianBlock(BlockBehaviour.Properties.copy(Blocks.CRYING_OBSIDIAN))
    }
    val ITEM = items.register("respawn_marker") { BlockItem(BLOCK.get(), Item.Properties()) }

    fun register(bus: IEventBus) {
        blocks.register(bus)
        items.register(bus)
    }
}
