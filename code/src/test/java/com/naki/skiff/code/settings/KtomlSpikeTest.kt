package com.naki.skiff.code.settings

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M0 spike: does ktoml compile and run at Kotlin 2.4.20 / AGP 9, where KSP and Room do not?
 *
 * The shapes here are the ones `settings.toml` needs in M4 — nested tables, a list of strings, a
 * table keyed by language, and a key left out so a default has to apply. M4 writes the real
 * `SettingsTomlTest` against the real settings class; this only has to answer whether the library
 * is usable at all.
 */
class KtomlSpikeTest {

    @Serializable
    private data class Settings(
        val theme: String,
        val editor: Editor,
        val diff: Diff,
        val limits: Limits,
        val lsp: Map<String, LanguageServer>,
    )

    @Serializable
    private data class Editor(
        val font: String,
        @SerialName("font_size") val fontSize: Int,
        @SerialName("tab_size") val tabSize: Int = 2,
        val wrap: Boolean = false,
    )

    @Serializable
    private data class Diff(val alpha: Double)

    @Serializable
    private data class Limits(
        @SerialName("max_bytes") val maxBytes: Long,
        @SerialName("open_files") val openFiles: Int,
    )

    @Serializable
    private data class LanguageServer(val command: List<String>)

    private val toml = """
        theme = "nord"

        [editor]
        font = "JetBrains Mono"
        font_size = 14
        wrap = true

        [diff]
        alpha = 0.15

        [limits]
        max_bytes = 2097152
        open_files = 30

        [lsp.python]
        command = [ "pyright-langserver", "--stdio" ]

        [lsp.typescript]
        command = [ "typescript-language-server", "--stdio" ]
    """.trimIndent()

    @Test
    fun `a settings file parses into nested classes`() {
        val settings = Toml.decodeFromString<Settings>(toml)
        assertEquals("nord", settings.theme)
        assertEquals("JetBrains Mono", settings.editor.font)
        assertEquals(14, settings.editor.fontSize)
        assertEquals(0.15, settings.diff.alpha, 1e-9)
        assertEquals(2097152L, settings.limits.maxBytes)
        assertEquals(30, settings.limits.openFiles)
    }

    @Test
    fun `a key left out falls back to the constructor default`() {
        val settings = Toml.decodeFromString<Settings>(toml)
        assertEquals(2, settings.editor.tabSize)
        assertTrue(settings.editor.wrap)
    }

    @Test
    fun `a table keyed by language becomes a map`() {
        val settings = Toml.decodeFromString<Settings>(toml)
        assertEquals(setOf("python", "typescript"), settings.lsp.keys)
        assertEquals(listOf("pyright-langserver", "--stdio"), settings.lsp.getValue("python").command)
    }
}
