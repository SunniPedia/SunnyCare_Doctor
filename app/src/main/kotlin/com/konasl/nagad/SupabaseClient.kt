package com.konasl.nagad

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
 * UPDATED: Phone Validation + Device Reset Fix
 */
object SupabaseClient {

    private const val SUPABASE_URL = "https://azbleibkgerzaqbrrydl.supabase.co/"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF6YmxlaWJrZ2VyemFxYnJyeWRsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODkxNzExNjYsImV4cCI6MjEwNDc0NzE2Nn0.6Q6PMcRJHXFIRPUZZf9lOjoTmq77_wbCoKc8tGkVF2o"

    private const val REPORTS_BUCKET = "test-reports"
    private const val PROFILE_BUCKET = "profile-pictures"

    private const val PREFS = "sunnycare_prefs"
    private const val KEY_LOGGED_IN = "is_logged_in"
    private const val KEY_PATIENT_ID = "patient_id"
    private const val KEY_PHONE = "phone"
    private const val KEY_NAME = "full_name"

    // ডাক্তার ও এডমিন নাম্বার - দুজনই এডমিন
    private val adminPhones = listOf("01710355342", "01632336631")
    // ডাক্তারের নাম্বার আলাদা ভাবে দরকার হলে
    const val DOCTOR_PHONE = "01710355342"
    const val ADMIN_PHONE = "01632336631"

    private val client = OkHttpClient.Builder()
       .connectTimeout(15, TimeUnit.SECONDS)
       .readTimeout(15, TimeUnit.SECONDS)
       .writeTimeout(30, TimeUnit.SECONDS)
       .build()

    private val JSON = "application/json".toMediaType()

    // [STRIPPED 69 bytes]
    // PHONE VALIDATION UTILS - NEW SMART LOGIC
    // [STRIPPED 69 bytes]
    object PhoneValidator {
        // বাংলাদেশি নাম্বার কিনা চেক (01, +8801, 8801)
        fun isBangladeshiNumber(rawPhone: String): Boolean {
            val p = rawPhone.trim().replace(" ", "").replace("-", "")
            return p.startsWith("01") || p.startsWith("+8801") || p.startsWith("8801") || p.startsWith("+880")
        }

        // বাংলাদেশি নাম্বারকে 01XXXXXXXXX ফরম্যাটে নরমালাইজ করা
        fun normalizeBangladeshi(rawPhone: String): String {
            var p = rawPhone.trim().replace(" ", "").replace("-", "")
            if (p.startsWith("+880")) p = "0" + p.substring(4)
            else if (p.startsWith("880")) p = "0" + p.substring(3)
            return p
        }

        // ভ্যালিডেশন রেজাল্ট
        data class ValidationResult(val isValid: Boolean, val normalizedPhone: String, val message: String, val isForeign: Boolean)

        fun validate(rawPhone: String): ValidationResult {
            val trimmed = rawPhone.trim()
            if (trimmed.isEmpty()) {
                return ValidationResult(false, "", "ফোন নাম্বার দিন", false)
            }

            // বিদেশি নাম্বার ডিটেক্ট (+ চিহ্ন থাকলে বা 01 দিয়ে শুরু না হলে এবং 11 ডিজিটের বেশি/কম হলে)
            val cleanForCheck = trimmed.replace(" ", "").replace("-", "")
            val isForeignCandidate = cleanForCheck.startsWith("+") || (!cleanForCheck.startsWith("01") &&!cleanForCheck.startsWith("880") &&!cleanForCheck.startsWith("+880"))

            if (isBangladeshiNumber(trimmed)) {
                val normalized = normalizeBangladeshi(trimmed)
                if (normalized.length!= 11) {
                    return ValidationResult(false, normalized, "বাংলাদেশি নাম্বার ১১ ডিজিটের হতে হবে (যেমন: 017XXXXXXXX)", false)
                }
                if (!normalized.matches(Regex("^01[0-9]{9}$"))) {
                    return ValidationResult(false, normalized, "সঠিক বাংলাদেশি নাম্বার দিন", false)
                }
                return ValidationResult(true, normalized, "OK", false)
            } else {
                // বিদেশি নাম্বার
                var foreign = cleanForCheck.replace("+", "")
                if (foreign.length < 7 || foreign.length > 15) {
                    return ValidationResult(false, trimmed, "বিদেশি নাম্বার ৭-১৫ ডিজিটের হতে হবে", true)
                }
                if (!foreign.matches(Regex("^[0-9]{7,15}$"))) {
                    return ValidationResult(false, trimmed, "সঠিক বিদেশি নাম্বার দিন (+ সহ)", true)
                }
                // বিদেশি নাম্বারের জন্য + সহ সেভ করবো
                val finalPhone = if (cleanForCheck.startsWith("+")) cleanForCheck else "+$foreign"
                return ValidationResult(true, finalPhone, "OK - Foreign Detected", true)
            }
        }
    }

    // Device ID Empty চেক - NULL বাগ ফিক্স
    fun isDeviceIdEmpty(deviceId: String?): Boolean {
        if (deviceId == null) return true
        val trimmed = deviceId.trim()
        return trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true) || trimmed.equals("NULL", ignoreCase = true)
    }

    fun getDeviceIdFromPatient(patient: JSONObject): String {
        // optString null হলে "null" স্ট্রিং দেয়, সেটাও হ্যান্ডেল করতে হবে
        if (patient.isNull("device_id")) return ""
        val raw = patient.optString("device_id", "")
        return raw
    }

    // [STRIPPED 69 bytes]
    // SESSION (SharedPreferences)
    // [STRIPPED 69 bytes]
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
        val phone = getPhone(context)?: return false
        // নরমালাইজ করে চেক করবে যাতে +880 দিয়েও এডমিন চেনে
        val normalized = PhoneValidator.normalizeBangladeshi(phone)
        return adminPhones.contains(phone) || adminPhones.contains(normalized)
    }

    // [STRIPPED 69 bytes]
    // LOW LEVEL REST HELPERS
    // [STRIPPED 69 bytes]
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

    // [STRIPPED 69 bytes]
    // PIN HASHING
    // [STRIPPED 69 bytes]
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

    // [STRIPPED 69 bytes]
    // OTP FLOW
    // [STRIPPED 69 bytes]
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
        return sdf.parse(cleaned)?.time?: 0L
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

    // [STRIPPED 69 bytes]
    // PATIENT
    // [STRIPPED 69 bytes]
    suspend fun findPatientByPhone(phone: String): Result<JSONObject?> = try {
        // normalized এবং raw দুটো দিয়েই খুঁজবে
        val normalized = PhoneValidator.normalizeBangladeshi(phone)
        var rows = get("patients?phone=eq.${enc(phone)}&limit=1")
        if (rows.length() == 0 && normalized!= phone) {
            rows = get("patients?phone=eq.${enc(normalized)}&limit=1")
        }
        Result.success(if (rows.length() > 0) rows.getJSONObject(0) else null)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun findPatientByDeviceId(deviceId: String, excludePatientId: String? = null): Result<JSONObject?> = try {
        var path = "patients?device_id=eq.${enc(deviceId)}&limit=1"
        if (excludePatientId!= null) path += "&id=neq.${enc(excludePatientId)}"
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
            put("age", p.age?: JSONObject.NULL)
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

    /**
     * SMART DEVICE RESET - FIXED
     * device_id কে null করে দেওয়া হয়, ফলে অন্য ডিভাইস দিয়ে লগইন করতে পারবে
     */
    suspend fun adminResetPatientDevice(patientId: String): Result<Unit> = try {
        val json = JSONObject().apply {
            put("device_id", JSONObject.NULL)
        }
        patch("patients?id=eq.${enc(patientId)}", json)
        Result.success(Unit)
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

    // [STRIPPED 69 bytes]
    // STORAGE
    // [STRIPPED 69 bytes]
    suspend fun uploadReportFile(fileName: String, mimeType: String, bytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val safeName = "${System.currentTimeMillis()}_${fileName.replace(Regex("[^A-Za-z0-9.-]"), "")}"
                val objectPath = "reports/$safeName"
                val mediaType = mimeType.toMediaTypeOrNull()?: "application/octet-stream".toMediaType()

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
                val safeName = "${System.currentTimeMillis()}_${fileName.replace(Regex("[^A-Za-z0-9.-]"), "")}"
                val objectPath = "avatars/$safeName"
                val mediaType = mimeType.toMediaTypeOrNull()?: "image/jpeg".toMediaType()

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

    // [STRIPPED 69 bytes]
    // APPOINTMENTS
    // [STRIPPED 69 bytes]
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
        reportUrl: String = "",
        weight: String = "",
        bloodPressure: String = ""
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
            if (weight.isNotEmpty()) put("weight", weight)
            if (bloodPressure.isNotEmpty()) put("blood_pressure", bloodPressure)
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

    // [STRIPPED 69 bytes]
    // ADMIN — টাইম-স্লট
    // [STRIPPED 69 bytes]
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

    // [STRIPPED 69 bytes]
    // ADMIN — পেমেন্ট
    // [STRIPPED 69 bytes]
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

    // ADMIN PANEL FUNCTIONS
    suspend fun adminGetAllPatients(): Result<JSONArray> = try {
        val rows = get("patients?select=*&order=created_at.desc")
        Result.success(rows)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun adminUpdatePatient(patientId: String, fields: JSONObject): Result<Unit> = try {
        patch("patients?id=eq.${enc(patientId)}", fields)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun adminDeletePatient(patientId: String): Result<Unit> = deleteAccount(patientId)

    suspend fun adminGetAllAppointments(): Result<JSONArray> = try {
        val rows = get("appointments?select=*&order=created_at.desc")
        Result.success(rows)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun adminGetPatientAppointments(patientId: String): Result<JSONArray> = getAppointments(patientId)

    suspend fun adminUpdateAppointment(appointmentId: String, fields: JSONObject): Result<Unit> = try {
        patch("appointments?id=eq.${enc(appointmentId)}", fields)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun adminDeleteAppointment(appointmentId: String): Result<Unit> = try {
        delete("appointments?id=eq.${enc(appointmentId)}")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun adminGetOtpStats(): Result<Triple<Int, Int, Int>> = try {
        val available = get("otp_codes?status=eq.available&select=id")
        val assigned = get("otp_codes?status=eq.assigned&select=id")
        val verified = get("otp_codes?status=eq.verified&select=id")
        Result.success(Triple(available.length(), assigned.length(), verified.length()))
    } catch (e: Exception) {
        Result.failure(e)
    }

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

    suspend fun adminGetAllSlotDateOverrides(): Result<JSONArray> = try {
        val rows = get("slot_date_overrides?select=*&order=slot_date.desc")
        Result.success(rows)
    } catch (e: Exception) {
        Result.failure(e)
    }
    suspend fun getOrCreateConversation(appointmentId: String, patientId: String): org.json.JSONObject {
    // 1. আগে আছে কিনা দেখো
    val existing = try {
        get("conversations?appointment_id=eq.${enc(appointmentId)}&limit=1")
    } catch (e: Exception) {
        org.json.JSONArray()
    }
    if (existing.length() > 0) {
        return existing.getJSONObject(0)
    }
    // 2. না থাকলে বানাও - appointment_id unique তাই একটাই রুম হবে
    val json = org.json.JSONObject().apply {
        put("appointment_id", appointmentId)
        put("patient_id", patientId)
        put("doctor_id", DOCTOR_PHONE)
        put("last_message", "")
    }
    val rows = post("conversations", json)
    return rows.getJSONObject(0)
}

suspend fun getMessages(conversationId: String): org.json.JSONArray {
    return get("messages?conversation_id=eq.${enc(conversationId)}&order=created_at.asc&limit=200")
}

suspend fun sendMessage(conversationId: String, senderId: String, senderRole: String, text: String): org.json.JSONObject {
    val json = org.json.JSONObject().apply {
        put("conversation_id", conversationId)
        put("sender_id", senderId)
        put("sender_role", senderRole) // doctor / patient
        put("message", text)
    }
    val rows = post("messages", json)
    // last_message আপডেট যাতে লিস্টে দেখা যায়
    try {
        patch("conversations?id=eq.${enc(conversationId)}", org.json.JSONObject().apply {
            put("last_message", text)
            put("last_message_at", "now()")
        })
    } catch (e: Exception) {}
    return rows.getJSONObject(0)
}

    // ------------------------------------------------------------------
    // SUPABASE REALTIME — APPOINTMENTS
    // Listens to every INSERT / UPDATE / DELETE for the current patient.
    // The REST API remains the source used to reload the complete list after
    // a realtime event, so the UI always renders the latest appointment data.
    // ------------------------------------------------------------------
    private val realtimeClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val realtimeHeartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var realtimeHeartbeatRunnable: Runnable? = null
    private var appointmentRealtimeSocket: WebSocket? = null
    private var appointmentRealtimeGeneration = 0L

    @Synchronized
    fun startAppointmentRealtime(
        patientId: String,
        onAppointmentChanged: () -> Unit
    ): WebSocket? {
        if (patientId.isBlank()) return null

        stopAppointmentRealtime()
        val generation = ++appointmentRealtimeGeneration
        val topic = "realtime:appointments_patient_$patientId"
        val encodedFilter = URLEncoder.encode("patient_id=eq.$patientId", "UTF-8")
        val realtimeBase = SUPABASE_URL
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        val realtimeUrl = "wss://$realtimeBase/realtime/v1/websocket?apikey=${enc(SUPABASE_ANON_KEY)}&vsn=1.0.0"

        fun scheduleReconnect() {
            if (generation != appointmentRealtimeGeneration) return
            realtimeHeartbeatHandler.postDelayed({
                if (generation == appointmentRealtimeGeneration && appointmentRealtimeSocket == null) {
                    startAppointmentRealtime(patientId, onAppointmentChanged)
                }
            }, 3000L)
        }

        val request = Request.Builder().url(realtimeUrl).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                if (generation != appointmentRealtimeGeneration) {
                    webSocket.cancel()
                    return
                }
                appointmentRealtimeSocket = webSocket

                val joinPayload = JSONObject().apply {
                    put("topic", topic)
                    put("event", "phx_join")
                    put("ref", "1")
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("broadcast", JSONObject().put("self", false))
                            put("presence", JSONObject().put("key", ""))
                            put("postgres_changes", org.json.JSONArray().put(
                                JSONObject().apply {
                                    put("event", "*")
                                    put("schema", "public")
                                    put("table", "appointments")
                                    put("filter", "patient_id=eq.$patientId")
                                }
                            ))
                        })
                        put("access_token", SUPABASE_ANON_KEY)
                    })
                }
                webSocket.send(joinPayload.toString())
                startRealtimeHeartbeat(webSocket, generation, topic)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (generation != appointmentRealtimeGeneration) return
                try {
                    val message = JSONObject(text)
                    val event = message.optString("event", "")
                    val payload = message.optJSONObject("payload")
                    val data = payload?.optJSONObject("data")

                    // postgres_changes payloads contain the changed row under
                    // record / old_record. We still perform a full REST reload
                    // so INSERT, UPDATE and DELETE are all reflected correctly.
                    if (event == "postgres_changes" ||
                        event == "INSERT" || event == "UPDATE" || event == "DELETE" ||
                        data != null
                    ) {
                        onAppointmentChanged()
                    }
                } catch (_: Exception) {
                    // Ignore non-JSON heartbeat/transport noise.
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (generation == appointmentRealtimeGeneration) {
                    appointmentRealtimeSocket = null
                    stopRealtimeHeartbeat()
                }
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (generation == appointmentRealtimeGeneration) {
                    appointmentRealtimeSocket = null
                    stopRealtimeHeartbeat()
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                if (generation == appointmentRealtimeGeneration) {
                    appointmentRealtimeSocket = null
                    stopRealtimeHeartbeat()
                    scheduleReconnect()
                }
            }
        }

        return realtimeClient.newWebSocket(request, listener).also {
            appointmentRealtimeSocket = it
        }
    }

    private fun startRealtimeHeartbeat(webSocket: WebSocket, generation: Long, topic: String) {
        stopRealtimeHeartbeat()
        val runnable = object : Runnable {
            override fun run() {
                if (generation != appointmentRealtimeGeneration || appointmentRealtimeSocket !== webSocket) return
                val heartbeat = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", System.currentTimeMillis().toString())
                }
                webSocket.send(heartbeat.toString())
                realtimeHeartbeatHandler.postDelayed(this, 25_000L)
            }
        }
        realtimeHeartbeatRunnable = runnable
        realtimeHeartbeatHandler.postDelayed(runnable, 25_000L)
    }

    @Synchronized
    fun stopAppointmentRealtime() {
        appointmentRealtimeGeneration++
        appointmentRealtimeSocket?.close(1000, "Activity paused")
        appointmentRealtimeSocket?.cancel()
        appointmentRealtimeSocket = null
        stopRealtimeHeartbeat()
    }

    private fun stopRealtimeHeartbeat() {
        realtimeHeartbeatRunnable?.let { realtimeHeartbeatHandler.removeCallbacks(it) }
        realtimeHeartbeatRunnable = null
    }

}
