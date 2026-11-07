import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "clone.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "clone.app"          // replica-brand renames this
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Product id of the one-time unlock, created by you in Play Console. Empty = billing shows "not set up".
        buildConfigField("String", "PLAY_UNLOCK_PRODUCT_ID", "\"${localProps.getProperty("play.unlockProductId", "")}\"")
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.ink.authoring.compose)
    implementation(libs.ink.brush)
    implementation(libs.ink.geometry)
    implementation(libs.ink.rendering)
    implementation(libs.ink.storage)
    implementation(libs.ink.strokes)
    implementation(libs.work.runtime.ktx)
    implementation(libs.billing.ktx)
    implementation(libs.play.services.auth)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)      // org.json is only a stub in plain JVM tests
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
