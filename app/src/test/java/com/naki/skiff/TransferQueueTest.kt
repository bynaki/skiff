package com.naki.skiff

import com.naki.skiff.data.SourceRegistry
import com.naki.skiff.fs.SourceId
import com.naki.skiff.transfer.ConflictAnswer
import com.naki.skiff.transfer.ConflictPolicy
import com.naki.skiff.transfer.ConflictPrompter
import com.naki.skiff.transfer.TransferJob
import com.naki.skiff.transfer.TransferQueue
import com.naki.skiff.transfer.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The queue against the device's own filesystem, rooted in a temp directory. */
class TransferQueueTest {

    private val root: File = Files.createTempDirectory("skiff-queue").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registry = SourceRegistry("local") { error("no server here") }
    private val prompter = ConflictPrompter()
    private val queue = TransferQueue(scope, registry, prompter) { it.toString() }

    @After
    fun tearDown() {
        scope.cancel()
        registry.closeAll()
        root.deleteRecursively()
    }

    private fun file(relative: String, content: String? = null): File =
        File(root, relative).also { file ->
            if (content != null) {
                file.parentFile!!.mkdirs()
                file.writeText(content)
            }
        }

    @Test
    fun `a move on one filesystem renames the free names and asks about the taken one`() = runBlocking {
        file("src/free.txt", "free")
        file("src/taken.txt", "new")
        file("dst/taken.txt", "old")
        var asked = 0
        scope.launch {
            prompter.pending.filterNotNull().collect {
                asked++
                prompter.respond(ConflictAnswer(ConflictPolicy.SKIP, applyToRest = false))
            }
        }

        val id = queue.enqueue(
            TransferJob(
                sourceId = SourceId.Local,
                destinationId = SourceId.Local,
                sourcePaths = listOf(file("src/free.txt").path, file("src/taken.txt").path),
                destinationDir = file("dst").path,
                move = true,
            ),
        )
        val job = withTimeout(10_000) { queue.jobs.first { jobs -> jobs.single { it.id == id }.finished } }
            .single { it.id == id }

        // The rename of the first used to leave it missing for the copy that followed.
        assertEquals(job.error, TransferStatus.DONE, job.status)
        assertEquals(1, asked)
        assertEquals("free", file("dst/free.txt").readText())
        assertFalse(file("src/free.txt").exists())
        // Skipped in a move: the existing file is untouched and the source stays put.
        assertEquals("old", file("dst/taken.txt").readText())
        assertTrue(file("src/taken.txt").exists())
    }
}
