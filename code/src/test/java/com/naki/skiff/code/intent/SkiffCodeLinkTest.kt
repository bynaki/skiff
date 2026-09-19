package com.naki.skiff.code.intent

import com.naki.skiff.code.intent.SkiffCodeUri.Local
import com.naki.skiff.code.intent.SkiffCodeUri.Remote
import com.naki.skiff.link.SkiffCodeLink
import org.junit.Assert.assertEquals
import org.junit.Test

/** What Skiff builds with `:core`'s [SkiffCodeLink] has to come back out of [SkiffCodeUri] unchanged. */
class SkiffCodeLinkTest {

    @Test
    fun `a remote link survives the parser`() {
        val link = SkiffCodeLink.remote("alice", "192.0.2.10", 2222, "/home/alice/demo/a.md", "home-server")
        assertEquals("skiffcode://alice@192.0.2.10:2222/home/alice/demo/a.md?alias=home-server", link)
        assertEquals(Remote("alice", "192.0.2.10", 2222, "/home/alice/demo/a.md", "home-server"), SkiffCodeUri.parse(link).target)
    }

    @Test
    fun `characters that mean something in a link stay in the path`() {
        val path = "/home/alice/메모 #1?&=+%.md"
        val link = SkiffCodeLink.remote("alice", "192.0.2.10", 22, path, "집 서버 & 백업")
        assertEquals(Remote("alice", "192.0.2.10", 22, path, "집 서버 & 백업"), SkiffCodeUri.parse(link).target)
    }

    @Test
    fun `an unusual user name and an IPv6 host`() {
        val link = SkiffCodeLink.remote("a.b@corp", "2001:db8::1", 22, "/x", null)
        assertEquals(Remote("a.b@corp", "2001:db8::1", 22, "/x", null), SkiffCodeUri.parse(link).target)
    }

    @Test
    fun `a local link survives the parser`() {
        val path = "/storage/emulated/0/Download/새 폴더/a b.txt"
        val link = SkiffCodeLink.local(path)
        assertEquals(Local(path), SkiffCodeUri.parse(link).target)
    }
}
