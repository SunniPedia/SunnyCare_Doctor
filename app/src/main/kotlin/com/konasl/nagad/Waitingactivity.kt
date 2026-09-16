package com.konasl.nagad

import android.app.Dialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
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
 * - মাঝখানে একটি লাইভ কাউন্টডাউন — দিন/ঘণ্টা/মিনিট/সেকেন্ড আলাদা আলাদা বক্সে
 *   দেখানো হয়, প্রতি ১ সেকেন্ডে আপডেট হয়
 * - প্রতি ১০ সেকেন্ডে Supabase থেকে সার্ভার-সাইড স্ট্যাটাস চেক করা হয়;
 *   অ্যাপয়েন্টমেন্ট cancelled/completed হয়ে গেলে বা স্লট আবার বন্ধ হয়ে
 *   গেলে সাথে সাথে ইউজারকে জানানো হয়
 * - ডাক্তারের সাথে যোগাযোগের জন্য Chat / Video Call / Audio Call — তিনটা
 *   ভেক্টর-আইকন বাটন। কাউন্টডাউন শেষ না হওয়া পর্যন্ত এগুলো "লকড" অবস্থায়
 *   থাকে (ধূসর, লক-আইকন সহ); লকড অবস্থায় ট্যাপ করলে একটা কাস্টম ডায়ালগ
 *   দেখিয়ে জানানো হয় যে এখনো সময় হয়নি এবং বাকি কতটুকু সময় আছে। কাউন্টডাউন
 *   শেষ হওয়ার সাথে সাথেই বাটনগুলো স্বয়ংক্রিয়ভাবে সক্রিয় (রঙিন) হয়ে যায় এবং
 *   ট্যাপ করলে সরাসরি ChatActivity / VideocallActivity / AudiocallActivity
 *   Intent দিয়ে ওপেন হয়।
 * - সম্পূর্ণ UI কোনো ইমুজি বা ফন্ট-ক্যারেক্টার ছাড়াই, Canvas/Paint দিয়ে আঁকা
 *   ভেক্টর আইকন দিয়ে তৈরি।
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
    private val colorInfo = Color.parseColor("#2563EB")
    private val colorLockedBg = Color.parseColor("#E5E7EB")

    // সিরিয়ালের জন্য কল করার নাম্বার (HomeActivity এর মতোই)
    private val doctorSerialPhone = "01660029028"

    private var appointmentId: String = ""
    private var patientName: String = ""
    private var patientPhone: String = ""
    private var preferredDate: String = ""
    private var preferredTime: String = ""
    private var reason: String = ""

    private var targetMillis: Long? = null

    // নতুন — কাউন্টডাউন শেষ হয়েছে কিনা (এর ওপর ভিত্তি করেই Chat/Video/Audio বাটন আনলক হয়)
    private var isCountdownFinished = false

    private lateinit var countdownLabelText: TextView
    private lateinit var statusBadge: TextView
    private lateinit var statusMessageText: TextView
    private lateinit var infoCard: LinearLayout

    // কাউন্টডাউন ইউনিট বক্সগুলোর রেফারেন্স (দিন/ঘণ্টা/মিনিট/সেকেন্ড)
    private lateinit var daysBox: LinearLayout
    private lateinit var daysColon: TextView
    private lateinit var daysValueText: TextView
    private lateinit var hoursValueText: TextView
    private lateinit var minutesValueText: TextView
    private lateinit var secondsValueText: TextView

    // যোগাযোগ সেকশন
    private lateinit var commSubtitle: TextView
    private val commButtons = mutableListOf<CommButtonViews>()

    private data class CommButtonViews(
        val circleBg: GradientDrawable,
        val iconDrawable: Drawable,
        val label: TextView,
        val lockBadge: ImageView,
        val displayColor: Int,
        val featureLabel: String,
        val targetActivity: Class<*>
    )

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
        updateCommunicationButtonsUi()
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
        // ইমুজি/টেক্সট অ্যারো ("←") নয় — সম্পূর্ণ Canvas-ভেক্টর ব্যাক আইকন
        val backBtn = ImageView(this).apply {
            setImageDrawable(BackArrowDrawable(Color.WHITE, dp(2).toFloat()))
            background = roundedBg(Color.argb(46, 255, 255, 255), 30f)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            val pad = dp(10)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            contentDescription = "পেছনে যান"
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

        // নতুন — একক বড় টেক্সটের বদলে দিন/ঘণ্টা/মিনিট/সেকেন্ড আলাদা আলাদা "বক্সে" দেখানো হয়,
        // দেখতে অনেক বেশি স্পষ্ট ও প্রফেশনাল
        val unitsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        val (daysBoxView, daysValueView) = timeUnitBox("দিন")
        daysBox = daysBoxView
        daysValueText = daysValueView
        daysBox.visibility = View.GONE
        daysColon = colonSeparator().apply { visibility = View.GONE }

        val (hoursBoxView, hoursValueView) = timeUnitBox("ঘণ্টা")
        hoursValueText = hoursValueView
        val (minutesBoxView, minutesValueView) = timeUnitBox("মিনিট")
        minutesValueText = minutesValueView
        val (secondsBoxView, secondsValueView) = timeUnitBox("সেকেন্ড")
        secondsValueText = secondsValueView

        unitsRow.addView(daysBox)
        unitsRow.addView(daysColon)
        unitsRow.addView(hoursBoxView)
        unitsRow.addView(colonSeparator())
        unitsRow.addView(minutesBoxView)
        unitsRow.addView(colonSeparator())
        unitsRow.addView(secondsBoxView)
        countdownCard.addView(unitsRow)

        countdownCard.addView(text("$preferredDate • $preferredTime", 13f, Typeface.BOLD, Color.argb(230, 255, 255, 255), Gravity.CENTER).apply {
            setPadding(0, dp(18), 0, 0)
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

        // ---------------- নতুন — ডাক্তারের সাথে যোগাযোগ (Chat/Video/Audio) ----------------
        val commCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(colorCard, 20f)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(18), dp(18), 0)
            }
            elevation = dp(2).toFloat()
        }
        commCard.addView(text("ডাক্তারের সাথে যোগাযোগ করুন", 14f, Typeface.BOLD, colorDark, Gravity.START))
        commSubtitle = text("অ্যাপয়েন্টমেন্টের সময় হলে বাটনগুলো স্বয়ংক্রিয়ভাবে চালু হয়ে যাবে", 11f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
            setPadding(0, dp(4), 0, dp(16))
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        commCard.addView(commSubtitle)

        val commRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        commRow.addView(buildCommActionCard("চ্যাট", ChatIconDrawable(Color.WHITE, dp(1.7f).toFloat()), colorInfo, "চ্যাট", ChatActivity::class.java))
        commRow.addView(space(dp(10)))
        commRow.addView(buildCommActionCard("ভিডিও কল", VideoCallIconDrawable(Color.WHITE), colorSuccess, "ভিডিও কল", VideocallActivity::class.java))
        commRow.addView(space(dp(10)))
        commRow.addView(buildCommActionCard("অডিও কল", AudioCallIconDrawable(Color.WHITE), colorAccent, "অডিও কল", AudiocallActivity::class.java))
        commCard.addView(commRow)

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
        content.addView(commCard)
        content.addView(infoCard)
        content.addView(callRow)
        content.addView(noteText)

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    /** কাউন্টডাউনের একটা একক ইউনিট (যেমন "ঘণ্টা") — উপরে সংখ্যা, নিচে লেবেল */
    private fun timeUnitBox(labelText: String): Pair<LinearLayout, TextView> {
        val valueText = TextView(this).apply {
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBg(Color.argb(40, 255, 255, 255), 12f)
            minWidth = dp(50)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            text = "00"
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(valueText)
            addView(text(labelText, 10f, Typeface.BOLD, Color.argb(210, 255, 255, 255), Gravity.CENTER).apply {
                setPadding(0, dp(6), 0, 0)
            })
        }
        return box to valueText
    }

    private fun colonSeparator(): TextView = TextView(this).apply {
        text = ":"
        textSize = 22f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.argb(200, 255, 255, 255))
        gravity = Gravity.CENTER
        setPadding(dp(4), 0, dp(4), 0)
    }

    /** Chat/Video/Audio বাটনগুলোর জন্য একটা আইকন-সার্কেল + লেবেল কার্ড তৈরি করে, লক-স্টেট সহ */
    private fun buildCommActionCard(
        label: String,
        icon: Drawable,
        displayColor: Int,
        featureLabel: String,
        targetActivity: Class<*>
    ): LinearLayout {
        val circleBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorLockedBg)
        }
        val circleWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
            background = circleBg
        }
        circleWrap.addView(ImageView(this).apply {
            setImageDrawable(icon)
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(24)).apply { gravity = Gravity.CENTER }
        })
        val lockBadge = ImageView(this).apply {
            setImageDrawable(LockIconDrawable(colorTextMuted, dp(1.4f).toFloat()))
            background = roundedBg(Color.WHITE, 30f)
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20)).apply { gravity = Gravity.TOP or Gravity.END }
            val pad = dp(4)
            setPadding(pad, pad, pad, pad)
            elevation = dp(2).toFloat()
        }
        circleWrap.addView(lockBadge)

        val labelView = text(label, 11.5f, Typeface.BOLD, colorTextMuted, Gravity.CENTER).apply {
            setPadding(0, dp(8), 0, 0)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            isFocusable = true
            addView(circleWrap)
            addView(labelView)
            setOnClickListener { onCommButtonClicked(targetActivity, featureLabel) }
        }

        commButtons.add(CommButtonViews(circleBg, icon, labelView, lockBadge, displayColor, featureLabel, targetActivity))
        return root
    }

    /**
     * Chat/Video/Audio বাটনে ক্লিক করলে এখানে আসে —
     * কাউন্টডাউন শেষ হয়ে থাকলে সরাসরি সংশ্লিষ্ট একটিভিটি Intent দিয়ে ওপেন হয়,
     * নাহলে একটা কাস্টম ডায়ালগ দেখিয়ে বাকি সময় জানিয়ে দেওয়া হয়।
     */
    private fun onCommButtonClicked(targetActivity: Class<*>, featureLabel: String) {
        if (isCountdownFinished) {
            startActivity(Intent(this, targetActivity).apply {
                putExtra("appointment_id", appointmentId)
                putExtra("patient_id", SupabaseClient.getPatientId(this@WaitingActivity))
                putExtra("patient_name", patientName)
                putExtra("patient_phone", patientPhone)
                putExtra("preferred_date", preferredDate)
                putExtra("preferred_time", preferredTime)
                putExtra("reason", reason)
            })
        } else {
            showNotYetReadyDialog(featureLabel)
        }
    }

    /** কাউন্টডাউন শেষ হওয়ার আগে Chat/Video/Audio বাটনে ক্লিক করলে দেখানো কাস্টম ডায়ালগ */
    private fun showNotYetReadyDialog(featureLabel: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(Color.WHITE, 22f)
            setPadding(dp(24), dp(28), dp(24), dp(22))
        }
        card.addView(ImageView(this).apply {
            setImageDrawable(LockIconDrawable(Color.WHITE, dp(2f)))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorPrimaryLight, colorPrimaryDark)
            ).apply { shape = GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            setPadding(dp(15), dp(15), dp(15), dp(15))
        })
        card.addView(text("এখনো সময় হয়নি", 16f, Typeface.BOLD, colorDark, Gravity.CENTER).apply {
            setPadding(0, dp(14), 0, 0)
        })
        card.addView(text(
            "আপনার অ্যাপয়েন্টমেন্টের নির্ধারিত সময়ের আগে $featureLabel চালু করা যাবে না। উপরের কাউন্টডাউন শেষ হলেই এই ফিচারটি স্বয়ংক্রিয়ভাবে চালু হয়ে যাবে।",
            12.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER
        ).apply {
            setPadding(0, dp(10), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        card.addView(text("বাকি সময়: ${remainingTimeSummary()}", 13f, Typeface.BOLD, colorPrimary, Gravity.CENTER).apply {
            setPadding(0, dp(16), 0, 0)
        })
        card.addView(text("বুঝেছি", 13.5f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
            background = roundedBg(colorPrimary, 14f)
            setPadding(0, dp(13), 0, dp(13))
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20)
            }
        })

        dialog.setContentView(card)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.86).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    /** ডায়ালগে দেখানোর জন্য বাকি সময়ের সংক্ষিপ্ত, মানুষ-পঠনযোগ্য বর্ণনা */
    private fun remainingTimeSummary(): String {
        val target = targetMillis ?: return "শীঘ্রই"
        val diff = target - System.currentTimeMillis()
        if (diff <= 0) return "শীঘ্রই"
        val totalSeconds = diff / 1000
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            days > 0 -> "$days দিন $hours ঘণ্টা"
            hours > 0 -> "$hours ঘণ্টা $minutes মিনিট"
            minutes > 0 -> "$minutes মিনিট"
            else -> "কয়েক সেকেন্ড"
        }
    }

    /** কাউন্টডাউন শেষ হওয়ার সাথে সাথেই Chat/Video/Audio বাটনগুলোর রঙ, আইকন-টিন্ট ও লক-ব্যাজ আপডেট হয় */
    private fun updateCommunicationButtonsUi() {
        commButtons.forEach { cb ->
            if (isCountdownFinished) {
                cb.circleBg.setColor(cb.displayColor)
                setIconTint(cb.iconDrawable, Color.WHITE)
                cb.label.setTextColor(colorDark)
                cb.lockBadge.visibility = View.GONE
            } else {
                cb.circleBg.setColor(colorLockedBg)
                setIconTint(cb.iconDrawable, colorTextMuted)
                cb.label.setTextColor(colorTextMuted)
                cb.lockBadge.visibility = View.VISIBLE
            }
        }
        commSubtitle.text = if (isCountdownFinished)
            "সময় হয়ে গেছে — এখন আপনি সরাসরি যোগাযোগ করতে পারবেন"
        else
            "অ্যাপয়েন্টমেন্টের সময় হলে বাটনগুলো স্বয়ংক্রিয়ভাবে চালু হয়ে যাবে"
    }

    private fun setIconTint(drawable: Drawable, color: Int) {
        (drawable as? TintableIcon)?.updateTint(color)
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
            setUnits(placeholder = true)
            countdownLabelText.text = "সময় হিসাব করা যাচ্ছে না"
            markCountdownFinishedIfNeeded()
            return
        }
        val diff = target - System.currentTimeMillis()
        if (diff <= 0) {
            setUnits(0, 0, 0, 0)
            countdownLabelText.text = "আপনার অ্যাপয়েন্টমেন্টের সময় হয়ে গেছে"
            markCountdownFinishedIfNeeded()
            return
        }
        val totalSeconds = diff / 1000
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        countdownLabelText.text = "অ্যাপয়েন্টমেন্ট পর্যন্ত বাকি সময়"
        setUnits(days, hours, minutes, seconds)
    }

    /** টার্গেট সময় পার হয়ে গেলে বা হিসাব করা না গেলে (উভয় ক্ষেত্রেই আটকে না রেখে) একবারই বাটন আনলক করে */
    private fun markCountdownFinishedIfNeeded() {
        if (!isCountdownFinished) {
            isCountdownFinished = true
            updateCommunicationButtonsUi()
        }
    }

    private fun setUnits(days: Long = 0, hours: Long = 0, minutes: Long = 0, seconds: Long = 0, placeholder: Boolean = false) {
        if (placeholder) {
            daysBox.visibility = View.GONE
            daysColon.visibility = View.GONE
            hoursValueText.text = "--"
            minutesValueText.text = "--"
            secondsValueText.text = "--"
            return
        }
        if (days > 0) {
            daysBox.visibility = View.VISIBLE
            daysColon.visibility = View.VISIBLE
            daysValueText.text = days.toString()
        } else {
            daysBox.visibility = View.GONE
            daysColon.visibility = View.GONE
        }
        hoursValueText.text = String.format(Locale.US, "%02d", hours)
        minutesValueText.text = String.format(Locale.US, "%02d", minutes)
        secondsValueText.text = String.format(Locale.US, "%02d", seconds)
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
        setUnits(0, 0, 0, 0)
        stopCountdown()
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------
    private fun text(t: String, sizeSp: Float, style: Int, color: Int, gravity: Int): TextView = TextView(this).apply {
        text = t; textSize = sizeSp; setTypeface(null, style); setTextColor(color); this.gravity = gravity
    }

    private fun space(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, h) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(color)
    }

    // ------------------------------------------------------------------
    // TintableIcon — যেসব কাস্টম ভেক্টর আইকনের রঙ রানটাইমে বদলানো দরকার
    // (লকড/আনলকড অবস্থা অনুযায়ী), তারা এই ইন্টারফেস ইমপ্লিমেন্ট করে
    // ------------------------------------------------------------------
    private interface TintableIcon {
        fun updateTint(color: Int)
    }

    // ------------------------------------------------------------------
    // BackArrowDrawable — ব্যাক বাটনের জন্য সম্পূর্ণ ভেক্টর-আঁকা (Canvas/Paint দিয়ে) অ্যারো আইকন
    // ------------------------------------------------------------------
    private class BackArrowDrawable(iconColor: Int, private val strokeWidthPx: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = iconColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return

            val left = b.left + w * 0.22f
            val right = b.left + w * 0.78f
            val cy = b.top + h / 2f
            val armLen = w * 0.26f

            canvas.drawLine(left, cy, right, cy, paint)
            canvas.drawLine(left, cy, left + armLen, cy - armLen, paint)
            canvas.drawLine(left, cy, left + armLen, cy + armLen, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // ------------------------------------------------------------------
    // LockIconDrawable — কাউন্টডাউন শেষ না হওয়া পর্যন্ত Chat/Video/Audio বাটনে
    // দেখানো "লক" ভেক্টর আইকন
    // ------------------------------------------------------------------
    private class LockIconDrawable(iconColor: Int, private val strokeWidthPx: Float) : Drawable() {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = iconColor
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = iconColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val bodyRect = android.graphics.RectF(b.left + w * 0.20f, b.top + h * 0.46f, b.right - w * 0.20f, b.bottom - h * 0.10f)
            canvas.drawRoundRect(bodyRect, w * 0.10f, w * 0.10f, fillPaint)
            val shackleRect = android.graphics.RectF(b.left + w * 0.32f, b.top + h * 0.08f, b.right - w * 0.32f, b.top + h * 0.58f)
            canvas.drawArc(shackleRect, 180f, 180f, false, strokePaint)
        }

        override fun setAlpha(alpha: Int) { strokePaint.alpha = alpha; fillPaint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { strokePaint.colorFilter = colorFilter; fillPaint.colorFilter = colorFilter }

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // ------------------------------------------------------------------
    // ChatIconDrawable — স্পিচ-বাবল + তিনটা টাইপিং-ডট, চ্যাট বাটনের ভেক্টর আইকন।
    // TintableIcon ইমপ্লিমেন্ট করে যাতে লক/আনলক অবস্থায় রঙ বদলানো যায়।
    // ------------------------------------------------------------------
    private class ChatIconDrawable(iconColor: Int, private val strokeWidthPx: Float) : Drawable(), TintableIcon {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = iconColor
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = iconColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val rect = android.graphics.RectF(b.left + w * 0.08f, b.top + h * 0.12f, b.right - w * 0.08f, b.top + h * 0.70f)
            canvas.drawRoundRect(rect, w * 0.16f, w * 0.16f, strokePaint)
            val tail = android.graphics.Path().apply {
                moveTo(b.left + w * 0.30f, rect.bottom - h * 0.02f)
                lineTo(b.left + w * 0.24f, b.top + h * 0.90f)
                lineTo(b.left + w * 0.46f, rect.bottom - h * 0.02f)
                close()
            }
            canvas.drawPath(tail, fillPaint)
            val dotR = w * 0.035f
            val dotY = (rect.top + rect.bottom) / 2f
            canvas.drawCircle(rect.left + rect.width() * 0.28f, dotY, dotR, fillPaint)
            canvas.drawCircle(rect.left + rect.width() * 0.5f, dotY, dotR, fillPaint)
            canvas.drawCircle(rect.left + rect.width() * 0.72f, dotY, dotR, fillPaint)
        }

        override fun setAlpha(alpha: Int) { strokePaint.alpha = alpha; fillPaint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { strokePaint.colorFilter = colorFilter; fillPaint.colorFilter = colorFilter }
        override fun updateTint(color: Int) { strokePaint.color = color; fillPaint.color = color; invalidateSelf() }

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // ------------------------------------------------------------------
    // VideoCallIconDrawable — ক্যামেরা-বডি + লেন্স ত্রিভুজ, ভিডিও কল বাটনের ভেক্টর আইকন
    // ------------------------------------------------------------------
    private class VideoCallIconDrawable(iconColor: Int) : Drawable(), TintableIcon {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = iconColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val bodyRect = android.graphics.RectF(b.left + w * 0.08f, b.top + h * 0.22f, b.left + w * 0.62f, b.top + h * 0.78f)
            canvas.drawRoundRect(bodyRect, w * 0.10f, w * 0.10f, paint)
            val lens = android.graphics.Path().apply {
                moveTo(bodyRect.right, b.top + h * 0.34f)
                lineTo(b.right - w * 0.06f, b.top + h * 0.20f)
                lineTo(b.right - w * 0.06f, b.top + h * 0.80f)
                lineTo(bodyRect.right, b.top + h * 0.66f)
                close()
            }
            canvas.drawPath(lens, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        override fun updateTint(color: Int) { paint.color = color; invalidateSelf() }

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // ------------------------------------------------------------------
    // AudioCallIconDrawable — ক্লাসিক ফোন-হ্যান্ডসেট সিলুয়েট, অডিও কল বাটনের ভেক্টর আইকন
    // (Bezier কার্ভ দিয়ে আঁকা, কোনো র‍্যাস্টার/ইমুজি নয়)
    // ------------------------------------------------------------------
    private class AudioCallIconDrawable(iconColor: Int) : Drawable(), TintableIcon {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = iconColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val path = android.graphics.Path().apply {
                moveTo(b.left + w * 0.20f, b.top + h * 0.12f)
                cubicTo(b.left + w * 0.05f, b.top + h * 0.20f, b.left + w * 0.05f, b.top + h * 0.40f, b.left + w * 0.20f, b.top + h * 0.55f)
                cubicTo(b.left + w * 0.35f, b.top + h * 0.70f, b.left + w * 0.50f, b.top + h * 0.80f, b.left + w * 0.65f, b.top + h * 0.85f)
                cubicTo(b.left + w * 0.80f, b.top + h * 0.90f, b.left + w * 0.90f, b.top + h * 0.75f, b.left + w * 0.88f, b.top + h * 0.68f)
                cubicTo(b.left + w * 0.86f, b.top + h * 0.62f, b.left + w * 0.72f, b.top + h * 0.55f, b.left + w * 0.65f, b.top + h * 0.60f)
                cubicTo(b.left + w * 0.60f, b.top + h * 0.63f, b.left + w * 0.55f, b.top + h * 0.60f, b.left + w * 0.48f, b.top + h * 0.52f)
                cubicTo(b.left + w * 0.42f, b.top + h * 0.45f, b.left + w * 0.40f, b.top + h * 0.40f, b.left + w * 0.42f, b.top + h * 0.34f)
                cubicTo(b.left + w * 0.46f, b.top + h * 0.27f, b.left + w * 0.38f, b.top + h * 0.14f, b.left + w * 0.32f, b.top + h * 0.12f)
                cubicTo(b.left + w * 0.28f, b.top + h * 0.10f, b.left + w * 0.24f, b.top + h * 0.10f, b.left + w * 0.20f, b.top + h * 0.12f)
                close()
            }
            canvas.drawPath(path, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        override fun updateTint(color: Int) { paint.color = color; invalidateSelf() }

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
