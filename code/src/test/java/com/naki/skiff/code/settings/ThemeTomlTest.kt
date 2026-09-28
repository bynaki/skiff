package com.naki.skiff.code.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeTomlTest {

    /** The bundled files as the app ships them; unit tests run in the module's directory. */
    private val themes = Themes { path -> File("src/main/assets", path).inputStream() }

    private fun bundled(name: String) =
        ThemeToml.read(File("src/main/assets/themes/$name.toml").readText()) { emptyMap() }

    private val light = themes.resolve("light", night = false).colors
    private val dark = themes.resolve("dark", night = false).colors
    private val fallback = { isDark: Boolean -> if (isDark) dark else light }

    @Test
    fun `each bundled theme reads without a problem and has every color`() {
        for ((name, isDark) in listOf("light" to false, "dark" to true)) {
            val read = bundled(name)
            assertEquals(name, emptyList<SettingsProblem>(), read.problems)
            assertEquals(name, isDark, read.theme.dark)
            assertEquals(name, ThemeToml.KEYS.toSet(), read.theme.colors.keys)
        }
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
