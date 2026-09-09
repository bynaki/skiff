package com.naki.skiff.fs

/** One directory entry, normalised across local and remote filesystems. */
data class FileNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedEpochSeconds: Long,
    /** POSIX permission bits (e.g. 0o644), or null when the filesystem does not report them. */
    val mode: Int? = null,
    val isSymlink: Boolean = false,
    /** For a symlink, whether the target resolves to a directory. */
    val linkTargetIsDirectory: Boolean = false,
) {
    val isHidden: Boolean get() = name.startsWith(".")

    /** Symlinks to directories should behave like directories when tapped. */
    val navigable: Boolean get() = isDirectory || (isSymlink && linkTargetIsDirectory)
}
