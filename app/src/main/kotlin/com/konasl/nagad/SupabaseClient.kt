package com.konasl.nagad

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
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
 *   password_salt text,
 *   password_hash text,
 *   device_id text,
 *   profile_picture_url text,
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
 *   status text default 'pending',
 *   payment_method text,
 *   fee integer default 800,
 *   transaction_id text,
 *   payment_status text default 'not_applicable',
 *   report_url text,
 *   slot_open boolean not null default false,
 *   created_at timestamptz default now()
 * );
 *
 * create table if not exists slot_settings (
 *   value24 text primary key,
 *   enabled boolean not null default true
 * );
 *
 * create table if not exists slot_date_overrides (
 *   id bigint generated always as identity primary key,
 *   slot_date text not null,
 *   value24 text not null,
 *   enabled boolean not null,
 *   unique(slot_date, value24)
 * );
 *
 * alter table otp_codes disable row level security;
 * alter table patients disable row level security;
 * alter table appointments disable row level security;
 * alter table slot_settings disable row level security;
 * alter table slot_date_overrides disable row level security;
 *
 * insert into storage.buckets (id, name, public)
 * values ('test-reports', 'test-reports', true)
 * on conflict (id) do nothing;
 *
 * create policy "Allow anon uploads to test-reports"
 * on storage.objects for insert to anon with check (bucket_id = 'test-reports');
 *
 * create policy "Allow public read of test-reports"
 * on storage.objects for select to anon using (bucket_id = 'test-reports');
 * ============================================================================
 */
object SupabaseClient {

    // TODO: নিজের Supabase প্রজেক্ট থেকে বসান (Project Settings > API)
    private const val SUPABASE_URL = "https://azbleibkgerzaqbrrydl.supabase.co/"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF6YmxlaWJrZ2VyemFxYnJyeWRsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODkxNzExNjYsImV4cCI6MjEwNDc0NzE2Nn0.6Q6PMcRJHXFIRPUZZf9lOjoTmq77_wbCoKc8tGkVF2o"

    // Storage bucket যেখানে রোগীদের টেস্ট রিপোর্ট (ছবি/PDF) জমা হয়
    private const val REPORTS_BUCKET = "test-reports"

    // প্রোফাইল ছবির জন্য আলাদা bucket
    private const val PROFILE_BUCKET = "profile-pictures"

    private const val PREFS = "sunnycare_prefs"
    private const val KEY_LOGGED_IN = "is_logged_in"
    private const val KEY_PATIENT_ID = "patient_id"
    private const val KEY_PHONE = "phone"
    private const val KEY_NAME = "full_name"

    // TODO: আপনার (ডাক্তার/ক্লিনিক অ্যাডমিনের) ফোন নাম্বার এখানে বসান
    private val adminPhones = listOf("+8801710355342")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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

    fun isAdmin(context: Context): Boolean {
        val phone = getPhone(context) ?: return false
        return adminPhones.contains(phone)
    }

    // ---------------------------------------------------------------------
    // LOW LEVEL REST HELPERS
    // ---------------------------------------------------------------------
    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

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

    private suspend fun postWithPrefer(path: String, json: JSONObject, prefer: String): Unit = withContext(Dispatchers.IO) {
        val req = baseRequest(path)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", prefer)
            .post(json.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                throw IOException("POST $path failed: ${resp.code} $body")
            }
        }
    }

    /** নতুন: JSONArray (bulk) POST করার জন্য — একসাথে অনেকগুলো OTP কোড যোগ করতে ব্যবহৃত হয় */
    private suspend fun postArray(path: String, jsonArray: JSONArray, prefer: String = "return=minimal"): Unit = withContext(Dispatchers.IO) {
        val req = baseRequest(path)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", prefer)
            .post(jsonArray.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                throw IOException("POST(array) $path failed: ${resp.code} $body")
            }
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

    private suspend fun delete(path: String): Unit = withContext(Dispatchers.IO) {
        val req = baseRequest(path)
            .delete()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                throw IOException("DELETE $path failed: ${resp.code} $body")
            }
        }
    }

    // ---------------------------------------------------------------------
    // PIN HASHING
    // ---------------------------------------------------------------------
    private fun sha256Hex(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPassword(pin: String, salt: String): String = sha256Hex("$salt:$pin")

    // ---------------------------------------------------------------------
    // OTP FLOW
    // ---------------------------------------------------------------------

    private const val OTP_VALIDITY_MINUTES = 2

    private fun isoTimeNowPlusMinutes(minutes: Int): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.MINUTE, minutes)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(cal.time)
    }

    private fun parseIsoTime(iso: String): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val cleaned = iso.replace("Z", "").substringBefore(".")
        return sdf.parse(cleaned)?.time ?: 0L
    }

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
                put("expires_at", isoTimeNowPlusMinutes(OTP_VALIDITY_MINUTES))
            }
            patch("otp_codes?id=eq.$id", updateBody)
            Result.success(code)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun verifyOtp(phone: String, code: String): Result<Boolean> = try {
        val rows = get(
            "otp_codes?phone=eq.${enc(phone)}&code=eq.${enc(code)}&status=eq.assigned&order=id.desc&limit=1"
        )
        if (rows.length() == 0) {
            Result.success(false)
        } else {
            val obj = rows.getJSONObject(0)
            val id = obj.getLong("id")
            val expiresAt = obj.optString("expires_at", "")
            val expired = expiresAt.isNotEmpty() && parseIsoTime(expiresAt) < System.currentTimeMillis()
            if (expired) {
                patch("otp_codes?id=eq.$id", JSONObject().put("status", "expired"))
                Result.success(false)
            } else {
                patch("otp_codes?id=eq.$id", JSONObject().put("status", "verified"))
                Result.success(true)
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ---------------------------------------------------------------------
    // PATIENT
    // ---------------------------------------------------------------------

    suspend fun findPatientByPhone(phone: String): Result<JSONObject?> = try {
        val rows = get("patients?phone=eq.${enc(phone)}&limit=1")
        Result.success(if (rows.length() > 0) rows.getJSONObject(0) else null)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun findPatientByDeviceId(deviceId: String, excludePatientId: String? = null): Result<JSONObject?> = try {
        var path = "patients?device_id=eq.${enc(deviceId)}&limit=1"
        if (excludePatientId != null) path += "&id=neq.${enc(excludePatientId)}"
        val rows = get(path)
        Result.success(if (rows.length() > 0) rows.getJSONObject(0) else null)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun bindDeviceToPatient(patientId: String, deviceId: String): Result<Unit> = try {
        patch("patients?id=eq.${enc(patientId)}", JSONObject().put("device_id", deviceId))
        Result.success(Unit)
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
        val medicalHistory: String,
        val pin: String,
        val deviceId: String,
        val profilePictureUrl: String = ""
    )

    suspend fun registerPatient(p: NewPatient): Result<JSONObject> = try {
        val salt = generateSalt()
        val hash = hashPassword(p.pin, salt)
        val json = JSONObject().apply {
            put("phone", p.phone)
            put("full_name", p.fullName)
            put("age", p.age ?: JSONObject.NULL)
            put("gender", p.gender)
            put("blood_group", p.bloodGroup)
            put("address", p.address)
            put("emergency_contact", p.emergencyContact)
            put("medical_history", p.medicalHistory)
            put("password_salt", salt)
            put("password_hash", hash)
            put("device_id", p.deviceId)
            if (p.profilePictureUrl.isNotEmpty()) put("profile_picture_url", p.profilePictureUrl)
        }
        val rows = post("patients", json)
        Result.success(rows.getJSONObject(0))
    } catch (e: Exception) {
        val friendly = if (e.message?.contains("duplicate", ignoreCase = true) == true)
            "এই ডিভাইসে ইতিমধ্যে একটি একাউন্ট যুক্ত আছে অথবা নাম্বারটি আগেই রেজিস্টার্ড"
        else e.message
        Result.failure(Exception(friendly, e))
    }

    suspend fun setPatientPassword(patientId: String, newPin: String): Result<Unit> = try {
        val salt = generateSalt()
        val hash = hashPassword(newPin, salt)
        val json = JSONObject().apply {
            put("password_salt", salt)
            put("password_hash", hash)
        }
        patch("patients?id=eq.${enc(patientId)}", json)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun verifyPatientPassword(patient: JSONObject, enteredPin: String): Result<Boolean> = try {
        val salt = patient.optString("password_salt", "")
        val hash = patient.optString("password_hash", "")
        if (salt.isEmpty() || hash.isEmpty()) {
            Result.success(false)
        } else {
            Result.success(hashPassword(enteredPin, salt) == hash)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteAccount(patientId: String): Result<Unit> = try {
        delete("appointments?patient_id=eq.${enc(patientId)}")
        delete("patients?id=eq.${enc(patientId)}")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ---------------------------------------------------------------------
    // STORAGE
    // ---------------------------------------------------------------------
    suspend fun uploadReportFile(fileName: String, mimeType: String, bytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val safeName = "${System.currentTimeMillis()}_${fileName.replace(Regex("[^A-Za-z0-9.-]"), "_")}"
                val objectPath = "reports/$safeName"
                val mediaType = mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()

                val req = Request.Builder()
                    .url("$SUPABASE_URL/storage/v1/object/$REPORTS_BUCKET/$objectPath")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    .addHeader("x-upsert", "true")
                    .post(bytes.toRequestBody(mediaType))
                    .build()

                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure<String>(IOException("রিপোর্ট আপলোড ব্যর্থ: ${resp.code} $body"))
                    }
                }

                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$REPORTS_BUCKET/$objectPath"
                Result.success(publicUrl)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun uploadProfilePicture(fileName: String, mimeType: String, bytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val safeName = "${System.currentTimeMillis()}_${fileName.replace(Regex("[^A-Za-z0-9.-]"), "_")}"
                val objectPath = "avatars/$safeName"
                val mediaType = mimeType.toMediaTypeOrNull() ?: "image/jpeg".toMediaType()

                val req = Request.Builder()
                    .url("$SUPABASE_URL/storage/v1/object/$PROFILE_BUCKET/$objectPath")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    .addHeader("x-upsert", "true")
                    .post(bytes.toRequestBody(mediaType))
                    .build()

                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure<String>(IOException("প্রোফাইল ছবি আপলোড ব্যর্থ: ${resp.code} $body"))
                    }
                }

                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$PROFILE_BUCKET/$objectPath"
                Result.success(publicUrl)
            } catch (e: Exception) {
                Result.failure(e)
            }
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
        time: String,
        paymentMethod: String = "",
        fee: Int = 800,
        transactionId: String = "",
        paymentStatus: String = "not_applicable",
        reportUrl: String = ""
    ): Result<JSONObject> = try {
        val json = JSONObject().apply {
            put("patient_id", patientId)
            put("patient_name", patientName)
            put("phone", phone)
            put("reason", reason)
            put("preferred_date", date)
            put("preferred_time", time)
            put("status", "pending")
            put("payment_method", paymentMethod)
            put("fee", fee)
            put("transaction_id", transactionId)
            put("payment_status", paymentStatus)
            if (reportUrl.isNotEmpty()) put("report_url", reportUrl)
        }
        val rows = post("appointments", json)
        Result.success(rows.getJSONObject(0))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getAppointments(patientId: String): Result<JSONArray> = try {
        val rows = get("appointments?patient_id=eq.${enc(patientId)}&order=created_at.desc")
        Result.success(rows)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getAppointmentById(appointmentId: String): Result<JSONObject?> = try {
        val rows = get("appointments?id=eq.${enc(appointmentId)}&limit=1")
        Result.success(if (rows.length() > 0) rows.getJSONObject(0) else null)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getBookedTimes(date: String): Result<List<String>> = try {
        val rows = get("appointments?preferred_date=eq.${enc(date)}&status=neq.cancelled&select=preferred_time")
        val times = mutableListOf<String>()
        for (i in 0 until rows.length()) {
            val t = rows.getJSONObject(i).optString("preferred_time", "")
            if (t.isNotEmpty()) times.add(t)
        }
        Result.success(times)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ---------------------------------------------------------------------
    // ADMIN — টাইম-স্লট এনাবল/ডিজেবল
    // ---------------------------------------------------------------------
    suspend fun getSlotSettings(): Result<Map<String, Boolean>> = try {
        val rows = get("slot_settings?select=value24,enabled")
        val map = mutableMapOf<String, Boolean>()
        for (i in 0 until rows.length()) {
            val o = rows.getJSONObject(i)
            map[o.getString("value24")] = o.optBoolean("enabled", true)
        }
        Result.success(map)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getSlotDateOverrides(date: String): Result<Map<String, Boolean>> = try {
        val rows = get("slot_date_overrides?slot_date=eq.${enc(date)}&select=value24,enabled")
        val map = mutableMapOf<String, Boolean>()
        for (i in 0 until rows.length()) {
            val o = rows.getJSONObject(i)
            map[o.getString("value24")] = o.getBoolean("enabled")
        }
        Result.success(map)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun setSlotEnabled(value24: String, enabled: Boolean): Result<Unit> = try {
        val json = JSONObject().put("value24", value24).put("enabled", enabled)
        postWithPrefer("slot_settings", json, "resolution=merge-duplicates,return=minimal")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun setSlotDateOverride(date: String, value24: String, enabled: Boolean): Result<Unit> = try {
        val json = JSONObject().put("slot_date", date).put("value24", value24).put("enabled", enabled)
        postWithPrefer("slot_date_overrides", json, "resolution=merge-duplicates,return=minimal")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteSlotDateOverride(date: String, value24: String): Result<Unit> = try {
        delete("slot_date_overrides?slot_date=eq.${enc(date)}&value24=eq.${enc(value24)}")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ---------------------------------------------------------------------
    // ADMIN — পেমেন্ট ভেরিফিকেশন
    // ---------------------------------------------------------------------
    suspend fun getPendingVerificationAppointments(): Result<JSONArray> = try {
        val rows = get("appointments?payment_status=eq.pending_verification&order=created_at.desc")
        Result.success(rows)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun verifyPayment(appointmentId: String): Result<Unit> = try {
        val json = JSONObject().apply {
            put("payment_status", "verified")
            put("status", "confirmed")
        }
        patch("appointments?id=eq.${enc(appointmentId)}", json)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun rejectPayment(appointmentId: String): Result<Unit> = try {
        val json = JSONObject().apply {
            put("payment_status", "rejected")
            put("status", "cancelled")
        }
        patch("appointments?id=eq.${enc(appointmentId)}", json)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // =======================================================================
    // ==========            নতুন — Admin.kt এর জন্য                ==========
    // ==========   (SupabaseClient এর সব ডেটা এডমিন পর্যবেক্ষণ ও   ==========
    // ==========          ম্যানেজ করতে পারবে এখানকার              ==========
    // ==========          ফাংশনগুলো দিয়ে)                          ==========
    // =======================================================================

    /** এডমিন প্যানেল — সব রোগীর সম্পূর্ণ লিস্ট (নতুন থেকে পুরনো) */
    suspend fun adminGetAllPatients(): Result<JSONArray> = try {
        val rows = get("patients?select=*&order=created_at.desc")
        Result.success(rows)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** এডমিন প্যানেল — নির্দিষ্ট কোনো রোগীর যেকোনো ফিল্ড (JSONObject আকারে) আপডেট */
    suspend fun adminUpdatePatient(patientId: String, fields: JSONObject): Result<Unit> = try {
        patch("patients?id=eq.${enc(patientId)}", fields)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** এডমিন প্যানেল — রোগী মুছে ফেলা (তার সব অ্যাপয়েন্টমেন্টসহ) */
    suspend fun adminDeletePatient(patientId: String): Result<Unit> = deleteAccount(patientId)

    /** এডমিন প্যানেল — সব অ্যাপয়েন্টমেন্টের সম্পূর্ণ লিস্ট (নতুন থেকে পুরনো) */
    suspend fun adminGetAllAppointments(): Result<JSONArray> = try {
        val rows = get("appointments?select=*&order=created_at.desc")
        Result.success(rows)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** এডমিন প্যানেল — অ্যাপয়েন্টমেন্টের যেকোনো ফিল্ড (status, slot_open, payment_status, fee ইত্যাদি) আপডেট */
    suspend fun adminUpdateAppointment(appointmentId: String, fields: JSONObject): Result<Unit> = try {
        patch("appointments?id=eq.${enc(appointmentId)}", fields)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** এডমিন প্যানেল — অ্যাপয়েন্টমেন্ট সম্পূর্ণ ডিলিট */
    suspend fun adminDeleteAppointment(appointmentId: String): Result<Unit> = try {
        delete("appointments?id=eq.${enc(appointmentId)}")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** এডমিন প্যানেল — OTP পুলের অবস্থা: (available, assigned, verified) কতগুলো করে আছে */
    suspend fun adminGetOtpStats(): Result<Triple<Int, Int, Int>> = try {
        val available = get("otp_codes?status=eq.available&select=id")
        val assigned = get("otp_codes?status=eq.assigned&select=id")
        val verified = get("otp_codes?status=eq.verified&select=id")
        Result.success(Triple(available.length(), assigned.length(), verified.length()))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** এডমিন প্যানেল — পুলে নতুন র‍্যান্ডম ৬-ডিজিট OTP কোড বাল্কে যোগ করা */
    suspend fun adminAddOtpCodes(count: Int): Result<Unit> = try {
        val arr = JSONArray()
        val rnd = java.security.SecureRandom()
        repeat(count) {
            val code = (100000 + rnd.nextInt(900000)).toString()
            arr.put(JSONObject().put("code", code))
        }
        postArray("otp_codes", arr)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** এডমিন প্যানেল — ব্যবহৃত/মেয়াদোত্তীর্ণ সব OTP কোডকে আবার "available" অবস্থায় রিসেট করা */
    suspend fun adminResetOtpPool(): Result<Unit> = try {
        val json = JSONObject().apply {
            put("status", "available")
            put("phone", JSONObject.NULL)
            put("assigned_at", JSONObject.NULL)
            put("expires_at", JSONObject.NULL)
        }
        patch("otp_codes?status=neq.available", json)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** এডমিন প্যানেল — একটি নির্দিষ্ট তারিখের সব স্লট-ওভাররাইড লিস্ট (তারিখ অনুযায়ী নতুন থেকে পুরনো) */
    suspend fun adminGetAllSlotDateOverrides(): Result<JSONArray> = try {
        val rows = get("slot_date_overrides?select=*&order=slot_date.desc")
        Result.success(rows)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
