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
