plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing secrets come from Gradle properties (e.g. ~/.gradle/gradle.properties)
// or environment variables of the same name. When any is missing, release builds
// stay unsigned (app-release-unsigned.apk) so local/CI builds without secrets work.
val releaseSigning: Map<String, String>? = listOf(
    "PRAYERWHEEL_KEYSTORE_FILE",
    "PRAYERWHEEL_KEYSTORE_PASSWORD",
    "PRAYERWHEEL_KEY_ALIAS",
    "PRAYERWHEEL_KEY_PASSWORD",
).associateWith { name ->
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull
}.takeIf { values -> values.values.none { it.isNullOrBlank() } }
    ?.mapValues { it.value!! }

android {
    namespace = "com.prayerwheel.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.prayerwheel.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getValue("PRAYERWHEEL_KEYSTORE_FILE"))
                storePassword = releaseSigning.getValue("PRAYERWHEEL_KEYSTORE_PASSWORD")
                keyAlias = releaseSigning.getValue("PRAYERWHEEL_KEY_ALIAS")
                keyPassword = releaseSigning.getValue("PRAYERWHEEL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigning != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Navigation (for future screens)
    implementation(libs.navigation.compose)

    // Glance (home-screen widget)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // WorkManager (background session save)
    implementation(libs.androidx.work.runtime.ktx)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
