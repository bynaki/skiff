package com.naki.skiff.code.lsp

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "SkiffCode"

/** Error, Warning, Information — LSP `DiagnosticSeverity`. */
private const val WARNING = 2
private const val INFORMATION = 3

/** A phrase from the Korean comment in the sample file, marked so wide glyphs are covered. */
private const val KOREAN_NEEDLE = "원격 파일을 읽어"
private const val FETCH_NEEDLE = "fetch("

/**
 * M0 spike: a fake language server standing in for a real one started over SSH exec.
 *
 * It speaks only the bare JSON-RPC messages `@codemirror/lsp-client`'s `Transport` carries. The
 * Content-Length framing a real `LspProcess` has to put on stdio is M6's work and deliberately
 * absent here — the question this answers is whether the bridge can be the transport.
 *
 * Diagnostics cover a Korean comment on purpose: the client converts LSP positions as UTF-16 code
 * units without negotiating `positionEncoding`, so a line with wide glyphs is where that breaks.
 */
class StubLsp(private val fullSync: Boolean, private val send: (String) -> Unit) {

    private val documents = mutableMapOf<String, Document>()

    /** One open document, with the line starts needed to turn offsets into LSP positions. */
    private class Document(val text: String) {
        private val lineStarts = buildList {
            add(0)
            text.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
        }

        fun position(offset: Int): JSONObject {
            var low = 0
            var high = lineStarts.size - 1
            while (low < high) {
                val mid = (low + high + 1) / 2
                if (lineStarts[mid] <= offset) low = mid else high = mid - 1
            }
            return JSONObject().put("line", low).put("character", offset - lineStarts[low])
        }

        fun offset(position: JSONObject): Int =
            lineStarts[position.getInt("line")] + position.getInt("character")

        fun range(from: Int, to: Int): JSONObject =
            JSONObject().put("start", position(from)).put("end", position(to))

        /** The identifier around [offset], empty when there is none. */
        fun wordAt(offset: Int): String {
            fun part(char: Char) = char.isLetterOrDigit() || char == '_' || char == '$'
            var from = offset
            while (from > 0 && part(text[from - 1])) from--
            var to = offset
            while (to < text.length && part(text[to])) to++
            return text.substring(from, to)
        }
    }

    fun receive(message: String) {
        val started = System.nanoTime()
        val request = JSONObject(message)
        val parsed = System.nanoTime()
        val method = request.getString("method")
        val id = if (request.has("id")) request.get("id") else null
        val params = request.optJSONObject("params") ?: JSONObject()
        Log.i(TAG, "lsp <- $method (${message.length} chars, parsed in ${(parsed - started) / 1_000_000} ms)")
        when (method) {
            "initialize" -> respond(id, JSONObject().put("capabilities", capabilities()))
            "initialized", "exit", "$/cancelRequest" -> {}
            "shutdown" -> respond(id, JSONObject.NULL)
            "textDocument/didOpen" -> params.getJSONObject("textDocument").let {
                open(it.getString("uri"), it.getString("text"))
            }
            "textDocument/didChange" -> {
                val uri = params.getJSONObject("textDocument").getString("uri")
                val document = documents[uri] ?: return
                open(uri, applyChanges(document, params.getJSONArray("contentChanges")))
            }
            "textDocument/didClose" -> documents.remove(params.getJSONObject("textDocument").getString("uri"))
            "textDocument/hover" -> respond(id, hover(params))
            "textDocument/completion" -> respond(id, completion(params))
            "textDocument/definition" -> respond(id, definition(params))
            else -> respondError(id, -32601, "Method not found: $method")
        }
    }

    private fun capabilities() = JSONObject()
        // Incremental, unless asked for Full, which makes every change resend the whole document.
        .put("textDocumentSync", if (fullSync) 1 else 2)
        .put("positionEncoding", "utf-16")
        .put("hoverProvider", true)
        .put("definitionProvider", true)
        .put("completionProvider", JSONObject().put("triggerCharacters", JSONArray().put(".")))

    private fun open(uri: String, text: String) {
        val document = Document(text)
        documents[uri] = document
        val diagnostics = JSONArray()
        mark(document, diagnostics, KOREAN_NEEDLE, INFORMATION, "주석은 영어로 적는다", limit = 1)
        mark(document, diagnostics, FETCH_NEEDLE, WARNING, "원격 파일은 브리지로 읽는다", limit = 49)
        send(
            JSONObject().put("jsonrpc", "2.0").put("method", "textDocument/publishDiagnostics")
                .put("params", JSONObject().put("uri", uri).put("diagnostics", diagnostics)).toString(),
        )
        Log.i(TAG, "lsp -> publishDiagnostics: ${diagnostics.length()} for $uri")
    }

    /**
     * A change without a range carries the whole document. Otherwise the ranges are all against the
     * previously synced text and arrive in descending order, so applying them in order never moves
     * an offset a later one still needs.
     */
    private fun applyChanges(document: Document, changes: JSONArray): String {
        val edits = (0 until changes.length()).map { changes.getJSONObject(it) }
        edits.lastOrNull { !it.has("range") }?.let { return it.getString("text") }
        val out = StringBuilder(document.text)
        for (edit in edits) {
            val range = edit.getJSONObject("range")
            out.replace(
                document.offset(range.getJSONObject("start")),
                document.offset(range.getJSONObject("end")),
                edit.getString("text"),
            )
        }
        Log.i(TAG, "lsp: applied ${edits.size} incremental changes")
        return out.toString()
    }

    private fun mark(document: Document, into: JSONArray, needle: String, severity: Int, message: String, limit: Int) {
        var at = document.text.indexOf(needle)
        var found = 0
        while (at >= 0 && found < limit) {
            into.put(
                JSONObject()
                    .put("range", document.range(at, at + needle.length))
                    .put("severity", severity)
                    .put("message", message)
                    .put("source", "stub"),
            )
            at = document.text.indexOf(needle, at + needle.length)
            found++
        }
    }

    private fun hover(params: JSONObject): Any {
        val (document, offset) = locate(params) ?: return JSONObject.NULL
        val position = params.getJSONObject("position")
        val word = document.wordAt(offset)
        val value = "`${word.ifEmpty { "(no word)" }}` — 스텁이 답했다\n\n" +
            "줄 ${position.getInt("line") + 1}, 글자 ${position.getInt("character")}, 오프셋 $offset"
        return JSONObject()
            .put("contents", JSONObject().put("kind", "markdown").put("value", value))
            .put("range", document.range(offset - word.length.coerceAtMost(offset), offset))
    }

    /** Jumps to where the word under the cursor is declared, which the sample file spells `function <word>`. */
    private fun definition(params: JSONObject): Any {
        val (document, offset) = locate(params) ?: return JSONObject.NULL
        val word = document.wordAt(offset).ifEmpty { return JSONObject.NULL }
        val at = document.text.indexOf("function $word(").takeIf { it >= 0 } ?: return JSONObject.NULL
        val from = at + "function ".length
        return JSONObject()
            .put("uri", params.getJSONObject("textDocument").getString("uri"))
            .put("range", document.range(from, from + word.length))
    }

    private fun completion(params: JSONObject): Any {
        val (document, offset) = locate(params) ?: return JSONObject.NULL
        val prefix = document.wordAt(offset)
        val items = JSONArray()
        for (candidate in COMPLETIONS) {
            if (!candidate.startsWith(prefix)) continue
            items.put(
                JSONObject()
                    .put("label", candidate)
                    .put("kind", 3) // Function
                    .put("detail", "(path: string) => Promise<string[]>")
                    .put("documentation", JSONObject().put("kind", "markdown").put("value", "스텁 완성 항목")),
            )
        }
        Log.i(TAG, "lsp -> completion: ${items.length()} items for prefix '$prefix'")
        return JSONObject().put("isIncomplete", false).put("items", items)
    }

    private fun locate(params: JSONObject): Pair<Document, Int>? {
        val document = documents[params.getJSONObject("textDocument").getString("uri")] ?: return null
        return document to document.offset(params.getJSONObject("position"))
    }

    private fun respond(id: Any?, result: Any) {
        if (id == null) return
        send(JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result).toString())
    }

    private fun respondError(id: Any?, code: Int, message: String) {
        if (id == null) return
        send(
            JSONObject().put("jsonrpc", "2.0").put("id", id)
                .put("error", JSONObject().put("code", code).put("message", message)).toString(),
        )
    }
}

private val COMPLETIONS = listOf(
    "readChunk0", "readChunk1", "readChunk2", "readChunkAll", "readChunkStream",
    "encodeURIComponent", "trimEnd", "브리지로읽기",
)
