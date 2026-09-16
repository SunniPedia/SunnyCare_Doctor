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
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.6.0")
    implementation("io.github.jan-tennert.supabase:realtime-kt:3.6.0")
    implementation("io.github.jan-tennert.supabase:storage-kt:3.6.0")
    implementation("io.github.jan-tennert.supabase:supabase-kt:3.6.0")
	implementation("androidx.viewpager2:viewpager2:1.1.0") 
	implementation("androidx.recyclerview:recyclerview:1.4.0")
}
