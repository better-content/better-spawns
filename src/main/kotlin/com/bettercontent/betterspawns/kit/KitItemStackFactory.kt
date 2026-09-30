package com.bettercontent.betterspawns.kit

import com.mojang.brigadier.StringReader
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraftforge.registries.ForgeRegistries

object KitItemStackFactory {
    data class ParsedItemSpec(
        val itemId: ResourceLocation,
        val tag: CompoundTag?
    )

    fun parse(rawSpec: String): ParsedItemSpec {
        val trimmed = rawSpec.trim()
        require(trimmed.isNotEmpty()) { "Item spec cannot be blank" }

        val nbtStart = trimmed.indexOf('{')
        val itemIdPart = if (nbtStart >= 0) trimmed.substring(0, nbtStart) else trimmed
        val itemId = ResourceLocation.tryParse(itemIdPart)
            ?: throw IllegalArgumentException("Invalid item id '$rawSpec'")

        val tag = if (nbtStart >= 0) {
            val nbtPart = trimmed.substring(nbtStart)
            TagParser(StringReader(nbtPart)).readStruct()
        } else {
            null
        }

        return ParsedItemSpec(itemId = itemId, tag = tag)
    }

    fun create(rawSpec: String, count: Int): ItemStack? {
        val parsed = runCatching { parse(rawSpec) }.getOrNull() ?: return null
        val item = ForgeRegistries.ITEMS.getValue(parsed.itemId) ?: return null
        if (item == Items.AIR) return null

        return ItemStack(item, count.coerceAtLeast(1)).apply {
            if (parsed.tag != null) {
                tag = parsed.tag.copy()
            }
        }
    }
}
