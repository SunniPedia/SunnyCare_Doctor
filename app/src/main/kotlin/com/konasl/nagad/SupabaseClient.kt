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
 *   created_at timestamptz default now()
 * );
 *
 * -- যদি patients টেবিল আগে থেকেই থাকে (পুরাতন অ্যাপ), শুধু এই দুই লাইন চালান:
 * -- alter table patients add column if not exists password_salt text;
 * -- alter table patients add column if not exists password_hash text;
 * -- পুরাতন যেসব ইউজারের password_hash খালি থাকবে, তাদের অ্যাপ প্রথম লগইনেই
 * -- নতুন পাসওয়ার্ড সেট করতে বলবে (LoginActivity তে handled)।
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
 *   payment_method text,
 *   fee integer default 800,
 *   transaction_id text,
 *   payment_status text default 'not_applicable', -- not_applicable / pending_verification / verified / rejected
 *   report_url text, -- রোগীর আপলোড করা আগের টেস্ট রিপোর্টের Supabase Storage পাবলিক URL(গুলো)।
 *                    -- একাধিক ফাইল আপলোড করলে কমা (,) দিয়ে একাধিক URL এই একই কলামে জমা হয়
 *                    -- (ফাঁকা রাখলে বুঝতে হবে রোগী কোনো রিপোর্ট আপলোড করেননি)
 *   created_at timestamptz default now()
 * );
 *
 * -- যদি appointments টেবিল আগে থেকেই থাকে, শুধু এই লাইনটা চালান:
 * -- alter table appointments add column if not exists report_url text;
 *
 * -- নির্দিষ্ট তারিখে বুক হওয়া সময়গুলো দ্রুত খুঁজে বের করার জন্য (BookAppointmentActivity-এ
 * -- ইতিমধ্যে বুক করা স্লট হাইড করতে ব্যবহৃত হয়) — ঐচ্ছিক কিন্তু বড় ডেটায় পারফরম্যান্সের জন্য ভালো:
 * -- create index if not exists idx_appointments_date on appointments (preferred_date);
 *
 * ----------------------------------------------------------------------------
 * এডমিন স্লট এনাবল/ডিজেবল (নতুন — টাইম-স্লট গায়েব/দৃশ্যমান করার জন্য)
 * ----------------------------------------------------------------------------
 * -- ১) গ্লোবাল সেটিংস: কোনো নির্দিষ্ট সময় (যেমন "20:00") সব তারিখেই বন্ধ রাখতে চাইলে
 * create table if not exists slot_settings (
 *   value24 text primary key,        -- '10:00', '14:30' ইত্যাদি (২৪-ঘণ্টা ফরম্যাট)
 *   enabled boolean not null default true
 * );
 *
 * -- ২) নির্দিষ্ট একটা তারিখে নির্দিষ্ট একটা সময় ব্যতিক্রমভাবে বন্ধ/চালু রাখতে চাইলে
 * --    (গ্লোবাল সেটিংসকে override করে — উদাহরণ: শুধু ঈদের দিন সব স্লট বন্ধ)
 * create table if not exists slot_date_overrides (
 *   id bigint generated always as identity primary key,
 *   slot_date text not null,         -- 'yyyy-MM-dd'
 *   value24 text not null,           -- '10:00', '14:30' ইত্যাদি
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
 * ----------------------------------------------------------------------------
 * TEST REPORT আপলোডের জন্য Storage bucket (একবারই সেটআপ করতে হবে):
 * ----------------------------------------------------------------------------
 * -- ১) বাকেট তৈরি (public = true রাখা হয়েছে যাতে রিপোর্টের লিংক সরাসরি খোলা যায়;
 * --    প্রয়োজনে ভবিষ্যতে signed URL / private bucket এ পরিবর্তন করা যায়)
 * insert into storage.buckets (id, name, public)
 * values ('test-reports', 'test-reports', true)
 * on conflict (id) do nothing;
 *
 * -- ২) anon key দিয়ে আপলোড/রিড করার অনুমতি (storage.objects এ RLS ডিফল্ট চালু থাকে)
 * create policy "Allow anon uploads to test-reports"
 * on storage.objects for insert
 * to anon
 * with check (bucket_id = 'test-reports');
 *
 * create policy "Allow public read of test-reports"
 * on storage.objects for select
 * to anon
 * using (bucket_id = 'test-reports');
 * ============================================================================
 */
object SupabaseClient {

    // TODO: নিজের Supabase প্রজেক্ট থেকে বসান (Project Settings > API)
    private const val SUPABASE_URL = "https://azbleibkgerzaqbrrydl.supabase.co/"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF6YmxlaWJrZ2VyemFxYnJyeWRsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODkxNzExNjYsImV4cCI6MjEwNDc0NzE2Nn0.6Q6PMcRJHXFIRPUZZf9lOjoTmq77_wbCoKc8tGkVF2o"

    // Storage bucket যেখানে রোগীদের টেস্ট রিপোর্ট (ছবি/PDF) জমা হয়
    private const val REPORTS_BUCKET = "test-reports"

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

    /**
     * PostgREST-এ কুয়েরি-স্ট্রিং ভ্যালু হিসেবে বসানোর আগে যেকোনো ইউজার-ইনপুট (phone, code,
     * date, id ইত্যাদি) এখান দিয়ে পাস করানো হয়। কারণ, ফোন নাম্বারের মতো ভ্যালুতে থাকা "+"
     * চিহ্ন এনকোড না করে সরাসরি URL-এ বসালে অনেক HTTP লাইব্রেরি/সার্ভার সেটাকে স্পেস হিসেবে
     * ডিকোড করে ফেলতে পারে (application/x-www-form-urlencoded কনভেনশন অনুযায়ী), ফলে
     * "+8801..." ধরনের নাম্বার দিয়ে OTP verify বা patient lookup ইন্টারমিটেন্টলি ফেইল করতে
     * পারে। URLEncoder ব্যবহার করলে "+" ঠিকভাবে "%2B" এ এনকোড হয়, তাই এই সমস্যাটা হয় না।
     */
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

    /** POST করার সময় নিজস্ব Prefer হেডার (যেমন upsert-এর জন্য merge-duplicates) দিতে চাইলে এটা ব্যবহার হয় */
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

    /** DELETE — Supabase PostgREST-এ row মুছে ফেলার জন্য। শর্ত (filter) path-এর কুয়েরি-স্ট্রিং এ দিতে হয়,
     *  যেমন: delete("appointments?patient_id=eq.$patientId") */
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
    // PASSWORD HASHING
    // ---------------------------------------------------------------------
    // NOTE: এখানে কোনো ব্যাকএন্ড সার্ভার ছাড়া শুধু Supabase REST (anon key) দিয়ে কাজ করা হচ্ছে,
    // তাই bcrypt/argon2-এর মতো সার্ভার-সাইড হ্যাশিং সম্ভব না। এর বদলে প্রতিটা পাসওয়ার্ডের জন্য
    // একটা আলাদা র‍্যান্ডম সল্ট (salt) জেনারেট করে salt+password এর SHA-256 হ্যাশ সেভ করা হয় —
    // যা প্লেইন-টেক্সট পাসওয়ার্ড সেভ করার চেয়ে অনেক নিরাপদ। সত্যিকারের প্রোডাকশন-গ্রেড সিকিউরিটির
    // জন্য ভবিষ্যতে Supabase Auth (ইমেইল/ফোন + পাসওয়ার্ড) ব্যবহার করার পরামর্শ থাকলো।
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

    private fun hashPassword(password: String, salt: String): String = sha256Hex("$salt:$password")

    // ---------------------------------------------------------------------
    // OTP FLOW (using pre-generated otp_codes pool - no real SMS gateway)
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

    /** একটা unused OTP রিজার্ভ করে এই ফোন নাম্বারের নামে অ্যাসাইন করে, ২ মিনিটের মেয়াদ সেট করে কোডটা রিটার্ন করে */
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

    /** ইউজারের দেওয়া কোড এই ফোন নাম্বারের সাথে assigned অবস্থায় ও মেয়াদের মধ্যে মিলছে কিনা চেক করে */
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

    /** ফোন নাম্বার আগে থেকে রেজিস্টার্ড কিনা চেক করে; থাকলে patient JSONObject রিটার্ন করে */
    suspend fun findPatientByPhone(phone: String): Result<JSONObject?> = try {
        val rows = get("patients?phone=eq.${enc(phone)}&limit=1")
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
        val medicalHistory: String,
        val password: String // সাইনআপ ফর্মে ইউজারকে অবশ্যই একটা পাসওয়ার্ড সেট করতে বলতে হবে
    )

    suspend fun registerPatient(p: NewPatient): Result<JSONObject> = try {
        val salt = generateSalt()
        val hash = hashPassword(p.password, salt)
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
        }
        val rows = post("patients", json)
        Result.success(rows.getJSONObject(0))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** প্রথমবার পাসওয়ার্ড সেট করা (পুরাতন অ্যাকাউন্ট যাদের password_hash খালি) অথবা অ্যাডমিন রিসেট করে দিলে */
    suspend fun setPatientPassword(patientId: String, newPassword: String): Result<Unit> = try {
        val salt = generateSalt()
        val hash = hashPassword(newPassword, salt)
        val json = JSONObject().apply {
            put("password_salt", salt)
            put("password_hash", hash)
        }
        patch("patients?id=eq.${enc(patientId)}", json)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** লগইনের সময় ইউজারের দেওয়া পাসওয়ার্ড, patients টেবিলে সেভ থাকা salt+hash এর সাথে মিলিয়ে দেখে */
    fun verifyPatientPassword(patient: JSONObject, enteredPassword: String): Result<Boolean> = try {
        val salt = patient.optString("password_salt", "")
        val hash = patient.optString("password_hash", "")
        if (salt.isEmpty() || hash.isEmpty()) {
            Result.success(false)
        } else {
            Result.success(hashPassword(enteredPassword, salt) == hash)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * ব্যবহারকারীর অ্যাকাউন্ট স্থায়ীভাবে ডিলিট করে — প্রথমে তার সব appointments মুছে,
     * তারপর patients টেবিল থেকে তার নিজের row মুছে ফেলে (foreign key constraint এড়াতে এই ক্রমটা জরুরি,
     * যেহেতু appointments.patient_id, patients(id) কে রেফার করে)।
     *
     * NOTE: এই প্রজেক্টে যেহেতু RLS বন্ধ আর anon key দিয়েই সব রিড/রাইট হচ্ছে (getAppointments,
     * createAppointment ইত্যাদির মতোই), তাই এখানেও একই মডেল অনুসরণ করা হলো — patientId
     * ক্লায়েন্টের SharedPreferences থেকেই আসে। প্রকৃত প্রোডাকশন অ্যাপে RLS চালু করে বা
     * সার্ভার-সাইড ভেরিফিকেশন যোগ করে এটা আরও সুরক্ষিত করার পরামর্শ থাকলো।
     */
    suspend fun deleteAccount(patientId: String): Result<Unit> = try {
        delete("appointments?patient_id=eq.${enc(patientId)}")
        delete("patients?id=eq.${enc(patientId)}")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ---------------------------------------------------------------------
    // STORAGE — রোগীর আগের টেস্ট রিপোর্ট (ছবি/PDF) আপলোড
    // ---------------------------------------------------------------------

    /**
     * রোগীর টেস্ট রিপোর্ট ফাইল Supabase Storage-এর `test-reports` বাকেটে আপলোড করে এবং
     * সফল হলে ফাইলটার পাবলিক URL রিটার্ন করে (যেটা appointments.report_url কলামে সেভ করা হবে)।
     * bucket public = true থাকায় এই URL সরাসরি ব্রাউজার/ImageView-তে খোলা যাবে।
     */
    suspend fun uploadReportFile(fileName: String, mimeType: String, bytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val safeName = "${System.currentTimeMillis()}_${fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
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

    /**
     * নির্দিষ্ট একটা তারিখে (yyyy-MM-dd) ইতিমধ্যে বুক হয়ে থাকা সময়গুলোর (preferred_time) তালিকা আনে।
     * 'cancelled' স্ট্যাটাসের অ্যাপয়েন্টমেন্ট বাদ দেওয়া হয় (বাতিল হয়ে গেলে সেই স্লট আবার খালি হয়ে যায়),
     * বাকি সব (pending/confirmed/completed) স্ট্যাটাসের সময়গুলো বুক করা ধরা হয়।
     * BookAppointmentActivity এই তালিকা ব্যবহার করে টাইম-স্লট গ্রিড থেকে বুক করা সময়গুলো হাইড করে।
     *
     * NOTE: এখানে preferred_date-এর exact match (eq.) ফিল্টার ব্যবহার করা হয়, তাই একটা তারিখের
     * বুকিং অন্য কোনো তারিখের স্লট হাইড করার কারণ হওয়ার কথা না — এই ফাংশনটা সবসময়ই সঠিকভাবে
     * তারিখ-স্কোপড ছিল। ক্লায়েন্ট সাইডে (BookAppointmentActivity) রেস-কন্ডিশন এড়াতে আলাদা
     * request-id + selectedDate গার্ড যোগ করা হয়েছে, যা এই কোয়েরির ফলাফল ভুল তারিখে বসে যাওয়া থেকে রক্ষা করে।
     */
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
    // ADMIN — টাইম-স্লট এনাবল/ডিজেবল (নতুন)
    // ---------------------------------------------------------------------

    /**
     * সব স্লটের গ্লোবাল enabled/disabled ম্যাপ আনে (slot_settings টেবিল)।
     * যেসব value24-এর জন্য কোনো row-ই নেই, তাদের জন্য ম্যাপে কোনো এন্ট্রি থাকবে না —
     * কলিং কোডে (BookAppointmentActivity.isSlotVisible) না-পাওয়া গেলে ডিফল্ট "enabled = true" ধরা হয়,
     * তাই নতুন কোনো টাইম-স্লট যোগ করলে এটা নিজে থেকেই দৃশ্যমান থাকবে, আলাদা করে row বানাতে হবে না।
     */
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

    /** নির্দিষ্ট তারিখের override গুলো আনে (slot_date_overrides) — থাকলে global settings-কে override করবে */
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

    /**
     * এডমিন প্যানেল থেকে একটা স্লট গ্লোবালি চালু/বন্ধ করে (upsert — আগে থেকে row থাকলে আপডেট,
     * না থাকলে নতুন তৈরি হয়)। slot_settings.value24 প্রাইমারি কি হওয়ায় merge-duplicates কাজ করে।
     */
    suspend fun setSlotEnabled(value24: String, enabled: Boolean): Result<Unit> = try {
        val json = JSONObject().put("value24", value24).put("enabled", enabled)
        postWithPrefer("slot_settings", json, "resolution=merge-duplicates,return=minimal")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * নির্দিষ্ট একটা তারিখের জন্য একটা স্লট চালু/বন্ধ করে (শুধু ওই দিনের exception হিসেবে,
     * গ্লোবাল সেটিংস অপরিবর্তিত থাকে)। unique(slot_date, value24) কনস্ট্রেইন্টের কারণে upsert কাজ করে।
     */
    suspend fun setSlotDateOverride(date: String, value24: String, enabled: Boolean): Result<Unit> = try {
        val json = JSONObject().put("slot_date", date).put("value24", value24).put("enabled", enabled)
        postWithPrefer("slot_date_overrides", json, "resolution=merge-duplicates,return=minimal")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** নির্দিষ্ট তারিখের একটা override মুছে দেয় (গ্লোবাল সেটিংসে ফিরিয়ে আনতে) */
    suspend fun deleteSlotDateOverride(date: String, value24: String): Result<Unit> = try {
        delete("slot_date_overrides?slot_date=eq.${enc(date)}&value24=eq.${enc(value24)}")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ---------------------------------------------------------------------
    // ADMIN — পেমেন্ট ভেরিফিকেশন
    // ---------------------------------------------------------------------

    /** payment_status = pending_verification এমন সব অ্যাপয়েন্টমেন্ট আনে, যাচাইয়ের জন্য */
    suspend fun getPendingVerificationAppointments(): Result<JSONArray> = try {
        val rows = get("appointments?payment_status=eq.pending_verification&order=created_at.desc")
        Result.success(rows)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** TrxID মিলিয়ে অ্যাডমিন পেমেন্ট ভেরিফাই করলে payment_status ও status আপডেট হয় */
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

    /** ভুল/জাল TrxID হলে অ্যাডমিন রিজেক্ট করতে পারবে */
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
}
