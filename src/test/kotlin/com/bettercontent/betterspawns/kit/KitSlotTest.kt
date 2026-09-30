package com.bettercontent.betterspawns.kit

import net.minecraft.world.entity.EquipmentSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KitSlotTest {
    @Test
    fun parsesInventorySlots() {
        assertTrue(KitSlot.parse(null) is KitSlotTarget.Inventory)
        assertTrue(KitSlot.parse(" ") is KitSlotTarget.Inventory)
        assertTrue(KitSlot.parse("INVENTORY") is KitSlotTarget.Inventory)
        assertTrue(KitSlot.parse("inventory") is KitSlotTarget.Inventory)
    }

    @Test
    fun parsesOffhandAndMainhandVariants() {
        assertTrue(KitSlot.parse("offhand") is KitSlotTarget.Offhand)
        assertTrue(KitSlot.parse("weapon.offhand") is KitSlotTarget.Offhand)
        assertEquals(0, (KitSlot.parse("mainhand") as KitSlotTarget.Hotbar).index)
        assertEquals(0, (KitSlot.parse("weapon.mainhand") as KitSlotTarget.Hotbar).index)
    }

    @Test
    fun parsesHotbarSlotsAndSupportsRange() {
        assertEquals(3, (KitSlot.parse("hotbar.3") as KitSlotTarget.Hotbar).index)
        assertEquals(4, (KitSlot.parse("HOTBAR.4") as KitSlotTarget.Hotbar).index)
        assertEquals(KitSlotTarget.Unknown("hotbar.9"), KitSlot.parse("hotbar.9"))
        assertEquals(KitSlotTarget.Unknown("hotbar.bad"), KitSlot.parse("hotbar.bad"))
        assertEquals(KitSlotTarget.Unknown("hotbar.-1"), KitSlot.parse("hotbar.-1"))
    }

    @Test
    fun parsesArmorSlots() {
        assertEquals(EquipmentSlot.HEAD, (KitSlot.parse("armor:head") as KitSlotTarget.Armor).slot)
        assertEquals(EquipmentSlot.CHEST, (KitSlot.parse("armor:chestplate") as KitSlotTarget.Armor).slot)
        assertEquals(EquipmentSlot.LEGS, (KitSlot.parse("armor:leggings") as KitSlotTarget.Armor).slot)
        assertEquals(EquipmentSlot.FEET, (KitSlot.parse("armor:boots") as KitSlotTarget.Armor).slot)
        assertEquals(KitSlotTarget.Unknown("armor:ring"), KitSlot.parse("armor:ring"))
    }

    @Test
    fun parsesCurioSlotsAndUnknownSlots() {
        assertEquals("charm", (KitSlot.parse("curio:charm") as KitSlotTarget.Curio).identifier)
        assertEquals("Charm", KitSlot.label("curio:charm"))
        assertEquals(KitSlotTarget.Unknown("curio:"), KitSlot.parse("curio:"))
        assertEquals(KitSlotTarget.Unknown("not-a-slot"), KitSlot.parse("not-a-slot"))
    }

    @Test
    fun reportsSupportAndLabels() {
        assertFalse(KitSlot.isSupported("not-a-slot"))
        assertNull(KitSlot.label("inventory"))
        assertNull(KitSlot.label(null))
        assertEquals("Off", KitSlot.label("offhand"))
        assertEquals("1", KitSlot.label("hotbar.0"))
        assertEquals("9", KitSlot.label("hotbar.8"))
        assertEquals("Head", KitSlot.label("armor:head"))
        assertEquals("Chest", KitSlot.label("armor:chest"))
        assertEquals("Legs", KitSlot.label("armor:legs"))
        assertEquals("Feet", KitSlot.label("armor:feet"))
        assertEquals("not-a-slot", KitSlot.label("not-a-slot"))
        assertEquals("armor:body", KitSlot.label("armor:body"))
    }

    @Test
    fun reachesUnknownAccessor() {
        val unknown = KitSlotTarget.Unknown("NotSupported")
        assertEquals("NotSupported", unknown.raw)
    }
}
