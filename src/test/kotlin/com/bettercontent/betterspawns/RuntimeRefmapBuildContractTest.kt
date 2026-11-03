package com.bettercontent.betterspawns

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeRefmapBuildContractTest {
    @Test
    fun generatedAnnotationProcessorSidecarsAreTrackedAndPackaged() {
        val build = Files.readString(Path.of("build.gradle.kts"))
        assertTrue(build.contains("outputs.file(generatedRefmap)"))
        assertTrue(build.contains("tmp/compileJava/compileJava-mappings.tsrg"))
        assertTrue(build.contains("options.isIncremental = false"))
        assertTrue(build.contains("from(generatedRefmap) { rename { \"better_spawns.refmap.json\" } }"))
    }

    @Test
    fun validationReadsFinalProductionJarAndPrecedesStagingWithoutCycle() {
        val build = Files.readString(Path.of("build.gradle.kts"))
        val gate = build.substringAfter("val verifyRuntimeRefmap by").substringBefore("val stageRuntimeJar by")
        val stage = build.substringAfter("val stageRuntimeJar by").substringBefore("tasks.named(\"assemble\")")
        assertTrue(gate.contains("dependsOn(tasks.named(\"reobfJar\"))"))
        assertTrue(gate.contains("reobfJar/output.jar"))
        assertTrue(gate.contains("zip.getEntry(\"better_spawns.refmap.json\")"))
        assertTrue(gate.contains("data[\"searge\"]"))
        assertTrue(gate.contains("ServerLevel;m_8809_()V"))
        assertTrue(gate.contains("ServerLevel;m_8615_(J)V"))
        assertFalse(gate.contains("dependsOn(stageRuntimeJar)"))
        assertTrue(stage.contains("dependsOn(verifyRuntimeRefmap)"))
    }
}
