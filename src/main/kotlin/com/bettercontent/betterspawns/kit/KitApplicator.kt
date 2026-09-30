package com.bettercontent.betterspawns.kit

import net.minecraft.server.level.ServerPlayer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraftforge.fml.ModList
import net.minecraftforge.registries.ForgeRegistries
import top.theillusivec4.curios.api.CuriosApi
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler

object KitApplicator {
    const val SELECTED_CLASS_TAG: String = "better_spawns:selected_class"

    fun hasSelectedClass(player: ServerPlayer): Boolean = player.persistentData.contains(SELECTED_CLASS_TAG)

    fun apply(player: ServerPlayer, kit: ClassKit) {
        applyItems(player, kit.items, kit.id)
    }

    fun applyItems(player: ServerPlayer, items: List<KitItem>, selectionId: String) {
        player.inventory.clearContent()
        items.forEach { giveItemToConfiguredSlot(player, it) }
        player.persistentData.putString(SELECTED_CLASS_TAG, selectionId)
    }

    fun giveStarterSchematicannon(player: ServerPlayer) {
        val item = ForgeRegistries.ITEMS.getValue(ResourceLocation("create", "schematicannon"))
        require(item != null && item != Items.AIR) { "Create Schematicannon is not registered" }
        val stack = ItemStack(item)
        if (!player.addItem(stack)) player.drop(stack, false)
    }

    private fun giveItemToConfiguredSlot(player: ServerPlayer, kitItem: KitItem) {
        val stack = KitItemStackFactory.create(kitItem.item, kitItem.count) ?: return
        when (val slotTarget = KitSlot.parse(kitItem.slot)) {
            KitSlotTarget.Inventory -> player.addItem(stack)
            is KitSlotTarget.Hotbar -> placeInHotbar(player, slotTarget.index, stack)
            KitSlotTarget.Offhand -> equipEquipmentSlot(player, stack, EquipmentSlot.OFFHAND)
            is KitSlotTarget.Armor -> equipEquipmentSlot(player, stack, slotTarget.slot)
            is KitSlotTarget.Curio -> equipCurioSlot(player, stack, slotTarget.identifier)
            is KitSlotTarget.Unknown -> player.addItem(stack)
        }
    }

    private fun placeInHotbar(player: ServerPlayer, slotIndex: Int, stack: ItemStack) {
        val inventory = player.inventory
        val existing = inventory.getItem(slotIndex)
        if (!existing.isEmpty) {
            player.addItem(existing)
        }
        inventory.setItem(slotIndex, stack)
        inventory.setChanged()
    }

    private fun equipEquipmentSlot(player: ServerPlayer, stack: ItemStack, target: EquipmentSlot) {
        if (!player.getItemBySlot(target).isEmpty) {
            player.addItem(stack)
            return
        }

        player.setItemSlot(target, stack)
    }

    private fun equipCurioSlot(player: ServerPlayer, stack: ItemStack, curioIdentifier: String) {
        if (!ModList.get().isLoaded("curios")) {
            player.addItem(stack)
            return
        }

        val handler = resolveCurioStacksHandler(player, curioIdentifier)
        if (handler == null) {
            player.addItem(stack)
            return
        }

        val slots = handler.slots
        val dynamicHandler = handler.stacks

        for (index in 0 until slots) {
            val currentStack = dynamicHandler.getStackInSlot(index)
            if (currentStack.isEmpty) {
                dynamicHandler.setStackInSlot(index, stack.copyWithCount(1))
                val remainder = stack.count - 1
                if (remainder > 0) {
                    player.addItem(stack.copyWithCount(remainder))
                }
                return
            }
        }

        player.addItem(stack)
    }

    private fun resolveCurioStacksHandler(
        player: ServerPlayer,
        curioIdentifier: String
    ): ICurioStacksHandler? {
        val inventory = CuriosApi.getCuriosInventory(player).resolve().orElse(null) ?: return null
        return inventory.getStacksHandler(curioIdentifier).orElse(null)
    }
}
