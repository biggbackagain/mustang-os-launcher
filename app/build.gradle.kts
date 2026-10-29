plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "com.mustangos.launcher"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mustangos.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "0.8.1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { viewBinding = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            // Preserve updates for this personal installation; never replace or export its existing key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    jvmToolchain(17)
}
dependencies {
    implementation(project(":core"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.github.mapsforge.mapsforge:mapsforge-map-android:0.30.0")
    implementation("com.github.mapsforge.mapsforge:mapsforge-map-reader:0.30.0")
    implementation("com.github.mapsforge.mapsforge:mapsforge-themes:0.30.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
