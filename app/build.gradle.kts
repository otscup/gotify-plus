import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    // FCM: reads app/google-services.json (register com.gotify.client in the
    // Firebase console first; the file is intentionally not in the repo).
    // `apply false`: the plugin is applied below only when the file exists, so
    // builds without Firebase config (local checkouts) still compile.
    alias(libs.plugins.google.services) apply false
}

// google-services.json is git-ignored. Apply the Google Services plugin only
// when it is present: CI restores it from the GOOGLE_SERVICES_JSON_BASE64 secret,
// local builds read it from app/google-services.json when the user adds it.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace   = "com.gotify.client"
    compileSdk  = 37

    defaultConfig {
        applicationId = "com.gotify.client"
        minSdk        = 26
        targetSdk     = 37
        // CI run number keeps every published release upgradable over the previous one.
        // v1.1.0 on user devices is versionCode 5; never build below 6.
        versionCode   = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 6).coerceAtLeast(6)
        versionName   = "1.1.0"
    }

    // Release signing comes from env vars set by CI (see .github/workflows/release.yml).
    // Absent locally -> release builds stay unsigned, same as before.
    val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile     = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias      = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword   = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Separate package so a debug-key install never blocks installing the signed release.
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
            isDebuggable   = true
            buildConfigField("Boolean", "IS_DEBUG", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "IS_DEBUG", "false")
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }



    buildFeatures {
        compose     = true
        buildConfig = true
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}


kotlin {
    compilerOptions {

        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)


    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)


    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)


    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)


    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)


    // FCM push while keep-alive is off (firebase-bom manages the versions)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)


    implementation(libs.coil.compose)


    implementation(libs.compose.markdown)


    implementation(libs.datastore.preferences)


    // UnifiedPush distributor: this app acts as the push distributor for
    // other apps (e.g. Nagram); the server side is gotify-cf (/up/*).
    implementation(libs.unifiedpush.distributor)
    implementation(libs.work.runtime.ktx)


    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    testImplementation(libs.junit)
}
