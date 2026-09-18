package com.naki.skiff.fs.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings

/**
 * "All files access" is a special app-ops grant, not a runtime permission, so it cannot be
 * requested with the permission launcher — the user has to flip it in Settings and come back.
 */
object LocalStorageAccess {

    fun isGranted(): Boolean = Environment.isExternalStorageManager()

    /** Deep-links to this app's entry in the "All files access" settings screen. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }

    /** Some OEM builds ship without the per-app screen; fall back to the global list. */
    fun fallbackSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
}
