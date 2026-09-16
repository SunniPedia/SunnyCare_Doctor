package com.konasl.nagad

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorBg = Color.parseColor("#F4F7F6")
    private val colorTextMuted = Color.parseColor("#6B7280")
    private val colorDark = Color.parseColor("#111827")
    private val colorBorder = Color.parseColor("#DDE7E4")

    private var appointmentId = ""
    private var patientId = ""
    private var patientName = ""
    private var conversationId: String? = null
    private var mySenderId = ""
    private var myRole = "patient"

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var inputField: EditText
    private lateinit var statusText: TextView
    private lateinit var topTitle: TextView
    private lateinit var sendButton: IconButton
    private lateinit var attachmentButton: IconButton
    private lateinit var callButton: IconButton
    private lateinit var videoButton: IconButton

    private val messagesList = mutableListOf<JSONObject>()
    private var realtimeJob: Job? = null
    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    private val realtimeSupabase by lazy {
        createSupabaseClient(
            supabaseUrl = CHAT_SUPABASE_URL,
            supabaseKey = CHAT_SUPABASE_ANON_KEY
        ) {
            install(Realtime)
        }
    }

    private val attachmentPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) uploadSelectedAttachment(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appointmentId = intent.getStringExtra("appointment_id").orEmpty()
        patientId = intent.getStringExtra("patient_id")
            ?: SupabaseClient.getPatientId(this).orEmpty()
        patientName = intent.getStringExtra("patient_name")
            ?: SupabaseClient.getName(this).orEmpty().ifBlank { "রোগী" }

        if (appointmentId.isBlank()) {
            Toast.makeText(this, "appointment_id পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (SupabaseClient.isAdmin(this)) {
            myRole = "doctor"
            mySenderId = SupabaseClient.getPhone(this).orEmpty().ifBlank { "doctor" }
        } else {
            myRole = "patient"
            mySenderId = patientId.ifBlank {
                SupabaseClient.getPatientId(this).orEmpty()
            }
        }

        if (mySenderId.isBlank()) {
            Toast.makeText(this, "চ্যাট user identity পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        buildUi()
        initConversation()
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

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#16897A"), colorPrimaryDark)
            )
            setPadding(dp(10), dp(34), dp(10), dp(12))
        }

        val back = IconButton(this, IconType.BACK).apply {
            setOnClickListener { finish() }
        }

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(4), 0)
        }

        topTitle = TextView(this).apply {
            text = if (myRole == "doctor") patientName else "ডাক্তার"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        statusText = TextView(this).apply {
            text = "চ্যাট খোলা হচ্ছে..."
            textSize = 11f
            setTextColor(Color.argb(210, 255, 255, 255))
        }

        titleColumn.addView(topTitle)
        titleColumn.addView(statusText)

        callButton = IconButton(this, IconType.PHONE).apply {
            setOnClickListener { openAudioCall() }
        }
        videoButton = IconButton(this, IconType.VIDEO).apply {
            setOnClickListener { openVideoCall() }
        }

        topBar.addView(back)
        topBar.addView(titleColumn)
        topBar.addView(callButton)
        topBar.addView(videoButton)
        root.addView(topBar)

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding(dp(10), dp(10), dp(10), dp(10))
            clipToPadding = false
        }

        adapter = MessageAdapter(messagesList, mySenderId)
        recyclerView.adapter = adapter
        root.addView(recyclerView)

        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            elevation = dp(4).toFloat()
        }

        attachmentButton = IconButton(this, IconType.ATTACHMENT).apply {
            setOnClickListener {
                attachmentPicker.launch(
                    arrayOf(
                        "image/*",
                        "video/*",
                        "audio/*",
                        "application/pdf",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    )
                )
            }
        }

        inputField = EditText(this).apply {
            hint = "মেসেজ লিখুন..."
            setHintTextColor(colorTextMuted)
            setTextColor(colorDark)
            textSize = 15f
            maxLines = 5
            setPadding(dp(15), dp(10), dp(15), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
                setColor(Color.parseColor("#F1F5F4"))
                setStroke(dp(1), colorBorder)
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
        }

        sendButton = IconButton(this, IconType.SEND).apply {
            setOnClickListener { sendTextMessage() }
        }

        inputBar.addView(attachmentButton)
        inputBar.addView(inputField)
        inputBar.addView(sendButton)
        root.addView(inputBar)

        setContentView(root)
    }

    private fun initConversation() {
        lifecycleScope.launch {
            try {
                statusText.text = "Conversation প্রস্তুত হচ্ছে..."

                val conversation =
                    SupabaseClient.getOrCreateConversation(appointmentId, patientId)

                conversationId = conversation.optString("id").ifBlank { null }

                if (conversationId == null) {
                    throw IllegalStateException("conversation id পাওয়া যায়নি")
                }

                topTitle.text = if (myRole == "doctor") patientName else "ডাক্তার"

                loadInitialMessages()
                startChatRealtime(conversationId!!)

            } catch (e: Exception) {
                statusText.text = "কানেক্ট ব্যর্থ"
                Toast.makeText(
                    this@ChatActivity,
                    "চ্যাট খোলা যায়নি: ${e.message ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun loadInitialMessages() {
        val convId = conversationId ?: return
        val rows = SupabaseClient.getMessages(convId)

        messagesList.clear()
        for (i in 0 until rows.length()) {
            val message = rows.optJSONObject(i) ?: continue
            addMessageIfMissing(message)
        }

        adapter.notifyDataSetChanged()

        if (messagesList.isNotEmpty()) {
            recyclerView.scrollToPosition(messagesList.lastIndex)
        }
    }

    private fun startChatRealtime(convId: String) {
        realtimeJob?.cancel()

        realtimeJob = lifecycleScope.launch {
            try {
                statusText.text = "Realtime সংযোগ হচ্ছে..."

                realtimeChannel?.let {
                    realtimeSupabase.realtime.removeChannel(it)
                }

                val channel = realtimeSupabase.channel("chat:$convId")
                realtimeChannel = channel

                val changeFlow =
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "messages"
                        filter = "conversation_id=eq.$convId"
                    }

                /*
                 * Flow collector অবশ্যই subscribe() এর আগে register করা হচ্ছে।
                 * তারপর subscribe() call করা হচ্ছে, তাই INSERT/UPDATE/DELETE
                 * event miss হওয়ার সম্ভাবনা কমে এবং polling দরকার হয় না।
                 */
                val collectorJob = launch {
                    changeFlow.collect { action ->
                        handleRealtimeAction(action)
                    }
                }

                channel.subscribe(blockUntilSubscribed = true)
                statusText.text = "অনলাইন • Realtime"

                collectorJob.join()

            } catch (e: Exception) {
                statusText.text = "Realtime সংযোগ ব্যর্থ"
                Toast.makeText(
                    this@ChatActivity,
                    "Realtime error: ${e.message ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleRealtimeAction(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> {
                val obj = jsonObjectFromSupabase(action.record)
                addMessageIfMissing(obj)
                refreshLastMessage()
            }

            is PostgresAction.Update -> {
                val obj = jsonObjectFromSupabase(action.record)
                replaceMessage(obj)
                refreshLastMessage()
            }

            is PostgresAction.Delete -> {
                val old = jsonObjectFromSupabase(action.oldRecord)
                removeMessage(old.optString("id"))
                refreshLastMessage()
            }

            is PostgresAction.Select -> {
                // Chat screen-এর জন্য SELECT event দরকার নেই।
            }
        }
    }

    private fun jsonObjectFromSupabase(record: kotlinx.serialization.json.JsonObject): JSONObject {
        return JSONObject(record.toString())
    }

    private fun addMessageIfMissing(obj: JSONObject) {
        val id = obj.optString("id").trim()

        if (id.isNotEmpty()) {
            val exists = messagesList.any {
                it.optString("id").equals(id, ignoreCase = false)
            }
            if (exists) return
        } else {
            return
        }

        messagesList.add(obj)
        messagesList.sortBy { it.optString("created_at", "") }

        runOnUiThread {
            adapter.notifyDataSetChanged()
            recyclerView.scrollToPosition(messagesList.lastIndex)
        }
    }

    private fun replaceMessage(obj: JSONObject) {
        val id = obj.optString("id")
        if (id.isBlank()) return

        val index = messagesList.indexOfFirst {
            it.optString("id") == id
        }

        if (index >= 0) {
            messagesList[index] = obj
        } else {
            messagesList.add(obj)
            messagesList.sortBy { it.optString("created_at", "") }
        }

        runOnUiThread {
            adapter.notifyDataSetChanged()
        }
    }

    private fun removeMessage(id: String) {
        if (id.isBlank()) return

        val index = messagesList.indexOfFirst {
            it.optString("id") == id
        }

        if (index >= 0) {
            messagesList.removeAt(index)
            runOnUiThread {
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun refreshLastMessage() {
        // Database event already contains the authoritative message row.
        // No REST polling/reload is performed here.
    }

    private fun sendTextMessage() {
        val text = inputField.text?.toString()?.trim().orEmpty()
        val convId = conversationId

        if (text.isBlank()) return

        if (convId == null) {
            Toast.makeText(this, "চ্যাট এখনো প্রস্তুত নয়", Toast.LENGTH_SHORT).show()
            return
        }

        inputField.setText("")

        lifecycleScope.launch {
            try {
                /*
                 * এখানে optimistic insert করা হচ্ছে না।
                 * INSERT event Realtime থেকে আসবে এবং id দিয়ে deduplicate হবে।
                 */
                val result =
                    SupabaseClient.sendMessage(
                        convId,
                        mySenderId,
                        myRole,
                        text
                    )

                if (result == null) {
                    throw IllegalStateException("message insert failed")
                }

            } catch (e: Exception) {
                inputField.setText(text)
                Toast.makeText(
                    this@ChatActivity,
                    "মেসেজ পাঠানো যায়নি: ${e.message ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun uploadSelectedAttachment(uri: Uri) {
        val convId = conversationId

        if (convId == null) {
            Toast.makeText(this, "চ্যাট এখনো প্রস্তুত নয়", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val fileName = queryDisplayName(uri)
                val mimeType =
                    contentResolver.getType(uri) ?: "application/octet-stream"

                val bytes = contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                } ?: throw IllegalStateException("ফাইল পড়া যায়নি")

                if (bytes.isEmpty()) {
                    throw IllegalStateException("ফাইল খালি")
                }

                if (bytes.size > 25 * 1024 * 1024) {
                    throw IllegalStateException("সর্বোচ্চ 25 MB ফাইল নেওয়া যাবে")
                }

                attachmentButton.isEnabled = false
                sendButton.isEnabled = false
                statusText.text = "Attachment আপলোড হচ্ছে..."

                val uploaded =
                    SupabaseClient.uploadChatAttachment(
                        conversationId = convId,
                        senderId = mySenderId,
                        fileName = fileName,
                        mimeType = mimeType,
                        bytes = bytes
                    ).getOrThrow()

                SupabaseClient.sendAttachmentMessage(
                    conversationId = convId,
                    senderId = mySenderId,
                    senderRole = myRole,
                    attachment = uploaded
                ).getOrThrow()

                statusText.text = "অনলাইন • Realtime"

            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatActivity,
                    "Attachment পাঠানো যায়নি: ${e.message ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
                statusText.text = "অনলাইন • Realtime"
            } finally {
                attachmentButton.isEnabled = true
                sendButton.isEnabled = true
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index).orEmpty().ifBlank { "attachment" }
                }
            }
        }

        return "attachment_${System.currentTimeMillis()}"
    }

    private fun openAudioCall() {
        val intent = Intent(this, AudiocallActivity::class.java).apply {
            putExtra("appointment_id", appointmentId)
            putExtra("patient_id", patientId)
            putExtra("patient_name", patientName)
            putExtra("conversation_id", conversationId.orEmpty())
            putExtra("caller_id", mySenderId)
            putExtra("caller_role", myRole)
        }
        startActivity(intent)
    }

    private fun openVideoCall() {
        val intent = Intent(this, VideocallActivity::class.java).apply {
            putExtra("appointment_id", appointmentId)
            putExtra("patient_id", patientId)
            putExtra("patient_name", patientName)
            putExtra("conversation_id", conversationId.orEmpty())
            putExtra("caller_id", mySenderId)
            putExtra("caller_role", myRole)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        realtimeJob?.cancel()
        realtimeJob = null

        realtimeChannel?.let { channel ->
            lifecycleScope.launch {
                try {
                    realtimeSupabase.realtime.removeChannel(channel)
                } catch (_: Exception) {
                }
            }
        }

        realtimeChannel = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun formatTime(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val parser = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                Locale.US
            )
            val date = parser.parse(value)
                ?: SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    Locale.US
                ).parse(value)

            if (date == null) "" else
                SimpleDateFormat("hh:mm a", Locale.US).format(date)
        } catch (_: Exception) {
            value.substringAfter("T").take(5)
        }
    }

    private fun parseAttachment(message: JSONObject): JSONObject? {
        if (!message.has("message")) return null

        val raw = message.optString("message", "")
        if (raw.isBlank()) return null

        return try {
            val obj = JSONObject(raw)
            if (obj.optString("kind") == "attachment") obj else null
        } catch (_: Exception) {
            null
        }
    }

    private inner class MessageAdapter(
        private val list: List<JSONObject>,
        private val myId: String
    ) : RecyclerView.Adapter<MessageAdapter.MessageHolder>() {

        inner class MessageHolder(val root: LinearLayout) :
            RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): MessageHolder {
            val root = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(dp(4), dp(3), dp(4), dp(3))
            }

            return MessageHolder(root)
        }

        override fun onBindViewHolder(holder: MessageHolder, position: Int) {
            val item = list[position]
            val isMine = item.optString("sender_id") == myId
            val root = holder.root

            root.removeAllViews()
            root.gravity = if (isMine) Gravity.END else Gravity.START

            val attachment = parseAttachment(item)

            val bubble = LinearLayout(this@ChatActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(8))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(18).toFloat()
                    if (isMine) {
                        setColor(colorPrimary)
                    } else {
                        setColor(Color.WHITE)
                        setStroke(dp(1), colorBorder)
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    (resources.displayMetrics.widthPixels * 0.78f).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            if (attachment != null) {
                val fileName =
                    attachment.optString("file_name", "Attachment")
                val mime =
                    attachment.optString("mime_type", "application/octet-stream")

                val fileText = TextView(this@ChatActivity).apply {
                    text = "$fileName\n$mime"
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (isMine) Color.WHITE else colorDark)
                    setPadding(0, 0, 0, dp(4))
                    setOnClickListener {
                        openAttachment(attachment)
                    }
                }

                val openText = TextView(this@ChatActivity).apply {
                    text = "Open attachment"
                    textSize = 12f
                    setTextColor(
                        if (isMine) Color.argb(220, 255, 255, 255)
                        else colorPrimary
                    )
                    setOnClickListener {
                        openAttachment(attachment)
                    }
                }

                bubble.addView(fileText)
                bubble.addView(openText)
            } else {
                val text = TextView(this@ChatActivity).apply {
                    text = item.optString("message", "")
                    textSize = 15f
                    setTextColor(if (isMine) Color.WHITE else colorDark)
                }
                bubble.addView(text)
            }

            val time = TextView(this@ChatActivity).apply {
                text = formatTime(item.optString("created_at", ""))
                textSize = 10f
                gravity = Gravity.END
                setPadding(0, dp(4), 0, 0)
                setTextColor(
                    if (isMine) Color.argb(190, 255, 255, 255)
                    else colorTextMuted
                )
            }

            bubble.addView(time)
            root.addView(bubble)
        }

        private fun openAttachment(attachment: JSONObject) {
            val url = attachment.optString("url", "")
            if (url.isBlank()) {
                Toast.makeText(
                    this@ChatActivity,
                    "Attachment URL পাওয়া যায়নি",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatActivity,
                    "Attachment খোলা যায়নি",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun getItemCount(): Int = list.size
    }

    private enum class IconType {
        BACK,
        PHONE,
        VIDEO,
        ATTACHMENT,
        SEND
    }

    private class IconButton(
        context: android.content.Context,
        private val type: IconType
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = resources.displayMetrics.density * 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
        }

        init {
            isClickable = true
            isFocusable = true
            setPadding(
                (10 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                (44 * resources.displayMetrics.density).toInt(),
                (44 * resources.displayMetrics.density).toInt()
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val d = resources.displayMetrics.density
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f

            when (type) {
                IconType.BACK -> {
                    canvas.drawLine(cx + 7 * d, cy, cx - 7 * d, cy, paint)
                    canvas.drawLine(cx - 7 * d, cy, cx - 1 * d, cy - 6 * d, paint)
                    canvas.drawLine(cx - 7 * d, cy, cx - 1 * d, cy + 6 * d, paint)
                }

                IconType.PHONE -> {
                    val path = android.graphics.Path()
                    path.moveTo(cx - 8 * d, cy - 9 * d)
                    path.lineTo(cx - 4 * d, cy - 13 * d)
                    path.quadTo(cx - 1 * d, cy - 13 * d, cx + 2 * d, cy - 8 * d)
                    path.lineTo(cx + 6 * d, cy - 2 * d)
                    path.quadTo(cx + 9 * d, cy + 2 * d, cx + 6 * d, cy + 5 * d)
                    path.lineTo(cx + 2 * d, cy + 9 * d)
                    path.quadTo(cx - 7 * d, cy + 5 * d, cx - 10 * d, cy - 4 * d)
                    path.close()
                    canvas.drawPath(path, paint)
                }

                IconType.VIDEO -> {
                    canvas.drawRoundRect(
                        cx - 11 * d,
                        cy - 8 * d,
                        cx + 4 * d,
                        cy + 8 * d,
                        3 * d,
                        3 * d,
                        paint
                    )
                    val path = android.graphics.Path()
                    path.moveTo(cx + 4 * d, cy - 5 * d)
                    path.lineTo(cx + 11 * d, cy - 9 * d)
                    path.lineTo(cx + 11 * d, cy + 9 * d)
                    path.lineTo(cx + 4 * d, cy + 5 * d)
                    canvas.drawPath(path, paint)
                }

                IconType.ATTACHMENT -> {
                    val path = android.graphics.Path()
                    path.moveTo(cx + 7 * d, cy - 4 * d)
                    path.lineTo(cx - 2 * d, cy + 5 * d)
                    path.quadTo(cx - 7 * d, cy + 10 * d, cx - 12 * d, cy + 5 * d)
                    path.quadTo(cx - 17 * d, cy, cx - 12 * d, cy - 5 * d)
                    path.lineTo(cx - 2 * d, cy - 15 * d)
                    path.quadTo(cx + 3 * d, cy - 20 * d, cx + 8 * d, cy - 15 * d)
                    path.quadTo(cx + 13 * d, cy - 10 * d, cx + 8 * d, cy - 5 * d)
                    path.lineTo(cx - 2 * d, cy + 5 * d)
                    canvas.drawPath(path, paint)
                }

                IconType.SEND -> {
                    val path = android.graphics.Path()
                    path.moveTo(cx - 11 * d, cy - 8 * d)
                    path.lineTo(cx + 12 * d, cy)
                    path.lineTo(cx - 11 * d, cy + 8 * d)
                    path.lineTo(cx - 5 * d, cy)
                    path.close()
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    companion object {
        /*
         * Keep these identical to the project's Supabase project.
         * They are the same public anon configuration already used by
         * SupabaseClient.kt. For a cleaner final architecture, move them
         * to BuildConfig later instead of duplicating them in Activities.
         */
        private const val CHAT_SUPABASE_URL =
            "https://azbleibkgerzaqbrrydl.supabase.co"

        private const val CHAT_SUPABASE_ANON_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF6YmxlaWJrZ2VyemFxYnJyeWRsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODkxNzExNjYsImV4cCI6MjEwNDc0NzE2Nn0.6Q6PMcRJHXFIRPUZZf9lOjoTmq77_wbCoKc8tGkVF2o"
    }
}
