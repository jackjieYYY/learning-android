plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Release version comes from the Git tag in CI (-PappVersionName=1.4.0); versionCode = major*10000 + minor*100 + patch.
val appVersionName = (findProperty("appVersionName") as String?) ?: "1.5.0"
val appVersionCode = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(appVersionName)?.destructured
    ?.let { (major, minor, patch) ->
        require(minor.toInt() < 100 && patch.toInt() < 100) { "minor/patch must be < 100: $appVersionName" }
        major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
    } ?: error("appVersionName must be MAJOR.MINOR.PATCH: $appVersionName")
// Release signing is supplied only through environment variables (GitHub Actions secrets); never committed.
val releaseStoreFile = System.getenv("SIGNING_STORE_FILE")

android {
    namespace = "com.jack.englishlearning"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.jack.englishlearning"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }
    signingConfigs {
        if (releaseStoreFile != null) create("release") {
            storeFile = file(releaseStoreFile)
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug {
            buildConfigField("boolean", "UPDATES_ENABLED", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("boolean", "UPDATES_ENABLED", "true")
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("androidx.media3:media3-session:1.6.1")
    testImplementation("androidx.work:work-testing:2.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.mockito:mockito-core:5.13.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
