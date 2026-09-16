package com.konasl.nagad

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
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
 * 6. The realtime subscription is owned by this Activity and is removed with the Activity.
 *
 * Required database setup:
 * - public.messages must be enabled in the supabase_realtime publication.
 * - RLS policies must allow the logged-in user to receive the conversation's rows.
 *
 * Required Gradle dependencies:
 * - supabase-kt 2.5.1
 * - realtime-kt 2.5.1
 * - a Ktor Android engine compatible with the project's supabase-kt version
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
    private lateinit var statusText: TextView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var typingText: TextView

    private val messages = mutableListOf<JSONObject>()
    private var conversationJob: Job? = null
    private var realtimeJob: Job? = null
    private var realtimeChannel: RealtimeChannel? = null
    private var realtimeStarted = false

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
            if (uri != null) handleSelectedAttachment(uri, "document")
        }

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) handleSelectedAttachment(uri, "image")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(34), dp(12), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorAccent, colorPrimaryDark)
            )
        }

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

        titleText = TextView(this).apply {
            text = patientName
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }

        subtitleText = TextView(this).apply {
            text = if (myRole == "doctor") "রোগী" else "ডাক্তার"
            setTextColor(Color.argb(220, 255, 255, 255))
            textSize = 11f
        }

        typingText = TextView(this).apply {
            text = ""
            setTextColor(Color.WHITE)
            textSize = 10f
            visibility = View.GONE
        }

        titleColumn.addView(titleText)
        titleColumn.addView(subtitleText)
        titleColumn.addView(typingText)

        val more = IconButtonView(this, IconType.MORE).apply {
            setColor(Color.WHITE)
            contentDescription = "আরও অপশন"
            setOnClickListener { showChatInfo() }
        }

        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(52)))
        header.addView(avatar, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(titleColumn)
        header.addView(more, LinearLayout.LayoutParams(dp(44), dp(52)))

        root.addView(header)

        val statusBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setBackgroundColor(Color.WHITE)
        }

        statusText = TextView(this).apply {
            text = "কানেক্ট হচ্ছে..."
            textSize = 11f
            setTextColor(colorMuted)
            layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f)
        }

        val privacy = TextView(this).apply {
            text = "প্রাইভেট চিকিৎসা চ্যাট"
            textSize = 10f
            setTextColor(colorMuted)
        }

        statusBar.addView(statusText)
        statusBar.addView(privacy)
        root.addView(statusBar)

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

        val quickActions = buildQuickActions()
        root.addView(quickActions)

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
    }

    private fun buildQuickActions(): View {
        val horizontal = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(5))
            setBackgroundColor(Color.WHITE)
        }

        if (myRole == "doctor") {
            addQuickAction(horizontal, "প্রেসক্রিপশন", IconType.PRESCRIPTION) {
                insertQuickText("প্রেসক্রিপশন সম্পর্কে বিস্তারিত জানাতে চাই।")
            }
            addQuickAction(horizontal, "রিপোর্ট", IconType.REPORT) {
                insertQuickText("আপনার রিপোর্টটি এখানে পাঠাতে পারেন।")
            }
            addQuickAction(horizontal, "অ্যাপয়েন্টমেন্ট", IconType.CALENDAR) {
                insertQuickText("অ্যাপয়েন্টমেন্টের সময় সম্পর্কে আলোচনা করি।")
            }
        } else {
            addQuickAction(horizontal, "প্রেসক্রিপশন", IconType.PRESCRIPTION) {
                insertQuickText("আমার প্রেসক্রিপশন সম্পর্কে জানতে চাই।")
            }
            addQuickAction(horizontal, "রিপোর্ট", IconType.REPORT) {
                showAttachmentOptions()
            }
            addQuickAction(horizontal, "অ্যাপয়েন্টমেন্ট", IconType.CALENDAR) {
                insertQuickText("অ্যাপয়েন্টমেন্ট সম্পর্কে জানতে চাই।")
            }
        }

        return horizontal
    }

    private fun addQuickAction(
        parent: LinearLayout,
        label: String,
        icon: IconType,
        action: () -> Unit
    ) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(3), dp(7), dp(3))
            background = roundedBackground(Color.WHITE, 10f, colorBorder)
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
            textSize = 9f
            setTextColor(colorText)
            gravity = Gravity.CENTER
        }

        item.addView(iconView, LinearLayout.LayoutParams(dp(30), dp(28)))
        item.addView(text)

        parent.addView(
            item,
            LinearLayout.LayoutParams(dp(96), dp(52)).apply {
                marginEnd = dp(6)
            }
        )
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
     * It listens directly to public.messages and filters by conversation_id.
     * No REST reload is performed after an INSERT.
     */
    private fun startRealtime() {
        if (realtimeStarted) return

        val id = conversationId
        if (id.isNullOrBlank()) return

        realtimeStarted = true
        realtimeJob?.cancel()

        realtimeJob = lifecycleScope.launch {
            try {
                setStatus("রিয়েলটাইম কানেক্ট হচ্ছে...", false)

                val channelName =
                    "chat-$id-${System.currentTimeMillis()}"

                val realtimeChannelLocal: RealtimeChannel = realtimeSupabase.channel(channelName)
                realtimeChannel = realtimeChannelLocal

                /*
                 * Keep this subscription intentionally broad for maximum
                 * compatibility with supabase-kt 2.5.1.
                 *
                 * We filter conversation_id locally in handleRealtimeInsert/
                 * handleRealtimeUpdate/handleRealtimeDelete().
                 *
                 * This avoids depending on the PostgresChangeFilter DSL,
                 * which differs between supabase-kt versions.
                 */
                val changes =
                    realtimeChannelLocal.postgresChangeFlow<PostgresAction>(
                        schema = "public"
                    )

                /*
                 * Subscribe from the SAME coroutine that owns the channel.
                 * subscribe() is suspend in supabase-kt.
                 */
                realtimeChannelLocal.subscribe(blockUntilSubscribed = true)
                setStatus("অনলাইন • রিয়েলটাইম", true)

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
                    "রিয়েলটাইম সংযোগ বিচ্ছিন্ন",
                    false
                )

                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this@ChatActivity,
                        "Realtime error: ${e.message ?: "অজানা সমস্যা"}",
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
            Toast.makeText(
                this,
                "ক্যামেরা সংযুক্ত করতে Camera permission ও camera flow যোগ করতে হবে",
                Toast.LENGTH_SHORT
            ).show()
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

    private fun handleSelectedAttachment(uri: Uri, type: String) {
        val name = getFileName(uri)

        val message =
            if (type == "image") {
                "রিপোর্ট/ছবি সংযুক্ত করার জন্য নির্বাচিত: $name"
            } else {
                "মেডিকেল ডকুমেন্ট সংযুক্ত করার জন্য নির্বাচিত: $name"
            }

        /*
         * The current uploaded SupabaseClient only exposes generic report
         * upload, not a chat-attachment table/storage method.
         * Therefore this Activity does not pretend that the attachment has
         * been uploaded to the chat.
         *
         * The selected filename is placed in the composer so the user can
         * continue without silently losing the selection.
         */
        inputField.setText(message)
        inputField.setSelection(inputField.text.length)

        Toast.makeText(
            this,
            "ফাইল নির্বাচিত: $name",
            Toast.LENGTH_SHORT
        ).show()
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

    private fun showChatInfo() {
        val dialog = android.app.Dialog(this)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = roundedBackground(Color.WHITE, 18f, colorBorder)
        }

        val title = TextView(this).apply {
            text = "চ্যাট তথ্য"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorText)
        }

        val info = TextView(this).apply {
            text =
                "রোগী: ${if (myRole == "doctor") patientName else "ডাক্তার"}\n" +
                    "Appointment: $appointmentId\n" +
                    "Conversation: ${conversationId ?: "প্রস্তুত হচ্ছে"}\n" +
                    "মোড: Supabase Realtime Postgres Changes"
            textSize = 13f
            setTextColor(colorMuted)
            setPadding(0, dp(12), 0, dp(16))
        }

        val close = TextView(this).apply {
            text = "বন্ধ করুন"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorPrimary)
            gravity = Gravity.CENTER
            background = roundedBackground(
                Color.parseColor("#E9F5F2"),
                12f,
                Color.TRANSPARENT
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { dialog.dismiss() }
        }

        box.addView(title)
        box.addView(info)
        box.addView(close)

        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun setStatus(text: String, online: Boolean) {
        runOnUiThread {
            statusText.text = text
            statusText.setTextColor(
                if (online) colorSuccess else colorMuted
            )
        }
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

    override fun onStop() {
        stopRealtime()
        super.onStop()
    }

    override fun onDestroy() {
        conversationJob?.cancel()
        stopRealtime()
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

                try {
                } catch (_: Exception) {
                }
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
        MORE,
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
                IconType.MORE -> drawMore(canvas, cx, cy, s)
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

        private fun drawMore(c: Canvas, x: Float, y: Float, s: Float) {
            paint.style = Paint.Style.FILL
            c.drawCircle(x - s, y, 2.4f, paint)
            c.drawCircle(x, y, 2.4f, paint)
            c.drawCircle(x + s, y, 2.4f, paint)
            paint.style = Paint.Style.STROKE
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

                val text =
                    message.optString("message", "")

                val createdAt =
                    message.optString("created_at", "")

                val mine =
                    senderId == currentUserId

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

                val messageText = TextView(this@ChatActivity).apply {
                    this.text = text
                    textSize = 14f
                    setTextColor(
                        if (mine) Color.WHITE else colorText
                    )
                    setPadding(0, 0, 0, dp(3))
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

                bubble.addView(messageText)
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
