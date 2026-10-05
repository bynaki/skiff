package com.naki.skiff.code.lsp

import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * A language server for tests, run inside MINA in place of a real one. It answers `initialize` and
 * `shutdown`, echoes a `didOpen`'s text back as a diagnostics notification, leaves on `exit`, and on
 * `die` writes half a message and leaves. Each method it is sent is handed to [received]. Its framing
 * is written out here on its own, so a mistake in [LspFraming] is not mirrored on both sides.
 */
class StubLanguageServer(
    private val stderrBytes: Int = 0,
    private val banner: String = "",
    private val onInputEnd: () -> Unit = {},
    private val positionEncoding: String? = null,
    private val received: (String) -> Unit = {},
) : Command {
    private lateinit var input: InputStream
    private lateinit var output: OutputStream
    private lateinit var error: OutputStream
    private lateinit var exit: ExitCallback

    override fun setInputStream(`in`: InputStream) { input = `in` }
    override fun setOutputStream(out: OutputStream) { output = out }
    override fun setErrorStream(err: OutputStream) { error = err }
    override fun setExitCallback(callback: ExitCallback) { exit = callback }

    override fun start(channel: ChannelSession, env: Environment) {
        thread(isDaemon = true) {
            try {
                serve()
            } catch (_: Exception) {
            } finally {
                onInputEnd()
            }
        }
    }

    private fun serve() {
        write(banner.toByteArray())
        while (true) {
            val message = receive() ?: break
            val id = message.opt("id")
            received(message.optString("method"))
            when (message.optString("method")) {
                "initialize" -> {
                    if (stderrBytes > 0) {
                        val line = "log line\n".toByteArray()
                        repeat(stderrBytes / line.size) { error.write(line) }
                        error.flush()
                    }
                    val capabilities = JSONObject().put("textDocumentSync", 2).putOpt("positionEncoding", positionEncoding)
                        reply(id, JSONObject().put("capabilities", capabilities))
                }
                "textDocument/didOpen" -> {
                    val text = message.getJSONObject("params").getJSONObject("textDocument").getString("text")
                    send(JSONObject().put("jsonrpc", "2.0").put("method", "textDocument/publishDiagnostics")
                        .put("params", JSONObject().put("echo", text)))
                }
                "shutdown" -> reply(id, JSONObject.NULL)
                "exit" -> return exit.onExit(0)
                "die" -> {
                    write("Content-Length: 100\r\n\r\n{\"id\":".toByteArray())
                    return exit.onExit(1)
                }
            }
        }
        exit.onExit(0)
    }

    private fun receive(): JSONObject? {
        var length = 0
        while (true) {
            val line = buildString {
                while (true) {
                    val c = input.read()
                    if (c < 0) return null
                    if (c == '\n'.code) break
                    append(c.toChar())
                }
            }.trimEnd('\r')
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:")) length = line.substringAfter(':').trim().toInt()
        }
        return JSONObject(input.readNBytes(length).decodeToString())
    }

    private fun reply(id: Any?, result: Any) =
        send(JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result))

    private fun send(message: JSONObject) {
        val body = message.toString().toByteArray()
        write("Content-Length: ${body.size}\r\n\r\n".toByteArray() + body)
    }

    private fun write(bytes: ByteArray) {
        output.write(bytes)
        output.flush()
    }

    override fun destroy(channel: ChannelSession) = Unit
}
