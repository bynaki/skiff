package com.naki.skiff.code

import android.app.Application
import com.naki.skiff.code.data.SkiffCodeStore
import com.naki.skiff.code.data.openSkiffCodeDataStore
import com.naki.skiff.code.session.RemoteSessions
import com.naki.skiff.fs.sftp.HostKeyGate
import com.naki.skiff.fs.sftp.HostKeyPrompter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class SkiffCodeApplication : Application() {

    lateinit var container: SkiffCodeContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Android's own "BC" is cut down; sshj needs the full provider under that name.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
        container = SkiffCodeContainer(this)
    }
}

/**
 * Process-scoped singletons. The store must be opened once per process, and the host key prompter
 * has to outlive any one screen: a connection can meet an unknown key while the activity is being
 * recreated, and the answer arrives from whichever instance is in front.
 */
class SkiffCodeContainer(app: Application) {

    val store = SkiffCodeStore(openSkiffCodeDataStore(app))

    val hostKeyPrompter = HostKeyPrompter()

    val sessions = RemoteSessions(newHostKeyGate = { HostKeyGate(store, hostKeyPrompter::ask) })
}

val Application.skiffCode: SkiffCodeContainer get() = (this as SkiffCodeApplication).container
