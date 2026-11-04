package com.bettercontent.betterspawns.client

import com.bettercontent.betterspawns.ClassSelectorMod
import com.bettercontent.betterspawns.embark.SelectionMode
import com.bettercontent.gameplaynotices.GameplayNotice
import com.bettercontent.gameplaynotices.NoticeReminder
import com.bettercontent.gameplaynotices.NoticeTheme
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
    private val guidance = NoticeReminder()

    @JvmStatic
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val mc = Minecraft.getInstance()
        val player = mc.player

        if (player == null) {
            OnboardingPlayerVisibility.clear(mc)
            ClassSelectionState.reset()
            guidance.clear()
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

        val mode = ClassSelectionState.selectionMode
        if (!ClassSelectionState.activeInCurrentWorld || !ClassSelectionState.selectionRequired ||
            mode == SelectionMode.PROGRESSION) {
            guidance.clear()
            return
        }
        val key = ClientModEvents.openClassMenuKey.translatedKeyMessage
        val (title, hint) = when (mode) {
            SelectionMode.NONE -> Component.translatable("message.better_spawns.guidance_spawn_title") to
                Component.translatable("message.better_spawns.guidance_spawn_hint", key)
            SelectionMode.CLASS -> Component.translatable("message.better_spawns.guidance_class_title") to
                Component.translatable("message.better_spawns.guidance_class_hint", key, key)
            SelectionMode.EMBARK_POINTS -> Component.translatable("message.better_spawns.guidance_embark_title") to
                Component.translatable("message.better_spawns.guidance_embark_hint", key, key)
            SelectionMode.PROGRESSION -> return
        }
        val noticeKey = "guidance:spawn:${mode.wireName}"
        guidance.tick(noticeKey, GameplayNotice(noticeKey, NoticeTheme.SPAWN, title, hint),
            { mc.player != null && ClassSelectionState.activeInCurrentWorld &&
                ClassSelectionState.selectionRequired && ClassSelectionState.selectionMode == mode },
            mc.screen == null && !mc.options.hideGui)
    }

    @JvmStatic
    @SubscribeEvent
    fun onRenderGui(event: RenderGuiEvent.Post) {
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.player == null || mc.screen != null) return
        val notice = ClassSelectionState.noticeText?.let { Component.literal(it) } ?: return
        val font = mc.font
        val maxWidth = (event.window.guiScaledWidth - 24).coerceIn(1, 320)
        val lines = font.split(notice, maxWidth)
        val boxWidth = lines.maxOfOrNull(font::width) ?: return
        val x = 8
        val y = 8
        event.guiGraphics.fill(x - 4, y - 4, x + boxWidth + 4, y + lines.size * 10 + 4, INSTRUCTION_BACKGROUND)
        lines.forEachIndexed { index, line ->
            event.guiGraphics.drawString(font, line, x, y + index * 10, 0xD58A8A)
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
