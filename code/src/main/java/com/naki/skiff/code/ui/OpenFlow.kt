package com.naki.skiff.code.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import com.naki.skiff.code.R
import com.naki.skiff.code.SkiffCodeContainer
import com.naki.skiff.code.doc.LoadResult
import com.naki.skiff.code.doc.SaveTarget
import com.naki.skiff.code.doc.Stamp
import com.naki.skiff.code.doc.Stamped
import com.naki.skiff.code.doc.TextLoader
import com.naki.skiff.code.doc.WatchedContent
import com.naki.skiff.code.doc.WatchedFile
import com.naki.skiff.code.doc.WatchedLocalPath
import com.naki.skiff.code.doc.WatchedPath
import com.naki.skiff.code.intent.OpenRequest
import com.naki.skiff.data.crypto.SecretStore
import com.naki.skiff.data.store.AuthMethod
import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsError
import com.naki.skiff.fs.LocalNetworkAccess
import com.naki.skiff.fs.local.LocalFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.source
import java.io.File

/**
 * Takes an [OpenRequest] from intent to the file's text: asks about unknown servers, passwords and
 * permissions, connects, checks the file is there and reads it.
 *
 * Anything that goes wrong on the way is told in a dialog. A file that was read but is not text
 * the viewer can show — too large, binary, an unknown encoding — is not a failure here: it comes
 * back as [Opened.result], for the page to say so where the document would have been.
 */
class OpenFlow(private val activity: MainActivity, private val container: SkiffCodeContainer) {

    private val loader = TextLoader()

    /**
     * A file that was read, how to name it, and the line the link asked for — with the file it
     * came from and how it looked when it was read, which is what `FileWatcher` measures a change
     * made somewhere else against.
     */
    data class Opened(
        val link: String,
        val name: String,
        val request: OpenRequest,
        val result: LoadResult,
        val line: Int?,
        val watched: WatchedFile,
        val stamp: Stamped,
        /** Where a save goes, or null for a document that cannot take one; see [SaveTarget]. */
        val save: SaveTarget?,
    )

    /**
     * Null when the user backed out or the file could not be opened; either way they have been
     * told. [fromSkiff] says the link came from Skiff itself, which is what lets it open without
     * confirming the path; see [confirmPath].
     */
    suspend fun open(link: String, request: OpenRequest, fromSkiff: Boolean): Opened? = try {
        val opened = if (!fromSkiff && !confirmPath(request)) null else when (request) {
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

    /**
     * Shows the server and the path a link picked, before anything is read or written there.
     *
     * A matched alias only decides *which server*: the path stays the link's own, so a link that
     * guesses a profile's name otherwise reaches any file on it with the stored credentials. That
     * was a file shown on screen while this was a viewer; once documents can be saved it is the
     * file the user's typing lands in.
     *
     * Listed one by one rather than with an `else`, so that a new kind of [OpenRequest] has to
     * say here whether its path was the user's choice instead of quietly skipping the question.
     */
    private suspend fun confirmPath(request: OpenRequest): Boolean = when (request) {
        is OpenRequest.LocalPath -> confirmPath(activity.getString(R.string.link_path_device), request.path)
        is OpenRequest.Remote ->
            confirmPath("${request.profile.username}@${request.profile.host}:${request.profile.port}", request.path)
        // UnknownServer asks anyway, with the same path in its dialog. Content is a grant the
        // sending app handed us, not a path we picked. Invalid never reaches a file.
        is OpenRequest.UnknownServer, is OpenRequest.Content, is OpenRequest.Invalid -> true
    }

    private suspend fun confirmPath(where: String, path: String): Boolean = activity.confirm(
        activity.getString(R.string.link_path_title),
        activity.getString(R.string.link_path_body, where, path),
        activity.getString(R.string.action_open),
    )

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
        val fs = LocalFileSystem(file.name)
        val result = loader.load(fs, request.path)
        return Opened(
            link, file.name, request, result, request.at.line,
            WatchedLocalPath(fs, request.path, loader), stampOf(result), saveTo(fs, request.path, result),
        )
    }

    private suspend fun openContent(link: String, request: OpenRequest.Content): Opened {
        val uri = Uri.parse(request.uri)
        val name = withContext(Dispatchers.IO) {
            activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }
        val stream = withContext(Dispatchers.IO) { activity.contentResolver.openInputStream(uri) } ?: throw FsError.NotFound(request.uri)
        val result = stream.use { loader.load(it.source()) }
        // The application's resolver, not this activity's: the watcher outlives a configuration
        // change. And the provider's own stamp, since the loader's `stat` here is of a stream.
        val watched = WatchedContent(activity.applicationContext.contentResolver, uri, loader)
        // No save target: a `content://` document is read-only here (docs/skiffcode.plan.md M3).
        return Opened(link, name ?: uri.lastPathSegment ?: request.uri, request, result, null, watched, watched.stamp(), null)
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
            val fs = container.sessions.get(profile)
            val node = try {
                fs.stat(request.path)
            } catch (e: FsError.AuthFailed) {
                profile = withNewPassword(profile, retry = true) ?: return null
                continue
            }
            return when {
                node == null -> fail(R.string.error_open, describe(FsError.NotFound(request.path)))
                node.navigable -> fail(R.string.error_open, activity.getString(R.string.error_is_directory, request.path))
                else -> {
                    val result = loader.load(fs, request.path)
                    Opened(
                        link, node.name, request.copy(profile = profile), result, request.at.line,
                        // The same filesystem the file was read through: `stat` goes over the browse
                        // connection while reading and saving go over transfer, so a poll every two
                        // seconds never waits behind either of them.
                        WatchedPath(fs, request.path, loader), stampOf(result), saveTo(fs, request.path, result),
                    )
                }
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

    /**
     * How the file is written back: the same filesystem it was read through, and the encoding, byte
     * order mark and line ending it was read with. Only a file that became text has any of that.
     */
    private fun saveTo(fs: FileSystem, path: String, result: LoadResult): SaveTarget? =
        (result as? LoadResult.Text)?.let { SaveTarget(fs, path, it.format) }

    /**
     * The `stat` [TextLoader] took before reading. Nothing else is watched: a file too large,
     * binary or in an encoding we cannot name has no text on screen for a change to be merged into.
     */
    private fun stampOf(result: LoadResult): Stamped =
        if (result is LoadResult.Text) Stamped.At(Stamp(result.size, result.modifiedEpochSeconds)) else Stamped.Unknown

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
