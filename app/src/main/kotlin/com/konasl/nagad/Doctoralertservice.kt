package com.konasl.nagad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * DoctorAlertService
 * ---------------------------------------------------------------------
 * উদ্দেশ্য: রোগী অ্যাপে না থাকলেও (অ্যাপ ব্যাকগ্রাউন্ডে থাকলে) ডাক্তার/এডমিন
 * (01710355342, 01632336631) থেকে চ্যাট মেসেজ এলে নোটিফিকেশন পাওয়া, এবং
 * অ্যাপ ফোরগ্রাউন্ডে (যেকোনো একটিভিটিতে) থাকলে একটা কাস্টম ডায়ালগ দেখানো,
 * যেটাতে ট্যাপ করলে WaitingActivity তে যাওয়া যায়।
 *
 * *** গুরুত্বপূর্ণ সীমাবদ্ধতা — অবশ্যই পড়ুন ***
 * এই প্রজেক্টে Firebase Cloud Messaging (FCM) সেটআপ করা নেই। তাই এই
 * সার্ভিসটা "true push notification" নয় — এটা একটা Foreground Service
 * যেটা Supabase Realtime এর WebSocket কানেকশন ধরে রাখে যতক্ষণ অ্যাপ
 * প্রসেস মেমোরিতে বেঁচে থাকে (রিসেন্ট অ্যাপ থেকে সোয়াইপ করে সরিয়ে না দিলে,
 * বা সিস্টেম মেমোরি চাপে কিল না করলে)।
 *
 * "অ্যাপ সম্পূর্ণ বন্ধ/কিল করা থাকলেও" নিশ্চিতভাবে নোটিফিকেশন পেতে হলে
 * FCM + একটা সার্ভার-সাইড ট্রিগার (Supabase Edge Function / Database
 * Webhook, যেটা messages টেবিলে ডাক্তার/এডমিন থেকে নতুন সারি ঢুকলে
 * পেশেন্টের ডিভাইসে পুশ পাঠাবে) লাগবে। সেটার জন্য একটা Firebase প্রজেক্ট ও
 * google-services.json দরকার, যা এই কোডে যুক্ত করা সম্ভব নয় কারণ এটা
 * আপনার নিজের Firebase অ্যাকাউন্ট-নির্ভর একটা আলাদা সেটআপ।
 */
class DoctorAlertService : Service() {

    companion object {
        const val ACTION_DOCTOR_MESSAGE = "com.konasl.nagad.ACTION_DOCTOR_MESSAGE"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_APPOINTMENT_ID = "appointment_id"
        const val EXTRA_PREVIEW = "preview"
        const val EXTRA_PATIENT_ID = "patient_id"

        private const val FOREGROUND_CHANNEL_ID = "sunnycare_bg_channel"
        private const val ALERT_CHANNEL_ID = "sunnycare_doctor_alert_channel"
        private const val FOREGROUND_NOTIF_ID = 9001
        private const val ALERT_NOTIF_ID_BASE = 9100

        // FIX: ডাক্তার/এডমিন নাম্বার — SupabaseClient এর সাথে সিঙ্কে রাখা হয়েছে।
        private val ADMIN_PHONES = listOf("01710355342", "01632336631")

        private const val SUPABASE_URL = "https://azbleibkgerzaqbrrydl.supabase.co"
        private const val SUPABASE_ANON_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF6YmxlaWJrZ2VyemFxYnJyeWRsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODkxNzExNjYsImV4cCI6MjEwNDc0NzE2Nn0.6Q6PMcRJHXFIRPUZZf9lOjoTmq77_wbCoKc8tGkVF2o"
    }

    private var patientId: String = ""
    private var socket: WebSocket? = null
    private var generation = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val incomingPatientId = intent?.getStringExtra(EXTRA_PATIENT_ID).orEmpty()
        if (incomingPatientId.isNotBlank()) {
            patientId = incomingPatientId
        }

        // FIX: Android 8+ এ ব্যাকগ্রাউন্ড সার্ভিসকে বাঁচিয়ে রাখতে অবশ্যই
        // startForeground() কল করতে হবে, নাহলে কিছুক্ষণ পরই সিস্টেম সার্ভিসটা
        // বন্ধ করে দেবে। এই নোটিফিকেশনটা লো-প্রায়োরিটি, নীরব ও সরানো যায় না।
        startForeground(FOREGROUND_NOTIF_ID, buildForegroundNotification())

        if (patientId.isNotBlank() && socket == null) {
            connectRealtime()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        generation++
        socket?.close(1000, "Service destroyed")
        socket = null
        super.onDestroy()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val bgChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "চ্যাট সংযোগ (ব্যাকগ্রাউন্ড)",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "ডাক্তারের মেসেজ পাওয়ার জন্য ব্যাকগ্রাউন্ড সংযোগ চালু রাখে"
                setShowBadge(false)
            }

            // FIX: এই চ্যানেলে ডিফল্ট নোটিফিকেশন সাউন্ড + ভাইব্রেশন সেট করা আছে,
            // যেন ডাক্তার মেসেজ পাঠালে ইউজার শব্দসহ টের পায়।
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "ডাক্তারের মেসেজ",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "ডাক্তার/এডমিন থেকে নতুন মেসেজ এলে সতর্কতা"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }

            nm.createNotificationChannel(bgChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("SunnyCare চালু আছে")
            .setContentText("ডাক্তারের মেসেজের জন্য অপেক্ষা করা হচ্ছে")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    // ------------------------------------------------------------------
    // Supabase Realtime — messages টেবিলে sender_role = doctor ফিল্টার
    // (একই প্যাটার্ন SupabaseClient.kt এর adminAppointmentsRealtime এর মতো)
    // ------------------------------------------------------------------
    private fun connectRealtime() {
        val myGeneration = ++generation
        val realtimeBase = SUPABASE_URL.removePrefix("https://").removePrefix("http://").trimEnd('/')
        val realtimeUrl =
            "wss://$realtimeBase/realtime/v1/websocket?apikey=${enc(SUPABASE_ANON_KEY)}&vsn=1.0.0"
        val topic = "realtime:doctor_alert_$patientId"

        val request = Request.Builder().url(realtimeUrl).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (myGeneration != generation) {
                    webSocket.cancel()
                    return
                }
                socket = webSocket

                val joinPayload = JSONObject().apply {
                    put("topic", topic)
                    put("event", "phx_join")
                    put("ref", "1")
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("broadcast", JSONObject().put("self", false))
                            put("presence", JSONObject().put("key", ""))
                            put(
                                "postgres_changes",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put("event", "INSERT")
                                        put("schema", "public")
                                        put("table", "messages")
                                        put("filter", "sender_role=eq.doctor")
                                    }
                                )
                            )
                        })
                        put("access_token", SUPABASE_ANON_KEY)
                    })
                }
                webSocket.send(joinPayload.toString())
                startHeartbeat(webSocket, myGeneration)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (myGeneration != generation) return
                try {
                    val message = JSONObject(text)
                    if (message.optString("event") != "postgres_changes") return
                    val data = message.optJSONObject("payload")?.optJSONObject("data") ?: return
                    val record = data.optJSONObject("record") ?: return
                    handleIncomingDoctorMessage(record)
                } catch (_: Exception) {
                    // heartbeat/transport নয়েজ উপেক্ষা করা হলো
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (myGeneration == generation) scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (myGeneration == generation) scheduleReconnect()
            }
        }

        httpClient.newWebSocket(request, listener)
    }

    private fun scheduleReconnect() {
        socket = null
        handler.postDelayed({
            if (patientId.isNotBlank()) connectRealtime()
        }, 4000L)
    }

    private fun startHeartbeat(webSocket: WebSocket, myGeneration: Long) {
        val runnable = object : Runnable {
            override fun run() {
                if (myGeneration != generation) return
                val heartbeat = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", System.currentTimeMillis().toString())
                }
                webSocket.send(heartbeat.toString())
                handler.postDelayed(this, 25_000L)
            }
        }
        handler.postDelayed(runnable, 25_000L)
    }

    private fun handleIncomingDoctorMessage(record: JSONObject) {
        val senderId = record.optString("sender_id", "")
        if (senderId !in ADMIN_PHONES) return

        val conversationId = record.optString("conversation_id", "")
        if (conversationId.isBlank()) return

        // REST কল নেটওয়ার্ক থ্রেডে করতে হবে (Service এর মেইন থ্রেডে নয়)
        Thread {
            try {
                val conversation = fetchConversation(conversationId) ?: return@Thread
                val convPatientId = conversation.optString("patient_id", "")
                if (convPatientId != patientId) return@Thread

                val appointmentId = conversation.optString("appointment_id", "")
                val rawMessage = record.optString("message", "")
                val preview = buildPreview(rawMessage)

                // ব্রডকাস্ট — অ্যাপ ফোরগ্রাউন্ডে থাকলে MyApp এটা ধরে কাস্টম ডায়ালগ দেখাবে
                sendDoctorMessageBroadcast(conversationId, appointmentId, preview)

                // অ্যাপ ব্যাকগ্রাউন্ডে থাকলে সিস্টেম নোটিফিকেশন (ডিফল্ট সাউন্ডসহ)
                if (!AppForegroundState.isAppInForeground) {
                    showAlertNotification(conversationId, appointmentId, preview)
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun buildPreview(rawMessage: String): String {
        val trimmed = rawMessage.trim()
        if (trimmed.startsWith("{")) {
            return try {
                val obj = JSONObject(trimmed)
                when (obj.optString("kind")) {
                    "attachment" -> "একটি ফাইল পাঠিয়েছেন: ${obj.optString("file_name", "ফাইল")}"
                    else -> "নতুন মেসেজ এসেছে"
                }
            } catch (_: Exception) {
                "নতুন মেসেজ এসেছে"
            }
        }
        return if (trimmed.isBlank()) "নতুন মেসেজ এসেছে" else trimmed
    }

    private fun sendDoctorMessageBroadcast(conversationId: String, appointmentId: String, preview: String) {
        val intent = Intent(ACTION_DOCTOR_MESSAGE).apply {
            setPackage(packageName)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_APPOINTMENT_ID, appointmentId)
            putExtra(EXTRA_PREVIEW, preview)
        }
        sendBroadcast(intent)
    }

    private fun showAlertNotification(conversationId: String, appointmentId: String, preview: String) {
        val targetIntent = Intent(this, WaitingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("appointment_id", appointmentId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            conversationId.hashCode(),
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("ডাক্তার একটি মেসেজ পাঠিয়েছেন")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setColor(Color.parseColor("#0F6C61"))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(ALERT_NOTIF_ID_BASE + (conversationId.hashCode() and 0xFFFF), notification)
    }

    private fun fetchConversation(conversationId: String): JSONObject? {
        val url =
            "$SUPABASE_URL/rest/v1/conversations?id=eq.${enc(conversationId)}&select=patient_id,appointment_id&limit=1"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            if (arr.length() > 0) arr.getJSONObject(0) else null
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
