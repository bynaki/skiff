package com.naki.skiff.transfer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A file the transfer found already at its destination. */
data class ConflictPrompt(val jobId: String, val fileName: String, val destinationDir: String)

/**
 * Bridges "a transfer met a taken name" to "some UI has to ask the user", the same way
 * [com.naki.skiff.fs.sftp.HostKeyPrompter] does for host keys and for the same reason: the
 * transfer runs with no screen on top as often as not, and the answer arrives when the app is
 * next in front. The queue runs one job at a time, so there is never more than one question.
 */
class ConflictPrompter {

    private val _pending = MutableStateFlow<ConflictPrompt?>(null)
    val pending: StateFlow<ConflictPrompt?> = _pending.asStateFlow()

    private var answer: CompletableDeferred<ConflictAnswer>? = null

    suspend fun ask(prompt: ConflictPrompt): ConflictAnswer {
        val deferred = CompletableDeferred<ConflictAnswer>()
        answer = deferred
        _pending.value = prompt
        try {
            return deferred.await()
        } finally {
            _pending.value = null
            answer = null
        }
    }

    fun respond(answer: ConflictAnswer) {
        this.answer?.complete(answer)
    }
}
