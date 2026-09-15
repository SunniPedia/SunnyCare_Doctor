package com.konasl.nagad

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.graphics.Outline
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ============================================================================
 * Admin.kt — এডমিন প্যানেল
 * ============================================================================
 * HomeActivity তে ডাক্তারের অ্যাভাটারে ক্লিক করলে (শুধুমাত্র SupabaseClient.isAdmin()
 * true হলে) এই একটিভিটি ওপেন হয়। এখানে SupabaseClient.kt এর সব ডেটা এডমিন
 * সুন্দরভাবে সাজানো অবস্থায় দেখতে, আপডেট করতে ও ডিলিট করতে পারেন:
 *
 *  ট্যাব ১ — অ্যাপয়েন্টমেন্ট: সব রোগীর সব অ্যাপয়েন্টমেন্ট, status/payment_status/
 *            slot_open/fee এডিট করা ও ডিলিট করা যায়
 *  ট্যাব ২ — রোগী: সব রোগীর প্রোফাইল তথ্য দেখা, এডিট করা, PIN রিসেট করা,
 *            ডিভাইস রিসেট করা (সিম/মোবাইল হারালে নতুন ডিভাইসে লগইনের জন্য),
 *            অ্যাকাউন্ট ডিলিট করা যায়
 *  ট্যাব ৩ — টাইম স্লট: প্রতিটি সময়-স্লট গ্লোবালি চালু/বন্ধ করা যায়, এবং
 *            নির্দিষ্ট তারিখের জন্য আলাদা ওভাররাইডও দেওয়া যায়
 *  ট্যাব ৪ — OTP পুল: available/assigned/verified কোডের সংখ্যা দেখা, নতুন কোড
 *            বাল্কে যোগ করা এবং ব্যবহৃত কোড রিসেট করা যায়
 * ============================================================================
 */
class AdminActivity : AppCompatActivity() {

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

    private enum class Tab { APPOINTMENTS, PATIENTS, SLOTS, OTP }
    private var currentTab = Tab.APPOINTMENTS

    private lateinit var appointmentsPanel: View
    private lateinit var patientsPanel: View
    private lateinit var slotsPanel: View
    private lateinit var otpPanel: View

    private lateinit var appointmentsContainer: LinearLayout
    private lateinit var patientsContainer: LinearLayout
    private lateinit var slotsContainer: LinearLayout
    private lateinit var otpStatsContainer: LinearLayout

    private data class NavItemViews(
        val root: LinearLayout,
        val pill: LinearLayout,
        val icon: ImageView,
        val iconDrawable: HomeActivity.VectorIconDrawable,
        val label: TextView
    )

    private lateinit var navAppointments: NavItemViews
    private lateinit var navPatients: NavItemViews
    private lateinit var navSlots: NavItemViews
    private lateinit var navOtp: NavItemViews

    // ডিফল্ট সময়-স্লট লিস্ট (সকাল ১০টা থেকে রাত ৮টা, ৩০ মিনিট পর পর)
    private val defaultTimeSlots: List<String> by lazy {
        val list = mutableListOf<String>()
        var h = 10
        var m = 0
        while (h < 20 || (h == 20 && m == 0)) {
            list.add(String.format("%02d:%02d", h, m))
            m += 30
            if (m >= 60) { m = 0; h += 1 }
        }
        list
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // শুধুমাত্র অ্যাডমিন এই একটিভিটিতে ঢুকতে পারবেন
        if (!SupabaseClient.isLoggedIn(this) || !SupabaseClient.isAdmin(this)) {
            Toast.makeText(this, "এই পাতাটি শুধুমাত্র এডমিনের জন্য", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F8FBFA"), colorBg)
            )
        }

        val panelsHost = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // ---------------- ট্যাব ১: অ্যাপয়েন্টমেন্ট ----------------
        val apptScroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val apptContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(110))
        }
        apptContent.addView(panelTopBar("এডমিন প্যানেল", "সব অ্যাপয়েন্টমেন্ট পর্যবেক্ষণ ও ম্যানেজমেন্ট"))
        appointmentsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), 0)
        }
        apptContent.addView(appointmentsContainer)
        apptScroll.addView(apptContent)
        appointmentsPanel = apptScroll

        // ---------------- ট্যাব ২: রোগী ----------------
        val patientScroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
        }
        val patientContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(110))
        }
        patientContent.addView(panelTopBar("রোগীর তালিকা", "সব রোগীর প্রোফাইল দেখুন ও ম্যানেজ করুন"))
        patientsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), 0)
        }
        patientContent.addView(patientsContainer)
        patientScroll.addView(patientContent)
        patientsPanel = patientScroll

        // ---------------- ট্যাব ৩: টাইম স্লট ----------------
        val slotsScroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
        }
        val slotsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(110))
        }
        slotsContent.addView(panelTopBar("টাইম স্লট", "কোন সময়ে সিরিয়াল নেওয়া যাবে তা নিয়ন্ত্রণ করুন"))
        slotsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), 0)
        }
        slotsContent.addView(slotsContainer)
        slotsScroll.addView(slotsContent)
        slotsPanel = slotsScroll

        // ---------------- ট্যাব ৪: OTP পুল ----------------
        val otpScroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
        }
        val otpContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(110))
        }
        otpContent.addView(panelTopBar("OTP পুল", "লগইনের জন্য ব্যবহৃত OTP কোডের পুল পরিচালনা করুন"))
        otpStatsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), 0)
        }
        otpContent.addView(otpStatsContainer)
        otpScroll.addView(otpContent)
        otpPanel = otpScroll

        panelsHost.addView(appointmentsPanel)
        panelsHost.addView(patientsPanel)
        panelsHost.addView(slotsPanel)
        panelsHost.addView(otpPanel)

        val bottomNav = buildBottomNav()

        root.addView(panelsHost)
        root.addView(bottomNav)
        setContentView(root)

        loadAppointments()
        updateNavStyle()
    }

    override fun onResume() {
        super.onResume()
        when (currentTab) {
            Tab.APPOINTMENTS -> loadAppointments()
            Tab.PATIENTS -> loadPatients()
            Tab.SLOTS -> loadSlots()
            Tab.OTP -> loadOtpStats()
        }
    }

    // ------------------------------------------------------------------
    // ট্যাব সুইচিং + বটম ন্যাভ
    // ------------------------------------------------------------------
    private fun switchTab(tab: Tab) {
        currentTab = tab
        appointmentsPanel.visibility = if (tab == Tab.APPOINTMENTS) View.VISIBLE else View.GONE
        patientsPanel.visibility = if (tab == Tab.PATIENTS) View.VISIBLE else View.GONE
        slotsPanel.visibility = if (tab == Tab.SLOTS) View.VISIBLE else View.GONE
        otpPanel.visibility = if (tab == Tab.OTP) View.VISIBLE else View.GONE
        updateNavStyle()
        when (tab) {
            Tab.APPOINTMENTS -> loadAppointments()
            Tab.PATIENTS -> loadPatients()
            Tab.SLOTS -> loadSlots()
            Tab.OTP -> loadOtpStats()
        }
    }

    private fun updateNavStyle() {
        listOf(
            navAppointments to Tab.APPOINTMENTS,
            navPatients to Tab.PATIENTS,
            navSlots to Tab.SLOTS,
            navOtp to Tab.OTP
        ).forEach { (item, tab) ->
            val active = tab == currentTab
            val color = if (active) Color.WHITE else colorTextMuted
            item.iconDrawable.updateTint(color)
            item.label.setTextColor(color)
            item.label.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            item.pill.background = if (active) {
                GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(colorPrimaryLight, colorPrimaryDark)).apply {
                    cornerRadius = dp(18).toFloat()
                }
            } else null
        }
    }

    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg(Color.WHITE, 24f)
            setPadding(dp(4), dp(10), dp(4), dp(10))
            elevation = dp(8).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
                }
            }
            clipToOutline = true
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                setMargins(dp(16), 0, dp(16), dp(16))
            }
        }
        navAppointments = navItem(HomeActivity.VectorIconDrawable.IconType.CALENDAR, "অ্যাপয়েন্টমেন্ট") { switchTab(Tab.APPOINTMENTS) }
        navPatients = navItem(HomeActivity.VectorIconDrawable.IconType.PERSON, "রোগী") { switchTab(Tab.PATIENTS) }
        navSlots = navItem(HomeActivity.VectorIconDrawable.IconType.CLOCK, "টাইম স্লট") { switchTab(Tab.SLOTS) }
        navOtp = navItem(HomeActivity.VectorIconDrawable.IconType.SHIELD, "OTP পুল") { switchTab(Tab.OTP) }
        nav.addView(navAppointments.root)
        nav.addView(navPatients.root)
        nav.addView(navSlots.root)
        nav.addView(navOtp.root)
        return nav
    }

    private fun navItem(iconType: HomeActivity.VectorIconDrawable.IconType, labelText: String, onClick: () -> Unit): NavItemViews {
        val drawable = HomeActivity.VectorIconDrawable(iconType, colorTextMuted, dp(20))
        val icon = ImageView(this).apply {
            setImageDrawable(drawable)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        }
        val labelView = text(labelText, 9.5f, Typeface.BOLD, colorTextMuted, Gravity.CENTER).apply {
            setPadding(0, dp(4), 0, 0)
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), dp(8))
            addView(icon)
            addView(labelView)
        }
        val itemRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            isClickable = true
            isFocusable = true
            addView(pill)
            setOnClickListener { onClick() }
        }
        return NavItemViews(itemRoot, pill, icon, drawable, labelView)
    }

    private fun panelTopBar(titleText: String, subtitle: String): View {
        val container = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark)
            ).apply {
                setCornerRadii(floatArrayOf(0f, 0f, 0f, 0f, dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat()))
            }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(22))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backBtn = TextView(this).apply {
            text = "←"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = roundedBg(Color.argb(46, 255, 255, 255), 30f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
        }
        textCol.addView(text(titleText, 17f, Typeface.BOLD, Color.WHITE, Gravity.START))
        textCol.addView(text(subtitle, 11.5f, Typeface.NORMAL, Color.argb(210, 255, 255, 255), Gravity.START).apply {
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(backBtn)
        row.addView(textCol)
        inner.addView(row)
        container.addView(inner)
        return container
    }

    // ==================================================================
    // ট্যাব ১ — অ্যাপয়েন্টমেন্ট
    // ==================================================================
    private fun loadAppointments() {
        appointmentsContainer.removeAllViews()
        appointmentsContainer.addView(loadingText())
        lifecycleScope.launch {
            val result = SupabaseClient.adminGetAllAppointments()
            appointmentsContainer.removeAllViews()
            result.onSuccess { rows ->
                if (rows.length() == 0) {
                    appointmentsContainer.addView(emptyStateCard("এখনো কোনো অ্যাপয়েন্টমেন্ট বুক হয়নি"))
                    return@onSuccess
                }
                for (i in 0 until rows.length()) {
                    val obj = rows.getJSONObject(i)
                    appointmentsContainer.addView(buildAppointmentCard(obj))
                    appointmentsContainer.addView(space(dp(10)))
                }
            }.onFailure {
                appointmentsContainer.addView(errorText("অ্যাপয়েন্টমেন্ট লোড করা যায়নি"))
            }
        }
    }

    private fun buildAppointmentCard(obj: JSONObject): LinearLayout {
        val id = obj.optString("id", "")
        val name = obj.optString("patient_name", "")
        val phone = obj.optString("phone", "")
        val date = obj.optString("preferred_date", "")
        val time = obj.optString("preferred_time", "")
        val reason = obj.optString("reason", "")
        val status = obj.optString("status", "pending")
        val paymentStatus = obj.optString("payment_status", "not_applicable")
        val slotOpen = obj.optBoolean("slot_open", false)
        val fee = obj.optInt("fee", 0)
        val txnId = obj.optString("transaction_id", "")

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(colorCard, 18f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            elevation = dp(1.5f).toFloat()

            val topRow = LinearLayout(this@AdminActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val infoCol = LinearLayout(this@AdminActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoCol.addView(text(name.ifEmpty { "নাম নেই" }, 14f, Typeface.BOLD, colorDark, Gravity.START))
            infoCol.addView(text("$phone", 11.5f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
                setPadding(0, dp(2), 0, 0)
            })
            topRow.addView(infoCol)
            topRow.addView(statusBadgeView(status))
            addView(topRow)

            addView(rowDivider(dp(12), dp(10)))

            addView(kvRow("তারিখ ও সময়", "$date • $time"))
            addView(kvRow("কারণ", reason.ifEmpty { "সাধারণ পরামর্শ" }))
            addView(kvRow("ফি", "৳$fee"))
            addView(kvRow("পেমেন্ট স্ট্যাটাস", paymentStatusLabel(paymentStatus)))
            if (txnId.isNotBlank()) addView(kvRow("ট্রানজেকশন আইডি", txnId))
            addView(kvRow("সিরিয়াল ওপেন", if (slotOpen) "হ্যাঁ (চালু)" else "না (বন্ধ)"))

            addView(rowDivider(dp(10), dp(10)))

            val actionsRow = LinearLayout(this@AdminActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            actionsRow.addView(smallActionButton("এডিট", colorPrimary) {
                showEditAppointmentDialog(obj)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            actionsRow.addView(space(dp(10)))
            actionsRow.addView(smallActionButton("ডিলিট", colorDanger) {
                confirmDialog(
                    title = "অ্যাপয়েন্টমেন্ট ডিলিট",
                    message = "আপনি কি নিশ্চিতভাবে এই অ্যাপয়েন্টমেন্টটি স্থায়ীভাবে মুছে ফেলতে চান?",
                    positiveText = "হ্যাঁ, ডিলিট করুন",
                    positiveColor = colorDanger
                ) {
                    lifecycleScope.launch {
                        val res = SupabaseClient.adminDeleteAppointment(id)
                        res.onSuccess {
                            Toast.makeText(this@AdminActivity, "ডিলিট করা হয়েছে", Toast.LENGTH_SHORT).show()
                            loadAppointments()
                        }.onFailure {
                            Toast.makeText(this@AdminActivity, "ডিলিট ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(actionsRow)
        }
    }

    /** অ্যাপয়েন্টমেন্ট এডিট করার কাস্টম ডায়ালগ — status, payment_status, slot_open, fee পরিবর্তনযোগ্য */
    private fun showEditAppointmentDialog(obj: JSONObject) {
        val id = obj.optString("id", "")
        var selectedStatus = obj.optString("status", "pending")
        var selectedPaymentStatus = obj.optString("payment_status", "not_applicable")
        var slotOpen = obj.optBoolean("slot_open", false)

        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val scrollWrap = ScrollView(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.WHITE, 22f)
            setPadding(dp(22), dp(22), dp(22), dp(20))
        }

        card.addView(text("অ্যাপয়েন্টমেন্ট এডিট করুন", 16f, Typeface.BOLD, colorDark, Gravity.START))
        card.addView(text(obj.optString("patient_name", ""), 12.5f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
            setPadding(0, dp(4), 0, dp(14))
        })

        // --- status চিপস ---
        card.addView(sectionLabel("স্ট্যাটাস"))
        val statusOptions = listOf("pending" to "পেন্ডিং", "confirmed" to "কনফার্মড", "completed" to "সম্পন্ন", "cancelled" to "বাতিল")
        val statusChipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val statusChipViews = mutableListOf<TextView>()
        statusOptions.forEach { (value, label) ->
            val chip = chipButton(label, value == selectedStatus)
            chip.setOnClickListener {
                selectedStatus = value
                statusChipViews.forEachIndexed { idx, v -> setChipSelected(v, statusOptions[idx].first == selectedStatus) }
            }
            statusChipViews.add(chip)
            statusChipsRow.addView(chip)
            statusChipsRow.addView(space(dp(8)))
        }
        card.addView(horizontalScrollOf(statusChipsRow))

        // --- payment status চিপস ---
        card.addView(sectionLabel("পেমেন্ট স্ট্যাটাস"))
        val paymentOptions = listOf(
            "not_applicable" to "প্রযোজ্য নয়",
            "pending_verification" to "যাচাই বাকি",
            "verified" to "যাচাইকৃত",
            "rejected" to "প্রত্যাখ্যাত"
        )
        val paymentChipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val paymentChipViews = mutableListOf<TextView>()
        paymentOptions.forEach { (value, label) ->
            val chip = chipButton(label, value == selectedPaymentStatus)
            chip.setOnClickListener {
                selectedPaymentStatus = value
                paymentChipViews.forEachIndexed { idx, v -> setChipSelected(v, paymentOptions[idx].first == selectedPaymentStatus) }
            }
            paymentChipViews.add(chip)
            paymentChipsRow.addView(chip)
            paymentChipsRow.addView(space(dp(8)))
        }
        card.addView(horizontalScrollOf(paymentChipsRow))

        // --- slot open সুইচ ---
        val slotRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        slotRow.addView(text("সিরিয়াল এখনই ওপেন করুন", 12.5f, Typeface.BOLD, colorDark, Gravity.START).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val slotSwitch = Switch(this).apply { isChecked = slotOpen }
        slotSwitch.setOnCheckedChangeListener { _, checked -> slotOpen = checked }
        slotRow.addView(slotSwitch)
        card.addView(slotRow)

        // --- ফি এডিট ---
        card.addView(sectionLabel("ফি (টাকা)").apply { setPadding(0, dp(14), 0, dp(6)) })
        val feeInput = EditText(this).apply {
            setText(obj.optInt("fee", 800).toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            background = roundedBg(Color.parseColor("#F4F7F6"), 12f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextColor(colorDark)
        }
        card.addView(feeInput)

        // --- বাটন ---
        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20)
            }
        }
        buttonsRow.addView(dialogButton("বাতিল", colorTextMuted, Color.parseColor("#F1F3F2")) { dialog.dismiss() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        buttonsRow.addView(space(dp(10)))
        buttonsRow.addView(dialogButton("সংরক্ষণ করুন", Color.WHITE, colorPrimary) {
            val fields = JSONObject().apply {
                put("status", selectedStatus)
                put("payment_status", selectedPaymentStatus)
                put("slot_open", slotOpen)
                put("fee", feeInput.text.toString().toIntOrNull() ?: obj.optInt("fee", 800))
            }
            lifecycleScope.launch {
                val res = SupabaseClient.adminUpdateAppointment(id, fields)
                res.onSuccess {
                    Toast.makeText(this@AdminActivity, "আপডেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadAppointments()
                }.onFailure {
                    Toast.makeText(this@AdminActivity, "আপডেট ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(buttonsRow)

        scrollWrap.addView(card)
        dialog.setContentView(scrollWrap)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            (resources.displayMetrics.heightPixels * 0.82).toInt()
        )
        dialog.show()
    }

    // ==================================================================
    // ট্যাব ২ — রোগী
    // ==================================================================
    private fun loadPatients() {
        patientsContainer.removeAllViews()
        patientsContainer.addView(loadingText())
        lifecycleScope.launch {
            val result = SupabaseClient.adminGetAllPatients()
            patientsContainer.removeAllViews()
            result.onSuccess { rows ->
                if (rows.length() == 0) {
                    patientsContainer.addView(emptyStateCard("এখনো কোনো রোগী রেজিস্ট্রেশন করেননি"))
                    return@onSuccess
                }
                for (i in 0 until rows.length()) {
                    val obj = rows.getJSONObject(i)
                    patientsContainer.addView(buildPatientCard(obj))
                    patientsContainer.addView(space(dp(10)))
                }
            }.onFailure {
                patientsContainer.addView(errorText("রোগীর তালিকা লোড করা যায়নি"))
            }
        }
    }

    private fun buildPatientCard(obj: JSONObject): LinearLayout {
        val id = obj.optString("id", "")
        val name = obj.optString("full_name", "")
        val phone = obj.optString("phone", "")
        val age = obj.optString("age", "")
        val gender = obj.optString("gender", "")
        val blood = obj.optString("blood_group", "")
        val deviceId = obj.optString("device_id", "")

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(colorCard, 18f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            elevation = dp(1.5f).toFloat()

            addView(text(name.ifEmpty { "নাম নেই" }, 14f, Typeface.BOLD, colorDark, Gravity.START))
            addView(text(phone, 11.5f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
                setPadding(0, dp(2), 0, 0)
            })
            addView(rowDivider(dp(10), dp(8)))
            val summary = listOfNotNull(
                if (age.isNotBlank() && age != "null") "বয়স: $age" else null,
                if (gender.isNotBlank()) "লিঙ্গ: $gender" else null,
                if (blood.isNotBlank()) "রক্ত: $blood" else null
            ).joinToString("  •  ")
            addView(text(summary.ifEmpty { "অতিরিক্ত তথ্য নেই" }, 11.5f, Typeface.NORMAL, colorDark, Gravity.START))
            addView(text(
                if (deviceId.isNotBlank() && deviceId != "null") "ডিভাইস: বাইন্ড করা আছে" else "ডিভাইস: বাইন্ড করা নেই",
                10.5f, Typeface.NORMAL, colorTextMuted, Gravity.START
            ).apply {
                setPadding(0, dp(4), 0, 0)
            })

            addView(rowDivider(dp(10), dp(10)))

            val actionsRow = LinearLayout(this@AdminActivity).apply { orientation = LinearLayout.HORIZONTAL }
            actionsRow.addView(smallActionButton("বিস্তারিত/এডিট", colorPrimary) {
                showEditPatientDialog(obj)
            }.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            actionsRow.addView(space(dp(8)))
            actionsRow.addView(smallActionButton("PIN রিসেট", colorAccent) {
                confirmDialog(
                    title = "PIN রিসেট",
                    message = "$name এর জন্য নতুন ৪-ডিজিট PIN তৈরি করা হবে। আগেরটা আর কাজ করবে না।",
                    positiveText = "রিসেট করুন",
                    positiveColor = colorAccent
                ) {
                    val newPin = (1000 + (0..8999).random()).toString()
                    lifecycleScope.launch {
                        val res = SupabaseClient.setPatientPassword(id, newPin)
                        res.onSuccess {
                            showPinResultDialog(name, newPin)
                        }.onFailure {
                            Toast.makeText(this@AdminActivity, "PIN রিসেট ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            addView(actionsRow)

            addView(space(dp(8)))

            // ডিভাইস রিসেট — সিম/মোবাইল হারিয়ে গেলে বা নষ্ট হয়ে গেলে রোগী নতুন ডিভাইসে
            // লগইন করতে পারার জন্য এডমিন এখান থেকে তার device_id আনবাইন্ড করে দিতে পারবেন
            addView(smallActionButton("ডিভাইস রিসেট", colorInfo) {
                confirmDialog(
                    title = "ডিভাইস রিসেট",
                    message = "$name এর সাথে যুক্ত ডিভাইসটি আনবাইন্ড করা হবে। এরপর সে যেকোনো নতুন মোবাইলে তার ফোন নাম্বার ও PIN দিয়ে লগইন করতে পারবে। সিম বা মোবাইল হারিয়ে/নষ্ট হয়ে গেলে এটি ব্যবহার করুন।",
                    positiveText = "রিসেট করুন",
                    positiveColor = colorInfo
                ) {
                    lifecycleScope.launch {
                        val res = SupabaseClient.adminResetPatientDevice(id)
                        res.onSuccess {
                            Toast.makeText(this@AdminActivity, "ডিভাইস রিসেট করা হয়েছে, রোগী এখন নতুন ডিভাইসে লগইন করতে পারবেন", Toast.LENGTH_SHORT).show()
                            loadPatients()
                        }.onFailure {
                            Toast.makeText(this@AdminActivity, "ডিভাইস রিসেট ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })

            addView(space(dp(8)))
            addView(smallActionButton("অ্যাকাউন্ট ডিলিট করুন", colorDanger) {
                confirmDialog(
                    title = "রোগীর অ্যাকাউন্ট ডিলিট",
                    message = "$name এর অ্যাকাউন্ট ও তার সব অ্যাপয়েন্টমেন্ট স্থায়ীভাবে মুছে যাবে। এগিয়ে যেতে চান?",
                    positiveText = "হ্যাঁ, ডিলিট করুন",
                    positiveColor = colorDanger
                ) {
                    lifecycleScope.launch {
                        val res = SupabaseClient.adminDeletePatient(id)
                        res.onSuccess {
                            Toast.makeText(this@AdminActivity, "অ্যাকাউন্ট ডিলিট করা হয়েছে", Toast.LENGTH_SHORT).show()
                            loadPatients()
                        }.onFailure {
                            Toast.makeText(this@AdminActivity, "ডিলিট ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }

    private fun showPinResultDialog(name: String, pin: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(Color.WHITE, 22f)
            setPadding(dp(24), dp(26), dp(24), dp(22))
        }
        card.addView(text("নতুন PIN তৈরি হয়েছে", 15f, Typeface.BOLD, colorDark, Gravity.CENTER))
        card.addView(text("$name এর নতুন লগইন PIN", 11.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER).apply {
            setPadding(0, dp(6), 0, dp(16))
        })
        card.addView(text(pin, 30f, Typeface.BOLD, colorPrimary, Gravity.CENTER).apply {
            letterSpacing = 0.25f
        })
        card.addView(text("এই PIN রোগীকে জানিয়ে দিন। এটি পরে আর দেখা যাবে না।", 11f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER).apply {
            setPadding(0, dp(14), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        card.addView(dialogButton("ঠিক আছে", Color.WHITE, colorPrimary) { dialog.dismiss() }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18)
            }
        })
        dialog.setContentView(card)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.86).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    /** রোগীর প্রোফাইল তথ্য এডিট করার ডায়ালগ */
    private fun showEditPatientDialog(obj: JSONObject) {
        val id = obj.optString("id", "")
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val scrollWrap = ScrollView(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.WHITE, 22f)
            setPadding(dp(22), dp(22), dp(22), dp(20))
        }
        card.addView(text("রোগীর তথ্য এডিট করুন", 16f, Typeface.BOLD, colorDark, Gravity.START).apply {
            setPadding(0, 0, 0, dp(14))
        })

        val nameInput = labeledInput(card, "পূর্ণ নাম", obj.optString("full_name", ""))
        val phoneInput = labeledInput(card, "ফোন নাম্বার", obj.optString("phone", ""))
        val ageInput = labeledInput(card, "বয়স", obj.optString("age", ""), InputType.TYPE_CLASS_NUMBER)
        val genderInput = labeledInput(card, "লিঙ্গ", obj.optString("gender", ""))
        val bloodInput = labeledInput(card, "রক্তের গ্রুপ", obj.optString("blood_group", ""))
        val addressInput = labeledInput(card, "ঠিকানা", obj.optString("address", ""))
        val emergencyInput = labeledInput(card, "জরুরি যোগাযোগ", obj.optString("emergency_contact", ""))
        val historyInput = labeledInput(card, "মেডিকেল হিস্ট্রি", obj.optString("medical_history", ""))

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18)
            }
        }
        buttonsRow.addView(dialogButton("বাতিল", colorTextMuted, Color.parseColor("#F1F3F2")) { dialog.dismiss() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        buttonsRow.addView(space(dp(10)))
        buttonsRow.addView(dialogButton("সংরক্ষণ করুন", Color.WHITE, colorPrimary) {
            val fields = JSONObject().apply {
                put("full_name", nameInput.text.toString())
                put("phone", phoneInput.text.toString())
                put("age", ageInput.text.toString().toIntOrNull() ?: JSONObject.NULL)
                put("gender", genderInput.text.toString())
                put("blood_group", bloodInput.text.toString())
                put("address", addressInput.text.toString())
                put("emergency_contact", emergencyInput.text.toString())
                put("medical_history", historyInput.text.toString())
            }
            lifecycleScope.launch {
                val res = SupabaseClient.adminUpdatePatient(id, fields)
                res.onSuccess {
                    Toast.makeText(this@AdminActivity, "রোগীর তথ্য আপডেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadPatients()
                }.onFailure {
                    Toast.makeText(this@AdminActivity, "আপডেট ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(buttonsRow)

        scrollWrap.addView(card)
        dialog.setContentView(scrollWrap)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
        dialog.show()
    }

    private fun labeledInput(parent: LinearLayout, label: String, value: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText {
        parent.addView(sectionLabel(label).apply { setPadding(0, dp(10), 0, dp(6)) })
        val input = EditText(this).apply {
            setText(value)
            this.inputType = inputType
            background = roundedBg(Color.parseColor("#F4F7F6"), 12f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextColor(colorDark)
            textSize = 13f
        }
        parent.addView(input)
        return input
    }

    // ==================================================================
    // ট্যাব ৩ — টাইম স্লট
    // ==================================================================
    private fun loadSlots() {
        slotsContainer.removeAllViews()
        slotsContainer.addView(loadingText())
        lifecycleScope.launch {
            val result = SupabaseClient.getSlotSettings()
            slotsContainer.removeAllViews()
            result.onSuccess { settingsMap ->
                slotsContainer.addView(text("গ্লোবাল সময়-স্লট (প্রতিদিনের জন্য প্রযোজ্য)", 13f, Typeface.BOLD, colorDark, Gravity.START).apply {
                    setPadding(dp(2), 0, 0, dp(10))
                })
                val slotsCard = LinearLayout(this@AdminActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = roundedBg(colorCard, 18f)
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    elevation = dp(1.5f).toFloat()
                }
                defaultTimeSlots.forEachIndexed { index, slot ->
                    val enabled = settingsMap[slot] ?: true
                    slotsCard.addView(slotToggleRow(slot, enabled) { checked ->
                        lifecycleScope.launch {
                            SupabaseClient.setSlotEnabled(slot, checked)
                        }
                    })
                    if (index != defaultTimeSlots.lastIndex) slotsCard.addView(rowDivider(0, 0))
                }
                slotsContainer.addView(slotsCard)

                slotsContainer.addView(text(
                    "উপরের যেকোনো স্লট বন্ধ (Off) করলে সেই সময়ে নতুন কেউ অ্যাপয়েন্টমেন্ট বুক করতে পারবে না।",
                    10.5f, Typeface.NORMAL, colorTextMuted, Gravity.START
                ).apply {
                    setPadding(dp(4), dp(10), dp(4), 0)
                    setLineSpacing(dp(2).toFloat(), 1f)
                })
            }.onFailure {
                slotsContainer.addView(errorText("স্লট সেটিংস লোড করা যায়নি"))
            }
        }
    }

    private fun slotToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(8), dp(10))
            addView(text(label, 13f, Typeface.BOLD, colorDark, Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            val sw = Switch(this@AdminActivity).apply { isChecked = checked }
            sw.setOnCheckedChangeListener { _: CompoundButton, isChecked -> onToggle(isChecked) }
            addView(sw)
        }
    }

    // ==================================================================
    // ট্যাব ৪ — OTP পুল
    // ==================================================================
    private fun loadOtpStats() {
        otpStatsContainer.removeAllViews()
        otpStatsContainer.addView(loadingText())
        lifecycleScope.launch {
            val result = SupabaseClient.adminGetOtpStats()
            otpStatsContainer.removeAllViews()
            result.onSuccess { (available, assigned, verified) ->
                val statsRow = LinearLayout(this@AdminActivity).apply { orientation = LinearLayout.HORIZONTAL }
                statsRow.addView(otpStatCard("খালি আছে", available, colorSuccess).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                statsRow.addView(space(dp(10)))
                statsRow.addView(otpStatCard("বরাদ্দকৃত", assigned, colorAccent).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                statsRow.addView(space(dp(10)))
                statsRow.addView(otpStatCard("ব্যবহৃত", verified, colorInfo).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                otpStatsContainer.addView(statsRow)

                otpStatsContainer.addView(space(dp(18)))

                otpStatsContainer.addView(fullWidthActionButton("নতুন ১০০টি OTP কোড যোগ করুন", colorPrimary) {
                    lifecycleScope.launch {
                        val res = SupabaseClient.adminAddOtpCodes(100)
                        res.onSuccess {
                            Toast.makeText(this@AdminActivity, "১০০টি নতুন কোড যোগ করা হয়েছে", Toast.LENGTH_SHORT).show()
                            loadOtpStats()
                        }.onFailure {
                            Toast.makeText(this@AdminActivity, "কোড যোগ করা ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }
                })

                otpStatsContainer.addView(space(dp(12)))

                otpStatsContainer.addView(fullWidthActionButton("ব্যবহৃত/মেয়াদোত্তীর্ণ কোড রিসেট করুন", colorDanger) {
                    confirmDialog(
                        title = "OTP পুল রিসেট",
                        message = "assigned/expired অবস্থায় থাকা সব কোড আবার 'available' করে দেওয়া হবে। এগিয়ে যাবেন?",
                        positiveText = "হ্যাঁ, রিসেট করুন",
                        positiveColor = colorDanger
                    ) {
                        lifecycleScope.launch {
                            val res = SupabaseClient.adminResetOtpPool()
                            res.onSuccess {
                                Toast.makeText(this@AdminActivity, "OTP পুল রিসেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                loadOtpStats()
                            }.onFailure {
                                Toast.makeText(this@AdminActivity, "রিসেট ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                })
            }.onFailure {
                otpStatsContainer.addView(errorText("OTP তথ্য লোড করা যায়নি"))
            }
        }
    }

    private fun otpStatCard(label: String, count: Int, color: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(colorCard, 16f)
            setPadding(dp(12), dp(16), dp(12), dp(16))
            elevation = dp(1.5f).toFloat()
            addView(text(count.toString(), 22f, Typeface.BOLD, color, Gravity.CENTER))
            addView(text(label, 11f, Typeface.BOLD, colorTextMuted, Gravity.CENTER).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun fullWidthActionButton(label: String, color: Int, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedBg(color, 16f)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(text(label, 13f, Typeface.BOLD, Color.WHITE, Gravity.CENTER))
        }
    }

    // ==================================================================
    // সাধারণ (shared) UI helpers
    // ==================================================================
    private fun statusBadgeView(status: String): TextView {
        val (label, color) = when (status) {
            "confirmed" -> "কনফার্মড" to colorSuccess
            "completed" -> "সম্পন্ন" to colorInfo
            "cancelled" -> "বাতিল" to colorDanger
            else -> "পেন্ডিং" to colorAccent
        }
        return text(label, 10f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
            background = roundedBg(color, 30f)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
    }

    private fun paymentStatusLabel(value: String): String = when (value) {
        "pending_verification" -> "যাচাই বাকি"
        "verified" -> "যাচাইকৃত"
        "rejected" -> "প্রত্যাখ্যাত"
        else -> "প্রযোজ্য নয়"
    }

    private fun kvRow(label: String, value: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, dp(5))
            addView(text(label, 11.5f, Typeface.BOLD, colorTextMuted, Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(text(value, 12f, Typeface.NORMAL, colorDark, Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun smallActionButton(label: String, color: Int, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedBg(Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)), 12f)
            setPadding(dp(10), dp(11), dp(10), dp(11))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(text(label, 12f, Typeface.BOLD, color, Gravity.CENTER))
        }
    }

    private fun chipButton(label: String, selected: Boolean): TextView {
        return TextView(this).apply {
            text = label
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            isClickable = true
            isFocusable = true
            setChipSelectedInternal(this, selected)
        }
    }

    private fun setChipSelectedInternal(view: TextView, selected: Boolean) {
        if (selected) {
            view.setTextColor(Color.WHITE)
            view.background = roundedBg(colorPrimary, 30f)
        } else {
            view.setTextColor(colorTextMuted)
            view.background = roundedBg(Color.parseColor("#F1F3F2"), 30f)
        }
    }

    private fun setChipSelected(view: TextView, selected: Boolean) = setChipSelectedInternal(view, selected)

    private fun horizontalScrollOf(row: LinearLayout): HorizontalScrollView {
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun sectionLabel(label: String): TextView =
        text(label, 11.5f, Typeface.BOLD, colorTextMuted, Gravity.START)

    private fun dialogButton(label: String, textColor: Int, bgColor: Int, onClick: () -> Unit): TextView {
        return text(label, 13.5f, Typeface.BOLD, textColor, Gravity.CENTER).apply {
            background = roundedBg(bgColor, 14f)
            setPadding(0, dp(13), 0, dp(13))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun confirmDialog(
        title: String,
        message: String,
        positiveText: String,
        positiveColor: Int,
        negativeText: String = "বাতিল",
        onPositive: () -> Unit
    ) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(Color.WHITE, 22f)
            setPadding(dp(24), dp(26), dp(24), dp(20))
        }
        card.addView(text(title, 16f, Typeface.BOLD, colorDark, Gravity.CENTER))
        card.addView(text(message, 12.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER).apply {
            setPadding(0, dp(10), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20)
            }
        }
        buttonsRow.addView(dialogButton(negativeText, colorTextMuted, Color.parseColor("#F1F3F2")) { dialog.dismiss() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        buttonsRow.addView(space(dp(10)))
        buttonsRow.addView(dialogButton(positiveText, Color.WHITE, positiveColor) {
            dialog.dismiss()
            onPositive()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(buttonsRow)

        dialog.setContentView(card)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.86).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    private fun emptyStateCard(message: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(colorCard, 18f)
            setPadding(dp(24), dp(30), dp(24), dp(30))
            addView(text(message, 12.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER))
        }
    }

    private fun loadingText(): TextView = text("লোড হচ্ছে...", 12.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER).apply {
        setPadding(0, dp(30), 0, dp(30))
    }

    private fun errorText(message: String): TextView =
        text(message, 12.5f, Typeface.NORMAL, colorDanger, Gravity.CENTER).apply {
            setPadding(0, dp(30), 0, dp(30))
        }

    private fun rowDivider(topMargin: Int, bottomMargin: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            this.topMargin = topMargin; this.bottomMargin = bottomMargin
        }
        setBackgroundColor(colorFieldBorder)
    }

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
}
