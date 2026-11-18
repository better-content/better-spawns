package com.bettercontent.betterspawns.integration

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Structural guard; native absent/old/current Door and dedicated server need runtime approval. */
class DoorBridgeAvailabilityTest {
    private val source = Path.of("src/main/kotlin/com/bettercontent/betterspawns")

    @Test fun commonAndClientBridgeRemainTypedAndSideIsolated() {
        val dispatcher = Files.readString(source.resolve("integration/DoorBridgeAvailability.kt"))
        val common = Files.readString(source.resolve("integration/TypedDoorBridge.kt"))
        val client = Files.readString(source.resolve("client/TypedDoorClientBridge.kt"))
        val events = Files.readString(source.resolve("client/ClientEvents.kt"))
        assertTrue(dispatcher.contains("ModList.get().isLoaded(\"better_deaths_door\")"))
        assertTrue(dispatcher.contains("TypedDoorBridge.compatible()"))
        assertTrue(common.contains("compatibleVersion(ScoutBridgeContract.version())"))
        assertFalse(common.contains("betterdeathsdoor.client"))
        assertTrue(client.contains("ScoutDeathClient.register()"))
        assertTrue(client.contains("ScoutDeathClient.disconnected()"))
        assertTrue(events.contains("DoorBridgeAvailability.registerClient()"))
        assertFalse(events.contains("ScoutDeathClient.register()"))
        assertFalse(dispatcher.contains("Class.forName("))
        assertFalse(dispatcher.contains("getDeclaredMethod("))
        assertFalse(client.contains("Class.forName("))
    }

    @Test fun onlyThePinnedContractVersionIsAccepted() {
        assertFalse(TypedDoorBridge.compatibleVersion(0))
        assertTrue(TypedDoorBridge.compatibleVersion(1))
        assertFalse(TypedDoorBridge.compatibleVersion(2))
    }
}
