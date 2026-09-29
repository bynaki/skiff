package com.naki.skiff.code.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTomlTest {

    @Test
    fun `the file a person is given reads back as the defaults`() {
        // The template is written by hand so it can explain itself; this is what keeps it saying
        // the same thing as the defaults in Settings.
        assertEquals(SettingsRead(Settings(), emptyList()), SettingsToml.read(SettingsToml.TEMPLATE))
    }

    @Test
    fun `every value written is the value read`() {
        val read = SettingsToml.read(
            """
            [editor]
            font = "Droid Sans Mono, monospace"
            font_size = 18
            tab_size = 2
            wrap = false

            [files]
            max_size_mb = 8
            kept_buffers = 0
            poll_seconds = 5
            """.trimIndent(),
        )
        assertEquals(emptyList<SettingsProblem>(), read.problems)
        assertEquals(
            Settings(Settings.Editor("Droid Sans Mono, monospace", 18, 2, false), Settings.Files(8, 0, 5)),
            read.settings,
        )
        assertEquals(8L * 1024 * 1024, read.settings.files.maxSizeBytes)
        assertEquals(5000L, read.settings.files.pollMillis)
    }

    @Test
    fun `an empty file is every default`() {
        assertEquals(SettingsRead(Settings(), emptyList()), SettingsToml.read(""))
    }

    @Test
    fun `a key left out takes its default and the rest are kept`() {
        val read = SettingsToml.read("[editor]\nfont_size = 20\n")
        assertEquals(emptyList<SettingsProblem>(), read.problems)
        assertEquals(20, read.settings.editor.fontSize)
        assertEquals(Settings().editor.copy(fontSize = 20), read.settings.editor)
        assertEquals(Settings().files, read.settings.files)
    }

    @Test
    fun `a value out of range is replaced by its default and reported, and the others are kept`() {
        val read = SettingsToml.read("[editor]\nfont_size = 99\ntab_size = 2\n\n[files]\npoll_seconds = 0\n")
        assertEquals(14, read.settings.editor.fontSize)
        assertEquals(2, read.settings.editor.tabSize)
        assertEquals(2, read.settings.files.pollSeconds)
        assertEquals(
            listOf(
                SettingsProblem.Refused("editor.font_size", "99", "14", SettingsToml.FONT_SIZES),
                SettingsProblem.Refused("files.poll_seconds", "0", "2", SettingsToml.POLL_SECONDS),
            ),
            read.problems,
        )
    }

    @Test
    fun `a blank font is replaced by the default`() {
        val read = SettingsToml.read("[editor]\nfont = \"  \"\n")
        assertEquals("monospace", read.settings.editor.font)
        assertEquals(listOf(SettingsProblem.Refused("editor.font", "\"  \"", "\"monospace\"", null)), read.problems)
    }

    @Test
    fun `a word where a number goes gives every default, with the reason`() {
        val read = SettingsToml.read("[editor]\nfont_size = \"big\"\nwrap = false\n")
        assertEquals(Settings(), read.settings)
        assertTrue(read.problems.single() is SettingsProblem.Unreadable)
    }

    @Test
    fun `a misspelled key is reported rather than skipped`() {
        val read = SettingsToml.read("[editor]\nfont_szie = 20\n")
        assertEquals(SettingsRead(Settings(), listOf(SettingsProblem.UnknownKey("editor.font_szie"))), read)
    }

    @Test
    fun `an unknown key outside any table is named on its own`() {
        assertEquals(listOf(SettingsProblem.UnknownKey("theme")), SettingsToml.read("theme = \"dark\"\n").problems)
    }

    @Test
    fun `broken TOML gives every default, with the reason`() {
        val read = SettingsToml.read("[editor\nfont_size = 20\n")
        assertEquals(Settings(), read.settings)
        assertTrue(read.problems.single() is SettingsProblem.Unreadable)
    }

    @Test
    fun `wrap takes only a boolean`() {
        assertFalse(SettingsToml.read("[editor]\nwrap = false\n").settings.editor.wrap)
        assertTrue(SettingsToml.read("[editor]\nwrap = \"no\"\n").problems.single() is SettingsProblem.Unreadable)
    }

    @Test
    fun `a theme that is not there is replaced by following the system`() {
        assertEquals("dark", SettingsToml.read("[editor]\ntheme = \"dark\"\n").settings.editor.theme)
        val read = SettingsToml.read("[editor]\ntheme = \"blue\"\n")
        assertEquals("system", read.settings.editor.theme)
        assertEquals(listOf(SettingsProblem.Refused("editor.theme", "\"blue\"", "\"system\"", null)), read.problems)
    }

    @Test
    fun `an imported theme is a theme the file may name`() {
        val themes = SettingsToml.THEMES + "ocean"
        assertEquals("ocean", SettingsToml.read("[editor]\ntheme = \"ocean\"\n", themes).settings.editor.theme)
        assertEquals("system", SettingsToml.read("[editor]\ntheme = \"ocean\"\n").settings.editor.theme)
    }

    @Test
    fun `choosing a theme changes only its line, and the file reads as that theme`() {
        val changed = SettingsToml.withTheme(SettingsToml.TEMPLATE, "dark")
        assertEquals(Settings(Settings().editor.copy(theme = "dark")), SettingsToml.read(changed).settings)
        val before = SettingsToml.TEMPLATE.lines()
        val after = changed.lines()
        assertEquals(before.size, after.size)
        assertEquals(listOf("theme = \"dark\""), after.filterIndexed { i, line -> line != before[i] })
    }

    @Test
    fun `choosing a theme adds the line where the file has none`() {
        val text = "# mine\n[editor]\nfont_size = 18\n\n[files]\npoll_seconds = 5\n"
        assertEquals(
            "# mine\n[editor]\ntheme = \"light\"\nfont_size = 18\n\n[files]\npoll_seconds = 5\n",
            SettingsToml.withTheme(text, "light"),
        )
        // A `theme` in another table is not the one being chosen.
        assertEquals(
            "[files]\ntheme = \"x\"\n\n[editor]\ntheme = \"light\"\n",
            SettingsToml.withTheme("[files]\ntheme = \"x\"\n", "light"),
        )
        assertEquals("[editor]\ntheme = \"dark\"\n", SettingsToml.withTheme("", "dark"))
    }
}
