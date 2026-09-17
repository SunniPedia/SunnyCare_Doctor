buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        // FIX: AGP 8.9.1 → 9.4.0 (সেপ্টেম্বর ২০২৬ পর্যন্ত সর্বশেষ স্টেবল)
        classpath("com.android.tools.build:gradle:9.4.0")
        // FIX: Kotlin Gradle Plugin 1.9.0 → 2.2.21 (AGP 9.4 এর সাথে অফিসিয়ালি টেস্ট করা ভার্সন)
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    }
}
