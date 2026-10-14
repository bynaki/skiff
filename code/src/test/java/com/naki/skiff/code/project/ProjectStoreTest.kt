package com.naki.skiff.code.project

import com.naki.skiff.code.data.SkiffCodeData
import com.naki.skiff.data.store.jsonDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val file by lazy { File(tmp.root, "skiffcode.json") }

    /** Runs [block] against a store on [file], then closes that DataStore so another may open it. */
    private suspend fun TestScope.withStore(block: suspend (ProjectStore) -> Unit) {
        val job = Job()
        val dataStore = jsonDataStore(
            file = file,
            serializer = SkiffCodeData.serializer(),
            defaultValue = SkiffCodeData(),
            scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job),
        )
        block(ProjectStore(dataStore))
        job.cancelAndJoin()
    }

    @Test
    fun `a project survives the store being opened again`() = runTest {
        lateinit var added: Project
        withStore { added = it.add("p1", "/home/alice/demo") }
        withStore { assertEquals(listOf(added), it.projects.first()) }
        assertEquals("demo", added.name)
    }

    @Test
    fun `adding the same root on the same server twice keeps one project`() = runTest {
        withStore { store ->
            val first = store.add("p1", "/home/alice/demo")
            val second = store.add("p1", "/home/alice/demo")

            assertEquals(first, second)
            assertEquals(1, store.projects.first().size)
        }
    }

    @Test
    fun `a folder is matched on its own server, under the root, by whole names`() = runTest {
        withStore { store ->
            val demo = store.add("p1", "/home/alice/demo")
            store.add("p2", "/home/alice/other")

            assertEquals(demo, store.containing(setOf("p1"), "/home/alice/demo"))
            assertEquals(demo, store.containing(setOf("p1"), "/home/alice/demo/src/deep"))
            assertNull(store.containing(setOf("p2"), "/home/alice/demo/src"))
            assertNull(store.containing(setOf("p1"), "/home/alice/demo2"))
            assertNull(store.containing(setOf("p1"), "/home/alice"))
        }
    }

    @Test
    fun `a removed project is gone, and the others stay`() = runTest {
        lateinit var kept: Project
        withStore { store ->
            val removed = store.add("p1", "/home/alice/demo")
            kept = store.add("p1", "/home/alice/other")
            store.remove(removed.id)

            assertNull(store.byId(removed.id))
            assertNull(store.containing(setOf("p1"), "/home/alice/demo/src"))
        }
        withStore { assertEquals(listOf(kept), it.projects.first()) }
    }

    @Test
    fun `nested roots match the deepest`() = runTest {
        withStore { store ->
            store.add("p1", "/home/alice")
            val inner = store.add("p1", "/home/alice/demo")

            assertEquals(inner, store.containing(setOf("p1"), "/home/alice/demo/src"))
            assertEquals("/home/alice", store.containing(setOf("p1"), "/home/alice/notes")?.root)
        }
    }

    @Test
    fun `a repository made a project through one address is found through another of its server`() = runTest {
        withStore { store ->
            val demo = store.add("domain", "/home/alice/demo")
            store.add("container", "/home/alice/other")

            assertEquals(demo, store.containing(setOf("domain", "tailnet"), "/home/alice/demo/src"))
            assertEquals(demo, store.containing(setOf("tailnet", "domain"), "/home/alice/demo"))
            // A profile of another server, or one not said to be the same machine, finds nothing.
            assertNull(store.containing(setOf("tailnet"), "/home/alice/demo/src"))
            assertNull(store.containing(setOf("container"), "/home/alice/demo/src"))
        }
    }

    @Test
    fun `of the server's profiles, nested roots match the deepest and one root the first made`() = runTest {
        withStore { store ->
            store.add("domain", "/home/alice")
            val inner = store.add("tailnet", "/home/alice/demo")
            // Made apart before the two were said to be one machine.
            val again = store.add("domain", "/home/alice/demo")

            val server = setOf("domain", "tailnet")
            assertEquals(inner, store.containing(server, "/home/alice/demo/src"))
            assertEquals(again, store.containing(setOf("domain"), "/home/alice/demo/src"))
            assertEquals("/home/alice", store.containing(server, "/home/alice/notes")?.root)
        }
    }
}
