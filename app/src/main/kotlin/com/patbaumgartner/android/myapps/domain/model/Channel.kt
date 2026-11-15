package com.patbaumgartner.android.myapps.domain.model

/**
 * Per-app update channel. Stable is the default; Snapshot is opt-in per app.
 */
enum class Channel {
    STABLE,
    SNAPSHOT,
    ;

    val label: String
        get() = when (this) {
            STABLE -> "Stable"
            SNAPSHOT -> "Snapshot"
        }
}
