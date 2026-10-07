plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.thorpilot"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.thorpilot"
        minSdk = 30
        targetSdk = 35
        testInstrumentationRunner = providers.gradleProperty("thorpilotTestRunner").orElse("dev.thorpilot.DeviceChecks").get()
        versionCode = 2
        versionName = "0.1.0-preview.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies { testImplementation("junit:junit:4.13.2") }
