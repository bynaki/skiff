package com.naki.skiff.ui

import android.util.Log

const val LOG_TAG = "Skiff"

/**
 * Every failure the UI swallows into a message should still leave a trace. Without this a
 * remote error reaches the user as one line of text with no way to find out what happened.
 */
fun logFailure(where: String, throwable: Throwable) {
    Log.w(LOG_TAG, "$where failed", throwable)
}
