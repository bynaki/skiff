package com.naki.skiff.transfer

import com.naki.skiff.fs.SourceId
import java.util.UUID

enum class TransferStatus { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

/** What to do when the destination already has a file with the same name. */
enum class ConflictPolicy { ASK, OVERWRITE, SKIP, KEEP_BOTH }

/** The user's answer to one conflict. [policy] is never [ConflictPolicy.ASK]. */
data class ConflictAnswer(val policy: ConflictPolicy, val applyToRest: Boolean)

data class TransferJob(
    val id: String = UUID.randomUUID().toString(),
    val sourceId: SourceId,
    val destinationId: SourceId,
    val sourcePaths: List<String>,
    val destinationDir: String,
    val move: Boolean,
    /** Becomes the user's answer once they apply one to the rest of the job. */
    val conflictPolicy: ConflictPolicy = ConflictPolicy.ASK,
    val status: TransferStatus = TransferStatus.QUEUED,
    /** Filled in once the tree has been walked; 0 until then. */
    val totalBytes: Long = 0,
    val transferredBytes: Long = 0,
    val totalFiles: Int = 0,
    val completedFiles: Int = 0,
    val currentFileName: String = "",
    val error: String? = null,
) {
    val label: String
        get() = sourcePaths.firstOrNull()?.substringAfterLast('/').orEmpty().let {
            if (sourcePaths.size > 1) "$it +${sourcePaths.size - 1}" else it
        }

    val fraction: Float
        get() = if (totalBytes <= 0) 0f else (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

    val finished: Boolean
        get() = status == TransferStatus.DONE ||
            status == TransferStatus.FAILED ||
            status == TransferStatus.CANCELLED
}
