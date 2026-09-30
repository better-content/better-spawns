package com.bettercontent.betterspawns.client

import com.bettercontent.betterspawns.embark.EmbarkPoolItem
import com.bettercontent.betterspawns.embark.SelectionMode
import com.bettercontent.betterspawns.kit.ClassKit
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassSelectionStateTest {
    @BeforeTest
    fun resetState() {
        ClassSelectionState.reset()
    }

    @Test
    fun resetsSelectionState() {
        ClassSelectionState.activeInCurrentWorld = true
        ClassSelectionState.selectionMode = SelectionMode.EMBARK_POINTS
        ClassSelectionState.promptOpen = true
        ClassSelectionState.selectionRequired = true
        ClassSelectionState.showNotice("Retry selection")
        ClassSelectionState.lockedClassId = "miner"
        ClassSelectionState.lockedRespawn = PendingRespawnSelection("minecraft:overworld", 1, 64, 2)
        ClassSelectionState.embarkItems = listOf(poolItem())
        ClassSelectionState.pointQuota = 4
        ClassSelectionState.embarkPurchases["logs"] = 2

        ClassSelectionState.reset()

        assertFalse(ClassSelectionState.activeInCurrentWorld)
        assertEquals(SelectionMode.NONE, ClassSelectionState.selectionMode)
        assertFalse(ClassSelectionState.promptOpen)
        assertFalse(ClassSelectionState.selectionRequired)
        assertNull(ClassSelectionState.noticeText)
        assertTrue(ClassSelectionState.kits.isEmpty())
        assertTrue(ClassSelectionState.embarkItems.isEmpty())
        assertTrue(ClassSelectionState.embarkPurchases.isEmpty())
        assertTrue(ClassSelectionState.pointQuota == 0)
        assertNull(ClassSelectionState.lockedClassId)
        assertNull(ClassSelectionState.lockedRespawn)
    }

    @Test
    fun clearPendingLocksOnlyClearsLocks() {
        val kit = ClassKit("miner", "Miner", "Dig", "Hard", emptyList())
        ClassSelectionState.kits = listOf(kit)
        ClassSelectionState.lockedClassId = "miner"
        ClassSelectionState.lockedRespawn = PendingRespawnSelection("minecraft:overworld", 3, 4, 5)
        ClassSelectionState.embarkPurchases["logs"] = 1

        ClassSelectionState.clearPendingLocks()

        assertNull(ClassSelectionState.lockedClassId)
        assertNull(ClassSelectionState.lockedRespawn)
        assertTrue(ClassSelectionState.embarkPurchases.isEmpty())
        assertTrue(ClassSelectionState.kits.isNotEmpty())
        assertTrue(ClassSelectionState.kits[0].id == "miner")
    }

    @Test
    fun noneModeDoesNotRequireSelectionPayloads() {
        ClassSelectionState.selectionMode = SelectionMode.NONE

        assertTrue(ClassSelectionState.hasSelectionOptions())
        assertTrue(ClassSelectionState.selectedEmbarkPurchases().isEmpty())
    }

    @Test
    fun submittingSelectionClearsPromptState() {
        ClassSelectionState.selectionRequired = true
        ClassSelectionState.promptOpen = true
        ClassSelectionState.showNotice("Retry selection")

        ClientOnboardingActions.markSelectionSubmitted()

        assertFalse(ClassSelectionState.selectionRequired)
        assertFalse(ClassSelectionState.promptOpen)
        assertNull(ClassSelectionState.noticeText)
    }

    @Test
    fun tracksEmbarkPurchasesWithinPointQuota() {
        val logs = poolItem(id = "logs", cost = 1, maxPurchases = 3)
        val shield = poolItem(id = "shield", cost = 3, maxPurchases = 1)
        ClassSelectionState.selectionMode = SelectionMode.EMBARK_POINTS
        ClassSelectionState.pointQuota = 4
        ClassSelectionState.embarkItems = listOf(logs, shield)
        ClassSelectionState.clearPendingLocks()

        ClassSelectionState.addEmbarkPurchase(logs)
        ClassSelectionState.addEmbarkPurchase(logs)
        ClassSelectionState.addEmbarkPurchase(shield)

        assertEquals(2, ClassSelectionState.embarkPurchases["logs"])
        assertNull(ClassSelectionState.embarkPurchases["shield"])
        assertEquals(2, ClassSelectionState.spentEmbarkPoints())
        assertEquals(2, ClassSelectionState.remainingEmbarkPoints())

        ClassSelectionState.removeEmbarkPurchase(logs)
        ClassSelectionState.addEmbarkPurchase(shield)

        assertEquals(1, ClassSelectionState.embarkPurchases["logs"])
        assertEquals(1, ClassSelectionState.embarkPurchases["shield"])
        assertEquals(4, ClassSelectionState.spentEmbarkPoints())
    }

    @Test
    fun randomizeEmbarkPurchasesSpendsFullQuotaWhenReachable() {
        val onePoint = poolItem(id = "torch", cost = 1, maxPurchases = 8)
        val threePoint = poolItem(id = "bed", cost = 3, maxPurchases = 2)
        ClassSelectionState.selectionMode = SelectionMode.EMBARK_POINTS
        ClassSelectionState.pointQuota = 6
        ClassSelectionState.embarkItems = listOf(onePoint, threePoint)
        ClassSelectionState.embarkPurchases["torch"] = 1

        val spent = ClassSelectionState.randomizeEmbarkPurchases(Random(7))

        assertEquals(6, spent)
        assertEquals(6, ClassSelectionState.spentEmbarkPoints())
        assertEquals(0, ClassSelectionState.remainingEmbarkPoints())
        assertTrue(ClassSelectionState.selectedEmbarkPurchases().isNotEmpty())
    }

    @Test
    fun randomizeEmbarkPurchasesUsesBestReachableSpendInsteadOfGreedyLeftover() {
        val threePoint = poolItem(id = "shield", cost = 3, maxPurchases = 1)
        val twoPoint = poolItem(id = "rope", cost = 2, maxPurchases = 2)
        ClassSelectionState.selectionMode = SelectionMode.EMBARK_POINTS
        ClassSelectionState.pointQuota = 4
        ClassSelectionState.embarkItems = listOf(threePoint, twoPoint)

        val spent = ClassSelectionState.randomizeEmbarkPurchases(Random(11))

        assertEquals(4, spent)
        assertNull(ClassSelectionState.embarkPurchases["shield"])
        assertEquals(2, ClassSelectionState.embarkPurchases["rope"])
        assertEquals(0, ClassSelectionState.remainingEmbarkPoints())
    }

    @Test
    fun randomizeEmbarkPurchasesLeavesOnlyUnavoidableRemainder() {
        val fivePoint = poolItem(id = "boat", cost = 5, maxPurchases = 1)
        val threePoint = poolItem(id = "food", cost = 3, maxPurchases = 1)
        ClassSelectionState.selectionMode = SelectionMode.EMBARK_POINTS
        ClassSelectionState.pointQuota = 7
        ClassSelectionState.embarkItems = listOf(fivePoint, threePoint)

        val spent = ClassSelectionState.randomizeEmbarkPurchases(Random(19))

        assertEquals(5, spent)
        assertEquals(5, ClassSelectionState.spentEmbarkPoints())
        assertEquals(2, ClassSelectionState.remainingEmbarkPoints())
    }

    private fun poolItem(
        id: String = "logs",
        cost: Int = 1,
        maxPurchases: Int = 2
    ) = EmbarkPoolItem(
        id = id,
        title = id,
        blurb = "",
        item = "minecraft:oak_log",
        count = 16,
        cost = cost,
        maxPurchases = maxPurchases,
        slot = "inventory"
    )
}
