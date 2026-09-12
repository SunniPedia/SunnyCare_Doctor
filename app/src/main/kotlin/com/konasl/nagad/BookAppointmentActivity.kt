package com.konasl.nagad

import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Calendar

class BookAppointmentActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorAccent = Color.parseColor("#F59E0B")
    private val colorBg = Color.parseColor("#F4F7F6")
    private val colorTextMuted = Color.parseColor("#6B7280")
    private val colorCard = Color.WHITE

    private val appointmentFee = 800

    data class DateOption(val label: String, val dayNum: Int, val month: String, val isoDate: String)
    data class TimeSlot(val label: String, val value24: String)
    data class PaymentOption(val label: String, val emoji: String)

    private var selectedDate: DateOption? = null
    private var selectedTime24: String? = null
    private var selectedPayment: String? = null

    private lateinit var dateRow: LinearLayout
    private lateinit var timeGrid: GridLayout
    private lateinit var customTimeBtn: TextView
    private lateinit var paymentRow: LinearLayout
    private lateinit var reasonInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var confirmBtn: LinearLayout
    private lateinit var totalFeeText: TextView

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
        PaymentOption("বিকাশ", "📱"),
        PaymentOption("নগদ", "💳"),
        PaymentOption("সরাসরি (ভিজিটে)", "🏥")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(colorBg)
        }
        val scroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(40))
        }

        // ---------------- HEADER ----------------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(colorPrimary, colorPrimaryDark))
            setPadding(dp(22), dp(40), dp(22), dp(46))
        }
        val backRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backBtn = TextView(this).apply {
            text = "←"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, dp(14), 0)
            setOnClickListener { finish() }
        }
        val headerTitle = text("অ্যাপয়েন্টমেন্ট বুক করুন", 18f, Typeface.BOLD, Color.WHITE, Gravity.START)
        backRow.addView(backBtn)
        backRow.addView(headerTitle)

        val headerSub = text(
            "ডা. মাসুম বিল্লাহ সানি • মেডিসিন, শিশু ও ডায়াবেটিস বিশেষজ্ঞ",
            12f, Typeface.NORMAL, Color.argb(215, 255, 255, 255), Gravity.START
        ).apply { setPadding(dp(34), dp(6), 0, 0) }

        header.addView(backRow)
        header.addView(headerSub)

        // ---------------- FEE CARD (overlap) ----------------
        val feeCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(colorCard, 20f)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(-24), dp(18), 0)
            }
            elevation = dp(6).toFloat()
        }
        val feeLeftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        feeLeftCol.addView(text("কনসালটেশন ফি", 12f, Typeface.NORMAL, colorTextMuted, Gravity.START))
        feeLeftCol.addView(
            text("৳ $appointmentFee", 22f, Typeface.BOLD, colorPrimary, Gravity.START).apply {
                setPadding(0, dp(2), 0, 0)
            }
        )
        val feeBadge = text("অনলাইন/সরাসরি পেমেন্ট", 10.5f, Typeface.BOLD, colorAccent, Gravity.CENTER).apply {
            background = roundedBg(Color.parseColor("#FEF3E2"), 30f)
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        feeCard.addView(feeLeftCol)
        feeCard.addView(feeBadge)

        // ---------------- SECTION: DATE ----------------
        val dateSection = sectionTitle("তারিখ নির্বাচন করুন")
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
        val timeSection = sectionTitle("সময় নির্বাচন করুন")
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
        val infoSection = sectionTitle("রোগীর তথ্য")
        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(colorCard, 18f)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(10), dp(18), 0)
            }
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

        // ---------------- SECTION: PAYMENT ----------------
        val paymentSection = sectionTitle("পেমেন্ট পদ্ধতি")
        paymentRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(10), dp(18), 0)
            }
        }
        buildPaymentOptions()

        // ---------------- CONFIRM BUTTON ----------------
        totalFeeText = text("সর্বমোট: ৳ $appointmentFee", 13f, Typeface.BOLD, Color.parseColor("#111827"), Gravity.START)
        val confirmWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), 0)
        }
        confirmWrap.addView(totalFeeText)
        confirmBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(colorPrimary, colorPrimaryDark)).apply {
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
            setOnClickListener { onConfirmClicked() }
        }
        confirmBtn.addView(text("কনফার্ম করুন  ✓", 15f, Typeface.BOLD, Color.WHITE, Gravity.CENTER))
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
                background = roundedBg(colorCard, 16f)
                setPadding(dp(18), dp(14), dp(18), dp(14))
                layoutParams = LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(10)
                }
                tag = option.isoDate
            }
            chip.addView(text(option.label, 11f, Typeface.BOLD, colorTextMuted, Gravity.CENTER))
            chip.addView(
                text("${option.dayNum}", 18f, Typeface.BOLD, Color.parseColor("#111827"), Gravity.CENTER).apply {
                    setPadding(0, dp(4), 0, 0)
                }
            )
            chip.addView(text(option.month, 10.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER))

            chip.setOnClickListener {
                selectedDate = option
                for (i in 0 until dateRow.childCount) {
                    val c = dateRow.getChildAt(i) as LinearLayout
                    val selected = c.tag == option.isoDate
                    c.background = roundedBg(if (selected) colorPrimary else colorCard, 16f)
                    for (j in 0 until c.childCount) {
                        (c.getChildAt(j) as? TextView)?.setTextColor(
                            if (selected) Color.WHITE else if (j == 1) Color.parseColor("#111827") else colorTextMuted
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
            val chip = text(slot.label, 12.5f, Typeface.BOLD, Color.parseColor("#111827"), Gravity.CENTER).apply {
                background = roundedBg(colorCard, 14f)
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
            c.background = roundedBg(if (selected) colorPrimary else colorCard, 14f)
            c.setTextColor(if (selected) Color.WHITE else Color.parseColor("#111827"))
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
                c.background = roundedBg(colorCard, 14f)
                c.setTextColor(Color.parseColor("#111827"))
            }
            customTimeBtn.background = roundedBg(colorPrimary, 14f)
            customTimeBtn.setTextColor(Color.WHITE)
            customTimeBtn.text = "নির্বাচিত সময়: $selectedTime24"
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
    }

    // ------------------------------------------------------------------
    private fun buildPaymentOptions() {
        paymentRow.removeAllViews()
        paymentOptions.forEach { option ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedBg(colorCard, 14f)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(6), 0, dp(6))
                }
                tag = option.label
            }
            row.addView(
                text(option.emoji, 18f, Typeface.NORMAL, Color.BLACK, Gravity.CENTER).apply {
                    setPadding(0, 0, dp(14), 0)
                }
            )
            row.addView(
                text(option.label, 13f, Typeface.BOLD, Color.parseColor("#111827"), Gravity.START).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            row.addView(text("○", 16f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER))

            row.setOnClickListener {
                selectedPayment = option.label
                for (i in 0 until paymentRow.childCount) {
                    val r = paymentRow.getChildAt(i) as LinearLayout
                    val selected = r.tag == option.label
                    r.background = roundedBg(if (selected) Color.parseColor("#E4F3F1") else colorCard, 14f)
                    val check = r.getChildAt(2) as TextView
                    check.text = if (selected) "●" else "○"
                    check.setTextColor(if (selected) colorPrimary else colorTextMuted)
                }
            }
            paymentRow.addView(row)
        }
    }

    // ------------------------------------------------------------------
    private fun onConfirmClicked() {
        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val reason = reasonInput.text.toString().trim()

        if (selectedDate == null) { Toast.makeText(this, "তারিখ নির্বাচন করুন", Toast.LENGTH_SHORT).show(); return }
        if (selectedTime24 == null) { Toast.makeText(this, "সময় নির্বাচন করুন", Toast.LENGTH_SHORT).show(); return }
        if (name.isEmpty() || phone.isEmpty()) { Toast.makeText(this, "নাম ও ফোন নাম্বার দিন", Toast.LENGTH_SHORT).show(); return }
        if (reason.isEmpty()) { Toast.makeText(this, "সমস্যার বিবরণ দিন", Toast.LENGTH_SHORT).show(); return }
        if (selectedPayment == null) { Toast.makeText(this, "পেমেন্ট পদ্ধতি নির্বাচন করুন", Toast.LENGTH_SHORT).show(); return }

        val patientId = SupabaseClient.getPatientId(this)
        if (patientId == null) {
            Toast.makeText(this, "সেশন পাওয়া যায়নি, আবার লগইন করুন", Toast.LENGTH_SHORT).show(); return
        }

        confirmBtn.isEnabled = false
        lifecycleScope.launch {
            val result = SupabaseClient.createAppointment(
                patientId = patientId,
                patientName = name,
                phone = phone,
                reason = reason,
                date = selectedDate!!.isoDate,
                time = selectedTime24!!,
                paymentMethod = selectedPayment!!,
                fee = appointmentFee
            )
            result.onSuccess {
                Toast.makeText(this@BookAppointmentActivity, "অ্যাপয়েন্টমেন্ট বুক হয়েছে", Toast.LENGTH_SHORT).show()
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
    private fun sectionTitle(title: String): TextView =
        text(title, 14.5f, Typeface.BOLD, Color.parseColor("#111827"), Gravity.START).apply {
            setPadding(dp(18), dp(22), dp(18), 0)
        }

    private fun fieldLabel(label: String): TextView =
        text(label, 11.5f, Typeface.BOLD, colorTextMuted, Gravity.START).apply {
            setPadding(0, 0, 0, dp(6))
        }

    private fun styledInput(hint: String, prefill: String): EditText = EditText(this).apply {
        setHint(hint)
        setText(prefill)
        background = roundedBg(Color.parseColor("#F1F5F4"), 12f)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setTextColor(Color.parseColor("#111827"))
    }

    private fun text(t: String, sizeSp: Float, style: Int, color: Int, gravity: Int): TextView = TextView(this).apply {
        text = t; textSize = sizeSp; setTypeface(null, style); setTextColor(color); this.gravity = gravity
    }

    private fun space(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, h) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(color)
    }
}
