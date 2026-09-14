package com.konasl.nagad

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Calendar

class BookAppointmentActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryLight = Color.parseColor("#16897A")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorAccent = Color.parseColor("#F59E0B")
    private val colorBg = Color.parseColor("#F4F7F6")
    private val colorTextMuted = Color.parseColor("#6B7280")
    private val colorDark = Color.parseColor("#111827")
    private val colorCard = Color.WHITE
    private val colorFieldBorder = Color.parseColor("#E7ECEA")

    private val appointmentFee = 800

    // TODO: আসল bKash/Nagad মার্চেন্ট/পার্সোনাল নাম্বার বসান
    private val bkashMerchantNumber = "01XXXXXXXXX"
    private val nagadMerchantNumber = "01XXXXXXXXX"

    // পেমেন্ট লোগো (ব্র্যান্ড ইমেজ, নেটওয়ার্ক থেকে লোড হয়)
    private val bkashLogoUrl = "https://play-lh.googleusercontent.com/ncgi2sk_NS5u8TfsEVmdaqQhRlv6D0c9JIQ-GmHvazUbp9GDU8gxNZxaq98ysy34juOmSA15KlPLjoAgquZ0nQ"
    private val nagadLogoUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQy31fyGGzUajazvS-Xj8xUUfb9Zv7ZBQ8I_e1FJeWlFQ&s=10"

    data class DateOption(val label: String, val dayNum: Int, val month: String, val isoDate: String)
    data class TimeSlot(val label: String, val value24: String)
    data class PaymentOption(val label: String, val subtitle: String, val logoUrl: String, val brandColor: Int, val merchantNumber: String)

    private var selectedDate: DateOption? = null
    private var selectedTime24: String? = null
    private var selectedPayment: String? = null

    private lateinit var dateRow: LinearLayout
    private lateinit var timeGrid: GridLayout
    private lateinit var customTimeBtn: TextView
    private lateinit var paymentRow: LinearLayout
    private lateinit var manualPayCard: LinearLayout
    private lateinit var manualPayNumberText: TextView
    private lateinit var manualPayInstructionText: TextView
    private lateinit var manualPayLogoWrap: FrameLayout
    private lateinit var manualPayLogoImg: ImageView
    private lateinit var trxIdInput: EditText
    private lateinit var reasonInput: EditText
    private lateinit var reportPickRow: LinearLayout
    private lateinit var reportPickText: TextView
    private lateinit var reportPickSubtext: TextView
    private lateinit var reportClearBtn: ImageView
    private lateinit var reportPickLauncher: ActivityResultLauncher<Array<String>>
    private var selectedReportUri: Uri? = null
    private var selectedReportName: String = ""
    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var confirmBtn: LinearLayout
    private lateinit var totalFeeText: TextView

    private val paymentRowViews = mutableListOf<Triple<LinearLayout, PaymentOption, HomeActivity.VectorIconDrawable>>()

    private val dateOptions = mutableListOf<DateOption>()

    private val timeSlots = listOf(
        TimeSlot("সকাল ১০:০০", "10:00"),
        TimeSlot("সকাল ১১:৩০", "11:30"),
        TimeSlot("দুপুর ০১:০০", "13:00"),
        TimeSlot("বিকাল ০৪:০০", "16:00"),
        TimeSlot("সন্ধ্যা ০৬:৩০", "18:30"),
        TimeSlot("রাত ০৮:০০", "20:00")
    )

    private val paymentOptions = listOf(
        PaymentOption("বিকাশ", "সেন্ড মানি করে বুক করুন", bkashLogoUrl, Color.parseColor("#E2136E"), bkashMerchantNumber),
        PaymentOption("নগদ", "সেন্ড মানি করে বুক করুন", nagadLogoUrl, Color.parseColor("#F42534"), nagadMerchantNumber)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reportPickLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) { /* কিছু ডকুমেন্ট প্রোভাইডার persistable permission সাপোর্ট করে না, সমস্যা নেই */ }
                selectedReportUri = uri
                selectedReportName = queryDisplayName(uri) ?: "রিপোর্ট ফাইল"
                updateReportPickUi()
            }
        }

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
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(40))
        }

        // ---------------- HEADER (গ্রেডিয়েন্ট + গ্লো ডেকোরেশন, HomeActivity-র সাথে সামঞ্জস্যপূর্ণ) ----------------
        val header = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark)
            ).apply {
                setCornerRadii(floatArrayOf(0f, 0f, 0f, 0f, dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat()))
            }
            clipToPadding = false
        }
        header.addView(glowCircle(dp(160), Color.argb(24, 255, 255, 255), Gravity.TOP or Gravity.END, dp(-55), dp(-50)))
        header.addView(glowCircle(dp(110), Color.argb(22, Color.red(colorAccent), Color.green(colorAccent), Color.blue(colorAccent)), Gravity.BOTTOM or Gravity.START, dp(-35), dp(-30)))

        val headerInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(40), dp(22), dp(46))
        }
        val backRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backBtn = TextView(this).apply {
            text = "←"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = roundedBg(Color.argb(46, 255, 255, 255), 30f)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            setOnClickListener { finish() }
        }
        val headerTitle = text("অ্যাপয়েন্টমেন্ট বুক করুন", 18f, Typeface.BOLD, Color.WHITE, Gravity.START).apply {
            setPadding(dp(12), 0, 0, 0)
        }
        backRow.addView(backBtn)
        backRow.addView(headerTitle)

        val headerSub = text(
            "ডা. মাসুম বিল্লাহ সানি • মেডিসিন, শিশু, ডায়াবেটিস ও চর্ম-যৌনরোগ বিশেষজ্ঞ",
            11.5f, Typeface.NORMAL, Color.argb(215, 255, 255, 255), Gravity.START
        ).apply { setPadding(dp(46), dp(8), 0, 0) }

        headerInner.addView(backRow)
        headerInner.addView(headerSub)
        header.addView(headerInner)

        // ---------------- FEE CARD (overlap) ----------------
        val feeCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(colorCard, 20f)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(-24), dp(18), 0)
            }
            elevation = dp(6).toFloat()
            outlineProvider = roundOutline(20)
            clipToOutline = true
        }
        feeCard.addView(ImageView(this).apply {
            setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.PILL, colorPrimary, dp(20)))
            background = roundedBg(Color.parseColor("#E4F3F1"), 14f)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setPadding(dp(11), dp(11), dp(11), dp(11))
        })
        val feeLeftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
        }
        feeLeftCol.addView(text("কনসালটেশন ফি", 11.5f, Typeface.NORMAL, colorTextMuted, Gravity.START))
        feeLeftCol.addView(
            text("৳ $appointmentFee", 21f, Typeface.BOLD, colorPrimary, Gravity.START).apply {
                setPadding(0, dp(2), 0, 0)
            }
        )
        val feeBadge = text("অনলাইন পেমেন্ট", 10.5f, Typeface.BOLD, colorAccent, Gravity.CENTER).apply {
            background = roundedBg(Color.parseColor("#FEF3E2"), 30f)
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        feeCard.addView(feeLeftCol)
        feeCard.addView(feeBadge)

        // ---------------- SECTION: DATE ----------------
        val dateSection = sectionTitle(HomeActivity.VectorIconDrawable.IconType.CALENDAR, "তারিখ নির্বাচন করুন")
        dateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dateScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(10), dp(18), 0)
            }
            addView(dateRow)
        }
        buildDateOptions()

        // ---------------- SECTION: TIME ----------------
        val timeSection = sectionTitle(HomeActivity.VectorIconDrawable.IconType.CLOCK, "সময় নির্বাচন করুন")
        timeGrid = GridLayout(this).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(14), dp(10), dp(14), 0)
            }
        }
        buildTimeSlots()

        customTimeBtn = text("+ অন্য সময় বেছে নিন", 12.5f, Typeface.BOLD, colorPrimary, Gravity.CENTER).apply {
            background = roundedBg(Color.parseColor("#E4F3F1"), 14f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(10), dp(18), 0)
            }
            setOnClickListener { pickCustomTime() }
        }

        // ---------------- SECTION: PATIENT INFO ----------------
        val infoSection = sectionTitle(HomeActivity.VectorIconDrawable.IconType.PERSON, "রোগীর তথ্য")
        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(colorCard, 18f)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(10), dp(18), 0)
            }
            elevation = 1.5f * resources.displayMetrics.density
        }
        nameInput = styledInput("রোগীর নাম", SupabaseClient.getName(this) ?: "")
        phoneInput = styledInput("ফোন নাম্বার", SupabaseClient.getPhone(this) ?: "").apply {
            inputType = InputType.TYPE_CLASS_PHONE
        }
        reasonInput = styledInput("সমস্যার সংক্ষিপ্ত বিবরণ", "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        infoCard.addView(fieldLabel("নাম"))
        infoCard.addView(nameInput)
        infoCard.addView(space(dp(14)))
        infoCard.addView(fieldLabel("ফোন নাম্বার"))
        infoCard.addView(phoneInput)
        infoCard.addView(space(dp(14)))
        infoCard.addView(fieldLabel("সমস্যার বিবরণ"))
        infoCard.addView(reasonInput)
        infoCard.addView(space(dp(14)))
        infoCard.addView(fieldLabel("আগের টেস্ট রিপোর্ট (ঐচ্ছিক)"))
        infoCard.addView(buildReportPickRow())

        // ---------------- SECTION: PAYMENT ----------------
        val paymentSection = sectionTitle(HomeActivity.VectorIconDrawable.IconType.SHIELD, "পেমেন্ট পদ্ধতি")
        paymentRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(10), dp(18), 0)
            }
        }
        buildPaymentOptions()

        // ---------------- MANUAL PAYMENT CARD (bKash/Nagad সিলেক্ট করলে দেখা যাবে) ----------------
        manualPayCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBgStroke(Color.parseColor("#FFF8EC"), Color.parseColor("#FBE3B8"), 16f, 1)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(10), dp(18), 0)
            }
            visibility = View.GONE
        }
        val manualHeadRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        manualPayLogoWrap = FrameLayout(this).apply {
            background = circleBg(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            outlineProvider = circleOutline()
            clipToOutline = true
            elevation = dp(1).toFloat()
        }
        manualPayLogoImg = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(26), dp(26)).apply { gravity = Gravity.CENTER }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        manualPayLogoWrap.addView(manualPayLogoImg)
        manualPayInstructionText = text(
            "নিচের নাম্বারে Send Money করে Transaction ID টি নিচে লিখুন",
            11.5f, Typeface.NORMAL, colorTextMuted, Gravity.START
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        manualHeadRow.addView(manualPayLogoWrap)
        manualHeadRow.addView(manualPayInstructionText)

        val numberRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(Color.WHITE, 12f)
            setPadding(dp(14), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(14), 0, dp(14))
            }
        }
        manualPayNumberText = text("01XXXXXXXXX", 16f, Typeface.BOLD, colorDark, Gravity.START).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val copyBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedBg(Color.parseColor("#E4F3F1"), 12f)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            isClickable = true
            isFocusable = true
            addView(ImageView(this@BookAppointmentActivity).apply {
                setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.DOCUMENT, colorPrimary, dp(13)))
                layoutParams = LinearLayout.LayoutParams(dp(13), dp(13))
            })
            addView(text("কপি", 11.5f, Typeface.BOLD, colorPrimary, Gravity.CENTER).apply {
                setPadding(dp(6), 0, 0, 0)
            })
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Payment Number", manualPayNumberText.text.toString()))
                Toast.makeText(this@BookAppointmentActivity, "নাম্বার কপি হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
        numberRow.addView(manualPayNumberText)
        numberRow.addView(copyBtn)

        trxIdInput = styledInput("Transaction ID (TrxID) লিখুন", "")
        trxIdInput.background = roundedBg(Color.WHITE, 12f)

        manualPayCard.addView(manualHeadRow)
        manualPayCard.addView(numberRow)
        manualPayCard.addView(fieldLabel("Transaction ID"))
        manualPayCard.addView(trxIdInput)

        // ---------------- CONFIRM BUTTON ----------------
        totalFeeText = text("সর্বমোট: ৳ $appointmentFee", 13f, Typeface.BOLD, colorDark, Gravity.START)
        val confirmWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), 0)
        }
        confirmWrap.addView(totalFeeText)
        confirmBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark)).apply {
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
            elevation = dp(3).toFloat()
            outlineProvider = roundOutline(16)
            clipToOutline = true
            setOnClickListener { onConfirmClicked() }
        }
        confirmBtn.addView(ImageView(this).apply {
            setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.CHECK, Color.WHITE, dp(18)))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) }
        })
        confirmBtn.addView(text("কনফার্ম করুন", 15f, Typeface.BOLD, Color.WHITE, Gravity.CENTER))
        confirmWrap.addView(confirmBtn)

        page.addView(header)
        page.addView(feeCard)
        page.addView(dateSection)
        page.addView(dateScroll)
        page.addView(timeSection)
        page.addView(timeGrid)
        page.addView(customTimeBtn)
        page.addView(infoSection)
        page.addView(infoCard)
        page.addView(paymentSection)
        page.addView(paymentRow)
        page.addView(manualPayCard)
        page.addView(confirmWrap)

        scroll.addView(page)
        root.addView(scroll)
        setContentView(root)
    }

    // ------------------------------------------------------------------
    private fun buildDateOptions() {
        dateRow.removeAllViews()
        dateOptions.clear()
        dateOptions.addAll(generateDateOptions())

        dateOptions.forEachIndexed { index, option ->
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = roundedBgStroke(colorCard, colorFieldBorder, 16f, 1)
                setPadding(dp(18), dp(14), dp(18), dp(14))
                layoutParams = LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(10)
                }
                tag = option.isoDate
            }
            chip.addView(text(option.label, 11f, Typeface.BOLD, colorTextMuted, Gravity.CENTER))
            chip.addView(
                text("${option.dayNum}", 18f, Typeface.BOLD, colorDark, Gravity.CENTER).apply {
                    setPadding(0, dp(4), 0, 0)
                }
            )
            chip.addView(text(option.month, 10.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER))

            chip.setOnClickListener {
                selectedDate = option
                for (i in 0 until dateRow.childCount) {
                    val c = dateRow.getChildAt(i) as LinearLayout
                    val selected = c.tag == option.isoDate
                    c.background = if (selected)
                        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(colorPrimaryLight, colorPrimaryDark)).apply { cornerRadius = dp(16).toFloat() }
                    else
                        roundedBgStroke(colorCard, colorFieldBorder, 16f, 1)
                    for (j in 0 until c.childCount) {
                        (c.getChildAt(j) as? TextView)?.setTextColor(
                            if (selected) Color.WHITE else if (j == 1) colorDark else colorTextMuted
                        )
                    }
                }
            }
            if (index == 0) chip.performClick()
            dateRow.addView(chip)
        }
    }

    /** আজ / আগামীকাল / পরের ২ দিনের তারিখ — প্রতিবার Activity খুললে Calendar থেকে ফ্রেশ জেনারেট হয়, তাই সবসময় আপডেটেড থাকে */
    private fun generateDateOptions(): List<DateOption> {
        val quickLabels = arrayOf("আজ", "আগামীকাল")
        val weekDays = arrayOf("রবি", "সোম", "মঙ্গল", "বুধ", "বৃহঃ", "শুক্র", "শনি")
        val months = arrayOf("জানু", "ফেব্রু", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্ট", "অক্টো", "নভে", "ডিসে")
        val result = mutableListOf<DateOption>()
        for (i in 0..3) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, i)
            val label = if (i < quickLabels.size) quickLabels[i] else weekDays[cal.get(Calendar.DAY_OF_WEEK) - 1]
            val dayNum = cal.get(Calendar.DAY_OF_MONTH)
            val month = months[cal.get(Calendar.MONTH)]
            val iso = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            result.add(DateOption(label, dayNum, month, iso))
        }
        return result
    }

    // ------------------------------------------------------------------
    private fun buildTimeSlots() {
        timeGrid.removeAllViews()
        timeSlots.forEach { slot ->
            val chip = text(slot.label, 12.5f, Typeface.BOLD, colorDark, Gravity.CENTER).apply {
                background = roundedBgStroke(colorCard, colorFieldBorder, 14f, 1)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                tag = slot.value24
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                setOnClickListener {
                    selectedTime24 = slot.value24
                    highlightSelectedTime(tag)
                }
            }
            timeGrid.addView(chip)
        }
    }

    private fun highlightSelectedTime(selectedTag: Any?) {
        for (i in 0 until timeGrid.childCount) {
            val c = timeGrid.getChildAt(i) as TextView
            val selected = c.tag == selectedTag
            c.background = if (selected)
                GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(colorPrimaryLight, colorPrimaryDark)).apply { cornerRadius = dp(14).toFloat() }
            else
                roundedBgStroke(colorCard, colorFieldBorder, 14f, 1)
            c.setTextColor(if (selected) Color.WHITE else colorDark)
        }
        customTimeBtn.background = roundedBg(Color.parseColor("#E4F3F1"), 14f)
        customTimeBtn.setTextColor(colorPrimary)
        customTimeBtn.text = "+ অন্য সময় বেছে নিন"
    }

    private fun pickCustomTime() {
        val cal = Calendar.getInstance()
        TimePickerDialog(this, { _, h, min ->
            selectedTime24 = "%02d:%02d".format(h, min)
            for (i in 0 until timeGrid.childCount) {
                val c = timeGrid.getChildAt(i) as TextView
                c.background = roundedBgStroke(colorCard, colorFieldBorder, 14f, 1)
                c.setTextColor(colorDark)
            }
            customTimeBtn.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(colorPrimaryLight, colorPrimaryDark)).apply { cornerRadius = dp(14).toFloat() }
            customTimeBtn.setTextColor(Color.WHITE)
            customTimeBtn.text = "নির্বাচিত সময়: $selectedTime24"
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
    }

    // ------------------------------------------------------------------
    private fun buildPaymentOptions() {
        paymentRow.removeAllViews()
        paymentRowViews.clear()

        paymentOptions.forEach { option ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedBgStroke(colorCard, colorFieldBorder, 16f, 1)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(6), 0, dp(6))
                }
                elevation = dp(1).toFloat()
                outlineProvider = roundOutline(16)
                clipToOutline = true
                tag = option.label
            }

            val logoWrap = FrameLayout(this).apply {
                background = circleBg(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
                outlineProvider = circleOutline()
                clipToOutline = true
                elevation = dp(1).toFloat()
            }
            val logoImg = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(38), dp(38)).apply { gravity = Gravity.CENTER }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            logoWrap.addView(logoImg)
            loadNetworkImage(option.logoUrl, logoImg)

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) }
            }
            col.addView(text(option.label, 14f, Typeface.BOLD, colorDark, Gravity.START))
            col.addView(text(option.subtitle, 10.5f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
                setPadding(0, dp(2), 0, 0)
            })

            val checkDrawable = HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.CHECK, Color.parseColor("#D1D5DB"), dp(20))
            val checkIcon = ImageView(this).apply {
                setImageDrawable(checkDrawable)
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            }

            row.addView(logoWrap)
            row.addView(col)
            row.addView(checkIcon)

            row.setOnClickListener {
                selectedPayment = option.label
                paymentRowViews.forEach { (r, opt, check) ->
                    val selected = opt.label == option.label
                    r.background = if (selected)
                        roundedBgStroke(Color.argb(20, Color.red(opt.brandColor), Color.green(opt.brandColor), Color.blue(opt.brandColor)), opt.brandColor, 16f, 2)
                    else
                        roundedBgStroke(colorCard, colorFieldBorder, 16f, 1)
                    check.updateTint(if (selected) opt.brandColor else Color.parseColor("#D1D5DB"))
                }
                updateManualPayCard(option)
            }
            paymentRow.addView(row)
            paymentRowViews.add(Triple(row, option, checkDrawable))
        }
    }

    /** bKash/Nagad যেটাই সিলেক্ট করা হোক, মার্চেন্ট নাম্বার + লোগো + TrxID ইনপুট দেখায় */
    private fun updateManualPayCard(option: PaymentOption) {
        manualPayNumberText.text = option.merchantNumber
        manualPayInstructionText.text =
            "${option.label}-এ (Send Money) ৳$appointmentFee পাঠিয়ে Transaction ID টি নিচে লিখুন"
        loadNetworkImage(option.logoUrl, manualPayLogoImg)
        manualPayCard.visibility = View.VISIBLE
    }

    /** নেটওয়ার্ক থেকে বিকাশ/নগদ লোগো ইমেজ লোড করে ImageView-তে বসায় (কোনো তৃতীয়-পক্ষ ইমেজ লাইব্রেরি ছাড়াই) */
    private fun loadNetworkImage(url: String, target: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.doInput = true
                connection.connect()
                val bitmap = BitmapFactory.decodeStream(connection.getInputStream())
                withContext(Dispatchers.Main) {
                    if (bitmap != null) target.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                // ইন্টারনেট না থাকলে বা লোড ব্যর্থ হলে চুপচাপ স্কিপ করা হয়, লেবেল টেক্সট দিয়েই বোঝা যাবে কোনটা কী
            }
        }
    }

    // ------------------------------------------------------------------
    // টেস্ট রিপোর্ট আপলোড (ঐচ্ছিক) — ছবি বা PDF, Supabase Storage-এ যায়
    // ------------------------------------------------------------------
    private fun buildReportPickRow(): LinearLayout {
        reportPickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBgStroke(Color.parseColor("#F8FBFA"), colorFieldBorder, 12f, 1)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { reportPickLauncher.launch(arrayOf("image/*", "application/pdf")) }
        }
        val iconWrap = ImageView(this).apply {
            setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.DOCUMENT, colorPrimary, dp(15)))
            background = roundedBg(Color.parseColor("#E4F3F1"), 10f)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
        }
        reportPickText = text("রিপোর্ট আপলোড করুন", 12.5f, Typeface.BOLD, colorDark, Gravity.START)
        reportPickSubtext = text("ছবি বা PDF সিলেক্ট করুন (ঐচ্ছিক)", 10.5f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
            setPadding(0, dp(2), 0, 0)
        }
        textCol.addView(reportPickText)
        textCol.addView(reportPickSubtext)

        reportClearBtn = ImageView(this).apply {
            setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.TRASH, Color.parseColor("#DC2626"), dp(14)))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setPadding(dp(6), dp(6), dp(6), dp(6))
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedReportUri = null
                selectedReportName = ""
                updateReportPickUi()
            }
        }

        reportPickRow.addView(iconWrap)
        reportPickRow.addView(textCol)
        reportPickRow.addView(reportClearBtn)
        return reportPickRow
    }

    private fun updateReportPickUi() {
        if (selectedReportUri != null) {
            reportPickText.text = selectedReportName
            reportPickSubtext.text = "ফাইল সিলেক্ট করা হয়েছে — বদলাতে ট্যাপ করুন"
            reportPickSubtext.setTextColor(colorPrimary)
            reportPickRow.background = roundedBgStroke(Color.parseColor("#E4F3F1"), colorPrimary, 12f, 1)
            reportClearBtn.visibility = View.VISIBLE
        } else {
            reportPickText.text = "রিপোর্ট আপলোড করুন"
            reportPickSubtext.text = "ছবি বা PDF সিলেক্ট করুন (ঐচ্ছিক)"
            reportPickSubtext.setTextColor(colorTextMuted)
            reportPickRow.background = roundedBgStroke(Color.parseColor("#F8FBFA"), colorFieldBorder, 12f, 1)
            reportClearBtn.visibility = View.GONE
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (e: Exception) {
        null
    }

    /** সিলেক্ট করা রিপোর্ট ফাইলটা পড়ে Supabase Storage-এ আপলোড করে, পাবলিক URL রিটার্ন করে */
    private suspend fun uploadSelectedReport(uri: Uri, name: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("ফাইল পড়া যায়নি"))
            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
            SupabaseClient.uploadReportFile(name.ifEmpty { "report" }, mime, bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------
    private fun onConfirmClicked() {
        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val reason = reasonInput.text.toString().trim()
        val trxId = trxIdInput.text.toString().trim()

        if (selectedDate == null) { Toast.makeText(this, "তারিখ নির্বাচন করুন", Toast.LENGTH_SHORT).show(); return }
        if (selectedTime24 == null) { Toast.makeText(this, "সময় নির্বাচন করুন", Toast.LENGTH_SHORT).show(); return }
        if (name.isEmpty() || phone.isEmpty()) { Toast.makeText(this, "নাম ও ফোন নাম্বার দিন", Toast.LENGTH_SHORT).show(); return }
        if (reason.isEmpty()) { Toast.makeText(this, "সমস্যার বিবরণ দিন", Toast.LENGTH_SHORT).show(); return }
        if (selectedPayment == null) { Toast.makeText(this, "পেমেন্ট পদ্ধতি নির্বাচন করুন", Toast.LENGTH_SHORT).show(); return }
        if (trxId.isEmpty()) { Toast.makeText(this, "Transaction ID লিখুন", Toast.LENGTH_SHORT).show(); return }

        val patientId = SupabaseClient.getPatientId(this)
        if (patientId == null) {
            Toast.makeText(this, "সেশন পাওয়া যায়নি, আবার লগইন করুন", Toast.LENGTH_SHORT).show(); return
        }

        confirmBtn.isEnabled = false
        val reportUri = selectedReportUri
        val reportName = selectedReportName
        if (reportUri != null) {
            Toast.makeText(this, "রিপোর্ট আপলোড হচ্ছে...", Toast.LENGTH_SHORT).show()
        }
        lifecycleScope.launch {
            var reportUrl = ""
            if (reportUri != null) {
                val uploadResult = uploadSelectedReport(reportUri, reportName)
                if (uploadResult.isFailure) {
                    confirmBtn.isEnabled = true
                    Toast.makeText(
                        this@BookAppointmentActivity,
                        "রিপোর্ট আপলোড ব্যর্থ: ${uploadResult.exceptionOrNull()?.message ?: "আবার চেষ্টা করুন"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                reportUrl = uploadResult.getOrNull() ?: ""
            }
            val result = SupabaseClient.createAppointment(
                patientId = patientId,
                patientName = name,
                phone = phone,
                reason = reason,
                date = selectedDate!!.isoDate,
                time = selectedTime24!!,
                paymentMethod = selectedPayment!!,
                fee = appointmentFee,
                transactionId = trxId,
                paymentStatus = "pending_verification",
                reportUrl = reportUrl
            )
            result.onSuccess {
                Toast.makeText(
                    this@BookAppointmentActivity,
                    "অ্যাপয়েন্টমেন্ট বুক হয়েছে, পেমেন্ট যাচাই হলে কনফার্ম করা হবে",
                    Toast.LENGTH_LONG
                ).show()
                setResult(RESULT_OK)
                finish()
            }.onFailure {
                confirmBtn.isEnabled = true
                Toast.makeText(this@BookAppointmentActivity, it.message ?: "বুক করা যায়নি", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------
    private fun sectionTitle(iconType: HomeActivity.VectorIconDrawable.IconType, title: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(22), dp(18), 0)
            addView(ImageView(this@BookAppointmentActivity).apply {
                setImageDrawable(HomeActivity.VectorIconDrawable(iconType, colorPrimary, dp(14)))
                background = roundedBg(Color.parseColor("#E4F3F1"), 8f)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
                setPadding(dp(6), dp(6), dp(6), dp(6))
            })
            addView(text(title, 14.5f, Typeface.BOLD, colorDark, Gravity.START).apply {
                setPadding(dp(10), 0, 0, 0)
            })
        }

    private fun fieldLabel(label: String): TextView =
        text(label, 11.5f, Typeface.BOLD, colorTextMuted, Gravity.START).apply {
            setPadding(0, 0, 0, dp(6))
        }

    private fun styledInput(hint: String, prefill: String): EditText = EditText(this).apply {
        setHint(hint)
        setText(prefill)
        background = roundedBgStroke(Color.parseColor("#F8FBFA"), colorFieldBorder, 12f, 1)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setTextColor(colorDark)
    }

    private fun text(t: String, sizeSp: Float, style: Int, color: Int, gravity: Int): TextView = TextView(this).apply {
        text = t; textSize = sizeSp; setTypeface(null, style); setTextColor(color); this.gravity = gravity
    }

    private fun space(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, h) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun glowCircle(size: Int, color: Int, gravityVal: Int, marginTopOrBottom: Int, marginSideVal: Int): View {
        return View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = gravityVal
                if (gravityVal and Gravity.TOP == Gravity.TOP) topMargin = marginTopOrBottom else bottomMargin = marginTopOrBottom
                if (gravityVal and Gravity.END == Gravity.END) rightMargin = marginSideVal else leftMargin = marginSideVal
            }
        }
    }

    private fun roundOutline(radiusDp: Int): ViewOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, dp(radiusDp).toFloat())
        }
    }

    /** সম্পূর্ণ গোলাকার (circle) outline — bKash/Nagad লোগো ব্যাজের জন্য */
    private fun circleOutline(): ViewOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    /** সম্পূর্ণ গোলাকার (circle) background drawable */
    private fun circleBg(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(color)
    }

    private fun roundedBgStroke(fillColor: Int, strokeColor: Int, radiusDp: Float, strokeWidthDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(fillColor)
        setStroke(dp(strokeWidthDp), strokeColor)
    }
}
