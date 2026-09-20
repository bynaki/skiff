package com.naki.skiff.code.data

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import com.naki.skiff.code.intent.SKIFF_PACKAGE
import com.naki.skiff.data.store.KnownHost
import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.link.SharedProfiles

/**
 * Reads the servers Skiff shares through its profile provider, for [SkiffCodeStore.importFromSkiff].
 * Null when there is nothing to read: Skiff is not installed, or is signed by another key.
 * Blocking; call it off the main thread.
 *
 * The provider's owner is checked before anything is read. Without Skiff installed, another app
 * could hold the authority and hand us host keys to trust, which would let it vouch for a man in
 * the middle; the permission alone does not stop that, since the reader is the one holding it.
 */
fun readSkiffProfiles(context: Context): Pair<List<ServerProfile>, List<KnownHost>>? {
    val pm = context.packageManager
    val owner = pm.resolveContentProvider(SharedProfiles.AUTHORITY, 0)?.packageName
    if (owner != SKIFF_PACKAGE || pm.checkSignatures(context.packageName, owner) != PackageManager.SIGNATURE_MATCH) {
        return null
    }
    return try {
        read(context.contentResolver)
    } catch (_: SecurityException) {
        null
    }
}

private fun read(resolver: ContentResolver): Pair<List<ServerProfile>, List<KnownHost>>? {
    val profiles = resolver.rows(SharedProfiles.PROFILES_PATH) {
        ServerProfile(
            id = string(SharedProfiles.ID),
            name = string(SharedProfiles.NAME),
            host = string(SharedProfiles.HOST),
            port = int(SharedProfiles.PORT),
            username = string(SharedProfiles.USERNAME),
            startPath = string(SharedProfiles.START_PATH),
        )
    }
    val knownHosts = resolver.rows(SharedProfiles.KNOWN_HOSTS_PATH) {
        KnownHost(
            host = string(SharedProfiles.HOST),
            port = int(SharedProfiles.PORT),
            keyType = string(SharedProfiles.KEY_TYPE),
            fingerprint = string(SharedProfiles.FINGERPRINT),
        )
    }
    return if (profiles == null || knownHosts == null) null else profiles to knownHosts
}

private fun <T> ContentResolver.rows(path: String, row: Cursor.() -> T): List<T>? {
    val uri = Uri.parse("content://${SharedProfiles.AUTHORITY}/$path")
    return query(uri, null, null, null, null)?.use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.row()) }
    }
}

private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
