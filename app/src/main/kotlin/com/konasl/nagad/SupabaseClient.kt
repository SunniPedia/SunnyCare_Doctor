package com.konasl.nagad

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ============================================================================
 * SUPABASE SETUP NOTE (Firebase ব্যবহার করা হয়নি, শুধু Supabase REST API)
 * ============================================================================
 * Supabase Project > SQL Editor এ নিচের টেবিলগুলো বানান:
 *
 * create table otp_codes (
 *   id bigint generated always as identity primary key,
 *   code text not null,
 *   phone text,
 *   status text not null default 'available', -- available / assigned / verified
 *   assigned_at timestamptz,
 *   expires_at timestamptz,
 *   created_at timestamptz default now()
 * );
 * -- ১০,০০০+ র‍্যান্ডম ৬ ডিজিট OTP অটো জেনারেট করে ইনসার্ট করতে:
 * insert into otp_codes (code)
 * select lpad(floor(random()*1000000)::text, 6, '0') from generate_series(1, 10000);
 *
 * create table patients (
 *   id uuid primary key default gen_random_uuid(),
 *   phone text unique not null,
 *   full_name text not null,
 *   age int,
 *   gender text,
 *   blood_group text,
 *   address text,
 *   emergency_contact text,
 *   medical_history text,
 *   created_at timestamptz default now()
 * );
 *
 * create table appointments (
 *   id uuid primary key default gen_random_uuid(),
 *   patient_id uuid references patients(id),
 *   patient_name text,
 *   phone text,
 *   reason text,
 *   preferred_date text,
 *   preferred_time text,
 *   status text default 'pending', -- pending / confirmed / completed / cancelled
 *   created_at timestamptz default now()
 * );
 *
 * -- ডেমো অ্যাপের জন্য RLS বন্ধ রাখুন (Table > RLS disable) অথবা anon-friendly policy দিন:
 * alter table otp_codes disable row level security;
 * alter table patients disable row level security;
 * alter table appointments disable row level security;
 * ============================================================================
 */
object SupabaseClient {

    // TODO: নিজের Supabase প্রজেক্ট থেকে বসান (Project Settings > API)
    private const val SUPABASE_URL = "https://YOUR_PROJECT_ID.supabase.co"
    private const val SUPABASE_ANON_KEY = "YOUR_SUPABASE_ANON_KEY"

    private const val PREFS = "sunnycare_prefs"
    private const val KEY_LOGGED_IN = "is_logged_in"
    private const val KEY_PATIENT_ID = "patient_id"
    private const val KEY_PHONE = "phone"
    private const val KEY_NAME = "full_name"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json".toMediaType()

    // ---------------------------------------------------------------------
    // SESSION (SharedPreferences)
    // ---------------------------------------------------------------------
    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LOGGED_IN, false)
    }

    fun saveSession(context: Context, patientId: String, phone: String, fullName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_PATIENT_ID, patientId)
            .putString(KEY_PHONE, phone)
            .putString(KEY_NAME, fullName)
            .apply()
    }

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun getPatientId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PATIENT_ID, null)

    fun getPhone(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PHONE, null)

    fun getName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null)

    // ---------------------------------------------------------------------
    // LOW LEVEL REST HELPERS
    // ---------------------------------------------------------------------
    private fun baseRequest(path: String): Request.Builder {
        return Request.Builder()
            .url("$SUPABASE_URL/rest/v1/$path")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
    }

    private suspend fun get(path: String): JSONArray = withContext(Dispatchers.IO) {
        val req = baseRequest(path).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("GET $path failed: ${resp.code} $body")
            JSONArray(body)
        }
    }

    private suspend fun post(path: String, json: JSONObject): JSONArray = withContext(Dispatchers.IO) {
        val req = baseRequest(path)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post(json.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("POST $path failed: ${resp.code} $body")
            JSONArray(body)
        }
    }

    private suspend fun patch(path: String, json: JSONObject): JSONArray = withContext(Dispatchers.IO) {
        val req = baseRequest(path)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .patch(json.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("PATCH $path failed: ${resp.code} $body")
            JSONArray(body)
        }
    }

    // ---------------------------------------------------------------------
    // OTP FLOW (using pre-generated otp_codes pool - no real SMS gateway)
    // ---------------------------------------------------------------------

    /** একটা unused OTP রিজার্ভ করে এই ফোন নাম্বারের নামে অ্যাসাইন করে, কোডটা রিটার্ন করে */
    suspend fun requestOtp(phone: String): Result<String> = try {
        val available = get("otp_codes?status=eq.available&limit=1&order=id.asc")
        if (available.length() == 0) {
            Result.failure(Exception("OTP পুল খালি, অ্যাডমিনকে জানান"))
        } else {
            val row = available.getJSONObject(0)
            val id = row.getLong("id")
            val code = row.getString("code")

            val updateBody = JSONObject().apply {
                put("status", "assigned")
                put("phone", phone)
                put("assigned_at", "now()")
            }
            patch("otp_codes?id=eq.$id", updateBody)
            Result.success(code)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** ইউজারের দেওয়া কোড এই ফোন নাম্বারের সাথে assigned অবস্থায় মিলছে কিনা চেক করে */
    suspend fun verifyOtp(phone: String, code: String): Result<Boolean> = try {
        val rows = get(
            "otp_codes?phone=eq.$phone&code=eq.$code&status=eq.assigned&order=id.desc&limit=1"
        )
        if (rows.length() == 0) {
            Result.success(false)
        } else {
            val id = rows.getJSONObject(0).getLong("id")
            patch("otp_codes?id=eq.$id", JSONObject().put("status", "verified"))
            Result.success(true)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ---------------------------------------------------------------------
    // PATIENT
    // ---------------------------------------------------------------------

    /** ফোন নাম্বার আগে থেকে রেজিস্টার্ড কিনা চেক করে; থাকলে patient JSONObject রিটার্ন করে */
    suspend fun findPatientByPhone(phone: String): Result<JSONObject?> = try {
        val rows = get("patients?phone=eq.$phone&limit=1")
        Result.success(if (rows.length() > 0) rows.getJSONObject(0) else null)
    } catch (e: Exception) {
        Result.failure(e)
    }

    data class NewPatient(
        val phone: String,
        val fullName: String,
        val age: Int?,
        val gender: String,
        val bloodGroup: String,
        val address: String,
        val emergencyContact: String,
        val medicalHistory: String
    )

    suspend fun registerPatient(p: NewPatient): Result<JSONObject> = try {
        val json = JSONObject().apply {
            put("phone", p.phone)
            put("full_name", p.fullName)
            put("age", p.age ?: JSONObject.NULL)
            put("gender", p.gender)
            put("blood_group", p.bloodGroup)
            put("address", p.address)
            put("emergency_contact", p.emergencyContact)
            put("medical_history", p.medicalHistory)
        }
        val rows = post("patients", json)
        Result.success(rows.getJSONObject(0))
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ---------------------------------------------------------------------
    // APPOINTMENTS
    // ---------------------------------------------------------------------

    suspend fun createAppointment(
        patientId: String,
        patientName: String,
        phone: String,
        reason: String,
        date: String,
        time: String
    ): Result<JSONObject> = try {
        val json = JSONObject().apply {
            put("patient_id", patientId)
            put("patient_name", patientName)
            put("phone", phone)
            put("reason", reason)
            put("preferred_date", date)
            put("preferred_time", time)
            put("status", "pending")
        }
        val rows = post("appointments", json)
        Result.success(rows.getJSONObject(0))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getAppointments(patientId: String): Result<JSONArray> = try {
        val rows = get("appointments?patient_id=eq.$patientId&order=created_at.desc")
        Result.success(rows)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
