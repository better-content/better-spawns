package com.bettercontent.betterspawns.client

import com.bettercontent.betterspawns.ClassSelectorMod
import com.bettercontent.betterspawns.embark.SelectionMode
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.client.event.RenderGuiEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.lwjgl.glfw.GLFW

@Mod.EventBusSubscriber(modid = ClassSelectorMod.MOD_ID, value = [Dist.CLIENT], bus = Mod.EventBusSubscriber.Bus.MOD)
object ClientModEvents {
    val openClassMenuKey: KeyMapping = KeyMapping(
        "key.better_spawns.open_menu",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_K,
        "key.categories.better_spawns"
    )
    @JvmStatic
    @SubscribeEvent
    fun registerKeys(event: RegisterKeyMappingsEvent) {
        event.register(openClassMenuKey)
    }
}

@Mod.EventBusSubscriber(modid = ClassSelectorMod.MOD_ID, value = [Dist.CLIENT])
object ClientForgeEvents {
    private const val INSTRUCTION_BACKGROUND = 0xB80A0D12.toInt()
    private const val INSTRUCTION_COLOR = 0xF4F0E6

    @JvmStatic
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val mc = Minecraft.getInstance()
        val player = mc.player

        if (player == null) {
            OnboardingPlayerVisibility.clear(mc)
            ClassSelectionState.reset()
            return
        }

        OnboardingPlayerVisibility.tick(mc)
        if (ClassSelectionState.noticeTicksRemaining > 0) {
            ClassSelectionState.noticeTicksRemaining--
            if (ClassSelectionState.noticeTicksRemaining == 0) ClassSelectionState.noticeText = null
        }

        if (ClassSelectionState.activeInCurrentWorld && ClassSelectionState.selectionRequired && ClientModEvents.openClassMenuKey.consumeClick()) {
            if (Screen.hasShiftDown()) {
                when (ClassSelectionState.selectionMode) {
                    SelectionMode.NONE -> ClientOnboardingActions.submitSpawnOnly(mc)
                    SelectionMode.CLASS -> ClassSelectionState.lockedClassId?.let(ClientOnboardingActions::submitClassSelection)
                    SelectionMode.EMBARK_POINTS -> ClientOnboardingActions.submitEmbarkSelection(ClassSelectionState.selectedEmbarkPurchases())
                    SelectionMode.PROGRESSION -> Unit
                }
            } else if (ClassSelectionState.selectionMode == SelectionMode.NONE) {
                ClassSelectionState.showNotice("Press Shift+K here to set your spawn and begin.")
            } else if (ClassSelectionState.hasSelectionOptions()) {
                openSelectionScreen(mc)
            } else {
                ClassSelectionState.showNotice("Starting options are still syncing.")
            }
        }

        if (ClassSelectionState.promptOpen && mc.screen == null) {
            ClassSelectionState.promptOpen = false
            if (ClassSelectionState.selectionMode != SelectionMode.NONE && ClassSelectionState.hasSelectionOptions()) {
                openSelectionScreen(mc)
            }
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onRenderGui(event: RenderGuiEvent.Post) {
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.player == null || mc.screen != null) return
        if (!ClassSelectionState.activeInCurrentWorld && ClassSelectionState.noticeText == null) return

        val key = ClientModEvents.openClassMenuKey.translatedKeyMessage
        val instructions = if (ClassSelectionState.activeInCurrentWorld && ClassSelectionState.selectionRequired) when (ClassSelectionState.selectionMode) {
            SelectionMode.NONE -> listOf(
                Component.translatable("message.better_spawns.spawn_step_1", key),
                Component.translatable("message.better_spawns.spawn_step_2", key)
            )
            SelectionMode.CLASS -> listOf(
                Component.translatable("message.better_spawns.class_step_1", key),
                Component.translatable("message.better_spawns.class_step_2", key),
                Component.translatable("message.better_spawns.class_step_3")
            )
            SelectionMode.EMBARK_POINTS -> listOf(
                Component.translatable("message.better_spawns.embark_step_1", key),
                Component.translatable("message.better_spawns.embark_step_2", key),
                Component.translatable("message.better_spawns.embark_step_3")
            )
            SelectionMode.PROGRESSION -> emptyList()
        } else emptyList()
        val notice = ClassSelectionState.noticeText?.let { Component.literal(it) }
        if (instructions.isEmpty() && notice == null) return
        val font = mc.font
        val maxWidth = (event.window.guiScaledWidth - 24).coerceIn(1, 320)
        val instructionLines = instructions.flatMap { font.split(it, maxWidth) }
        val noticeLines = notice?.let { font.split(it, maxWidth) }.orEmpty()
        val lines = instructionLines + noticeLines
        val boxWidth = lines.maxOfOrNull(font::width) ?: return
        val x = 8
        val y = 8
        event.guiGraphics.fill(x - 4, y - 4, x + boxWidth + 4, y + lines.size * 10 + 4, INSTRUCTION_BACKGROUND)
        lines.forEachIndexed { index, line ->
            val color = if (index < instructionLines.size) INSTRUCTION_COLOR else 0xD58A8A
            event.guiGraphics.drawString(font, line, x, y + index * 10, color)
        }
    }

    private fun openSelectionScreen(mc: Minecraft) {
        when (ClassSelectionState.selectionMode) {
            SelectionMode.NONE -> {}
            SelectionMode.CLASS -> mc.setScreen(ClassSelectionScreen(ClassSelectionState.kits))
            SelectionMode.EMBARK_POINTS -> mc.setScreen(
                EmbarkSelectionScreen(ClassSelectionState.embarkItems, ClassSelectionState.pointQuota)
            )
            SelectionMode.PROGRESSION -> {}
        }
    }
}
