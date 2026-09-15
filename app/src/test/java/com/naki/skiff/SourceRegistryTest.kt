package com.naki.skiff

import com.naki.skiff.data.SourceRegistry
import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.fs.SourceId
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.PublicKey

/**
 * The picker used to build its list from the store while this registry built its profiles from
 * the same flow separately, so the two could disagree for an update. These tests hold the
 * contract that replaced that: what [SourceRegistry.sources] offers, [SourceRegistry.get]
 * resolves.
 */
class SourceRegistryTest {

    private val registry = SourceRegistry(LOCAL_NAME) { NeverAsked }

    @After
    fun tearDown() = registry.closeAll()

    @Test
    fun `the local source is offered before any profile arrives`() {
        assertEquals(listOf(SourceId.Local), registry.sources.value.map { it.id })
        assertEquals(LOCAL_NAME, registry.sources.value.single().name)
    }

    @Test
    fun `a saved profile reaches the picker without a further call`() {
        registry.updateProfiles(listOf(NAS))

        assertEquals(
            listOf(SourceId.Local, SourceId.Remote(NAS.id)),
            registry.sources.value.map { it.id },
        )
    }

    @Test
    fun `the picker emits the change to whoever is collecting`() = runTest {
        val seen = mutableListOf<List<SourceId>>()
        backgroundScope.launch {
            registry.sources.collect { seen += it.map { source -> source.id } }
        }
        runCurrent()

        registry.updateProfiles(listOf(NAS))
        runCurrent()

        assertEquals(
            listOf(
                listOf(SourceId.Local),
                listOf(SourceId.Local, SourceId.Remote(NAS.id)),
            ),
            seen,
        )
    }

    @Test
    fun `every source the picker offers can be resolved`() {
        registry.updateProfiles(listOf(NAS))

        // The reason the list is published from here: a descriptor this registry does not know
        // is not a stale menu entry, it is an error() the moment the user picks it.
        registry.sources.value.forEach { registry.get(it.id) }
    }

    @Test
    fun `a source stays the same filesystem across lookups`() {
        registry.updateProfiles(listOf(NAS))

        assertSame(registry.get(SourceId.Local), registry.get(SourceId.Local))
        val remote = SourceId.Remote(NAS.id)
        assertSame(registry.get(remote), registry.get(remote))
    }

    @Test
    fun `a deleted profile leaves the picker and stops resolving`() {
        registry.updateProfiles(listOf(NAS))
        registry.get(SourceId.Remote(NAS.id))

        registry.updateProfiles(emptyList())

        assertEquals(listOf(SourceId.Local), registry.sources.value.map { it.id })
        assertThrows(IllegalStateException::class.java) {
            registry.get(SourceId.Remote(NAS.id))
        }
    }

    private companion object {
        const val LOCAL_NAME = "This device"

        val NAS = ServerProfile(
            id = "11111111-2222-3333-4444-555555555555",
            name = "nas",
            // Reserved for documentation (RFC 5737); nothing here resolves or connects.
            host = "192.0.2.10",
            username = "tester",
        )

        /** No connection is opened in these tests, so the verifier is never consulted. */
        val NeverAsked = object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean =
                error("host key verification is not reachable without connecting")

            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> =
                emptyList()
        }
    }
}
