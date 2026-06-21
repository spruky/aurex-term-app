import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing is driven entirely by environment variables that CI injects
// from GitHub repo secrets. Nothing here is committed. If the keystore env vars
// are absent (e.g. a local debug build), we fall back to the debug signing
// config so `assembleRelease` still produces an installable APK.
val keystoreEnvPresent =
    System.getenv("AUREX_KEYSTORE_BASE64") != null ||
        System.getenv("AUREX_KEYSTORE_FILE") != null

android {
    namespace = "io.spruky.aurexterm"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.spruky.aurexterm"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // proot needs the embedded ELF interpreter; keep both common arches.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (keystoreEnvPresent) {
            create("release") {
                // CI writes the decoded keystore to this path before the build.
                storeFile = file(System.getenv("AUREX_KEYSTORE_FILE") ?: "release.keystore")
                storePassword = System.getenv("AUREX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AUREX_KEY_ALIAS")
                keyPassword = System.getenv("AUREX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystoreEnvPresent) {
                signingConfigs.getByName("release")
            } else {
                // Unsigned-for-distribution fallback; debug keystore is fine for CI artifacts.
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // The Debian rootfs tarball lands in assets/bootstrap at CI time. Don't let
    // aapt try to compress it again — it's already xz/gz and huge.
    androidResources {
        noCompress += listOf("tar", "gz", "xz", "zst")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
}
