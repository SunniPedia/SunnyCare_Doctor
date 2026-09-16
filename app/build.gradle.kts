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
        targetSdk = 35

        versionCode = 2
        versionName = "2.0"
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

    // ─────────────────────────────────────────
    // AndroidX
    // ─────────────────────────────────────────

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")


    // ─────────────────────────────────────────
    // Material
    // ─────────────────────────────────────────

    implementation("com.google.android.material:material:1.11.0")


    // ─────────────────────────────────────────
    // Kotlin Coroutines
    // ─────────────────────────────────────────

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")


    // ─────────────────────────────────────────
    // OkHttp
    // ─────────────────────────────────────────

    implementation("com.squareup.okhttp3:okhttp:4.12.0")


    // ─────────────────────────────────────────
    // Supabase Kotlin 2.5.1
    //
    // IMPORTANT:
    // Do NOT mix Supabase 3.x BOM with 2.5.1 modules.
    // ─────────────────────────────────────────

    implementation("io.github.jan-tennert.supabase:supabase-kt:2.5.1")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.5.1")
    implementation("io.github.jan-tennert.supabase:realtime-kt:2.5.1")
    implementation("io.github.jan-tennert.supabase:storage-kt:2.5.1")


    // ─────────────────────────────────────────
    // Ktor 2.x
    //
    // Supabase 2.5.1-এর সঙ্গে Ktor 3.0.3 ব্যবহার করবেন না।
    // ─────────────────────────────────────────

    implementation("io.ktor:ktor-client-android:2.3.12")
}
