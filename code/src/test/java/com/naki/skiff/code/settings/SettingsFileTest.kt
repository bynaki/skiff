package com.naki.skiff.code.settings

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsFileTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `reset puts the template back and reads it as every default`() = runTest {
        val file = File(tmp.root, "settings.toml")
        val settings = SettingsFile(file) { SettingsToml.THEMES }
        settings.import("[editor]\ntheme = \"dark\"\nfont_size = 20\n")
        assertEquals(20, settings.current().settings.editor.fontSize)

        val read = settings.reset()
        assertEquals(SettingsToml.TEMPLATE, file.readText())
        assertEquals(Settings(), read.settings)
        assertEquals(emptyList<SettingsProblem>(), read.problems)
    }
}
