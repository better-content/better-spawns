package com.bettercontent.classselector.client

import com.bettercontent.classselector.kit.ClassKit
import com.bettercontent.classselector.kit.KitItem
import com.bettercontent.classselector.kit.KitItemStackFactory
import com.bettercontent.classselector.kit.KitSlot
import com.bettercontent.classselector.kit.KitSlotTarget
import com.mojang.authlib.GameProfile
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemStack
import java.util.UUID

class ClassSelectionScreen(private val kits: List<ClassKit>) : Screen(Component.literal("Class Lock-In")) {
    private companion object {
        private const val PREVIEW_PROFILE_SEED = "class_selector-preview"
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
    }

    private data class Layout(
        val leftX: Int,
        val centerX: Int,
        val rightX: Int,
        val top: Int,
        val bottom: Int,
        val leftWidth: Int,
        val centerWidth: Int,
        val rightWidth: Int,
        val height: Int
    ) {
        val leftRight: Int get() = leftX + leftWidth
        val centerRight: Int get() = centerX + centerWidth
        val rightRight: Int get() = rightX + rightWidth
    }

    private var selectedKitIndex: Int = 0
    private val classButtons: MutableList<Button> = mutableListOf()
    private var lockClassButton: Button? = null
    private var unlockClassButton: Button? = null
    private var lockRespawnButton: Button? = null
    private var clearRespawnButton: Button? = null
    private var beginButton: Button? = null
    private var hoveredItem: ItemStack = ItemStack.EMPTY

    override fun init() {
        classButtons.clear()

        if (kits.isEmpty()) {
            lockClassButton = null
            unlockClassButton = null
            lockRespawnButton = null
            clearRespawnButton = null
            beginButton = null
            return
        }

        ClassSelectionState.lockedClassId?.let { lockedId ->
            val lockedIndex = kits.indexOfFirst { it.id == lockedId }
            if (lockedIndex >= 0) {
                selectedKitIndex = lockedIndex
            }
        }
        selectedKitIndex = selectedKitIndex.coerceIn(0, kits.lastIndex)

        val layout = computeLayout()
        val listButtonX = layout.leftX + 8
        val listButtonWidth = layout.leftWidth - 16
        val listTop = layout.top + 34

        kits.forEachIndexed { index, kit ->
            val y = listTop + index * (BUTTON_HEIGHT + 4)
            val button = addRenderableWidget(
                Button.builder(Component.literal(kit.title)) {
                    selectedKitIndex = index
                    refreshButtonState()
                }.pos(listButtonX, y).size(listButtonWidth, BUTTON_HEIGHT).build()
            )
            classButtons += button
        }

        val actionX = layout.rightX + 8
        val actionWidth = layout.rightWidth - 16
        val actionBottom = layout.bottom - 8

        beginButton = addRenderableWidget(
            Button.builder(Component.literal("Begin")) {
                val classId = ClassSelectionState.lockedClassId ?: return@builder
                if (!ClientOnboardingActions.submitClassSelection(classId)) return@builder
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

        unlockClassButton = addRenderableWidget(
            Button.builder(Component.literal("Unlock Class")) {
                ClassSelectionState.lockedClassId = null
                refreshButtonState()
            }.pos(actionX, actionBottom - (BUTTON_HEIGHT + 4) * 4).size(actionWidth, BUTTON_HEIGHT).build()
        )

        lockClassButton = addRenderableWidget(
            Button.builder(Component.literal("Lock Selected Class")) {
                val selected = kits.getOrNull(selectedKitIndex) ?: return@builder
                ClassSelectionState.lockedClassId = selected.id
                refreshButtonState()
            }.pos(actionX, actionBottom - (BUTTON_HEIGHT + 4) * 5).size(actionWidth, BUTTON_HEIGHT).build()
        )

        refreshButtonState()
    }

    private fun refreshButtonState() {
        val selected = kits.getOrNull(selectedKitIndex)
        val lockedClassId = ClassSelectionState.lockedClassId
        val lockedRespawn = ClassSelectionState.lockedRespawn

        classButtons.forEachIndexed { index, button ->
            val kit = kits[index]
            val marker = buildString {
                if (index == selectedKitIndex) append("> ")
                if (kit.id == lockedClassId) append("[L] ")
            }
            button.message = Component.literal(marker + kit.title)
            button.active = index != selectedKitIndex
        }

        lockClassButton?.active = ClassSelectionState.activeInCurrentWorld && selected != null && selected.id != lockedClassId
        unlockClassButton?.active = ClassSelectionState.activeInCurrentWorld && lockedClassId != null
        lockRespawnButton?.active = ClassSelectionState.activeInCurrentWorld
        clearRespawnButton?.active = ClassSelectionState.activeInCurrentWorld && lockedRespawn != null
        beginButton?.active = ClassSelectionState.activeInCurrentWorld && lockedClassId != null && lockedRespawn != null
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(gui)
        hoveredItem = ItemStack.EMPTY

        gui.fill(0, 0, width, height, 0xE10A0D12.toInt())
        gui.drawCenteredString(font, title, width / 2, 10, TEXT_PRIMARY)

        if (kits.isEmpty()) {
            gui.drawCenteredString(font, Component.literal("No classes available."), width / 2, height / 2 - 10, TEXT_BAD)
            gui.drawCenteredString(font, Component.literal("Ask an admin to configure class kits."), width / 2, height / 2 + 4, TEXT_MUTED)
            super.render(gui, mouseX, mouseY, partialTick)
            return
        }

        val selected = kits[selectedKitIndex]
        val layout = computeLayout()

        drawPanel(gui, layout.leftX, layout.top, layout.leftWidth, layout.height, "Classes")
        drawPanel(gui, layout.centerX, layout.top, layout.centerWidth, layout.height, "Selected Class")
        drawPanel(gui, layout.rightX, layout.top, layout.rightWidth, layout.height, "Lock-In Checklist")

        renderLeftPanel(gui, layout)
        renderCenterPanel(gui, layout, selected, mouseX, mouseY)
        renderRightPanel(gui, layout, selected, mouseX, mouseY)

        hoveredItem.takeIf { !it.isEmpty }?.let {
            gui.renderTooltip(font, it, mouseX, mouseY)
        }

        super.render(gui, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen(): Boolean = false

    private fun renderLeftPanel(gui: GuiGraphics, layout: Layout) {
        val lockedTitle = kits.firstOrNull { it.id == ClassSelectionState.lockedClassId }?.title ?: "None"
        gui.drawString(font, Component.literal("Locked: $lockedTitle"), layout.leftX + 8, layout.bottom - 18, TEXT_MUTED)
    }

    private fun renderCenterPanel(gui: GuiGraphics, layout: Layout, kit: ClassKit, mouseX: Int, mouseY: Int) {
        val innerX = layout.centerX + 10
        val innerWidth = layout.centerWidth - 20
        var y = layout.top + 10

        gui.drawString(font, Component.literal(kit.title), innerX, y, TEXT_PRIMARY)
        y += 15
        gui.drawString(font, Component.literal(kit.blurb), innerX, y, TEXT_WARM)
        y += 18

        y += renderWrappedBlock(gui, Component.literal(kit.description), innerX, y, innerWidth, 5, TEXT_MUTED)
        y += 8

        gui.drawString(font, Component.literal("Flow"), innerX, y, TEXT_PRIMARY)
        y += 12
        y += renderWrappedBlock(
            gui,
            Component.literal("1. Choose and lock a class. 2. Scout in spectator, reopen with K, and lock your respawn. 3. Press Begin once or Shift+K to confirm."),
            innerX,
            y,
            innerWidth,
            6,
            TEXT_WARM
        )
        y += 12

        val itemsHeaderY = y
        gui.drawString(font, Component.literal("Kit Items"), innerX, itemsHeaderY, TEXT_PRIMARY)
        val itemsBoxY = itemsHeaderY + 12
        val itemsBoxHeight = layout.bottom - itemsBoxY - 10
        gui.fill(innerX - 2, itemsBoxY - 2, innerX + innerWidth + 2, itemsBoxY + itemsBoxHeight, 0x5511141A)
        renderKitItems(gui, kit, innerX + 6, itemsBoxY + 6, innerWidth - 12, itemsBoxHeight - 12, mouseX, mouseY)
    }

    private fun renderRightPanel(gui: GuiGraphics, layout: Layout, kit: ClassKit, mouseX: Int, mouseY: Int) {
        val innerX = layout.rightX + 10
        val innerWidth = layout.rightWidth - 20
        val previewBoxTop = layout.top + 10
        val previewBoxHeight = 116
        val previewBoxBottom = previewBoxTop + previewBoxHeight

        gui.fill(innerX - 2, previewBoxTop - 2, innerX + innerWidth + 2, previewBoxBottom, 0x5511141A)
        gui.drawCenteredString(font, Component.literal("Preview"), innerX + innerWidth / 2, previewBoxTop + 4, TEXT_PRIMARY)
        buildPreviewPlayer(kit)?.let { previewPlayer ->
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                gui,
                innerX + innerWidth / 2,
                previewBoxBottom - 10,
                42,
                (innerX + innerWidth / 2 - mouseX).toFloat(),
                (previewBoxBottom - 48 - mouseY).toFloat(),
                previewPlayer
            )
        }

        var y = previewBoxBottom + 10
        val lockedClass = kits.firstOrNull { it.id == ClassSelectionState.lockedClassId }
        val lockedRespawn = ClassSelectionState.lockedRespawn
        val currentPlayer = minecraft?.player
        val currentDim = currentPlayer?.level()?.dimension()?.location()?.toString() ?: "unknown"
        val currentCoords = currentPlayer?.let { "${it.blockX} ${it.blockY} ${it.blockZ}" } ?: "unknown"

        gui.drawString(font, Component.literal("Status"), innerX, y, TEXT_PRIMARY)
        y += 12
        y += renderStatusBlock(gui, innerX, innerWidth, y, "Class lock", lockedClass?.title ?: "Missing", lockedClass != null)
        y += 4
        y += renderStatusBlock(gui, innerX, innerWidth, y, "Respawn lock", lockedRespawn?.let { "${it.x} ${it.y} ${it.z}" } ?: "Missing", lockedRespawn != null)
        y += 10

        gui.drawString(font, Component.literal("Current spot"), innerX, y, TEXT_PRIMARY)
        y += 12
        y += renderWrappedBlock(gui, Component.literal(currentDim), innerX, y, innerWidth, 2, TEXT_MUTED)
        y += 2
        gui.drawString(font, Component.literal(currentCoords), innerX, y, TEXT_MUTED)
        y += 16

        gui.drawString(font, Component.literal("Locked respawn"), innerX, y, TEXT_PRIMARY)
        y += 12
        if (lockedRespawn == null) {
            gui.drawString(font, Component.literal("Not locked yet."), innerX, y, TEXT_BAD)
        } else {
            y += renderWrappedBlock(gui, Component.literal(lockedRespawn.dim), innerX, y, innerWidth, 2, TEXT_GOOD)
            y += 2
            gui.drawString(font, Component.literal("${lockedRespawn.x} ${lockedRespawn.y} ${lockedRespawn.z}"), innerX, y, TEXT_GOOD)
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

    private fun renderKitItems(
        gui: GuiGraphics,
        kit: ClassKit,
        startX: Int,
        startY: Int,
        availableWidth: Int,
        availableHeight: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val displayItems = kit.items.mapNotNull(::toDisplayEntry)
        val cellWidth = 28
        val cellHeight = 28
        val columns = (availableWidth / cellWidth).coerceIn(4, 6)
        val rows = (availableHeight / cellHeight).coerceAtLeast(1)
        val visibleCount = (columns * rows).coerceAtMost(displayItems.size)

        displayItems.take(visibleCount).forEachIndexed { index, entry ->
            val x = startX + (index % columns) * cellWidth
            val y = startY + (index / columns) * cellHeight

            gui.renderFakeItem(entry.stack, x, y)
            gui.renderItemDecorations(font, entry.stack, x, y)

            entry.slotLabel?.let { slotLabel ->
                gui.drawCenteredString(font, Component.literal(slotLabel), x + 8, y + 18, TEXT_MUTED)
            }

            if (mouseX in x until (x + 16) && mouseY in y until (y + 16)) {
                hoveredItem = entry.stack
            }
        }

        val hiddenCount = displayItems.size - visibleCount
        if (hiddenCount > 0) {
            gui.drawString(font, Component.literal("+$hiddenCount more item${if (hiddenCount == 1) "" else "s"}"), startX, startY + rows * cellHeight + 2, TEXT_MUTED)
        }

        val invalidCount = kit.items.size - displayItems.size
        if (invalidCount > 0) {
            gui.drawString(font, Component.literal("$invalidCount invalid item${if (invalidCount == 1) "" else "s"}"), startX + 82, startY + rows * cellHeight + 2, TEXT_BAD)
        }
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
        val leftWidth = (fullWidth / 5).coerceIn(132, 154)
        var rightWidth = (fullWidth / 4).coerceIn(180, 208)
        var centerWidth = fullWidth - leftWidth - rightWidth - PANEL_GAP * 2

        if (centerWidth < 230) {
            val recovered = (230 - centerWidth).coerceAtMost(rightWidth - 160)
            rightWidth -= recovered
            centerWidth += recovered
        }

        val top = TOP_MARGIN
        val height = height - TOP_MARGIN - BOTTOM_MARGIN
        val leftX = OUTER_PADDING
        val centerX = leftX + leftWidth + PANEL_GAP
        val rightX = centerX + centerWidth + PANEL_GAP

        return Layout(
            leftX = leftX,
            centerX = centerX,
            rightX = rightX,
            top = top,
            bottom = top + height,
            leftWidth = leftWidth,
            centerWidth = centerWidth,
            rightWidth = rightWidth,
            height = height
        )
    }

    private fun buildPreviewPlayer(kit: ClassKit): net.minecraft.client.player.RemotePlayer? {
        val mc = minecraft ?: return null
        val level = mc.level ?: return null
        val localPlayer = mc.player
        val baseProfile = localPlayer?.gameProfile
        val previewProfile = GameProfile(UUID.nameUUIDFromBytes(PREVIEW_PROFILE_SEED.toByteArray()), baseProfile?.name ?: "Preview")
        val preview = net.minecraft.client.player.RemotePlayer(level, previewProfile)

        preview.setPos(0.0, 0.0, 0.0)
        preview.setOnGround(true)
        preview.setInvisible(false)
        preview.setPose(Pose.STANDING)
        preview.setXRot(0f)
        preview.setYRot(0f)
        preview.setYHeadRot(0f)
        preview.setYBodyRot(0f)

        kit.items.forEachIndexed { index, item ->
            equipPreviewItem(preview, item, index)
        }
        return preview
    }

    private fun equipPreviewItem(player: net.minecraft.client.player.RemotePlayer, kitItem: KitItem, index: Int) {
        val stack = resolveStack(kitItem) ?: return
        when (val slotTarget = KitSlot.parse(kitItem.slot)) {
            KitSlotTarget.Inventory -> {
                if (index == previewMainhandItemIndex()) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, stack)
                }
            }
            is KitSlotTarget.Hotbar -> {
                if (index == previewMainhandItemIndex()) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, stack)
                }
            }
            KitSlotTarget.Offhand -> player.setItemSlot(EquipmentSlot.OFFHAND, stack)
            is KitSlotTarget.Armor -> player.setItemSlot(slotTarget.slot, stack)
            is KitSlotTarget.Curio -> {}
            is KitSlotTarget.Unknown -> {}
        }
    }

    private fun previewMainhandItemIndex(): Int {
        val items = kits.getOrNull(selectedKitIndex)?.items ?: return -1
        val exactHotbar = items.indexOfFirst { KitSlot.parse(it.slot) == KitSlotTarget.Hotbar(0) }
        if (exactHotbar >= 0) return exactHotbar
        val anyHotbar = items.indexOfFirst { KitSlot.parse(it.slot) is KitSlotTarget.Hotbar }
        if (anyHotbar >= 0) return anyHotbar
        return items.indexOfFirst { KitSlot.parse(it.slot) == KitSlotTarget.Inventory }
    }

    private fun toDisplayEntry(kitItem: KitItem): DisplayItemEntry? {
        val stack = resolveStack(kitItem) ?: return null
        return DisplayItemEntry(stack, KitSlot.label(kitItem.slot))
    }

    private fun resolveStack(kitItem: KitItem): ItemStack? {
        return KitItemStackFactory.create(kitItem.item, kitItem.count)
    }

    private data class DisplayItemEntry(val stack: ItemStack, val slotLabel: String?)
}
