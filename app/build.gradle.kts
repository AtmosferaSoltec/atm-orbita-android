import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// The URL of the Orbita API lives in local.properties, which is not committed (docs/04).
// Without it the app runs on the in-memory demo data.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun localProperty(name: String): String = localProperties.getProperty(name, "").trim()

android {
    namespace = "com.atmosferast.orbita"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.atmosferast.orbita"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // No key or secret ever goes here: the app only knows where the API is.
        buildConfigField("String", "API_BASE_URL", "\"${localProperty("API_BASE_URL")}\"")
        // atm-auth-api, where every sign-in/up/out, renewal and password call goes.
        buildConfigField("String", "AUTH_API_BASE_URL", "\"${localProperty("AUTH_API_BASE_URL")}\"")
        // A public identifier (not a secret): the Google server client ID auth-api accepts.
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${localProperty("GOOGLE_SERVER_CLIENT_ID")}\"")
    }

    signingConfigs {
        // The upload key, from local.properties (never committed). Without it the release
        // build comes out unsigned.
        val storeFile = localProperty("RELEASE_STORE_FILE")
        if (storeFile.isNotBlank()) {
            create("release") {
                this.storeFile = rootProject.file(storeFile)
                storePassword = localProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // R8: shrinks, obfuscates and optimizes. Its mapping.txt travels inside the .aab, so
            // Play Console deobfuscates crash reports by itself.
            optimization {
                enable = true
                keepRules {
                    files.add(getDefaultProguardFile("proguard-android-optimize.txt"))
                    files.add(file("proguard-rules.pro"))
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// A release without the API URLs would ship on the demo data (or with no way to sign in): stop it.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.project == project && it.name.contains("Release") && !it.name.startsWith("lint") }) {
        for (name in listOf("API_BASE_URL", "AUTH_API_BASE_URL")) {
            check(localProperty(name).isNotBlank()) { "$name is empty in local.properties: a release build needs it." }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    // Sign in with Google: the system account picker, which hands out an ID token.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.googleid)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
