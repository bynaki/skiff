package com.naki.skiff.fs.sftp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bridges "a connection thread needs a yes/no" to "some UI has to ask the user".
 *
 * It lives at process scope rather than in a ViewModel because a background transfer can
 * meet an unknown host key while no screen is on top; the answer then arrives whenever the
 * app is next in front. The mutex keeps two connections from racing over the same prompt.
 */
class HostKeyPrompter {

    private val _pending = MutableStateFlow<HostKeyPrompt?>(null)
    val pending: StateFlow<HostKeyPrompt?> = _pending.asStateFlow()

    private val mutex = Mutex()
    private var answer: CompletableDeferred<HostKeyDecision>? = null

    suspend fun ask(prompt: HostKeyPrompt): HostKeyDecision = mutex.withLock {
        val deferred = CompletableDeferred<HostKeyDecision>()
        answer = deferred
        _pending.value = prompt
        try {
            deferred.await()
        } finally {
            _pending.value = null
            answer = null
        }
    }

    fun respond(decision: HostKeyDecision) {
        answer?.complete(decision)
    }
}
