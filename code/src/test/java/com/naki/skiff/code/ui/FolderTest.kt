package com.naki.skiff.code.ui

import com.naki.skiff.code.intent.OpenAt
import com.naki.skiff.code.intent.OpenRequest
import com.naki.skiff.code.intent.SkiffCodeUri
import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.fs.local.LocalFileSystem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FolderTest {

    private val dir: File = Files.createTempDirectory("folder").toFile()
    private val fs = LocalFileSystem("local")
    private val server = ServerProfile(id = "home", name = "home", host = "192.0.2.10", username = "someone")

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `lists the files beside this one, not directories, itself or what is already open`() = runTest {
        for (name in listOf("b.py", "a.py", "open.py", "self.py", ".env")) File(dir, name).writeText("")
        File(dir, "sub").mkdir()
        val folder = Folder.of(fs, OpenRequest.LocalPath("${dir.path}/self.py", OpenAt()))!!

        val names = folder.names { request -> keyOf(request) == keyOf(OpenRequest.LocalPath("${dir.path}/open.py", OpenAt())) }

        assertEquals(listOf(".env", "a.py", "b.py"), names)
    }

    @Test
    fun `a name opens the file beside this one, on the same server, with nothing from the first link`() {
        val folder = Folder.of(fs, OpenRequest.Remote(server, "/srv/app/main.py", OpenAt(line = 40)))!!

        assertEquals(OpenRequest.Remote(server, "/srv/app/util.py", OpenAt()), folder.request("util.py"))
    }

    @Test
    fun `nothing but a plain name is taken, so the page cannot reach outside the directory`() {
        val folder = Folder.of(fs, OpenRequest.LocalPath("/sdcard/notes/a.md", OpenAt()))!!

        for (name in listOf("", ".", "..", "../secret", "/etc/passwd", "sub/b.md", "a\u0000.md")) {
            assertNull(name, folder.request(name))
        }
    }

    @Test
    fun `a content document has no directory`() {
        assertNull(Folder.of(fs, OpenRequest.Content("content://docs/1", writable = false)))
    }

    @Test
    fun `the link made for a file beside finds the same profile again`() {
        val link = linkOf(OpenRequest.Remote(server, "/srv/app/한글 파일.py", OpenAt()))

        val target = SkiffCodeUri.parse(link).target as SkiffCodeUri.Remote
        assertEquals("home", target.alias)
        assertEquals("/srv/app/한글 파일.py", target.path)
        assertEquals(server, OpenRequest.findProfile(target, listOf(server)))
    }
}
