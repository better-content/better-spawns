package com.bettercontent.classselector.client

import com.bettercontent.classselector.ClassSelectorMod
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = ClassSelectorMod.MOD_ID, value = [Dist.CLIENT])
object SpawnPreviewRenderer {
    private var previousPosition: Vec3? = null
    private var stillTicks = 0

    @JvmStatic
    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val player = Minecraft.getInstance().player
        if (player == null || !player.isSpectator || !ClassSelectionState.selectionRequired || !ClassSelectionState.activeInCurrentWorld) {
            previousPosition = null
            stillTicks = 0
            return
        }
        val position = player.position()
        stillTicks = if (previousPosition?.distanceToSqr(position)?.let { it < 0.0004 } == true) stillTicks + 1 else 0
        previousPosition = position
    }

    @JvmStatic
    @SubscribeEvent
    fun onRender(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES || stillTicks < 40) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (mc.screen != null || !player.isSpectator) return

        val x = player.blockX
        val z = player.blockZ
        var base: BlockPos? = null
        for (y in player.blockY.coerceAtMost(level.maxBuildHeight - 3) downTo level.minBuildHeight) {
            val candidate = BlockPos(x, y, z)
            if (level.getBlockState(candidate).isFaceSturdy(level, candidate, Direction.UP)) {
                base = candidate
                break
            }
        }
        val target = base ?: return
        val camera = event.camera.position
        val pose = event.poseStack
        pose.pushPose()
        pose.translate(-camera.x, -camera.y, -camera.z)

        val source = mc.renderBuffers().bufferSource()
        val fill = source.getBuffer(RenderType.debugFilledBox())
        LevelRenderer.addChainedFilledBoxVertices(
            pose, fill,
            target.x + 0.005, target.y + 0.005, target.z + 0.005,
            target.x + 0.995, target.y + 0.995, target.z + 0.995,
            0.72f, 0.08f, 1.0f, 0.48f
        )
        source.endBatch(RenderType.debugFilledBox())

        val lines = source.getBuffer(RenderType.lines())
        LevelRenderer.renderLineBox(pose, lines, target.x.toDouble(), target.y.toDouble(), target.z.toDouble(),
            target.x + 1.0, target.y + 1.0, target.z + 1.0, 1.0f, 0.28f, 1.0f, 1.0f)
        LevelRenderer.renderLineBox(pose, lines, target.x + 0.06, target.y + 0.06, target.z + 0.06,
            target.x + 0.94, target.y + 0.94, target.z + 0.94, 0.9f, 0.72f, 1.0f, 1.0f)

        val cx = target.x + 0.5
        val cz = target.z + 0.5
        val feet = target.y + 1.0
        // A bright body, head, arms, and legs outline shows exactly where the player will stand.
        LevelRenderer.renderLineBox(pose, lines, cx - 0.19, feet + 0.68, cz - 0.12,
            cx + 0.19, feet + 1.48, cz + 0.12, 0.96f, 0.7f, 1.0f, 1.0f)
        LevelRenderer.renderLineBox(pose, lines, cx - 0.25, feet + 1.48, cz - 0.25,
            cx + 0.25, feet + 1.95, cz + 0.25, 1.0f, 0.82f, 1.0f, 1.0f)
        LevelRenderer.renderLineBox(pose, lines, cx - 0.34, feet + 0.67, cz - 0.11,
            cx - 0.2, feet + 1.44, cz + 0.11, 0.88f, 0.42f, 1.0f, 1.0f)
        LevelRenderer.renderLineBox(pose, lines, cx + 0.2, feet + 0.67, cz - 0.11,
            cx + 0.34, feet + 1.44, cz + 0.11, 0.88f, 0.42f, 1.0f, 1.0f)
        LevelRenderer.renderLineBox(pose, lines, cx - 0.18, feet, cz - 0.11,
            cx - 0.02, feet + 0.68, cz + 0.11, 0.88f, 0.42f, 1.0f, 1.0f)
        LevelRenderer.renderLineBox(pose, lines, cx + 0.02, feet, cz - 0.11,
            cx + 0.18, feet + 0.68, cz + 0.11, 0.88f, 0.42f, 1.0f, 1.0f)
        pose.popPose()
        source.endBatch(RenderType.lines())
    }
}
