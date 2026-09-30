package com.bettercontent.betterspawns.kit

data class ClassKit(
    val id: String,
    val title: String,
    val blurb: String,
    val description: String,
    val items: List<KitItem>
)

data class KitItem(
    val item: String,
    val count: Int = 1,
    val slot: String? = null
)
