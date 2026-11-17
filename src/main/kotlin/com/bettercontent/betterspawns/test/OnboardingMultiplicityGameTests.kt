package com.bettercontent.betterspawns.test

import com.bettercontent.betterspawns.ClassSelectorMod
import com.bettercontent.betterspawns.integration.OnboardingIntegration
import com.bettercontent.betterspawns.respawn.PersonalRespawnPoint
import com.bettercontent.betterspawns.respawn.PersonalRespawnService
import com.mojang.authlib.GameProfile
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import java.util.UUID

/** Isolated native persistence/clone fixture, NOT a substitute for concurrently connected clients. */
@GameTestHolder(ClassSelectorMod.MOD_ID)
object OnboardingMultiplicityGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun individualOnboardingSurvivesThirtyTwoPlayerClones(helper: GameTestHelper) {
        val originals = (0 until 32).map { index ->
            ServerPlayer(helper.level.server, helper.level, GameProfile(UUID.randomUUID(), "spawn-$index"))
        }
        val points = originals.indices.map { index ->
            val feet = BlockPos(index % 8, 1, index / 8)
            helper.setBlock(feet.below(), Blocks.STONE)
            helper.setBlock(feet, Blocks.AIR)
            helper.setBlock(feet.above(), Blocks.AIR)
            val absolute = helper.absolutePos(feet)
            PersonalRespawnPoint(helper.level.dimension().location().toString(), absolute.x, absolute.y, absolute.z)
        }
        originals.forEachIndexed { index, player ->
            val point = points[index]
            val prepared = PersonalRespawnService.setRespawnPoint(player, point)
            helper.assertTrue(prepared.point == point && prepared.sitePrepared, "Site $index was not prepared")
            val id = OnboardingIntegration.buildSpawnId(point)
            helper.assertTrue(OnboardingIntegration.finalizeOnboarding(player, "spawn_only", id), "First finalization must succeed for $index")
            helper.assertFalse(OnboardingIntegration.finalizeOnboarding(player, "replacement", "wrong"), "Repeated finalization must fail for $index")
        }
        for (count in listOf(8, 16, 32)) {
            val clones = originals.take(count).map { source ->
                ServerPlayer(helper.level.server, helper.level, GameProfile(source.uuid, source.gameProfile.name)).also { target ->
                    PersonalRespawnService.copyRespawnPoint(source, target)
                    OnboardingIntegration.copyPersistentState(source, target)
                }
            }
            clones.forEachIndexed { index, clone ->
                helper.assertTrue(PersonalRespawnService.getRespawnPoint(clone) == points[index], "$count-player clone $index lost its own spawn")
                helper.assertTrue(OnboardingIntegration.hasCompletedOnboarding(clone), "$count-player clone $index lost onboarding")
                helper.assertTrue(OnboardingIntegration.getStartingKit(clone) == "spawn_only", "$count-player clone $index lost kit identity")
                helper.assertTrue(OnboardingIntegration.getStartingSite(clone) == OnboardingIntegration.buildSpawnId(points[index]),
                    "$count-player clone $index inherited another player's site")
            }
        }
        helper.succeed()
    }
}
