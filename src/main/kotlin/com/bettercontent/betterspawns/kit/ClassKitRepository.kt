package com.bettercontent.betterspawns.kit

import com.bettercontent.betterspawns.ClassSelectorMod
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import net.minecraftforge.fml.loading.FMLPaths
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.Paths

object ClassKitRepository {
    private const val DEFAULT_TEMPLATE_RESOURCE: String = "/data/better_spawns/class_kits/kits.json"

    private val gson = Gson()
    @Volatile
    private var cached: List<ClassKit> = emptyList()

    fun parse(json: String): List<ClassKit> = try {
        gson.fromJson(json, Array<ClassKit>::class.java)?.toList() ?: emptyList()
    } catch (exception: JsonSyntaxException) {
        throw IllegalArgumentException("Invalid JSON syntax at ${kitsPath()}: ${exception.message}", exception)
    }

    fun load(): List<ClassKit> {
        val configPath = kitsPath()
        ensureConfigExists(configPath)
        val parsed = Files.newBufferedReader(configPath, StandardCharsets.UTF_8).use { parse(it.readText()) }
        val validated = validate(parsed)
        cached = validated
        return validated
    }

    fun get(): List<ClassKit> = cached

    internal fun validate(kits: List<ClassKit>): List<ClassKit> {
        require(kits.isNotEmpty()) { "No class kits defined at ${kitsPath()}" }
        require(kits.all { it.id.isNotBlank() }) { "Every class kit must have a non-blank id" }
        val ids = kits.map { it.id }
        require(ids.size == ids.toSet().size) { "Duplicate class kit ids found: $ids" }

        kits.forEach { kit ->
            require(kit.title.isNotBlank()) { "Class kit '${kit.id}' must have a non-blank title" }
            require(kit.blurb.isNotBlank()) { "Class kit '${kit.id}' must have a non-blank blurb" }
            require(kit.description.isNotBlank()) { "Class kit '${kit.id}' must have a non-blank description" }
            require(kit.items.isNotEmpty()) { "Class kit '${kit.id}' must define at least one item" }

            kit.items.forEachIndexed { index, item ->
                require(runCatching { KitItemStackFactory.parse(item.item) }.isSuccess) {
                    "Class kit '${kit.id}' item #${index + 1} has an invalid item spec '${item.item}'"
                }
                require(item.count > 0) {
                    "Class kit '${kit.id}' item #${index + 1} must have a positive count"
                }
                require(KitSlot.isSupported(item.slot)) {
                    "Class kit '${kit.id}' item #${index + 1} uses unsupported slot '${item.slot}'"
                }
            }
        }

        return kits
    }

    private fun ensureConfigExists(configPath: Path) {
        if (Files.exists(configPath)) return
        Files.createDirectories(configPath.parent)
        val defaultTemplate = ClassKitRepository::class.java.getResourceAsStream(DEFAULT_TEMPLATE_RESOURCE)
            ?: throw IllegalStateException("Missing bundled default class kits at $DEFAULT_TEMPLATE_RESOURCE")
        defaultTemplate.use { input ->
            Files.newOutputStream(configPath, StandardOpenOption.CREATE_NEW).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun kitsPath(): Path {
        val configRoot = FMLPaths.CONFIGDIR.get() ?: Paths.get("config")
        return configRoot.resolve(ClassSelectorMod.MOD_ID).resolve("kits.json")
    }
}
