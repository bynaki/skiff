plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.naki.skiff.code"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.naki.skiff.code"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
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
}

dependencies {
    implementation(libs.androidx.webkit)
}
