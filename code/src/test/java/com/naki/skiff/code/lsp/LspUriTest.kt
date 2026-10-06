package com.naki.skiff.code.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LspUriTest {

    @Test
    fun `a plain path is the path after file colon slash slash`() {
        assertEquals("file:///home/alice/demo/app.py", LspUri.of("/home/alice/demo/app.py"))
    }

    @Test
    fun `spaces, Korean and reserved characters are percent-encoded as UTF-8`() {
        assertEquals("file:///home/alice/my%20proj/%ED%95%9C%EA%B8%80%23%3F.py", LspUri.of("/home/alice/my proj/한글#?.py"))
    }

    @Test
    fun `a path comes back from its URI, however a server chose to encode it`() {
        for (path in listOf("/home/alice/demo/app.py", "/srv/my proj/한글#?.py", "/a/b:c@d+e.ts")) {
            assertEquals(path, LspUri.path(LspUri.of(path)))
        }
        // vscode-uri encodes `:` and leaves some others, which decode the same.
        assertEquals("/a/b:c.py", LspUri.path("file:///a/b%3Ac.py"))
        assertEquals("/a/b c.py", LspUri.path("file:///a/b%20c.py"))
    }

    @Test
    fun `anything but a file URI with an absolute path and no host is none`() {
        assertNull(LspUri.path("untitled:Untitled-1"))
        assertNull(LspUri.path("file://server/share/a.py"))
        assertNull(LspUri.path("https://example.com/a.py"))
        assertNull(LspUri.path("file:///a/b%2"))
        assertNull(LspUri.path("file:///a/b%zz"))
        assertNull(LspUri.path("file:///a/b%00c"))
    }
}
