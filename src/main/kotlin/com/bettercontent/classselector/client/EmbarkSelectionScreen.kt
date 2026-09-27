package com.bettercontent.classselector.client

import com.bettercontent.classselector.embark.EmbarkPoolItem
import com.bettercontent.classselector.kit.KitItemStackFactory
import com.bettercontent.classselector.kit.KitSlot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

class EmbarkSelectionScreen(
    private val poolItems: List<EmbarkPoolItem>,
    private val pointQuota: Int
) : Screen(Component.literal("Embark Supplies")) {
    private companion object {
        private const val OUTER_PADDING = 14
        private const val TOP_MARGIN = 28
        private const val BOTTOM_MARGIN = 16
        private const val PANEL_GAP = 10
        private const val PANEL_BACKGROUND = 0xC8141820.toInt()
        private const val PANEL_BORDER = 0xFF33404D.toInt()
        private const val PANEL_ACCENT = 0xFF6E8CA6.toInt()
        private const val TEXT_PRIMARY = 0xF4F0E6
        private const val TEXT_MUTED = 0xAEB7C1
        private const val TEXT_WARM = 0xE0C58F
        private const val TEXT_GOOD = 0xA7E0A4
        private const val TEXT_BAD = 0xD58A8A
        private const val BUTTON_HEIGHT = 20
        private const val ROW_HEIGHT = 32
        private const val ITEM_COLUMNS = 2
        private const val ACTION_BUTTON_COUNT = 5
        private const val RECOMMENDED_FILTER_ID = "recommended"
        private const val ALL_FILTER_ID = "all"
        private const val RECOMMENDED_ITEM_COUNT = 12
        private val RECOMMENDED_ITEM_IDS = listOf(
            "filled_waterskin",
            "terracotta_bowl",
            "torch_bundle",
            "lantern",
            "campfire",
            "rope_coil",
            "compass",
            "empty_map",
            "apple_ration",
            "bread_ration",
            "wheat_seeds",
            "string_bundle"
        )
    }

    private data class ItemFilter(val id: String, val label: String)

    private data class Layout(
        val leftX: Int,
        val rightX: Int,
        val top: Int,
        val bottom: Int,
        val leftWidth: Int,
        val rightWidth: Int,
        val height: Int
    )

    private var page: Int = 0
    private val minusButtons: MutableList<Button> = mutableListOf()
    private val plusButtons: MutableList<Button> = mutableListOf()
    private var previousPageButton: Button? = null
    private var nextPageButton: Button? = null
    private var lockRespawnButton: Button? = null
    private var clearRespawnButton: Button? = null
    private var clearSuppliesButton: Button? = null
    private var randomizeSuppliesButton: Button? = null
    private var beginButton: Button? = null
    private val filterButtons: MutableList<Button> = mutableListOf()
    private var selectedFilterId: String = RECOMMENDED_FILTER_ID
    private var filterAreaBottom: Int = 0
    private var hoveredItem: ItemStack = ItemStack.EMPTY

    override fun init() {
        minusButtons.clear()
        plusButtons.clear()
        filterButtons.clear()

        if (poolItems.isEmpty()) {
            previousPageButton = null
            nextPageButton = null
            lockRespawnButton = null
            clearRespawnButton = null
            clearSuppliesButton = null
            randomizeSuppliesButton = null
            beginButton = null
            return
        }

        val layout = computeLayout()
        buildFilterButtons(layout)
        val capacity = pageCapacity(layout)
        page = page.coerceIn(0, maxPage(capacity))

        repeat(capacity) { index ->
            val cell = itemCell(layout, index)
            minusButtons += addRenderableWidget(
                Button.builder(Component.literal("-")) {
                    visibleItems().getOrNull(index)?.let(ClassSelectionState::removeEmbarkPurchase)
                    refreshButtonState()
                }.pos(cell.controlX, cell.y).size(18, BUTTON_HEIGHT).build()
            )
            plusButtons += addRenderableWidget(
                Button.builder(Component.literal("+")) {
                    visibleItems().getOrNull(index)?.let(ClassSelectionState::addEmbarkPurchase)
                    refreshButtonState()
                }.pos(cell.controlX + 20, cell.y).size(18, BUTTON_HEIGHT).build()
            )
        }

        val pageY = layout.bottom - BUTTON_HEIGHT - 8
        previousPageButton = addRenderableWidget(
            Button.builder(Component.literal("<")) {
                page = (page - 1).coerceAtLeast(0)
                refreshButtonState()
            }.pos(layout.leftX + 8, pageY).size(36, BUTTON_HEIGHT).build()
        )
        nextPageButton = addRenderableWidget(
            Button.builder(Component.literal(">")) {
                page = (page + 1).coerceAtMost(maxPage(capacity))
                refreshButtonState()
            }.pos(layout.leftX + layout.leftWidth - 44, pageY).size(36, BUTTON_HEIGHT).build()
        )

        val actionX = layout.rightX + 8
        val actionWidth = layout.rightWidth - 16
        val actionBottom = layout.bottom - 8

        beginButton = addRenderableWidget(
            Button.builder(Component.literal("Begin")) {
                val purchases = ClassSelectionState.selectedEmbarkPurchases()
                if (!ClientOnboardingActions.submitEmbarkSelection(purchases)) return@builder
                onClose()
            }.pos(actionX, actionBottom - BUTTON_HEIGHT).size(actionWidth, BUTTON_HEIGHT).build()
        )

        clearRespawnButton = addRenderableWidget(
            Button.builder(Component.literal("Unlock Respawn")) {
                ClientOnboardingActions.clearLockedRespawn()
                refreshButtonState()
            }.pos(actionX, actionBottom - (BUTTON_HEIGHT + 4) * 2).size(actionWidth, BUTTON_HEIGHT).build()
        )

        lockRespawnButton = addRenderableWidget(
            Button.builder(Component.literal("Lock Current Respawn")) {
                if (!ClientOnboardingActions.lockCurrentRespawn(minecraft ?: return@builder)) return@builder
                refreshButtonState()
            }.pos(actionX, actionBottom - (BUTTON_HEIGHT + 4) * 3).size(actionWidth, BUTTON_HEIGHT).build()
        )

        clearSuppliesButton = addRenderableWidget(
            Button.builder(Component.literal("Clear Supplies")) {
                ClassSelectionState.embarkPurchases.clear()
                refreshButtonState()
            }.pos(actionX, actionBottom - (BUTTON_HEIGHT + 4) * 4).size(actionWidth, BUTTON_HEIGHT).build()
        )

        randomizeSuppliesButton = addRenderableWidget(
            Button.builder(Component.literal("Randomize Supplies")) {
                ClassSelectionState.randomizeEmbarkPurchases()
                refreshButtonState()
            }.pos(actionX, actionBottom - (BUTTON_HEIGHT + 4) * 5).size(actionWidth, BUTTON_HEIGHT).build()
        )

        refreshButtonState()
    }

    private fun refreshButtonState() {
        val capacity = minusButtons.size.coerceAtLeast(1)
        page = page.coerceIn(0, maxPage(capacity))
        val visible = visibleItems()

        minusButtons.forEachIndexed { index, button ->
            val item = visible.getOrNull(index)
            val quantity = item?.let { ClassSelectionState.embarkPurchases[it.id] } ?: 0
            button.visible = item != null
            button.active = item != null && quantity > 0
        }

        plusButtons.forEachIndexed { index, button ->
            val item = visible.getOrNull(index)
            button.visible = item != null
            button.active = item != null && ClassSelectionState.canPurchase(item)
        }

        previousPageButton?.active = page > 0
        nextPageButton?.active = page < maxPage(capacity)
        filterButtons.forEachIndexed { index, button ->
            val filter = filters().getOrNull(index)
            button.visible = filter != null
            button.active = filter != null && filter.id != selectedFilterId
        }
        lockRespawnButton?.active = ClassSelectionState.activeInCurrentWorld
        clearRespawnButton?.active = ClassSelectionState.activeInCurrentWorld && ClassSelectionState.lockedRespawn != null
        clearSuppliesButton?.active = ClassSelectionState.activeInCurrentWorld && ClassSelectionState.embarkPurchases.isNotEmpty()
        randomizeSuppliesButton?.active = ClassSelectionState.activeInCurrentWorld && ClassSelectionState.canRandomizeEmbarkPurchases()
        beginButton?.active = ClassSelectionState.activeInCurrentWorld &&
            ClassSelectionState.lockedRespawn != null &&
            ClassSelectionState.selectedEmbarkPurchases().isNotEmpty()
        beginButton?.message = Component.literal(beginButtonLabel())
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(gui)
        hoveredItem = ItemStack.EMPTY

        gui.fill(0, 0, width, height, 0xE10A0D12.toInt())
        gui.drawCenteredString(font, title, width / 2, 10, TEXT_PRIMARY)

        if (poolItems.isEmpty()) {
            gui.drawCenteredString(font, Component.literal("No embark items available."), width / 2, height / 2 - 10, TEXT_BAD)
            gui.drawCenteredString(font, Component.literal("Ask an admin to configure embark.json."), width / 2, height / 2 + 4, TEXT_MUTED)
            super.render(gui, mouseX, mouseY, partialTick)
            return
        }

        val layout = computeLayout()
        drawPanel(gui, layout.leftX, layout.top, layout.leftWidth, layout.height, "Supply Pool")
        drawPanel(gui, layout.rightX, layout.top, layout.rightWidth, layout.height, "Embark Checklist")

        renderPoolPanel(gui, layout, mouseX, mouseY)
        renderSummaryPanel(gui, layout)

        hoveredItem.takeIf { !it.isEmpty }?.let {
            gui.renderTooltip(font, it, mouseX, mouseY)
        }

        super.render(gui, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen(): Boolean = false

    private fun renderPoolPanel(gui: GuiGraphics, layout: Layout, mouseX: Int, mouseY: Int) {
        val innerX = layout.leftX + 8
        val innerWidth = layout.leftWidth - 16
        var y = layout.top + 24
        val spent = ClassSelectionState.spentEmbarkPoints()
        val remaining = ClassSelectionState.remainingEmbarkPoints()

        gui.drawString(font, Component.literal("Points: $remaining available / $pointQuota"), innerX, y, if (remaining >= 0) TEXT_GOOD else TEXT_BAD)
        val spentLabel = "Spent: $spent"
        gui.drawString(font, Component.literal(spentLabel), innerX + innerWidth - font.width(spentLabel), y, TEXT_MUTED)
        y += 14
        gui.drawString(font, Component.literal(currentFilterCaption()), innerX, y, TEXT_WARM)

        visibleItems().forEachIndexed { index, item ->
            val cell = itemCell(layout, index)
            val rowTop = cell.y
            val stack = KitItemStackFactory.create(item.item, item.count) ?: ItemStack.EMPTY
            val quantity = ClassSelectionState.embarkPurchases[item.id] ?: 0
            val rowBottom = rowTop + ROW_HEIGHT - 1
            val rowColor = if (quantity > 0) 0x55304024 else 0x4411141A

            gui.fill(cell.x, rowTop, cell.x + cell.width - 4, rowBottom, rowColor)
            if (!stack.isEmpty) {
                gui.renderFakeItem(stack, cell.x + 2, rowTop + 2)
                gui.renderItemDecorations(font, stack, cell.x + 2, rowTop + 2)
                if (mouseX in (cell.x + 2) until (cell.x + 18) && mouseY in (rowTop + 2) until (rowTop + 18)) {
                    hoveredItem = stack
                }
            }

            val textX = cell.x + 22
            val controlsX = cell.controlX - 3
            val textWidth = (controlsX - textX - 4).coerceAtLeast(60)
            val metaColor = rowMetaColor(item, quantity)

            gui.drawString(font, Component.literal(fitText(item.title, textWidth)), textX, rowTop + 1, TEXT_PRIMARY)
            gui.drawString(font, Component.literal(fitText(item.blurb, textWidth)), textX, rowTop + 11, TEXT_MUTED)
            gui.drawString(font, Component.literal(fitText(rowMetaLabel(item, quantity), textWidth)), textX, rowTop + 21, metaColor)
        }

        val capacity = pageCapacity(layout)
        val pageLabel = "Page ${page + 1} / ${maxPage(capacity) + 1}"
        gui.drawCenteredString(font, Component.literal(pageLabel), layout.leftX + layout.leftWidth / 2, layout.bottom - 22, TEXT_MUTED)
    }

    private fun renderSummaryPanel(gui: GuiGraphics, layout: Layout) {
        val innerX = layout.rightX + 10
        val innerWidth = layout.rightWidth - 20
        val contentBottom = actionAreaTop(layout) - 8
        gui.fill(innerX - 2, actionAreaTop(layout) - 6, innerX + innerWidth + 2, actionAreaTop(layout) - 5, PANEL_BORDER)
        var y = layout.top + 10
        val lockedRespawn = ClassSelectionState.lockedRespawn
        val currentPlayer = minecraft?.player
        val currentDim = currentPlayer?.level()?.dimension()?.location()?.toString() ?: "unknown"
        val currentCoords = currentPlayer?.let { "${it.blockX} ${it.blockY} ${it.blockZ}" } ?: "unknown"
        val purchases = ClassSelectionState.selectedEmbarkPurchases()

        gui.drawString(font, Component.literal("How to begin"), innerX, y, TEXT_PRIMARY)
        y += 12
        y += renderWrappedBlock(
            gui,
            Component.literal("1. Pick supplies. 2. Scout in spectator, reopen with K, and lock respawn. 3. Press Begin once or Shift+K to confirm."),
            innerX,
            y,
            innerWidth,
            5,
            TEXT_WARM
        )
        y += 8

        gui.drawString(font, Component.literal("Status"), innerX, y, TEXT_PRIMARY)
        y += 12
        y += renderStatusBlock(gui, innerX, innerWidth, y, "Items picked", purchases.sumOf { it.quantity }.toString(), purchases.isNotEmpty())
        y += 4
        y += renderStatusBlock(gui, innerX, innerWidth, y, "Respawn lock", lockedRespawn?.let { "${it.x} ${it.y} ${it.z}" } ?: "Missing", lockedRespawn != null)
        y += 10

        if (y >= contentBottom) return
        gui.drawString(font, Component.literal("Selected supplies"), innerX, y, TEXT_PRIMARY)
        y += 12
        if (purchases.isEmpty()) {
            gui.drawString(font, Component.literal("None selected."), innerX, y, TEXT_BAD)
            y += 12
        } else {
            val itemsById = poolItems.associateBy { it.id }
            val lineCapacity = ((contentBottom - y - 2) / 10).coerceAtLeast(0)
            val visibleCount = if (purchases.size > lineCapacity && lineCapacity > 1) lineCapacity - 1 else lineCapacity

            purchases.take(visibleCount).forEach { purchase ->
                val item = itemsById[purchase.itemId]
                val title = item?.title ?: purchase.itemId
                val totalCost = item?.let { it.cost * purchase.quantity }
                val costLabel = totalCost?.let { " ($it pts)" }.orEmpty()
                gui.drawString(font, Component.literal(fitText("${purchase.quantity}x $title$costLabel", innerWidth)), innerX, y, TEXT_GOOD)
                y += 10
            }
            val hidden = purchases.size - visibleCount
            if (hidden > 0 && y < contentBottom) {
                gui.drawString(font, Component.literal("+$hidden more"), innerX, y, TEXT_MUTED)
                y += 12
            }
        }

        y += 6
        if (y >= contentBottom - 12) return
        gui.drawString(font, Component.literal("Current spot"), innerX, y, TEXT_PRIMARY)
        y += 12
        y += renderWrappedBlock(gui, Component.literal(currentDim), innerX, y, innerWidth, 1, TEXT_MUTED)
        y += 2
        if (y < contentBottom) {
            gui.drawString(font, Component.literal(currentCoords), innerX, y, TEXT_MUTED)
        }
        y += 16

        if (y >= contentBottom - 12) return
        gui.drawString(font, Component.literal("Locked respawn"), innerX, y, TEXT_PRIMARY)
        y += 12
        if (lockedRespawn == null) {
            gui.drawString(font, Component.literal("Not locked yet."), innerX, y, TEXT_BAD)
        } else {
            y += renderWrappedBlock(gui, Component.literal(lockedRespawn.dim), innerX, y, innerWidth, 1, TEXT_GOOD)
            y += 2
            if (y < contentBottom) {
                gui.drawString(font, Component.literal("${lockedRespawn.x} ${lockedRespawn.y} ${lockedRespawn.z}"), innerX, y, TEXT_GOOD)
            }
        }

    }

    private fun renderStatusBlock(gui: GuiGraphics, x: Int, width: Int, y: Int, label: String, value: String, ready: Boolean): Int {
        val color = if (ready) TEXT_GOOD else TEXT_BAD
        gui.drawString(font, Component.literal(label), x, y, TEXT_MUTED)
        return 10 + renderWrappedBlock(gui, Component.literal(value), x, y + 10, width, 2, color)
    }

    private fun renderWrappedBlock(
        gui: GuiGraphics,
        text: Component,
        x: Int,
        y: Int,
        width: Int,
        maxLines: Int,
        color: Int
    ): Int {
        val lines = font.split(text, width).take(maxLines)
        lines.forEachIndexed { index, line ->
            gui.drawString(font, line, x, y + index * 10, color)
        }
        return lines.size * 10
    }

    private fun drawPanel(gui: GuiGraphics, x: Int, y: Int, width: Int, height: Int, label: String) {
        gui.fill(x, y, x + width, y + height, PANEL_BACKGROUND)
        gui.fill(x, y, x + width, y + 1, PANEL_BORDER)
        gui.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER)
        gui.fill(x, y, x + 1, y + height, PANEL_BORDER)
        gui.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER)
        gui.fill(x, y, x + width, y + 18, 0x66243340)
        gui.fill(x, y + 18, x + width, y + 19, PANEL_ACCENT)
        gui.drawString(font, Component.literal(label), x + 8, y + 5, TEXT_PRIMARY)
    }

    private fun computeLayout(): Layout {
        val fullWidth = width - OUTER_PADDING * 2
        val rightWidth = (fullWidth / 3).coerceIn(190, 230)
        val leftWidth = fullWidth - rightWidth - PANEL_GAP
        val top = TOP_MARGIN
        val height = height - TOP_MARGIN - BOTTOM_MARGIN
        val leftX = OUTER_PADDING
        val rightX = leftX + leftWidth + PANEL_GAP

        return Layout(
            leftX = leftX,
            rightX = rightX,
            top = top,
            bottom = top + height,
            leftWidth = leftWidth,
            rightWidth = rightWidth,
            height = height
        )
    }

    private fun rowsPerPage(layout: Layout): Int {
        val availableHeight = layout.bottom - poolRowTop(layout) - 36
        val neededRows = ((filteredItems().size + ITEM_COLUMNS - 1) / ITEM_COLUMNS).coerceAtLeast(1)
        return (availableHeight / ROW_HEIGHT).coerceIn(1, neededRows.coerceAtMost(20))
    }

    private data class ItemCell(val x: Int, val y: Int, val width: Int, val controlX: Int)

    private fun itemCell(layout: Layout, index: Int): ItemCell {
        val innerX = layout.leftX + 8
        val innerWidth = layout.leftWidth - 16
        val columnWidth = innerWidth / ITEM_COLUMNS
        val column = index % ITEM_COLUMNS
        val row = index / ITEM_COLUMNS
        val x = innerX + column * columnWidth
        val width = if (column == ITEM_COLUMNS - 1) innerWidth - columnWidth * column else columnWidth
        return ItemCell(
            x = x,
            y = poolRowTop(layout) + row * ROW_HEIGHT,
            width = width,
            controlX = x + width - 44
        )
    }

    private fun pageCapacity(layout: Layout): Int = rowsPerPage(layout) * ITEM_COLUMNS

    private fun poolRowTop(layout: Layout): Int = filterAreaBottom + 10

    private fun actionAreaTop(layout: Layout): Int {
        return layout.bottom - 8 - ((BUTTON_HEIGHT + 4) * ACTION_BUTTON_COUNT)
    }

    private fun visibleItems(): List<EmbarkPoolItem> {
        val capacity = minusButtons.size.coerceAtLeast(1)
        return filteredItems().drop(page * capacity).take(capacity)
    }

    private fun maxPage(capacity: Int): Int = ((filteredItems().size - 1) / capacity.coerceAtLeast(1)).coerceAtLeast(0)

    private fun slotLabel(item: EmbarkPoolItem): String {
        val label = KitSlot.label(item.slot) ?: return ""
        return label
    }

    private fun rowMetaLabel(item: EmbarkPoolItem, quantity: Int): String {
        val parts = mutableListOf<String>()
        item.category?.takeIf { it.isNotBlank() }?.let(parts::add)
        parts += "${item.cost} pt"
        parts += "Picked $quantity/${item.maxPurchases}"
        parts += "Gives ${item.count}"
        slotLabel(item).takeIf { it.isNotBlank() && it != "Inventory" }?.let { parts += it }
        parts += purchaseStateLabel(item, quantity)
        return parts.joinToString(" | ")
    }

    private fun rowMetaColor(item: EmbarkPoolItem, quantity: Int): Int {
        return when {
            quantity > 0 -> TEXT_GOOD
            ClassSelectionState.canPurchase(item) -> TEXT_MUTED
            else -> TEXT_BAD
        }
    }

    private fun purchaseStateLabel(item: EmbarkPoolItem, quantity: Int): String {
        val remaining = ClassSelectionState.remainingEmbarkPoints()
        return when {
            quantity >= item.maxPurchases -> "Max"
            remaining < item.cost -> "Need ${item.cost - remaining}"
            else -> "Available"
        }
    }

    private fun beginButtonLabel(): String {
        return when {
            !ClassSelectionState.activeInCurrentWorld -> "Join World"
            ClassSelectionState.selectedEmbarkPurchases().isEmpty() -> "Pick Supplies"
            ClassSelectionState.lockedRespawn == null -> "Lock Respawn"
            else -> "Begin"
        }
    }

    private fun fitText(text: String, maxWidth: Int): String {
        if (text.isBlank() || font.width(text) <= maxWidth) return text
        val suffix = "..."
        val width = (maxWidth - font.width(suffix)).coerceAtLeast(0)
        return font.plainSubstrByWidth(text, width).trimEnd() + suffix
    }

    private fun buildFilterButtons(layout: Layout) {
        val filters = filters()
        val startX = layout.leftX + 8
        val maxX = layout.leftX + layout.leftWidth - 8
        var x = startX
        var y = layout.top + 42

        filters.forEach { filter ->
            val buttonWidth = (font.width(filter.label) + 16).coerceIn(58, 110)
            if (x + buttonWidth > maxX) {
                x = startX
                y += BUTTON_HEIGHT + 4
            }
            filterButtons += addRenderableWidget(
                Button.builder(Component.literal(filter.label)) {
                    selectedFilterId = filter.id
                    page = 0
                    refreshButtonState()
                }.pos(x, y).size(buttonWidth, BUTTON_HEIGHT).build()
            )
            x += buttonWidth + 4
        }

        filterAreaBottom = y + BUTTON_HEIGHT
    }

    private fun filters(): List<ItemFilter> {
        val categories = linkedSetOf<String>()
        poolItems.forEach { item ->
            item.category?.trim()?.takeIf { it.isNotEmpty() }?.let(categories::add)
        }
        return buildList {
            add(ItemFilter(RECOMMENDED_FILTER_ID, "Recommended"))
            add(ItemFilter(ALL_FILTER_ID, "All"))
            categories.forEach { category ->
                add(ItemFilter(category.lowercase(), category))
            }
        }
    }

    private fun filteredItems(): List<EmbarkPoolItem> {
        return when (selectedFilterId) {
            RECOMMENDED_FILTER_ID -> recommendedItems()
            ALL_FILTER_ID -> poolItems
            else -> poolItems.filter { it.category?.trim()?.equals(selectedFilterId, ignoreCase = true) == true }
        }
    }

    private fun recommendedItems(): List<EmbarkPoolItem> {
        val byId = poolItems.associateBy { it.id }
        val curated = RECOMMENDED_ITEM_IDS.mapNotNull(byId::get)
        if (curated.size >= RECOMMENDED_ITEM_COUNT) return curated.take(RECOMMENDED_ITEM_COUNT)

        val usedIds = curated.mapTo(linkedSetOf(), EmbarkPoolItem::id)
        return curated + poolItems.asSequence()
            .filterNot { it.id in usedIds }
            .take(RECOMMENDED_ITEM_COUNT - curated.size)
            .toList()
    }

    private fun currentFilterCaption(): String {
        return when (selectedFilterId) {
            RECOMMENDED_FILTER_ID -> "Starter-friendly picks"
            ALL_FILTER_ID -> "Full embark pool"
            else -> filters().firstOrNull { it.id == selectedFilterId }?.label ?: "Filtered"
        }
    }
}
