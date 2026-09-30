package com.bettercontent.betterspawns.embark

import com.bettercontent.betterspawns.kit.ClassKit
import com.bettercontent.betterspawns.kit.KitItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressionSelectionTest {
    private val classIds = listOf(
        "wayfinder", "field_cook", "rail_scout", "flood_runner", "market_runner", "trail_wrangler"
    )
    private val kits = classIds.mapIndexed { index, id ->
        ClassKit(id, id, "", "", listOf(KitItem("minecraft:item_$index")))
    }
    private val configured = EmbarkSettings(
        SelectionMode.PROGRESSION,
        6,
        kits.mapIndexed { index, kit ->
            EmbarkPoolItem(
                "item_$index", kit.id, "Class supplies", "", kit.items.single().item, 1, 1, 1, "inventory"
            )
        }
    )

    @Test
    fun firstGenerationRemainsSpawnOnly() {
        val data = resolve(SelectionMode.NONE)

        assertEquals(SelectionMode.NONE, data.mode)
        assertTrue(data.kits.isEmpty())
        assertFalse(data.starterSchematicannon)
    }

    @Test
    fun followsTheCompleteTwelveGenerationProgression() {
        val policies = buildList {
            add(policy(SelectionMode.NONE))
            classIds.take(5).indices.forEach { lastUnlocked ->
                add(policy(SelectionMode.CLASS, classIds.take(lastUnlocked + 1).toSet()))
            }
            add(policy(SelectionMode.EMBARK_POINTS, classIds.toSet(), 6))
            listOf(9, 12, 15, 18).forEach { budget ->
                add(policy(SelectionMode.EMBARK_POINTS, classIds.toSet(), budget))
            }
            add(policy(SelectionMode.EMBARK_POINTS, classIds.toSet(), 18, starterSchematicannon = true))
        }

        assertEquals(12, policies.size)
        policies.forEachIndexed { index, progressionPolicy ->
            val data = SelectionDataRepository.resolveProgression(configured, kits, progressionPolicy)
            when (index) {
                0 -> {
                    assertEquals(SelectionMode.NONE, data.mode)
                    assertTrue(data.kits.isEmpty())
                }
                in 1..5 -> {
                    assertEquals(SelectionMode.CLASS, data.mode)
                    assertEquals(index, data.kits.size)
                }
                else -> {
                    assertEquals(SelectionMode.EMBARK_POINTS, data.mode)
                    assertTrue(data.kits.isEmpty())
                }
            }
        }
        assertTrue(
            SelectionDataRepository.resolveProgression(configured, kits, policies.last()).starterSchematicannon
        )
    }

    @Test
    fun revealsExactlyThePaidClassesInCanonicalOrder() {
        classIds.dropLast(1).indices.forEach { lastUnlocked ->
            val unlocked = classIds.take(lastUnlocked + 1).toSet()
            val data = resolve(SelectionMode.CLASS, unlocked)

            assertEquals(SelectionMode.CLASS, data.mode)
            assertEquals(classIds.take(lastUnlocked + 1), data.kits.map { it.id })
            assertFalse(data.starterSchematicannon)
        }
    }

    @Test
    fun embarkReplacesClassesAtEveryBudgetTier() {
        listOf(6, 9, 12, 15, 18).forEach { budget ->
            val data = resolve(SelectionMode.EMBARK_POINTS, classIds.toSet(), budget)

            assertEquals(SelectionMode.EMBARK_POINTS, data.mode)
            assertTrue(data.kits.isEmpty())
            assertEquals(budget, data.embarkSettings.pointQuota)
        }
    }

    @Test
    fun finalEntitlementAddsTheStarterSchematicannonWithoutChangingEmbark() {
        val data = resolve(SelectionMode.EMBARK_POINTS, classIds.toSet(), 18, starterSchematicannon = true)

        assertEquals(18, data.embarkSettings.pointQuota)
        assertTrue(data.starterSchematicannon)
    }

    @Test
    fun rejectsIncompleteClassOrEmbarkCatalogs() {
        assertFailsWith<IllegalArgumentException> {
            SelectionDataRepository.resolveProgression(configured, kits.dropLast(1), policy(SelectionMode.NONE))
        }
        assertFailsWith<IllegalArgumentException> {
            SelectionDataRepository.resolveProgression(
                configured.copy(items = configured.items.dropLast(1)), kits, policy(SelectionMode.NONE)
            )
        }
    }

    @Test
    fun rejectsImpossibleLifecyclePolicies() {
        assertFailsWith<IllegalArgumentException> {
            resolve(SelectionMode.CLASS, emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            resolve(SelectionMode.CLASS, classIds.toSet())
        }
        assertFailsWith<IllegalArgumentException> {
            resolve(SelectionMode.CLASS, setOf("unknown"))
        }
        assertFailsWith<IllegalArgumentException> {
            resolve(SelectionMode.EMBARK_POINTS, classIds.toSet(), 7)
        }
        assertFailsWith<IllegalArgumentException> {
            resolve(SelectionMode.EMBARK_POINTS, classIds.dropLast(1).toSet(), 6)
        }
        assertFailsWith<IllegalArgumentException> {
            resolve(SelectionMode.NONE, setOf(classIds.first()))
        }
        assertFailsWith<IllegalArgumentException> {
            resolve(SelectionMode.NONE, budget = 6)
        }
        assertFailsWith<IllegalArgumentException> {
            resolve(SelectionMode.EMBARK_POINTS, classIds.toSet(), 15, starterSchematicannon = true)
        }
    }

    private fun resolve(
        mode: SelectionMode,
        unlocked: Set<String> = emptySet(),
        budget: Int = 0,
        starterSchematicannon: Boolean = false
    ): SelectionData = SelectionDataRepository.resolveProgression(
        configured, kits, policy(mode, unlocked, budget, starterSchematicannon)
    )

    private fun policy(
        mode: SelectionMode,
        unlocked: Set<String> = emptySet(),
        budget: Int = 0,
        starterSchematicannon: Boolean = false
    ) = ProgressionPolicy(mode, unlocked, budget, starterSchematicannon)
}
