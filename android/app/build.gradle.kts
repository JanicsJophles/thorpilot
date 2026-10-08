plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val sourceRevision = runCatching {
    val process = ProcessBuilder("git", "describe", "--always", "--dirty")
        .directory(rootProject.rootDir).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    val revision = process.inputStream.bufferedReader().use { it.readText().trim() }
    if (process.waitFor() == 0 && revision.matches(Regex("[A-Za-z0-9._-]{1,80}"))) revision else "unknown"
}.getOrDefault("unknown")

android {
    buildFeatures { buildConfig = true }
    namespace = "dev.thorpilot"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.thorpilot"
        minSdk = 30
        targetSdk = 35
        testInstrumentationRunner = providers.gradleProperty("thorpilotTestRunner").orElse("dev.thorpilot.DeviceChecks").get()
        buildConfigField("String", "SOURCE_REVISION", "\"$sourceRevision\"")
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
