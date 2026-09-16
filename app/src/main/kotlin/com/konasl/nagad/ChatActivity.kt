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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray

class ChatActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorBg = Color.parseColor("#F4F7F6")
    private val colorCard = Color.WHITE
    private val colorTextMuted = Color.parseColor("#6B7280")
    private val colorDark = Color.parseColor("#111827")

    private var appointmentId: String = ""
    private var patientId: String = ""
    private var patientName: String = ""
    private var conversationId: String? = null
    private var mySenderId: String = ""
    private var myRole: String = "patient"

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var inputField: EditText
    private lateinit var sendBtn: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var topTitle: TextView

    private var pollingJob: Job? = null
    private val messagesList = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appointmentId = intent.getStringExtra("appointment_id") ?: ""
        patientId = intent.getStringExtra("patient_id") ?: SupabaseClient.getPatientId(this) ?: ""
        patientName = intent.getStringExtra("patient_name") ?: SupabaseClient.getName(this) ?: "রোগী"

        if (appointmentId.isEmpty()) {
            Toast.makeText(this, "appointment_id পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // আমার পরিচয় ঠিক করো
        if (SupabaseClient.isAdmin(this)) {
            myRole = "doctor"
            mySenderId = SupabaseClient.getPhone(this) ?: "doctor"
        } else {
            myRole = "patient"
            mySenderId = patientId.ifEmpty { SupabaseClient.getPatientId(this) ?: "unknown" }
        }

        buildUI()
        initConversation()
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(colorBg)
        }

        // Top Bar
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
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topTitle = TextView(this).apply {
            text = patientName
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        statusText = TextView(this).apply {
            text = "কানেক্ট হচ্ছে..."
            textSize = 11f
            setTextColor(Color.argb(200, 255, 255, 255))
        }
        titleCol.addView(topTitle)
        titleCol.addView(statusText)
        topBar.addView(backBtn)
        topBar.addView(titleCol)
        root.addView(topBar)

        // RecyclerView
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply { stackFromEnd = true }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            clipToPadding = false
        }
        adapter = MessageAdapter(messagesList, mySenderId)
        recyclerView.adapter = adapter
        root.addView(recyclerView)

        // Input Area
        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(10)
            }
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
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
            text = "➤"
            textSize = 18f
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
                // এই appointment_id এর জন্য conversation আছে কিনা দেখো, না থাকলে বানাও
                val conv = SupabaseClient.getOrCreateConversation(appointmentId, patientId)
                conversationId = conv.getString("id")
                statusText.text = "অনলাইন • প্রাইভেট চ্যাট"
                topTitle.text = if (myRole == "doctor") patientName else "ডাক্তার"

                // প্রথমবার মেসেজ লোড
                loadMessagesOnce()
                // Polling শুরু - Realtime এর মতো কাজ করবে
                startPolling()

            } catch (e: Exception) {
                statusText.text = "কানেক্ট ব্যর্থ"
                Toast.makeText(this@ChatActivity, "চ্যাট খোলা যায়নি: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadMessagesOnce() {
        val convId = conversationId ?: return
        try {
            val rows = SupabaseClient.getMessages(convId)
            messagesList.clear()
            for (i in 0 until rows.length()) {
                messagesList.add(rows.getJSONObject(i))
            }
            runOnUiThread {
                adapter.notifyDataSetChanged()
                if (messagesList.isNotEmpty()) recyclerView.scrollToPosition(messagesList.size - 1)
            }
        } catch (e: Exception) {
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                delay(2000) // 2 সেকেন্ড পর পর নতুন মেসেজ চেক - এটাই তোমার Realtime এর সহজ ভার্সন
                try {
                    val convId = conversationId ?: continue
                    val rows = SupabaseClient.getMessages(convId)
                    // শুধু নতুন মেসেজ থাকলে আপডেট করো
                    if (rows.length() != messagesList.size) {
                        messagesList.clear()
                        for (i in 0 until rows.length()) {
                            messagesList.add(rows.getJSONObject(i))
                        }
                        runOnUiThread {
                            adapter.notifyDataSetChanged()
                            recyclerView.scrollToPosition(messagesList.size - 1)
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun sendMessage() {
        val text = inputField.text.toString().trim()
        if (text.isEmpty()) return
        val convId = conversationId
        if (convId == null) {
            Toast.makeText(this, "চ্যাট এখনো রেডি হয়নি", Toast.LENGTH_SHORT).show()
            return
        }
        inputField.setText("")
        // Optimistic UI - সাথে সাথে দেখাও
        val tempObj = JSONObject().apply {
            put("sender_id", mySenderId)
            put("message", text)
            put("created_at", "")
        }
        messagesList.add(tempObj)
        adapter.notifyItemInserted(messagesList.size - 1)
        recyclerView.scrollToPosition(messagesList.size - 1)

        lifecycleScope.launch {
            try {
                SupabaseClient.sendMessage(convId, mySenderId, myRole, text)
                // পাঠানোর পর সাথে সাথে রিফ্রেশ
                loadMessagesOnce()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "পাঠানো যায়নি", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // Adapter - প্রাইভেট চ্যাটের জন্য 2 টাইপ
    inner class MessageAdapter(
        private val list: List<JSONObject>,
        private val myId: String
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            val sender = list[position].optString("sender_id", "")
            return if (sender == myId) 1 else 0 // 1 = sent, 0 = received
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val bubble = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            val textView = TextView(parent.context).apply {
                textSize = 14f
                setPadding(dp(14), dp(10), dp(14), dp(10))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    maxWidth = (parent.context.resources.displayMetrics.widthPixels * 0.75).toInt()
                }
            }
            bubble.addView(textView)
            return object : RecyclerView.ViewHolder(bubble) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val obj = list[position]
            val message = obj.optString("message", "")
            val isMe = getItemViewType(position) == 1

            val bubbleLayout = holder.itemView as LinearLayout
            val tv = bubbleLayout.getChildAt(0) as TextView
            tv.text = message

            val params = bubbleLayout.layoutParams as LinearLayout.LayoutParams
            val tvParams = tv.layoutParams as LinearLayout.LayoutParams

            if (isMe) {
                bubbleLayout.gravity = Gravity.END
                tv.setTextColor(Color.WHITE)
                tv.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(dp(18).toFloat(), dp(18).toFloat(), dp(4).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat())
                    setColor(colorPrimary)
                }
                tvParams.gravity = Gravity.END
            } else {
                bubbleLayout.gravity = Gravity.START
                tv.setTextColor(colorDark)
                tv.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(4).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat())
                    setColor(Color.WHITE)
                }
                tvParams.gravity = Gravity.START
            }
            tv.layoutParams = tvParams
        }

        override fun getItemCount(): Int = list.size
        private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    }
}
