package com.konasl.nagad

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.InputType
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SunnyCare Doctor - Telemedicine Chat
 *
 * Important:
 * 1. No polling.
 * 2. No delay(2000).
 * 3. New messages are received through Supabase Realtime Postgres Changes.
 * 4. Initial history is loaded once through the existing SupabaseClient REST API.
 * 5. Sending a message uses the existing SupabaseClient.sendMessage().
 * 6. The realtime subscription is owned by this Activity and is removed with the Activity,
 *    and is re-created whenever the Activity comes back to the foreground.
 * 7. Keyboard-overlap is handled in-code (edge-to-edge + WindowInsets), independent of
 *    manifest windowSoftInputMode.
 * 8. Image attachments preview in the chat bubble and open in an in-app zoomable viewer.
 * 9. PDF attachments open in an in-app swipeable, lazy-loading page viewer.
 */
class ChatActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#083F39")
    private val colorAccent = Color.parseColor("#16897A")
    private val colorBg = Color.parseColor("#F4F7F6")
    private val colorCard = Color.WHITE
    private val colorText = Color.parseColor("#111827")
    private val colorMuted = Color.parseColor("#6B7280")
    private val colorBorder = Color.parseColor("#E5E7EB")
    private val colorDanger = Color.parseColor("#B42318")
    private val colorSuccess = Color.parseColor("#15803D")

    // FIX: বড় পিডিএফ পেইজ রেন্ডারের সময় মেমোরি-সাশ্রয়ী রাখতে ক্যাশ সাইজ সীমা।
    private val PDF_PAGE_CACHE_SIZE = 5

    private var appointmentId = ""
    private var patientId = ""
    private var patientName = ""
    private var conversationId: String? = null
    private var mySenderId = ""
    private var myRole = "patient"

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var inputField: EditText
    private lateinit var sendButton: IconButtonView
    private lateinit var titleText: TextView
    private lateinit var typingText: TextView
    private lateinit var headerView: LinearLayout
    private lateinit var rootView: LinearLayout

    private val messages = mutableListOf<JSONObject>()
    private var conversationJob: Job? = null
    private var realtimeJob: Job? = null
    private var realtimeChannel: RealtimeChannel? = null
    private var realtimeStarted = false

    // In-memory cache so attachment images are not re-downloaded on every
    // RecyclerView rebind / scroll, and reused when opening the full viewer.
    private val imageCache = LruCache<String, Bitmap>(24)

    // FIX: WhatsApp-স্টাইল আপলোড প্রিভিউ — পিক করা ছবির লোকাল থাম্বনেইল ক্যাশ,
    // যাতে আপলোড শেষ না হওয়া পর্যন্ত bubble-এ সাথে সাথে ছবি দেখা যায় (নেটওয়ার্ক ছাড়াই)।
    private val pendingImageCache = LruCache<String, Bitmap>(12)

    // Holds the destination Uri while the camera app is capturing a photo.
    private var pendingCameraUri: Uri? = null

    // FIX: আগে যে Thread.UncaughtExceptionHandler বসানো ছিল তা ধরে রাখা হয়,
    // যাতে রিয়েলটাইম সকেট ছাড়া অন্য যেকোনো ক্র্যাশ আগের মতোই রিপোর্ট হয়।
    private var previousUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    // FIX: WhatsApp-স্টাইল আপলোড — কোনো পেন্ডিং আপলোড ব্যর্থ হলে সেই আপলোডটা
    // আবার শুরু করার জন্য (retry-on-tap) local_id দিয়ে রাখা রিট্রাই অ্যাকশন।
    private val pendingRetryActions = mutableMapOf<String, () -> Unit>()

    /*
     * These values mirror the existing SupabaseClient because the uploaded
     * SupabaseClient currently keeps them private.
     *
     * Prefer moving these values to BuildConfig in a production build.
     */
    private val supabaseUrl =
        "https://azbleibkgerzaqbrrydl.supabase.co"

    private val supabaseAnonKey =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF6YmxlaWJrZ2VyemFxYnJyeWRsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODkxNzExNjYsImV4cCI6MjEwNDc0NzE2Nn0.6Q6PMcRJHXFIRPUZZf9lOjoTmq77_wbCoKc8tGkVF2o"

    private val realtimeSupabase by lazy {
        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseAnonKey
        ) {
            install(Realtime)
        }
    }

    private val documentPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) uploadAndSendAttachment(uri, null)
        }

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) uploadAndSendAttachment(uri, "image/*".let { contentResolver.getType(uri) } ?: "image/jpeg")
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCamera()
            } else {
                Toast.makeText(
                    this,
                    "ছবি তুলতে ক্যামেরা পারমিশন প্রয়োজন",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val cameraCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingCameraUri
            if (success && uri != null) {
                uploadAndSendAttachment(uri, "image/jpeg")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FIX: রিয়েলটাইম WebSocket রিডার থ্রেডে হঠাৎ SocketException
        // ("Software caused connection abort") আসলে পুরো অ্যাপ ক্র্যাশ করত
        // (CRASH REPORT দেখুন)। এই সকেট এক্সসেপশনটা supabase-kt লাইব্রেরির
        // নিজস্ব internal coroutine-এ ঘটে বলে আমাদের নিজস্ব try/catch এটা
        // ধরতে পারে না — তাই থ্রেড লেভেলে একটা সেফটি-নেট বসানো হলো: শুধু এই
        // নির্দিষ্ট নেটওয়ার্ক এক্সসেপশনটা চুপচাপ লগ করে রিয়েলটাইম রিকানেক্ট করা
        // হবে, অন্য যেকোনো আসল ক্র্যাশ আগের মতোই স্বাভাবিকভাবে রিপোর্ট হবে।
        installRealtimeCrashGuard()

        // FIX: এখন কীবোর্ড ওভারল্যাপ ফিক্স ম্যানিফেস্টের windowSoftInputMode এর উপর নির্ভর না করে
        // কোডেই edge-to-edge + WindowInsets দিয়ে হ্যান্ডল করা হয় (buildUi() এর ভেতরে দেখুন)।
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )

        appointmentId = intent.getStringExtra("appointment_id").orEmpty()
        patientId =
            intent.getStringExtra("patient_id")
                ?: SupabaseClient.getPatientId(this).orEmpty()
        patientName =
            intent.getStringExtra("patient_name")
                ?: SupabaseClient.getName(this)
                ?: "রোগী"

        if (appointmentId.isBlank()) {
            Toast.makeText(this, "appointment_id পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (SupabaseClient.isAdmin(this)) {
            myRole = "doctor"
            mySenderId = SupabaseClient.getPhone(this) ?: SupabaseClient.DOCTOR_PHONE
        } else {
            myRole = "patient"
            mySenderId =
                patientId.ifBlank {
                    SupabaseClient.getPatientId(this) ?: "unknown"
                }
        }

        buildUi()
        openConversation()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        rootView = root

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(34), dp(12), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorAccent, colorPrimaryDark)
            )
        }
        headerView = header

        val back = IconButtonView(this, IconType.BACK).apply {
            setColor(Color.WHITE)
            contentDescription = "ফিরে যান"
            setOnClickListener { finish() }
        }

        val avatar = AvatarView(this).apply {
            initials = initials(patientName)
        }

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginStart = dp(10)
            }
        }

        // FIX: "রোগী"/"ডাক্তার" সাবটাইটেল ফিল্ড এবং অনলাইন স্ট্যাটাস ডট — দুটোই
        // সম্পূর্ণ বাদ দেওয়া হয়েছে ("অনলাইন লেখা ফিল্ডটা একেবারে বাদ দাও")।
        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        titleText = TextView(this).apply {
            text = patientName
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }

        nameRow.addView(titleText)

        typingText = TextView(this).apply {
            text = ""
            setTextColor(Color.WHITE)
            textSize = 10f
            visibility = View.GONE
        }

        titleColumn.addView(nameRow)
        titleColumn.addView(typingText)

        // FIX: থ্রি-ডট (⋮) মেনু আইকন ও তার অ্যাকশন সম্পূর্ণ বাদ দেওয়া হয়েছে।
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(52)))
        header.addView(avatar, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(titleColumn)

        root.addView(header)

        // FIX: "প্রাইভেট চিকিৎসা চ্যাট" / কানেকশন স্ট্যাটাস বার সম্পূর্ণ বাদ দেওয়া
        // হয়েছে ("অনলাইন লেখা ফিল্ডটা একেবারে বাদ দাও") — হেডারের ঠিক নিচেই
        // এখন সরাসরি চ্যাট লিস্ট বসবে।
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            itemAnimator = null
        }

        adapter = MessageAdapter(messages, mySenderId)
        recyclerView.adapter = adapter
        root.addView(recyclerView)

        // NOTE: The quick-action row (প্রেসক্রিপশন/রিপোর্ট/অ্যাপয়েন্টমেন্ট) that used to
        // sit directly above the composer has been removed per request, so the
        // chat list sits right above the message box.

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.WHITE)
        }

        val attach = IconButtonView(this, IconType.ATTACH).apply {
            setColor(colorPrimary)
            contentDescription = "ফাইল সংযুক্ত করুন"
            setOnClickListener { showAttachmentOptions() }
        }

        inputField = EditText(this).apply {
            hint = "মেসেজ লিখুন"
            setHintTextColor(colorMuted)
            setTextColor(colorText)
            textSize = 14f
            maxLines = 5
            minLines = 1
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = roundedBackground(
                Color.parseColor("#F1F5F4"),
                22f,
                Color.TRANSPARENT
            )
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(6)
            }
        }

        sendButton = IconButtonView(this, IconType.SEND).apply {
            setColor(Color.WHITE)
            background = roundedBackground(colorPrimary, 23f, Color.TRANSPARENT)
            contentDescription = "মেসেজ পাঠান"
            setOnClickListener { sendTextMessage() }
        }

        composer.addView(attach, LinearLayout.LayoutParams(dp(44), dp(48)))
        composer.addView(inputField)
        composer.addView(sendButton, LinearLayout.LayoutParams(dp(46), dp(46)))

        root.addView(composer)

        setContentView(root)

        // FIX: কীবোর্ড ওভারল্যাপ প্রতিরোধ — decorFitsSystemWindows(false) করার কারণে
        // এখন সিস্টেম বার/কীবোর্ড ইনসেট নিজেই ম্যানুয়ালি হ্যান্ডল করা হচ্ছে:
        // - হেডারের টপ প্যাডিং = স্ট্যাটাস বার ইনসেট + স্বাভাবিক প্যাডিং
        // - রুটের বটম প্যাডিং = কীবোর্ড উঁচু হলে কীবোর্ডের উচ্চতা, নাহলে নেভিগেশন বার ইনসেট
        // ফলে কম্পোজার (মেসেজ বক্স) কখনো কীবোর্ডের নিচে ঢাকা পড়বে না।
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            headerView.updatePadding(top = dp(12) + systemBars.top)
            view.updatePadding(bottom = if (imeBottom > 0) imeBottom else systemBars.bottom)

            insets
        }
    }

    private fun openConversation() {
        conversationJob?.cancel()
        conversationJob = lifecycleScope.launch {
            try {
                setStatus("চ্যাট খোলা হচ্ছে...", false)

                val conversation =
                    SupabaseClient.getOrCreateConversation(
                        appointmentId,
                        patientId
                    )

                conversationId = conversation.optString("id")

                if (conversationId.isNullOrBlank()) {
                    throw IllegalStateException("conversation id পাওয়া যায়নি")
                }

                titleText.text =
                    if (myRole == "doctor") patientName else "ডাক্তার"

                loadInitialHistory()
                startRealtime()

            } catch (e: Exception) {
                setStatus("চ্যাট সংযোগ ব্যর্থ", false)
                Toast.makeText(
                    this@ChatActivity,
                    "চ্যাট খোলা যায়নি: ${e.message ?: "অজানা সমস্যা"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun loadInitialHistory() {
        val id = conversationId ?: return
        val rows = SupabaseClient.getMessages(id)

        messages.clear()

        for (i in 0 until rows.length()) {
            messages.add(rows.getJSONObject(i))
        }

        runOnUiThread {
            adapter.notifyDataSetChanged()
            scrollToBottom(false)
        }
    }

    /**
     * The ONLY realtime listener for this chat.
     *
     * It listens directly to public.messages (table filter set below) and
     * additionally filters by conversation_id locally.
     *
     * IMPORTANT FIX: earlier this flow was created WITHOUT a `table` filter,
     * so Supabase never matched any postgres_changes event to this
     * subscription and no realtime INSERT ever arrived — messages only
     * appeared after leaving and reopening the Activity (which reloads
     * history via REST). Setting `table = "messages"` fixes this.
     */
    private fun startRealtime() {
        if (realtimeStarted) return

        val id = conversationId
        if (id.isNullOrBlank()) return

        realtimeStarted = true
        realtimeJob?.cancel()

        realtimeJob = lifecycleScope.launch {
            try {
                setStatus("সংযোগ হচ্ছে...", false)

                val channelName =
                    "chat-$id-${System.currentTimeMillis()}"

                val realtimeChannelLocal: RealtimeChannel = realtimeSupabase.channel(channelName)
                realtimeChannel = realtimeChannelLocal

                /*
                 * Table filter is REQUIRED here. Without it Supabase Realtime
                 * does not know which table's changes to stream to this
                 * channel and the collect{} below never receives anything.
                 *
                 * conversation_id is still filtered locally in
                 * handleRealtimeInsert/Update/Delete for extra safety and for
                 * compatibility across supabase-kt column-filter DSL versions.
                 */
                val changes =
                    realtimeChannelLocal.postgresChangeFlow<PostgresAction>(
                        schema = "public"
                    ) {
                        table = "messages"
                    }

                /*
                 * Subscribe from the SAME coroutine that owns the channel.
                 * subscribe() is suspend in supabase-kt.
                 */
                realtimeChannelLocal.subscribe(blockUntilSubscribed = true)
                setStatus("অনলাইন", true)

                changes.collect { action: PostgresAction ->
                    when (action) {
                        is PostgresAction.Insert -> {
                            handleRealtimeInsert(JSONObject(action.record.toString()))
                        }

                        is PostgresAction.Update -> {
                            handleRealtimeUpdate(JSONObject(action.record.toString()))
                        }

                        is PostgresAction.Delete -> {
                            handleRealtimeDelete(JSONObject(action.oldRecord.toString()))
                        }

                        is PostgresAction.Select -> {
                            handleRealtimeInsert(JSONObject(action.record.toString()))
                        }
                    }
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                /*
                 * Normal when Activity stops/destroys or realtimeJob is
                 * cancelled. Do not show an error Toast.
                 */
                throw e

            } catch (e: Exception) {
                realtimeStarted = false

                setStatus(
                    "সংযোগ বিচ্ছিন্ন",
                    false
                )

                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this@ChatActivity,
                        "সংযোগ ত্রুটি: ${e.message ?: "অজানা সমস্যা"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun handleRealtimeInsert(record: JSONObject) {
        val recordConversationId =
            record.optString("conversation_id", "")

        if (recordConversationId != conversationId) return

        val recordId = record.optString("id", "")

        if (recordId.isNotBlank() && containsMessageId(recordId)) {
            return
        }

        messages.add(record)

        messages.sortBy {
            it.optString("created_at", "")
        }

        runOnUiThread {
            adapter.notifyDataSetChanged()
            scrollToBottom(true)
        }
    }

    private fun handleRealtimeUpdate(record: JSONObject) {
        val id = record.optString("id", "")
        if (id.isBlank()) return

        val index = findMessageIndex(id)
        if (index >= 0) {
            messages[index] = record

            runOnUiThread {
                adapter.notifyItemChanged(index)
            }
        }
    }

    private fun handleRealtimeDelete(oldRecord: JSONObject) {
        val id = oldRecord.optString("id", "")
        if (id.isBlank()) return

        val index = findMessageIndex(id)

        if (index >= 0) {
            messages.removeAt(index)

            runOnUiThread {
                adapter.notifyItemRemoved(index)
            }
        }
    }

    private fun sendTextMessage() {
        val text = inputField.text.toString().trim()

        if (text.isBlank()) return

        val id = conversationId

        if (id.isNullOrBlank()) {
            Toast.makeText(
                this,
                "চ্যাট এখনো প্রস্তুত হয়নি",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        inputField.setText("")
        sendButton.isEnabled = false

        lifecycleScope.launch {
            try {
                /*
                 * Do not add an optimistic row here.
                 * The INSERT from Supabase Realtime is the single source
                 * that inserts the message into the RecyclerView.
                 */
                SupabaseClient.sendMessage(
                    id,
                    mySenderId,
                    myRole,
                    text
                )

            } catch (e: Exception) {
                inputField.setText(text)

                Toast.makeText(
                    this@ChatActivity,
                    "মেসেজ পাঠানো যায়নি: ${e.message ?: "অজানা সমস্যা"}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                sendButton.isEnabled = true
            }
        }
    }

    private fun insertQuickText(text: String) {
        inputField.setText(text)
        inputField.setSelection(inputField.text.length)
        inputField.requestFocus()
    }

    private fun showAttachmentOptions() {
        val dialog = android.app.Dialog(this)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = roundedBackground(Color.WHITE, 18f, colorBorder)
        }

        val title = TextView(this).apply {
            text = "চিকিৎসা ফাইল নির্বাচন করুন"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorText)
            setPadding(0, 0, 0, dp(12))
        }

        box.addView(title)

        addDialogAction(
            box,
            "ছবি / রিপোর্ট",
            IconType.IMAGE
        ) {
            dialog.dismiss()
            imagePicker.launch("image/*")
        }

        addDialogAction(
            box,
            "PDF / DOC / অন্যান্য ফাইল",
            IconType.DOCUMENT
        ) {
            dialog.dismiss()
            documentPicker.launch(
                arrayOf(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain"
                )
            )
        }

        addDialogAction(
            box,
            "ক্যামেরা",
            IconType.CAMERA
        ) {
            dialog.dismiss()
            openCamera()
        }

        addDialogAction(
            box,
            "বাতিল",
            IconType.CLOSE
        ) {
            dialog.dismiss()
        }

        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun addDialogAction(
        parent: LinearLayout,
        label: String,
        icon: IconType,
        action: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

        val iconView = IconButtonView(this, icon).apply {
            setColor(colorPrimary)
            isClickable = false
        }

        val text = TextView(this).apply {
            this.text = label
            textSize = 14f
            setTextColor(colorText)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            ).apply {
                marginStart = dp(10)
            }
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(iconView, LinearLayout.LayoutParams(dp(42), dp(48)))
        row.addView(text)
        parent.addView(row)
    }

    // ------------------------------------------------------------------
    // CAMERA CAPTURE
    // ------------------------------------------------------------------

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            val photoFile = File(
                cacheDir,
                "chat_photo_${System.currentTimeMillis()}.jpg"
            )
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                photoFile
            )
            pendingCameraUri = uri
            cameraCaptureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "ক্যামেরা চালু করা যায়নি: ${e.message ?: "অজানা সমস্যা"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ------------------------------------------------------------------
    // ATTACHMENT UPLOAD + SEND (real implementation)
    // ------------------------------------------------------------------

    // FIX: WhatsApp-স্টাইল আপলোড — এখন ফাইল বেছে নেওয়ার সাথে সাথে চ্যাটে একটা
    // bubble দেখানো হয় (ছবি হলে লোকাল থাম্বনেইলসহ) এবং আপলোড হতে হতে সেই
    // bubble-এ বাস্তব প্রগ্রেস % আপডেট হতে থাকে, ঠিক যেমন WhatsApp দেখায়।
    private fun uploadAndSendAttachment(uri: Uri, mimeTypeHint: String?) {
        val id = conversationId
        if (id.isNullOrBlank()) {
            Toast.makeText(
                this,
                "চ্যাট এখনো প্রস্তুত হয়নি",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val fileName = getFileName(uri)
        val mimeType = mimeTypeHint
            ?: contentResolver.getType(uri)
            ?: "application/octet-stream"
        val isImage = mimeType.startsWith("image/")

        val localId = "local_${System.currentTimeMillis()}_${(1000..9999).random()}"

        if (isImage) {
            val preview = decodeSampledBitmap(uri, 480)
            if (preview != null) {
                pendingImageCache.put(localId, preview)
            }
        }

        val pendingEnvelope = JSONObject().apply {
            put("kind", "pending_attachment")
            put("local_id", localId)
            put("file_name", fileName)
            put("mime_type", mimeType)
            put("is_image", isImage)
            put("progress", 0)
            put("failed", false)
        }

        val pendingMessage = JSONObject().apply {
            put("id", "")
            put("sender_id", mySenderId)
            put("sender_role", myRole)
            put("created_at", "")
            put("message", pendingEnvelope.toString())
        }

        messages.add(pendingMessage)
        val insertedAt = messages.lastIndex
        runOnUiThread {
            adapter.notifyItemInserted(insertedAt)
            scrollToBottom(true)
        }

        fun startUpload() {
            lifecycleScope.launch {
                try {
                    updatePendingEnvelope(localId) { env ->
                        env.put("progress", 0)
                        env.put("failed", false)
                    }

                    val bytes = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("ফাইল পড়া যায়নি")
                    }

                    if (bytes.size > 25 * 1024 * 1024) {
                        removePendingMessage(localId)
                        Toast.makeText(
                            this@ChatActivity,
                            "সর্বোচ্চ ২৫ MB ফাইল পাঠানো যাবে",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    var lastReportedPercent = -1
                    val attachment = SupabaseClient.uploadChatAttachmentWithProgress(
                        id,
                        mySenderId,
                        fileName,
                        mimeType,
                        bytes
                    ) { sent, total ->
                        val percent = if (total > 0) ((sent * 100) / total).toInt() else 0
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            updatePendingEnvelope(localId) { env ->
                                env.put("progress", percent)
                            }
                        }
                    }.getOrThrow()

                    updatePendingEnvelope(localId) { env ->
                        env.put("progress", 100)
                    }

                    /*
                     * The Supabase Realtime INSERT event also delivers this
                     * same message to this screen; containsMessageId() below
                     * prevents it from being added twice.
                     */
                    val sentRow = SupabaseClient.sendAttachmentMessage(
                        id,
                        mySenderId,
                        myRole,
                        attachment
                    ).getOrThrow()

                    pendingRetryActions.remove(localId)
                    pendingImageCache.remove(localId)
                    replacePendingMessage(localId, sentRow)

                } catch (e: Exception) {
                    pendingRetryActions[localId] = { startUpload() }
                    updatePendingEnvelope(localId) { env ->
                        env.put("failed", true)
                    }
                    Toast.makeText(
                        this@ChatActivity,
                        "ফাইল পাঠানো যায়নি: ${e.message ?: "অজানা সমস্যা"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        startUpload()
    }

    // FIX: পিক করা ছবির Uri থেকে সরাসরি একটা ছোট, ডাউনস্কেল থাম্বনেইল দ্রুত
    // ডিকোড করা হয় (নেটওয়ার্ক ছাড়াই) যাতে আপলোড শুরুর সাথে সাথে bubble-এ ছবি
    // দেখা যায় — ঠিক যেমন WhatsApp আপলোডের আগেই লোকাল প্রিভিউ দেখায়।
    private fun decodeSampledBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }

            var sampleSize = 1
            val w = boundsOptions.outWidth
            val h = boundsOptions.outHeight
            if (w > 0 && h > 0) {
                while ((w / sampleSize) > maxDimension || (h / sampleSize) > maxDimension) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun findPendingIndexByLocalId(localId: String): Int {
        return messages.indexOfFirst {
            val envelope = parseEnvelope(it.optString("message", ""))
            envelope != null &&
                envelope.optString("kind") == "pending_attachment" &&
                envelope.optString("local_id") == localId
        }
    }

    private fun updatePendingEnvelope(localId: String, mutate: (JSONObject) -> Unit) {
        val index = findPendingIndexByLocalId(localId)
        if (index < 0) return
        val msg = messages[index]
        val envelope = parseEnvelope(msg.optString("message", "")) ?: return
        mutate(envelope)
        msg.put("message", envelope.toString())
        runOnUiThread {
            adapter.notifyItemChanged(index)
        }
    }

    private fun removePendingMessage(localId: String) {
        val index = findPendingIndexByLocalId(localId)
        pendingImageCache.remove(localId)
        pendingRetryActions.remove(localId)
        if (index < 0) return
        messages.removeAt(index)
        runOnUiThread {
            adapter.notifyItemRemoved(index)
        }
    }

    private fun replacePendingMessage(localId: String, realRow: JSONObject) {
        val index = findPendingIndexByLocalId(localId)
        if (index < 0) {
            // পেন্ডিং bubble ইতিমধ্যে সরে গেছে (যেমন realtime insert আগেই এসে
            // গেছে) — তাও ডুপ্লিকেট এড়াতে id চেক করে নেওয়া হচ্ছে।
            if (!containsMessageId(realRow.optString("id", ""))) {
                messages.add(realRow)
                messages.sortBy { it.optString("created_at", "") }
                runOnUiThread {
                    adapter.notifyDataSetChanged()
                    scrollToBottom(true)
                }
            }
            return
        }
        messages[index] = realRow
        runOnUiThread {
            adapter.notifyItemChanged(index)
        }
    }

    private fun getFileName(uri: Uri): String {
        var result = "ফাইল"

        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index =
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

            if (index >= 0 && cursor.moveToFirst()) {
                result = cursor.getString(index)
            }
        }

        return result
    }

    // ------------------------------------------------------------------
    // ATTACHMENT VIEWING (image thumbnail loading + PDF/image in-app viewers)
    // ------------------------------------------------------------------

    private fun parseAttachment(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        return try {
            val obj = JSONObject(trimmed)
            if (obj.optString("kind") == "attachment") obj else null
        } catch (e: Exception) {
            null
        }
    }

    private fun loadAttachmentImage(url: String, target: ImageView) {
        val cached = imageCache.get(url)
        if (cached != null) {
            target.setImageBitmap(cached)
            return
        }

        target.setImageDrawable(null)
        target.tag = url

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { downloadBitmap(url) }
            if (target.tag == url && bitmap != null) {
                imageCache.put(url, bitmap)
                target.setImageBitmap(bitmap)
            }
        }
    }

    // FIX: "ছবি চ্যাটে প্রিভিউ দেখায় না, ক্লিক করলে লোড হয় না" — Supabase
    // স্টোরেজের রিকোয়েস্টে আগে কোনো apikey/Authorization হেডার পাঠানো হতো না,
    // ফলে bucket পাবলিক না হলে (বা পাবলিক পলিসি ঠিকমতো সেট না থাকলে) প্রতিটা
    // ডাউনলোড ব্যর্থ হতো। এখন সবসময় anon key হেডার সহ রিকোয়েস্ট পাঠানো হচ্ছে,
    // যা পাবলিক ও প্রাইভেট — দুই ধরনের bucket-এই কাজ করে।
    private fun openAuthedConnection(urlString: String): HttpURLConnection {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.doInput = true
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("apikey", supabaseAnonKey)
        connection.setRequestProperty("Authorization", "Bearer $supabaseAnonKey")
        return connection
    }

    private fun downloadBitmap(urlString: String): Bitmap? {
        return try {
            val connection = openAuthedConnection(urlString)
            connection.connect()
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return null
            }
            connection.inputStream.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun openAttachmentUrl(url: String, mimeType: String) {
        if (url.isBlank()) {
            Toast.makeText(this, "ফাইল লিংক পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse(url), mimeType.ifBlank { "*/*" })
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e2: Exception) {
                Toast.makeText(
                    this,
                    "ফাইল খোলার মতো অ্যাপ পাওয়া যায়নি",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun isPdfUrl(nameOrUrl: String): Boolean =
        nameOrUrl.substringBefore("?").lowercase().endsWith(".pdf")

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        return if (kb < 1024) {
            "%.0f KB".format(kb)
        } else {
            "%.1f MB".format(kb / 1024.0)
        }
    }

    // ------------------------------------------------------------------
    // FIX: In-app zoomable image viewer — image attachments now open here
    // instead of an external app.
    // ------------------------------------------------------------------

    private fun openImageViewer(bitmap: Bitmap? = null, url: String? = null, title: String = "ছবি") {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val zoomImage = ZoomableImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val progress = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
            visibility = if (bitmap != null) View.GONE else View.VISIBLE
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(34), dp(14), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(190, 0, 0, 0), Color.TRANSPARENT)
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP }
        }

        val closeBtn = IconButtonView(this, IconType.CLOSE).apply {
            setColor(Color.WHITE)
            background = roundedBackground(Color.argb(60, 255, 255, 255), 30f, Color.TRANSPARENT)
            contentDescription = "বন্ধ করুন"
            setOnClickListener { dialog.dismiss() }
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginStart = dp(12) }
        }

        topBar.addView(closeBtn, LinearLayout.LayoutParams(dp(34), dp(34)))
        topBar.addView(titleView)

        root.addView(zoomImage)
        root.addView(progress)
        root.addView(topBar)
        dialog.setContentView(root)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.show()

        fun applyBitmap(bmp: Bitmap) {
            progress.visibility = View.GONE
            zoomImage.setImageBitmap(bmp)
            zoomImage.resetZoomFit()
        }

        if (bitmap != null) {
            applyBitmap(bitmap)
        } else if (url != null) {
            val cached = imageCache.get(url)
            if (cached != null) {
                applyBitmap(cached)
            } else {
                lifecycleScope.launch {
                    try {
                        val bmp = withContext(Dispatchers.IO) { downloadBitmap(url) }
                        if (bmp != null) {
                            imageCache.put(url, bmp)
                            applyBitmap(bmp)
                        } else {
                            progress.visibility = View.GONE
                            Toast.makeText(this@ChatActivity, "ছবি লোড করা যায়নি", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        progress.visibility = View.GONE
                        Toast.makeText(
                            this@ChatActivity,
                            "ছবি লোড ব্যর্থ: ${e.message ?: "অজানা সমস্যা"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private class ZoomableImageView(context: android.content.Context) : ImageView(context) {
        private val imgMatrix = Matrix()
        private var lastX = 0f
        private var lastY = 0f
        private var isDragging = false
        private var minScale = 1f
        private val maxScale = 8f
        private var currentScale = 1f

        private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                var factor = detector.scaleFactor
                val projected = currentScale * factor
                if (projected < minScale) factor = minScale / currentScale
                if (projected > maxScale) factor = maxScale / currentScale
                currentScale *= factor
                imgMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                imageMatrix = imgMatrix
                return true
            }
        })

        init {
            scaleType = ScaleType.MATRIX
            setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        lastX = event.x; lastY = event.y; isDragging = true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (isDragging && !scaleDetector.isInProgress) {
                            val dx = event.x - lastX
                            val dy = event.y - lastY
                            imgMatrix.postTranslate(dx, dy)
                            imageMatrix = imgMatrix
                            lastX = event.x; lastY = event.y
                        }
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_POINTER_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                    }
                }
                true
            }
        }

        fun resetZoomFit() {
            post {
                val d = drawable ?: return@post
                val vw = width.toFloat()
                val vh = height.toFloat()
                val dw = d.intrinsicWidth.toFloat()
                val dh = d.intrinsicHeight.toFloat()
                if (vw <= 0 || vh <= 0 || dw <= 0 || dh <= 0) return@post
                val scale = minOf(vw / dw, vh / dh)
                minScale = scale
                currentScale = scale
                imgMatrix.reset()
                imgMatrix.postScale(scale, scale)
                imgMatrix.postTranslate((vw - dw * scale) / 2f, (vh - dh * scale) / 2f)
                imageMatrix = imgMatrix
            }
        }
    }

    // ------------------------------------------------------------------
    // FIX: In-app swipeable, lazy-loading PDF viewer — PDF attachments now
    // open here instead of an external app. Other document types (doc,
    // docx, txt) still open externally via openAttachmentUrl().
    // ------------------------------------------------------------------

    private fun downloadToTempPdfFile(urlString: String): File {
        // FIX: "পিডিএফ ক্লিক করলে লোড হয় না" — এখানেও একই কারণে (apikey/
        // Authorization হেডার ছাড়া রিকোয়েস্ট) ডাউনলোড ব্যর্থ হচ্ছিল।
        val connection = openAuthedConnection(urlString)
        connection.connect()
        if (connection.responseCode !in 200..299) {
            val errorBody = try {
                connection.errorStream?.bufferedReader()?.use { it.readText() }
            } catch (_: Exception) {
                null
            }
            connection.disconnect()
            throw IOException(
                "PDF ডাউনলোড ব্যর্থ: ${connection.responseCode} ${errorBody.orEmpty()}"
            )
        }
        val bytes = connection.inputStream.use { it.readBytes() }
        val file = File(cacheDir, "chat_pdf_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    private data class PdfViewerViews(
        val root: FrameLayout,
        val viewPager: ViewPager2,
        val progress: ProgressBar,
        val loadingLabel: TextView,
        val titleText: TextView,
        val pageIndicator: TextView,
        val closeBtn: View
    )

    private fun buildPdfViewerViews(bgColor: Int, title: String): PdfViewerViews {
        val root = FrameLayout(this).apply {
            setBackgroundColor(bgColor)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val viewPager = ViewPager2(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            offscreenPageLimit = 1
            visibility = View.GONE
        }

        val progress = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
        }

        val loadingLabel = TextView(this).apply {
            text = "PDF লোড হচ্ছে, একটু অপেক্ষা করুন..."
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 11.5f
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                topMargin = dp(56)
            }
        }

        val pageIndicator = TextView(this).apply {
            text = ""
            setTextColor(Color.WHITE)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBackground(Color.argb(160, 0, 0, 0), 30f, Color.TRANSPARENT)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(22)
            }
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(34), dp(14), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(210, 0, 0, 0), Color.TRANSPARENT)
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP }
        }

        val closeBtn = IconButtonView(this, IconType.CLOSE).apply {
            setColor(Color.WHITE)
            background = roundedBackground(Color.argb(60, 255, 255, 255), 30f, Color.TRANSPARENT)
            contentDescription = "বন্ধ করুন"
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginStart = dp(12) }
        }

        topBar.addView(closeBtn, LinearLayout.LayoutParams(dp(34), dp(34)))
        topBar.addView(titleView)

        root.addView(viewPager)
        root.addView(progress)
        root.addView(loadingLabel)
        root.addView(pageIndicator)
        root.addView(topBar)

        return PdfViewerViews(root, viewPager, progress, loadingLabel, titleView, pageIndicator, closeBtn)
    }

    private fun openPdfViewer(url: String, title: String = "ডকুমেন্ট") {
        if (url.isBlank()) {
            Toast.makeText(this, "ফাইল লিংক পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val bgColor = Color.parseColor("#1A1A1A")
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bgColor))

        val views = buildPdfViewerViews(bgColor, title)
        views.closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(views.root)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.show()

        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { downloadToTempPdfFile(url) }
                openSwipeablePdf(file, views, title, dialog)
            } catch (e: Exception) {
                views.progress.visibility = View.GONE
                views.loadingLabel.visibility = View.GONE
                Toast.makeText(
                    this@ChatActivity,
                    "PDF লোড ব্যর্থ: ${e.message ?: "অজানা সমস্যা"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun openSwipeablePdf(
        file: File,
        views: PdfViewerViews,
        title: String,
        dialog: android.app.Dialog
    ) {
        val pfd = withContext(Dispatchers.IO) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: Exception) {
            withContext(Dispatchers.IO) {
                try { pfd.close() } catch (ex: Exception) { }
                try { file.delete() } catch (ex: Exception) { }
            }
            throw e
        }
        val pageCount = renderer.pageCount

        views.progress.visibility = View.GONE
        views.loadingLabel.visibility = View.GONE
        views.titleText.text = "$title ($pageCount পৃষ্ঠা)"
        views.pageIndicator.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
        views.pageIndicator.text = "1 / $pageCount"

        // FIX: PdfRenderer থ্রেড-সেফ নয়, তাই একসাথে একাধিক পেইজ রেন্ডার হওয়া ঠেকাতে Mutex ব্যবহার করা হচ্ছে।
        val rendererMutex = Mutex()
        val screenWidthPx = resources.displayMetrics.widthPixels
        val adapter = PdfPageAdapter(this@ChatActivity, renderer, rendererMutex, pageCount, screenWidthPx, lifecycleScope)

        views.viewPager.adapter = adapter
        views.viewPager.visibility = View.VISIBLE
        views.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                views.pageIndicator.text = "${position + 1} / $pageCount"
            }
        })

        dialog.setOnDismissListener {
            adapter.cancelAllAndClear()
            try { renderer.close() } catch (e: Exception) { }
            lifecycleScope.launch(Dispatchers.IO) {
                try { pfd.close() } catch (e: Exception) { }
                try { file.delete() } catch (e: Exception) { }
            }
        }
    }

    // FIX: ViewPager2 এর RecyclerView.Adapter — প্রতিটা পেইজ শুধু স্ক্রিনে আসার সময়
    // (lazy-loading) রেন্ডার হয়, এবং সাম্প্রতিক কয়েকটি বাদে বাকি bitmap ক্যাশ থেকে
    // সরিয়ে recycle করে দেয় যাতে মেমোরি কম লাগে। পেইজে ট্যাপ করলে ইন-অ্যাপ পিঞ্চ-জুম
    // ইমেজ ভিউয়ার খোলে (openImageViewer পুনঃব্যবহার করে)।
    private class PdfPageAdapter(
        private val activity: ChatActivity,
        private val renderer: PdfRenderer,
        private val rendererMutex: Mutex,
        private val pageCount: Int,
        private val screenWidthPx: Int,
        private val scope: CoroutineScope
    ) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

        private val maxCacheSize = activity.PDF_PAGE_CACHE_SIZE
        private val bitmapCache = object : LinkedHashMap<Int, Bitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>): Boolean {
                if (size > maxCacheSize) {
                    val bmp = eldest.value
                    if (!bmp.isRecycled) bmp.recycle()
                    return true
                }
                return false
            }
        }
        private val renderJobs = mutableMapOf<Int, Job>()

        inner class PageViewHolder(
            val frame: FrameLayout,
            val imageView: ImageView,
            val progress: ProgressBar
        ) : RecyclerView.ViewHolder(frame)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val frame = FrameLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val imageView = ImageView(activity).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    val m = activity.dp(10)
                    setMargins(m, m, m, m)
                }
                isClickable = true
                isFocusable = true
            }
            val progress = ProgressBar(activity).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                }
                indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
            }
            frame.addView(imageView)
            frame.addView(progress)
            return PageViewHolder(frame, imageView, progress)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val cached = bitmapCache[position]
            if (cached != null && !cached.isRecycled) {
                holder.imageView.setImageBitmap(cached)
                holder.progress.visibility = View.GONE
            } else {
                holder.imageView.setImageBitmap(null)
                holder.progress.visibility = View.VISIBLE
                renderJobs[position]?.cancel()
                val job = scope.launch {
                    val bmp = renderPage(position)
                    if (bmp != null) {
                        bitmapCache[position] = bmp
                        if (holder.bindingAdapterPosition == position) {
                            holder.imageView.setImageBitmap(bmp)
                            holder.progress.visibility = View.GONE
                        }
                    } else if (holder.bindingAdapterPosition == position) {
                        holder.progress.visibility = View.GONE
                    }
                }
                renderJobs[position] = job
            }
            holder.imageView.setOnClickListener {
                val bmp = bitmapCache[position]
                if (bmp != null && !bmp.isRecycled) {
                    activity.openImageViewer(bitmap = bmp, title = "পৃষ্ঠা ${position + 1}/$pageCount")
                }
            }
        }

        override fun onViewRecycled(holder: PageViewHolder) {
            val pos = holder.bindingAdapterPosition
            renderJobs[pos]?.cancel()
            renderJobs.remove(pos)
            holder.imageView.setOnClickListener(null)
        }

        override fun getItemCount(): Int = pageCount

        fun cancelAllAndClear() {
            renderJobs.values.forEach { it.cancel() }
            renderJobs.clear()
            bitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
            bitmapCache.clear()
        }

        private suspend fun renderPage(index: Int): Bitmap? = withContext(Dispatchers.IO) {
            rendererMutex.withLock {
                try {
                    val page = renderer.openPage(index)
                    val rawScale = (screenWidthPx.toFloat() / page.width.toFloat()) * 2f
                    val safeScale = rawScale.coerceIn(1f, 4f)
                    val outW = (page.width * safeScale).toInt().coerceAtLeast(1)
                    val outH = (page.height * safeScale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    // FIX: CRASH REPORT-এ দেখা java.net.SocketException
    // ("Software caused connection abort") রিয়েলটাইম WebSocket রিডার
    // থ্রেডে (DefaultDispatcher-worker) ঘটে, যেটা আমাদের নিজস্ব
    // try/catch-এর বাইরে। সেটাকে চুপচাপ ধরে অ্যাপ বাঁচিয়ে রাখা হয় এবং
    // রিয়েলটাইম রিকানেক্ট করার চেষ্টা করা হয়; অন্য কোনো ক্র্যাশ হলে
    // আগের হ্যান্ডলারকেই কল করা হয় যাতে স্বাভাবিক ক্র্যাশ রিপোর্টিং নষ্ট না হয়।
    private fun installRealtimeCrashGuard() {
        if (previousUncaughtExceptionHandler != null) return
        previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val isRealtimeSocketError =
                throwable is java.net.SocketException ||
                    throwable.cause is java.net.SocketException ||
                    (throwable.message?.contains("WebSocket", ignoreCase = true) == true)

            if (isRealtimeSocketError) {
                android.util.Log.e(
                    "ChatActivity",
                    "রিয়েলটাইম সকেট ত্রুটি ধরা পড়েছে, ক্র্যাশ না করে রিকানেক্ট করা হচ্ছে: ${throwable.message}"
                )
                try {
                    runOnUiThread {
                        try {
                            realtimeStarted = false
                            startRealtime()
                        } catch (_: Exception) {
                        }
                    }
                } catch (_: Exception) {
                }
            } else {
                previousUncaughtExceptionHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun setStatus(text: String, online: Boolean) {
        // FIX: "অনলাইন লেখা ফিল্ডটা একেবারে বাদ দাও" — কানেকশন স্ট্যাটাস টেক্সট
        // ও অনলাইন ডট UI থেকে সম্পূর্ণ সরিয়ে ফেলা হয়েছে। এই ফাংশনটা এখন কিছু
        // দেখায় না, কিন্তু বাকি সব কল-সাইট (openConversation/startRealtime/
        // stopRealtime) অপরিবর্তিত রাখতে ফাংশনটা রাখা হলো।
    }

    private fun containsMessageId(id: String): Boolean {
        return messages.any {
            it.optString("id", "") == id
        }
    }

    private fun findMessageIndex(id: String): Int {
        return messages.indexOfFirst {
            it.optString("id", "") == id
        }
    }

    private fun scrollToBottom(animated: Boolean) {
        if (messages.isEmpty()) return

        recyclerView.post {
            val position = messages.lastIndex
            if (animated) {
                recyclerView.smoothScrollToPosition(position)
            } else {
                recyclerView.scrollToPosition(position)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        /*
         * Re-establish the realtime subscription if the Activity comes back
         * to the foreground after onStop() tore it down (e.g. user switched
         * apps or the screen was turned off and back on) and the
         * conversation is already known.
         */
        if (!conversationId.isNullOrBlank() && !realtimeStarted) {
            startRealtime()
        }
    }

    override fun onStop() {
        stopRealtime()
        super.onStop()
    }

    override fun onDestroy() {
        conversationJob?.cancel()
        stopRealtime()
        previousUncaughtExceptionHandler?.let {
            Thread.setDefaultUncaughtExceptionHandler(it)
        }
        super.onDestroy()
    }

    private fun stopRealtime() {
        realtimeStarted = false

        /*
         * Cancel the collector first. This prevents the old realtime flow
         * from continuing after the Activity has stopped.
         */
        realtimeJob?.cancel()
        realtimeJob = null

        val channel = realtimeChannel
        realtimeChannel = null

        if (channel != null) {
            /*
             * Both unsubscribe() and removeChannel() are suspend functions
             * in supabase-kt 2.x, so they MUST run inside a coroutine.
             */
            lifecycleScope.launch {
                try {
                    channel.unsubscribe()
                } catch (_: Exception) {
                }

                /*
                 * NOTE: supabase-kt's RealtimeChannel/Realtime API for fully
                 * removing a channel object differs across 2.x point
                 * releases. unsubscribe() above already stops the socket
                 * subscription (which is what matters for this Activity's
                 * lifecycle), so we intentionally do not call an
                 * uncertain/unstable removeChannel() API here.
                 */
            }
        }
    }

    private fun roundedBackground(
        fill: Int,
        radiusDp: Float,
        stroke: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setColor(fill)
            if (stroke != Color.TRANSPARENT) {
                setStroke(dp(1), stroke)
            }
        }
    }

    private fun initials(name: String): String {
        val parts = name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        return when {
            parts.isEmpty() -> "SC"
            parts.size == 1 -> parts[0].take(2).uppercase(Locale.getDefault())
            else ->
                "${parts[0].first()}${parts[1].first()}".uppercase(
                    Locale.getDefault()
                )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private enum class IconType {
        BACK,
        ATTACH,
        SEND,
        PRESCRIPTION,
        REPORT,
        CALENDAR,
        IMAGE,
        DOCUMENT,
        CAMERA,
        CLOSE
    }

    private class AvatarView(
        context: android.content.Context
    ) : View(context) {

        var initials = "SC"

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) * 0.45f

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, radius, paint)

            paint.color = Color.parseColor("#0F6C61")
            paint.textSize = radius * 0.8f

            val fm = paint.fontMetrics
            val baseline =
                cy - (fm.ascent + fm.descent) / 2f

            canvas.drawText(
                initials,
                cx,
                baseline,
                paint
            )
        }
    }

    private class IconButtonView(
        context: android.content.Context,
        private val type: IconType
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 2.2f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private var iconColor = Color.WHITE

        init {
            minimumWidth = 42
            minimumHeight = 42
            isClickable = true
            isFocusable = true
        }

        fun setColor(color: Int) {
            iconColor = color
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            paint.color = iconColor

            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val s = minOf(w, h) * 0.23f

            when (type) {
                IconType.BACK -> drawBack(canvas, cx, cy, s)
                IconType.ATTACH -> drawAttach(canvas, cx, cy, s)
                IconType.SEND -> drawSend(canvas, cx, cy, s)
                IconType.PRESCRIPTION -> drawPrescription(canvas, cx, cy, s)
                IconType.REPORT -> drawReport(canvas, cx, cy, s)
                IconType.CALENDAR -> drawCalendar(canvas, cx, cy, s)
                IconType.IMAGE -> drawImage(canvas, cx, cy, s)
                IconType.DOCUMENT -> drawDocument(canvas, cx, cy, s)
                IconType.CAMERA -> drawCamera(canvas, cx, cy, s)
                IconType.CLOSE -> drawClose(canvas, cx, cy, s)
            }
        }

        private fun drawBack(c: Canvas, x: Float, y: Float, s: Float) {
            c.drawLine(x + s, y, x - s, y, paint)
            c.drawLine(x - s, y, x, y - s, paint)
            c.drawLine(x - s, y, x, y + s, paint)
        }

        private fun drawAttach(c: Canvas, x: Float, y: Float, s: Float) {
            val p = Path()
            p.moveTo(x + s * 0.6f, y - s * 0.2f)
            p.lineTo(x - s * 0.25f, y + s * 0.65f)
            p.cubicTo(
                x - s * 0.9f,
                y + s * 0.05f,
                x - s * 0.1f,
                y - s * 0.95f,
                x + s * 0.65f,
                y - s * 0.25f
            )
            p.cubicTo(
                x + s * 1.0f,
                y + s * 0.1f,
                x + s * 0.35f,
                y + s * 0.75f,
                x - s * 0.1f,
                y + s * 0.35f
            )
            c.drawPath(p, paint)
        }

        private fun drawSend(c: Canvas, x: Float, y: Float, s: Float) {
            val p = Path()
            p.moveTo(x - s, y - s * 0.8f)
            p.lineTo(x + s, y)
            p.lineTo(x - s, y + s * 0.8f)
            p.lineTo(x - s * 0.45f, y)
            p.close()
            c.drawPath(p, paint)
        }

        private fun drawPrescription(
            c: Canvas,
            x: Float,
            y: Float,
            s: Float
        ) {
            val left = x - s
            val top = y - s * 1.1f
            val right = x + s
            val bottom = y + s * 1.1f

            c.drawRoundRect(
                left,
                top,
                right,
                bottom,
                s * 0.18f,
                s * 0.18f,
                paint
            )

            c.drawLine(
                left + s * 0.35f,
                top + s * 0.55f,
                right - s * 0.35f,
                top + s * 0.55f,
                paint
            )

            c.drawLine(
                left + s * 0.35f,
                top + s * 1.0f,
                right - s * 0.35f,
                top + s * 1.0f,
                paint
            )
        }

        private fun drawReport(
            c: Canvas,
            x: Float,
            y: Float,
            s: Float
        ) {
            val left = x - s
            val top = y - s
            val right = x + s
            val bottom = y + s

            c.drawRect(left, top, right, bottom, paint)
            c.drawLine(left + s * 0.35f, top, left + s * 0.35f, bottom, paint)
            c.drawLine(left + s * 0.55f, top, left + s * 0.55f, bottom, paint)
            c.drawLine(left + s * 0.75f, top, left + s * 0.75f, bottom, paint)
        }

        private fun drawCalendar(
            c: Canvas,
            x: Float,
            y: Float,
            s: Float
        ) {
            val left = x - s
            val top = y - s * 0.85f
            val right = x + s
            val bottom = y + s * 0.85f

            c.drawRoundRect(
                left,
                top,
                right,
                bottom,
                s * 0.18f,
                s * 0.18f,
                paint
            )

            c.drawLine(left, top + s * 0.5f, right, top + s * 0.5f, paint)
            c.drawLine(left + s * 0.45f, top, left + s * 0.45f, top + s * 0.25f, paint)
            c.drawLine(left + s * 0.55f, top, left + s * 0.55f, top + s * 0.25f, paint)
        }

        private fun drawImage(
            c: Canvas,
            x: Float,
            y: Float,
            s: Float
        ) {
            val left = x - s
            val top = y - s * 0.8f
            val right = x + s
            val bottom = y + s * 0.8f

            c.drawRoundRect(
                left,
                top,
                right,
                bottom,
                s * 0.15f,
                s * 0.15f,
                paint
            )

            paint.style = Paint.Style.FILL
            c.drawCircle(
                x + s * 0.45f,
                y - s * 0.35f,
                s * 0.16f,
                paint
            )
            paint.style = Paint.Style.STROKE

            val path = Path()
            path.moveTo(left + s * 0.2f, bottom - s * 0.2f)
            path.lineTo(left + s * 0.75f, y)
            path.lineTo(right - s * 0.15f, bottom - s * 0.15f)
            c.drawPath(path, paint)
        }

        private fun drawDocument(
            c: Canvas,
            x: Float,
            y: Float,
            s: Float
        ) {
            val left = x - s * 0.8f
            val top = y - s
            val right = x + s * 0.8f
            val bottom = y + s

            c.drawRect(left, top, right, bottom, paint)
            c.drawLine(
                left + s * 0.3f,
                y - s * 0.35f,
                right - s * 0.25f,
                y - s * 0.35f,
                paint
            )
            c.drawLine(
                left + s * 0.3f,
                y,
                right - s * 0.25f,
                y,
                paint
            )
            c.drawLine(
                left + s * 0.3f,
                y + s * 0.35f,
                right - s * 0.25f,
                y + s * 0.35f,
                paint
            )
        }

        private fun drawCamera(
            c: Canvas,
            x: Float,
            y: Float,
            s: Float
        ) {
            c.drawRoundRect(
                x - s * 1.15f,
                y - s * 0.65f,
                x + s * 1.15f,
                y + s * 0.65f,
                s * 0.2f,
                s * 0.2f,
                paint
            )

            c.drawCircle(x, y, s * 0.45f, paint)
            c.drawLine(
                x - s * 0.55f,
                y - s * 0.65f,
                x - s * 0.2f,
                y - s * 1.0f,
                paint
            )
        }

        private fun drawClose(
            c: Canvas,
            x: Float,
            y: Float,
            s: Float
        ) {
            c.drawLine(x - s, y - s, x + s, y + s, paint)
            c.drawLine(x + s, y - s, x - s, y + s, paint)
        }
    }

    private inner class MessageAdapter(
        private val list: List<JSONObject>,
        private val currentUserId: String
    ) : RecyclerView.Adapter<MessageAdapter.MessageHolder>() {

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): MessageHolder {
            val container = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(3), dp(4), dp(3))
            }

            return MessageHolder(container)
        }

        override fun onBindViewHolder(
            holder: MessageHolder,
            position: Int
        ) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size

        inner class MessageHolder(
            private val container: LinearLayout
        ) : RecyclerView.ViewHolder(container) {

            fun bind(message: JSONObject) {
                container.removeAllViews()

                val senderId =
                    message.optString("sender_id", "")

                val rawText =
                    message.optString("message", "")

                val createdAt =
                    message.optString("created_at", "")

                val mine =
                    senderId == currentUserId

                val attachment = parseAttachment(rawText)

                val bubble = LinearLayout(this@ChatActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        dp(14),
                        dp(9),
                        dp(14),
                        dp(7)
                    )
                    background = roundedBackground(
                        if (mine) colorPrimary else Color.WHITE,
                        18f,
                        if (mine) Color.TRANSPARENT else colorBorder
                    )
                }

                if (attachment != null) {
                    bindAttachment(bubble, attachment, mine)
                } else {
                    val messageText = TextView(this@ChatActivity).apply {
                        this.text = rawText
                        textSize = 14f
                        setTextColor(
                            if (mine) Color.WHITE else colorText
                        )
                        setPadding(0, 0, 0, dp(3))
                    }
                    bubble.addView(messageText)
                }

                val time = TextView(this@ChatActivity).apply {
                    this.text = formatTime(createdAt)
                    textSize = 9f
                    setTextColor(
                        if (mine)
                            Color.argb(210, 255, 255, 255)
                        else
                            colorMuted
                    )
                    gravity =
                        if (mine) Gravity.END else Gravity.START
                }

                bubble.addView(time)

                val params =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity =
                            if (mine) Gravity.END else Gravity.START
                    }

                container.addView(bubble, params)
            }

            private fun bindAttachment(
                bubble: LinearLayout,
                attachment: JSONObject,
                mine: Boolean
            ) {
                val fileName = attachment.optString("file_name", "ফাইল")
                val mimeType = attachment.optString("mime_type", "application/octet-stream")
                val fileSize = attachment.optLong("file_size", 0L)
                val url = attachment.optString("url", "")

                if (mimeType.startsWith("image/")) {
                    // FIX: ছবি প্রেভিউ ক্লিক করলে এখন ইন-অ্যাপ Zoomable Viewer খোলে।
                    val imageView = ImageView(this@ChatActivity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = roundedBackground(
                            Color.parseColor("#E5E7EB"),
                            12f,
                            Color.TRANSPARENT
                        )
                        layoutParams = LinearLayout.LayoutParams(dp(180), dp(180))
                        isClickable = true
                        setOnClickListener { openImageViewer(url = url, title = fileName) }
                    }
                    bubble.addView(imageView)
                    if (url.isNotBlank()) {
                        loadAttachmentImage(url, imageView)
                    }

                    val caption = TextView(this@ChatActivity).apply {
                        this.text = fileName
                        textSize = 11f
                        setTextColor(
                            if (mine) Color.argb(220, 255, 255, 255) else colorMuted
                        )
                        setPadding(0, dp(4), 0, 0)
                    }
                    bubble.addView(caption)
                } else {
                    // FIX: PDF হলে ইন-অ্যাপ Swipeable/Lazy-load PDF Viewer খোলে,
                    // অন্য ফাইল টাইপের জন্য আগের মতো এক্সটার্নাল অ্যাপে খোলে।
                    val isPdf =
                        mimeType == "application/pdf" ||
                            isPdfUrl(fileName) ||
                            (url.isNotBlank() && isPdfUrl(url))

                    val row = LinearLayout(this@ChatActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        isClickable = true
                        setOnClickListener {
                            if (isPdf) openPdfViewer(url, fileName) else openAttachmentUrl(url, mimeType)
                        }
                    }

                    val icon = IconButtonView(this@ChatActivity, IconType.DOCUMENT).apply {
                        setColor(if (mine) Color.WHITE else colorPrimary)
                        isClickable = false
                    }

                    val textColumn = LinearLayout(this@ChatActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            marginStart = dp(8)
                        }
                    }

                    val nameText = TextView(this@ChatActivity).apply {
                        this.text = fileName
                        textSize = 13f
                        setTextColor(if (mine) Color.WHITE else colorText)
                        typeface = Typeface.DEFAULT_BOLD
                        maxLines = 2
                    }

                    val sizeText = TextView(this@ChatActivity).apply {
                        this.text = formatFileSize(fileSize) + if (isPdf) " • PDF দেখতে ট্যাপ করুন" else " • ফাইল দেখতে ট্যাপ করুন"
                        textSize = 10f
                        setTextColor(
                            if (mine) Color.argb(220, 255, 255, 255) else colorMuted
                        )
                    }

                    textColumn.addView(nameText)
                    textColumn.addView(sizeText)

                    row.addView(icon, LinearLayout.LayoutParams(dp(34), dp(34)))
                    row.addView(textColumn)

                    bubble.addView(
                        row,
                        LinearLayout.LayoutParams(dp(210), ViewGroup.LayoutParams.WRAP_CONTENT)
                    )
                }
            }
        }
    }

    private fun formatTime(value: String): String {
        if (value.isBlank()) return ""

        return try {
            val cleaned =
                value.replace("Z", "")
                    .substringBefore(".")

            val input =
                SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    Locale.US
                )

            val output =
                SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
                )

            output.format(input.parse(cleaned) ?: Date())
        } catch (_: Exception) {
            value.takeLast(5)
        }
    }
}
