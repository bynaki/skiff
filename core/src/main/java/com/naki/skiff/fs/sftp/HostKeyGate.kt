package com.naki.skiff.fs.sftp

import com.naki.skiff.data.store.KnownHost
import com.naki.skiff.data.store.KnownHostStore
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey

/** What the UI must answer when we meet a host key we do not already trust. */
sealed interface HostKeyDecision {
    data object Accept : HostKeyDecision
    data object Reject : HostKeyDecision
}

data class HostKeyPrompt(
    val host: String,
    val port: Int,
    val keyType: String,
    val fingerprint: String,
    /** Non-null when we had a different key on file — that is a warning, not a first meeting. */
    val previousFingerprint: String?,
)

/**
 * Trust-on-first-use host key checking.
 *
 * sshj calls [verify] on its own transport thread and expects a blocking answer, so the
 * suspending prompt is bridged with runBlocking. Never replace this with
 * PromiscuousVerifier: without it there is nothing stopping a man in the middle.
 */
class HostKeyGate(
    private val store: KnownHostStore,
    private val askUser: suspend (HostKeyPrompt) -> HostKeyDecision,
) : HostKeyVerifier {

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean = runBlocking {
        val fingerprint = HostKeyFingerprint.of(key)
        val keyType = KeyType.fromKey(key).toString()
        val known = store.knownHost(hostname, port)

        when {
            known != null && known.fingerprint == fingerprint -> true

            else -> {
                val decision = askUser(
                    HostKeyPrompt(
                        host = hostname,
                        port = port,
                        keyType = keyType,
                        fingerprint = fingerprint,
                        previousFingerprint = known?.fingerprint,
                    ),
                )
                if (decision == HostKeyDecision.Accept) {
                    store.rememberHost(KnownHost(hostname, port, keyType, fingerprint))
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
}
