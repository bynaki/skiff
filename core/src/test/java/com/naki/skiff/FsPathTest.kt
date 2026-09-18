package com.naki.skiff

import com.naki.skiff.fs.FsPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FsPathTest {

    @Test
    fun `normalize collapses separators and dot segments`() {
        assertEquals("/a/b", FsPath.normalize("/a//b/"))
        assertEquals("/a/b", FsPath.normalize("/a/./b"))
        assertEquals("/a", FsPath.normalize("/a/b/.."))
        assertEquals("/", FsPath.normalize("/"))
        assertEquals("/", FsPath.normalize("//"))
        assertEquals("/", FsPath.normalize("/a/.."))
    }

    @Test
    fun `normalize cannot escape above root`() {
        assertEquals("/", FsPath.normalize("/../.."))
        assertEquals("/a", FsPath.normalize("/../a"))
    }

    @Test
    fun `normalize keeps unicode and spaces intact`() {
        assertEquals("/sdcard/문서/내 파일.txt", FsPath.normalize("/sdcard/문서//내 파일.txt"))
    }

    @Test
    fun `join treats an absolute child as a replacement`() {
        assertEquals("/a/b/c", FsPath.join("/a/b", "c"))
        assertEquals("/a/b/c", FsPath.join("/a/b/", "c"))
        assertEquals("/c", FsPath.join("/a/b", "/c"))
        assertEquals("/a/c", FsPath.join("/a/b", "../c"))
    }

    @Test
    fun `parent and name`() {
        assertEquals("/a", FsPath.parent("/a/b"))
        assertEquals("/", FsPath.parent("/a"))
        assertEquals("/", FsPath.parent("/"))
        assertEquals("b", FsPath.name("/a/b"))
        assertEquals("/", FsPath.name("/"))
    }

    @Test
    fun `crumbs walk the path`() {
        assertEquals(
            listOf("a" to "/a", "b" to "/a/b", "c" to "/a/b/c"),
            FsPath.crumbs("/a/b/c"),
        )
        assertEquals(emptyList<Pair<String, String>>(), FsPath.crumbs("/"))
    }

    @Test
    fun `extension ignores leading dot of dotfiles`() {
        assertEquals("gz", FsPath.extension("archive.tar.gz"))
        assertEquals("", FsPath.extension(".bashrc"))
        assertEquals("", FsPath.extension("Makefile"))
        assertEquals("kt", FsPath.extension("Main.KT"))
    }

    @Test
    fun `uniqueName inserts a counter before the extension`() {
        val taken = setOf("notes.md", "notes (1).md")
        assertEquals("notes (2).md", FsPath.uniqueName("notes.md", taken))
        assertEquals("fresh.md", FsPath.uniqueName("fresh.md", taken))
        assertEquals("folder (1)", FsPath.uniqueName("folder", setOf("folder")))
    }

    @Test
    fun `isAncestorOrSame guards moving a directory into itself`() {
        assertTrue(FsPath.isAncestorOrSame("/a", "/a"))
        assertTrue(FsPath.isAncestorOrSame("/a", "/a/b/c"))
        assertTrue(FsPath.isAncestorOrSame("/", "/a"))
        assertFalse(FsPath.isAncestorOrSame("/a", "/ab"))
        assertFalse(FsPath.isAncestorOrSame("/a/b", "/a"))
    }
}
