package com.naki.skiff.code.lsp

import com.naki.skiff.code.session.ExecRefused
import com.naki.skiff.code.session.RemoteExec
import com.naki.skiff.code.session.ShellQuote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * A project's language servers, one per [LanguageServer], each run on its server through [exec]
 * (docs/skiffcode.spec.md "git과 LSP"). Messages pass through as the strings the page's LSP client
 * writes; only an `initialize` and its reply are read.
 *
 * A server starts when the page sends `initialize` for it, and nothing else starts one: a message
 * for a server that is not running is dropped, because the page was told it ended ([Listener.ended])
 * and starts over with `initialize`. Starting over is also how a dropped server gets its documents
 * back — the page's client sends `didOpen` for each open file whenever it connects, so nothing here
 * keeps their text. An `initialize` for a server already running replaces it: that is a page that
 * was rebuilt.
 *
 * A server unused for [idleMs] is shut down (`shutdown`, then `exit`). So is every one at
 * [stopAll], for an app that has been away long enough.
 */
class LspManager(
    private val exec: RemoteExec,
    private val root: String,
    private val idleMs: Long = IDLE_MS,
    private val commands: (LanguageServer) -> List<List<String>> = LanguageServer::commands,
) {

    interface Listener {
        /** A message from [server], on a background thread. */
        fun message(server: LanguageServer, message: String)

        /** [server] is no longer running, or never started; [why] says whether to start it again. */
        fun ended(server: LanguageServer, why: LspEnd)
    }

    /** Where messages go. Null while no page is listening; they are dropped meanwhile. */
    @Volatile
    var listener: Listener? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Everything that changes [running] goes through here, one at a time and in order. */
    private val inbox = Channel<Op>(Channel.UNLIMITED)
    private val running = ConcurrentHashMap<LanguageServer, Running>()

    private val detecting = Mutex()
    private val found = HashMap<LanguageServer, List<String>?>()

    init {
        scope.launch {
            for (op in inbox) {
                when (op) {
                    is Op.Send -> deliver(op.server, op.message)
                    is Op.Ended -> if (running.remove(op.server, op.running)) {
                        op.running.idle?.cancel()
                        listener?.ended(op.server, LspEnd.Failed(op.cause, op.running.process.stderrTail()))
                    }
                    is Op.Idle -> if (running[op.server] === op.running) stop(op.server, op.running, LspEnd.Idle)
                    is Op.Refuse -> if (running[op.server] === op.running) {
                        stop(op.server, op.running, LspEnd.Unsupported(op.positionEncoding))
                    }
                    is Op.StopAll -> running.entries.toList().forEach { (server, it) -> stop(server, it, LspEnd.Stopped) }
                }
            }
        }
    }

    /** Hands [message] to [server]. Never blocks, so the main thread may call it. */
    fun send(server: LanguageServer, message: String) {
        inbox.trySend(Op.Send(server, message))
    }

    /** Shuts every running server down, telling the page each one [LspEnd.Stopped]. */
    fun stopAll() {
        inbox.trySend(Op.StopAll)
    }

    /**
     * The command [server] would be started with, or null when none of its commands is on the PATH
     * the account's login shell gives. Asked once per server; a check that failed is asked again.
     */
    suspend fun command(server: LanguageServer): List<String>? = detecting.withLock {
        if (server in found) return@withLock found[server]
        val argv = try {
            commands(server).firstOrNull { exec.run(loginShell("command -v " + ShellQuote.quote(it.first()))).exitStatus == 0 }
        } catch (_: ExecRefused) {
            null
        }
        found[server] = argv
        argv
    }

    /**
     * Ends every server without the shutdown exchange, for a project going away: the connection
     * is closed right after, which ends them on the server too. Writes to the socket, so not on the
     * main thread.
     */
    fun close() {
        inbox.close()
        scope.cancel()
        running.values.forEach { it.process.close() }
        running.clear()
    }

    private suspend fun deliver(server: LanguageServer, message: String) {
        val current = running[server]
        val initializeId = initializeId(message)
        val target = when {
            initializeId != null -> {
                current?.let { stop(server, it, why = null) }
                start(server)?.also { it.initializeId = initializeId } ?: return
            }
            current != null -> current
            else -> return
        }
        target.process.send(message)
        target.idle?.cancel()
        target.idle = scope.launch {
            delay(idleMs)
            inbox.send(Op.Idle(server, target))
        }
    }

    /** [server] started and recorded as running, or null when it could not be, which the page is told. */
    private suspend fun start(server: LanguageServer): Running? {
        val channel = try {
            val argv = command(server) ?: run {
                listener?.ended(server, LspEnd.NotInstalled)
                return null
            }
            exec.start(loginShell("cd ${ShellQuote.quote(root)} && exec ${ShellQuote.command(argv)}"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            listener?.ended(server, LspEnd.Failed(e, ""))
            return null
        }
        val started = Running()
        started.process = LspProcess(
            channel,
            onMessage = { received(server, started, it) },
            onEnd = { cause ->
                started.ended.complete(Unit)
                inbox.trySend(Op.Ended(server, started, cause))
            },
        )
        running[server] = started
        return started
    }

    /** A message from [from]: the reply to `initialize` is checked, the reply to our `shutdown` kept here. */
    private fun received(server: LanguageServer, from: Running, message: String) {
        if (from.stopping) {
            if (replyId(message) == SHUTDOWN_ID) from.shutDown.complete(Unit)
            return
        }
        val waitingFor = from.initializeId
        if (waitingFor != null) {
            val reply = runCatching { JSONObject(message) }.getOrNull()
            if (reply != null && !reply.has("method") && reply.opt("id") == waitingFor) {
                from.initializeId = null
                // The page's client counts positions in UTF-16 code units and offers nothing else.
                val encoding = reply.optJSONObject("result")?.optJSONObject("capabilities")
                    ?.optString("positionEncoding", UTF_16) ?: UTF_16
                if (encoding != UTF_16) {
                    inbox.trySend(Op.Refuse(server, from, encoding))
                    return
                }
            }
        }
        listener?.message(server, message)
    }

    /** `shutdown`, its reply or a timeout, `exit`, its end or a timeout, then the channel closed. */
    private suspend fun stop(server: LanguageServer, it: Running, why: LspEnd?) {
        running.remove(server, it)
        it.idle?.cancel()
        it.stopping = true
        it.process.send("""{"jsonrpc":"2.0","id":"$SHUTDOWN_ID","method":"shutdown"}""")
        withTimeoutOrNull(SHUTDOWN_MS) { it.shutDown.await() }
        it.process.send("""{"jsonrpc":"2.0","method":"exit"}""")
        withTimeoutOrNull(EXIT_MS) { it.ended.await() }
        it.process.close()
        if (why != null) listener?.ended(server, why)
    }

    private class Running {
        lateinit var process: LspProcess

        /** The page's `initialize` id until its reply has come. */
        @Volatile
        var initializeId: Any? = null

        @Volatile
        var stopping = false
        val shutDown = CompletableDeferred<Unit>()
        val ended = CompletableDeferred<Unit>()

        /** Touched only from the inbox. */
        var idle: Job? = null
    }

    private sealed interface Op {
        class Send(val server: LanguageServer, val message: String) : Op
        class Ended(val server: LanguageServer, val running: Running, val cause: Throwable?) : Op
        class Idle(val server: LanguageServer, val running: Running) : Op
        class Refuse(val server: LanguageServer, val running: Running, val positionEncoding: String) : Op
        data object StopAll : Op
    }

    companion object {
        const val IDLE_MS = 10 * 60_000L
        private const val SHUTDOWN_MS = 3_000L
        private const val EXIT_MS = 1_000L
        private const val SHUTDOWN_ID = "skiff-shutdown"
        private const val UTF_16 = "utf-16"

        /**
         * [script] run by the account's own shell as a login shell, so it sees the PATH a person gets
         * on that server — sshd's `-c` alone skips the profile, where version managers put theirs.
         */
        internal fun loginShell(script: String): List<String> =
            listOf("sh", "-c", "exec \"\${SHELL:-/bin/sh}\" -lc \"\$1\"", "sh", script)

        /** The id of an `initialize` request, or null for any other message — most never parsed. */
        internal fun initializeId(message: String): Any? {
            if ("\"initialize\"" !in message) return null
            val json = runCatching { JSONObject(message) }.getOrNull() ?: return null
            return if (json.optString("method") == "initialize") json.opt("id") else null
        }

        private fun replyId(message: String): Any? =
            runCatching { JSONObject(message) }.getOrNull()?.takeIf { !it.has("method") }?.opt("id")
    }
}

/** Why a language server is not running, which decides whether the page starts it again. */
sealed interface LspEnd {
    /** Unused for [LspManager.IDLE_MS]. The next `initialize` starts it. */
    data object Idle : LspEnd

    /** Stopped from here ([LspManager.stopAll]): the app was away. */
    data object Stopped : LspEnd

    /** None of its commands is on the server. Not worth asking again for this project. */
    data object NotInstalled : LspEnd

    /** It counts positions some other way than the page can, so its positions would be wrong. */
    data class Unsupported(val positionEncoding: String) : LspEnd

    /** It ended by itself or never started: a crash, a dropped link, a refused command. [stderr] is its last words. */
    class Failed(val cause: Throwable?, val stderr: String) : LspEnd
}
