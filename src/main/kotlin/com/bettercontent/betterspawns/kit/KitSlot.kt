package com.bettercontent.betterspawns.kit

import net.minecraft.world.entity.EquipmentSlot

sealed interface KitSlotTarget {
    data object Inventory : KitSlotTarget
    data class Hotbar(val index: Int) : KitSlotTarget
    data object Offhand : KitSlotTarget
    data class Armor(val slot: EquipmentSlot) : KitSlotTarget
    data class Curio(val identifier: String) : KitSlotTarget
    data class Unknown(val raw: String) : KitSlotTarget
}

object KitSlot {
    fun parse(rawSlot: String?): KitSlotTarget {
        val slot = rawSlot?.trim()?.lowercase()
        if (slot.isNullOrEmpty() || slot == "inventory") {
            return KitSlotTarget.Inventory
        }

        if (slot == "offhand" || slot == "weapon.offhand") {
            return KitSlotTarget.Offhand
        }

        if (slot == "mainhand" || slot == "weapon.mainhand") {
            return KitSlotTarget.Hotbar(0)
        }

        if (slot.startsWith("hotbar.")) {
            val index = slot.removePrefix("hotbar.").toIntOrNull()
            return if (index != null && index in 0..8) {
                KitSlotTarget.Hotbar(index)
            } else {
                KitSlotTarget.Unknown(slot)
            }
        }

        if (slot.startsWith("armor:")) {
            return when (slot.removePrefix("armor:")) {
                "head", "helmet" -> KitSlotTarget.Armor(EquipmentSlot.HEAD)
                "chest", "chestplate" -> KitSlotTarget.Armor(EquipmentSlot.CHEST)
                "legs", "leggings" -> KitSlotTarget.Armor(EquipmentSlot.LEGS)
                "feet", "boots" -> KitSlotTarget.Armor(EquipmentSlot.FEET)
                else -> KitSlotTarget.Unknown(slot)
            }
        }

        if (slot.startsWith("curio:")) {
            val identifier = slot.removePrefix("curio:").trim()
            return if (identifier.isNotEmpty()) {
                KitSlotTarget.Curio(identifier)
            } else {
                KitSlotTarget.Unknown(slot)
            }
        }

        return KitSlotTarget.Unknown(slot)
    }

    fun isSupported(rawSlot: String?): Boolean = parse(rawSlot) !is KitSlotTarget.Unknown

    fun label(rawSlot: String?): String? {
        val target = parse(rawSlot)
        return when (target) {
        KitSlotTarget.Inventory -> null
        is KitSlotTarget.Hotbar -> "${target.index + 1}"
        KitSlotTarget.Offhand -> "Off"
        is KitSlotTarget.Armor -> when (target.slot) {
            EquipmentSlot.HEAD -> "Head"
            EquipmentSlot.CHEST -> "Chest"
            EquipmentSlot.LEGS -> "Legs"
            EquipmentSlot.FEET -> "Feet"
            else -> null
        }
        is KitSlotTarget.Curio -> target.identifier.replaceFirstChar { it.titlecase() }
        is KitSlotTarget.Unknown -> rawSlot?.trim()?.ifBlank { null }
    }
    }
}
