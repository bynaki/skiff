package com.naki.skiff.code.data

import com.naki.skiff.data.store.KnownHost
import com.naki.skiff.data.store.ServerProfile
import kotlinx.serialization.Serializable

/**
 * The user's answer to "is this the same machine" for two saved profiles that showed the same host
 * key, by their ids in either order. The design is in `docs/skiffcode.spec/open-flow.md`, "같은 서버의
 * 다른 주소".
 */
@Serializable
data class SameMachineAnswer(
    val first: String,
    val second: String,
    val same: Boolean,
) {
    fun isAbout(a: String, b: String): Boolean = (first == a && second == b) || (first == b && second == a)

    fun mentions(id: String): Boolean = first == id || second == id
}

/**
 * One server as Skiff Code tells them apart: what [key] names it by, for an open file's identity and
 * for finding a project, and the profiles that reach it.
 */
data class Server(val key: String, val profileIds: Set<String>)

/**
 * Which saved profiles are one machine. Profiles go together when they show the same host key and
 * the user said so: a key alone is not enough, since an image with its keys made at build time gives
 * every machine made from it the same one. A group is named by the key and its first profile in
 * [profiles]' order, which a profile added later never changes, since new ones go at the end.
 *
 * A profile with no key on record, or one not saved at all, is a server of its own.
 */
class ServerGroups(
    private val profiles: List<ServerProfile>,
    knownHosts: List<KnownHost>,
    private val answers: List<SameMachineAnswer>,
) {
    /** By host and port exactly, as [com.naki.skiff.fs.sftp.HostKeyGate] wrote them. */
    private val fingerprints: Map<String, String> = profiles.mapNotNull { profile ->
        knownHosts.firstOrNull { it.host == profile.host && it.port == profile.port }?.let { profile.id to it.fingerprint }
    }.toMap()

    /** Each profile's group as a list in [profiles]' order, so its first is the one it is named by. */
    private val groups: Map<String, List<ServerProfile>> = run {
        // Each profile points at an earlier one of its group, by place in [profiles], so following
        // the pointers ends at the group's first. Joined only through "same" answers between profiles
        // that still show one key: an answer about a profile whose key has since changed joins nothing.
        val index = profiles.withIndex().associate { (i, profile) -> profile.id to i }
        val parent = IntArray(profiles.size) { it }
        fun root(i: Int): Int = if (parent[i] == i) i else root(parent[i]).also { parent[i] = it }
        for (answer in answers) {
            if (!answer.same) continue
            val a = index[answer.first] ?: continue
            val b = index[answer.second] ?: continue
            val key = fingerprints[answer.first] ?: continue
            if (fingerprints[answer.second] != key) continue
            val (ra, rb) = root(a) to root(b)
            parent[maxOf(ra, rb)] = minOf(ra, rb)
        }
        val byRoot = profiles.indices.groupBy(::root) { profiles[it] }
        profiles.indices.associate { profiles[it].id to byRoot.getValue(root(it)) }
    }

    /** The server [profile] reaches. */
    fun serverOf(profile: ServerProfile): Server {
        val fingerprint = fingerprints[profile.id]
        val group = groups[profile.id]
        if (fingerprint == null || group == null) return Server("profile:${profile.id}", setOf(profile.id))
        return Server("key:$fingerprint:${group.first().id}", group.mapTo(LinkedHashSet()) { it.id })
    }

    /**
     * The groups [profile] has to be asked about: those with its host key that it is not part of and
     * has no answer about yet, each by the profile it is named by. An answer between any profile of
     * its group and any of the other counts, so each group is asked about once.
     */
    fun toAsk(profile: ServerProfile): List<ServerProfile> {
        val fingerprint = fingerprints[profile.id] ?: return emptyList()
        val mine = groups[profile.id] ?: return emptyList()
        val mineIds = mine.mapTo(HashSet()) { it.id }
        return profiles
            .filter { it.id !in mineIds && fingerprints[it.id] == fingerprint }
            .map { groups.getValue(it.id) }
            .distinct()
            .filter { other -> answers.none { answer -> mine.any { a -> other.any { b -> answer.isAbout(a.id, b.id) } } } }
            .map { it.first() }
    }
}
