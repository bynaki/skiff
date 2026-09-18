package com.naki.skiff.data.store

import kotlinx.serialization.Serializable

/** Layout choices the user made and expects to find again next launch. */
@Serializable
data class Settings(
    val splitEnabled: Boolean = false,
    val splitDirection: String = "HORIZONTAL",
    val splitRatio: Float = 0.5f,
    val showHidden: Boolean = false,
    val sortBy: String = "NAME",
    val sortAscending: Boolean = true,
)

@Serializable
data class SkiffData(
    val profiles: List<ServerProfile> = emptyList(),
    val knownHosts: List<KnownHost> = emptyList(),
    val settings: Settings = Settings(),
)
