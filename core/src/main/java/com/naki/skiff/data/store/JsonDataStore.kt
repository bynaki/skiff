package com.naki.skiff.data.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * A [DataStore] holding one JSON document in [file], the way both apps keep their profiles and
 * accepted host keys.
 *
 * A file that no longer decodes — most likely after a class the JSON names was renamed, or after
 * a downgrade — is copied aside to `<name>.corrupt-<epoch millis>` before the store starts over
 * from [defaultValue]. Starting over is the only way to keep the app usable, but without the copy
 * the next write would destroy every profile and, worse, every accepted host key: each server
 * would then look new rather than changed, and the prompt that should have warned about a
 * different key would ask to trust it instead.
 *
 * Only one of these may be open per file in a process, so each app builds it once in a
 * process-scoped container.
 */
fun <T> jsonDataStore(
    file: File,
    serializer: KSerializer<T>,
    defaultValue: T,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    now: () -> Long = System::currentTimeMillis,
): DataStore<T> = DataStoreFactory.create(
    serializer = JsonSerializer(serializer, defaultValue),
    corruptionHandler = ReplaceFileCorruptionHandler {
        // Runs before the replacement is written, so the file still holds what failed to decode.
        file.copyTo(File(file.parentFile, "${file.name}.corrupt-${now()}"))
        defaultValue
    },
    scope = scope,
    produceFile = { file },
)

private class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T = try {
        json.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: IllegalArgumentException) {
        // SerializationException is one of these.
        throw CorruptionException("stored JSON does not decode", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
