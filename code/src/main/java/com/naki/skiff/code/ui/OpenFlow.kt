package com.naki.skiff.code.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import com.naki.skiff.code.R
import com.naki.skiff.code.SkiffCodeContainer
import com.naki.skiff.code.intent.OpenRequest
import com.naki.skiff.data.crypto.SecretStore
import com.naki.skiff.data.store.AuthMethod
import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.fs.FsError
import com.naki.skiff.fs.LocalNetworkAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Takes an [OpenRequest] from intent to a file that can be read: asks about unknown servers,
 * passwords and permissions, connects, and checks the file is there.
 *
 * It stops at [Opened]. Reading and showing the text is the viewer's, which M2 builds next.
 */
class OpenFlow(private val activity: MainActivity, private val container: SkiffCodeContainer) {

    /** A file that exists and can be read, and how to name it. */
    data class Opened(val link: String, val name: String, val request: OpenRequest)

    /** Null when the user backed out or the file could not be opened; either way they have been told. */
    suspend fun open(link: String, request: OpenRequest): Opened? = try {
        val opened = when (request) {
            is OpenRequest.Invalid -> fail(R.string.error_bad_link, request.reason)
            is OpenRequest.LocalPath -> openLocal(link, request)
            is OpenRequest.Content -> openContent(link, request)
            is OpenRequest.UnknownServer -> adopt(request)?.let { openRemote(link, it) }
            is OpenRequest.Remote -> openRemote(link, request)
        }
        opened?.also { container.store.addRecentFile(link, System.currentTimeMillis() / 1000) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "open failed: $link", e)
        fail(R.string.error_open, describe(e))
    }

    private suspend fun openLocal(link: String, request: OpenRequest.LocalPath): Opened? {
        if (!Environment.isExternalStorageManager()) {
            val go = activity.confirm(
                activity.getString(R.string.storage_title),
                activity.getString(R.string.storage_body),
                activity.getString(R.string.action_open_settings),
            )
            if (!go) return null
            activity.startActivityAndWaitForReturn(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${activity.packageName}")),
            )
            if (!Environment.isExternalStorageManager()) return fail(R.string.error_open, activity.getString(R.string.storage_denied))
        }
        val file = File(request.path)
        val problem = withContext(Dispatchers.IO) {
            when {
                !file.exists() -> FsError.NotFound(request.path)
                file.isDirectory -> FsError.Unknown(activity.getString(R.string.error_is_directory, request.path))
                !file.canRead() -> FsError.PermissionDenied(request.path)
                else -> null
            }
        }
        if (problem != null) return fail(R.string.error_open, describe(problem))
        return Opened(link, file.name, request)
    }

    private suspend fun openContent(link: String, request: OpenRequest.Content): Opened {
        val uri = Uri.parse(request.uri)
        val name = withContext(Dispatchers.IO) {
            activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }
        return Opened(link, name ?: uri.lastPathSegment ?: request.uri, request)
    }

    /**
     * Asks whether to open a server we have no profile for. The answer becomes a profile either way
     * — saved, or held only in memory for this process — so everything after this is the same path.
     */
    private suspend fun adopt(request: OpenRequest.UnknownServer): OpenRequest.Remote? {
        val answer = activity.confirmUnknownServer(request) ?: return null
        if (answer.user.isEmpty()) return fail(R.string.error_open, activity.getString(R.string.error_no_user))
        val profile = ServerProfile(
            name = request.alias ?: "${answer.user}@${request.host}",
            host = request.host,
            port = request.port,
            username = answer.user,
            auth = AuthMethod.Password(SecretStore.encrypt(answer.password)),
        )
        if (answer.saveAsProfile) container.store.upsertProfile(profile)
        return OpenRequest.Remote(profile, request.path, request.at)
    }

    private suspend fun openRemote(link: String, request: OpenRequest.Remote): Opened? {
        var profile = request.profile
        if (!ensureLocalNetwork(profile.host)) return fail(R.string.error_open, describe(FsError.LocalNetworkNotGranted()))

        if ((profile.auth as? AuthMethod.Password)?.encryptedPassword == null) {
            profile = withNewPassword(profile, retry = false) ?: return null
        }
        while (true) {
            val node = try {
                container.sessions.get(profile).stat(request.path)
            } catch (e: FsError.AuthFailed) {
                profile = withNewPassword(profile, retry = true) ?: return null
                continue
            }
            return when {
                node == null -> fail(R.string.error_open, describe(FsError.NotFound(request.path)))
                node.navigable -> fail(R.string.error_open, activity.getString(R.string.error_is_directory, request.path))
                else -> Opened(link, node.name, request.copy(profile = profile))
            }
        }
    }

    /** Asks for a password and stores it, in the saved profile too when there is one. Null is "cancel". */
    private suspend fun withNewPassword(profile: ServerProfile, retry: Boolean): ServerProfile? {
        val password = activity.askPassword("${profile.username}@${profile.host}:${profile.port}", retry) ?: return null
        val updated = profile.copy(auth = AuthMethod.Password(SecretStore.encrypt(password)))
        if (container.store.profiles.first().any { it.id == profile.id }) container.store.upsertProfile(updated)
        return updated
    }

    private suspend fun ensureLocalNetwork(host: String): Boolean {
        val permission = LocalNetworkAccess.permission ?: return true
        if (LocalNetworkAccess.isGranted(activity)) return true
        if (!withContext(Dispatchers.IO) { LocalNetworkAccess.isLocalHost(host) }) return true
        return activity.requestPermissionAndWait(permission)
    }

    private suspend fun fail(title: Int, message: String): Nothing? {
        activity.showError(activity.getString(title), message)
        return null
    }

    private fun describe(e: Throwable): String = when (e) {
        is FsError.NotFound -> activity.getString(R.string.error_not_found, e.path)
        is FsError.PermissionDenied -> activity.getString(R.string.error_permission, e.path)
        is FsError.Unreachable -> activity.getString(R.string.error_unreachable, e.host)
        is FsError.HostKeyRejected -> activity.getString(R.string.error_host_key)
        is FsError.NetworkLost -> activity.getString(R.string.error_network_lost)
        is FsError.LocalNetworkNotGranted -> activity.getString(R.string.error_local_network)
        else -> e.message ?: e.javaClass.simpleName
    }

    private companion object {
        const val TAG = "SkiffCode"
    }
}
