plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.recipealarm"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.recipealarm"
        minSdk = 35
        targetSdk = 36
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
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // DataStore for local data storage
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Gson for JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")
}