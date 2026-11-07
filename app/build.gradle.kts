plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    namespace = "app.mannybrowser"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.mannybrowser"
        minSdk = 26
        targetSdk = 34
        // CI stamps versionCode from the Actions run number (-PmannyVersionCode=N) so every
        // published build is strictly newer - the in-app updater compares versionCode,
        // not versionName. max() with the source floor matters: if CI run numbering ever
        // resets, raising this floor above the last published versionCode keeps updates
        // flowing instead of stalling below the installed base (installed builds carry 1).
        val stampedVersionCode = providers.gradleProperty("mannyVersionCode").orNull?.toIntOrNull() ?: 0
        versionCode = maxOf(stampedVersionCode, 2)
        // Semantic version. Every merge intended to produce a release bumps this; the
        // publish job skips cleanly when the tag exists.
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (!System.getenv("MANNY_KEYSTORE_PATH").isNullOrBlank()) {
            create("stable") {
                storeFile = file(System.getenv("MANNY_KEYSTORE_PATH"))
                storePassword = System.getenv("MANNY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MANNY_KEY_ALIAS")
                keyPassword = System.getenv("MANNY_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        debug {
            // When CI restores the stable key, the debug variant (the shipped package
            // app.mannybrowser) is signed with it; local builds keep the per-machine key.
            if (!System.getenv("MANNY_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
    }
    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    testImplementation("junit:junit:4.13.2")
    // Real org.json on the JVM so the update-manifest parser is unit-testable.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.10.0")
}
