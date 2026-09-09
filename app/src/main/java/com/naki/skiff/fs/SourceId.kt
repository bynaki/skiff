package com.naki.skiff.fs

import kotlinx.serialization.Serializable

/** Identifies which filesystem a pane is showing. */
@Serializable
sealed interface SourceId {
    @Serializable
    data object Local : SourceId

    @Serializable
    data class Remote(val profileId: String) : SourceId
}
