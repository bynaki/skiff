plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.naki.skiff"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.naki.skiff"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
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

    buildFeatures {
        compose = true
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.window)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okio)

    // sshj resolves a logger in DefaultConfig's constructor, so slf4j is not optional:
    // excluding it makes the very first connection attempt die with NoClassDefFoundError.
    // The Android binding routes sshj's own logging to logcat, which is worth having when
    // a connection misbehaves in the field.
    implementation(libs.sshj)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.android)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.eddsa)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // A real SFTP server, so SftpFileSystem is exercised by the real sshj client rather
    // than by a mock that agrees with whatever we assumed the protocol does.
    testImplementation(libs.mina.sshd.core)
    testImplementation(libs.mina.sshd.sftp)
    // slf4j-android is a no-op off-device, so the JVM tests take the console binding at the
    // same slf4j version. Nothing here may add a library the app itself lacks.
    testRuntimeOnly(libs.slf4j.simple)
}
