package com.bettercontent.betterspawns.embark

import com.bettercontent.betterspawns.ClassSelectorMod
import com.bettercontent.betterspawns.kit.KitItemStackFactory
import com.bettercontent.betterspawns.kit.KitSlot
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import net.minecraftforge.fml.loading.FMLPaths
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

object EmbarkConfigRepository {
    private const val DEFAULT_TEMPLATE_RESOURCE: String = "/data/better_spawns/embark/embark.json"

    private val gson = Gson()

    @Volatile
    private var cached: EmbarkSettings? = null

    fun parse(json: String): EmbarkSettings = try {
        normalize(gson.fromJson(json, EmbarkConfigFile::class.java) ?: EmbarkConfigFile())
    } catch (exception: JsonSyntaxException) {
        throw IllegalArgumentException("Invalid JSON syntax at ${embarkPath()}: ${exception.message}", exception)
    }

    fun load(): EmbarkSettings {
        val configPath = embarkPath()
        ensureConfigExists(configPath)
        val parsed = Files.newBufferedReader(configPath, StandardCharsets.UTF_8).use { parse(it.readText()) }
        val validated = validate(parsed)
        cached = validated
        return validated
    }

    fun get(): EmbarkSettings? = cached

    internal fun validate(settings: EmbarkSettings): EmbarkSettings {
        require(settings.pointQuota >= 0) { "Embark pointQuota cannot be negative at ${embarkPath()}" }

        when (settings.mode) {
            SelectionMode.NONE -> Unit
            SelectionMode.CLASS -> Unit

            SelectionMode.EMBARK_POINTS,
            SelectionMode.PROGRESSION -> {
                require(settings.pointQuota > 0) { "Embark pointQuota must be positive at ${embarkPath()}" }
                require(settings.items.isNotEmpty()) { "Embark mode requires at least one item in ${embarkPath()}" }
            }
        }

        val ids = settings.items.map { it.id }
        require(ids.size == ids.toSet().size) { "Duplicate embark item ids found: $ids" }

        settings.items.forEachIndexed { index, item ->
            require(item.id.isNotBlank()) { "Embark item #${index + 1} must have a non-blank id" }
            require(item.title.isNotBlank()) { "Embark item '${item.id}' must have a non-blank title" }
            require(runCatching { KitItemStackFactory.parse(item.item) }.isSuccess) {
                "Embark item '${item.id}' has an invalid item spec '${item.item}'"
            }
            require(item.count > 0) { "Embark item '${item.id}' must have a positive count" }
            require(item.cost > 0) { "Embark item '${item.id}' must have a positive cost" }
            require(item.maxPurchases > 0) { "Embark item '${item.id}' must have a positive maxPurchases" }
            require(KitSlot.isSupported(item.slot)) {
                "Embark item '${item.id}' uses unsupported slot '${item.slot}'"
            }
        }

        return settings
    }

    private fun normalize(file: EmbarkConfigFile): EmbarkSettings {
        val mode = SelectionMode.parse(file.mode)
        val pointQuota = file.pointQuota ?: 12
        val items = file.items.orEmpty().mapIndexed { index, item ->
            val id = item.id?.trim().orEmpty()
            EmbarkPoolItem(
                id = id,
                title = item.title?.trim().orEmpty(),
                category = item.category?.trim()?.ifBlank { null },
                blurb = item.blurb?.trim().orEmpty(),
                item = item.item?.trim().orEmpty(),
                count = item.count ?: 1,
                cost = item.cost ?: 1,
                maxPurchases = item.maxPurchases ?: 1,
                slot = item.slot?.trim()?.ifBlank { null }
            ).also {
                require(it.id.isNotBlank()) { "Embark item #${index + 1} must have a non-blank id" }
            }
        }

        return EmbarkSettings(mode = mode, pointQuota = pointQuota, items = items)
    }

    private fun ensureConfigExists(configPath: Path) {
        if (Files.exists(configPath)) return
        Files.createDirectories(configPath.parent)
        val defaultTemplate = EmbarkConfigRepository::class.java.getResourceAsStream(DEFAULT_TEMPLATE_RESOURCE)
            ?: throw IllegalStateException("Missing bundled default embark config at $DEFAULT_TEMPLATE_RESOURCE")
        defaultTemplate.use { input ->
            Files.newOutputStream(configPath, StandardOpenOption.CREATE_NEW).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun embarkPath(): Path {
        val configRoot = FMLPaths.CONFIGDIR.get() ?: Paths.get("config")
        return configRoot.resolve(ClassSelectorMod.MOD_ID).resolve("embark.json")
    }

    private data class EmbarkConfigFile(
        val mode: String? = null,
        val pointQuota: Int? = null,
        val items: List<EmbarkPoolItemFile>? = null
    )

    private data class EmbarkPoolItemFile(
        val id: String? = null,
        val title: String? = null,
        val category: String? = null,
        val blurb: String? = null,
        val item: String? = null,
        val count: Int? = null,
        val cost: Int? = null,
        val maxPurchases: Int? = null,
        val slot: String? = null
    )
}
