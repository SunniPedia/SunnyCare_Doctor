package com.konasl.nagad

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.text.TextUtils
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
import kotlinx.coroutines.Job
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
    private data class PaymentRowRefs(
        val row: LinearLayout,
        val option: PaymentOption,
        val check: HomeActivity.VectorIconDrawable,
        val logo: CircularImageView
    )

    private var selectedDate: DateOption? = null
    private var selectedTime24: String? = null
    private var selectedPayment: String? = null

    // নির্বাচিত তারিখে Supabase-এ ইতিমধ্যে বুক হয়ে থাকা সময়গুলোর সেট — এগুলো UI থেকে হাইড করা হয়
    private var bookedTimesForDate: MutableSet<String> = mutableSetOf()
    private var bookingFetchJob: Job? = null

    // প্রতিটা booked-times ফেচ রিকোয়েস্টের নিজস্ব id থাকে। কোনো পুরনো (আগের তারিখের)
    // রিকোয়েস্ট দেরিতে ফলাফল ফিরিয়ে বর্তমান তারিখের ডেটা ওভাররাইট করে ফেলতে না পারে,
    // সেজন্য শুধু সর্বশেষ রিকোয়েস্টের ফলাফলই গ্রহণ করা হয় — এটাই "আজ ১০টা বুক করলে
    // কালকের ১০টাও গায়েব দেখানো"-র মতো cross-date leak ঠেকানোর মূল সুরক্ষা।
    private var bookingFetchRequestId: Long = 0L

    private lateinit var dateRow: LinearLayout
    private lateinit var timeGrid: GridLayout
    private lateinit var timeLoadingText: TextView
    private lateinit var customTimeBtn: TextView
    private lateinit var paymentRow: LinearLayout
    private lateinit var manualPayCard: LinearLayout
    private lateinit var manualPayNumberText: TextView
    private lateinit var manualPayInstructionText: TextView
    private lateinit var manualPayLogoView: CircularImageView
    private lateinit var trxIdInput: EditText
    private lateinit var reasonInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var confirmBtn: LinearLayout
    private lateinit var totalFeeText: TextView

    // ---------------- একাধিক টেস্ট রিপোর্ট আপলোড ----------------
    private lateinit var reportPickRow: LinearLayout
    private lateinit var reportPickText: TextView
    private lateinit var reportPickSubtext: TextView
    private lateinit var reportListContainer: LinearLayout
    private lateinit var reportPickLauncher: ActivityResultLauncher<Array<String>>
    private val selectedReports = mutableListOf<Pair<Uri, String>>()

    private val paymentRowViews = mutableListOf<PaymentRowRefs>()

    private val dateOptions = mutableListOf<DateOption>()

    // সকাল ১০:০০ থেকে রাত ৮:০০ পর্যন্ত প্রতি ৩০ মিনিট অন্তর টাইম স্লট (মোট ২১টি স্লট),
    // generateTimeSlots() ফাংশন দিয়ে স্বয়ংক্রিয়ভাবে তৈরি হয়
    private val timeSlots = generateTimeSlots()

    private val paymentOptions = listOf(
        PaymentOption("বিকাশ", "সেন্ড মানি করে বুক করুন", bkashLogoUrl, Color.parseColor("#E2136E"), bkashMerchantNumber),
        PaymentOption("নগদ", "সেন্ড মানি করে বুক করুন", nagadLogoUrl, Color.parseColor("#F42534"), nagadMerchantNumber)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // একাধিক ডকুমেন্ট (ছবি/PDF) একসাথে সিলেক্ট করার লঞ্চার
        reportPickLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) { /* কিছু ডকুমেন্ট প্রোভাইডার persistable permission সাপোর্ট করে না, সমস্যা নেই */ }
                    if (selectedReports.none { it.first == uri }) {
                        val name = queryDisplayName(uri) ?: "ডকুমেন্ট ${selectedReports.size + 1}"
                        selectedReports.add(uri to name)
                    }
                }
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
        // ইমুজি/টেক্সট অ্যারো নয় — সম্পূর্ণ ভেক্টর-আঁকা (Canvas দিয়ে) ব্যাক আইকন, তাই যেকোনো সাইজে ঝকঝকে দেখাবে
        val backBtn = ImageView(this).apply {
            setImageDrawable(BackArrowDrawable(Color.WHITE, dp(2).toFloat()))
            background = roundedBg(Color.argb(46, 255, 255, 255), 30f)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            val pad = dp(9)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            contentDescription = "পেছনে যান"
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

        // ---------------- SECTION: TIME ----------------
        // FIX: timeGrid/timeLoadingText/customTimeBtn এখন buildDateOptions() কল করার
        // *আগে* initialize করা হচ্ছে, কারণ buildDateOptions() প্রথম ডেট চিপ অটো-সিলেক্ট
        // (performClick) করে, যেটা fetchBookedTimesAndRefresh -> highlightSelectedTime
        // এর মাধ্যমে সরাসরি timeGrid অ্যাক্সেস করে। আগে এই অর্ডারটা উল্টো থাকায়
        // timeGrid lateinit-uninitialized অবস্থায় অ্যাক্সেস হয়ে ক্র্যাশ করত।
        val timeSection = sectionTitle(HomeActivity.VectorIconDrawable.IconType.CLOCK, "সময় নির্বাচন করুন")
        timeLoadingText = text("তারিখের জন্য খালি সময় যাচাই করা হচ্ছে...", 11f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(10), dp(18), 0)
            }
            visibility = View.GONE
        }
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

        // timeGrid প্রস্তুত হওয়ার পরই এখন buildDateOptions() কল হচ্ছে, যাতে প্রথম
        // তারিখ অটো-সিলেক্ট হওয়ার সময় (chip.performClick()) কোনো ক্র্যাশ না হয়।
        buildDateOptions()

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
        infoCard.addView(fieldLabel("আগের টেস্ট রিপোর্ট (ঐচ্ছিক, একাধিক ফাইল যোগ করা যাবে)"))
        infoCard.addView(buildReportPickSection())

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
        manualPayLogoView = CircularImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            circleBackgroundColor = Color.WHITE
            borderColor = Color.parseColor("#FBE3B8")
            borderWidthDp = 1f
            elevation = dp(1).toFloat()
        }
        manualPayInstructionText = text(
            "নিচের নাম্বারে Send Money করে Transaction ID টি নিচে লিখুন",
            11.5f, Typeface.NORMAL, colorTextMuted, Gravity.START
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        manualHeadRow.addView(manualPayLogoView)
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
        page.addView(timeLoadingText)
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
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(10)
                }
                elevation = dp(1).toFloat()
                outlineProvider = roundOutline(16)
                clipToOutline = true
                tag = option.isoDate
            }
            chip.addView(text(option.label, 10.5f, Typeface.BOLD, colorTextMuted, Gravity.CENTER).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            chip.addView(
                text(toBnDigits(option.dayNum), 18f, Typeface.BOLD, colorDark, Gravity.CENTER).apply {
                    setPadding(0, dp(5), 0, dp(1))
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
                    c.elevation = if (selected) dp(3).toFloat() else dp(1).toFloat()
                    for (j in 0 until c.childCount) {
                        (c.getChildAt(j) as? TextView)?.setTextColor(
                            if (selected) Color.WHITE else if (j == 1) colorDark else colorTextMuted
                        )
                    }
                }
                // এই তারিখে Supabase-এ ইতিমধ্যে বুক থাকা সময়গুলো যাচাই করে সেগুলো হাইড করে
                fetchBookedTimesAndRefresh(option.isoDate)
            }
            if (index == 0) chip.performClick()
            dateRow.addView(chip)
        }
    }

    /** আজ থেকে শুরু করে পরবর্তী ১০ দিনের তারিখ — প্রতিবার Activity খুললে Calendar থেকে ফ্রেশ জেনারেট হয়, তাই সবসময় আপডেটেড থাকে */
    private fun generateDateOptions(): List<DateOption> {
        val quickLabels = arrayOf("আজ", "আগামীকাল")
        val weekDays = arrayOf("রবি", "সোম", "মঙ্গল", "বুধ", "বৃহঃ", "শুক্র", "শনি")
        val months = arrayOf("জানু", "ফেব্রু", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্ট", "অক্টো", "নভে", "ডিসে")
        val result = mutableListOf<DateOption>()
        for (i in 0..9) {
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

    /** ইংরেজি সংখ্যাকে বাংলা সংখ্যায় রূপান্তর করে, যাতে তারিখের UI বাকি অ্যাপের সাথে সামঞ্জস্যপূর্ণ ও প্রফেশনাল দেখায় */
    private fun toBnDigits(n: Int): String {
        val bn = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        return n.toString().map { c -> if (c.isDigit()) bn[c - '0'] else c }.joinToString("")
    }

    /** টাইম-স্লট লেবেলের মতো স্ট্রিং (যেমন "04:30")-এর ভেতরের সংখ্যাগুলোকে বাংলা সংখ্যায় রূপান্তর করে */
    private fun toBnDigits(s: String): String {
        val bn = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        return s.map { c -> if (c.isDigit()) bn[c - '0'] else c }.joinToString("")
    }

    /**
     * সকাল ১০:০০ থেকে রাত ৮:০০ (20:00) পর্যন্ত প্রতি ৩০ মিনিট অন্তর একটা করে TimeSlot তৈরি করে।
     * প্রতিটা স্লটের value24 ("HH:mm", ২৪-ঘণ্টা ফরম্যাট) Supabase-এ বুকিং তুলনার জন্য ব্যবহৃত হয়,
     * আর label বাংলা AM/PM প্রিফিক্স ও বাংলা সংখ্যাসহ ইউজারকে দেখানো হয়।
     */
    private fun generateTimeSlots(): List<TimeSlot> {
        val result = mutableListOf<TimeSlot>()
        val startMinutes = 10 * 60   // সকাল ১০:০০
        val endMinutes = 20 * 60     // রাত ৮:০০ (20:00)
        var totalMinutes = startMinutes
        while (totalMinutes <= endMinutes) {
            val hour = totalMinutes / 60
            val minute = totalMinutes % 60
            val period = when {
                hour in 10..11 -> "সকাল"
                hour in 12..14 -> "দুপুর"
                hour in 15..17 -> "বিকাল"
                hour == 18 -> "সন্ধ্যা"
                else -> "রাত" // 19, 20
            }
            val displayHour = if (hour > 12) hour - 12 else hour
            val displayTime = "%02d:%02d".format(displayHour, minute)
            val value24 = "%02d:%02d".format(hour, minute)
            result.add(TimeSlot("$period ${toBnDigits(displayTime)}", value24))
            totalMinutes += 30
        }
        return result
    }

    /** আজকের তারিখ "yyyy-MM-dd" ফরম্যাটে — DateOption.isoDate-এর সাথে মেলানোর জন্য */
    private fun todayIsoDate(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    /**
     * নির্বাচিত তারিখ আজকের হলে এবং দেওয়া value24 (HH:mm) সময়টা বর্তমান সময়ের আগে হলে true রিটার্ন করে,
     * অর্থাৎ এই স্লটটা এখন আর বুক করা সম্ভব না বলে গণ্য হবে। আজকের বাইরের অন্য যেকোনো তারিখের জন্য
     * সবসময় false — ভবিষ্যতের তারিখের কোনো স্লট কখনো "অতীত" হিসেবে হাইড হবে না।
     */
    private fun isPastTimeSlot(value24: String): Boolean {
        val date = selectedDate ?: return false
        if (date.isoDate != todayIsoDate()) return false
        val parts = value24.split(":")
        if (parts.size != 2) return false
        val slotHour = parts[0].toIntOrNull() ?: return false
        val slotMinute = parts[1].toIntOrNull() ?: return false
        val now = Calendar.getInstance()
        val slotCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, slotHour)
            set(Calendar.MINUTE, slotMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return slotCal.before(now)
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
            val chosen = "%02d:%02d".format(h, min)
            if (bookedTimesForDate.contains(chosen)) {
                Toast.makeText(this, "এই সময়ে ইতিমধ্যে অ্যাপয়েন্টমেন্ট বুক করা আছে, অন্য একটা সময় বেছে নিন", Toast.LENGTH_SHORT).show()
                return@TimePickerDialog
            }
            if (isPastTimeSlot(chosen)) {
                Toast.makeText(this, "এই সময়টা ইতিমধ্যে পার হয়ে গেছে, অন্য একটা সময় বেছে নিন", Toast.LENGTH_SHORT).show()
                return@TimePickerDialog
            }
            selectedTime24 = chosen
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
    // নির্বাচিত তারিখের জন্য Supabase থেকে ইতিমধ্যে বুক থাকা সময়গুলো আনা ও UI আপডেট করা
    // ------------------------------------------------------------------
    private fun fetchBookedTimesAndRefresh(isoDate: String) {
        bookingFetchJob?.cancel()

        // এই কলটার নিজস্ব id — নেটওয়ার্ক রেসপন্স ফিরে আসার সময় এটা মিলিয়ে দেখা হবে,
        // যাতে ব্যবহারকারী ততক্ষণে অন্য তারিখে চলে গেলে পুরনো রিকোয়েস্টের ফলাফল আর প্রয়োগ না হয়
        val requestId = ++bookingFetchRequestId

        // তারিখ পরিবর্তন হলে আগের সময় নির্বাচন বাতিল করে দেওয়া হয়, কারণ প্রতিটা তারিখের খালি সময় ভিন্ন হতে পারে
        selectedTime24 = null
        highlightSelectedTime(null)

        // ডেটা আসা পর্যন্ত বাকি সব স্লট দৃশ্যমান রাখা হয়, তবে যেসব স্লটের সময় ইতিমধ্যে পার হয়ে গেছে
        // (শুধু আজকের তারিখের ক্ষেত্রে প্রযোজ্য) সেগুলো নেটওয়ার্ক রেসপন্সের অপেক্ষা না করেই সরাসরি হাইড করা হয়
        for (i in 0 until timeGrid.childCount) {
            val c = timeGrid.getChildAt(i)
            val value = c.tag as? String
            c.visibility = if (value != null && isPastTimeSlot(value)) View.GONE else View.VISIBLE
        }
        timeLoadingText.visibility = View.VISIBLE

        bookingFetchJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { SupabaseClient.getBookedTimes(isoDate) }

            // এই সময়ের মধ্যে ইউজার অন্য কোনো তারিখ সিলেক্ট করে ফেললে requestId বেড়ে যায়,
            // তখন এই পুরনো রেসপন্সটা চুপচাপ উপেক্ষা করা হয় — এটাই cross-date leak ঠেকানোর গার্ড
            if (requestId != bookingFetchRequestId) return@launch

            timeLoadingText.visibility = View.GONE
            result.onSuccess { booked ->
                bookedTimesForDate = booked.toMutableSet()
                applyBookedTimesFilter()
            }.onFailure {
                // ইন্টারনেট/সার্ভার সমস্যায় চুপচাপ সব (অতীত ছাড়া) স্লট খোলা রাখা হয়, যেন ইউজার আটকে না যায়
                bookedTimesForDate = mutableSetOf()
                applyBookedTimesFilter()
            }
        }
    }

    /**
     * নির্দিষ্ট তারিখে Supabase-এ যেসব সময় ইতিমধ্যে বুক হয়ে আছে, এবং (আজকের তারিখ হলে) যেসব সময়
     * ইতিমধ্যে পার হয়ে গেছে — এই দুই ধরনের স্লট টাইম-গ্রিড থেকে হাইড (gone) করে দেয়
     */
    private fun applyBookedTimesFilter() {
        for (i in 0 until timeGrid.childCount) {
            val c = timeGrid.getChildAt(i) as TextView
            val value = c.tag as? String ?: continue
            val hiddenByBooking = bookedTimesForDate.contains(value)
            val hiddenByPastTime = isPastTimeSlot(value)
            c.visibility = if (hiddenByBooking || hiddenByPastTime) View.GONE else View.VISIBLE
        }
    }

    // ------------------------------------------------------------------
    // টেস্ট রিপোর্ট আপলোড (ঐচ্ছিক, একাধিক ফাইল) — ছবি বা PDF, Supabase Storage-এ যায়
    // ------------------------------------------------------------------
    private fun buildReportPickSection(): LinearLayout {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

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
        reportPickSubtext = text("এক বা একাধিক ছবি/PDF সিলেক্ট করুন (ঐচ্ছিক)", 10.5f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
            setPadding(0, dp(2), 0, 0)
        }
        textCol.addView(reportPickText)
        textCol.addView(reportPickSubtext)

        val addIcon = ImageView(this).apply {
            setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.ARROW_RIGHT, colorPrimary, dp(14)))
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
        }

        reportPickRow.addView(iconWrap)
        reportPickRow.addView(textCol)
        reportPickRow.addView(addIcon)

        reportListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }

        wrap.addView(reportPickRow)
        wrap.addView(reportListContainer)
        return wrap
    }

    private fun updateReportPickUi() {
        if (selectedReports.isEmpty()) {
            reportPickText.text = "রিপোর্ট আপলোড করুন"
            reportPickSubtext.text = "এক বা একাধিক ছবি/PDF সিলেক্ট করুন (ঐচ্ছিক)"
            reportPickSubtext.setTextColor(colorTextMuted)
            reportPickRow.background = roundedBgStroke(Color.parseColor("#F8FBFA"), colorFieldBorder, 12f, 1)
        } else {
            reportPickText.text = "আরও ডকুমেন্ট যোগ করুন"
            reportPickSubtext.text = "${selectedReports.size}টি ফাইল সিলেক্ট করা হয়েছে"
            reportPickSubtext.setTextColor(colorPrimary)
            reportPickRow.background = roundedBgStroke(Color.parseColor("#E4F3F1"), colorPrimary, 12f, 1)
        }

        reportListContainer.removeAllViews()
        selectedReports.forEachIndexed { index, pair ->
            reportListContainer.addView(reportFileRow(pair.second, index))
            if (index != selectedReports.lastIndex) reportListContainer.addView(space(dp(6)))
        }
    }

    private fun reportFileRow(name: String, index: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBgStroke(colorCard, colorFieldBorder, 10f, 1)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            addView(ImageView(this@BookAppointmentActivity).apply {
                setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.DOCUMENT, colorPrimary, dp(13)))
                background = roundedBg(Color.parseColor("#E4F3F1"), 7f)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
                setPadding(dp(6), dp(6), dp(6), dp(6))
            })
            addView(text(name, 11.5f, Typeface.NORMAL, colorDark, Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) }
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(ImageView(this@BookAppointmentActivity).apply {
                setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.TRASH, Color.parseColor("#DC2626"), dp(13)))
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
                setPadding(dp(6), dp(6), dp(6), dp(6))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (index < selectedReports.size) selectedReports.removeAt(index)
                    updateReportPickUi()
                }
            })
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

    /** সিলেক্ট করা একটা ফাইল পড়ে Supabase Storage-এ আপলোড করে, পাবলিক URL রিটার্ন করে */
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

            // bKash/Nagad লোগো — CircularImageView দিয়ে, সবসময় নিখুঁত গোলাকার (প্লেসহোল্ডার অবস্থায়ও, লোড হওয়ার পরও)
            val logoView = CircularImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
                circleBackgroundColor = Color.WHITE
                borderColor = Color.parseColor("#E7ECEA")
                borderWidthDp = 1f
                elevation = dp(1).toFloat()
            }
            loadNetworkImage(option.logoUrl, logoView)

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

            row.addView(logoView)
            row.addView(col)
            row.addView(checkIcon)

            row.setOnClickListener {
                selectedPayment = option.label
                paymentRowViews.forEach { refs ->
                    val selected = refs.option.label == option.label
                    refs.row.background = if (selected)
                        roundedBgStroke(Color.argb(20, Color.red(refs.option.brandColor), Color.green(refs.option.brandColor), Color.blue(refs.option.brandColor)), refs.option.brandColor, 16f, 2)
                    else
                        roundedBgStroke(colorCard, colorFieldBorder, 16f, 1)
                    refs.check.updateTint(if (selected) refs.option.brandColor else Color.parseColor("#D1D5DB"))
                    refs.logo.borderColor = if (selected) refs.option.brandColor else Color.parseColor("#E7ECEA")
                    refs.logo.borderWidthDp = if (selected) 2f else 1f
                }
                updateManualPayCard(option)
            }
            paymentRow.addView(row)
            paymentRowViews.add(PaymentRowRefs(row, option, checkDrawable, logoView))
        }
    }

    /** bKash/Nagad যেটাই সিলেক্ট করা হোক, মার্চেন্ট নাম্বার + লোগো + TrxID ইনপুট দেখায় */
    private fun updateManualPayCard(option: PaymentOption) {
        manualPayNumberText.text = option.merchantNumber
        manualPayInstructionText.text =
            "${option.label}-এ (Send Money) ৳$appointmentFee পাঠিয়ে Transaction ID টি নিচে লিখুন"
        manualPayLogoView.borderColor = option.brandColor
        loadNetworkImage(option.logoUrl, manualPayLogoView)
        manualPayCard.visibility = View.VISIBLE
    }

    /** নেটওয়ার্ক থেকে বিকাশ/নগদ লোগো ইমেজ লোড করে CircularImageView-তে বসায় (কোনো তৃতীয়-পক্ষ ইমেজ লাইব্রেরি ছাড়াই) */
    private fun loadNetworkImage(url: String, target: CircularImageView) {
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
    private fun onConfirmClicked() {
        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val reason = reasonInput.text.toString().trim()
        val trxId = trxIdInput.text.toString().trim()

        if (selectedDate == null) { Toast.makeText(this, "তারিখ নির্বাচন করুন", Toast.LENGTH_SHORT).show(); return }
        if (selectedTime24 == null) { Toast.makeText(this, "সময় নির্বাচন করুন", Toast.LENGTH_SHORT).show(); return }
        if (bookedTimesForDate.contains(selectedTime24)) {
            Toast.makeText(this, "এই সময়ে ইতিমধ্যে অ্যাপয়েন্টমেন্ট বুক হয়ে গেছে, অন্য একটা সময় বেছে নিন", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedTime24?.let { isPastTimeSlot(it) } == true) {
            Toast.makeText(this, "এই সময়টা ইতিমধ্যে পার হয়ে গেছে, অন্য একটা সময় বেছে নিন", Toast.LENGTH_SHORT).show()
            return
        }
        if (name.isEmpty() || phone.isEmpty()) { Toast.makeText(this, "নাম ও ফোন নাম্বার দিন", Toast.LENGTH_SHORT).show(); return }
        if (reason.isEmpty()) { Toast.makeText(this, "সমস্যার বিবরণ দিন", Toast.LENGTH_SHORT).show(); return }
        if (selectedPayment == null) { Toast.makeText(this, "পেমেন্ট পদ্ধতি নির্বাচন করুন", Toast.LENGTH_SHORT).show(); return }
        if (trxId.isEmpty()) { Toast.makeText(this, "Transaction ID লিখুন", Toast.LENGTH_SHORT).show(); return }

        val patientId = SupabaseClient.getPatientId(this)
        if (patientId == null) {
            Toast.makeText(this, "সেশন পাওয়া যায়নি, আবার লগইন করুন", Toast.LENGTH_SHORT).show(); return
        }

        confirmBtn.isEnabled = false
        val reportsToUpload = selectedReports.toList()
        if (reportsToUpload.isNotEmpty()) {
            Toast.makeText(this, "রিপোর্ট আপলোড হচ্ছে (${reportsToUpload.size}টি ফাইল)...", Toast.LENGTH_SHORT).show()
        }
        lifecycleScope.launch {
            // শেষ মুহূর্তে আরেকজন একই সময় বুক করে ফেলেছে কিনা তা নিশ্চিত করার জন্য একবার আবার যাচাই করা হয়
            val recheck = SupabaseClient.getBookedTimes(selectedDate!!.isoDate)
            val nowBooked = recheck.getOrNull()?.toSet() ?: emptySet()
            if (nowBooked.contains(selectedTime24)) {
                confirmBtn.isEnabled = true
                bookedTimesForDate = nowBooked.toMutableSet()
                applyBookedTimesFilter()
                Toast.makeText(this@BookAppointmentActivity, "দুঃখিত, এই সময়টা এইমাত্র বুক হয়ে গেছে। অন্য একটা সময় বেছে নিন", Toast.LENGTH_LONG).show()
                return@launch
            }

            val uploadedUrls = mutableListOf<String>()
            for ((uri, fileName) in reportsToUpload) {
                val uploadResult = uploadSelectedReport(uri, fileName)
                if (uploadResult.isFailure) {
                    confirmBtn.isEnabled = true
                    Toast.makeText(
                        this@BookAppointmentActivity,
                        "\"$fileName\" আপলোড ব্যর্থ: ${uploadResult.exceptionOrNull()?.message ?: "আবার চেষ্টা করুন"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                uploadResult.getOrNull()?.let { uploadedUrls.add(it) }
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
                reportUrl = uploadedUrls.joinToString(",")
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

    // ------------------------------------------------------------------
    // BackArrowDrawable — ব্যাক বাটনের জন্য সম্পূর্ণ ভেক্টর-আঁকা (Canvas/Paint দিয়ে) অ্যারো আইকন।
    // কোনো ইমুজি বা ফন্ট-ক্যারেক্টার নয়, তাই সব ডিভাইস/ফন্টে একইরকম নিখুঁত ও প্রফেশনাল দেখায়।
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

            // মূল অনুভূমিক রেখা
            canvas.drawLine(left, cy, right, cy, paint)
            // চেভরন হেড (বাম দিকে নির্দেশক)
            canvas.drawLine(left, cy, left + armLen, cy - armLen, paint)
            canvas.drawLine(left, cy, left + armLen, cy + armLen, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // ------------------------------------------------------------------
    // CircularImageView — bKash/Nagad লোগোর জন্য সবসময় নিখুঁত গোলাকার ইমেজ আঁকে
    // (BitmapShader দিয়ে ক্যানভাসে সরাসরি বৃত্তে ক্লিপ করা হয়, তাই placeholder সাদা
    // বৃত্ত এবং নেটওয়ার্ক থেকে লোড হওয়া আসল লোগো — দুই অবস্থাতেই শেইপ গোল থাকে)
    // ------------------------------------------------------------------
    class CircularImageView(context: Context) : View(context) {

        private var sourceBitmap: Bitmap? = null
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        var circleBackgroundColor: Int = Color.WHITE
            set(value) { field = value; invalidate() }

        var borderColor: Int = Color.TRANSPARENT
            set(value) { field = value; invalidate() }

        var borderWidthDp: Float = 0f
            set(value) { field = value; invalidate() }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        fun setImageBitmap(bitmap: Bitmap?) {
            sourceBitmap = bitmap
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val borderW = borderWidthDp * resources.displayMetrics.density
            val radius = (minOf(w, h) - borderW) / 2f
            val cx = w / 2f
            val cy = h / 2f

            bgPaint.color = circleBackgroundColor
            canvas.drawCircle(cx, cy, radius, bgPaint)

            val bmp = sourceBitmap
            if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val matrix = Matrix()
                val scale: Float
                var dx = 0f
                var dy = 0f
                if (bmp.width.toFloat() * (radius * 2f) > (radius * 2f) * bmp.height.toFloat()) {
                    scale = (radius * 2f) / bmp.height.toFloat()
                    dx = ((radius * 2f) - bmp.width.toFloat() * scale) * 0.5f
                } else {
                    scale = (radius * 2f) / bmp.width.toFloat()
                    dy = ((radius * 2f) - bmp.height.toFloat() * scale) * 0.5f
                }
                matrix.setScale(scale, scale)
                matrix.postTranslate(dx + cx - radius, dy + cy - radius)
                shader.setLocalMatrix(matrix)
                imagePaint.shader = shader
                canvas.drawCircle(cx, cy, radius, imagePaint)
            }

            if (borderW > 0f && borderColor != Color.TRANSPARENT) {
                borderPaint.color = borderColor
                borderPaint.strokeWidth = borderW
                canvas.drawCircle(cx, cy, radius - borderW / 2f, borderPaint)
            }
        }
    }
}
