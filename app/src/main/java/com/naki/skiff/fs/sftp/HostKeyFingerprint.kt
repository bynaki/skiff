package com.naki.skiff.fs.sftp

import net.schmizz.sshj.common.Buffer
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

/**
 * The "SHA256:…" fingerprint that ssh and ssh-keygen print.
 *
 * sshj's own helper still returns the old MD5 hex form, which a user cannot compare against
 * what their terminal shows them — and a fingerprint nobody checks is not a security control.
 */
object HostKeyFingerprint {

    fun of(key: PublicKey): String = of(wireFormat(key))

    /** [blob] is the SSH wire encoding — the same bytes base64'd in an authorized_keys line. */
    fun of(blob: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    fun wireFormat(key: PublicKey): ByteArray =
        Buffer.PlainBuffer().putPublicKey(key).compactData
}
