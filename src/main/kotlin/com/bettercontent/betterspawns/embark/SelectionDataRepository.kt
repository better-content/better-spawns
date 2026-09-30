package com.bettercontent.betterspawns.embark

import com.bettercontent.betterspawns.kit.ClassKit
import com.bettercontent.betterspawns.kit.ClassKitRepository
import com.bettercontent.betterworldmanagement.PrestigePerks
import net.minecraft.server.MinecraftServer
import net.minecraftforge.fml.ModList

data class SelectionData(
    val mode: SelectionMode,
    val kits: List<ClassKit>,
    val embarkSettings: EmbarkSettings,
    val starterSchematicannon: Boolean = false
)

internal data class ProgressionPolicy(
    val mode: SelectionMode,
    val unlockedClassIds: Set<String>,
    val embarkBudget: Int,
    val starterSchematicannon: Boolean
)

internal object WorldLifecyclePolicy {
    fun resolve(server: MinecraftServer): ProgressionPolicy {
        require(ModList.get().isLoaded("better_world_management")) {
            "Better Spawns progression mode requires better_world_management"
        }
        val policy = PrestigePerks.activeOnboardingPolicy(server)
        val mode = when (policy.mode()) {
            PrestigePerks.OnboardingMode.SPAWN_ONLY -> SelectionMode.NONE
            PrestigePerks.OnboardingMode.CLASS -> SelectionMode.CLASS
            PrestigePerks.OnboardingMode.EMBARK -> SelectionMode.EMBARK_POINTS
        }
        return ProgressionPolicy(
            mode,
            policy.unlockedClassIds(),
            policy.embarkBudget(),
            policy.starterSchematicannon()
        )
    }
}

object SelectionDataRepository {
    private val canonicalClassIds = setOf(
        "wayfinder", "field_cook", "rail_scout", "flood_runner", "market_runner", "trail_wrangler"
    )

    @Volatile
    private var cached: SelectionData? = null

    fun load(server: MinecraftServer): SelectionData {
        val configured = EmbarkConfigRepository.load()
        if (configured.mode != SelectionMode.PROGRESSION) {
            val kits = when (configured.mode) {
                SelectionMode.CLASS -> ClassKitRepository.load()
                SelectionMode.NONE, SelectionMode.EMBARK_POINTS -> ClassKitRepository.get()
                SelectionMode.PROGRESSION -> error("unreachable")
            }
            return SelectionData(configured.mode, kits, configured).also { cached = it }
        }

        val allKits = ClassKitRepository.load()
        return resolveProgression(configured, allKits, WorldLifecyclePolicy.resolve(server)).also { cached = it }
    }

    internal fun resolveProgression(
        configured: EmbarkSettings,
        allKits: List<ClassKit>,
        policy: ProgressionPolicy
    ): SelectionData {
        require(allKits.map { it.id }.toSet() == canonicalClassIds) {
            "Progression mode requires exactly the canonical class kits $canonicalClassIds"
        }
        val missingEmbarkSpecs = allKits.flatMap { it.items }.map { it.item }.toSet() - configured.items.map { it.item }.toSet()
        require(missingEmbarkSpecs.isEmpty()) {
            "Embark progression catalog is missing class item specs: ${missingEmbarkSpecs.sorted()}"
        }

        require(policy.unlockedClassIds.all(canonicalClassIds::contains)) {
            "World Lifecycle Manager unlocked unknown classes: ${policy.unlockedClassIds - canonicalClassIds}"
        }
        require(policy.embarkBudget in setOf(0, 6, 9, 12, 15, 18)) {
            "World Lifecycle Manager returned invalid Embark budget ${policy.embarkBudget}"
        }
        when (policy.mode) {
            SelectionMode.NONE -> {
                require(policy.unlockedClassIds.isEmpty()) {
                    "Spawn-only onboarding cannot expose unlocked classes"
                }
                require(policy.embarkBudget == 0) {
                    "Spawn-only onboarding cannot expose an Embark budget"
                }
                require(!policy.starterSchematicannon) {
                    "Spawn-only onboarding cannot grant the Schematicannon capstone"
                }
            }
            SelectionMode.CLASS -> {
                require(policy.unlockedClassIds.size in 1 until canonicalClassIds.size) {
                    "Class onboarding requires between one and five unlocked classes"
                }
                require(policy.embarkBudget == 0) {
                    "Class onboarding cannot expose an Embark budget"
                }
                require(!policy.starterSchematicannon) {
                    "Class onboarding cannot grant the Schematicannon capstone"
                }
            }
            SelectionMode.EMBARK_POINTS -> {
                require(policy.unlockedClassIds == canonicalClassIds) {
                    "Embark onboarding requires all canonical classes to be unlocked"
                }
                require(policy.embarkBudget in setOf(6, 9, 12, 15, 18)) {
                    "Embark onboarding requires an active budget tier"
                }
                require(!policy.starterSchematicannon || policy.embarkBudget == 18) {
                    "The Schematicannon capstone requires the maximum Embark budget"
                }
            }
            SelectionMode.PROGRESSION -> error("World Lifecycle Manager cannot return progression as an onboarding mode")
        }
        val effectiveKits = if (policy.mode == SelectionMode.CLASS) {
            allKits.filter { it.id in policy.unlockedClassIds }
        } else emptyList()
        val effectiveEmbark = configured.copy(
            mode = policy.mode,
            pointQuota = if (policy.mode == SelectionMode.EMBARK_POINTS) policy.embarkBudget else configured.pointQuota
        )
        return SelectionData(policy.mode, effectiveKits, effectiveEmbark, policy.starterSchematicannon)
    }

    fun getOrLoad(): SelectionData = cached ?: error("Better Spawns selection data was not loaded during server startup")
}
