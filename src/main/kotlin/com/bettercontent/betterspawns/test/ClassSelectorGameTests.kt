package com.bettercontent.betterspawns.test

import com.bettercontent.betterspawns.ClassSelectorMod
import com.bettercontent.betterspawns.integration.OnboardingIntegration
import com.bettercontent.betterspawns.integration.PlayerStartFinalizedEvent
import com.bettercontent.betterspawns.kit.ClassKitRepository
import com.bettercontent.betterspawns.kit.KitItemStackFactory
import com.bettercontent.betterspawns.kit.KitApplicator
import com.bettercontent.betterspawns.kit.KitSlot
import com.bettercontent.betterspawns.respawn.PersonalRespawnPoint
import com.bettercontent.betterspawns.respawn.PersonalRespawnService
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.registries.ForgeRegistries
import java.util.UUID

@GameTestHolder(ClassSelectorMod.MOD_ID)
object ClassSelectorGameTests {
    private fun testPlayer(helper: GameTestHelper): ServerPlayer = ServerPlayer(
        helper.level.server,
        helper.level,
        GameProfile(UUID.randomUUID(), "better-spawns-test")
    )

    private class FinalizationProbe {
        var count: Int = 0
        var completedAtEvent: Boolean = false
        var kitIdAtEvent: String? = null
        var spawnIdAtEvent: String? = null

        @SubscribeEvent
        fun onFinalized(event: PlayerStartFinalizedEvent) {
            val player = event.entity as ServerPlayer
            count++
            completedAtEvent = OnboardingIntegration.hasCompletedOnboarding(player)
            kitIdAtEvent = OnboardingIntegration.getStartingKit(player)
            spawnIdAtEvent = OnboardingIntegration.getStartingSite(player)
        }
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun kitsShouldLoad(helper: GameTestHelper) {
        val kits = ClassKitRepository.load()
        val expectedIds = setOf(
            "wayfinder",
            "field_cook",
            "rail_scout",
            "flood_runner",
            "market_runner",
            "trail_wrangler"
        )
        val actualIds = kits.map { it.id }.toSet()
        if (actualIds != expectedIds) {
            throw GameTestAssertException("Expected ids $expectedIds, found $actualIds")
        }

        val wayfinder = kits.firstOrNull { it.id == "wayfinder" }
            ?: throw GameTestAssertException("Expected wayfinder kit to exist")
        if (wayfinder.items.firstOrNull()?.slot != "weapon.offhand") {
            throw GameTestAssertException("Expected wayfinder compass to target weapon.offhand")
        }

        kits.forEach { kit ->
            kit.items.forEachIndexed { index, item ->
                if (!KitSlot.isSupported(item.slot)) {
                    throw GameTestAssertException("Unsupported slot '${item.slot}' in kit '${kit.id}' item #${index + 1}")
                }
                runCatching { KitItemStackFactory.parse(item.item) }.getOrElse { error ->
                    throw GameTestAssertException("Invalid item spec in kit '${kit.id}' item #${index + 1}: ${error.message}")
                }
            }
        }

        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun schematicannonCapstoneGrantsRegisteredCreateItem(helper: GameTestHelper) {
        val player = testPlayer(helper)
        val schematicannon = ForgeRegistries.ITEMS.getValue(ResourceLocation("create", "schematicannon"))
            ?: throw GameTestAssertException("Create Schematicannon is not registered")
        KitApplicator.giveStarterSchematicannon(player)
        helper.assertTrue(
            player.inventory.items.any { it.`is`(schematicannon) },
            "Expected the capstone grant to place a registered Create Schematicannon in player inventory"
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun flyingRespawnSelectionUsesFirstSolidBlockStraightBelow(helper: GameTestHelper) {
        val requestedFeetPos = BlockPos(1, 6, 1)
        val targetFeetPos = BlockPos(1, 2, 1)
        val sideFeetPos = BlockPos(2, 5, 1)
        for (y in 2..7) helper.setBlock(BlockPos(1, y, 1), Blocks.AIR)
        helper.setBlock(targetFeetPos.below(), Blocks.STONE)
        helper.setBlock(sideFeetPos.below(), Blocks.STONE)
        helper.setBlock(sideFeetPos, Blocks.AIR)
        helper.setBlock(sideFeetPos.above(), Blocks.AIR)

        val requestedFeetAbs = helper.absolutePos(requestedFeetPos)
        val targetFeetAbs = helper.absolutePos(targetFeetPos)

        val prepared = PersonalRespawnService.prepareRespawnPoint(
            helper.level.server,
            PersonalRespawnPoint(
                dim = helper.level.dimension().location().toString(),
                x = requestedFeetAbs.x,
                y = requestedFeetAbs.y,
                z = requestedFeetAbs.z
            )
        )

        helper.assertTrue(prepared.locationAdjusted, "Expected flying selection to descend to terrain")
        helper.assertTrue(prepared.sitePrepared, "Expected the terrain block to be replaced with crying obsidian")
        helper.assertTrue(
            prepared.point == PersonalRespawnPoint(
                dim = helper.level.dimension().location().toString(),
                x = targetFeetAbs.x,
                y = targetFeetAbs.y,
                z = targetFeetAbs.z
            ),
            "Expected respawn point directly below the player, found ${prepared.point}"
        )
        helper.assertBlockPresent(Blocks.CRYING_OBSIDIAN, targetFeetPos.below())
        helper.assertBlockPresent(Blocks.AIR, targetFeetPos)
        helper.assertBlockPresent(Blocks.AIR, targetFeetPos.above())
        helper.assertBlockPresent(Blocks.STONE, sideFeetPos.below())

        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun respawnSelectionUsesFirstSolidBlockInColumn(helper: GameTestHelper) {
        val requestedFeetPos = BlockPos(3, 7, 3)
        val firstFeetPos = BlockPos(3, 5, 3)
        val lowerFeetPos = BlockPos(3, 2, 3)

        for (x in 0..7) {
            for (y in 0..7) {
                for (z in 0..7) {
                    helper.setBlock(BlockPos(x, y, z), Blocks.AIR)
                }
            }
        }

        helper.setBlock(firstFeetPos.below(), Blocks.STONE)
        helper.setBlock(lowerFeetPos.below(), Blocks.STONE)

        val requestedFeetAbs = helper.absolutePos(requestedFeetPos)
        val firstFeetAbs = helper.absolutePos(firstFeetPos)

        val prepared = PersonalRespawnService.prepareRespawnPoint(
            helper.level.server,
            PersonalRespawnPoint(
                dim = helper.level.dimension().location().toString(),
                x = requestedFeetAbs.x,
                y = requestedFeetAbs.y,
                z = requestedFeetAbs.z
            )
        )

        helper.assertTrue(
            prepared.point == PersonalRespawnPoint(
                dim = helper.level.dimension().location().toString(),
                x = firstFeetAbs.x,
                y = firstFeetAbs.y,
                z = firstFeetAbs.z
            ),
            "Expected first solid block in the player's column, found ${prepared.point}"
        )
        helper.assertBlockPresent(Blocks.CRYING_OBSIDIAN, firstFeetPos.below())
        helper.assertBlockPresent(Blocks.STONE, lowerFeetPos.below())

        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun alreadyPreparedValidRespawnSiteIsLeftInPlace(helper: GameTestHelper) {
        val requestedFeetPos = BlockPos(1, 1, 1)

        helper.setBlock(requestedFeetPos.below(), Blocks.CRYING_OBSIDIAN)
        helper.setBlock(requestedFeetPos, Blocks.AIR)
        helper.setBlock(requestedFeetPos.above(), Blocks.AIR)

        val requestedFeetAbs = helper.absolutePos(requestedFeetPos)
        val prepared = PersonalRespawnService.prepareRespawnPoint(
            helper.level.server,
            PersonalRespawnPoint(
                dim = helper.level.dimension().location().toString(),
                x = requestedFeetAbs.x,
                y = requestedFeetAbs.y,
                z = requestedFeetAbs.z
            )
        )

        helper.assertTrue(!prepared.locationAdjusted, "Expected a valid requested site to stay selected")
        helper.assertTrue(!prepared.sitePrepared, "Expected already prepared site to avoid extra block writes")
        helper.assertTrue(
            prepared.point == PersonalRespawnPoint(
                dim = helper.level.dimension().location().toString(),
                x = requestedFeetAbs.x,
                y = requestedFeetAbs.y,
                z = requestedFeetAbs.z
            ),
            "Expected prepared point to equal requested point, found ${prepared.point}"
        )
        helper.assertBlockPresent(Blocks.CRYING_OBSIDIAN, requestedFeetPos.below())
        helper.assertBlockPresent(Blocks.AIR, requestedFeetPos)
        helper.assertBlockPresent(Blocks.AIR, requestedFeetPos.above())

        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun finalizationPublishesCommittedPlayerStartOnce(helper: GameTestHelper) {
        val player = testPlayer(helper)
        val spawnId = "${helper.level.dimension().location()}@10,64,-5"
        val probe = FinalizationProbe()
        MinecraftForge.EVENT_BUS.register(probe)

        try {
            helper.assertTrue(
                OnboardingIntegration.finalizeOnboarding(player, "spawn_only", spawnId),
                "Expected the first finalization attempt to commit"
            )
            helper.assertFalse(
                OnboardingIntegration.finalizeOnboarding(player, "replacement", "minecraft:overworld@0,0,0"),
                "Expected repeated finalization to be ignored"
            )
            helper.assertTrue(probe.count == 1, "Expected exactly one PlayerStartFinalizedEvent, found ${probe.count}")
            helper.assertTrue(probe.completedAtEvent, "Expected onboarding to be committed before the event is published")
            helper.assertTrue(probe.kitIdAtEvent == "spawn_only", "Expected committed kit data to be visible to event listeners")
            helper.assertTrue(probe.spawnIdAtEvent == spawnId, "Expected committed spawn data to be visible to event listeners")
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe)
        }

        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun bedSpawnCannotReplacePermanentStartingSpawn(helper: GameTestHelper) {
        val player = testPlayer(helper)
        val lockedRelative = BlockPos(1, 1, 1)
        helper.setBlock(lockedRelative.below(), Blocks.CRYING_OBSIDIAN)
        helper.setBlock(lockedRelative, Blocks.AIR)
        helper.setBlock(lockedRelative.above(), Blocks.AIR)

        val lockedAbsolute = helper.absolutePos(lockedRelative)
        val lockedPoint = PersonalRespawnPoint(
            dim = helper.level.dimension().location().toString(),
            x = lockedAbsolute.x,
            y = lockedAbsolute.y,
            z = lockedAbsolute.z
        )
        PersonalRespawnService.setRespawnPoint(player, lockedPoint)
        OnboardingIntegration.finalizeOnboarding(player, "spawn_only", OnboardingIntegration.buildSpawnId(lockedPoint))

        val attemptedBedSpawn = helper.absolutePos(BlockPos(3, 1, 3))
        player.setRespawnPosition(helper.level.dimension(), attemptedBedSpawn, 0.0f, false, false)

        helper.assertTrue(
            PersonalRespawnService.getRespawnPoint(player) == lockedPoint,
            "Expected the permanent starting spawn to remain stored after using a bed"
        )
        helper.assertTrue(
            player.respawnPosition == lockedAbsolute,
            "Expected the vanilla respawn mirror to remain at the permanent starting spawn"
        )
        helper.assertTrue(player.isRespawnForced, "Expected the permanent starting spawn to remain forced")
        helper.succeed()
    }
}
