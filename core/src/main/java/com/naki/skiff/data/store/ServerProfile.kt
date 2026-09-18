package com.naki.skiff.data.store

import kotlinx.serialization.Serializable
import java.util.UUID

/** How we authenticate. Only [Password] is implemented; the others are the extension points. */
@Serializable
sealed interface AuthMethod {
    @Serializable
    data class Password(val encryptedPassword: String?) : AuthMethod

    @Serializable
    data class PrivateKey(val keyId: String, val encryptedPassphrase: String?) : AuthMethod

    @Serializable
    data object KeyboardInteractive : AuthMethod
}

@Serializable
data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    /** Directory a pane opens at when it switches to this server. */
    val startPath: String = ".",
    val auth: AuthMethod = AuthMethod.Password(null),
    val lastUsedEpochSeconds: Long = 0,
)

/** A host key we have accepted, keyed by host:port. Changing fingerprints are refused. */
@Serializable
data class KnownHost(
    val host: String,
    val port: Int,
    val keyType: String,
    val fingerprint: String,
)
