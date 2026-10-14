package com.naki.skiff.code.data

import com.naki.skiff.data.store.KnownHost
import com.naki.skiff.data.store.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SameServerTest {

    private fun profile(id: String, host: String, port: Int = 22) = ServerProfile(id = id, name = id, host = host, port = port, username = "alice")

    private fun key(host: String, fingerprint: String, port: Int = 22) = KnownHost(host, port, "ssh-ed25519", fingerprint)

    // One machine by a domain, a tailnet name and a tailnet address.
    private val domain = profile("domain", "server.example")
    private val tailnetName = profile("tailnet-name", "server")
    private val tailnetAddress = profile("tailnet-address", "192.0.2.10")
    private val sameMachineKeys = listOf(
        key("server.example", "SHA256:AAAA"),
        key("server", "SHA256:AAAA"),
        key("192.0.2.10", "SHA256:AAAA"),
    )

    // Two containers from one image, its key made when the image was built.
    private val containerOne = profile("container-1", "192.0.2.20", 2201)
    private val containerTwo = profile("container-2", "192.0.2.20", 2202)
    private val containerKeys = listOf(key("192.0.2.20", "SHA256:CCCC", 2201), key("192.0.2.20", "SHA256:CCCC", 2202))

    private fun ids(profiles: List<ServerProfile>) = profiles.map { it.id }

    @Test
    fun `a profile no other shares a key with is a server of its own, with nothing to ask`() {
        val groups = ServerGroups(listOf(domain, containerOne), sameMachineKeys + containerKeys, emptyList())
        assertEquals(Server("key:SHA256:AAAA:domain", setOf("domain")), groups.serverOf(domain))
        assertEquals(emptyList<String>(), ids(groups.toAsk(domain)))
    }

    @Test
    fun `a second address with the same key is asked about, and is apart until it is answered`() {
        val groups = ServerGroups(listOf(domain, tailnetAddress), sameMachineKeys, emptyList())
        assertEquals(listOf("domain"), ids(groups.toAsk(tailnetAddress)))
        assertEquals(listOf("tailnet-address"), ids(groups.toAsk(domain)))
        assertNotEquals(groups.serverOf(domain).key, groups.serverOf(tailnetAddress).key)
    }

    @Test
    fun `answered the same, two addresses are one server named by the first, with nothing more to ask`() {
        val answers = listOf(SameMachineAnswer("tailnet-address", "domain", same = true))
        val groups = ServerGroups(listOf(domain, tailnetAddress), sameMachineKeys, answers)
        val server = Server("key:SHA256:AAAA:domain", setOf("domain", "tailnet-address"))
        assertEquals(server, groups.serverOf(domain))
        assertEquals(server, groups.serverOf(tailnetAddress))
        assertEquals(emptyList<String>(), ids(groups.toAsk(domain)))
        assertEquals(emptyList<String>(), ids(groups.toAsk(tailnetAddress)))
    }

    @Test
    fun `a third address is asked about the group once, by its first profile`() {
        val answers = listOf(SameMachineAnswer("domain", "tailnet-name", same = true))
        val groups = ServerGroups(listOf(domain, tailnetName, tailnetAddress), sameMachineKeys, answers)
        assertEquals(listOf("domain"), ids(groups.toAsk(tailnetAddress)))

        val all = ServerGroups(listOf(domain, tailnetName, tailnetAddress), sameMachineKeys, answers + SameMachineAnswer("tailnet-address", "domain", true))
        assertEquals(setOf("domain", "tailnet-name", "tailnet-address"), all.serverOf(tailnetName).profileIds)
        assertEquals("key:SHA256:AAAA:domain", all.serverOf(tailnetAddress).key)
    }

    @Test
    fun `an answer with any of a group counts for all of it`() {
        // The tailnet address was told apart from the tailnet name; the domain joined the name later.
        val answers = listOf(
            SameMachineAnswer("tailnet-address", "tailnet-name", same = false),
            SameMachineAnswer("domain", "tailnet-name", same = true),
        )
        val groups = ServerGroups(listOf(domain, tailnetName, tailnetAddress), sameMachineKeys, answers)
        assertEquals(emptyList<String>(), ids(groups.toAsk(tailnetAddress)))
        assertEquals(setOf("tailnet-address"), groups.serverOf(tailnetAddress).profileIds)
    }

    @Test
    fun `machines answered apart stay apart, and the same path on each is on another server`() {
        val answers = listOf(SameMachineAnswer("container-2", "container-1", same = false))
        val groups = ServerGroups(listOf(containerOne, containerTwo), containerKeys, answers)
        assertEquals(emptyList<String>(), ids(groups.toAsk(containerTwo)))
        assertEquals(Server("key:SHA256:CCCC:container-1", setOf("container-1")), groups.serverOf(containerOne))
        assertEquals(Server("key:SHA256:CCCC:container-2", setOf("container-2")), groups.serverOf(containerTwo))
    }

    @Test
    fun `only profiles with the same key are asked about or joined`() {
        // A stale "same" between profiles whose keys now differ joins nothing.
        val answers = listOf(SameMachineAnswer("domain", "container-1", same = true))
        val groups = ServerGroups(listOf(domain, containerOne, containerTwo, tailnetAddress), sameMachineKeys + containerKeys, answers)
        assertEquals(listOf("tailnet-address"), ids(groups.toAsk(domain)))
        assertEquals(listOf("container-2"), ids(groups.toAsk(containerOne)))
        assertEquals(setOf("domain"), groups.serverOf(domain).profileIds)
    }

    @Test
    fun `a profile without a key on record is a server of its own`() {
        val answers = listOf(SameMachineAnswer("domain", "tailnet-address", same = true))
        val groups = ServerGroups(listOf(domain, tailnetAddress), listOf(key("server.example", "SHA256:AAAA")), answers)
        assertEquals(Server("profile:tailnet-address", setOf("tailnet-address")), groups.serverOf(tailnetAddress))
        assertEquals(emptyList<String>(), ids(groups.toAsk(tailnetAddress)))
        assertEquals(emptyList<String>(), ids(groups.toAsk(domain)))
    }

    @Test
    fun `a key is found by the profile's host and port exactly`() {
        val groups = ServerGroups(listOf(domain), listOf(key("SERVER.example", "SHA256:AAAA"), key("server.example", "SHA256:BBBB", 2222)), emptyList())
        assertEquals("profile:domain", groups.serverOf(domain).key)
    }

    @Test
    fun `a profile that is not saved is a server of its own and is never asked about`() {
        val unsaved = profile("unsaved", "192.0.2.10")
        val groups = ServerGroups(listOf(domain), sameMachineKeys, emptyList())
        assertEquals(Server("profile:unsaved", setOf("unsaved")), groups.serverOf(unsaved))
        assertEquals(emptyList<String>(), ids(groups.toAsk(unsaved)))
        assertEquals(emptyList<String>(), ids(groups.toAsk(domain)))
    }

    @Test
    fun `a group keeps its name when a profile is added after it`() {
        val answers = listOf(SameMachineAnswer("tailnet-address", "domain", same = true))
        val before = ServerGroups(listOf(domain, tailnetAddress), sameMachineKeys, answers)
        val after = ServerGroups(
            listOf(domain, tailnetAddress, tailnetName),
            sameMachineKeys,
            answers + SameMachineAnswer("tailnet-name", "tailnet-address", same = true),
        )
        assertEquals(before.serverOf(tailnetAddress).key, after.serverOf(tailnetName).key)
        assertEquals(before.serverOf(domain).key, after.serverOf(domain).key)
    }

    @Test
    fun `an answer is about two profiles in either order`() {
        val answer = SameMachineAnswer("a", "b", same = true)
        assertEquals(true, answer.isAbout("b", "a"))
        assertEquals(false, answer.isAbout("a", "c"))
        assertEquals(true, answer.mentions("b"))
        assertEquals(false, answer.mentions("c"))
    }
}
