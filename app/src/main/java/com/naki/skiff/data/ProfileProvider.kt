package com.naki.skiff.data

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.naki.skiff.link.SharedProfiles
import com.naki.skiff.skiff
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Hands Skiff Code the servers set up here, read-only. The manifest guards it with
 * [SharedProfiles.PERMISSION] at signature level; what it returns is listed in [SharedProfiles],
 * and a password is never among it.
 */
class ProfileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        // Called on a binder thread, after Application.onCreate has built the container.
        val store = (context!!.applicationContext as Application).skiff.store
        return when (uri.lastPathSegment) {
            SharedProfiles.PROFILES_PATH -> MatrixCursor(SharedProfiles.PROFILE_COLUMNS).apply {
                for (p in runBlocking { store.profiles.first() }) {
                    addRow(arrayOf<Any>(p.id, p.name, p.host, p.port, p.username, p.startPath))
                }
            }
            SharedProfiles.KNOWN_HOSTS_PATH -> MatrixCursor(SharedProfiles.KNOWN_HOST_COLUMNS).apply {
                for (h in runBlocking { store.knownHosts.first() }) {
                    addRow(arrayOf<Any>(h.host, h.port, h.keyType, h.fingerprint))
                }
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}
