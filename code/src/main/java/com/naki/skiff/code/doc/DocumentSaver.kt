package com.naki.skiff.code.doc

import com.naki.skiff.fs.FileNode
import com.naki.skiff.fs.FileSystem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

sealed interface SaveResult {
    /** Written. The `stat` taken afterwards, which the next save compares against. */
    data class Saved(val size: Long, val modifiedEpochSeconds: Long) : SaveResult

    /**
     * The file on disk is no longer the one that was read, so nothing was written. [current] is
     * what `stat` says now, null when it has been deleted.
     */
    data class Conflict(val current: FileNode?) : SaveResult

    /**
     * The document's encoding cannot write [text], the first code point it refuses, found at
     * [index] in the buffer. Nothing was written.
     */
    data class Unencodable(val text: String, val index: Int) : SaveResult
}

/**
 * Writes a document back the way [TextLoader] read it: the same encoding, byte order mark and
 * line ending, with the buffer's own text — including whether it ends in a newline, which is the
 * user's to decide and not ours to restore.
 *
 * The write is a truncate in place. Writing a temporary file and renaming it over the original
 * would break ownership, hard links and symlinks, which is not a trade a text editor gets to make
 * on someone else's server.
 *
 * Whether the file is still the one that was read is decided by size and modification time, and
 * SFTP reports that time in whole seconds: a change made in the same second that leaves the size
 * alone is invisible here. [FileWatcher] is what shortens that window; this check is the one that
 * stops a save from landing on top of a file that moved on while it was open.
 */
class DocumentSaver(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {

    /**
     * [expectedSize] and [expectedModifiedEpochSeconds] are what the file looked like when it was
     * read ([LoadResult.Text]) or when it was last saved ([SaveResult.Saved]).
     */
    suspend fun save(
        fs: FileSystem,
        path: String,
        text: String,
        format: TextFormat,
        expectedSize: Long,
        expectedModifiedEpochSeconds: Long,
    ): SaveResult {
        // Encoded first: a buffer the file's encoding cannot hold must not reach the file at all.
        val bytes = when (val encoded = encode(text, format)) {
            is Encoded.Bytes -> encoded.value
            is Encoded.Refused -> return SaveResult.Unencodable(encoded.text, encoded.index)
        }

        val before = fs.stat(path)
        if (before == null ||
            before.size != expectedSize ||
            before.modifiedEpochSeconds != expectedModifiedEpochSeconds
        ) {
            return SaveResult.Conflict(before)
        }

        withContext(dispatcher) { fs.openWrite(path).buffer().use { it.write(bytes) } }

        // Our own write, stat'd back, so that watching the file does not read it as someone else's.
        val after = fs.stat(path)
        return SaveResult.Saved(
            size = after?.size ?: bytes.size.toLong(),
            modifiedEpochSeconds = after?.modifiedEpochSeconds ?: 0,
        )
    }

    private sealed interface Encoded {
        class Bytes(val value: ByteArray) : Encoded
        class Refused(val text: String, val index: Int) : Encoded
    }

    /**
     * Encodes strictly, for the reason [TextLoader] decodes strictly: a lenient encoder writes
     * `?` where a character does not fit, which is data loss the user never asked for and would
     * not see until later.
     */
    private fun encode(text: String, format: TextFormat): Encoded {
        val body = if (format.lineEnding == LineEnding.CRLF) text.replace("\n", "\r\n") else text
        val encoder = format.encoding.charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        val input = CharBuffer.wrap(body)
        val output = ByteBuffer.allocate(BOM.size + (body.length * encoder.maxBytesPerChar()).toInt() + 1)
        if (format.bom) output.put(BOM)
        // The loop form rather than encode(CharBuffer): it leaves the position on the character
        // that was refused, which is what names it for the user.
        val result = encoder.encode(input, output, true)
        if (result.isError) {
            val offending = body.substring(input.position(), input.position() + result.length())
            return Encoded.Refused(offending, input.position() - crlfPadding(body, input.position()))
        }
        // The buffer is sized by the encoder's own maximum, so this says the sizing was wrong
        // rather than letting a short write through as if it were the document.
        check(!result.isOverflow) { "encoder overflowed a buffer sized for its maximum" }
        encoder.flush(output)

        output.flip()
        return Encoded.Bytes(ByteArray(output.remaining()).also(output::get))
    }

    /** How far the CRLF conversion has pushed a position past the buffer's own. */
    private fun crlfPadding(body: String, position: Int): Int {
        var padding = 0
        for (i in 1 until position) if (body[i] == '\n' && body[i - 1] == '\r') padding++
        return padding
    }

    private companion object {
        val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
