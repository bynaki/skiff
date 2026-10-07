package com.naki.skiff.code.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * `settings.toml` in the app's own files, which only this app can reach: it is edited here, through
 * `Open Settings`, and saving it is what applies it. Getting it in and out of the app is SAF's job
 * (docs/skiffcode.plan.md M4, import/export), not a shared folder's.
 */
class SettingsFile(val file: File, private val themes: () -> Collection<String>) {

    private val lock = Mutex()

    @Volatile
    private var read: SettingsRead? = null

    /** What the file says, read the first time anything asks. */
    suspend fun current(): SettingsRead = read ?: reload()

    /**
     * The last read, or the defaults before the first one. For what asks too often to suspend — the
     * watch asks on every poll — and only ever asks after a file has been opened, which read it.
     */
    val latest: Settings get() = read?.settings ?: Settings()

    /** Reads the file again, which is what saving it in the app does. A file that is not there is every default. */
    suspend fun reload(): SettingsRead = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (file.exists()) SettingsToml.read(file.readText(), themes()) else SettingsRead(Settings(), emptyList())
            } catch (e: IOException) {
                SettingsRead(Settings(), listOf(SettingsProblem.Unreadable(e.message ?: e.javaClass.simpleName)))
            }
        }.also { read = it }
    }

    /**
     * Sets `[editor]`'s `theme` in the file — made from [SettingsToml.TEMPLATE] if there is none yet —
     * and reads it again. An open buffer of the file hears about it the way it hears of any change
     * made outside it: its watch.
     */
    suspend fun setTheme(name: String): SettingsRead {
        lock.withLock {
            withContext(Dispatchers.IO) {
                val text = if (file.exists()) file.readText() else SettingsToml.TEMPLATE
                file.writeText(SettingsToml.withTheme(text, name))
            }
        }
        return reload()
    }

    /**
     * Puts [text] in place of the file, as Import Settings does, and reads it — by the same rules as
     * saving it in the app, so what cannot be used is reported rather than refused.
     */
    suspend fun import(text: String): SettingsRead {
        lock.withLock { withContext(Dispatchers.IO) { file.writeText(text) } }
        return reload()
    }

    /** Puts [SettingsToml.TEMPLATE] in place of the file, as Reset Data does, and reads it. */
    suspend fun reset(): SettingsRead = import(SettingsToml.TEMPLATE)

    /** What Export Settings writes out: the file, or the one `Open Settings` would make when there is none. */
    suspend fun text(): String = lock.withLock {
        withContext(Dispatchers.IO) { if (file.exists()) file.readText() else SettingsToml.TEMPLATE }
    }

    /**
     * Writes [SettingsToml.TEMPLATE] if there is no file yet, so there is something to open, or adds
     * to the one there the keys it lacks, commented out ([SettingsToml.withMissingKeys]) — a file
     * made before a key was, shows it too. Only written when something was added.
     */
    suspend fun ensureComplete() = lock.withLock {
        withContext(Dispatchers.IO) {
            val text = if (file.exists()) file.readText() else ""
            val complete = SettingsToml.withMissingKeys(text)
            if (complete != text) file.writeText(complete)
        }
    }
}
