package com.naki.skiff.code.ui

import com.naki.skiff.code.intent.OpenAt
import com.naki.skiff.code.intent.OpenRequest
import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsPath
import com.naki.skiff.link.SkiffCodeLink

/**
 * The directory an open file is in, which the palette's file mode lists and opens files from
 * (docs/skiffcode.spec.md "커맨드 버튼과 팔레트"). A `content://` document has none: it is a grant for
 * one file, with no directory behind it that we may read.
 *
 * [fs] is the filesystem the file itself was read through, so listing a remote directory goes over
 * the same session rather than asking [com.naki.skiff.code.session.RemoteSessions] for one again.
 */
class Folder private constructor(private val fs: FileSystem, private val request: OpenRequest) {

    private val file: String = when (request) {
        is OpenRequest.LocalPath -> request.path
        is OpenRequest.Remote -> request.path
        else -> throw IllegalArgumentException("${request.javaClass.simpleName} is not a file in a directory")
    }

    val path: String = FsPath.parent(file)

    private val self: String = FsPath.name(file)

    /**
     * The file called [name] next to this one, or null when [name] is not a plain file name. The
     * page names the file and this is what keeps it to this directory: whatever name it sends, the
     * file opened is one in the directory of a file the user has already opened.
     */
    fun request(name: String): OpenRequest? {
        if (name.isEmpty() || name == "." || name == ".." || '/' in name || '\u0000' in name) return null
        val path = FsPath.join(this.path, name)
        return when (request) {
            is OpenRequest.LocalPath -> OpenRequest.LocalPath(path, OpenAt())
            is OpenRequest.Remote -> request.copy(path = path, at = OpenAt())
            else -> null
        }
    }

    /**
     * The files beside this one, by name, leaving out directories, this file itself and whatever
     * [open] says is already open — the palette offers those as open files, which switching to
     * is, rather than as a second copy to read.
     */
    suspend fun names(open: (OpenRequest) -> Boolean): List<String> =
        fs.list(path)
            .filter { !it.navigable && it.name != self }
            .mapNotNull { node -> node.name.takeIf { name -> request(name)?.let(open) == false } }
            .sorted()

    companion object {
        fun of(fs: FileSystem, request: OpenRequest): Folder? =
            if (request is OpenRequest.LocalPath || request is OpenRequest.Remote) Folder(fs, request) else null
    }
}

/**
 * The link that names a file [Folder.request] made, which is what the recent files list keeps for a
 * file opened without one. The alias is the profile's name, which [OpenRequest.findProfile] tries
 * first, so the link finds the same profile again.
 */
fun linkOf(request: OpenRequest): String = when (request) {
    is OpenRequest.LocalPath -> SkiffCodeLink.local(request.path)
    is OpenRequest.Remote -> with(request.profile) { SkiffCodeLink.remote(username, host, port, request.path, alias = name) }
    else -> throw IllegalArgumentException("${request.javaClass.simpleName} has no link of its own")
}
