package com.naki.skiff.code.intent

import com.naki.skiff.data.store.ServerProfile

/** Where to put the cursor and which layer to show, as a link asked for them. */
data class OpenAt(
    val line: Int? = null,
    val col: Int? = null,
    val layer: SkiffCodeUri.Layer? = null,
)

/**
 * What an incoming intent asks to open, with the server already matched against the stored
 * profiles. Built by [OpenRequest.of] from the intent's action and data alone, so it is tested on
 * a plain JVM.
 */
sealed interface OpenRequest {

    /** `skiffcode:///path`, read through MANAGE_EXTERNAL_STORAGE. */
    data class LocalPath(val path: String, val at: OpenAt) : OpenRequest

    /** `content://…` from another app's ACTION_VIEW or ACTION_EDIT. */
    data class Content(val uri: String, val writable: Boolean) : OpenRequest

    /** A link that named a server we have a profile for. */
    data class Remote(val profile: ServerProfile, val path: String, val at: OpenAt) : OpenRequest

    /**
     * A link to a server with no matching profile. Nothing is connected until the user has seen
     * the address and agreed; [user] is null when the link did not carry one.
     */
    data class UnknownServer(
        val user: String?,
        val host: String,
        val port: Int,
        val alias: String?,
        val path: String,
        val at: OpenAt,
    ) : OpenRequest

    /** Not something we open. [reason] is shown to the user as is. */
    data class Invalid(val reason: String) : OpenRequest

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_EDIT = "android.intent.action.EDIT"

        fun of(action: String?, data: String?, profiles: List<ServerProfile>): OpenRequest {
            if (data == null) return Invalid("the intent carries no link")
            if (action != ACTION_VIEW && action != ACTION_EDIT) return Invalid("unsupported action: $action")

            if (data.startsWith("content://", ignoreCase = true)) {
                return Content(data, writable = action == ACTION_EDIT)
            }
            val uri = try {
                SkiffCodeUri.parse(data)
            } catch (e: IllegalArgumentException) {
                return Invalid(e.message ?: "not a valid link")
            }
            val at = OpenAt(uri.line, uri.col, uri.layer)
            return when (val target = uri.target) {
                is SkiffCodeUri.Local -> LocalPath(target.path, at)
                is SkiffCodeUri.Remote -> {
                    val profile = findProfile(target, profiles)
                    if (profile != null) {
                        Remote(profile, target.path, at)
                    } else {
                        UnknownServer(target.user, target.host, target.port, target.alias, target.path, at)
                    }
                }
            }
        }

        /**
         * The alias first, then the address. A link's alias names the user's own profile, so when
         * it matches, the profile's host is used and the link's address is not: a link cannot
         * point a known alias at another machine. Without a user in the link, the address matches
         * only when exactly one profile has that host and port.
         */
        fun findProfile(target: SkiffCodeUri.Remote, profiles: List<ServerProfile>): ServerProfile? {
            target.alias?.let { alias ->
                profiles.firstOrNull { it.name == alias }?.let { return it }
            }
            val sameAddress = profiles.filter { it.host.equals(target.host, ignoreCase = true) && it.port == target.port }
            return if (target.user != null) {
                sameAddress.firstOrNull { it.username == target.user }
            } else {
                sameAddress.singleOrNull()
            }
        }
    }
}
