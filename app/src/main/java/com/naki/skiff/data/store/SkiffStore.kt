package com.naki.skiff.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private object SkiffDataSerializer : Serializer<SkiffData> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue = SkiffData()

    override suspend fun readFrom(input: InputStream): SkiffData =
        runCatching { json.decodeFromString<SkiffData>(input.readBytes().decodeToString()) }
            .getOrDefault(defaultValue)

    override suspend fun writeTo(t: SkiffData, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

private val Context.skiffDataStore: DataStore<SkiffData> by dataStore(
    fileName = "skiff.json",
    serializer = SkiffDataSerializer,
)

/**
 * Server profiles and accepted host keys. Passwords inside profiles are already
 * ciphertext from [com.naki.skiff.data.crypto.SecretStore]; this layer never sees plaintext.
 */
class SkiffStore(private val context: Context) {

    val profiles: Flow<List<ServerProfile>> =
        context.skiffDataStore.data.map { it.profiles }

    val settings: Flow<Settings> =
        context.skiffDataStore.data.map { it.settings }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        context.skiffDataStore.updateData { it.copy(settings = transform(it.settings)) }
    }

    suspend fun upsertProfile(profile: ServerProfile) {
        context.skiffDataStore.updateData { data ->
            val existing = data.profiles.indexOfFirst { it.id == profile.id }
            val profiles = data.profiles.toMutableList()
            if (existing >= 0) profiles[existing] = profile else profiles.add(profile)
            data.copy(profiles = profiles)
        }
    }

    suspend fun deleteProfile(id: String) {
        context.skiffDataStore.updateData { data ->
            data.copy(profiles = data.profiles.filterNot { it.id == id })
        }
    }

    suspend fun touchProfile(id: String, epochSeconds: Long) {
        context.skiffDataStore.updateData { data ->
            data.copy(
                profiles = data.profiles.map {
                    if (it.id == id) it.copy(lastUsedEpochSeconds = epochSeconds) else it
                },
            )
        }
    }

    suspend fun knownHost(host: String, port: Int): KnownHost? =
        context.skiffDataStore.updateData { it }
            .knownHosts.firstOrNull { it.host == host && it.port == port }

    suspend fun rememberHost(knownHost: KnownHost) {
        context.skiffDataStore.updateData { data ->
            val others = data.knownHosts.filterNot {
                it.host == knownHost.host && it.port == knownHost.port
            }
            data.copy(knownHosts = others + knownHost)
        }
    }

    suspend fun forgetHost(host: String, port: Int) {
        context.skiffDataStore.updateData { data ->
            data.copy(knownHosts = data.knownHosts.filterNot { it.host == host && it.port == port })
        }
    }
}
