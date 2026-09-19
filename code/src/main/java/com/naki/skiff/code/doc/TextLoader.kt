package com.naki.skiff.code.doc

import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.Source
import okio.buffer
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** How a file was written, kept so that saving can write it back the same way. */
data class TextFormat(
    val encoding: TextEncoding,
    /** A UTF-8 byte order mark was stripped from the front. */
    val bom: Boolean,
    val lineEnding: LineEnding,
    val finalNewline: Boolean,
)

enum class TextEncoding(val charset: Charset) {
    UTF_8(Charsets.UTF_8),
    EUC_KR(Charset.forName("EUC-KR")),
}

enum class LineEnding(val separator: String) { LF("\n"), CRLF("\r\n") }

sealed interface LoadResult {
    /**
     * [text] has its line endings turned into `\n`, which is what CodeMirror holds. [size] and
     * [modifiedEpochSeconds] are the `stat` taken before reading, for the saver to compare against.
     */
    data class Text(
        val text: String,
        val format: TextFormat,
        val size: Long,
        val modifiedEpochSeconds: Long,
    ) : LoadResult

    data class TooLarge(val size: Long, val limit: Long) : LoadResult

    /** A NUL byte in the first [BINARY_PROBE] bytes. */
    data object Binary : LoadResult

    /** Neither UTF-8 nor EUC-KR decodes it without loss. */
    data object UnknownEncoding : LoadResult
}

/**
 * Reads a file as text, or says why it will not: too large, binary, or in an encoding it cannot
 * name. The extension is never consulted — a `.txt` full of NULs is binary, a `Makefile` is text.
 *
 * Decoding is strict on purpose. A lenient decode swaps undecodable bytes for U+FFFD, and saving
 * that text back would overwrite the original bytes with it.
 */
class TextLoader(
    private val sizeLimit: Long = DEFAULT_SIZE_LIMIT,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun load(fs: FileSystem, path: String): LoadResult {
        val node = fs.stat(path) ?: throw FsError.NotFound(path)
        if (node.size > sizeLimit) return LoadResult.TooLarge(node.size, sizeLimit)
        val bytes = withContext(dispatcher) { fs.openRead(path).readAtMost(sizeLimit + 1) }
        // The file grew between stat and read; the limit is about what we hold, not what stat said.
        if (bytes.size > sizeLimit) return LoadResult.TooLarge(bytes.size.toLong(), sizeLimit)
        return decode(bytes, node.size, node.modifiedEpochSeconds)
    }

    /**
     * A stream with no `stat` behind it, such as a `content://` document. The size is what was
     * read, the modification time is unknown (0), and a [LoadResult.TooLarge] only knows that the
     * stream went past the limit.
     */
    suspend fun load(source: Source): LoadResult {
        val bytes = withContext(dispatcher) { source.readAtMost(sizeLimit + 1) }
        if (bytes.size > sizeLimit) return LoadResult.TooLarge(bytes.size.toLong(), sizeLimit)
        return decode(bytes, bytes.size.toLong(), 0)
    }

    fun decode(bytes: ByteArray, size: Long, modifiedEpochSeconds: Long): LoadResult {
        val probe = minOf(bytes.size, BINARY_PROBE)
        for (i in 0 until probe) if (bytes[i] == 0.toByte()) return LoadResult.Binary

        val bom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val (encoding, raw) = if (bom) {
            TextEncoding.UTF_8 to (strictDecode(bytes, 3, TextEncoding.UTF_8) ?: return LoadResult.UnknownEncoding)
        } else {
            TextEncoding.entries.firstNotNullOfOrNull { encoding -> strictDecode(bytes, 0, encoding)?.let { encoding to it } }
                ?: return LoadResult.UnknownEncoding
        }

        // The first line ending decides, as most editors do. A file mixing both comes back with
        // every line ending in that one.
        val firstNewline = raw.indexOf('\n')
        val lineEnding = if (firstNewline > 0 && raw[firstNewline - 1] == '\r') LineEnding.CRLF else LineEnding.LF
        val text = raw.replace("\r\n", "\n")
        val format = TextFormat(encoding, bom, lineEnding, finalNewline = text.endsWith('\n'))
        return LoadResult.Text(text, format, size, modifiedEpochSeconds)
    }

    private fun strictDecode(bytes: ByteArray, offset: Int, encoding: TextEncoding): String? = try {
        if (encoding == TextEncoding.EUC_KR && !isEucKrShaped(bytes, offset)) return null
        encoding.charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /**
     * EUC-KR's byte shape, checked here rather than left to the decoder: ASCII, or two bytes both
     * in A1..FE, leaving out the user-defined rows C9 and FE. Android's decoder under this name
     * takes far more — UHC's extra hangul (`A1 41`), `80` as U+0080, `FF` and the user-defined rows
     * as private-use characters — so a file the JVM refuses would open on the device.
     */
    private fun isEucKrShaped(bytes: ByteArray, offset: Int): Boolean {
        var i = offset
        while (i < bytes.size) {
            val lead = bytes[i].toInt() and 0xFF
            if (lead < 0x80) {
                i++
                continue
            }
            if (lead !in 0xA1..0xFE || lead == 0xC9 || lead == 0xFE || i + 1 >= bytes.size) return false
            if ((bytes[i + 1].toInt() and 0xFF) !in 0xA1..0xFE) return false
            i += 2
        }
        return true
    }

    companion object {
        const val DEFAULT_SIZE_LIMIT = 2L * 1024 * 1024
        const val BINARY_PROBE = 8 * 1024
    }
}

private fun Source.readAtMost(limit: Long): ByteArray = buffer().use { source ->
    val sink = Buffer()
    while (sink.size < limit && source.read(sink, limit - sink.size) != -1L) Unit
    sink.readByteArray()
}
