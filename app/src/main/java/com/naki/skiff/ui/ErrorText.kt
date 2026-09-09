package com.naki.skiff.ui

import android.content.Context
import com.naki.skiff.R
import com.naki.skiff.fs.FsError

/** Turns an [FsError] into something a person can read, in the device's language. */
fun Context.describe(throwable: Throwable): String = when (throwable) {
    is FsError.NotFound -> getString(R.string.error_not_found)
    is FsError.PermissionDenied -> getString(R.string.error_permission_denied)
    is FsError.AlreadyExists -> getString(R.string.error_already_exists)
    is FsError.NotADirectory -> getString(R.string.error_not_a_directory)
    is FsError.DirectoryNotEmpty -> getString(R.string.error_directory_not_empty)
    is FsError.NoSpace -> getString(R.string.error_no_space)
    is FsError.AuthFailed -> getString(R.string.error_auth_failed)
    is FsError.HostKeyRejected -> getString(R.string.error_host_key)
    is FsError.NetworkLost -> getString(R.string.error_network_lost)
    is FsError.Unreachable -> getString(R.string.error_unreachable, throwable.host)
    is FsError.LocalNetworkNotGranted -> getString(R.string.error_local_network_not_granted)
    is FsError.StorageNotGranted -> getString(R.string.error_storage_not_granted)
    else -> throwable.message ?: throwable::class.java.simpleName
}
