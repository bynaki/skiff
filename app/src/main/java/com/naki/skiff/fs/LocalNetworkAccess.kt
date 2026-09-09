package com.naki.skiff.fs

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.InetAddress

/**
 * Android 17 requires a separate runtime permission to reach private-range addresses.
 * Without it the connection is not refused, it is dropped — so the user sees a 15 second
 * stall and a timeout, with nothing to suggest a permission is missing.
 */
object LocalNetworkAccess {

    /** The API level that introduced ACCESS_LOCAL_NETWORK. */
    private const val REQUIRED_SDK = 37

    val permission: String? =
        if (Build.VERSION.SDK_INT >= REQUIRED_SDK) Manifest.permission.ACCESS_LOCAL_NETWORK else null

    fun isRequired(): Boolean = permission != null

    fun isGranted(context: Context): Boolean {
        val name = permission ?: return true
        return ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * True when [host] is somewhere the permission actually governs. Asking for it before
     * connecting to a public server would be a prompt with no reason behind it.
     */
    fun isLocalHost(host: String): Boolean = runCatching {
        val address = InetAddress.getByName(host)
        address.isSiteLocalAddress ||
            address.isLinkLocalAddress ||
            address.isLoopbackAddress ||
            address.isAnyLocalAddress
    }.getOrDefault(false)
}
