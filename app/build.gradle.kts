plugins {
 id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose")
}

val apiUrl=(project.findProperty("MARAN_API_BASE_URL") as String?) ?: "http://10.0.2.2:8000/"
val buildingRelease=gradle.startParameter.taskNames.any { it.contains("Release",ignoreCase=true) }
if(buildingRelease) {
 require(apiUrl.startsWith("https://")) {
  "Release builds require an HTTPS MARAN_API_BASE_URL. Never ship the emulator HTTP endpoint."
 }
}
val releaseStorePath=System.getenv("MARAN_KEYSTORE_PATH")

android {
 namespace="ai.maran.app"; compileSdk=36
 defaultConfig {
  applicationId="ai.maran.app"; minSdk=26; targetSdk=36; versionCode=14; versionName="0.10.1"
  buildConfigField("String","MARAN_API_BASE_URL","\"${apiUrl}\"")
  manifestPlaceholders["usesCleartextTraffic"]="false"
 }
 flavorDimensions += "distribution"
 productFlavors {
  create("play") {
   dimension="distribution"
   buildConfigField("boolean","PLAY_DISTRIBUTION","true")
  }
  create("full") {
   dimension="distribution"
   buildConfigField("boolean","PLAY_DISTRIBUTION","false")
  }
 }
 signingConfigs {
  if(!releaseStorePath.isNullOrBlank()) {
   create("release") {
    storeFile=file(releaseStorePath)
    storePassword=System.getenv("MARAN_KEYSTORE_PASSWORD")
    keyAlias=System.getenv("MARAN_KEY_ALIAS")
    keyPassword=System.getenv("MARAN_KEY_PASSWORD")
   }
  }
 }
 buildTypes {
  getByName("debug") {
   manifestPlaceholders["usesCleartextTraffic"]="true"
  }
  getByName("release") {
   manifestPlaceholders["usesCleartextTraffic"]="false"
   isMinifyEnabled=true
   isShrinkResources=true
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
   signingConfigs.findByName("release")?.let { signingConfig=it }
  }
 }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
 buildFeatures { compose=true; buildConfig=true }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.01.01"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.work:work-runtime-ktx:2.10.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 implementation("com.squareup.retrofit2:retrofit:2.11.0")
 implementation("com.squareup.retrofit2:converter-gson:2.11.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 testImplementation("junit:junit:4.13.2")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
