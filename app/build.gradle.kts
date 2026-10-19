plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.maisolo.octoeqtv"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.maisolo.octoeqtv"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    kotlinOptions { jvmTarget = "17" }
}


dependencies { testImplementation("junit:junit:4.13.2"); testImplementation("org.robolectric:robolectric:4.14.1") }
