package com.naki.skiff.code.project

import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsPath

/**
 * Finds the git working tree a remote file sits in, over SFTP alone — no `exec`, so it works on an
 * account that has no shell, and it runs before the user has agreed to a project.
 *
 * It walks up from the file's directory looking for `.git`, a directory in an ordinary checkout and
 * a file in a worktree or submodule. The walk never leaves [home]: it stops there, and a file that is
 * not under it is not looked into at all. Both sides are compared canonical, so a symlink out of the
 * home counts as outside, and a home reached through a symlinked `/home` still matches.
 */
object GitScopeFinder {

    /**
     * The working tree's top directory, or null when the file is in none below [home]. [home] is
     * where the login lands (`canonicalize(".")`) unless given.
     */
    suspend fun find(fs: FileSystem, file: String, home: String? = null): String? {
        val top = FsPath.normalize(home ?: fs.canonicalize("."))
        var dir = fs.canonicalize(FsPath.parent(file))
        if (!FsPath.isAncestorOrSame(top, dir)) return null
        while (true) {
            if (fs.stat(FsPath.join(dir, ".git")) != null) return dir
            if (dir == top) return null
            dir = FsPath.parent(dir)
        }
    }
}
