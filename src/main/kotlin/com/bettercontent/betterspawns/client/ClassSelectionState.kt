package com.bettercontent.betterspawns.client

import com.bettercontent.betterspawns.embark.EmbarkPoolItem
import com.bettercontent.betterspawns.embark.EmbarkPurchase
import com.bettercontent.betterspawns.embark.SelectionMode
import com.bettercontent.betterspawns.kit.ClassKit
import kotlin.random.Random

data class PendingRespawnSelection(
    val dim: String,
    val x: Int,
    val y: Int,
    val z: Int
)

object ClassSelectionState {
    var activeInCurrentWorld: Boolean = false
    var selectionMode: SelectionMode = SelectionMode.NONE
    var kits: List<ClassKit> = emptyList()
    var embarkItems: List<EmbarkPoolItem> = emptyList()
    var pointQuota: Int = 0
    var promptOpen: Boolean = false
    var selectionRequired: Boolean = false
    var reselectingSpawn: Boolean = false
    var lockedClassId: String? = null
    var lockedRespawn: PendingRespawnSelection? = null
    var noticeText: String? = null
    var noticeTicksRemaining: Int = 0
    val embarkPurchases: MutableMap<String, Int> = linkedMapOf()

    fun clearPendingLocks() {
        lockedClassId = null
        lockedRespawn = null
        embarkPurchases.clear()
    }

    fun showNotice(message: String) {
        noticeText = message
        noticeTicksRemaining = 200
    }

    fun hasSelectionOptions(): Boolean = when (selectionMode) {
        SelectionMode.NONE -> true
        SelectionMode.CLASS -> kits.isNotEmpty()
        SelectionMode.EMBARK_POINTS -> pointQuota > 0 && embarkItems.isNotEmpty()
        SelectionMode.PROGRESSION -> false
    }

    fun selectedEmbarkPurchases(): List<EmbarkPurchase> =
        embarkItems.mapNotNull { item ->
            val quantity = embarkPurchases[item.id] ?: return@mapNotNull null
            if (quantity > 0) EmbarkPurchase(item.id, quantity) else null
        }

    fun spentEmbarkPoints(): Int =
        embarkItems.sumOf { item -> (embarkPurchases[item.id] ?: 0) * item.cost }

    fun remainingEmbarkPoints(): Int = pointQuota - spentEmbarkPoints()

    fun canPurchase(item: EmbarkPoolItem): Boolean {
        val current = embarkPurchases[item.id] ?: 0
        return current < item.maxPurchases && remainingEmbarkPoints() >= item.cost
    }

    fun addEmbarkPurchase(item: EmbarkPoolItem) {
        if (!canPurchase(item)) return
        embarkPurchases[item.id] = (embarkPurchases[item.id] ?: 0) + 1
    }

    fun removeEmbarkPurchase(item: EmbarkPoolItem) {
        val current = embarkPurchases[item.id] ?: return
        if (current <= 1) {
            embarkPurchases.remove(item.id)
        } else {
            embarkPurchases[item.id] = current - 1
        }
    }

    fun canRandomizeEmbarkPurchases(): Boolean =
        pointQuota > 0 && embarkItems.any { item -> item.cost in 1..pointQuota && item.maxPurchases > 0 }

    fun randomizeEmbarkPurchases(random: Random = Random.Default): Int {
        embarkPurchases.clear()
        if (!canRandomizeEmbarkPurchases()) return 0

        val units = embarkItems
            .flatMap { item -> List(item.maxPurchases) { item } }
            .filter { item -> item.cost in 1..pointQuota }
            .shuffled(random)

        val paths = arrayOfNulls<List<EmbarkPoolItem>>(pointQuota + 1)
        paths[0] = emptyList()

        units.forEach { item ->
            for (spent in pointQuota - item.cost downTo 0) {
                val path = paths[spent] ?: continue
                val nextSpent = spent + item.cost
                if (paths[nextSpent] == null || random.nextBoolean()) {
                    paths[nextSpent] = path + item
                }
            }
        }

        val targetSpend = (pointQuota downTo 1).firstOrNull { paths[it] != null } ?: return 0
        paths[targetSpend].orEmpty()
            .groupingBy { item -> item.id }
            .eachCount()
            .forEach { (itemId, quantity) -> embarkPurchases[itemId] = quantity }

        return targetSpend
    }

    fun reconcileEmbarkPurchases() {
        val poolById = embarkItems.associateBy { it.id }
        val iterator = embarkPurchases.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val poolItem = poolById[entry.key]
            if (poolItem == null || entry.value <= 0) {
                iterator.remove()
            } else if (entry.value > poolItem.maxPurchases) {
                entry.setValue(poolItem.maxPurchases)
            }
        }

        while (remainingEmbarkPoints() < 0 && embarkPurchases.isNotEmpty()) {
            val lastSelected = embarkPurchases.keys.last()
            val poolItem = poolById[lastSelected] ?: break
            removeEmbarkPurchase(poolItem)
        }
    }

    fun reset() {
        activeInCurrentWorld = false
        selectionMode = SelectionMode.NONE
        kits = emptyList()
        embarkItems = emptyList()
        pointQuota = 0
        promptOpen = false
        selectionRequired = false
        reselectingSpawn = false
        noticeText = null
        noticeTicksRemaining = 0
        clearPendingLocks()
    }
}
