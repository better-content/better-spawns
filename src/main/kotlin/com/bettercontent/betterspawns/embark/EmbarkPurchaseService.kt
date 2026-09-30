package com.bettercontent.betterspawns.embark

import com.bettercontent.betterspawns.kit.KitItem

object EmbarkPurchaseService {
    const val SELECTION_ID: String = "embark_points"

    fun validate(settings: EmbarkSettings, purchases: List<EmbarkPurchase>): EmbarkPurchaseResult {
        require(settings.mode == SelectionMode.EMBARK_POINTS) { "Embark purchases are only valid in embark_points mode" }
        require(purchases.isNotEmpty()) { "Select at least one embark item before beginning" }

        val poolById = settings.items.associateBy { it.id }
        val quantitiesById = linkedMapOf<String, Int>()

        purchases.forEach { purchase ->
            val id = purchase.itemId.trim()
            require(id.isNotBlank()) { "Embark purchase item id cannot be blank" }
            require(purchase.quantity > 0) { "Embark purchase '$id' must have a positive quantity" }
            require(quantitiesById.put(id, purchase.quantity) == null) {
                "Embark purchase '$id' was submitted more than once"
            }
        }

        var totalCost = 0L
        val selectedItems = mutableListOf<KitItem>()

        quantitiesById.forEach { (id, quantity) ->
            val poolItem = poolById[id] ?: throw IllegalArgumentException("Embark item '$id' is not in the configured pool")
            require(quantity <= poolItem.maxPurchases) {
                "Embark item '${poolItem.title}' can only be purchased ${poolItem.maxPurchases} time(s)"
            }

            totalCost += poolItem.cost.toLong() * quantity.toLong()
            require(totalCost <= settings.pointQuota) {
                "Embark purchases cost $totalCost point(s), but only ${settings.pointQuota} are available"
            }

            repeat(quantity) {
                selectedItems += KitItem(item = poolItem.item, count = poolItem.count, slot = poolItem.slot)
            }
        }

        return EmbarkPurchaseResult(
            selectedItems = selectedItems,
            totalCost = totalCost.toInt(),
            remainingPoints = settings.pointQuota - totalCost.toInt()
        )
    }
}
