package com.konasl.nagad

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    // FIX: PDF "বড় ফাইল" থ্রেশহোল্ড — এর যেকোনো একটি শর্ত (পেইজ সংখ্যা বা সাইজ) না মিললে
    // সরাসরি in-app viewer এ খুলবে, দুটো শর্তই মিললে তবেই চুজার ডায়ালগ দেখাবে।
    private val PDF_LARGE_PAGE_THRESHOLD = 10
    private val PDF_LARGE_SIZE_BYTES = 4L * 1024 * 1024 // 4 MB

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

    private lateinit var appointmentSearchBar: LinearLayout
    private lateinit var appointmentSearchInput: EditText
    private lateinit var patientSearchBar: LinearLayout
    private lateinit var patientSearchInput: EditText

    private var allAppointments: List<JSONObject> = emptyList()
    private var allPatients: List<JSONObject> = emptyList()

    // FIX: এই সেটে শুধুমাত্র সেই patient_id গুলো থাকবে যাদের কমপক্ষে একটি বৈধ (non-null/non-empty) report_url আছে।
    // "রোগীর রিপোর্ট দেখুন" বাটনটি শুধুমাত্র এই সেটে থাকা রোগীদের জন্যই দেখানো হবে।
    private var patientsWithReports: Set<String> = emptySet()

    // Realtime: 5-second REST polling নেই। Database change এলে WebSocket callback থেকে reload হবে।
    private var adminRealtimeStarted = false
    private var pendingNewAppointmentId: String? = null

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

        val (apptSearchBarView, apptSearchInputView) = buildSearchBar("রোগীর নাম বা নাম্বার দিয়ে সার্চ করুন") { query ->
            renderAppointments(query)
        }
        appointmentSearchBar = apptSearchBarView
        appointmentSearchInput = apptSearchInputView
        appointmentSearchBar.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(16), dp(14), dp(16), dp(2))
        }
        apptContent.addView(panelTopBar("এডমিন প্যানেল", "সব অ্যাপয়েন্টমেন্ট পর্যবেক্ষণ ও ম্যানেজমেন্ট", appointmentSearchBar))
        apptContent.addView(appointmentSearchBar)
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

        val (patientSearchBarView, patientSearchInputView) = buildSearchBar("রোগীর নাম বা নাম্বার দিয়ে সার্চ করুন") { query ->
            renderPatients(query)
        }
        patientSearchBar = patientSearchBarView
        patientSearchInput = patientSearchInputView
        patientSearchBar.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(16), dp(14), dp(16), dp(2))
        }
        patientContent.addView(panelTopBar("রোগীর তালিকা", "সব রোগীর প্রোফাইল ও রিপোর্ট দেখুন ও ম্যানেজ করুন", patientSearchBar))
        patientContent.addView(patientSearchBar)
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

        loadAppointments(showNewBookingPopup = false)
        startAdminRealtime()
        updateNavStyle()
    }

    override fun onResume() {
        super.onResume()
        startAdminRealtime()
        when (currentTab) {
            Tab.APPOINTMENTS -> loadAppointments()
            Tab.PATIENTS -> loadPatients()
            Tab.SLOTS -> loadSlots()
            Tab.OTP -> loadOtpStats()
        }
    }

    override fun onPause() {
        super.onPause()
        stopAdminRealtime()
    }

    private fun startAdminRealtime() {
        if (adminRealtimeStarted) return
        adminRealtimeStarted = true
        SupabaseClient.startAdminAppointmentsRealtime { eventType, record ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val id = record?.optString("id", "")?.takeIf { it.isNotBlank() }
                if (eventType == "INSERT" && id != null) {
                    pendingNewAppointmentId = id
                }
                // INSERT/UPDATE/DELETE — শুধু change-এর পরেই REST reload।
                // কোনো fixed interval polling নেই।
                loadAppointments(showNewBookingPopup = eventType == "INSERT")
            }
        }
    }

    private fun stopAdminRealtime() {
        if (!adminRealtimeStarted) return
        adminRealtimeStarted = false
        pendingNewAppointmentId = null
        SupabaseClient.stopAdminAppointmentsRealtime()
    }

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

    private fun panelTopBar(titleText: String, subtitle: String, searchBarView: View? = null): View {
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

        if (searchBarView != null) {
            val searchToggleBtn = ImageView(this).apply {
                setImageDrawable(SearchIconDrawable(Color.WHITE, dp(2f).toFloat()))
                background = roundedBg(Color.argb(46, 255, 255, 255), 30f)
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginStart = dp(10) }
                val pad = dp(8)
                setPadding(pad, pad, pad, pad)
                isClickable = true
                isFocusable = true
                contentDescription = "সার্চ করুন"
                setOnClickListener {
                    val bar = searchBarView as LinearLayout
                    val input = bar.getChildAt(1) as? EditText
                    if (bar.visibility == View.VISIBLE) {
                        bar.visibility = View.GONE
                        input?.setText("")
                    } else {
                        bar.visibility = View.VISIBLE
                        input?.requestFocus()
                    }
                }
            }
            row.addView(searchToggleBtn)
        }

        inner.addView(row)
        container.addView(inner)
        return container
    }

    private fun buildSearchBar(hint: String, onQueryChange: (String) -> Unit): Pair<LinearLayout, EditText> {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBgStroke(Color.WHITE, colorFieldBorder, 14f, 1)
            setPadding(dp(14), dp(2), dp(10), dp(2))
            visibility = View.GONE
            elevation = dp(1f).toFloat()
        }
        val searchIcon = ImageView(this).apply {
            setImageDrawable(SearchIconDrawable(colorTextMuted, dp(1.8f).toFloat()))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) }
        }
        val input = EditText(this).apply {
            this.hint = hint
            setHintTextColor(colorTextMuted)
            setTextColor(colorDark)
            textSize = 13f
            background = null
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, dp(11), 0, dp(11))
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onQueryChange(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        val clearBtn = ImageView(this).apply {
            setImageDrawable(CloseIconDrawable(colorTextMuted, dp(1.6f).toFloat()))
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
            isClickable = true
            isFocusable = true
            setOnClickListener { input.setText("") }
        }
        bar.addView(searchIcon)
        bar.addView(input)
        bar.addView(clearBtn)
        return bar to input
    }

    private fun matchesQuery(name: String, phone: String, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return name.lowercase().contains(q) || phone.replace(" ", "").contains(q.replace(" ", ""))
    }

    // ==================================================================
    // ট্যাব ১ — অ্যাপয়েন্টমেন্ট
    // ==================================================================
    private fun loadAppointments(showNewBookingPopup: Boolean = false) {
        appointmentsContainer.removeAllViews()
        appointmentsContainer.addView(loadingText())
        lifecycleScope.launch {
            val result = SupabaseClient.adminGetAllAppointments()
            result.onSuccess { rows ->
                val list = mutableListOf<JSONObject>()
                for (i in 0 until rows.length()) list.add(rows.getJSONObject(i))
                allAppointments = list
                renderAppointments(appointmentSearchInput.text?.toString().orEmpty())

                if (showNewBookingPopup) {
                    val newId = pendingNewAppointmentId
                    val newBooking = newId?.let { id -> list.firstOrNull { it.optString("id", "") == id } }
                    pendingNewAppointmentId = null
                    if (newBooking != null) showNewBookingPopup(newBooking)
                }
            }.onFailure {
                appointmentsContainer.removeAllViews()
                appointmentsContainer.addView(errorText("অ্যাপয়েন্টমেন্ট লোড করা যায়নি"))
            }
        }
    }

    private fun showNewBookingPopup(obj: JSONObject) {
        val name = obj.optString("patient_name", "নতুন রোগী").ifBlank { "নতুন রোগী" }
        val phone = obj.optString("phone", "")
        val date = obj.optString("preferred_date", "")
        val time = obj.optString("preferred_time", "")
        val fee = obj.optInt("fee", 0)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🔔 নতুন বুকিং")
            .setMessage("$name
$phone
$date • $time
ফি: ৳$fee")
            .setPositiveButton("দেখুন") { _, _ ->
                switchTab(Tab.APPOINTMENTS)
            }
            .setNegativeButton("ঠিক আছে", null)
            .show()
    }

    private fun renderAppointments(query: String) {
        appointmentsContainer.removeAllViews()
        val filtered = allAppointments.filter {
            matchesQuery(it.optString("patient_name", ""), it.optString("phone", ""), query)
        }
        if (filtered.isEmpty()) {
            appointmentsContainer.addView(emptyStateCard(
                if (query.isBlank()) "এখনো কোনো অ্যাপয়েন্টমেন্ট বুক হয়নি" else "কোনো ফলাফল পাওয়া যায়নি"
            ))
            return
        }
        filtered.forEach { obj ->
            appointmentsContainer.addView(buildAppointmentCard(obj))
            appointmentsContainer.addView(space(dp(10)))
        }
    }

    // FIX: appointment এর report_url থেকে বৈধ (non-blank) URL গুলো বের করে দেয়।
    // "null" স্ট্রিং, খালি স্ট্রিং, শুধু কমা/স্পেস — এসব ক্ষেত্রে খালি লিস্ট রিটার্ন করবে।
    private fun extractValidReportUrls(rawReportUrl: String): List<String> {
        if (rawReportUrl.isBlank() || rawReportUrl.equals("null", ignoreCase = true)) return emptyList()
        return rawReportUrl.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
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
        val weight = obj.optString("weight", "")
        val bloodPressure = obj.optString("blood_pressure", "")
        val reportUrl = obj.optString("report_url", "")
        val patientId = obj.optString("patient_id", "")

        // FIX: শুধু "reportUrl.isNotBlank()" চেক করলে "null" স্ট্রিং বা খালি কমা সহ ভুল করে বাটন দেখানো হতে পারত।
        // এখন বৈধ URL গুলো আগে থেকেই বের করে নেওয়া হচ্ছে, তারপর সেই লিস্ট অনুযায়ী বাটন দেখানো/লুকানো হবে।
        val validReportUrls = extractValidReportUrls(reportUrl)

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
            infoCol.addView(text(phone, 11.5f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
                setPadding(0, dp(2), 0, 0)
            })
            topRow.addView(infoCol)
            topRow.addView(statusBadgeView(status))
            addView(topRow)

            addView(rowDivider(dp(12), dp(10)))

            addView(kvRow("তারিখ ও সময়", "$date • $time"))
            addView(kvRow("কারণ", reason.ifEmpty { "সাধারণ পরামর্শ" }))
            if (weight.isNotBlank() && weight != "null") addView(kvRow("বর্তমান ওজন", "$weight কেজি"))
            if (bloodPressure.isNotBlank() && bloodPressure != "null") addView(kvRow("রক্তচাপ", bloodPressure))
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

            addView(space(dp(8)))
            val commRow = LinearLayout(this@AdminActivity).apply { orientation = LinearLayout.HORIZONTAL }
            commRow.addView(communicationButton("চ্যাট", ChatIconDrawable(colorInfo, dp(1.6f).toFloat()), colorInfo) {
                startActivity(Intent(this@AdminActivity, ChatActivity::class.java).apply {
                    putExtra("appointment_id", id)
                    putExtra("patient_id", patientId)
                    putExtra("patient_name", name)
                    putExtra("patient_phone", phone)
                })
            }.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            commRow.addView(space(dp(8)))
            commRow.addView(communicationButton("ভিডিও কল", VideoCallIconDrawable(colorSuccess), colorSuccess) {
                startActivity(Intent(this@AdminActivity, VideocallActivity::class.java).apply {
                    putExtra("appointment_id", id)
                    putExtra("patient_id", patientId)
                    putExtra("patient_name", name)
                    putExtra("patient_phone", phone)
                })
            }.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            commRow.addView(space(dp(8)))
            commRow.addView(communicationButton("অডিও কল", AudioCallIconDrawable(colorAccent), colorAccent) {
                startActivity(Intent(this@AdminActivity, AudiocallActivity::class.java).apply {
                    putExtra("appointment_id", id)
                    putExtra("patient_id", patientId)
                    putExtra("patient_name", name)
                    putExtra("patient_phone", phone)
                })
            }.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            addView(commRow)

            // FIX: শুধু বৈধ report URL থাকলেই "রিপোর্ট দেখুন" বাটন দেখাবে
            if (validReportUrls.isNotEmpty()) {
                addView(space(dp(8)))
                addView(smallActionButton("এই অ্যাপয়েন্টমেন্টের রিপোর্ট দেখুন", colorInfo) {
                    renderReportsListDialog(name.ifEmpty { "রোগী" }, validReportUrls)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                })
            }
        }
    }

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

        card.addView(sectionLabel("ফি (টাকা)").apply { setPadding(0, dp(14), 0, dp(6)) })
        val feeInput = EditText(this).apply {
            setText(obj.optInt("fee", 800).toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            background = roundedBg(Color.parseColor("#F4F7F6"), 12f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextColor(colorDark)
        }
        card.addView(feeInput)

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
            val patientsResult = SupabaseClient.adminGetAllPatients()
            // FIX: রোগীর তালিকার সাথে সাথে সব appointment ও লোড করা হচ্ছে, যাতে বোঝা যায় কোন রোগীর
            // আসলেই কোনো বৈধ রিপোর্ট আপলোড করা আছে কিনা। এর ভিত্তিতেই "রোগীর রিপোর্ট দেখুন" বাটনটি
            // দেখানো বা লুকানো হবে।
            val appointmentsResult = SupabaseClient.adminGetAllAppointments()

            patientsResult.onSuccess { rows ->
                val list = mutableListOf<JSONObject>()
                for (i in 0 until rows.length()) list.add(rows.getJSONObject(i))
                allPatients = list

                val reportSet = mutableSetOf<String>()
                appointmentsResult.onSuccess { apptRows ->
                    for (i in 0 until apptRows.length()) {
                        val apptObj = apptRows.getJSONObject(i)
                        val pid = apptObj.optString("patient_id", "")
                        val rawReportUrl = apptObj.optString("report_url", "")
                        if (pid.isNotBlank() && extractValidReportUrls(rawReportUrl).isNotEmpty()) {
                            reportSet.add(pid)
                        }
                    }
                }
                // appointmentsResult ব্যর্থ হলেও রোগীর তালিকা দেখাতে সমস্যা নেই — শুধু সেক্ষেত্রে
                // সেফটির জন্য কোনো রোগীর জন্যই রিপোর্ট বাটন দেখানো হবে না, যতক্ষণ না রিলোড করা হয়।
                patientsWithReports = reportSet

                renderPatients(patientSearchInput.text?.toString().orEmpty())
            }.onFailure {
                patientsContainer.removeAllViews()
                patientsContainer.addView(errorText("রোগীর তালিকা লোড করা যায়নি"))
            }
        }
    }

    private fun renderPatients(query: String) {
        patientsContainer.removeAllViews()
        val filtered = allPatients.filter {
            matchesQuery(it.optString("full_name", ""), it.optString("phone", ""), query)
        }
        if (filtered.isEmpty()) {
            patientsContainer.addView(emptyStateCard(
                if (query.isBlank()) "এখনো কোনো রোগী রেজিস্ট্রেশন করেননি" else "কোনো ফলাফল পাওয়া যায়নি"
            ))
            return
        }
        filtered.forEach { obj ->
            patientsContainer.addView(buildPatientCard(obj))
            patientsContainer.addView(space(dp(10)))
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

        // FIX: এই রোগীর কমপক্ষে একটি appointment-এ বৈধ report_url আছে কিনা তা চেক করা হচ্ছে।
        // না থাকলে নিচে "রোগীর রিপোর্ট দেখুন" বাটনটি একদমই যোগ হবে না।
        val hasReport = patientsWithReports.contains(id)

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

            // FIX: শুধুমাত্র বৈধ রিপোর্ট থাকলেই এই বাটন ও তার আগের স্পেসিং যোগ হবে
            if (hasReport) {
                addView(space(dp(8)))
                addView(smallActionButton("রোগীর রিপোর্ট দেখুন", colorPrimary) {
                    showPatientReportsDialog(id, name.ifEmpty { "রোগী" })
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                })
            }

            addView(space(dp(8)))

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
    // রোগীর রিপোর্ট লিস্ট + হাই-কোয়ালিটি ImageViewer / PdfViewer
    // ==================================================================
    private fun showPatientReportsDialog(patientId: String, patientName: String) {
        Toast.makeText(this, "রিপোর্ট খোঁজা হচ্ছে...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = SupabaseClient.adminGetPatientAppointments(patientId)
            val reportUrls = mutableListOf<String>()
            result.onSuccess { rows ->
                for (i in 0 until rows.length()) {
                    val obj = rows.getJSONObject(i)
                    val raw = obj.optString("report_url", "")
                    reportUrls.addAll(extractValidReportUrls(raw))
                }
            }
            if (result.isFailure) {
                Toast.makeText(this@AdminActivity, "রিপোর্ট লোড করা যায়নি", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (reportUrls.isEmpty()) {
                Toast.makeText(this@AdminActivity, "$patientName এর কোনো রিপোর্ট পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                return@launch
            }
            renderReportsListDialog(patientName, reportUrls)
        }
    }

    private fun renderReportsListDialog(patientName: String, urls: List<String>) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val scrollWrap = ScrollView(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.WHITE, 22f)
            setPadding(dp(20), dp(20), dp(20), dp(18))
        }
        card.addView(text("$patientName এর রিপোর্ট", 15.5f, Typeface.BOLD, colorDark, Gravity.START))
        card.addView(text("${urls.size}টি ফাইল পাওয়া গেছে — দেখতে ট্যাপ করুন", 11f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
            setPadding(0, dp(4), 0, dp(14))
        })

        urls.forEachIndexed { index, url ->
            val isPdf = isPdfUrl(url)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedBgStroke(Color.parseColor("#F8FBFA"), colorFieldBorder, 12f, 1)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (index != 0) topMargin = dp(8)
                }
            }
            row.addView(ImageView(this).apply {
                setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.DOCUMENT, colorPrimary, dp(15)))
                background = roundedBg(Color.parseColor("#E4F3F1"), 10f)
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                setPadding(dp(8), dp(8), dp(8), dp(8))
            })
            row.addView(text(
                if (isPdf) "PDF রিপোর্ট ${index + 1}" else "ছবি রিপোর্ট ${index + 1}",
                12.5f, Typeface.BOLD, colorDark, Gravity.START
            ).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            row.addView(ImageView(this).apply {
                setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.ARROW_RIGHT, colorPrimary, dp(14)))
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
            })
            row.setOnClickListener {
                dialog.dismiss()
                // FIX: PDF ফাইলে ক্লিক করলে এখন সরাসরি ভিউয়ারে না গিয়ে স্মার্ট চেক হয় —
                // পেইজ সংখ্যা ১০+ এবং সাইজ ৪MB+ হলে "In app" বা "Open in browser" বেছে নেওয়ার কাস্টম ডায়ালগ দেখাবে।
                if (isPdf) openPdfSmart(url, "রিপোর্ট ${index + 1}") else openImageViewer(url = url, title = "রিপোর্ট ${index + 1}")
            }
            card.addView(row)
        }

        card.addView(dialogButton("বন্ধ করুন", colorTextMuted, Color.parseColor("#F1F3F2")) { dialog.dismiss() }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            }
        })

        scrollWrap.addView(card)
        dialog.setContentView(scrollWrap)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.8).toInt()
        )
        dialog.show()
    }

    private fun isPdfUrl(url: String): Boolean = url.substringBefore("?").lowercase().endsWith(".pdf")

    private fun downloadBytes(url: String): ByteArray {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.doInput = true
        connection.connect()
        return connection.getInputStream().use { it.readBytes() }
    }

    // FIX: HEAD রিকোয়েস্ট দিয়ে ফাইল ডাউনলোড না করেই সাইজ (বাইটে) জানার চেষ্টা করে।
    // সার্ভার Content-Length না দিলে বা কোনো কারণে রিকোয়েস্ট ব্যর্থ হলে -1 রিটার্ন করবে,
    // তখন ডাউনলোড করেই আসল সাইজ যাচাই করা হবে (openPdfSmart এর else ব্রাঞ্চে)।
    private fun getRemoteFileSizeBytes(url: String): Long {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()
            connection.contentLengthLong
        } catch (e: Exception) {
            -1L
        } finally {
            try { connection?.disconnect() } catch (e: Exception) { }
        }
    }

    // FIX: PDF ফাইলটি ডাউনলোড করে ক্যাশ ডিরেক্টরিতে একটি টেম্প ফাইল হিসেবে সংরক্ষণ করে।
    private fun downloadToTempFile(url: String): File {
        val bytes = downloadBytes(url)
        val file = File(cacheDir, "admin_report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    // FIX: লোকাল ফাইল থেকে শুধু পেইজ সংখ্যা বের করে (রেন্ডার না করেই), পরে renderer বন্ধ করে দেয়।
    private fun getPdfPageCount(file: File): Int {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) {
            -1
        } finally {
            try { renderer?.close() } catch (e: Exception) { }
            try { pfd?.close() } catch (e: Exception) { }
        }
    }

    // FIX: PDF ক্লিকের মূল ডিসিশন ফাংশন —
    // ১) আগে HEAD দিয়ে সাইজ যাচাই করে, ৪MB এর কম নিশ্চিত হলে সরাসরি in-app viewer এ খোলে।
    // ২) সাইজ অজানা বা ৪MB+ হলে ফাইল ডাউনলোড করে পেইজ সংখ্যা ও আসল সাইজ যাচাই করে।
    // ৩) পেইজ ১০+ এবং সাইজ ৪MB+ দুটো শর্তই পূরণ হলে — "In app" / "Open in browser" কাস্টম ডায়ালগ দেখায়।
    // ৪) অন্যথায় সরাসরি ইতিমধ্যে ডাউনলোড করা ফাইল দিয়েই in-app viewer এ রেন্ডার করে (re-download লাগে না)।
    private fun openPdfSmart(url: String, title: String = "ডকুমেন্ট") {
        lifecycleScope.launch {
            val remoteSize = withContext(Dispatchers.IO) { getRemoteFileSizeBytes(url) }

            if (remoteSize in 0 until PDF_LARGE_SIZE_BYTES) {
                openPdfViewer(url, title)
                return@launch
            }

            Toast.makeText(this@AdminActivity, "ফাইল পরীক্ষা করা হচ্ছে...", Toast.LENGTH_SHORT).show()
            try {
                val file = withContext(Dispatchers.IO) { downloadToTempFile(url) }
                val pageCount = withContext(Dispatchers.IO) { getPdfPageCount(file) }
                val actualSize = file.length()

                if (pageCount >= PDF_LARGE_PAGE_THRESHOLD && actualSize >= PDF_LARGE_SIZE_BYTES) {
                    showPdfOpenOptionsDialog(url, title, file, pageCount, actualSize)
                } else {
                    renderPdfFromLocalFile(file, title)
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminActivity, "PDF লোড ব্যর্থ: ${e.message ?: "অজানা সমস্যা"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // FIX: বড় PDF এর জন্য "In app" নাকি "Open in browser" — এই দুটির মধ্যে বেছে নেওয়ার কাস্টম ডায়ালগ।
    private fun showPdfOpenOptionsDialog(url: String, title: String, localFile: File, pageCount: Int, sizeBytes: Long) {
        val sizeMB = sizeBytes / (1024f * 1024f)
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.WHITE, 22f)
            setPadding(dp(22), dp(24), dp(22), dp(20))
        }
        card.addView(ImageView(this).apply {
            setImageDrawable(HomeActivity.VectorIconDrawable(HomeActivity.VectorIconDrawable.IconType.DOCUMENT, colorPrimary, dp(22)))
            background = roundedBg(Color.parseColor("#E4F3F1"), 14f)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            setPadding(dp(11), dp(11), dp(11), dp(11))
        })
        card.addView(text("বড় ডকুমেন্ট", 16f, Typeface.BOLD, colorDark, Gravity.START).apply {
            setPadding(0, dp(12), 0, 0)
        })
        card.addView(text(
            "এই ফাইলে $pageCount টি পৃষ্ঠা আছে এবং সাইজ প্রায় ${"%.1f".format(sizeMB)} MB। কিভাবে দেখতে চান?",
            12.5f, Typeface.NORMAL, colorTextMuted, Gravity.START
        ).apply {
            setPadding(0, dp(6), 0, dp(18))
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        card.addView(dialogButton("অ্যাপে দেখুন (In App)", Color.WHITE, colorPrimary) {
            dialog.dismiss()
            renderPdfFromLocalFile(localFile, title)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        card.addView(space(dp(10)))
        card.addView(dialogButton("ব্রাউজারে খুলুন (Open in Browser)", colorPrimary, Color.parseColor("#E4F3F1")) {
            dialog.dismiss()
            try { localFile.delete() } catch (e: Exception) { }
            openPdfInBrowser(url)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        card.addView(space(dp(10)))
        card.addView(dialogButton("বাতিল", colorTextMuted, Color.parseColor("#F1F3F2")) {
            dialog.dismiss()
            try { localFile.delete() } catch (e: Exception) { }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        dialog.setContentView(card)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.86).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.setCancelable(true)
        dialog.setOnCancelListener {
            try { localFile.delete() } catch (e: Exception) { }
        }
        dialog.show()
    }

    // FIX: ডিভাইসের ডিফল্ট ব্রাউজার/PDF হ্যান্ডলার দিয়ে URL টি সরাসরি ওপেন করে।
    private fun openPdfInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "ব্রাউজার খোলা যায়নি", Toast.LENGTH_SHORT).show()
        }
    }

    // FIX: এখন এটি শুধু URL থেকে ডাউনলোড করে openSwipeablePdf কে কল করে —
    // মূল রেন্ডারিং লজিকটি লেজি-লোডিং সোয়াইপেবল ভিউয়ারে সরানো হয়েছে (নিচে দেখুন)।
    private fun openPdfViewer(url: String, title: String = "ডকুমেন্ট") {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val bgColor = Color.parseColor("#1A1A1A")
        dialog.window?.setBackgroundDrawable(ColorDrawable(bgColor))

        val views = buildPdfViewerViews(bgColor, title)
        views.closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(views.root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()

        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { downloadToTempFile(url) }
                openSwipeablePdf(file, views, title, dialog, deleteFileOnClose = true)
            } catch (e: Exception) {
                views.progress.visibility = View.GONE
                views.loadingLabel.visibility = View.GONE
                Toast.makeText(this@AdminActivity, "PDF লোড ব্যর্থ: ${e.message ?: "অজানা সমস্যা"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // FIX: ইতিমধ্যে ডাউনলোড করা একটি লোকাল PDF ফাইল সরাসরি সোয়াইপেবল ফুল-স্ক্রিন ভিউয়ারে খোলে।
    // openPdfSmart এবং showPdfOpenOptionsDialog "In app" চাপলে এটি ব্যবহার করে, তাই re-download লাগে না।
    private fun renderPdfFromLocalFile(file: File, title: String = "ডকুমেন্ট") {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val bgColor = Color.parseColor("#1A1A1A")
        dialog.window?.setBackgroundDrawable(ColorDrawable(bgColor))

        val views = buildPdfViewerViews(bgColor, title)
        views.closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(views.root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()

        lifecycleScope.launch {
            try {
                openSwipeablePdf(file, views, title, dialog, deleteFileOnClose = true)
            } catch (e: Exception) {
                views.progress.visibility = View.GONE
                views.loadingLabel.visibility = View.GONE
                Toast.makeText(this@AdminActivity, "PDF লোড ব্যর্থ: ${e.message ?: "অজানা সমস্যা"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // FIX: root + ViewPager2 (swipe নেভিগেশনের জন্য) + পেইজ ইন্ডিকেটর ব্যাজ + টপবার (টাইটেল/ক্লোজ)
    // + প্রাথমিক লোডিং স্পিনার — এই সবগুলো ধারণ করে। openPdfViewer ও renderPdfFromLocalFile দুটোই এটি শেয়ার করে।
    private data class PdfViewerViews(
        val root: FrameLayout,
        val viewPager: ViewPager2,
        val progress: ProgressBar,
        val loadingLabel: TextView,
        val titleText: TextView,
        val pageIndicator: TextView,
        val closeBtn: ImageView
    )

    // FIX: PDF ফুল-স্ক্রিন ভিউয়ারের UI কাঠামো তৈরি করে (ViewPager2 সহ)।
    // closeBtn টি রিটার্ন করা হয় যাতে কলার নিজের Dialog রেফারেন্স দিয়ে ক্লিক লিসেনার সেট করতে পারে।
    private fun buildPdfViewerViews(bgColor: Int, title: String): PdfViewerViews {
        val root = FrameLayout(this).apply {
            setBackgroundColor(bgColor)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // FIX: এখন পেইজগুলো একটার নিচে একটা স্ক্রল করে দেখানোর বদলে ViewPager2 দিয়ে
        // সোয়াইপ করে এক পেইজ থেকে আরেক পেইজে যাওয়া যায়। offscreenPageLimit = 1 রাখায়
        // পাশের একটি করে পেইজ আগে থেকেই তৈরি (lazy) থাকে, ফলে সোয়াইপ মসৃণ হয়।
        val viewPager = ViewPager2(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            offscreenPageLimit = 1
            visibility = View.GONE
        }

        val progress = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
        }
        val loadingLabel = text("PDF লোড হচ্ছে, একটু অপেক্ষা করুন...", 11.5f, Typeface.NORMAL, Color.parseColor("#CCCCCC"), Gravity.CENTER).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                topMargin = dp(56)
            }
        }

        // FIX: নিচে ভাসমান "X / Y" পেইজ ইন্ডিকেটর ব্যাজ — সোয়াইপ করার সাথে সাথে আপডেট হয়,
        // আগে যেভাবে প্রতিটি পেইজের নিচে "পৃষ্ঠা X/Y" লেখা থাকতো তার বদলে এটি এখন একটি ভাসমান ব্যাজ।
        val pageIndicator = text("", 11.5f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
            background = roundedBg(Color.argb(160, 0, 0, 0), 30f)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(22)
            }
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(34), dp(14), dp(14))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(210, 0, 0, 0), Color.TRANSPARENT))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
            }
        }
        val closeBtn = ImageView(this).apply {
            setImageDrawable(CloseIconDrawable(Color.WHITE, dp(2).toFloat()))
            background = roundedBg(Color.argb(60, 255, 255, 255), 30f)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            val pad = dp(9)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
        }
        val titleText = text(title, 14f, Typeface.BOLD, Color.WHITE, Gravity.START).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }
        topBar.addView(closeBtn)
        topBar.addView(titleText)

        root.addView(viewPager)
        root.addView(progress)
        root.addView(loadingLabel)
        root.addView(pageIndicator)
        root.addView(topBar)

        return PdfViewerViews(root, viewPager, progress, loadingLabel, titleText, pageIndicator, closeBtn)
    }

    // FIX: মূল সেটআপ ফাংশন — PdfRenderer একবার খোলে, ViewPager2 তে lazy-loading adapter বসায়,
    // এবং ডায়ালগ বন্ধ হলে (dialog.setOnDismissListener) renderer/pfd/temp-file পরিষ্কার করে।
    // যেহেতু পেইজগুলো এখন lazily (দরকার হলে তবেই) রেন্ডার হয়, তাই renderer সম্পূর্ণ ডায়ালগের
    // লাইফটাইম জুড়ে খোলা রাখতে হয় — আগের মতো ফাংশনের শেষে try/finally তে বন্ধ করা যাবে না।
    private suspend fun openSwipeablePdf(
        file: File,
        views: PdfViewerViews,
        title: String,
        dialog: Dialog,
        deleteFileOnClose: Boolean
    ) {
        val pfd = withContext(Dispatchers.IO) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: Exception) {
            withContext(Dispatchers.IO) {
                try { pfd.close() } catch (ex: Exception) { }
                if (deleteFileOnClose) { try { file.delete() } catch (ex: Exception) { } }
            }
            throw e
        }
        val pageCount = renderer.pageCount

        views.progress.visibility = View.GONE
        views.loadingLabel.visibility = View.GONE
        views.titleText.text = "$title ($pageCount পৃষ্ঠা)"
        views.pageIndicator.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
        views.pageIndicator.text = "1 / $pageCount"

        // FIX: PdfRenderer থ্রেড-সেফ নয়, তাই একসাথে একাধিক পেইজ রেন্ডার হওয়া ঠেকাতে Mutex ব্যবহার করা হচ্ছে।
        val rendererMutex = Mutex()
        val screenWidthPx = resources.displayMetrics.widthPixels
        val adapter = PdfPageAdapter(this@AdminActivity, renderer, rendererMutex, pageCount, screenWidthPx, lifecycleScope)

        views.viewPager.adapter = adapter
        views.viewPager.visibility = View.VISIBLE
        views.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                views.pageIndicator.text = "${position + 1} / $pageCount"
            }
        })

        // FIX: ডায়ালগ বন্ধ হলে (ক্লোজ বাটন বা ব্যাক-প্রেস, যেকোনো উপায়ে) pending render job,
        // ক্যাশে থাকা bitmap, renderer, pfd এবং প্রয়োজনে temp ফাইল — সব ক্লিনআপ হয়।
        dialog.setOnDismissListener {
            adapter.cancelAllAndClear()
            try { renderer.close() } catch (e: Exception) { }
            lifecycleScope.launch(Dispatchers.IO) {
                try { pfd.close() } catch (e: Exception) { }
                if (deleteFileOnClose) {
                    try { file.delete() } catch (e: Exception) { }
                }
            }
        }
    }

    // FIX: ViewPager2 এর RecyclerView.Adapter — প্রতিটা পেইজ শুধু স্ক্রিনে আসার সময় (বা তার পাশের
    // offscreenPageLimit রেঞ্জে) রেন্ডার হয় (lazy-loading), এবং সাম্প্রতিক কয়েকটি বাদে বাকি bitmap
    // ক্যাশ থেকে সরিয়ে recycle করে দেয় যাতে মেমোরি কম লাগে। প্রতিটি পেইজে ট্যাপ করলে আগের মতোই
    // pinch-zoom করা যায় এমন ফুল-স্ক্রিন ইমেজ ভিউয়ার খোলে।
    private class PdfPageAdapter(
        private val activity: AdminActivity,
        private val renderer: PdfRenderer,
        private val rendererMutex: Mutex,
        private val pageCount: Int,
        private val screenWidthPx: Int,
        private val scope: CoroutineScope
    ) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

        // FIX: সর্বোচ্চ ৫টি পেইজের bitmap ক্যাশে রাখা হয় (বর্তমান + আশেপাশের কয়েকটি)।
        // এর বেশি হলে সবচেয়ে পুরনোটা স্বয়ংক্রিয়ভাবে recycle হয়ে যায় — এটাই মূল মেমোরি-সাশ্রয়ী lazy অংশ।
        private val maxCacheSize = 5
        private val bitmapCache = object : LinkedHashMap<Int, Bitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>): Boolean {
                if (size > maxCacheSize) {
                    val bmp = eldest.value
                    if (!bmp.isRecycled) bmp.recycle()
                    return true
                }
                return false
            }
        }
        private val renderJobs = mutableMapOf<Int, Job>()

        inner class PageViewHolder(
            val frame: FrameLayout,
            val imageView: ImageView,
            val progress: ProgressBar
        ) : RecyclerView.ViewHolder(frame)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val frame = FrameLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val imageView = ImageView(activity).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    val m = activity.dp(10)
                    setMargins(m, m, m, m)
                }
                isClickable = true
                isFocusable = true
            }
            val progress = ProgressBar(activity).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                }
                indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
            }
            frame.addView(imageView)
            frame.addView(progress)
            return PageViewHolder(frame, imageView, progress)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val cached = bitmapCache[position]
            if (cached != null && !cached.isRecycled) {
                holder.imageView.setImageBitmap(cached)
                holder.progress.visibility = View.GONE
            } else {
                holder.imageView.setImageBitmap(null)
                holder.progress.visibility = View.VISIBLE
                renderJobs[position]?.cancel()
                val job = scope.launch {
                    val bmp = renderPage(position)
                    if (bmp != null) {
                        bitmapCache[position] = bmp
                        if (holder.bindingAdapterPosition == position) {
                            holder.imageView.setImageBitmap(bmp)
                            holder.progress.visibility = View.GONE
                        }
                    } else if (holder.bindingAdapterPosition == position) {
                        holder.progress.visibility = View.GONE
                    }
                }
                renderJobs[position] = job
            }
            holder.imageView.setOnClickListener {
                val bmp = bitmapCache[position]
                if (bmp != null && !bmp.isRecycled) {
                    activity.openImageViewer(bitmap = bmp, title = "পৃষ্ঠা ${position + 1}/$pageCount")
                }
            }
        }

        override fun onViewRecycled(holder: PageViewHolder) {
            val pos = holder.bindingAdapterPosition
            renderJobs[pos]?.cancel()
            renderJobs.remove(pos)
            holder.imageView.setOnClickListener(null)
        }

        override fun getItemCount(): Int = pageCount

        // FIX: ডায়ালগ বন্ধ হওয়ার সময় সব pending render job বাতিল করে এবং ক্যাশে থাকা bitmap গুলো recycle করে।
        fun cancelAllAndClear() {
            renderJobs.values.forEach { it.cancel() }
            renderJobs.clear()
            bitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
            bitmapCache.clear()
        }

        private suspend fun renderPage(index: Int): Bitmap? = withContext(Dispatchers.IO) {
            rendererMutex.withLock {
                try {
                    val page = renderer.openPage(index)
                    val rawScale = (screenWidthPx.toFloat() / page.width.toFloat()) * 2f
                    val safeScale = rawScale.coerceIn(1f, 4f)
                    val outW = (page.width * safeScale).toInt().coerceAtLeast(1)
                    val outH = (page.height * safeScale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                } catch (e: Exception) {
                    null
                }
            }
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

    private fun communicationButton(label: String, icon: Drawable, color: Int, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedBg(Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)), 12f)
            setPadding(dp(6), dp(11), dp(6), dp(11))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(ImageView(this@AdminActivity).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginEnd = dp(5) }
            })
            addView(text(label, 11f, Typeface.BOLD, color, Gravity.CENTER))
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

    private fun roundedBgStroke(fillColor: Int, strokeColor: Int, radiusDp: Float, strokeWidthDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(fillColor)
        setStroke(dp(strokeWidthDp), strokeColor)
    }

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

    private class CloseIconDrawable(iconColor: Int, private val strokeWidthPx: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            color = iconColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val inset = minOf(w, h) * 0.28f
            canvas.drawLine(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset, paint)
            canvas.drawLine(b.right - inset, b.top + inset, b.left + inset, b.bottom - inset, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class SearchIconDrawable(iconColor: Int, private val strokeWidthPx: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            color = iconColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val radius = minOf(w, h) * 0.30f
            val cx = b.left + w * 0.42f
            val cy = b.top + h * 0.42f
            canvas.drawCircle(cx, cy, radius, paint)
            val angleOffset = radius * 0.72f
            canvas.drawLine(cx + angleOffset, cy + angleOffset, b.left + w * 0.86f, b.top + h * 0.86f, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class ChatIconDrawable(iconColor: Int, private val strokeWidthPx: Float) : Drawable() {
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

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class VideoCallIconDrawable(iconColor: Int) : Drawable() {
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

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class AudioCallIconDrawable(iconColor: Int) : Drawable() {
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

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class ZoomableImageView(context: Context) : ImageView(context) {
        private val imgMatrix = Matrix()
        private var lastX = 0f
        private var lastY = 0f
        private var isDragging = false
        private var minScale = 1f
        private val maxScale = 8f
        private var currentScale = 1f

        private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                var factor = detector.scaleFactor
                val projected = currentScale * factor
                if (projected < minScale) factor = minScale / currentScale
                if (projected > maxScale) factor = maxScale / currentScale
                currentScale *= factor
                imgMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                imageMatrix = imgMatrix
                return true
            }
        })

        init {
            scaleType = ScaleType.MATRIX
            setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.x; lastY = event.y; isDragging = true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging && !scaleDetector.isInProgress) {
                            val dx = event.x - lastX
                            val dy = event.y - lastY
                            imgMatrix.postTranslate(dx, dy)
                            imageMatrix = imgMatrix
                            lastX = event.x; lastY = event.y
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                    }
                }
                true
            }
        }

        fun resetZoomFit() {
            post {
                val d = drawable ?: return@post
                val vw = width.toFloat()
                val vh = height.toFloat()
                val dw = d.intrinsicWidth.toFloat()
                val dh = d.intrinsicHeight.toFloat()
                if (vw <= 0 || vh <= 0 || dw <= 0 || dh <= 0) return@post
                val scale = minOf(vw / dw, vh / dh)
                minScale = scale
                currentScale = scale
                imgMatrix.reset()
                imgMatrix.postScale(scale, scale)
                imgMatrix.postTranslate((vw - dw * scale) / 2f, (vh - dh * scale) / 2f)
                imageMatrix = imgMatrix
            }
        }
    }

    private fun openImageViewer(bitmap: Bitmap? = null, url: String? = null, title: String = "ছবি") {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val zoomImage = ZoomableImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val progress = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
            visibility = if (bitmap != null) View.GONE else View.VISIBLE
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(34), dp(14), dp(14))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(190, 0, 0, 0), Color.TRANSPARENT))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
            }
        }
        val closeBtn = ImageView(this).apply {
            setImageDrawable(CloseIconDrawable(Color.WHITE, dp(2).toFloat()))
            background = roundedBg(Color.argb(60, 255, 255, 255), 30f)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            val pad = dp(9)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }
        val titleText = text(title, 14f, Typeface.BOLD, Color.WHITE, Gravity.START).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }
        topBar.addView(closeBtn)
        topBar.addView(titleText)

        root.addView(zoomImage)
        root.addView(progress)
        root.addView(topBar)
        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()

        fun applyBitmap(bmp: Bitmap) {
            progress.visibility = View.GONE
            zoomImage.setImageBitmap(bmp)
            zoomImage.resetZoomFit()
        }

        if (bitmap != null) {
            applyBitmap(bitmap)
        } else if (url != null) {
            lifecycleScope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) { downloadBytes(url) }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) applyBitmap(bmp) else {
                        progress.visibility = View.GONE
                        Toast.makeText(this@AdminActivity, "ছবি লোড করা যায়নি", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    progress.visibility = View.GONE
                    Toast.makeText(this@AdminActivity, "ছবি লোড ব্যর্থ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
