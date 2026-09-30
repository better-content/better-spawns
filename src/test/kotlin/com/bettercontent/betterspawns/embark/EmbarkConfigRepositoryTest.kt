package com.bettercontent.betterspawns.embark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EmbarkConfigRepositoryTest {
    @Test
    fun bundledFallbackDisablesSelectionAndContainsNoTechBypass() {
        val json = EmbarkConfigRepository::class.java
            .getResourceAsStream("/data/better_spawns/embark/embark.json")!!
            .bufferedReader().use { it.readText() }
        val settings = EmbarkConfigRepository.validate(EmbarkConfigRepository.parse(json))

        assertEquals(SelectionMode.NONE, settings.mode)
        assertFalse(settings.items.any { it.item.startsWith("create:") || it.item.startsWith("tconstruct:") })
        assertFalse(settings.items.any { it.item == "minecraft:powered_rail" || it.item == "minecraft:recovery_compass" })
    }

    @Test
    fun parsesAndValidatesEmbarkConfig() {
        val json = """
            {
              "mode": "embark_points",
              "pointQuota": 8,
              "items": [
                {
                  "id": "torches",
                  "title": "Torches",
                  "category": "Caving",
                  "blurb": "Light.",
                  "item": "minecraft:torch",
                  "count": 16,
                  "cost": 1,
                  "maxPurchases": 3,
                  "slot": "inventory"
                },
                {
                  "id": "shield",
                  "title": "Shield",
                  "item": "minecraft:shield",
                  "cost": 3,
                  "slot": "weapon.offhand"
                }
              ]
            }
        """.trimIndent()

        val settings = EmbarkConfigRepository.validate(EmbarkConfigRepository.parse(json))

        assertEquals(SelectionMode.EMBARK_POINTS, settings.mode)
        assertEquals(8, settings.pointQuota)
        assertEquals(2, settings.items.size)
        assertEquals("Caving", settings.items[0].category)
        assertEquals(1, settings.items[1].count)
        assertEquals(1, settings.items[1].maxPurchases)
    }

    @Test
    fun parsesModeAliases() {
        assertEquals(SelectionMode.NONE, SelectionMode.parse("none"))
        assertEquals(SelectionMode.NONE, SelectionMode.parse("spawn_only"))
        assertEquals(SelectionMode.CLASS, SelectionMode.parse("class"))
        assertEquals(SelectionMode.CLASS, SelectionMode.parse("classes"))
        assertEquals(SelectionMode.EMBARK_POINTS, SelectionMode.parse("embark"))
        assertEquals(SelectionMode.EMBARK_POINTS, SelectionMode.parse("point_buy"))
        assertEquals(SelectionMode.PROGRESSION, SelectionMode.parse("progression"))
        assertEquals(SelectionMode.PROGRESSION, SelectionMode.parse("prestige"))
        assertEquals(SelectionMode.PROGRESSION, SelectionMode.parse("world_lifecycle"))
    }

    @Test
    fun allowsDormantEmbarkDataInNoneMode() {
        val settings = EmbarkSettings(
            mode = SelectionMode.NONE,
            pointQuota = 8,
            items = listOf(poolItem())
        )

        assertEquals(SelectionMode.NONE, EmbarkConfigRepository.validate(settings).mode)
    }

    @Test
    fun allowsDormantEmbarkDataInClassMode() {
        val settings = EmbarkSettings(
            mode = SelectionMode.CLASS,
            pointQuota = 8,
            items = listOf(poolItem())
        )

        assertEquals(SelectionMode.CLASS, EmbarkConfigRepository.validate(settings).mode)
    }

    @Test
    fun rejectsDuplicatePoolIds() {
        val settings = EmbarkSettings(
            mode = SelectionMode.EMBARK_POINTS,
            pointQuota = 6,
            items = listOf(
                poolItem(id = "wood"),
                poolItem(id = "wood", title = "Other Wood")
            )
        )

        val error = assertFailsWith<IllegalArgumentException> {
            EmbarkConfigRepository.validate(settings)
        }
        assertTrue(error.message!!.contains("Duplicate embark item ids"))
    }

    @Test
    fun rejectsInvalidItemCostAndSlot() {
        val badCost = EmbarkSettings(
            mode = SelectionMode.EMBARK_POINTS,
            pointQuota = 6,
            items = listOf(poolItem(cost = 0))
        )
        val badSlot = EmbarkSettings(
            mode = SelectionMode.EMBARK_POINTS,
            pointQuota = 6,
            items = listOf(poolItem(slot = "belt:left"))
        )

        assertTrue(
            assertFailsWith<IllegalArgumentException> { EmbarkConfigRepository.validate(badCost) }
                .message!!.contains("positive cost")
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> { EmbarkConfigRepository.validate(badSlot) }
                .message!!.contains("unsupported slot")
        )
    }

    private fun poolItem(
        id: String = "wood",
        title: String = "Wood",
        item: String = "minecraft:oak_log",
        count: Int = 16,
        cost: Int = 1,
        maxPurchases: Int = 2,
        slot: String? = "inventory"
    ) = EmbarkPoolItem(
        id = id,
        title = title,
        category = null,
        blurb = "",
        item = item,
        count = count,
        cost = cost,
        maxPurchases = maxPurchases,
        slot = slot
    )
}
