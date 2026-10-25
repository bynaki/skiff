package com.naki.skiff.code.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

class ThemeTomlTest {

    /** The bundled files as the app ships them; unit tests run in the module's directory. */
    private val themes = Themes({ path -> File("src/main/assets", path).inputStream() }, File("build/no-imported-themes"))

    private fun bundled(name: String) =
        ThemeToml.read(File("src/main/assets/themes/$name.toml").readText()) { emptyMap() }

    private val light = themes.resolve("light", night = false).colors
    private val dark = themes.resolve("dark", night = false).colors
    private val fallback = { isDark: Boolean -> if (isDark) dark else light }

    private val index = Json.decodeFromString<List<BundledTheme>>(File("src/main/assets/themes/index.json").readText())

    @Test
    fun `each bundled theme reads without a problem, has every color and is on the side the index says`() {
        for (entry in index) {
            val read = bundled(entry.id)
            assertEquals(entry.id, emptyList<SettingsProblem>(), read.problems)
            assertEquals(entry.id, entry.base == "dark", read.theme.dark)
            assertEquals(entry.id, ThemeToml.KEYS.toSet(), read.theme.colors.keys)
        }
    }

    @Test
    fun `the index lists every bundled file once, and nothing else`() {
        val ids = index.map { it.id }
        assertEquals(ids.distinct(), ids)
        val files = File("src/main/assets/themes").listFiles { file -> file.name.endsWith(".toml") }.orEmpty()
        assertEquals(files.map { it.name.removeSuffix(".toml") }.toSet(), ids.toSet())
        assertTrue(ids.containsAll(listOf("light", "dark")))
        for (entry in index) assertTrue(entry.id, Regex("[a-z][a-z0-9-]*").matches(entry.id))
        for (entry in index) assertTrue(entry.id, entry.base in setOf("light", "dark"))
    }

    /**
     * The theme repository's files, which the app downloads, read by the app's own parser
     * (docs/skiffcode.spec/community-themes.md): run with `SKIFF_THEMES_DIR` naming a checkout of
     * it before pushing there. Skipped without one. Its `user-themes/`, where `/theme-convert` writes a
     * theme for one person to import, is read too when there is one.
     */
    @Test
    fun `every theme of the theme repository reads without a problem and has every color`() {
        val dir = System.getenv("SKIFF_THEMES_DIR")
        assumeTrue("SKIFF_THEMES_DIR names no checkout of skiffcode-themes", !dir.isNullOrBlank())
        val toml = { at: String -> File(dir, at).listFiles { file -> file.name.endsWith(".toml") }.orEmpty().sortedBy { it.name } }
        val listed = toml("themes")
        assertTrue("no themes in $dir/themes", listed.isNotEmpty())
        val own = toml("user-themes")
        for (file in listed + own) {
            val text = file.readText()
            val read = ThemeToml.read(text, fallback)
            assertEquals(file.name, emptyList<SettingsProblem>(), read.problems)
            assertEquals(file.name, ThemeToml.KEYS.toSet(), read.theme.colors.keys)
            // Every key is written in the file, in order, none of them left to the base's.
            var table = ""
            val written = text.lines().mapNotNull { line ->
                Regex("""\[(\w+)]""").matchAt(line, 0)?.let { table = it.groupValues[1] }
                Regex("""(\w+) = """).matchAt(line, 0)?.takeIf { table.isNotEmpty() }?.let { "$table.${it.groupValues[1]}" }
            }
            assertEquals(file.name, ThemeToml.KEYS, written)
        }
        println("read ${listed.size} themes and ${own.size} in user-themes from $dir")
    }

    @Test
    fun `system follows the night and a name does not`() {
        assertEquals(dark, themes.resolve("system", night = true).colors)
        assertEquals(light, themes.resolve("system", night = false).colors)
        assertEquals(light, themes.resolve("light", night = true).colors)
        assertTrue(themes.resolve("dark", night = false).dark)
    }

    @Test
    fun `a color left out is the base's`() {
        val read = ThemeToml.read("base = \"dark\"\n[ui]\nbackground = \"#000\"\n", fallback)
        assertEquals(emptyList<SettingsProblem>(), read.problems)
        assertEquals(dark + ("ui.background" to "#000"), read.theme.colors)
    }

    @Test
    fun `a theme from before diff changed takes the base's, not its own accent`() {
        for ((base, colors) in listOf("light" to light, "dark" to dark)) {
            val read = ThemeToml.read("base = \"$base\"\n[ui]\naccent = \"#a0f\"\n", fallback)
            assertEquals(base, colors["diff.changed"], read.theme.colors["diff.changed"])
        }
        assertEquals("#4493f8", dark["diff.changed"])
        assertEquals("#0969da", light["diff.changed"])
    }

    @Test
    fun `a value that is not a color is the base's, reported, and the others are kept`() {
        val read = ThemeToml.read("[ui]\nbackground = \"red\"\nforeground = \"#123456\"\n", fallback)
        assertEquals(light["ui.background"], read.theme.colors["ui.background"])
        assertEquals("#123456", read.theme.colors["ui.foreground"])
        assertEquals(
            listOf(SettingsProblem.Refused("ui.background", "\"red\"", "\"${light["ui.background"]}\"", null)),
            read.problems,
        )
    }

    @Test
    fun `every length of hex is a color and nothing else is`() {
        for (value in listOf("#abc", "#abcd", "#aabbcc", "#aabbccdd")) {
            assertEquals(value, emptyList<SettingsProblem>(), ThemeToml.read("[ui]\nmuted = \"$value\"\n", fallback).problems)
        }
        for (value in listOf("#ab", "#abcde", "abc", "rgb(0, 0, 0)", "#ggg", "#abc; color: red")) {
            assertEquals(value, 1, ThemeToml.read("[ui]\nmuted = \"$value\"\n", fallback).problems.size)
        }
    }

    @Test
    fun `a misspelled color gives the base whole, named`() {
        val read = ThemeToml.read("base = \"dark\"\n[ui]\nbackgroud = \"#000\"\nforeground = \"#fff\"\n", fallback)
        assertEquals(Theme(true, dark), read.theme)
        assertEquals(listOf(SettingsProblem.UnknownKey("ui.backgroud")), read.problems)
    }

    @Test
    fun `an unknown base is light, reported`() {
        val read = ThemeToml.read("base = \"dim\"\n", fallback)
        assertFalse(read.theme.dark)
        assertEquals(light, read.theme.colors)
        assertEquals(listOf(SettingsProblem.Refused("base", "\"dim\"", "\"light\"", null)), read.problems)
    }

    @Test
    fun `a color goes to Android with its alpha moved to the front`() {
        val theme = Theme(false, mapOf("a" to "#fff", "b" to "#1f232866", "c" to "#0969da", "d" to "#abc8"))
        assertEquals(0xffffffff.toInt(), theme.argb("a"))
        assertEquals(0x661f2328, theme.argb("b"))
        assertEquals(0xff0969da.toInt(), theme.argb("c"))
        assertEquals(0x88aabbcc.toInt(), theme.argb("d"))
    }

    @Test
    fun `a file that does not read is light whole, with the reason`() {
        val read = ThemeToml.read("[ui\nbackground = \"#000\"\n", fallback)
        assertEquals(Theme(false, light), read.theme)
        assertTrue(read.problems.single() is SettingsProblem.Unreadable)
        // A number where a color goes does not read either: every value is a string.
        assertTrue(ThemeToml.read("[ui]\nbackground = 0\n", fallback).problems.single() is SettingsProblem.Unreadable)
    }
}
