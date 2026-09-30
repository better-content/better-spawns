package com.bettercontent.betterspawns.kit

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KitItemStackFactoryTest {
    @Test
    fun parsesSimpleItemSpec() {
        val parsed = KitItemStackFactory.parse("minecraft:stone")
        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "stone"), parsed.itemId)
        assertNull(parsed.tag)
    }

    @Test
    fun parsesItemSpecWithWhitespace() {
        val parsed = KitItemStackFactory.parse("  minecraft:stick  ")
        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "stick"), parsed.itemId)
    }

    @Test
    fun parsesItemSpecWithNbt() {
        val parsed = KitItemStackFactory.parse("minecraft:stone{Damage:0}")
        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "stone"), parsed.itemId)
        assertNotNull(parsed.tag)
    }

    @Test
    fun parsesItemSpecWithBracesOnlyInsideNbtPayload() {
        val parsed = KitItemStackFactory.parse("""minecraft:stone{display:{Name:'{"text":"Starter"}'}}""")

        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "stone"), parsed.itemId)
        assertNotNull(parsed.tag)
        assertEquals("""{"text":"Starter"}""", parsed.tag.getCompound("display").getString("Name"))
    }

    @Test
    fun rejectsBlankSpec() {
        val error = assertFailsWith<IllegalArgumentException> {
            KitItemStackFactory.parse("   ")
        }
        assertEquals("Item spec cannot be blank", error.message)
    }

    @Test
    fun rejectsInvalidResource() {
        val error = assertFailsWith<IllegalArgumentException> {
            KitItemStackFactory.parse("not a resource")
        }
        assertEquals("Invalid item id 'not a resource'", error.message)
    }

    @Test
    fun returnsNullWhenCreateSpecIsInvalid() {
        assertNull(KitItemStackFactory.create("  ", 1))
    }
}
