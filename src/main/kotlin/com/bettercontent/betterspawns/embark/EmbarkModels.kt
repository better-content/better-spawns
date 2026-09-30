package com.bettercontent.betterspawns.embark

import com.bettercontent.betterspawns.kit.KitItem

enum class SelectionMode(val wireName: String) {
    NONE("none"),
    CLASS("class"),
    EMBARK_POINTS("embark_points"),
    PROGRESSION("progression");

    companion object {
        fun parse(rawMode: String?): SelectionMode {
            val normalized = rawMode?.trim()?.lowercase().orEmpty()
            return when (normalized) {
                "none", "disabled", "spawn_only", "respawn_only" -> NONE
                "", "class", "classes" -> CLASS
                "embark", "embark_points", "point_buy", "points" -> EMBARK_POINTS
                "progression", "prestige", "world_lifecycle" -> PROGRESSION
                else -> throw IllegalArgumentException(
                    "Unsupported selection mode '$rawMode'. Supported modes are 'none', 'class', 'embark_points', and 'progression'."
                )
            }
        }
    }
}

data class EmbarkPoolItem(
    val id: String,
    val title: String,
    val category: String? = null,
    val blurb: String,
    val item: String,
    val count: Int,
    val cost: Int,
    val maxPurchases: Int,
    val slot: String?
)

data class EmbarkSettings(
    val mode: SelectionMode,
    val pointQuota: Int,
    val items: List<EmbarkPoolItem>
)

data class EmbarkPurchase(
    val itemId: String,
    val quantity: Int
)

data class EmbarkPurchaseResult(
    val selectedItems: List<KitItem>,
    val totalCost: Int,
    val remainingPoints: Int
)
