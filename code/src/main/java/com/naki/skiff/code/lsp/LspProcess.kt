package com.naki.skiff.code.lsp

import com.naki.skiff.code.session.ExecChannel
import com.naki.skiff.code.session.RemoteExec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One language server, talked to over the channel [RemoteExec.start] left running for it
 * (docs/skiffcode.spec.md "git과 LSP"). This frames and unframes and nothing more: messages go in and
 * come out as the JSON strings the page's LSP client exchanges, and are never parsed here.
 *
 * [onMessage] hears each message the server sends, in order, on a background thread. [onEnd] is
 * called once when the server stops answering — it exited, the link dropped, or it wrote something
 * that is not LSP — with the reason, or null for an end between two messages. Neither is called
 * after [close].
 *
 * Its stderr is read as it comes and only the last [STDERR_KEPT] bytes kept ([stderrTail]): left
 * unread, a server that logs there would fill the channel's window and stop answering.
 */
class LspProcess(
    private val channel: ExecChannel,
    private val onMessage: (String) -> Unit,
    private val onEnd: (Throwable?) -> Unit,
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outbox = Channel<String>(Channel.UNLIMITED)
    private val finished = AtomicBoolean(false)
    private val stderr = ByteArrayOutputStream()

    init {
        scope.launch {
            val reader = LspReader(channel.stdout)
            val cause = try {
                while (true) onMessage(reader.read() ?: break)
                null
            } catch (e: Exception) {
                e
            }
            finish(cause, report = true)
        }
        scope.launch {
            try {
                for (message in outbox) {
                    channel.stdin.write(LspFraming.encode(message))
                    channel.stdin.flush()
                }
            } catch (e: Exception) {
                finish(e, report = true)
            }
        }
        scope.launch {
            val buffer = ByteArray(4096)
            runCatching {
                while (true) {
                    val n = channel.stderr.read(buffer)
                    if (n < 0) break
                    keep(buffer, n)
                }
            }
        }
    }

    /** Queues [message] for the server. Never blocks, so the main thread may call it; dropped once ended. */
    fun send(message: String) {
        outbox.trySend(message)
    }

    /** The last of what the server wrote to stderr, which is where it says why it would not start. */
    fun stderrTail(): String = synchronized(stderr) { stderr.toByteArray().decodeToString() }

    /** Closes the channel, which writes to the socket, so not on the main thread. */
    override fun close() = finish(null, report = false)

    private fun finish(cause: Throwable?, report: Boolean) {
        if (!finished.compareAndSet(false, true)) return
        outbox.close()
        scope.cancel()
        channel.close()
        if (report) onEnd(cause)
    }

    private fun keep(bytes: ByteArray, count: Int) = synchronized(stderr) {
        stderr.write(bytes, 0, count)
        if (stderr.size() > STDERR_KEPT * 2) {
            val kept = stderr.toByteArray().let { it.copyOfRange(it.size - STDERR_KEPT, it.size) }
            stderr.reset()
            stderr.write(kept)
        }
    }

    private companion object {
        const val STDERR_KEPT = 4096
    }
}
