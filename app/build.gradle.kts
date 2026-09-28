plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "ai.maran.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "ai.maran.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
