package com.naki.skiff.code.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Themes brought in with Import Theme, beside the bundled ones. */
class ThemesTest {

    private val dir: File = Files.createTempDirectory("themes").toFile().resolve("themes")

    /** The bundled files as the app ships them; unit tests run in the module's directory. */
    private fun themes() = Themes({ path -> File("src/main/assets", path).inputStream() }, dir)

    @Test
    fun `a theme is named after its file`() {
        assertEquals("ocean", Themes.nameOf("ocean.toml"))
        assertEquals("Ocean", Themes.nameOf("Ocean.TOML"))
        assertEquals("ocean (1)", Themes.nameOf("ocean (1).toml"))
        assertEquals("바다", Themes.nameOf("바다.toml"))
        assertEquals("ocean", Themes.nameOf("ocean"))
    }

    @Test
    fun `a name that reaches out of the directory, or that TOML would have to escape, is refused`() {
        for (fileName in listOf("../ocean.toml", "a/b.toml", ".toml", ".hidden.toml", "a.b.toml", "say \"hi\".toml", "a\\b.toml", "a\u0000.toml", "")) {
            assertNull(fileName, Themes.nameOf(fileName))
        }
    }

    @Test
    fun `the names settings already means something by are refused, in any case`() {
        for (fileName in listOf("system.toml", "light.toml", "Dark.toml")) assertNull(fileName, Themes.nameOf(fileName))
    }

    @Test
    fun `an imported theme is offered after the ones every install has, and is used`() {
        val themes = themes()
        assertEquals(listOf("system", "light", "dark"), themes.names)
        val problems = themes.import("ocean", "base = \"dark\"\n[ui]\nbackground = \"#012345\"\n")
        assertEquals(emptyList<SettingsProblem>(), problems)
        assertEquals(listOf("system", "light", "dark", "ocean"), themes.names)
        val ocean = themes.resolve("ocean", night = false)
        assertTrue(ocean.dark)
        assertEquals("#012345", ocean.colors["ui.background"])
        // What it lacks is the dark bundle's.
        assertEquals(themes.resolve("dark", night = false).colors["ui.foreground"], ocean.colors["ui.foreground"])
    }

    @Test
    fun `an imported theme survives into the next process, and exports as it was written`() {
        val text = "# mine\nbase = \"light\"\n[syntax]\nkeyword = \"#abc\"\n"
        themes().import("ocean", text)
        val next = themes()
        assertEquals(listOf("system", "light", "dark", "ocean"), next.names)
        assertEquals("#abc", next.resolve("ocean", night = true).colors["syntax.keyword"])
        assertEquals(text, next.text("ocean"))
        assertEquals(File("src/main/assets/themes/dark.toml").readText(), next.text("dark"))
    }

    @Test
    fun `importing a name again replaces it, and only an imported name counts as one`() {
        val themes = themes()
        assertFalse(themes.isImported("ocean"))
        themes.import("ocean", "[ui]\nbackground = \"#111\"\n")
        themes.resolve("ocean", night = false)
        assertTrue(themes.isImported("ocean"))
        assertFalse(themes.isImported("dark"))
        themes.import("ocean", "[ui]\nbackground = \"#222\"\n")
        assertEquals("#222", themes.resolve("ocean", night = false).colors["ui.background"])
        assertEquals(listOf("system", "light", "dark", "ocean"), themes.names)
    }

    @Test
    fun `a theme with problems still comes in, with what was passed over`() {
        val themes = themes()
        val problems = themes.import("ocean", "[ui]\nbackgrund = \"#111\"\n")
        assertEquals(listOf(SettingsProblem.UnknownKey("ui.backgrund")), problems)
        assertEquals(themes.resolve("light", night = false).colors, themes.resolve("ocean", night = false).colors)
    }

    @Test
    fun `system follows the night, whatever has been imported`() {
        val themes = themes()
        themes.import("ocean", "base = \"dark\"\n")
        assertEquals("dark", themes.nameIn("system", night = true))
        assertEquals("light", themes.nameIn("system", night = false))
        assertEquals("ocean", themes.nameIn("ocean", night = false))
    }

    @Test
    fun `a copy of a bundled theme is the person's own, with the bundled file's text`() {
        val themes = themes()
        themes.import("dark copy", themes.text("dark"))
        assertTrue(themes.isImported("dark copy"))
        assertEquals(File("src/main/assets/themes/dark.toml").readText(), themes.text("dark copy"))
        assertEquals(themes.resolve("dark", night = false), themes.resolve("dark copy", night = false))
    }

    @Test
    fun `a copy of a theme of the person's own does not change with it`() {
        val themes = themes()
        themes.import("ocean", "[ui]\nbackground = \"#111\"\n")
        themes.import("ocean copy", themes.text("ocean"))
        themes.file("ocean").writeText("[ui]\nbackground = \"#222\"\n")
        themes.reread("ocean")
        assertEquals("#111", themes.resolve("ocean copy", night = false).colors["ui.background"])
    }

    @Test
    fun `saving an edited theme is read again, with what was passed over`() {
        val themes = themes()
        themes.import("ocean", "[ui]\nbackground = \"#111\"\n")
        themes.resolve("ocean", night = false)
        themes.file("ocean").writeText("[ui]\nbackground = \"#222\"\n")
        assertEquals(emptyList<SettingsProblem>(), themes.reread("ocean"))
        assertEquals("#222", themes.resolve("ocean", night = false).colors["ui.background"])
        // A typo is the same as it is on import: the whole base, and the key named.
        themes.file("ocean").writeText("[ui]\nbackgrund = \"#333\"\n")
        assertEquals(listOf(SettingsProblem.UnknownKey("ui.backgrund")), themes.reread("ocean"))
        assertEquals(themes.resolve("light", night = false).colors, themes.resolve("ocean", night = false).colors)
    }

    @Test
    fun `a deleted theme is no longer offered or kept`() {
        val themes = themes()
        themes.import("ocean", "base = \"dark\"\n")
        themes.resolve("ocean", night = false)
        themes.delete("ocean")
        assertEquals(listOf("system", "light", "dark"), themes.names)
        assertFalse(themes.isImported("ocean"))
        assertFalse(themes.file("ocean").exists())
        assertEquals(listOf("system", "light", "dark"), themes().names)
    }

    @Test
    fun `a bundled theme cannot be deleted`() {
        val themes = themes()
        for (name in listOf("light", "dark", "system", "ocean")) {
            assertThrows(name, IllegalArgumentException::class.java) { themes.delete(name) }
        }
        assertEquals(listOf("system", "light", "dark"), themes.names)
    }

    @Test
    fun `only a file of the person's own themes is one`() {
        val themes = themes()
        themes.import("ocean", "base = \"dark\"\n")
        assertEquals("ocean", themes.ownAt(themes.file("ocean").path))
        assertNull(themes.ownAt(themes.file("sea").path))
        assertNull(themes.ownAt(File(dir, "ocean.TOML").path))
        assertNull(themes.ownAt(File(dir.parentFile, "ocean.toml").path))
        assertNull(themes.ownAt(File(dir, "dark.toml").path))
    }
}
