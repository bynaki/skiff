package com.naki.skiff.transfer

import com.naki.skiff.data.SourceRegistry
import com.naki.skiff.fs.FsPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * Serial transfer queue. One job at a time on purpose: two concurrent uploads over one SSH
 * connection contend for the same channel and finish later than if they had queued, and a
 * single active job keeps the progress notification honest.
 */
class TransferQueue(
    private val scope: CoroutineScope,
    private val registry: SourceRegistry,
    private val describeError: (Throwable) -> String,
) {

    private val engine = CopyEngine()
    private val _jobs = MutableStateFlow<List<TransferJob>>(emptyList())
    val jobs: StateFlow<List<TransferJob>> = _jobs.asStateFlow()

    private var runner: Job? = null
    private var activeJobId: String? = null

    val hasActiveWork: Boolean get() = _jobs.value.any { !it.finished }

    fun enqueue(job: TransferJob): String {
        _jobs.update { it + job }
        ensureRunning()
        return job.id
    }

    fun cancel(jobId: String) {
        if (jobId == activeJobId) {
            runner?.cancel()
        } else {
            update(jobId) { it.copy(status = TransferStatus.CANCELLED) }
        }
    }

    fun clearFinished() {
        _jobs.update { jobs -> jobs.filterNot { it.finished } }
    }

    private fun ensureRunning() {
        if (runner?.isActive == true) return
        runner = scope.launch {
            while (true) {
                val next = _jobs.value.firstOrNull { it.status == TransferStatus.QUEUED } ?: break
                activeJobId = next.id
                runJob(next)
                activeJobId = null
            }
        }
    }

    private suspend fun runJob(job: TransferJob) {
        update(job.id) { it.copy(status = TransferStatus.RUNNING) }
        try {
            val source = registry.get(job.sourceId)
            val destination = registry.get(job.destinationId)

            // Same filesystem + move: rename is instant, so try it before streaming bytes.
            if (job.move && job.sourceId == job.destinationId) {
                val renamed = job.sourcePaths.all { path ->
                    runCatching {
                        destination.rename(path, FsPath.join(job.destinationDir, FsPath.name(path)))
                    }.isSuccess
                }
                if (renamed) {
                    update(job.id) {
                        it.copy(status = TransferStatus.DONE, completedFiles = job.sourcePaths.size)
                    }
                    return
                }
                // Fall through to copy+delete: rename fails across mount points.
            }

            val plan = engine.plan(
                source = source,
                sourcePaths = job.sourcePaths,
                destination = destination,
                destinationDir = job.destinationDir,
                conflictPolicy = ConflictPolicy.KEEP_BOTH,
            )
            update(job.id) {
                it.copy(totalBytes = plan.totalBytes, totalFiles = plan.fileCount)
            }

            engine.execute(
                plan = plan,
                source = source,
                destination = destination,
                conflictPolicy = ConflictPolicy.KEEP_BOTH,
            ) { bytes, files, name ->
                update(job.id) {
                    it.copy(
                        transferredBytes = bytes,
                        completedFiles = files,
                        currentFileName = name,
                    )
                }
            }

            if (job.move) {
                for (path in job.sourcePaths) source.delete(path, recursive = true)
            }

            update(job.id) { it.copy(status = TransferStatus.DONE) }
        } catch (e: CancellationException) {
            update(job.id) { it.copy(status = TransferStatus.CANCELLED) }
            throw e
        } catch (e: Throwable) {
            update(job.id) {
                it.copy(status = TransferStatus.FAILED, error = describeError(e))
            }
        }
    }

    private fun update(id: String, transform: (TransferJob) -> TransferJob) {
        _jobs.update { jobs -> jobs.map { if (it.id == id) transform(it) else it } }
    }
}
