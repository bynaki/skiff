package com.naki.skiff.code.ui

import com.naki.skiff.code.data.SameMachineAnswer
import com.naki.skiff.code.data.ServerGroups
import com.naki.skiff.code.doc.LoadResult
import com.naki.skiff.code.doc.Stamped
import com.naki.skiff.code.doc.WatchedFile
import com.naki.skiff.code.intent.OpenAt
import com.naki.skiff.code.intent.OpenRequest
import com.naki.skiff.data.store.KnownHost
import com.naki.skiff.data.store.ServerProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenDocumentsTest {

    @Test
    fun `the file opened last is the one on screen, and the list keeps the order they were opened in`() {
        val docs = OpenDocuments()
        val first = docs.open("a.py")
        val second = docs.open("b.py")

        assertEquals(second.id, docs.active?.id)
        assertEquals(listOf(first.id, second.id), docs.ids())
        assertEquals(second.id, docs.listed().getInt("active"))
    }

    @Test
    fun `activating says whether it moved the screen`() {
        val docs = OpenDocuments()
        val first = docs.open("a.py")
        val second = docs.open("b.py")

        assertTrue(docs.activate(first.id))
        assertEquals(first.id, docs.active?.id)
        // Already showing, and a file that is not open at all.
        assertFalse(docs.activate(first.id))
        assertFalse(docs.activate(second.id + 100))
    }

    @Test
    fun `a link naming a file that is already open finds it rather than opening it twice`() {
        val docs = OpenDocuments()
        val request = OpenRequest.LocalPath("/sdcard/a.py", OpenAt())
        val entry = docs.open("a.py", keyOf(request))

        assertEquals(entry.id, docs.byKey(keyOf(OpenRequest.LocalPath("/sdcard/a.py", OpenAt(line = 4))))?.id)
        assertNull(docs.byKey(keyOf(OpenRequest.LocalPath("/sdcard/b.py", OpenAt()))))
    }

    @Test
    fun `the same path on two servers is two files`() {
        val one = ServerProfile(id = "one", name = "one", host = "192.0.2.10", username = "someone")
        val two = ServerProfile(id = "two", name = "two", host = "192.0.2.11", username = "someone")

        assertFalse(
            keyOf(OpenRequest.Remote(one, "/srv/app.py", OpenAt())) ==
                keyOf(OpenRequest.Remote(two, "/srv/app.py", OpenAt())),
        )
    }

    @Test
    fun `one server reached by two addresses is one file, once they are said to be one machine`() {
        // A domain and a tailnet address resolve to two profiles, but the machine answering is one.
        val byName = ServerProfile(id = "name", name = "name", host = "host.example", username = "someone")
        val byAddress = ServerProfile(id = "address", name = "someone@192.0.2.10", host = "192.0.2.10", username = "other")
        val keys = listOf(KnownHost("host.example", 22, "ssh-ed25519", "SHA256:same-key"), KnownHost("192.0.2.10", 22, "ssh-ed25519", "SHA256:same-key"))
        val servers = ServerGroups(listOf(byName, byAddress), keys, listOf(SameMachineAnswer("address", "name", same = true)))
        val first = OpenRequest.Remote(byName, "/srv/app.py", OpenAt(), servers.serverOf(byName).key)
        val second = OpenRequest.Remote(byAddress, "/srv/app.py", OpenAt(line = 7), servers.serverOf(byAddress).key)

        assertEquals(keyOf(first), keyOf(second))

        val docs = OpenDocuments()
        val entry = docs.open("app.py", keyOf(first))
        assertEquals(entry.id, docs.byKey(keyOf(second))?.id)
    }

    @Test
    fun `one host key on machines said to be apart, or not yet asked about, is two files`() {
        // Two containers from one image, which made its key when it was built.
        val one = ServerProfile(id = "one", name = "one", host = "192.0.2.20", port = 2201, username = "root")
        val two = ServerProfile(id = "two", name = "two", host = "192.0.2.20", port = 2202, username = "root")
        val keys = listOf(KnownHost("192.0.2.20", 2201, "ssh-ed25519", "SHA256:baked"), KnownHost("192.0.2.20", 2202, "ssh-ed25519", "SHA256:baked"))
        for (answers in listOf(emptyList(), listOf(SameMachineAnswer("two", "one", same = false)))) {
            val servers = ServerGroups(listOf(one, two), keys, answers)
            assertFalse(
                keyOf(OpenRequest.Remote(one, "/root/app/main.py", OpenAt(), servers.serverOf(one).key)) ==
                    keyOf(OpenRequest.Remote(two, "/root/app/main.py", OpenAt(), servers.serverOf(two).key)),
            )
        }
    }

    @Test
    fun `two servers are two files, whatever the profile`() {
        val server = ServerProfile(id = "one", name = "one", host = "192.0.2.10", username = "someone")

        assertFalse(
            keyOf(OpenRequest.Remote(server, "/srv/app.py", OpenAt(), server = "key:SHA256:one-key:one")) ==
                keyOf(OpenRequest.Remote(server, "/srv/app.py", OpenAt(), server = "key:SHA256:other-key:one")),
        )
    }

    @Test
    fun `without a server named the profile says which server it is`() {
        val server = ServerProfile(id = "one", name = "one", host = "192.0.2.10", username = "someone")

        assertEquals("remote:profile:one:/srv/app.py", keyOf(OpenRequest.Remote(server, "/srv/app.py", OpenAt())))
    }

    @Test
    fun `closing the file on screen moves to the one after it, and to the one before when it was last`() {
        val docs = OpenDocuments()
        val first = docs.open("a.py")
        val second = docs.open("b.py")
        val third = docs.open("c.py")

        docs.activate(second.id)
        docs.close(second.id)
        assertEquals(third.id, docs.active?.id)
        assertEquals(listOf(first.id, third.id), docs.ids())

        docs.close(third.id)
        assertEquals(first.id, docs.active?.id)
    }

    @Test
    fun `closing a file that is not on screen leaves the screen where it is`() {
        val docs = OpenDocuments()
        val first = docs.open("a.py")
        val second = docs.open("b.py")

        docs.close(first.id)
        assertEquals(second.id, docs.active?.id)
    }

    @Test
    fun `closing the only file leaves nothing open`() {
        val docs = OpenDocuments()
        docs.close(docs.open("a.py").id)

        assertTrue(docs.all.isEmpty())
        assertNull(docs.active)
        assertEquals(JSONObject.NULL, docs.listed().get("active"))
    }

    @Test
    fun `closing a file releases what its watch held open`() {
        val docs = OpenDocuments()
        val watched = CountingWatchedFile()
        val entry = docs.add("k", "a.py", "/sdcard/a.py", JSONObject(), watched, watcher = null, save = null, base = null, folder = null)

        docs.close(entry.id)
        assertEquals(1, watched.closed)
    }

    @Test
    fun `closeAll releases every file`() {
        val docs = OpenDocuments()
        val watched = listOf(CountingWatchedFile(), CountingWatchedFile())
        watched.forEachIndexed { i, file -> docs.add("k$i", "a$i.py", "/sdcard/a$i.py", JSONObject(), file, null, null, null, null) }

        docs.closeAll()
        assertEquals(listOf(1, 1), watched.map { it.closed })
        assertNull(docs.active)
    }
}

private class CountingWatchedFile : WatchedFile {
    var closed = 0
        private set

    override suspend fun stamp(): Stamped = Stamped.Unknown

    override suspend fun read(): LoadResult = LoadResult.Binary

    override fun close() {
        closed++
    }
}

private fun OpenDocuments.open(name: String, key: String = "key:$name") =
    add(key, name, "/sdcard/$name", JSONObject().put("state", "text").put("name", name), CountingWatchedFile(), null, null, null, null)

private fun OpenDocuments.ids() = all.map { it.id }
