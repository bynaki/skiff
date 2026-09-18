package com.naki.skiff

import androidx.datastore.core.DataStore
import com.naki.skiff.data.store.AuthMethod
import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.data.store.jsonDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonDataStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Serializable
    data class Data(val profiles: List<ServerProfile> = emptyList())

    private val file by lazy { File(tmp.root, "store.json") }

    private fun backups() = tmp.root.listFiles()!!.filter { it.name.startsWith("store.json.corrupt-") }

    /** Opens the store on [file], runs [block], then closes it so another may open the same file. */
    private suspend fun TestScope.withStore(block: suspend (DataStore<Data>) -> Unit) {
        val job = Job()
        val store = jsonDataStore(
            file = file,
            serializer = Data.serializer(),
            defaultValue = Data(),
            scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job),
            now = { 1_000 },
        )
        block(store)
        job.cancelAndJoin()
    }

    private val home = ServerProfile(
        id = "p1",
        name = "home-server",
        host = "192.0.2.10",
        username = "alice",
        auth = AuthMethod.Password("Y2lwaGVydGV4dA=="),
    )

    @Test
    fun `what is written is read back after reopening`() = runTest {
        withStore { it.updateData { Data(listOf(home)) } }
        withStore { assertEquals(Data(listOf(home)), it.data.first()) }
        assertEquals(emptyList<File>(), backups())
    }

    @Test
    fun `a missing file reads as the default without a backup`() = runTest {
        withStore { assertEquals(Data(), it.data.first()) }
        assertEquals(emptyList<File>(), backups())
    }

    @Test
    fun `keys this version does not know are ignored, not treated as corruption`() = runTest {
        file.writeText("""{"profiles":[],"addedLater":true}""")
        withStore { assertEquals(Data(), it.data.first()) }
        assertEquals(emptyList<File>(), backups())
    }

    @Test
    fun `a file that is not JSON is copied aside before the store starts over`() = runTest {
        file.writeText("{ not json")
        withStore { assertEquals(Data(), it.data.first()) }

        val backup = backups().single()
        assertEquals("store.json.corrupt-1000", backup.name)
        assertEquals("{ not json", backup.readText())
    }

    @Test
    fun `a renamed class in the JSON loses nothing, and the next write leaves the copy alone`() = runTest {
        // Valid JSON, but the auth type names a class that no longer exists: the realistic way
        // this file stops decoding.
        val stored = """
            {"profiles":[{"id":"p1","name":"home-server","host":"192.0.2.10","username":"alice",
            "auth":{"type":"com.naki.skiff.data.store.AuthMethod.OldPassword","encryptedPassword":"Y2lwaGVydGV4dA=="}}]}
        """.trimIndent()
        file.writeText(stored)

        withStore {
            assertEquals(Data(), it.data.first())
            it.updateData { Data(listOf(home)) }
        }

        assertEquals(stored, backups().single().readText())
        withStore { assertEquals(Data(listOf(home)), it.data.first()) }
    }
}
