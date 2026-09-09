package com.naki.skiff.fs

/**
 * Failures the UI needs to tell apart. Filesystem implementations translate their
 * native exceptions into these so screens never have to match on SFTPException or IOException.
 */
sealed class FsError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotFound(val path: String) : FsError("Not found: $path")
    class PermissionDenied(val path: String) : FsError("Permission denied: $path")
    class AlreadyExists(val path: String) : FsError("Already exists: $path")
    class NotADirectory(val path: String) : FsError("Not a directory: $path")
    class DirectoryNotEmpty(val path: String) : FsError("Directory not empty: $path")
    class NoSpace(val path: String) : FsError("Out of space: $path")
    class AuthFailed(cause: Throwable? = null) : FsError("Authentication failed", cause)
    class HostKeyRejected(val fingerprint: String) : FsError("Host key rejected: $fingerprint")
    class NetworkLost(cause: Throwable? = null) : FsError("Connection lost", cause)
    class StorageNotGranted : FsError("Storage access has not been granted")
    class Unknown(message: String, cause: Throwable? = null) : FsError(message, cause)
}
