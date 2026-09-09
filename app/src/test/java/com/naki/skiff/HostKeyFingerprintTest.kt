package com.naki.skiff

import com.naki.skiff.fs.sftp.HostKeyFingerprint
import net.schmizz.sshj.common.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

/**
 * Vectors produced by `ssh-keygen -lf`. A fingerprint the user cannot match against what
 * their terminal prints is worse than none, so this pins the exact OpenSSH format.
 */
class HostKeyFingerprintTest {

    private val ed25519Blob =
        "AAAAC3NzaC1lZDI1NTE5AAAAIEQcKYtsPO9sxz80I7mlWija0FgZzpD1X3Pw7kBhf9QM"
    private val ed25519Fingerprint = "SHA256:zucIRfQek11XlfAWkCe1+6BUxLSAvTl+b8VO0z7Ip4w"

    private val rsaBlob =
        "AAAAB3NzaC1yc2EAAAADAQABAAABAQCr3/KvCt/SMbki5gp+2QmMA4Cgcn3RPDZAApU5Lw9zJ4Gm" +
            "El60GX5/QsNkw40B/pHKLoihEHEUPofOY+aDuAc5ipph5kcH6eiusC0RQ+mTW7n9kH0kal6UE/KJ" +
            "nrV+XB8VEBjdVcpJSDt03Y8gfVwLMUXpcrS1eRwRk10C35MDkNYQTxdbzu4lSyCe08MIg3cceaKz" +
            "j+mH99DmeJclwilX4m0PbhrFRDurY/jQHM3OdpOj6u7+KIZ9Wkz6WaipvexYDKn/z4rnHLuzpgcG" +
            "1NB6niN49vCaT5x38vwOUn47UESAlg8pS2rAEwCEuDcQDnTlzdVdd8MFOHDoW3eb9GCD"
    private val rsaFingerprint = "SHA256:M9V0MJiFejM5eBcli82L12JgKKQ+B2dQ3N73k79wTYo"

    @Test
    fun `matches ssh-keygen for an ed25519 key`() {
        assertEquals(ed25519Fingerprint, HostKeyFingerprint.of(Base64.getDecoder().decode(ed25519Blob)))
    }

    @Test
    fun `matches ssh-keygen for an rsa key`() {
        assertEquals(rsaFingerprint, HostKeyFingerprint.of(Base64.getDecoder().decode(rsaBlob)))
    }

    @Test
    fun `hashing a parsed key gives the same answer as hashing the raw blob`() {
        // This is the step that actually runs in production: sshj hands us a PublicKey, and
        // re-encoding it must reproduce the bytes OpenSSH hashed.
        for ((blob, expected) in listOf(ed25519Blob to ed25519Fingerprint, rsaBlob to rsaFingerprint)) {
            val decoded = Base64.getDecoder().decode(blob)
            val key = Buffer.PlainBuffer(decoded).readPublicKey()
            assertEquals(expected, HostKeyFingerprint.of(key))
        }
    }
}
