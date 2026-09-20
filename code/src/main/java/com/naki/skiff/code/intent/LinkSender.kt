package com.naki.skiff.code.intent

import android.content.Context
import android.content.pm.PackageManager

/** Skiff's package. The only sender whose links open without asking about the path. */
const val SKIFF_PACKAGE = "com.naki.skiff"

/**
 * Whether [callerPackage] is Skiff, signed with our key.
 *
 * A link chooses a path, and the path stays the sender's choice until the user has seen it. Skiff
 * only ever sends a path the user tapped, so its links keep opening straight away; every other
 * sender has the path confirmed first.
 *
 * The package name has to come from the system rather than the intent. `getReferrer()` is no good
 * here: the caller fills in `EXTRA_REFERRER` itself, so any app can claim to be Skiff.
 * `ComponentCaller.getPackage()` and `getLaunchedFromPackage()` are answered by the framework —
 * a caller decides only *whether* to reveal itself (`ActivityOptions.setShareIdentityEnabled`),
 * never *what* it is. Null, from an app that did not opt in or from a device below Android 15,
 * is simply not Skiff.
 */
fun Context.sentBySkiff(callerPackage: String?): Boolean =
    callerPackage == SKIFF_PACKAGE &&
        packageManager.checkSignatures(packageName, callerPackage) == PackageManager.SIGNATURE_MATCH
