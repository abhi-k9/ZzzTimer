import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val versionMajor = 1
val versionMinor = 0
val versionPatch = 0
val versionBuild = 0

/**
 * `M…Mmmppbb`: every component gets its own two digits (more for major), so codes never overlap and always increase,
 * e.g. `1.2.3` → `1020300`. Google Play caps versionCode at 2100000000.
 */
fun versionCodeOf(major: Int, minor: Int, patch: Int, build: Int): Int {
    require(major in 0..2099) { "versionMajor must be in 0..2099, was $major" }
    require(minor in 0..99) { "versionMinor must be in 0..99, was $minor" }
    require(patch in 0..99) { "versionPatch must be in 0..99, was $patch" }
    require(build in 0..99) { "versionBuild must be in 0..99, was $build" }
    return major * 1_000_000 + minor * 10_000 + patch * 100 + build
}

/**
 * Release signing is read from Gradle properties or environment variables, so that no secret is ever committed:
 * `zzztimer.signing.storeFile` / `ZZZTIMER_SIGNING_STORE_FILE`, and likewise `storePassword`, `keyAlias`, `keyPassword`.
 * Release builds are left unsigned when they are missing.
 */
fun signingValue(name: String): String? =
    providers.gradleProperty("zzztimer.signing.$name")
        .orElse(providers.environmentVariable("ZZZTIMER_SIGNING_" + name.replace(Regex("([A-Z])"), "_$1").uppercase()))
        .orNull
        ?.takeIf { it.isNotBlank() }

android {
    namespace = "io.github.abhik9.zzztimer"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.abhik9.zzztimer"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeOf(versionMajor, versionMinor, versionPatch, versionBuild)
        versionName = "$versionMajor.$versionMinor.$versionPatch"
    }

    signingConfigs {
        val storeFile = signingValue("storeFile")
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Installable next to the release build.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        // Findings are printed in the build output (CI logs), in addition to the HTML and SARIF reports.
        textReport = true
        textOutput = file("stdout")
        // Dependency updates are handled by Dependabot, not by failing unrelated builds.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable", "OldTargetApi")
    }

    // Reproducible builds: no Google-encrypted dependency metadata in the outputs (F-Droid / IzzyOnDroid).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += setOf("kotlin-tooling-metadata.json", "**/*.kotlin_builtins", "META-INF/*.version")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
