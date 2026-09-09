package com.naki.skiff

import android.app.Application
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class SkiffApplication : Application() {

    lateinit var container: SkiffContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installBouncyCastle()
        container = SkiffContainer(this)
    }

    /**
     * Android ships a cut-down BouncyCastle already registered as "BC". sshj needs the full
     * one, and JCE resolves providers by name, so the platform's stub has to be evicted
     * before ours is registered or half the ciphers and key formats silently go missing.
     */
    private fun installBouncyCastle() {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
    }
}

val Application.skiff: SkiffContainer get() = (this as SkiffApplication).container
