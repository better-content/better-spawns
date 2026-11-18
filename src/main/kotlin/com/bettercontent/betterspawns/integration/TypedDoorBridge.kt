package com.bettercontent.betterspawns.integration

import com.bettercontent.betterdeathsdoor.api.ScoutBridgeContract

/** Isolated common-side reference to the optional, versioned Door contract. */
internal object TypedDoorBridge {
    fun compatibleVersion(version: Int): Boolean = version == 1
    fun compatible(): Boolean = compatibleVersion(ScoutBridgeContract.version())
}
