plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.naki.skiff.core"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = 30
    }

    // SftpTestServer is published as a fixture because :app's transfer tests drive CopyEngine
    // across a real SFTP server too, and a second copy of it would drift from this one.
    testFixtures {
        enable = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md",
            )
        }
    }
}

dependencies {
    // api, not implementation: these types are in this module's own signatures. FileSystem
    // hands out okio Source/Sink, HostKeyGate is an sshj HostKeyVerifier, and SourceId is
    // @Serializable — a consumer cannot call any of it without them on its compile classpath.
    // jsonDataStore returns a DataStore, so the same goes for it.
    api(libs.okio)
    api(libs.sshj)
    api(libs.kotlinx.serialization.json)
    api(libs.androidx.datastore)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // sshj resolves a logger in DefaultConfig's constructor, so slf4j is not optional:
    // excluding it makes the very first connection attempt die with NoClassDefFoundError.
    // The Android binding routes sshj's own logging to logcat, which is worth having when
    // a connection misbehaves in the field.
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.android)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.eddsa)

    // A real SFTP server, so SftpFileSystem is exercised by the real sshj client rather
    // than by a mock that agrees with whatever we assumed the protocol does.
    testFixturesApi(libs.mina.sshd.core)
    testFixturesApi(libs.mina.sshd.sftp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // slf4j-android is a no-op off-device, so the JVM tests take the console binding at the
    // same slf4j version. Nothing here may add a library the app itself lacks.
    testRuntimeOnly(libs.slf4j.simple)
}
