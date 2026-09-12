package com.konasl.nagad

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryLight = Color.parseColor("#16897A")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorAccent = Color.parseColor("#F59E0B")
    private val colorBg = Color.parseColor("#F4F7F6")
    private val colorTextMuted = Color.parseColor("#6B7280")
    private val colorDark = Color.parseColor("#111827")
    private val colorCard = Color.WHITE
    private val colorFieldBorder = Color.parseColor("#E7ECEA")

    // ডাক্তারের তথ্য
    private val doctorPhoneForCall = "+8801632336631" // TODO: বসান

    private lateinit var appointmentsContainer: LinearLayout
    private lateinit var greetingText: TextView
    private lateinit var actionsGrid: GridLayout

    // ---------------- ন্যাভিগেশন ----------------
    private enum class Tab { HOME, APPOINTMENTS, PROFILE }
    private var currentTab = Tab.HOME

    private lateinit var homePanel: View
    private lateinit var appointmentsPanel: View
    private lateinit var profilePanel: View

    private data class NavItemViews(val root: LinearLayout, val icon: TextView, val label: TextView)
    private lateinit var navHome: NavItemViews
    private lateinit var navAppointments: NavItemViews
    private lateinit var navProfile: NavItemViews

    // ---------------- প্রোফাইল প্যানেল ----------------
    private lateinit var profileAvatar: PatientAvatarView
    private lateinit var profileNameText: TextView
    private lateinit var profilePhoneText: TextView
    private lateinit var profileDetailsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SupabaseClient.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(colorBg)
        }

        // ---------------- HOME PANEL ----------------
        val homeScroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val homeContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(100))
        }

        // ---------------- HEADER ----------------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark))
            setPadding(dp(22), dp(44), dp(22), dp(26))
        }
        val headerTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        greetingText = text("স্বাগতম, ${SupabaseClient.getName(this) ?: "রোগী"}", 18f, Typeface.BOLD, Color.WHITE, Gravity.START)
        val appTitle = text("SunnyCare ☀ Doctor", 12f, Typeface.NORMAL, Color.argb(210, 255, 255, 255), Gravity.START)
        headerTextCol.addView(greetingText)
        headerTextCol.addView(appTitle)

        val logoutBtn = TextView(this).apply {
            text = "লগ আউট  ⎋"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            background = roundedBg(Color.argb(50, 255, 255, 255), 30f)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { onLogout() }
        }
        header.addView(headerTextCol)
        header.addView(logoutBtn)

        // ---------------- DOCTOR CARD (redesigned, unique) ----------------
        val doctorCard = buildDoctorCard()

        // ---------------- QUICK ACTIONS ----------------
        actionsGrid = GridLayout(this).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(14), dp(18), dp(14), 0)
            }
        }
        actionsGrid.addView(actionCard("📅", "অ্যাপয়েন্টমেন্ট বুক", colorPrimary) {
            startActivity(Intent(this, BookAppointmentActivity::class.java))
        })
        actionsGrid.addView(actionCard("📞", "ডাক্তারকে কল করুন", Color.parseColor("#2563EB")) { callDoctor() })
        actionsGrid.addView(actionCard("💊", "প্রেসক্রিপশন", Color.parseColor("#7C3AED")) { showComingSoon("প্রেসক্রিপশন হিস্ট্রি") })
        actionsGrid.addView(actionCard("👤", "আমার প্রোফাইল", Color.parseColor("#DB2777")) { switchTab(Tab.PROFILE) })

        // ---------------- ADMIN-ONLY: পেমেন্ট ভেরিফিকেশন ----------------
        if (SupabaseClient.isAdmin(this)) {
            actionsGrid.addView(actionCard("✅", "পেমেন্ট ভেরিফিকেশন", Color.parseColor("#059669")) {
                startActivity(Intent(this, AdminPaymentVerificationActivity::class.java))
            })
        }

        homeContent.addView(header)
        homeContent.addView(doctorCard)
        homeContent.addView(actionsGrid)
        homeScroll.addView(homeContent)
        homePanel = homeScroll

        // ---------------- APPOINTMENTS PANEL ----------------
        val appointmentsScroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
        }
        val appointmentsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(100))
        }
        val appointmentsTopBar = panelTopBar("আমার অ্যাপয়েন্টমেন্টসমূহ")
        appointmentsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), 0)
        }
        appointmentsContent.addView(appointmentsTopBar)
        appointmentsContent.addView(appointmentsContainer)
        appointmentsScroll.addView(appointmentsContent)
        appointmentsPanel = appointmentsScroll

        // ---------------- PROFILE PANEL (এই একটিভিটির মধ্যেই) ----------------
        val profileScroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
        }
        profilePanel = profileScroll
        profileScroll.addView(buildProfilePanel())

        // ---------------- PANELS HOST ----------------
        val panelsHost = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        panelsHost.addView(homePanel)
        panelsHost.addView(appointmentsPanel)
        panelsHost.addView(profilePanel)

        // ---------------- BOTTOM NAVIGATION BAR ----------------
        val bottomNav = buildBottomNav()

        root.addView(panelsHost)
        root.addView(bottomNav)
        setContentView(root)

        loadAppointments()
        loadProfileDetails()
        updateNavStyle()
    }

    override fun onResume() {
        super.onResume()
        loadAppointments()
    }

    // ------------------------------------------------------------------
    // ট্যাব সুইচিং
    // ------------------------------------------------------------------
    private fun switchTab(tab: Tab) {
        currentTab = tab
        homePanel.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        appointmentsPanel.visibility = if (tab == Tab.APPOINTMENTS) View.VISIBLE else View.GONE
        profilePanel.visibility = if (tab == Tab.PROFILE) View.VISIBLE else View.GONE
        updateNavStyle()
    }

    private fun updateNavStyle() {
        listOf(navHome to Tab.HOME, navAppointments to Tab.APPOINTMENTS, navProfile to Tab.PROFILE).forEach { (item, tab) ->
            val active = tab == currentTab
            val color = if (active) colorPrimary else colorTextMuted
            item.icon.setTextColor(color)
            item.label.setTextColor(color)
            item.label.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg(Color.WHITE, 24f)
            setPadding(dp(6), dp(10), dp(6), dp(10))
            elevation = dp(16).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
                }
            }
            clipToOutline = true
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                setMargins(dp(18), 0, dp(18), dp(16))
            }
        }
        navHome = navItem("🏠", "হোম") { switchTab(Tab.HOME) }
        navAppointments = navItem("📅", "অ্যাপয়েন্টমেন্ট") { switchTab(Tab.APPOINTMENTS) }
        navProfile = navItem("👤", "প্রোফাইল") { switchTab(Tab.PROFILE) }
        nav.addView(navHome.root)
        nav.addView(navAppointments.root)
        nav.addView(navProfile.root)
        return nav
    }

    private fun navItem(emoji: String, labelText: String, onClick: () -> Unit): NavItemViews {
        val icon = text(emoji, 19f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER)
        val labelView = text(labelText, 10.5f, Typeface.BOLD, colorTextMuted, Gravity.CENTER).apply {
            setPadding(0, dp(3), 0, 0)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, dp(6), 0, dp(6))
            isClickable = true
            isFocusable = true
            addView(icon)
            addView(labelView)
            setOnClickListener { onClick() }
        }
        return NavItemViews(root, icon, labelView)
    }

    private fun panelTopBar(titleText: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark))
        setPadding(dp(22), dp(44), dp(22), dp(22))
        addView(text(titleText, 18f, Typeface.BOLD, Color.WHITE, Gravity.START))
    }

    // ------------------------------------------------------------------
    // ডাক্তারের কার্ড - ইউনিক ও প্রিমিয়াম ডিজাইন
    // ------------------------------------------------------------------
    private fun buildDoctorCard(): LinearLayout {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(colorCard, 22f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(-20), dp(18), 0)
            }
            elevation = dp(14).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(22).toFloat())
                }
            }
            clipToOutline = true
        }

        // উপরে একটা সরু গ্রেডিয়েন্ট রিবন - কার্ডকে আলাদা করে চেনায়
        val ribbon = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(colorAccent, colorPrimary, colorPrimaryDark))
        }
        outer.addView(ribbon)

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        // নাম + অ্যাভাটার + এভেইলেবিলিটি স্ট্যাটাস
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val avatarWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
        }
        avatarWrap.addView(DoctorAvatarView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(64), dp(64))
        })
        avatarWrap.addView(View(this).apply {
            background = roundedBg(Color.parseColor("#22C55E"), 20f)
            layoutParams = FrameLayout.LayoutParams(dp(14), dp(14)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
        })
        val doctorTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(14) }
        }
        doctorTextCol.addView(text("ডা. মাসুম বিল্লাহ সানি", 16.5f, Typeface.BOLD, colorDark, Gravity.START))
        doctorTextCol.addView(text("মেডিসিন, শিশু ও ডায়াবেটিস বিশেষজ্ঞ", 12f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
            setPadding(0, dp(2), 0, 0)
        })
        doctorTextCol.addView(text("🟢 এখন অনলাইনে উপলব্ধ", 11f, Typeface.BOLD, Color.parseColor("#16A34A"), Gravity.START).apply {
            setPadding(0, dp(6), 0, 0)
        })
        topRow.addView(avatarWrap)
        topRow.addView(doctorTextCol)
        inner.addView(topRow)

        // রেটিং রো
        val ratingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        ratingRow.addView(text("★★★★★", 13f, Typeface.BOLD, colorAccent, Gravity.START))
        ratingRow.addView(text(" ৪.৮  •  ৫০০+ রোগী দেখেছেন", 11.5f, Typeface.NORMAL, colorTextMuted, Gravity.START))
        inner.addView(ratingRow)

        // ডিগ্রি
        inner.addView(
            text(
                "এম.বি.বি.এস (সি.ইউ), ডি.এম.ইউ (আল্ট্রা), পিজিটি,\nএম.সি.জি.পি (মেডিসিন ও শিশু), সি.সি.ডি (ডায়াবেটিস- বারডেম, ঢাকা)",
                11f, Typeface.NORMAL, colorTextMuted, Gravity.START
            ).apply { setPadding(0, dp(12), 0, 0) }
        )

        // ভেরিফাইড ব্যাজ
        inner.addView(
            text("✓ বি.এম.ডি.সি এ-১৭৬৩০ • Verified", 10.5f, Typeface.BOLD, colorPrimary, Gravity.START).apply {
                background = roundedBg(Color.parseColor("#E4F3F1"), 30f)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(12)
                }
            }
        )

        // পাতলা বিভাজক
        inner.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(16) }
            setBackgroundColor(colorFieldBorder)
        })

        // বিশেষজ্ঞতা - স্ক্রলযোগ্য চিপস
        val expertise = listOf("নাক-কান-গলা", "এলার্জি", "শ্বাসকষ্ট", "চর্মরোগ", "উচ্চ রক্তচাপ", "বাত ব্যাথা")
        val chipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        expertise.forEach { item ->
            chipsRow.addView(text(item, 11f, Typeface.BOLD, colorPrimaryDark, Gravity.CENTER).apply {
                background = roundedBg(Color.parseColor("#EEF6F4"), 30f)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(8)
                }
            })
        }
        val chipsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
            addView(chipsRow)
        }
        inner.addView(chipsScroll)

        outer.addView(inner)
        return outer
    }

    // ------------------------------------------------------------------
    // প্রোফাইল প্যানেল - এই একটিভিটির মধ্যেই সম্পূর্ণ প্রোফাইল দেখা যায়
    // ------------------------------------------------------------------
    private fun buildProfilePanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(110))
        }
        panel.addView(panelTopBar("আমার প্রোফাইল"))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), 0)
        }

        val headCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(colorCard, 22f)
            setPadding(dp(20), dp(24), dp(20), dp(24))
            elevation = dp(8).toFloat()
        }
        profileAvatar = PatientAvatarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(84)).apply { gravity = Gravity.CENTER_HORIZONTAL }
            initial = (SupabaseClient.getName(this@HomeActivity)?.trim()?.firstOrNull() ?: 'র').toString()
        }
        profileNameText = text(SupabaseClient.getName(this) ?: "রোগী", 17f, Typeface.BOLD, colorDark, Gravity.CENTER).apply {
            setPadding(0, dp(14), 0, 0)
        }
        profilePhoneText = text(SupabaseClient.getPhone(this) ?: "", 12.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER).apply {
            setPadding(0, dp(4), 0, 0)
        }
        headCard.addView(profileAvatar)
        headCard.addView(profileNameText)
        headCard.addView(profilePhoneText)
        body.addView(headCard)

        profileDetailsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(colorCard, 20f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            }
            elevation = dp(6).toFloat()
        }
        body.addView(profileDetailsContainer)

        val logoutFromProfile = TextView(this).apply {
            text = "লগ আউট করুন"
            setTextColor(Color.parseColor("#DC2626"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = roundedBg(Color.parseColor("#FEE2E2"), 14f)
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20)
            }
            setOnClickListener { onLogout() }
        }
        body.addView(logoutFromProfile)

        panel.addView(body)
        return panel
    }

    private fun profileRow(labelText: String, value: String): View? {
        if (value.isBlank()) return null
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(text(labelText, 12.5f, Typeface.BOLD, colorTextMuted, Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(text(value, 12.5f, Typeface.NORMAL, colorDark, Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun loadProfileDetails() {
        val phone = SupabaseClient.getPhone(this) ?: return
        lifecycleScope.launch {
            val result = SupabaseClient.findPatientByPhone(phone)
            result.onSuccess { patient ->
                if (patient == null) return@onSuccess
                profileDetailsContainer.removeAllViews()
                val rows = listOf(
                    "বয়স" to patient.optString("age", ""),
                    "লিঙ্গ" to patient.optString("gender", ""),
                    "রক্তের গ্রুপ" to patient.optString("blood_group", ""),
                    "ঠিকানা" to patient.optString("address", ""),
                    "জরুরি যোগাযোগ" to patient.optString("emergency_contact", ""),
                    "মেডিকেল হিস্ট্রি" to patient.optString("medical_history", "")
                )
                var added = false
                rows.forEach { (labelText, value) ->
                    profileRow(labelText, value)?.let {
                        if (added) {
                            profileDetailsContainer.addView(View(this@HomeActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                                setBackgroundColor(colorFieldBorder)
                            })
                        }
                        profileDetailsContainer.addView(it)
                        added = true
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    private fun loadAppointments() {
        val patientId = SupabaseClient.getPatientId(this) ?: return
        appointmentsContainer.removeAllViews()
        val loadingText = text("লোড হচ্ছে...", 12.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER)
        appointmentsContainer.addView(loadingText)

        lifecycleScope.launch {
            val result = SupabaseClient.getAppointments(patientId)
            appointmentsContainer.removeAllViews()
            result.onSuccess { rows ->
                if (rows.length() == 0) {
                    appointmentsContainer.addView(
                        text("কোনো অ্যাপয়েন্টমেন্ট নেই। নতুন বুক করুন।", 12.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER).apply {
                            setPadding(0, dp(20), 0, dp(20))
                        }
                    )
                } else {
                    for (i in 0 until rows.length()) {
                        val obj = rows.getJSONObject(i)
                        appointmentsContainer.addView(appointmentRow(
                            date = obj.optString("preferred_date"),
                            time = obj.optString("preferred_time"),
                            reason = obj.optString("reason"),
                            status = obj.optString("status")
                        ))
                        appointmentsContainer.addView(space(dp(10)))
                    }
                }
            }.onFailure {
                appointmentsContainer.addView(
                    text("অ্যাপয়েন্টমেন্ট লোড করা যায়নি", 12.5f, Typeface.NORMAL, Color.parseColor("#D32F2F"), Gravity.CENTER)
                )
            }
        }
    }

    private fun appointmentRow(date: String, time: String, reason: String, status: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(colorCard, 16f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(text("$date • $time", 13f, Typeface.BOLD, colorDark, Gravity.START))
        col.addView(text(reason.ifEmpty { "সাধারণ পরামর্শ" }, 11.5f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
            setPadding(0, dp(4), 0, 0)
        })

        val (statusLabel, statusColor) = when (status) {
            "confirmed" -> "কনফার্মড" to Color.parseColor("#16A34A")
            "completed" -> "সম্পন্ন" to Color.parseColor("#2563EB")
            "cancelled" -> "বাতিল" to Color.parseColor("#DC2626")
            else -> "পেন্ডিং" to colorAccent
        }
        val badge = text(statusLabel, 10.5f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
            background = roundedBg(statusColor, 30f)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        row.addView(col)
        row.addView(badge)
        return row
    }

    // ------------------------------------------------------------------
    private fun callDoctor() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$doctorPhoneForCall"))
        startActivity(intent)
    }

    private fun showComingSoon(feature: String) {
        Toast.makeText(this, "$feature শীঘ্রই আসছে", Toast.LENGTH_SHORT).show()
    }

    private fun onLogout() {
        AlertDialog.Builder(this)
            .setTitle("লগ আউট")
            .setMessage("আপনি কি লগ আউট করতে চান?")
            .setPositiveButton("হ্যাঁ") { _, _ ->
                SupabaseClient.logout(this)
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("না", null)
            .show()
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------
    private fun actionCard(emoji: String, label: String, color: Int, onClick: () -> Unit): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBg(colorCard, 18f)
            setPadding(dp(14), dp(20), dp(14), dp(20))
            layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            elevation = dp(2).toFloat()
            setOnClickListener { onClick() }
        }
        val iconCircle = TextView(this).apply {
            text = emoji
            textSize = 22f
            gravity = Gravity.CENTER
            background = roundedBg(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)), 40f)
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        }
        val labelView = text(label, 12f, Typeface.BOLD, colorDark, Gravity.CENTER).apply {
            setPadding(0, dp(10), 0, 0)
        }
        card.addView(iconCircle)
        card.addView(labelView)
        return card
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

    class DoctorAvatarView(context: Context) : View(context) {
        private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val paintOrange = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val paintRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.WHITE
        }

        init { setLayerType(LAYER_TYPE_SOFTWARE, null); paintBg.setShadowLayer(10f, 0f, 4f, Color.argb(70, 0, 0, 0)) }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            paintBg.shader = RadialGradient(w / 2, h / 2, w / 2, Color.parseColor("#16897A"), Color.parseColor("#0A4A42"), Shader.TileMode.CLAMP)
            canvas.drawCircle(w / 2, h / 2, w / 2 - 3f, paintBg)
            canvas.drawCircle(w / 2, h / 2, w / 2 - 3f, paintRing)
            canvas.drawCircle(w / 2, h / 2, w * 0.30f, paintOrange.apply { color = Color.parseColor("#F59E0B") })
            val crossW = w * 0.22f; val crossH = w * 0.07f
            canvas.drawRect(w / 2 - crossW / 2, h / 2 - crossH / 2, w / 2 + crossW / 2, h / 2 + crossH / 2, paintWhite)
            canvas.drawRect(w / 2 - crossH / 2, h / 2 - crossW / 2, w / 2 + crossH / 2, h / 2 + crossW / 2, paintWhite)
        }
    }

    /** রোগীর প্রোফাইল অ্যাভাটার - নামের প্রথম অক্ষর দিয়ে তৈরি (কোনো ছবি ছাড়া) */
    class PatientAvatarView(context: Context) : View(context) {
        var initial: String = "র"
            set(value) { field = value; invalidate() }

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }

        init { textPaint.textSize = 30f * resources.displayMetrics.scaledDensity / resources.displayMetrics.density }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            bgPaint.shader = RadialGradient(w / 2, h / 2, w / 2, Color.parseColor("#16897A"), Color.parseColor("#0A4A42"), Shader.TileMode.CLAMP)
            canvas.drawCircle(w / 2, h / 2, w / 2, bgPaint)
            textPaint.textSize = h * 0.4f
            val cy = h / 2 - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(initial.uppercase(), w / 2, cy, textPaint)
        }
    }
}
