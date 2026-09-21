package com.naki.skiff.code.ui

import com.naki.skiff.code.doc.LoadResult
import com.naki.skiff.code.doc.Stamped
import com.naki.skiff.code.doc.WatchedFile
import com.naki.skiff.code.intent.OpenAt
import com.naki.skiff.code.intent.OpenRequest
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
        val entry = docs.add("k", "a.py", "/sdcard/a.py", JSONObject(), watched, watcher = null)

        docs.close(entry.id)
        assertEquals(1, watched.closed)
    }

    @Test
    fun `closeAll releases every file`() {
        val docs = OpenDocuments()
        val watched = listOf(CountingWatchedFile(), CountingWatchedFile())
        watched.forEachIndexed { i, file -> docs.add("k$i", "a$i.py", "/sdcard/a$i.py", JSONObject(), file, null) }

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
    add(key, name, "/sdcard/$name", JSONObject().put("state", "text").put("name", name), CountingWatchedFile(), null)

private fun OpenDocuments.ids() = all.map { it.id }
