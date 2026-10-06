package com.naki.skiff.code.lsp

import com.naki.skiff.code.session.ExecChannel
import com.naki.skiff.code.session.ExecRefused
import com.naki.skiff.code.session.ExecTimedOut
import com.naki.skiff.fs.FsError
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
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import net.schmizz.sshj.common.SSHException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.net.SocketException
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
 *
 * A server whose link dropped under it may still be running on the server: a phone that moved from
 * Wi-Fi to LTE never got its goodbye across, and sshd keeps the session until TCP gives up on it, two
 * hours on Linux. The start script announces the server's pid, and the next start of that server ends
 * what is left of the old one first ([orphans]).
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

    /** Servers whose link dropped under them, to be ended on the server before they start again. Touched only from the inbox. */
    private val orphans = HashMap<LanguageServer, Running>()

    private val detecting = Mutex()

    /** By the commands that were tried, so a server whose commands `settings.toml` changed is looked for again. */
    private val found = HashMap<List<List<String>>, List<String>?>()

    init {
        scope.launch {
            for (op in inbox) {
                when (op) {
                    is Op.Send -> deliver(op.server, op.message)
                    is Op.Ended -> if (running.remove(op.server, op.running)) {
                        op.running.idle?.cancel()
                        val dropped = droppedLink(op.cause)
                        if (dropped) orphans[op.server] = op.running
                        val why = if (dropped) LspEnd.Disconnected else LspEnd.Failed(op.cause, op.running.process.stderrTail())
                        listener?.ended(op.server, why)
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
     * the account's login shell gives, with the project's tools around it ([withTools]). Asked once
     * per server and set of commands; a check that failed is asked again.
     */
    suspend fun command(server: LanguageServer): List<String>? = detecting.withLock {
        val candidates = commands(server)
        if (candidates in found) return@withLock found[candidates]
        val argv = try {
            candidates.firstOrNull { exec.run(loginShell(withTools(root, "command -v " + ShellQuote.quote(it.first())))).exitStatus == 0 }
        } catch (_: ExecRefused) {
            null
        }
        found[candidates] = argv
        argv
    }

    /**
     * Ends every server without the shutdown exchange, for a project going away or a session being
     * replaced: the connection is closed right after, which ends them on the server too. The page
     * is told [LspEnd.Closed] for each. Writes to the socket, so not on the main thread.
     */
    fun close() {
        inbox.close()
        scope.cancel()
        running.forEach { (server, it) ->
            it.process.close()
            listener?.ended(server, LspEnd.Closed)
        }
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
        orphans[server]?.let { reap(server, it) }
        val argv: List<String>
        val channel = try {
            argv = command(server) ?: run {
                listener?.ended(server, LspEnd.NotInstalled)
                return null
            }
            exec.start(loginShell(withTools(root, "cd ${ShellQuote.quote(root)} && echo $PID_MARKER \$\$ && exec ${ShellQuote.command(argv)}")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            listener?.ended(server, if (droppedLink(e)) LspEnd.Disconnected else LspEnd.Failed(e, ""))
            return null
        }
        val pid = try {
            announcedPid(channel)
        } catch (e: CancellationException) {
            channel.close()
            throw e
        } catch (e: EOFException) {
            // The shell ended before the server started, so its stderr has ended too and says why.
            listener?.ended(server, LspEnd.Failed(null, channel.failure()))
            channel.close()
            return null
        } catch (e: Exception) {
            channel.close()
            listener?.ended(server, if (droppedLink(e)) LspEnd.Disconnected else LspEnd.Failed(e, ""))
            return null
        }
        val started = Running(pid, argv.first().substringAfterLast('/'))
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

    /**
     * The pid the start script prints ahead of the server, which is the server's: `exec` keeps it.
     * Anything else first is a login shell writing to stdout, which the server's messages would have
     * met anyway. Throws [EOFException] when the shell ended without starting it.
     */
    private suspend fun announcedPid(channel: ExecChannel): Int {
        val line = withTimeoutOrNull(ANNOUNCE_MS) {
            runInterruptible(Dispatchers.IO) {
                val bytes = ByteArrayOutputStream()
                while (bytes.size() < MAX_ANNOUNCEMENT) {
                    val c = channel.stdout.read()
                    if (c < 0) throw EOFException("the shell ended before the language server started")
                    if (c == '\n'.code) break
                    bytes.write(c)
                }
                bytes.toString(Charsets.UTF_8.name())
            }
        } ?: throw ExecTimedOut(ANNOUNCE_MS)
        // Never 1 or less: `kill` takes those for every process it may end.
        return line.removePrefix("$PID_MARKER ").takeIf { line.startsWith(PID_MARKER) }?.toIntOrNull()?.takeIf { it > 1 }
            ?: throw LspProtocolError("not the language server's pid: $line")
    }

    /**
     * Ends what is left of [server] from before its link dropped. Once it has run the orphan is
     * forgotten whatever it found; a link still down keeps it for the next start.
     */
    private suspend fun reap(server: LanguageServer, orphan: Running) {
        try {
            exec.run(kill(orphan.pid, orphan.program))
            orphans.remove(server)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!droppedLink(e)) orphans.remove(server)
        }
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

    /** [pid] leads its process group on the server; [program] is the name its command line has. */
    private class Running(val pid: Int, val program: String) {
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
        internal const val PID_MARKER = "skiff-lsp-pid"
        private const val MAX_ANNOUNCEMENT = 1024
        private const val ANNOUNCE_MS = 60_000L

        /**
         * Ends the process group [pid] leads — the server and what it started, as pyright's Python
         * wrapper starts node — if its command line still names [program], so a pid taken since by
         * something else is left alone. sshd makes each session's command a group leader; where it is
         * not, only [pid] is ended. `kill -s TERM --` is the form dash takes for a group.
         */
        internal fun kill(pid: Int, program: String): List<String> = listOf(
            "sh", "-c",
            "case \"\$(ps -o args= -p \"\$1\" 2>/dev/null)\" in *\"\$2\"*) kill -s TERM -- \"-\$1\" 2>/dev/null || kill -s TERM \"\$1\";; esac",
            "sh", pid.toString(), program,
        )

        /**
         * [script] run by the account's own shell as a login shell, so it sees the PATH a person gets
         * on that server — sshd's `-c` alone skips the profile, where version managers put theirs.
         */
        internal fun loginShell(script: String): List<String> =
            listOf("sh", "-c", "exec \"\${SHELL:-/bin/sh}\" -lc \"\$1\"", "sh", script)

        /**
         * [script] with the project's virtualenv ahead of the login shell's PATH, as VS Code takes a
         * project's own tools first, and `~/.local/bin` after it (2026-10-06 사용자 결정). pip, uv and
         * pipx put commands there, and the line that adds it to the PATH is often only in `.zshrc` or
         * `.bashrc`, which a login shell that is not interactive does not read — an interactive one
         * would, but may print to stdout, which the server's messages share.
         */
        internal fun withTools(root: String, script: String): String =
            "PATH=${ShellQuote.quote("$root/.venv/bin")}:\"\$PATH\":\"\$HOME/.local/bin\"; export PATH; $script"

        /**
         * Whether [cause] is the link to the server going, not the language server: the socket or
         * sshj's transport under a running one, or a connection [RemoteExec] could not make again.
         */
        internal fun droppedLink(cause: Throwable?): Boolean =
            cause is SSHException || cause is SocketException || cause is FsError.NetworkLost || cause is FsError.Unreachable

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

    /**
     * Its project's command connection was closed ([LspManager.close]): the project was removed, or
     * its profile changed and the session was replaced. Starting again finds out which.
     */
    data object Closed : LspEnd

    /**
     * The link to the server dropped under it, or was down when it was to start — a phone folded shut
     * or off the network. Not the server's doing: started again when next wanted, or when the app
     * comes back.
     */
    data object Disconnected : LspEnd

    /** None of its commands is on the server. Not worth asking again for this project. */
    data object NotInstalled : LspEnd

    /** It counts positions some other way than the page can, so its positions would be wrong. */
    data class Unsupported(val positionEncoding: String) : LspEnd

    /** It ended by itself or never started: a crash, a dropped link, a refused command. [stderr] is its last words. */
    class Failed(val cause: Throwable?, val stderr: String) : LspEnd
}
