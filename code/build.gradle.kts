plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
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

    // Same list as :app, for the same jars: sshj and BouncyCastle arrive through :core.
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
    // Brings okio, sshj, kotlinx-serialization and DataStore with it, as it does for :app.
    implementation(project(":core"))

    implementation(libs.androidx.webkit)
    implementation(libs.ktoml.core)
    implementation(libs.kotlinx.coroutines.android)
    // Registered by hand in SkiffCodeApplication, for the reason SkiffApplication gives.
    implementation(libs.bouncycastle.prov)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // org.json is part of the framework, so the unit test JVM only has a stub that throws.
    testImplementation(libs.org.json)
    // TextLoaderTest reads through a real SFTP server, the same one :core's tests use.
    testImplementation(testFixtures(project(":core")))
    // slf4j-android is a no-op off-device, so the JVM tests take the console binding, as :app does.
    testRuntimeOnly(libs.slf4j.simple)
}

// The UI is a Vite + TypeScript bundle in web/, built into src/main/assets/web/ (gitignored)
// before anything reads assets. npm comes from PATH, so the Gradle daemon must be started from a
// shell that has node on it.
val npmCi = tasks.register<Exec>("npmCi") {
    workingDir = file("web")
    commandLine("npm", "ci")
    inputs.files("web/package.json", "web/package-lock.json")
    outputs.dir("web/node_modules")
}

val buildWeb = tasks.register<Exec>("buildWeb") {
    dependsOn(npmCi)
    workingDir = file("web")
    commandLine("npm", "run", "build")
    inputs.files("web/package.json", "web/package-lock.json", "web/index.html", "web/tsconfig.json", "web/vite.config.ts")
    inputs.dir("web/src")
    outputs.dir("src/main/assets/web")
}

tasks.named("preBuild") {
    dependsOn(buildWeb)
}

// The page's own tests (vitest, in web/test). No Kotlin test task reaches them, so `check` is
// where they join everything else that has to pass.
val testWeb = tasks.register<Exec>("testWeb") {
    dependsOn(npmCi)
    workingDir = file("web")
    commandLine("npm", "test")
}

tasks.named("check") {
    dependsOn(testWeb)
}
