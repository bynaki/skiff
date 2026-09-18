package com.naki.skiff.code.intent

import com.naki.skiff.code.intent.OpenRequest.Companion.ACTION_EDIT
import com.naki.skiff.code.intent.OpenRequest.Companion.ACTION_VIEW
import com.naki.skiff.data.store.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRequestTest {

    private val home = ServerProfile(id = "home", name = "home-server", host = "192.0.2.10", username = "alice")
    private val homeBob = ServerProfile(id = "home-bob", name = "home-bob", host = "192.0.2.10", username = "bob")
    private val nas = ServerProfile(id = "nas", name = "nas", host = "NAS.Example", port = 2222, username = "alice")
    private val profiles = listOf(home, homeBob, nas)

    private fun open(data: String?, action: String? = ACTION_VIEW, with: List<ServerProfile> = profiles) =
        OpenRequest.of(action, data, with)

    @Test
    fun `a link to a known address opens that profile with the position it asked for`() {
        assertEquals(
            OpenRequest.Remote(home, "/home/alice/a.md", OpenAt(line = 42, layer = SkiffCodeUri.Layer.EDITOR)),
            open("skiffcode://alice@192.0.2.10/home/alice/a.md?line=42&layer=editor"),
        )
    }

    @Test
    fun `the user picks between two profiles on one address`() {
        assertEquals(homeBob, (open("skiffcode://bob@192.0.2.10/a") as OpenRequest.Remote).profile)
    }

    @Test
    fun `the alias wins over the address`() {
        // The link says bob, but its alias names alice's profile.
        assertEquals(home, (open("skiffcode://bob@192.0.2.10/a?alias=home-server") as OpenRequest.Remote).profile)
    }

    @Test
    fun `a matching alias keeps the profile's own host, whatever address the link carries`() {
        val request = open("skiffcode://mallory@198.51.100.7/etc/passwd?alias=home-server") as OpenRequest.Remote
        assertEquals("192.0.2.10", request.profile.host)
        assertEquals("/etc/passwd", request.path)
    }

    @Test
    fun `an alias that matches nothing falls back to the address`() {
        assertEquals(home, (open("skiffcode://alice@192.0.2.10/a?alias=gone") as OpenRequest.Remote).profile)
    }

    @Test
    fun `the host matches without case, and the port must match`() {
        assertEquals(nas, (open("skiffcode://alice@nas.example:2222/a") as OpenRequest.Remote).profile)
        assertTrue(open("skiffcode://alice@nas.example/a") is OpenRequest.UnknownServer)
    }

    @Test
    fun `without a user the address has to name one profile`() {
        assertEquals(nas, (open("skiffcode://nas.example:2222/a") as OpenRequest.Remote).profile)
        // alice and bob both have 192.0.2.10:22.
        assertTrue(open("skiffcode://192.0.2.10/a") is OpenRequest.UnknownServer)
    }

    @Test
    fun `an unknown server carries everything the confirmation needs`() {
        assertEquals(
            OpenRequest.UnknownServer("carol", "192.0.2.99", 22, "lab", "/srv/x.py", OpenAt(line = 3)),
            open("skiffcode://carol@192.0.2.99/srv/x.py?alias=lab&line=3"),
        )
    }

    @Test
    fun `a local link needs no profile`() {
        assertEquals(
            OpenRequest.LocalPath("/storage/emulated/0/Documents/메모.md", OpenAt()),
            open("skiffcode:///storage/emulated/0/Documents/%EB%A9%94%EB%AA%A8.md", with = emptyList()),
        )
    }

    @Test
    fun `a content uri is writable only through EDIT`() {
        val uri = "content://com.example.files/document/7"
        assertEquals(OpenRequest.Content(uri, writable = false), open(uri, ACTION_VIEW))
        assertEquals(OpenRequest.Content(uri, writable = true), open(uri, ACTION_EDIT))
    }

    @Test
    fun `a bad link is refused with the parser's reason`() {
        assertEquals(
            OpenRequest.Invalid("a password in the link is not accepted"),
            open("skiffcode://alice:hunter2@192.0.2.10/a"),
        )
    }

    @Test
    fun `other schemes, actions and a missing link are refused`() {
        assertTrue(open("file:///sdcard/a.md") is OpenRequest.Invalid)
        assertTrue(open("skiffcode:///a", action = "android.intent.action.SEND") is OpenRequest.Invalid)
        assertTrue(open(null) is OpenRequest.Invalid)
    }
}
