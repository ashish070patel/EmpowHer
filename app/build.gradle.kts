plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services) // Apply the Google Services plugin
}

android {
    namespace = "com.example.womensafety"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.womensafety"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // AndroidX dependencies
    implementation(libs.androidx.appcompat) // Correct reference to appcompat
    implementation(libs.androidx.material) // Correct reference to material
    implementation(libs.androidx.activity) // Correct reference to activity
    implementation(libs.androidx.constraintlayout) // Correct reference to constraintlayout

    // Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:32.8.0")) // Firebase BoM
    implementation("com.google.firebase:firebase-database") // Firebase Realtime Database
    implementation("com.google.firebase:firebase-analytics") // Optional: Firebase Analytics

    // Testing dependencies
    testImplementation(libs.junit) // Correct reference to junit
    androidTestImplementation(libs.androidx.ext.junit) // Correct reference to extJunit
    androidTestImplementation(libs.androidx.espresso.core) // Correct reference to espressoCore
    implementation ("de.hdodenhof:circleimageview:3.1.0")

        implementation ("com.google.firebase:firebase-auth:22.1.1")
        implementation ("com.google.firebase:firebase-database:20.3.1")
    implementation ("com.google.android.gms:play-services-maps:18.2.0")
    implementation ("com.google.android.gms:play-services-location:21.0.1")
    implementation ("com.google.android.libraries.places:places:3.2.0")
    implementation ("androidx.recyclerview:recyclerview:1.3.1") // For RecyclerView
    implementation ("androidx.core:core-ktx:1.10.1") // For AndroidX Core


}