plugins { id("com.android.application") }
android {
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")
    namespace = "com.aysuio.opentypeless"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.aysuio.opentypeless"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    sourceSets["main"].assets.srcDir("../../shared")
    buildTypes { release { isMinifyEnabled = false } }
}
