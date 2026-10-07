package com.naki.skiff.code.ui

import com.naki.skiff.code.ui.OpenFileKind.Other
import com.naki.skiff.code.ui.OpenFileKind.Remote
import com.naki.skiff.code.ui.OpenFileKind.Settings
import com.naki.skiff.code.ui.OpenFileKind.Theme
import org.junit.Assert.assertEquals
import org.junit.Test

class ResetDataTest {

    private val files = listOf("a.py" to Remote, "settings.toml" to Settings, "ocean.toml" to Theme, "notes.md" to Other)

    private fun closing(vararg chosen: ResetItem) =
        ResetData.closing(ResetData.complete(chosen.toSet()), files) { it.second }.map { it.first }

    @Test
    fun `profiles take the projects with them, and nothing else takes anything`() {
        assertEquals(setOf(ResetItem.Profiles, ResetItem.Projects), ResetData.complete(setOf(ResetItem.Profiles)))
        for (item in ResetItem.entries - ResetItem.Profiles) {
            assertEquals(item.name, setOf(item), ResetData.complete(setOf(item)))
        }
    }

    @Test
    fun `open files closes every file`() {
        assertEquals(files.map { it.first }, closing(ResetItem.OpenFiles))
    }

    @Test
    fun `each reset closes the files it leaves nothing behind`() {
        assertEquals(listOf("a.py"), closing(ResetItem.Profiles))
        assertEquals(listOf("settings.toml"), closing(ResetItem.Settings))
        assertEquals(listOf("ocean.toml"), closing(ResetItem.Themes))
        assertEquals(listOf("a.py", "ocean.toml"), closing(ResetItem.Profiles, ResetItem.Themes))
    }

    @Test
    fun `projects, host keys and the lists close nothing`() {
        assertEquals(
            emptyList<String>(),
            closing(ResetItem.Projects, ResetItem.HostKeys, ResetItem.RecentFiles, ResetItem.PaletteRecents),
        )
    }
}
