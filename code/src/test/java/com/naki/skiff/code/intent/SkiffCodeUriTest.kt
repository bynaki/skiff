package com.naki.skiff.code.intent

import com.naki.skiff.code.intent.SkiffCodeUri.Layer
import com.naki.skiff.code.intent.SkiffCodeUri.Local
import com.naki.skiff.code.intent.SkiffCodeUri.Remote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SkiffCodeUriTest {

    private fun remote(uri: String) = SkiffCodeUri.parse(uri).target as Remote

    private fun rejects(uri: String, messagePart: String) {
        val e = assertThrows(IllegalArgumentException::class.java) { SkiffCodeUri.parse(uri) }
        assertTrue("\"${e.message}\" should mention \"$messagePart\"", e.message!!.contains(messagePart))
    }

    @Test
    fun `the full remote form`() {
        val parsed = SkiffCodeUri.parse(
            "skiffcode://alice@192.0.2.10:2222/home/alice/demo/a.md?alias=home-server&line=42&col=7&layer=viewer",
        )
        assertEquals(
            SkiffCodeUri(
                target = Remote("alice", "192.0.2.10", 2222, "/home/alice/demo/a.md", "home-server"),
                line = 42,
                col = 7,
                layer = Layer.VIEWER,
            ),
            parsed,
        )
    }

    @Test
    fun `the port defaults to 22 and the query is optional`() {
        val parsed = SkiffCodeUri.parse("skiffcode://alice@192.0.2.10/etc/hosts")
        assertEquals(SkiffCodeUri(Remote("alice", "192.0.2.10", 22, "/etc/hosts", null)), parsed)
    }

    @Test
    fun `the local form has an empty authority`() {
        val parsed = SkiffCodeUri.parse("skiffcode:///storage/emulated/0/Documents/a.md?line=3")
        assertEquals(SkiffCodeUri(Local("/storage/emulated/0/Documents/a.md"), line = 3), parsed)
    }

    @Test
    fun `the path is percent decoded as UTF-8`() {
        // "메모 #1+2.md", with the space, the hash and the Korean all encoded.
        val target = remote("skiffcode://alice@192.0.2.10/home/alice/%EB%A9%94%EB%AA%A8%20%231+2.md")
        assertEquals("/home/alice/메모 #1+2.md", target.path)
    }

    @Test
    fun `characters that arrive unencoded are taken as they are`() {
        val target = remote("skiffcode://alice@192.0.2.10/home/alice/메모.md")
        assertEquals("/home/alice/메모.md", target.path)
    }

    @Test
    fun `an encoded slash stays in the path`() {
        assertEquals("/a/b", SkiffCodeUri.parse("skiffcode:///a%2Fb").target.path)
    }

    @Test
    fun `the user and the alias are percent decoded`() {
        val target = remote("skiffcode://first%2Elast@192.0.2.10/x?alias=%ED%99%88%20%EC%84%9C%EB%B2%84")
        assertEquals("first.last", target.user)
        assertEquals("홈 서버", target.alias)
    }

    @Test
    fun `a fragment is dropped`() {
        assertEquals("/a.md", SkiffCodeUri.parse("skiffcode:///a.md#heading").target.path)
    }

    @Test
    fun `an IPv6 host drops its brackets and keeps its port`() {
        val target = remote("skiffcode://alice@[2001:DB8::10]:2222/home/alice/a.md")
        assertEquals("2001:db8::10", target.host)
        assertEquals(2222, target.port)
        assertEquals("/home/alice/a.md", target.path)
    }

    @Test
    fun `an IPv6 host without a port gets the default`() {
        assertEquals(22, remote("skiffcode://alice@[2001:db8::10]/a").port)
    }

    @Test
    fun `an unclosed or bogus IPv6 host is refused`() {
        rejects("skiffcode://alice@[2001:db8::10/a", "IPv6")
        rejects("skiffcode://alice@[192.0.2.10]/a", "IPv6")
        rejects("skiffcode://alice@[2001:db8::10]x/a", "IPv6")
    }

    @Test
    fun `the host is lowercased`() {
        assertEquals("nas.example", remote("skiffcode://alice@NAS.Example/a").host)
    }

    @Test
    fun `a password in the link is refused`() {
        rejects("skiffcode://alice:hunter2@192.0.2.10/a", "password")
        rejects("skiffcode://alice:@192.0.2.10/a", "password")
    }

    @Test
    fun `a link without a user can still name its server by alias`() {
        val target = remote("skiffcode://192.0.2.10/a?alias=home-server")
        assertNull(target.user)
        assertEquals("home-server", target.alias)
    }

    @Test
    fun `both the alias and the address reach the caller, which decides between them`() {
        // The alias wins over (user, host, port) when profiles are looked up, so the parser must
        // not drop either one: an alias that matches nothing falls back to the address.
        val target = remote("skiffcode://alice@192.0.2.10:2222/a?alias=home-server")
        assertEquals("home-server", target.alias)
        assertEquals(Triple("alice", "192.0.2.10", 2222), Triple(target.user, target.host, target.port))
    }

    @Test
    fun `an empty alias counts as none`() {
        assertNull(remote("skiffcode://alice@192.0.2.10/a?alias=").alias)
    }

    @Test
    fun `the first of a repeated key wins and unknown keys are ignored`() {
        val parsed = SkiffCodeUri.parse("skiffcode:///a?line=1&line=9&future=x")
        assertEquals(1, parsed.line)
    }

    @Test
    fun `the scheme is matched without case and the layer too`() {
        val parsed = SkiffCodeUri.parse("SkiffCode:///a?layer=Diff")
        assertEquals(Layer.DIFF, parsed.layer)
    }

    @Test
    fun `another scheme is refused`() {
        rejects("https://192.0.2.10/a", "skiffcode")
        rejects("skiffcode:/a", "skiffcode")
    }

    @Test
    fun `a link without a path is refused`() {
        rejects("skiffcode://alice@192.0.2.10", "path")
        rejects("skiffcode://alice@192.0.2.10/", "path")
        rejects("skiffcode:///", "path")
        rejects("skiffcode://alice@192.0.2.10?line=1", "path")
    }

    @Test
    fun `an empty user or host is refused`() {
        rejects("skiffcode://@192.0.2.10/a", "user")
        rejects("skiffcode://alice@/a", "host")
        rejects("skiffcode://alice@:22/a", "host")
    }

    @Test
    fun `a port outside 1 to 65535 or not a number is refused`() {
        rejects("skiffcode://alice@192.0.2.10:0/a", "port")
        rejects("skiffcode://alice@192.0.2.10:65536/a", "port")
        rejects("skiffcode://alice@192.0.2.10:ssh/a", "port")
        rejects("skiffcode://alice@192.0.2.10:/a", "port")
        rejects("skiffcode://alice@192.0.2.10:+22/a", "port")
    }

    @Test
    fun `line and col must be positive numbers`() {
        rejects("skiffcode:///a?line=0", "line")
        rejects("skiffcode:///a?line=-3", "line")
        rejects("skiffcode:///a?line=abc", "line")
        rejects("skiffcode:///a?col=", "col")
        rejects("skiffcode:///a?line=99999999999", "line")
    }

    @Test
    fun `an unknown layer is refused`() {
        rejects("skiffcode:///a?layer=preview", "layer")
    }

    @Test
    fun `broken percent encoding is refused`() {
        rejects("skiffcode:///a%2", "percent")
        rejects("skiffcode:///a%zz", "percent")
        rejects("skiffcode:///a%+1b", "percent")
    }

    @Test
    fun `bytes that are not UTF-8 are refused`() {
        // "메" in EUC-KR.
        rejects("skiffcode:///%B8%DE.md", "UTF-8")
    }

    @Test
    fun `a NUL in the path is refused`() {
        rejects("skiffcode:///a%00.md", "NUL")
    }
}
