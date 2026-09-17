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
            // Note: no signingConfig here on purpose.
            // The release workflow (Android Release Build) signs the APK
            // manually via zipalign + apksigner using the release.jks
            // decoded from KEYSTORE_BASE64, so `gradle assembleRelease`
            // is expected to output an *-unsigned.apk which the workflow
            // then signs. Do not add signingConfigs.release here unless
            // you also update the workflow to stop re-signing.
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

    // FIX: Supabase-kt + Ktor + OkHttp + Coroutines together commonly
    // bundle duplicate META-INF metadata files across their jars.
    // This passes locally (Gradle may just warn) but can hard-fail
    // `assembleRelease` on a clean CI runner. Excluding these avoids
    // "More than one file was found with OS independent path ..." errors.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/*.kotlin_module"
            )
        }
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
    //
    // IMPORTANT FIX:
    // ktor-client-android ইঞ্জিন WebSocket সাপোর্ট করে না, তাই
    // Supabase Realtime-এর channel.subscribe() কখনো সম্পূর্ণ হতো না
    // ("কানেক্ট হচ্ছে..." তে আটকে থাকা)। ktor-client-okhttp
    // ব্যবহার করলে WebSocket ঠিকমতো কাজ করবে, আর প্রজেক্টে এমনিতেই
    // OkHttp ডিপেন্ডেন্সি আছে বলে এটা সবচেয়ে সামঞ্জস্যপূর্ণ পছন্দ।
    // ─────────────────────────────────────────

    implementation("io.ktor:ktor-client-okhttp:2.3.12")
}
