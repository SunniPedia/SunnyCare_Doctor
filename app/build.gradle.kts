plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.konasl.nagad"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.konasl.nagad"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
	// Supabase - Full Stack (No Firebase at all)
    // Postgrest = Database (profiles, otps, appointments)
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.5.1")
    // Realtime = Chat (future)
    implementation("io.github.jan-tennert.supabase:realtime-kt:2.5.1")
    // Storage = Reports upload (future)
    implementation("io.github.jan-tennert.supabase:storage-kt:2.5.1")
    // Supabase KT = Main client
    implementation("io.github.jan-tennert.supabase:supabase-kt:2.5.1")
}
