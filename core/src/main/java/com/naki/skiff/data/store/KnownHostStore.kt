package com.naki.skiff.data.store

/**
 * The host keys we have already accepted. [com.naki.skiff.fs.sftp.HostKeyGate] is written
 * against this and not against a concrete store, so each app that uses this module keeps its
 * own persistence — Skiff has one JSON blob, Skiff Code has another.
 *
 * [knownHost] is reached from the SSH transport thread through runBlocking, so an
 * implementation must answer it with a real read and nothing more. DataStore's `data.first()`
 * qualifies; `updateData` does not, because it takes the write lock and can block behind an
 * unrelated write — a stall on the thread the connection itself is waiting on.
 */
interface KnownHostStore {

    suspend fun knownHost(host: String, port: Int): KnownHost?

    suspend fun rememberHost(knownHost: KnownHost)
}
