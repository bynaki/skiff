package com.naki.skiff.code.data

import com.naki.skiff.data.store.AuthMethod
import com.naki.skiff.data.store.KnownHost
import com.naki.skiff.data.store.ServerProfile
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SkiffCodeStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val file by lazy { File(tmp.root, "skiffcode.json") }

    /** Runs [block] against a store on [file], then closes that DataStore so another may open it. */
    private suspend fun TestScope.withStore(block: suspend (SkiffCodeStore) -> Unit) {
        val job = Job()
        val dataStore = jsonDataStore(
            file = file,
            serializer = SkiffCodeData.serializer(),
            defaultValue = SkiffCodeData(),
            scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job),
        )
        block(SkiffCodeStore(dataStore))
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
    fun `an empty file reads as no profiles, no hosts and no recent files`() = runTest {
        withStore { store ->
            assertEquals(emptyList<ServerProfile>(), store.profiles.first())
            assertEquals(emptyList<RecentFile>(), store.recentFiles.first())
            assertNull(store.knownHost("192.0.2.10", 22))
        }
    }

    @Test
    fun `everything survives the store being opened again`() = runTest {
        withStore { store ->
            store.upsertProfile(home)
            store.rememberHost(KnownHost("192.0.2.10", 22, "ssh-ed25519", "SHA256:AAAA"))
            store.addRecentFile("skiffcode://alice@192.0.2.10/home/alice/a.md", 100)
        }
        withStore { store ->
            assertEquals(listOf(home), store.profiles.first())
            assertEquals("SHA256:AAAA", store.knownHost("192.0.2.10", 22)?.fingerprint)
            assertEquals(
                listOf(RecentFile("skiffcode://alice@192.0.2.10/home/alice/a.md", 100)),
                store.recentFiles.first(),
            )
        }
    }

    @Test
    fun `the password is kept as the ciphertext it was given`() = runTest {
        withStore { it.upsertProfile(home) }
        assertTrue(file.readText().contains("Y2lwaGVydGV4dA=="))
    }

    @Test
    fun `upsert replaces a profile with the same id and delete removes it`() = runTest {
        withStore { store ->
            store.upsertProfile(home)
            store.upsertProfile(home.copy(id = "p2", name = "other"))
            store.upsertProfile(home.copy(port = 2222))
            assertEquals(listOf(2222, 22), store.profiles.first().map { it.port })

            store.deleteProfile("p1")
            assertEquals(listOf("p2"), store.profiles.first().map { it.id })
        }
    }

    @Test
    fun `a host key is kept per host and port, and replaced or forgotten there`() = runTest {
        withStore { store ->
            store.rememberHost(KnownHost("192.0.2.10", 22, "ssh-ed25519", "SHA256:AAAA"))
            store.rememberHost(KnownHost("192.0.2.10", 2222, "ssh-ed25519", "SHA256:BBBB"))
            store.rememberHost(KnownHost("192.0.2.10", 22, "ssh-rsa", "SHA256:CCCC"))

            assertEquals("SHA256:CCCC", store.knownHost("192.0.2.10", 22)?.fingerprint)
            assertEquals("SHA256:BBBB", store.knownHost("192.0.2.10", 2222)?.fingerprint)

            store.forgetHost("192.0.2.10", 22)
            assertNull(store.knownHost("192.0.2.10", 22))
            assertEquals("SHA256:BBBB", store.knownHost("192.0.2.10", 2222)?.fingerprint)
        }
    }

    @Test
    fun `reopening a recent file moves it to the front instead of listing it twice`() = runTest {
        withStore { store ->
            store.addRecentFile("skiffcode:///a", 1)
            store.addRecentFile("skiffcode:///b", 2)
            store.addRecentFile("skiffcode:///a", 3)
            assertEquals(
                listOf(RecentFile("skiffcode:///a", 3), RecentFile("skiffcode:///b", 2)),
                store.recentFiles.first(),
            )

            store.removeRecentFile("skiffcode:///a")
            assertEquals(listOf("skiffcode:///b"), store.recentFiles.first().map { it.uri })
        }
    }

    @Test
    fun `recent files stop at the limit, dropping the oldest`() = runTest {
        withStore { store ->
            val limit = SkiffCodeStore.MAX_RECENT_FILES
            for (i in 0..limit) store.addRecentFile("skiffcode:///$i", i.toLong())
            val uris = store.recentFiles.first().map { it.uri }
            assertEquals(limit, uris.size)
            assertEquals("skiffcode:///$limit", uris.first())
            assertEquals("skiffcode:///1", uris.last())
        }
    }
}
