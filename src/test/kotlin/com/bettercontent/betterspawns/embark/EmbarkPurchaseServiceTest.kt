package com.bettercontent.betterspawns.embark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EmbarkPurchaseServiceTest {
    @Test
    fun validatesPurchasesAndBuildsKitItems() {
        val result = EmbarkPurchaseService.validate(
            settings = settings(),
            purchases = listOf(
                EmbarkPurchase("logs", 2),
                EmbarkPurchase("shield", 1)
            )
        )

        assertEquals(5, result.totalCost)
        assertEquals(1, result.remainingPoints)
        assertEquals(3, result.selectedItems.size)
        assertEquals("minecraft:oak_log", result.selectedItems[0].item)
        assertEquals(16, result.selectedItems[0].count)
        assertEquals("weapon.offhand", result.selectedItems[2].slot)
    }

    @Test
    fun rejectsOverspendAndUnknownItems() {
        val overspend = assertFailsWith<IllegalArgumentException> {
            EmbarkPurchaseService.validate(
                settings(),
                listOf(EmbarkPurchase("logs", 3), EmbarkPurchase("shield", 2))
            )
        }
        val unknown = assertFailsWith<IllegalArgumentException> {
            EmbarkPurchaseService.validate(settings(), listOf(EmbarkPurchase("diamonds", 1)))
        }

        assertTrue(overspend.message!!.contains("only 6 are available"))
        assertTrue(unknown.message!!.contains("not in the configured pool"))
    }

    @Test
    fun rejectsDuplicateAndExcessPurchases() {
        val duplicate = assertFailsWith<IllegalArgumentException> {
            EmbarkPurchaseService.validate(settings(), listOf(EmbarkPurchase("logs", 1), EmbarkPurchase("logs", 1)))
        }
        val tooMany = assertFailsWith<IllegalArgumentException> {
            EmbarkPurchaseService.validate(settings(), listOf(EmbarkPurchase("logs", 4)))
        }

        assertTrue(duplicate.message!!.contains("submitted more than once"))
        assertTrue(tooMany.message!!.contains("can only be purchased 3 time"))
    }

    private fun settings(): EmbarkSettings = EmbarkSettings(
        mode = SelectionMode.EMBARK_POINTS,
        pointQuota = 6,
        items = listOf(
            EmbarkPoolItem(
                id = "logs",
                title = "Logs",
                blurb = "",
                item = "minecraft:oak_log",
                count = 16,
                cost = 1,
                maxPurchases = 3,
                slot = "inventory"
            ),
            EmbarkPoolItem(
                id = "shield",
                title = "Shield",
                blurb = "",
                item = "minecraft:shield",
                count = 1,
                cost = 3,
                maxPurchases = 2,
                slot = "weapon.offhand"
            )
        )
    )
}
