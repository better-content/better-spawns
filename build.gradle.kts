import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val curiosApiNotation = "top.theillusivec4.curios:curios-forge:${property("curios_version")}:api"

plugins {
    idea
    `maven-publish`
    jacoco
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("net.minecraftforge.gradle") version "6.0.54"
    id("org.parchmentmc.librarian.forgegradle") version "1.2.0"
    id("org.spongepowered.mixin") version "0.7.38"
}

mixin {
    add(sourceSets.main.get(), "better_spawns.refmap.json")
    config("better_spawns.mixins.json")
}

group = "com.bettercontent"
version = property("mod_version") as String

base {
    archivesName.set(property("artifact_name") as String)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

minecraft {
    mappings("parchment", property("parchment_version") as String)
    copyIdeResources = true

    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.console.level", "debug")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")
            mods {
                create(property("mod_id") as String) {
                    source(sourceSets.main.get())
                }
            }
        }

        create("client") {
            property("forge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        create("server") {
            property("forge.enabledGameTestNamespaces", property("mod_id") as String)
            arg("--nogui")
        }
        create("gameTestServer") {
            property("forge.enabledGameTestNamespaces", property("mod_id") as String)
        }
    }
}

// CI and fresh-release builds provide verified runtime JARs explicitly.
// Ordinary local builds retain the canonical sibling build/libs convention.
fun betterContentJar(repository: String, artifact: String): java.io.File {
    val directory = providers.environmentVariable("BC_CUSTOM_MOD_JAR_DIR").orNull
    require(directory == null || directory.isNotBlank()) { "BC_CUSTOM_MOD_JAR_DIR must not be blank" }
    val jar = if (directory == null) file("../$repository/build/libs/$artifact") else file(directory).resolve(artifact)
    require(jar.isFile) {
        "Missing Better Content provider $artifact at $jar; prepare BC_CUSTOM_MOD_JAR_DIR or build $repository first"
    }
    return jar
}

repositories {
    maven("https://maven.minecraftforge.net")
    maven("https://maven.theillusivec4.top/")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    maven("https://maven.createmod.net")
    maven("https://maven.ithundxr.dev/mirror")
    maven("https://maven.tterrag.com/")
    mavenCentral()
}

fun deobf(notation: String): Any =
    requireNotNull(extensions.getByName("fg").withGroovyBuilder { "deobf"(notation) })

dependencies {
    minecraft("net.minecraftforge:forge:${property("minecraft_version")}-${property("forge_version")}")
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    implementation("thedarkcolour:kotlinforforge:${property("kotlinforforge_version")}")
    compileOnly(fg.deobf(curiosApiNotation))
    runtimeOnly(fg.deobf("top.theillusivec4.curios:curios-forge:${property("curios_version")}"))
    compileOnly(files(betterContentJar("better-world-management", "better-world-management-0.1.0.jar")))
    runtimeOnly(deobf("com.simibubi.create:create-${property("minecraft_version")}:${property("create_version")}:slim"))
    runtimeOnly(deobf("net.createmod.ponder:Ponder-Forge-${property("minecraft_version")}:${property("ponder_version")}"))
    runtimeOnly(deobf("dev.engine-room.flywheel:flywheel-forge-${property("minecraft_version")}:${property("flywheel_version")}"))
    runtimeOnly(deobf("com.tterrag.registrate:Registrate:${property("registrate_version")}"))
    runtimeOnly(deobf("io.github.llamalad7:mixinextras-forge:0.3.6"))

    testImplementation(kotlin("test"))
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.named<Jar>("jar") {
    finalizedBy("reobfJar")
}

val stageRuntimeJar by tasks.registering(Copy::class) {
    group = "build"
    description = "Stages the reobfuscated runtime jar into build/libs using the canonical release filename."
    dependsOn(tasks.named("reobfJar"))
    from(layout.buildDirectory.file("reobfJar/output.jar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "${base.archivesName.get()}-$version.jar" }
}

tasks.named("assemble") {
    dependsOn(stageRuntimeJar)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy("jacocoTestReport")
}

jacoco {
    toolVersion = "0.8.12"
}

val jacocoIncludedClasses = listOf(
    "**/kit/ClassKit.class",
    "**/kit/KitItem.class",
    "**/kit/KitSlot.class",
    "**/kit/KitSlotTarget*",
    "**/client/ClassSelectionState*.class"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    val mainClasses = fileTree(layout.buildDirectory.dir("classes/kotlin/main").get().asFile) {
        include(jacocoIncludedClasses)
    }
    classDirectories.setFrom(mainClasses)
}

tasks.register("headlessGameTest") {
    group = "verification"
    description = "Runs Forge game tests in a headless dedicated server."
    dependsOn(tasks.named("runGameTestServer"))
}

val syncGameTestStructures by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("gameteststructures"))
    into(layout.projectDirectory.dir("run/gameteststructures"))
}

tasks.matching { it.name == "prepareRunGameTestServer" }.configureEach {
    dependsOn(syncGameTestStructures)
}

tasks.processResources {
    val props = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "forge_version" to project.property("forge_version"),
        "kotlinforforge_version" to project.property("kotlinforforge_version"),
        "curios_version" to project.property("curios_version"),
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
        "mod_version" to project.property("mod_version")
    )

    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(props)
    }
}

tasks.register("verifyFast") {
    group = "verification"
    description = "Runs the fast deterministic verification lane."
    dependsOn(tasks.named("check"))
}

tasks.register("verifyFull") {
    group = "verification"
    description = "Runs the full verification lane, including headless Forge GameTests."
    dependsOn(tasks.named("verifyFast"))
    dependsOn(tasks.named("headlessGameTest"))
}
