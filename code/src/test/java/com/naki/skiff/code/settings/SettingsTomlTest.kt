package com.naki.skiff.code.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTomlTest {

    /** [text] with every key line of [SettingsToml.KEYS] that is commented out taken out of its comment. */
    private fun uncommented(text: String): String {
        val names = SettingsToml.KEYS.map { it.name }
        return text.lines().joinToString("\n") { line ->
            val bare = line.removePrefix("# ")
            if (line.startsWith("# ") && names.any { bare.startsWith("$it = ") }) bare else line
        }
    }

    @Test
    fun `the file a person is given reads back as the defaults, with its keys commented out or not`() {
        assertEquals(SettingsRead(Settings(), emptyList()), SettingsToml.read(SettingsToml.TEMPLATE))
        // The defaults it shows are written by hand so it can explain itself; this is what keeps them
        // saying the same thing as the defaults in Settings.
        val set = uncommented(SettingsToml.TEMPLATE)
        assertEquals(SettingsToml.KEYS.size, set.lines().count { line -> SettingsToml.KEYS.any { line.startsWith("${it.name} = ") } })
        assertEquals(SettingsRead(Settings(), emptyList()), SettingsToml.read(set))
    }

    @Test
    fun `every key in Settings is one the file shows`() {
        val descriptor = Settings.serializer().descriptor
        val inSettings = (0 until descriptor.elementsCount).flatMap { table ->
            val keys = descriptor.getElementDescriptor(table)
            (0 until keys.elementsCount).map { descriptor.getElementName(table) + "." + keys.getElementName(it) }
        }
        assertEquals(inSettings.toSet(), SettingsToml.KEYS.map { "${it.table}.${it.name}" }.toSet())
        assertEquals(SettingsToml.KEYS.size, inSettings.size)
    }

    @Test
    fun `each key says its default in its comment`() {
        for (key in SettingsToml.KEYS) {
            assertTrue(key.name, key.lines.dropLast(1).last().endsWith("Default: ${key.default}."))
            assertEquals("# ${key.name} = ${key.default}", key.lines.last())
        }
    }

    @Test
    fun `a file made before a key was is given it, commented out, in its own table`() {
        val text = "# mine\n[editor]\ntheme = \"dark\"\nfont_size = 18\n\n[files]\npoll_seconds = 5\n"
        val complete = SettingsToml.withMissingKeys(text)
        // What the person wrote is still there, in order, and still what is read.
        val kept = complete.lines().filter { it in text.lines() }
        assertEquals(text.lines(), kept)
        assertEquals(SettingsToml.read(text), SettingsToml.read(complete))
        // Every key is there now, and in its own table.
        val editor = complete.substringBefore("[files]")
        val files = complete.substringAfter("[files]")
        for (key in SettingsToml.KEYS) {
            val inTable = if (key.table == "editor") editor else files
            assertTrue(key.name, inTable.lines().any { it == "${key.name} = 18" || it == "# ${key.name} = ${key.default}" || it.startsWith("${key.name} = ") })
        }
        assertTrue(editor.contains(SettingsToml.KEYS.first { it.name == "diff_alpha" }.lines.joinToString("\n")))
        // The blank line between the tables is still between them.
        assertTrue(complete.contains("\n\n[files]\n"))
        assertTrue(complete.endsWith("\n"))
        // And done again, nothing more is added.
        assertEquals(complete, SettingsToml.withMissingKeys(complete))
    }

    @Test
    fun `a key the person keeps commented out is left that way`() {
        val text = SettingsToml.TEMPLATE.replace("# diff_alpha = 30", "#diff_alpha = 45")
        assertEquals(text, SettingsToml.withMissingKeys(text))
        assertEquals(SettingsToml.TEMPLATE, SettingsToml.withMissingKeys(SettingsToml.TEMPLATE))
    }

    @Test
    fun `a table the file lacks is added at its end`() {
        val complete = SettingsToml.withMissingKeys("[editor]\nwrap = false\n\n\n")
        assertTrue(complete, complete.contains("wrap = false\n# A CSS font family"))
        assertTrue(complete, complete.contains("Default: 30.\n# diff_alpha = 30\n\n[files]\n# 1 to 64."))
        assertTrue(complete.endsWith("# poll_seconds = 2\n"))
        assertEquals(SettingsToml.read("[editor]\nwrap = false\n"), SettingsToml.read(complete))
    }

    @Test
    fun `a file that does not read is left as it is, and a blank one is the template`() {
        assertEquals("[editor\nfont_size = 20\n", SettingsToml.withMissingKeys("[editor\nfont_size = 20\n"))
        assertEquals(SettingsToml.TEMPLATE, SettingsToml.withMissingKeys(""))
        assertEquals(SettingsToml.TEMPLATE, SettingsToml.withMissingKeys("\n  \n"))
        // A misspelled key is still TOML: the key it was meant to be joins it, for the person to compare.
        assertTrue(SettingsToml.withMissingKeys("[editor]\nfont_szie = 20\n").contains("# font_size = 14"))
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
            diff_alpha = 45

            [files]
            max_size_mb = 8
            kept_buffers = 0
            poll_seconds = 5
            """.trimIndent(),
        )
        assertEquals(emptyList<SettingsProblem>(), read.problems)
        assertEquals(
            Settings(Settings.Editor("Droid Sans Mono, monospace", 18, 2, false, diffAlpha = 45), Settings.Files(8, 0, 5)),
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
        val read = SettingsToml.read("[editor]\nfont_size = 99\ntab_size = 2\ndiff_alpha = 101\n\n[files]\npoll_seconds = 0\n")
        assertEquals(14, read.settings.editor.fontSize)
        assertEquals(2, read.settings.editor.tabSize)
        assertEquals(30, read.settings.editor.diffAlpha)
        assertEquals(2, read.settings.files.pollSeconds)
        assertEquals(
            listOf(
                SettingsProblem.Refused("editor.font_size", "99", "14", SettingsToml.FONT_SIZES),
                SettingsProblem.Refused("editor.diff_alpha", "101", "30", SettingsToml.DIFF_ALPHAS),
                SettingsProblem.Refused("files.poll_seconds", "0", "2", SettingsToml.POLL_SECONDS),
            ),
            read.problems,
        )
    }

    @Test
    fun `a table whose keys are all commented out is every default for it`() {
        val read = SettingsToml.read("[editor]\n# font_size = 14\nwrap = false\n\n[files]\n# poll_seconds = 2\n")
        assertEquals(SettingsRead(Settings(Settings().editor.copy(wrap = false)), emptyList()), read)
        // A broken line further down is still reported on its own line.
        val broken = SettingsToml.read("[files]\n# x\n[editor]\nfont_size = \"big\"\n")
        assertTrue(broken.problems.toString(), broken.problems.single().toString().contains("Line 4:"))
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
    fun `choosing a theme takes its line out of its comment`() {
        val text = "[editor]\n# Which theme. Default: \"system\".\n# theme = \"system\"\nwrap = false\n"
        assertEquals(
            "[editor]\n# Which theme. Default: \"system\".\ntheme = \"dark\"\nwrap = false\n",
            SettingsToml.withTheme(text, "dark"),
        )
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
