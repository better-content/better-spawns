package com.bettercontent.betterspawns.respawn

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RespawnMarkerResourcesTest {
    private val resources = Path.of("src/main/resources")
    private fun json(path: String) = JsonParser.parseString(Files.readString(resources.resolve(path))).asJsonObject

    @Test
    fun markerUsesVanillaModelsAndHasSelfLootWithoutRecipe() {
        assertEquals("minecraft:block/crying_obsidian", json("assets/better_spawns/blockstates/respawn_marker.json")
            .getAsJsonObject("variants").getAsJsonObject("").get("model").asString)
        assertEquals("minecraft:block/crying_obsidian", json("assets/better_spawns/models/item/respawn_marker.json").get("parent").asString)
        assertEquals("better_spawns:respawn_marker", json("data/better_spawns/loot_tables/blocks/respawn_marker.json")
            .getAsJsonArray("pools")[0].asJsonObject.getAsJsonArray("entries")[0].asJsonObject.get("name").asString)
        for (tag in listOf("mineable/pickaxe", "needs_diamond_tool")) {
            val data = json("data/minecraft/tags/blocks/$tag.json")
            assertFalse(data.get("replace").asBoolean)
            assertTrue(data.getAsJsonArray("values").any { it.asString == "better_spawns:respawn_marker" })
        }
        val recipes = resources.resolve("data/better_spawns/recipes")
        assertFalse(Files.exists(recipes))
    }
}
