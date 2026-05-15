import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
fun signingValue(key: String): String? =
    System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.tarnlabs.allergybuster"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tarnlabs.allergybuster"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("ALLERGYBUSTER_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("ALLERGYBUSTER_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = signingValue("ALLERGYBUSTER_KEYSTORE_PASSWORD")
                keyAlias = signingValue("ALLERGYBUSTER_KEY_ALIAS")
                keyPassword = signingValue("ALLERGYBUSTER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        disable += "NullSafeMutableLiveData"
        checkReleaseBuilds = true
        abortOnError = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

// All appcompat versions have a widget_description string containing {str} which AAPT2 in
// AGP 8.7.3 rejects as an invalid escape at compile time — before our string override applies.
// This app uses ComponentActivity + Material3 + Compose and has zero AppCompat API usage,
// so we exclude appcompat from every configuration entirely.
configurations.all {
    exclude(group = "androidx.appcompat", module = "appcompat")
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Glance widget
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Core
    implementation(libs.core.ktx)

    // Debug
    debugImplementation(libs.compose.ui.tooling)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
