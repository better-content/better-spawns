package com.bettercontent.betterspawns.kit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClassKitRepositoryTest {
    @Test
    fun bundledFallbackKitsContainNoProgressionBypasses() {
        val json = ClassKitRepository::class.java
            .getResourceAsStream("/data/better_spawns/class_kits/kits.json")!!
            .bufferedReader().use { it.readText() }
        val kits = ClassKitRepository.validate(ClassKitRepository.parse(json))
        val itemSpecs = kits.flatMap { kit -> kit.items.map { it.item } }

        assertEquals(6, kits.size)
        assertFalse(itemSpecs.any { '{' in it }, "fallback kits must not ship authored tool NBT")
        assertFalse(itemSpecs.any { it.startsWith("create:") }, "fallback kits must not bypass Create progression")
        assertFalse(itemSpecs.any { it.startsWith("tconstruct:") }, "fallback kits must not bypass TCon metallurgy")
        assertFalse(itemSpecs.any { it == "minecraft:powered_rail" || it == "minecraft:recovery_compass" })
        assertFalse(itemSpecs.any { it.startsWith("sophisticatedbackpacks:") || it == "minecraft:tnt" })
    }

    @Test
    fun parsesClassKitsFromJson() {
        val json = """
            [
              {
                "id": "warrior",
                "title": "Warrior",
                "blurb": "Melee",
                "description": "Front line",
                "items": [
                  {"item": "minecraft:stone_sword", "count": 1, "slot": "inventory"},
                  {"item": "minecraft:iron_helmet", "count": 1, "slot": "armor:head"},
                  {"item": "minecraft:spyglass", "count": 1, "slot": "curio:charm"}
                ]
              }
            ]
        """.trimIndent()

        val kits = ClassKitRepository.parse(json)
        assertEquals(1, kits.size)
        assertEquals("warrior", kits.first().id)
        assertEquals("minecraft:stone_sword", kits.first().items.first().item)
        assertEquals("armor:head", kits.first().items[1].slot)
        assertEquals("curio:charm", kits.first().items[2].slot)
        assertTrue(kits.first().items.first().count > 0)
    }

    @Test
    fun validatesSupportedSlotsIncludingOffhand() {
        val kits = listOf(
            ClassKit(
                id = "guardian",
                title = "Guardian",
                blurb = "Defensive",
                description = "Uses a shield.",
                items = listOf(
                    KitItem(item = "minecraft:shield", slot = "weapon.offhand"),
                    KitItem(item = "minecraft:iron_sword", slot = "hotbar.0")
                )
            )
        )

        val validated = ClassKitRepository.validate(kits)
        assertEquals("weapon.offhand", validated.first().items.first().slot)
        assertEquals("hotbar.0", validated.first().items[1].slot)
    }

    @Test
    fun validatesItemSpecsWithNbtPayload() {
        val kits = listOf(
            ClassKit(
                id = "miner",
                title = "Miner",
                blurb = "NBT",
                description = "Contains a tool with NBT.",
                items = listOf(
                    KitItem(item = "minecraft:iron_pickaxe{Damage:0}", slot = "hotbar.0")
                )
            )
        )

        val validated = ClassKitRepository.validate(kits)
        assertEquals("minecraft:iron_pickaxe{Damage:0}", validated.first().items.first().item)
    }

    @Test
    fun parseRejectsInvalidJsonWithLineReference() {
        val malformedJson = """
            [
              {
                "id": "broken",
                "title": "Broken",
                "blurb": "Invalid",
                "description": "Missing comma between fields"
                "items": [{"item": "minecraft:stick"}]
              }
            ]
        """.trimIndent()

        val error = assertFailsWith<IllegalArgumentException> {
            ClassKitRepository.parse(malformedJson)
        }
        assertTrue(error.message!!.contains("Invalid JSON syntax"))
        assertTrue(error.message!!.contains("line", ignoreCase = true))
        assertTrue(error.message!!.contains("column", ignoreCase = true))
    }

    @Test
    fun rejectsUnsupportedSlots() {
        val kits = listOf(
            ClassKit(
                id = "broken",
                title = "Broken",
                blurb = "Invalid",
                description = "Contains an unsupported slot.",
                items = listOf(
                    KitItem(item = "minecraft:stick", slot = "belt:left")
                )
            )
        )

        val error = assertFailsWith<IllegalArgumentException> {
            ClassKitRepository.validate(kits)
        }
        assertTrue(error.message!!.contains("unsupported slot"))
    }

    @Test
    fun validateRejectsEmptyClassKitList() {
        val error = assertFailsWith<IllegalArgumentException> {
            ClassKitRepository.validate(emptyList())
        }
        assertTrue(error.message!!.contains("No class kits defined"))
    }

    @Test
    fun validateRejectsDuplicateIds() {
        val kits = listOf(
            ClassKit(
                id = "dup",
                title = "First",
                blurb = "A",
                description = "X",
                items = listOf(KitItem(item = "minecraft:stick", slot = "inventory"))
            ),
            ClassKit(
                id = "dup",
                title = "Second",
                blurb = "B",
                description = "Y",
                items = listOf(KitItem(item = "minecraft:stone", slot = "inventory"))
            )
        )

        val error = assertFailsWith<IllegalArgumentException> {
            ClassKitRepository.validate(kits)
        }
        assertTrue(error.message!!.contains("Duplicate class kit ids"))
    }

    @Test
    fun validateRejectsInvalidItemSpecs() {
        val kits = listOf(
            ClassKit(
                id = "broken_item",
                title = "Broken Item",
                blurb = "Invalid",
                description = "Contains an invalid item id.",
                items = listOf(KitItem(item = "not a resource", slot = "inventory"))
            )
        )

        val error = assertFailsWith<IllegalArgumentException> {
            ClassKitRepository.validate(kits)
        }
        assertTrue(error.message!!.contains("invalid item spec"))
    }

    @Test
    fun validateRejectsMissingRequiredFields() {
        val missingTitle = listOf(
            ClassKit(id = "missing", title = "", blurb = "B", description = "C", items = listOf(KitItem(item = "minecraft:stone")))
        )
        val missingBlurb = listOf(
            ClassKit(id = "missing", title = "A", blurb = " ", description = "C", items = listOf(KitItem(item = "minecraft:stone")))
        )
        val missingDescription = listOf(
            ClassKit(id = "missing", title = "A", blurb = "B", description = "", items = listOf(KitItem(item = "minecraft:stone")))
        )

        val titleError = assertFailsWith<IllegalArgumentException> { ClassKitRepository.validate(missingTitle) }
        val blurbError = assertFailsWith<IllegalArgumentException> { ClassKitRepository.validate(missingBlurb) }
        val descriptionError = assertFailsWith<IllegalArgumentException> { ClassKitRepository.validate(missingDescription) }

        assertTrue(titleError.message!!.contains("non-blank title"))
        assertTrue(blurbError.message!!.contains("non-blank blurb"))
        assertTrue(descriptionError.message!!.contains("non-blank description"))
    }

    @Test
    fun validateRejectsZeroItemCount() {
        val kits = listOf(
            ClassKit(id = "zero", title = "Zero", blurb = "Count", description = "No items", items = listOf(
                KitItem(item = "minecraft:stone", count = 0)
            ))
        )

        val error = assertFailsWith<IllegalArgumentException> { ClassKitRepository.validate(kits) }
        assertTrue(error.message!!.contains("must have a positive count"))
    }

    @Test
    fun validateRejectsKitsWithoutItems() {
        val kits = listOf(
            ClassKit(id = "empty", title = "Empty", blurb = "None", description = "No starting gear.", items = emptyList())
        )

        val error = assertFailsWith<IllegalArgumentException> { ClassKitRepository.validate(kits) }
        assertTrue(error.message!!.contains("must define at least one item"))
    }
}
