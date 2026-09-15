package com.konasl.nagad

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.graphics.Outline
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * ============================================================================
 * WaitingActivity
 * ============================================================================
 * HomeActivity তে "আসন্ন অ্যাপয়েন্টমেন্ট" কার্ডে (যখন status == confirmed &&
 * slot_open == true থাকে) ক্লিক করলে এই একটিভিটি ওপেন হয়।
 *
 * এই একটিভিটিতে যা যা থাকে:
 * - রোগীর অ্যাপয়েন্টমেন্টের তথ্য (নাম, ফোন, তারিখ, সময়, কারণ)
 * - মাঝখানে একটি লাইভ কাউন্টডাউন — অ্যাপয়েন্টমেন্টের নির্ধারিত সময় পর্যন্ত
 *   ঠিক কত সময় বাকি (দিন:ঘণ্টা:মিনিট:সেকেন্ড), প্রতি ১ সেকেন্ডে আপডেট হয়
 * - প্রতি ১০ সেকেন্ডে Supabase থেকে সার্ভার-সাইড স্ট্যাটাস চেক করা হয়;
 *   অ্যাপয়েন্টমেন্ট cancelled/completed হয়ে গেলে বা স্লট আবার বন্ধ হয়ে
 *   গেলে সাথে সাথে ইউজারকে জানানো হয়
 *
 * Intent extras (HomeActivity থেকে পাঠানো হয়):
 *   appointment_id, patient_name, phone, preferred_date, preferred_time, reason
 * ============================================================================
 */
class WaitingActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryLight = Color.parseColor("#16897A")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorAccent = Color.parseColor("#F59E0B")
    private val colorBg = Color.parseColor("#F4F7F6")
    private val colorTextMuted = Color.parseColor("#6B7280")
    private val colorDark = Color.parseColor("#111827")
    private val colorCard = Color.WHITE
    private val colorFieldBorder = Color.parseColor("#E7ECEA")
    private val colorDanger = Color.parseColor("#DC2626")
    private val colorSuccess = Color.parseColor("#16A34A")

    // সিরিয়ালের জন্য কল করার নাম্বার (HomeActivity এর মতোই)
    private val doctorSerialPhone = "01660029028"

    private var appointmentId: String = ""
    private var patientName: String = ""
    private var patientPhone: String = ""
    private var preferredDate: String = ""
    private var preferredTime: String = ""
    private var reason: String = ""

    private var targetMillis: Long? = null

    private lateinit var countdownBigText: TextView
    private lateinit var countdownLabelText: TextView
    private lateinit var statusBadge: TextView
    private lateinit var statusMessageText: TextView
    private lateinit var infoCard: LinearLayout

    // প্রতি সেকেন্ডে কাউন্টডাউন আপডেট
    private val tickHandler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    // প্রতি ১০ সেকেন্ডে সার্ভার স্ট্যাটাস পোল
    private val pollHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private val POLL_INTERVAL_MS = 10_000L

    private var alreadyFinishedByStatus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appointmentId = intent.getStringExtra("appointment_id") ?: ""
        patientName = intent.getStringExtra("patient_name") ?: (SupabaseClient.getName(this) ?: "")
        patientPhone = intent.getStringExtra("phone") ?: (SupabaseClient.getPhone(this) ?: "")
        preferredDate = intent.getStringExtra("preferred_date") ?: ""
        preferredTime = intent.getStringExtra("preferred_time") ?: ""
        reason = intent.getStringExtra("reason") ?: ""

        targetMillis = computeTargetMillis(preferredDate, preferredTime)

        buildUi()
        startCountdown()
        startStatusPolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdown()
        stopStatusPolling()
    }

    // ------------------------------------------------------------------
    // UI বানানো
    // ------------------------------------------------------------------
    private fun buildUi() {
        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F8FBFA"), colorBg)
            )
        }

        val scroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(40))
        }

        // ---------------- হেডার ----------------
        val headerContainer = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark)
            ).apply {
                setCornerRadii(floatArrayOf(0f, 0f, 0f, 0f, dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat()))
            }
            clipToPadding = false
        }
        headerContainer.addView(glowCircle(dp(160), Color.argb(24, 255, 255, 255), Gravity.TOP or Gravity.END, dp(-55), dp(-50)))

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(26))
        }
        val backBtn = TextView(this).apply {
            text = "←"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = roundedBg(Color.argb(46, 255, 255, 255), 30f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        val headerTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) }
        }
        headerTextCol.addView(text("সিরিয়ালের অপেক্ষা", 18f, Typeface.BOLD, Color.WHITE, Gravity.START))
        headerTextCol.addView(text("আপনার অ্যাপয়েন্টমেন্টের সময় ঘনিয়ে আসছে", 12f, Typeface.NORMAL, Color.argb(210, 255, 255, 255), Gravity.START).apply {
            setPadding(0, dp(4), 0, 0)
        })
        headerRow.addView(backBtn)
        headerRow.addView(headerTextCol)
        headerContainer.addView(headerRow)

        // ---------------- কাউন্টডাউন কার্ড ----------------
        val countdownCardWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(-26), dp(18), 0)
            }
        }
        val countdownCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark)
            ).apply { cornerRadius = dp(24).toFloat() }
            setPadding(dp(22), dp(30), dp(22), dp(28))
            elevation = dp(6).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
                }
            }
            clipToOutline = true
        }

        countdownCard.addView(ImageView(this).apply {
            setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.CLOCK, Color.WHITE, dp(26)))
            background = roundedBg(Color.argb(46, 255, 255, 255), 16f)
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            setPadding(dp(13), dp(13), dp(13), dp(13))
        })

        countdownLabelText = text("অ্যাপয়েন্টমেন্ট পর্যন্ত বাকি সময়", 12.5f, Typeface.BOLD, Color.argb(220, 255, 255, 255), Gravity.CENTER).apply {
            setPadding(0, dp(16), 0, 0)
        }
        countdownCard.addView(countdownLabelText)

        countdownBigText = TextView(this).apply {
            text = "--:--:--"
            textSize = 40f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
            letterSpacing = 0.02f
        }
        countdownCard.addView(countdownBigText)

        countdownCard.addView(text("$preferredDate • $preferredTime", 13f, Typeface.BOLD, Color.argb(230, 255, 255, 255), Gravity.CENTER).apply {
            setPadding(0, dp(14), 0, 0)
        })

        countdownCardWrap.addView(countdownCard)

        // ---------------- স্ট্যাটাস ব্যানার ----------------
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(colorCard, 18f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(18), dp(18), 0)
            }
            elevation = dp(2).toFloat()
        }
        val statusIconWrap = ImageView(this).apply {
            setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.CHECK, Color.WHITE, dp(16)))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(colorSuccess) }
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            setPadding(dp(9), dp(9), dp(9), dp(9))
        }
        val statusCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
        }
        statusMessageText = text("আপনার সিরিয়াল ওপেন করা হয়েছে, দয়া করে প্রস্তুত থাকুন", 12.5f, Typeface.BOLD, colorDark, Gravity.START)
        statusCol.addView(statusMessageText)
        statusCol.addView(text("প্রতি ১০ সেকেন্ডে স্ট্যাটাস অটো-আপডেট হয়", 10.5f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
            setPadding(0, dp(3), 0, 0)
        })
        statusBadge = text("লাইভ", 10f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
            background = roundedBg(colorSuccess, 30f)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        statusRow.addView(statusIconWrap)
        statusRow.addView(statusCol)
        statusRow.addView(statusBadge)

        // ---------------- অ্যাপয়েন্টমেন্ট তথ্য কার্ড ----------------
        infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(colorCard, 20f)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(18), dp(18), 0)
            }
            elevation = dp(2).toFloat()
        }
        infoCard.addView(text("অ্যাপয়েন্টমেন্টের বিবরণ", 14f, Typeface.BOLD, colorDark, Gravity.START))
        infoCard.addView(rowDivider(topMargin = dp(12), bottomMargin = dp(4)))
        infoCard.addView(infoRow("রোগীর নাম", patientName.ifEmpty { "—" }))
        infoCard.addView(infoRow("ফোন নাম্বার", patientPhone.ifEmpty { "—" }))
        infoCard.addView(infoRow("তারিখ", preferredDate.ifEmpty { "—" }))
        infoCard.addView(infoRow("সময়", preferredTime.ifEmpty { "—" }))
        infoCard.addView(infoRow("সমস্যার বিবরণ", reason.ifEmpty { "সাধারণ পরামর্শ" }))

        // ---------------- সিরিয়ালের জন্য কল করুন বাটন ----------------
        val callRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark)
            ).apply { cornerRadius = dp(18).toFloat() }
            setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(18), dp(18), 0)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$doctorSerialPhone")))
            }
        }
        callRow.addView(ImageView(this).apply {
            setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.PHONE, Color.WHITE, dp(18)))
            background = roundedBg(Color.argb(46, 255, 255, 255), 12f)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        })
        callRow.addView(text("চেম্বারে কল করে নিশ্চিত হোন", 13f, Typeface.BOLD, Color.WHITE, Gravity.START).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
        })
        callRow.addView(ImageView(this).apply {
            setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.ARROW_RIGHT, Color.WHITE, dp(16)))
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
        })

        // ---------------- নোট ----------------
        val noteText = text(
            "দ্রষ্টব্য: কাউন্টডাউন আপনার বুক করা সময় অনুযায়ী দেখানো হচ্ছে। ক্লিনিকের প্রকৃত সিরিয়াল কিছুটা এগিয়ে-পিছিয়ে যেতে পারে, তাই স্ট্যাটাস দেখে প্রস্তুত থাকুন।",
            11f, Typeface.NORMAL, colorTextMuted, Gravity.START
        ).apply {
            setPadding(dp(18), dp(16), dp(18), 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        }

        content.addView(headerContainer)
        content.addView(countdownCardWrap)
        content.addView(statusRow)
        content.addView(infoCard)
        content.addView(callRow)
        content.addView(noteText)

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun infoRow(label: String, value: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(9), 0, dp(9))
            addView(text(label, 12f, Typeface.BOLD, colorTextMuted, Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(text(value, 12.5f, Typeface.NORMAL, colorDark, Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun rowDivider(topMargin: Int = 0, bottomMargin: Int = 0): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            this.topMargin = topMargin; this.bottomMargin = bottomMargin
        }
        setBackgroundColor(colorFieldBorder)
    }

    private fun glowCircle(size: Int, color: Int, gravityVal: Int, marginTopOrBottom: Int, marginSideVal: Int): View {
        return View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = gravityVal
                if (gravityVal and Gravity.TOP == Gravity.TOP) topMargin = marginTopOrBottom else bottomMargin = marginTopOrBottom
                if (gravityVal and Gravity.END == Gravity.END) rightMargin = marginSideVal else leftMargin = marginSideVal
            }
        }
    }

    // ------------------------------------------------------------------
    // কাউন্টডাউন লজিক
    // ------------------------------------------------------------------

    /** preferred_date ও preferred_time স্ট্রিং থেকে টার্গেট টাইমস্ট্যাম্প বের করার চেষ্টা করে; সম্ভব না হলে null */
    private fun computeTargetMillis(dateStr: String, timeStr: String): Long? {
        if (dateStr.isBlank()) return null
        val datePatterns = listOf("yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "MM/dd/yyyy", "yyyy/MM/dd")
        val timePatterns = listOf("HH:mm", "hh:mm a", "h:mm a", "HH:mm:ss", "hh:mm:ss a")
        val cleanTime = timeStr.trim().ifEmpty { "00:00" }
        for (dp in datePatterns) {
            for (tp in timePatterns) {
                try {
                    val sdf = SimpleDateFormat("$dp $tp", Locale.US)
                    sdf.isLenient = false
                    val parsed = sdf.parse("${dateStr.trim()} $cleanTime")
                    if (parsed != null) return parsed.time
                } catch (e: Exception) {
                    // এই ফরম্যাটে মিলেনি, পরের ফরম্যাট চেষ্টা করা হবে
                }
            }
        }
        return null
    }

    private fun startCountdown() {
        stopCountdown()
        val runnable = object : Runnable {
            override fun run() {
                updateCountdownText()
                tickHandler.postDelayed(this, 1000)
            }
        }
        tickRunnable = runnable
        tickHandler.post(runnable)
    }

    private fun stopCountdown() {
        tickRunnable?.let { tickHandler.removeCallbacks(it) }
        tickRunnable = null
    }

    private fun updateCountdownText() {
        val target = targetMillis
        if (target == null) {
            countdownBigText.text = "--:--:--"
            countdownLabelText.text = "সময় হিসাব করা যাচ্ছে না"
            return
        }
        val diff = target - System.currentTimeMillis()
        if (diff <= 0) {
            countdownBigText.text = "০০:০০:০০"
            countdownLabelText.text = "আপনার অ্যাপয়েন্টমেন্টের সময় হয়ে গেছে"
            return
        }
        val totalSeconds = diff / 1000
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        countdownLabelText.text = "অ্যাপয়েন্টমেন্ট পর্যন্ত বাকি সময়"
        countdownBigText.text = if (days > 0) {
            String.format(Locale.US, "%dদিন %02d:%02d:%02d", days, hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    // ------------------------------------------------------------------
    // সার্ভার-সাইড স্ট্যাটাস পোলিং (প্রতি ১০ সেকেন্ড)
    // ------------------------------------------------------------------
    private fun startStatusPolling() {
        stopStatusPolling()
        if (appointmentId.isBlank()) return
        val runnable = object : Runnable {
            override fun run() {
                checkAppointmentStatus()
                pollHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
        pollRunnable = runnable
        pollHandler.postDelayed(runnable, POLL_INTERVAL_MS)
    }

    private fun stopStatusPolling() {
        pollRunnable?.let { pollHandler.removeCallbacks(it) }
        pollRunnable = null
    }

    private fun checkAppointmentStatus() {
        if (alreadyFinishedByStatus) return
        lifecycleScope.launch {
            val result = SupabaseClient.getAppointmentById(appointmentId)
            result.onSuccess { appt ->
                if (appt == null) return@onSuccess
                val status = appt.optString("status", "")
                val slotOpen = appt.optBoolean("slot_open", false)

                when {
                    status == "cancelled" -> {
                        alreadyFinishedByStatus = true
                        showFinalStatus(
                            message = "দুঃখিত, আপনার অ্যাপয়েন্টমেন্টটি বাতিল করা হয়েছে",
                            color = colorDanger,
                            badgeText = "বাতিল"
                        )
                    }
                    status == "completed" -> {
                        alreadyFinishedByStatus = true
                        showFinalStatus(
                            message = "আপনার পরামর্শ সম্পন্ন হয়েছে, ধন্যবাদ",
                            color = colorPrimary,
                            badgeText = "সম্পন্ন"
                        )
                    }
                    status == "confirmed" && slotOpen -> {
                        statusMessageText.text = "আপনার সিরিয়াল ওপেন করা হয়েছে, দয়া করে প্রস্তুত থাকুন"
                        statusBadge.text = "লাইভ"
                        statusBadge.background = roundedBg(colorSuccess, 30f)
                    }
                    status == "confirmed" && !slotOpen -> {
                        statusMessageText.text = "কনফার্মড — সিরিয়াল সাময়িকভাবে বন্ধ আছে, একটু অপেক্ষা করুন"
                        statusBadge.text = "অপেক্ষমান"
                        statusBadge.background = roundedBg(colorAccent, 30f)
                    }
                    else -> {
                        statusMessageText.text = "আপনার অ্যাপয়েন্টমেন্ট এখনো রিভিউ করা হচ্ছে"
                        statusBadge.text = "পেন্ডিং"
                        statusBadge.background = roundedBg(colorAccent, 30f)
                    }
                }
            }
            // ব্যর্থ হলে চুপচাপ স্কিপ, পরের পোলে আবার চেষ্টা হবে
        }
    }

    private fun showFinalStatus(message: String, color: Int, badgeText: String) {
        stopStatusPolling()
        statusMessageText.text = message
        statusBadge.text = badgeText
        statusBadge.background = roundedBg(color, 30f)
        countdownLabelText.text = message
        countdownBigText.text = "--:--:--"
        stopCountdown()
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------
    private fun text(t: String, sizeSp: Float, style: Int, color: Int, gravity: Int): TextView = TextView(this).apply {
        text = t; textSize = sizeSp; setTypeface(null, style); setTextColor(color); this.gravity = gravity
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(color)
    }
}
