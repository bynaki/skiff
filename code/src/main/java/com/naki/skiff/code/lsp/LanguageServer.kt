package com.naki.skiff.code.lsp

/**
 * The language servers a project can have, one running per kind (docs/skiffcode.spec.md "git과 LSP").
 * [commands] are tried in order with `command -v`; none is installed on the server by this app.
 */
enum class LanguageServer(val languageIds: Set<String>, val commands: List<List<String>>) {
    Python(setOf("python"), listOf(listOf("pyright-langserver", "--stdio"), listOf("pylsp"))),
    TypeScript(
        setOf("typescript", "javascript", "typescriptreact", "javascriptreact"),
        listOf(listOf("typescript-language-server", "--stdio")),
    ),
    Markdown(setOf("markdown"), listOf(listOf("marksman", "server")));

    companion object {
        /** The server for an LSP language id, or null for a language none of them speaks. */
        fun forLanguage(languageId: String): LanguageServer? = entries.firstOrNull { languageId in it.languageIds }
    }
}
