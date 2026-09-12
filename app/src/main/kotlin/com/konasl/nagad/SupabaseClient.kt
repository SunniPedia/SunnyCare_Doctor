package com.konasl.nagad

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import android.content.Context

object SupabaseClient {
    // তোমার Supabase URL & ANON KEY এখানে বসাও
    const val SUPABASE_URL = "https://vfptbhrjdafbxpypfahq.supabase.co/"
    const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZmcHRiaHJqZGFmYnhweXBmYWhxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODkxNzkyMTgsImV4cCI6MjEwNDc1NTIxOH0.SkGoRetLYObXZGQnEas4Bla5cPN41e1MiGA744j09lg"

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }

    // Session Management - No Firebase, Only SharedPreferences
    private const val PREF_NAME = "sunnycare_session"
    private const val KEY_PHONE = "logged_phone"
    private const val KEY_NAME = "logged_name"

    fun saveLogin(context: Context, phone: String, name: String = "") {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PHONE, phone)
            .putString(KEY_NAME, name)
            .apply()
    }

    fun getLoggedPhone(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_PHONE, null)
    }

    fun isLoggedIn(context: Context): Boolean {
        return !getLoggedPhone(context).isNullOrEmpty()
    }

    fun logout(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
