package com.naki.skiff.link

/**
 * The contract of Skiff's profile provider, which Skiff Code reads so that a server set up in
 * Skiff opens there without being set up again. Both apps declare [PERMISSION] at signature level,
 * so it is granted whichever is installed first, and only to an app signed with the same key.
 *
 * Nothing secret crosses: no password or passphrase, only what is already in a `skiffcode://`
 * link plus the start path and the host keys the user accepted.
 */
object SharedProfiles {

    const val PERMISSION = "com.naki.skiff.permission.READ_PROFILES"
    const val AUTHORITY = "com.naki.skiff.profiles"

    const val PROFILES_PATH = "profiles"
    const val KNOWN_HOSTS_PATH = "known_hosts"

    const val ID = "id"
    const val NAME = "name"
    const val HOST = "host"
    const val PORT = "port"
    const val USERNAME = "username"
    const val START_PATH = "start_path"
    const val KEY_TYPE = "key_type"
    const val FINGERPRINT = "fingerprint"

    val PROFILE_COLUMNS = arrayOf(ID, NAME, HOST, PORT, USERNAME, START_PATH)
    val KNOWN_HOST_COLUMNS = arrayOf(HOST, PORT, KEY_TYPE, FINGERPRINT)
}
