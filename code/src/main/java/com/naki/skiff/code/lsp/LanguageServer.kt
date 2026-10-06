package com.naki.skiff.code.lsp

/**
 * The language servers a project can have, one running per kind (docs/skiffcode.spec.md "git과 LSP").
 * [commands] are tried in order with `command -v`; none is installed on the server by this app.
 * [languageIds] gives the LSP language id of each file extension the server takes.
 */
enum class LanguageServer(val languageIds: Map<String, String>, val commands: List<List<String>>) {
    Python(
        mapOf("py" to "python", "pyi" to "python"),
        listOf(listOf("pyright-langserver", "--stdio"), listOf("pylsp")),
    ),
    TypeScript(
        mapOf(
            "ts" to "typescript", "mts" to "typescript", "cts" to "typescript", "tsx" to "typescriptreact",
            "js" to "javascript", "mjs" to "javascript", "cjs" to "javascript", "jsx" to "javascriptreact",
        ),
        listOf(listOf("typescript-language-server", "--stdio")),
    ),
    Markdown(mapOf("md" to "markdown", "markdown" to "markdown"), listOf(listOf("marksman", "server")));

    companion object {
        /** The server for a file by its name, and the language id it is opened with; null for a file none of them takes. */
        fun forFile(name: String): Pair<LanguageServer, String>? {
            val extension = name.substringAfterLast('.', "").lowercase()
            return entries.firstNotNullOfOrNull { server -> server.languageIds[extension]?.let { server to it } }
        }
    }
}
