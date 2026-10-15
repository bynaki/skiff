package com.naki.skiff.code.intent

import com.naki.skiff.code.intent.MarkdownLink.Base
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownLinkTest {

    private val project = Base.Path("/home/alice/demo/docs/guide.md", root = "/home/alice/demo")
    private val single = Base.Path("/home/alice/notes/today.md", root = null)
    private val web = Base.Web("https://raw.githubusercontent.com/o/r/main/docs/guide.md")

    private fun file(path: String, fragment: String? = null) = LinkTarget.File(path, fragment)

    @Test
    fun `a relative link is taken from the document's folder`() {
        assertEquals(file("/home/alice/demo/docs/a.md"), MarkdownLink.resolve(project, "a.md"))
        assertEquals(file("/home/alice/demo/docs/a.md"), MarkdownLink.resolve(project, "./a.md"))
        assertEquals(file("/home/alice/demo/docs/sub/b.md"), MarkdownLink.resolve(project, "sub/b.md"))
    }

    @Test
    fun `a link may leave the project and the document's folder without being refused`() {
        assertEquals(file("/home/alice/demo/README.md"), MarkdownLink.resolve(project, "../README.md"))
        assertEquals(file("/home/alice/other/x.md"), MarkdownLink.resolve(project, "../../other/x.md"))
        assertEquals(file("/home/alice/x.py"), MarkdownLink.resolve(single, "../x.py"))
    }

    @Test
    fun `a link from the root is the project's root, or the server's for a single file`() {
        assertEquals(file("/home/alice/demo/docs/a.md"), MarkdownLink.resolve(project, "/docs/a.md"))
        assertEquals(file("/etc/hosts"), MarkdownLink.resolve(single, "/etc/hosts"))
    }

    @Test
    fun `a dot-dot that climbs above the root is refused rather than clamped`() {
        assertEquals(LinkTarget.Ignore, MarkdownLink.resolve(single, "../../../../x.md"))
        assertEquals(LinkTarget.Ignore, MarkdownLink.resolve(single, "/../x.md"))
        // From the project's root, climbing out of the project is still on the server.
        assertEquals(file("/home/alice/x.md"), MarkdownLink.resolve(project, "/../x.md"))
    }

    @Test
    fun `escapes are decoded, the query dropped and the fragment kept`() {
        assertEquals(file("/home/alice/demo/docs/my doc.md"), MarkdownLink.resolve(project, "my%20doc.md"))
        assertEquals(file("/home/alice/demo/docs/한글.md"), MarkdownLink.resolve(project, "%ED%95%9C%EA%B8%80.md"))
        assertEquals(file("/home/alice/demo/docs/a.md"), MarkdownLink.resolve(project, "a.md?plain=1"))
        assertEquals(file("/home/alice/demo/docs/a.md", "설치-방법"), MarkdownLink.resolve(project, "a.md#%EC%84%A4%EC%B9%98-%EB%B0%A9%EB%B2%95"))
        assertEquals(file("/home/alice/demo/docs/a.md"), MarkdownLink.resolve(project, "a.md#"))
    }

    @Test
    fun `a NUL or a broken escape opens nothing`() {
        assertEquals(LinkTarget.Ignore, MarkdownLink.resolve(project, "a%00.md"))
        assertEquals(LinkTarget.Ignore, MarkdownLink.resolve(project, "a%zz.md"))
        assertEquals(LinkTarget.Ignore, MarkdownLink.resolve(project, "a%2"))
    }

    @Test
    fun `a bare fragment is the page's to follow`() {
        assertEquals(LinkTarget.Ignore, MarkdownLink.resolve(project, "#usage"))
        assertEquals(LinkTarget.Ignore, MarkdownLink.resolve(project, ""))
        assertEquals(LinkTarget.Ignore, MarkdownLink.resolve(project, "?q=1"))
    }

    @Test
    fun `a content document has no folder for a path to be in`() {
        assertEquals(LinkTarget.Ignore, MarkdownLink.resolve(Base.NoFolder, "a.md"))
        assertEquals(LinkTarget.Ignore, MarkdownLink.resolve(Base.NoFolder, "/a.md"))
        assertEquals(LinkTarget.External("https://example.com/"), MarkdownLink.resolve(Base.NoFolder, "https://example.com/"))
    }

    @Test
    fun `an https markdown file opens in the app`() {
        assertEquals(LinkTarget.Web("https://example.com/a/README.md", null), MarkdownLink.resolve(project, "https://example.com/a/README.md"))
        assertEquals(LinkTarget.Web("https://example.com/NOTES.MD", null), MarkdownLink.resolve(project, "https://example.com/NOTES.MD"))
        assertEquals(LinkTarget.Web("https://example.com/a.md?v=2", "usage"), MarkdownLink.resolve(project, "https://example.com/a.md?v=2#usage"))
    }

    @Test
    fun `a GitHub blob page is read from its raw file`() {
        assertEquals(
            LinkTarget.Web("https://raw.githubusercontent.com/o/r/main/docs/guide.md", "install"),
            MarkdownLink.resolve(project, "https://github.com/o/r/blob/main/docs/guide.md?plain=1#install"),
        )
        // Not a blob: the repository's own page, a tree.
        assertEquals(LinkTarget.External("https://github.com/o/r"), MarkdownLink.resolve(project, "https://github.com/o/r"))
        assertEquals(LinkTarget.External("https://github.com/o/r/tree/main/docs"), MarkdownLink.resolve(project, "https://github.com/o/r/tree/main/docs"))
    }

    @Test
    fun `other web links, plain http and mail go to the default app`() {
        assertEquals(LinkTarget.External("https://example.com/page"), MarkdownLink.resolve(project, "https://example.com/page"))
        assertEquals(LinkTarget.External("http://example.com/a.md"), MarkdownLink.resolve(project, "http://example.com/a.md"))
        assertEquals(LinkTarget.External("mailto:alice@example.com"), MarkdownLink.resolve(project, "mailto:alice@example.com"))
        assertEquals(LinkTarget.External("HTTPS://example.com/"), MarkdownLink.resolve(project, "HTTPS://example.com/"))
    }

    @Test
    fun `any other scheme is dropped`() {
        for (href in listOf("javascript:alert(1)", "file:///etc/passwd", "intent://x#Intent;end", "content://a/b", "skiffcode://alice@192.0.2.10/x", "//example.com/a.md")) {
            assertEquals(href, LinkTarget.Ignore, MarkdownLink.resolve(project, href))
        }
    }

    @Test
    fun `a link in a web document is taken from its address`() {
        assertEquals(LinkTarget.Web("https://raw.githubusercontent.com/o/r/main/docs/other.md", null), MarkdownLink.resolve(web, "other.md"))
        assertEquals(LinkTarget.Web("https://raw.githubusercontent.com/o/r/main/README.md", "top"), MarkdownLink.resolve(web, "../README.md#top"))
        assertEquals(LinkTarget.External("https://raw.githubusercontent.com/o/r/main/docs/main.py"), MarkdownLink.resolve(web, "main.py"))
        assertEquals(LinkTarget.Web("https://example.com/x.md", null), MarkdownLink.resolve(web, "//example.com/x.md"))
        assertEquals(LinkTarget.Ignore, MarkdownLink.resolve(web, "#top"))
    }
}
