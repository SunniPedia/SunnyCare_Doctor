package com.konasl.nagad

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * SunnyCare Doctor - ChatActivity
 *
 * Realtime architecture:
 * 1. Initial message history is loaded once through the existing SupabaseClient REST API.
 * 2. New INSERT / UPDATE / DELETE events are received through supabase-kt realtime-kt.
 * 3. No OkHttp polling and no delay-based message refresh.
 * 4. The realtime flow is registered before channel.subscribe().
 * 5. Duplicate INSERT events are ignored by message id.
 *
 * IMPORTANT:
 * app module must include supabase-kt realtime-kt and a Ktor Android engine.
 */
class ChatActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorBg = Color.parseColor("#F4F7F6")
    private val colorTextMuted = Color.parseColor("#6B7280")
    private val colorDark = Color.parseColor("#111827")

    private var appointmentId = ""
    private var patientId = ""
    private var patientName = ""
    private var conversationId: String? = null
    private var mySenderId = ""
    private var myRole = "patient"

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var inputField: EditText
    private lateinit var sendBtn: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var topTitle: TextView

    private val messagesList = mutableListOf<JSONObject>()

    private var realtimeJob: Job? = null
    private var realtimeChannel: RealtimeChannel? = null

    /**
     * Dedicated Kotlin Supabase client only for Realtime.
     * Existing REST operations remain inside SupabaseClient.
     */
    private val realtimeSupabase by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Realtime)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appointmentId = intent.getStringExtra("appointment_id").orEmpty()
        patientId = intent.getStringExtra("patient_id")
            ?: SupabaseClient.getPatientId(this).orEmpty()
        patientName = intent.getStringExtra("patient_name")
            ?: SupabaseClient.getName(this)
            ?: "রোগী"

        if (appointmentId.isBlank()) {
            Toast.makeText(
                this,
                "appointment_id পাওয়া যায়নি",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        if (SupabaseClient.isAdmin(this)) {
            myRole = "doctor"
            mySenderId = SupabaseClient.getPhone(this) ?: "doctor"
        } else {
            myRole = "patient"
            mySenderId = patientId.ifBlank {
                SupabaseClient.getPatientId(this) ?: "unknown"
            }
        }

        buildUI()
        initConversation()
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(colorBg)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#16897A"), colorPrimaryDark)
            )
            setPadding(dp(16), dp(38), dp(16), dp(16))
        }

        val backBtn = TextView(this).apply {
            text = "‹"
            textSize = 28f
            setTextColor(Color.WHITE)
            setPadding(dp(4), 0, dp(12), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        topTitle = TextView(this).apply {
            text = patientName
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }

        statusText = TextView(this).apply {
            text = "চ্যাট খোলা হচ্ছে..."
            textSize = 11f
            setTextColor(Color.argb(200, 255, 255, 255))
        }

        titleCol.addView(topTitle)
        titleCol.addView(statusText)

        topBar.addView(backBtn)
        topBar.addView(titleCol)
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
            setPadding(dp(12), dp(12), dp(12), dp(12))
            clipToPadding = false
        }

        adapter = MessageAdapter(messagesList, mySenderId)
        recyclerView.adapter = adapter
        root.addView(recyclerView)

        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        inputField = EditText(this).apply {
            hint = "মেসেজ লিখুন..."
            setHintTextColor(colorTextMuted)
            setTextColor(colorDark)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(Color.parseColor("#F1F5F4"))
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(10)
            }
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        sendBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorPrimary)
            }
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            isClickable = true
            isFocusable = true
            setOnClickListener { sendMessage() }
        }

        val sendIcon = TextView(this).apply {
            text = ">"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }

        sendBtn.addView(sendIcon)
        inputBar.addView(inputField)
        inputBar.addView(sendBtn)
        root.addView(inputBar)

        setContentView(root)
    }

    private fun initConversation() {
        lifecycleScope.launch {
            try {
                statusText.text = "চ্যাট খোলা হচ্ছে..."

                val conv = SupabaseClient.getOrCreateConversation(
                    appointmentId,
                    patientId
                )

                conversationId = conv.optString("id").ifBlank { null }

                val convId = conversationId
                if (convId == null) {
                    throw IllegalStateException("conversation id পাওয়া যায়নি")
                }

                topTitle.text = if (myRole == "doctor") {
                    patientName
                } else {
                    "ডাক্তার"
                }

                loadInitialMessages()
                startChatRealtime(convId)

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

    /**
     * REST is used only once for the initial history.
     * After this, message changes come from Realtime.
     */
    private suspend fun loadInitialMessages() {
        val convId = conversationId ?: return
        val rows = SupabaseClient.getMessages(convId)

        messagesList.clear()

        for (i in 0 until rows.length()) {
            val obj = rows.optJSONObject(i) ?: continue
            addMessageIfMissing(obj)
        }

        adapter.notifyDataSetChanged()

        if (messagesList.isNotEmpty()) {
            recyclerView.scrollToPosition(messagesList.lastIndex)
        }
    }

    /**
     * Registers postgresChangeFlow BEFORE subscribe().
     *
     * We intentionally subscribe to the messages table without a Kotlin
     * filter DSL here. The conversation_id is checked in handleRealtimeAction().
     * This avoids the common "filter(...) invocation expected" compiler
     * problem caused by an incompatible realtime-kt DSL version.
     */
    private fun startChatRealtime(convId: String) {
        realtimeJob?.cancel()

        realtimeJob = lifecycleScope.launch {
            try {
                statusText.text = "Realtime সংযোগ হচ্ছে..."

                realtimeChannel?.let { oldChannel ->
                    try {
                        oldChannel.unsubscribe()
                    } catch (_: Exception) {
                    }

                    try {
                        realtimeSupabase.realtime.removeChannel(oldChannel)
                    } catch (_: Exception) {
                    }
                }

                val channel = realtimeSupabase.channel(
                    "chat_messages_$convId"
                )

                realtimeChannel = channel

                val changeFlow =
                    channel.postgresChangeFlow<PostgresAction>(
                        schema = "public"
                    ) {
                        table = "messages"
                    }

                /*
                 * The collector is started before subscribe().
                 * This is important because the postgres_changes
                 * configuration is sent when the channel joins.
                 */
                val collectorJob = launch {
                    changeFlow.collect { action ->
                        handleRealtimeAction(action)
                    }
                }

                channel.subscribe()

                statusText.text = "অনলাইন • Realtime"

                /*
                 * Keep this coroutine alive while the channel is active.
                 * The Activity lifecycle cancels it automatically.
                 */
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
                val obj = recordToJsonObject(action.record)

                if (obj.optString("conversation_id") == conversationId) {
                    addMessageIfMissing(obj)

                    runOnUiThread {
                        adapter.notifyDataSetChanged()
                        scrollToBottom()
                    }
                }
            }

            is PostgresAction.Update -> {
                val obj = recordToJsonObject(action.record)

                if (obj.optString("conversation_id") == conversationId) {
                    replaceMessage(obj)
                }
            }

            is PostgresAction.Delete -> {
                val obj = recordToJsonObject(action.oldRecord)

                if (obj.optString("conversation_id") == conversationId) {
                    removeMessage(obj)
                }
            }

            is PostgresAction.Select -> {
                // Initial SELECT-like event is not needed because
                // history was already loaded through REST.
            }
        }
    }

    /**
     * supabase-kt realtime records are decoded into a JSON-like map.
     * This conversion keeps the existing JSONObject-based adapter intact.
     */
    private fun recordToJsonObject(record: Any?): JSONObject {
        if (record is JSONObject) {
            return record
        }

        val result = JSONObject()

        when (record) {
            is Map<*, *> -> {
                for ((key, value) in record) {
                    if (key != null) {
                        result.put(key.toString(), value ?: JSONObject.NULL)
                    }
                }
            }

            else -> {
                /*
                 * Some supabase-kt versions expose the record as a
                 * serializable map-like object. Reflection is used only
                 * as a compatibility fallback; normal Map handling is
                 * preferred.
                 */
                try {
                    val entriesMethod = record?.javaClass?.methods?.firstOrNull {
                        it.name == "getEntries" && it.parameterTypes.isEmpty()
                    }

                    val entries = entriesMethod?.invoke(record)
                    if (entries is Iterable<*>) {
                        for (entry in entries) {
                            val keyMethod = entry?.javaClass?.methods?.firstOrNull {
                                it.name == "getKey" && it.parameterTypes.isEmpty()
                            }
                            val valueMethod = entry?.javaClass?.methods?.firstOrNull {
                                it.name == "getValue" && it.parameterTypes.isEmpty()
                            }

                            val key = keyMethod?.invoke(entry)?.toString()
                            if (!key.isNullOrBlank()) {
                                result.put(
                                    key,
                                    valueMethod?.invoke(entry) ?: JSONObject.NULL
                                )
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        return result
    }

    private fun addMessageIfMissing(obj: JSONObject) {
        val id = obj.optString("id", "")

        if (id.isNotBlank()) {
            val alreadyExists = messagesList.any {
                it.optString("id", "") == id
            }

            if (alreadyExists) {
                return
            }
        } else {
            /*
             * Fallback for an unusual schema without an id.
             * Avoid inserting an identical temporary/history row.
             */
            val sender = obj.optString("sender_id", "")
            val text = obj.optString("message", "")
            val createdAt = obj.optString("created_at", "")

            val duplicate = messagesList.any {
                it.optString("sender_id", "") == sender &&
                    it.optString("message", "") == text &&
                    it.optString("created_at", "") == createdAt
            }

            if (duplicate) {
                return
            }
        }

        messagesList.add(obj)

        messagesList.sortBy {
            it.optString("created_at", "")
        }

        runOnUiThread {
            adapter.notifyDataSetChanged()
        }
    }

    private fun replaceMessage(obj: JSONObject) {
        val id = obj.optString("id", "")
        if (id.isBlank()) return

        val index = messagesList.indexOfFirst {
            it.optString("id", "") == id
        }

        if (index >= 0) {
            messagesList[index] = obj

            runOnUiThread {
                adapter.notifyItemChanged(index)
            }
        } else {
            addMessageIfMissing(obj)
        }
    }

    private fun removeMessage(obj: JSONObject) {
        val id = obj.optString("id", "")
        if (id.isBlank()) return

        val index = messagesList.indexOfFirst {
            it.optString("id", "") == id
        }

        if (index >= 0) {
            messagesList.removeAt(index)

            runOnUiThread {
                adapter.notifyItemRemoved(index)
            }
        }
    }

    private fun scrollToBottom() {
        if (messagesList.isNotEmpty()) {
            recyclerView.scrollToPosition(messagesList.lastIndex)
        }
    }

    private fun sendMessage() {
        val text = inputField.text.toString().trim()
        if (text.isEmpty()) return

        val convId = conversationId
        if (convId.isNullOrBlank()) {
            Toast.makeText(
                this,
                "চ্যাট এখনো প্রস্তুত হয়নি",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        inputField.setText("")
        sendBtn.isEnabled = false

        lifecycleScope.launch {
            try {
                /*
                 * Do not add an optimistic duplicate here.
                 * The INSERT event from Supabase Realtime becomes
                 * the single source for adding the sent message.
                 */
                SupabaseClient.sendMessage(
                    convId,
                    mySenderId,
                    myRole,
                    text
                )
            } catch (e: Exception) {
                inputField.setText(text)

                Toast.makeText(
                    this@ChatActivity,
                    "মেসেজ পাঠানো যায়নি: ${e.message ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                sendBtn.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        realtimeJob?.cancel()
        realtimeJob = null

        realtimeChannel?.let { channel ->
            try {
                channel.unsubscribe()
            } catch (_: Exception) {
            }

            try {
                realtimeSupabase.realtime.removeChannel(channel)
            } catch (_: Exception) {
            }
        }

        realtimeChannel = null

        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    inner class MessageAdapter(
        private val list: List<JSONObject>,
        private val myId: String
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            val sender = list[position].optString("sender_id", "")
            return if (sender == myId) 1 else 0
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): RecyclerView.ViewHolder {

            val bubble = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }

            val textView = TextView(parent.context).apply {
                textSize = 14f
                setPadding(dp(14), dp(10), dp(14), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    maxWidth = (
                        parent.context.resources.displayMetrics.widthPixels * 0.75
                    ).toInt()
                }
            }

            bubble.addView(textView)

            return object : RecyclerView.ViewHolder(bubble) {}
        }

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int
        ) {
            val obj = list[position]
            val message = obj.optString("message", "")
            val isMe = getItemViewType(position) == 1

            val bubbleLayout = holder.itemView as LinearLayout
            val tv = bubbleLayout.getChildAt(0) as TextView

            tv.text = message

            val tvParams = tv.layoutParams as LinearLayout.LayoutParams

            if (isMe) {
                bubbleLayout.gravity = Gravity.END
                tv.setTextColor(Color.WHITE)

                tv.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(
                        dp(18).toFloat(),
                        dp(18).toFloat(),
                        dp(4).toFloat(),
                        dp(18).toFloat(),
                        dp(18).toFloat(),
                        dp(18).toFloat(),
                        dp(18).toFloat(),
                        dp(18).toFloat()
                    )
                    setColor(colorPrimary)
                }

                tvParams.gravity = Gravity.END
            } else {
                bubbleLayout.gravity = Gravity.START
                tv.setTextColor(colorDark)

                tv.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(
                        dp(18).toFloat(),
                        dp(18).toFloat(),
                        dp(18).toFloat(),
                        dp(18).toFloat(),
                        dp(4).toFloat(),
                        dp(18).toFloat(),
                        dp(18).toFloat(),
                        dp(18).toFloat()
                    )
                    setColor(Color.WHITE)
                }

                tvParams.gravity = Gravity.START
            }

            tv.layoutParams = tvParams
        }

        override fun getItemCount(): Int = list.size
    }

    companion object {
        private const val SUPABASE_URL =
            "https://azbleibkgerzaqbrrydl.supabase.co"

        private const val SUPABASE_ANON_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF6YmxlaWJrZ2VyemFxYnJyeWRsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODkxNzExNjYsImV4cCI6MjEwNDc0NzE2Nn0.6Q6PMcRJHXFIRPUZZf9lOjoTmq77_wbCoKc8tGkVF2o"
    }
}
