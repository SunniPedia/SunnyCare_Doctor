package com.konasl.nagad

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/**
 * ডিভাইস আইডেন্টিফায়ার হেল্পার।
 *
 * Android এর নিজস্ব ANDROID_ID ব্যবহার করা হয়েছে — এই আইডি একই অ্যাপ সিগনেচার
 * এবং একই ডিভাইসের জন্য সবসময় একই থাকে, এমনকি অ্যাপ আনইন্সটল করে আবার
 * ইন্সটল করলেও (Android 8.0+ এ)। শুধুমাত্র ডিভাইস ফ্যাক্টরি-রিসেট করলে বা
 * অ্যাপের সাইনিং কি পরিবর্তন হলে এই আইডি বদলে যায়।
 *
 * এই কারণেই এটা "একটা ডিভাইসে একটা একাউন্ট" — এই বাঁধন তৈরির জন্য ব্যবহার করা
 * হচ্ছে, PIN এর পাশাপাশি একটা দ্বিতীয় লেয়ার হিসেবে। সত্যিকারের সিম-বাইন্ডিং
 * (IMSI/ICCID) আধুনিক Android এ থার্ড-পার্টি অ্যাপের জন্য প্র্যাক্টিক্যালি
 * বন্ধ (privilege লাগে), তাই ডিভাইস-বাইন্ডিং + OTP-ভেরিফায়েড ফোন নাম্বার —
 * এই দুটো মিলিয়ে একই কাজ (এক নাম্বার + এক ডিভাইস = এক একাউন্ট) অর্জন করা
 * হয়েছে।
 */
object DeviceUtils {

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (id.isNullOrBlank()) "unknown-device" else id
    }
}
