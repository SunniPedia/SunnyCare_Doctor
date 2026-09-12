package com.konasl.nagad

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * ---------------------------------------------------------------------
 * HomeActivity — আপলোড করা "SunnyCare" স্ক্রিনশট (হোম / ডাক্তারের প্রোফাইল /
 * স্বাস্থ্য টিপস) অনুযায়ী পুরো UI রিস্কিন করা হয়েছে:
 *   • Plain mint background, ভারী গ্র্যাডিয়েন্ট হেডারের বদলে হালকা টপ-বার
 *     (অ্যাভাটার + সময়ভিত্তিক শুভেচ্ছা + নোটিফিকেশন বেল)
 *   • ডাক্তারের কার্ড: স্কয়ার অ্যাভাটার, ভেরিফায়েড ব্যাজ, রেটিং পিল,
 *     হাসপাতালের নাম, আউটলাইন এক্সপার্টাইজ চিপস
 *   • Quick actions: বড় টিল "বুক অ্যাপয়েন্টমেন্ট" কার্ড + পাশে দুইটি সাদা কার্ড
 *     (আমার অ্যাপয়েন্টমেন্ট / জরুরি যোগাযোগ), নিচে সেকেন্ডারি অ্যাকশন চিপস সারি
 *     (প্রেসক্রিপশন / প্রোফাইল / [অ্যাডমিন-শুধু] পেমেন্ট ভেরিফিকেশন)
 *   • "আসন্ন অ্যাপয়েন্টমেন্ট" প্রিভিউ (date-badge কার্ড) + "সব দেখুন" লিংক
 *   • নিচে ডার্ক "আজকের স্বাস্থ্য টিপস" ব্যানার কার্ড
 *
 * সমস্ত আগের ফিচার অক্ষুণ্ণ: বটম ন্যাভিগেশন (হোম/অ্যাপয়েন্টমেন্ট/প্রোফাইল),
 * প্রোফাইল প্যানেল, অ্যাপয়েন্টমেন্ট লিস্ট লোডিং, কল/লগআউট/অ্যাডমিন অ্যাকশন ইত্যাদি।
 * ---------------------------------------------------------------------
 */
class HomeActivity : AppCompatActivity() {

    // ---------------------------------------------------------------
    // Palette — আপলোড করা ছবির ডিজাইন সিস্টেম অনুযায়ী
    // ---------------------------------------------------------------
    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryLight = Color.parseColor("#16897A")
    private val colorPrimaryDark = Color.parseColor("#0A2A26")
    private val colorAccent = Color.parseColor("#F59E0B")
    private val colorAccentSoft = Color.parseColor("#FEF3C7")
    private val colorScreenBg = Color.parseColor("#F8FFFE")
    private val colorCard = Color.WHITE
    private val colorBorder = Color.parseColor("#E6EFED")
    private val colorTextMuted = Color.parseColor("#6B7C7A")
    private val colorDark = Color.parseColor("#0A2A26")
    private val colorChipBg = Color.parseColor("#EEF6F4")
    private val colorDanger = Color.parseColor("#DC2626")
    private val colorDangerSoft = Color.parseColor("#FEE2E2")
    private val colorSuccess = Color.parseColor("#16A34A")

    // ডাক্তারের তথ্য
    private val doctorPhoneForCall = "+8801632336631" // TODO: বসান
    private val doctorName = "ডা. মাসুম বিল্লাহ সানি"
    private val doctorCreds = "MBBS, DMU, PGT, MCGP, CCD • BMDC A-17630"
    private val doctorHospital = "পার্কভিউ হাসপাতাল"
    private val doctorRating = "৪.৯"
    private val doctorPatients = "২.৩k রোগী"
    private val doctorTags = listOf("মেডিসিন", "শিশু", "ডায়াবেটিস")

    private lateinit var appointmentsContainer: LinearLayout
    private lateinit var homeUpcomingContainer: LinearLayout
    private lateinit var greetingText: TextView

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
    private lateinit var profileAvatar: TextView
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
            setBackgroundColor(colorScreenBg)
        }

        // ---------------- HOME PANEL ----------------
        val homeScroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val homeContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(46), dp(18), dp(110))
        }

        // ---------------- হালকা টপ-বার: অ্যাভাটার + শুভেচ্ছা + নোটিফিকেশন বেল ----------------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nameFirstLetter = (SupabaseClient.getName(this)?.trim()?.firstOrNull() ?: 'র').toString()
        val headerAvatar = circleAvatar(nameFirstLetter, 46, colorPrimary)
        val headerTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
        }
        val greetingPrefix = text(timeBasedGreeting(), 12.5f, Typeface.NORMAL, colorTextMuted, Gravity.START)
        greetingText = text("${SupabaseClient.getName(this) ?: "রোগী"} 👋", 17f, Typeface.BOLD, colorDark, Gravity.START)
        headerTextCol.addView(greetingPrefix)
        headerTextCol.addView(greetingText)

        val bellBtn = FrameLayout(this).apply {
            background = roundedBg(Color.WHITE, 30f)
            elevation = dp(3).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            addView(text("🔔", 18f, Typeface.NORMAL, colorDark, Gravity.CENTER).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            })
            addView(View(this@HomeActivity).apply {
                background = roundedBg(colorDanger, 10f)
                layoutParams = FrameLayout.LayoutParams(dp(9), dp(9)).apply { gravity = Gravity.TOP or Gravity.END; topMargin = dp(8); rightMargin = dp(8) }
            })
            setOnClickListener { showComingSoon("নোটিফিকেশন") }
        }
        header.addView(headerAvatar)
        header.addView(headerTextCol)
        header.addView(bellBtn)

        // ---------------- DOCTOR CARD ----------------
        val doctorCard = buildDoctorCard()

        // ---------------- QUICK ACTIONS (প্রাইমারি + সেকেন্ডারি) ----------------
        val quickActionsPrimary = buildPrimaryQuickActions()
        val quickActionsSecondary = buildSecondaryQuickActions()

        // ---------------- আসন্ন অ্যাপয়েন্টমেন্ট (হোম প্রিভিউ) ----------------
        val upcomingHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22) }
        }
        upcomingHeaderRow.addView(text("আসন্ন অ্যাপয়েন্টমেন্ট", 15f, Typeface.BOLD, colorDark, Gravity.START).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        upcomingHeaderRow.addView(text("সব দেখুন", 12.5f, Typeface.BOLD, colorPrimary, Gravity.END).apply {
            isClickable = true; isFocusable = true
            setOnClickListener { switchTab(Tab.APPOINTMENTS) }
        })
        homeUpcomingContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }

        // ---------------- স্বাস্থ্য টিপস ব্যানার ----------------
        val tipBanner = buildHealthTipBanner()

        homeContent.addView(header)
        homeContent.addView(doctorCard)
        homeContent.addView(quickActionsPrimary)
        homeContent.addView(quickActionsSecondary)
        homeContent.addView(upcomingHeaderRow)
        homeContent.addView(homeUpcomingContainer)
        homeContent.addView(tipBanner)
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

        // ---------------- PROFILE PANEL ----------------
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

    private fun timeBasedGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "সকাল ভালো,"
            hour < 16 -> "শুভ অপরাহ্ন,"
            hour < 20 -> "শুভ সন্ধ্যা,"
            else -> "শুভ রাত্রি,"
        }
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
    // ডাক্তারের কার্ড — আপলোড করা ছবির (Image 1 / Image 2) মতো
    // ------------------------------------------------------------------
    private fun buildDoctorCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardStroke(colorBorder, 20f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18)
            }
            elevation = dp(3).toFloat()
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val avatar = squareAvatar("MS", 60, colorPrimaryDark, 16f)
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) }
        }
        val nameRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        nameRow.addView(text(doctorName, 15.5f, Typeface.BOLD, colorPrimaryDark, Gravity.START))
        nameRow.addView(text(" ✓", 13f, Typeface.BOLD, colorSuccess, Gravity.START))
        textCol.addView(nameRow)
        textCol.addView(text(doctorCreds, 11f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
            setPadding(0, dp(3), 0, 0)
        })
        topRow.addView(avatar)
        topRow.addView(textCol)
        card.addView(topRow)

        // রেটিং পিল + হাসপাতাল
        val ratingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        val ratingPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(colorAccentSoft, 30f)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            addView(text("🏅 ", 12f, Typeface.NORMAL, colorAccent, Gravity.CENTER))
            addView(text("$doctorRating • $doctorPatients", 11.5f, Typeface.BOLD, colorAccent, Gravity.CENTER))
        }
        ratingRow.addView(ratingPill)
        ratingRow.addView(text("  •  $doctorHospital", 11.5f, Typeface.NORMAL, colorTextMuted, Gravity.START))
        card.addView(ratingRow)

        // এক্সপার্টাইজ চিপস
        val chipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
        }
        doctorTags.forEachIndexed { idx, tag ->
            chipsRow.addView(text(tag, 11f, Typeface.BOLD, colorPrimaryDark, Gravity.CENTER).apply {
                background = cardStroke(colorBorder, 30f)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx > 0) marginStart = dp(8)
                }
            })
        }
        card.addView(chipsRow)

        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { showComingSoon("ডাক্তারের প্রোফাইল") }
        return card
    }

    // ------------------------------------------------------------------
    // প্রাইমারি কুইক অ্যাকশন — বড় "বুক অ্যাপয়েন্টমেন্ট" কার্ড + দুইটি সাদা কার্ড
    // ------------------------------------------------------------------
    private fun buildPrimaryQuickActions(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18)
            }
        }

        val bookCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(colorPrimaryDark, 20f)
            setPadding(dp(18), dp(20), dp(16), dp(20))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(10) }
            setOnClickListener { startActivity(Intent(this@HomeActivity, BookAppointmentActivity::class.java)) }
            addView(text("📅", 24f, Typeface.NORMAL, Color.WHITE, Gravity.START))
            addView(text("অ্যাপয়েন্টমেন্ট বুক করুন", 14.5f, Typeface.BOLD, Color.WHITE, Gravity.START).apply {
                setPadding(0, dp(14), 0, 0)
            })
            addView(text("ফি ৮০০ টাকা", 11.5f, Typeface.NORMAL, Color.argb(210, 255, 255, 255), Gravity.START).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }

        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        rightCol.addView(smallActionCard("📄", "আমার অ্যাপয়েন্টমেন্ট", colorPrimary) { switchTab(Tab.APPOINTMENTS) })
        rightCol.addView(space(dp(10)))
        rightCol.addView(smallActionCard("📞", "জরুরি যোগাযোগ", colorDanger) { callDoctor() })

        row.addView(bookCard)
        row.addView(rightCol)
        return row
    }

    private fun smallActionCard(emoji: String, label: String, accentColor: Int, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardStroke(colorBorder, 16f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setOnClickListener { onClick() }
            addView(text(emoji, 16f, Typeface.NORMAL, accentColor, Gravity.CENTER).apply {
                background = roundedBg(Color.argb(28, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)), 30f)
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                gravity = Gravity.CENTER
            })
            addView(text(label, 12f, Typeface.BOLD, colorDark, Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
            })
        }
    }

    // ------------------------------------------------------------------
    // সেকেন্ডারি কুইক অ্যাকশন — আগের সব ফিচার (প্রেসক্রিপশন/প্রোফাইল/অ্যাডমিন) বজায় রাখতে
    // ------------------------------------------------------------------
    private fun buildSecondaryQuickActions(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
        }
        val items = mutableListOf<Triple<String, String, () -> Unit>>(
            Triple("💊", "প্রেসক্রিপশন") { showComingSoon("প্রেসক্রিপশন হিস্ট্রি") },
            Triple("👤", "আমার প্রোফাইল") { switchTab(Tab.PROFILE) }
        )
        if (SupabaseClient.isAdmin(this)) {
            items.add(Triple("✅", "পেমেন্ট ভেরিফিকেশন") {
                startActivity(Intent(this, AdminPaymentVerificationActivity::class.java))
            })
        }
        items.forEachIndexed { idx, (emoji, label, onClick) ->
            row.addView(secondaryChip(emoji, label, onClick).apply {
                (layoutParams as LinearLayout.LayoutParams).apply {
                    if (idx > 0) marginStart = dp(10)
                }
            })
        }
        return row
    }

    private fun secondaryChip(emoji: String, label: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = cardStroke(colorBorder, 16f)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
            addView(text(emoji, 16f, Typeface.NORMAL, colorPrimaryDark, Gravity.CENTER))
            addView(text(label, 10.5f, Typeface.BOLD, colorDark, Gravity.CENTER).apply {
                setPadding(0, dp(6), 0, 0)
            })
        }
    }

    // ------------------------------------------------------------------
    // স্বাস্থ্য টিপস ব্যানার (নিচে ডার্ক কার্ড)
    // ------------------------------------------------------------------
    private fun buildHealthTipBanner(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(colorPrimaryDark, 18f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(22)
            }
            setOnClickListener { showComingSoon("স্বাস্থ্য টিপস") }
            addView(text("💓", 20f, Typeface.NORMAL, colorAccent, Gravity.CENTER).apply {
                background = roundedBg(Color.argb(40, 255, 255, 255), 14f)
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                gravity = Gravity.CENTER
            })
            val textCol = LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
            }
            textCol.addView(text("আজকের স্বাস্থ্য টিপস", 13.5f, Typeface.BOLD, Color.WHITE, Gravity.START))
            textCol.addView(text("ডায়াবেটিসে করলা ও মেথির উপকারিতা...", 11f, Typeface.NORMAL, Color.argb(210, 255, 255, 255), Gravity.START).apply {
                setPadding(0, dp(3), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(textCol)
            addView(text("›", 20f, Typeface.BOLD, Color.WHITE, Gravity.CENTER))
        }
    }

    // ------------------------------------------------------------------
    // প্রোফাইল প্যানেল
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
            background = cardStroke(colorBorder, 20f)
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        profileAvatar = circleAvatar(
            (SupabaseClient.getName(this)?.trim()?.firstOrNull() ?: 'র').toString(), 84, colorPrimaryDark, textSizeSp = 30f
        ).apply { layoutParams = LinearLayout.LayoutParams(dp(84), dp(84)).apply { gravity = Gravity.CENTER_HORIZONTAL } }
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
            background = cardStroke(colorBorder, 18f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            }
        }
        body.addView(profileDetailsContainer)

        val logoutFromProfile = TextView(this).apply {
            text = "লগ আউট করুন"
            setTextColor(colorDanger)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = roundedBg(colorDangerSoft, 14f)
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
                                setBackgroundColor(colorBorder)
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
    // অ্যাপয়েন্টমেন্ট লোডিং — একই ফেচ থেকে হোম-প্রিভিউ ও অ্যাপয়েন্টমেন্ট-ট্যাব দুটোই আপডেট হয়
    // ------------------------------------------------------------------
    private fun loadAppointments() {
        val patientId = SupabaseClient.getPatientId(this) ?: return
        appointmentsContainer.removeAllViews()
        homeUpcomingContainer.removeAllViews()
        appointmentsContainer.addView(text("লোড হচ্ছে...", 12.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER))
        homeUpcomingContainer.addView(text("লোড হচ্ছে...", 12.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER))

        lifecycleScope.launch {
            val result = SupabaseClient.getAppointments(patientId)
            appointmentsContainer.removeAllViews()
            homeUpcomingContainer.removeAllViews()
            result.onSuccess { rows ->
                if (rows.length() == 0) {
                    appointmentsContainer.addView(
                        text("কোনো অ্যাপয়েন্টমেন্ট নেই। নতুন বুক করুন।", 12.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER).apply {
                            setPadding(0, dp(20), 0, dp(20))
                        }
                    )
                    homeUpcomingContainer.addView(
                        text("কোনো অ্যাপয়েন্টমেন্ট নেই। উপরে থেকে নতুন বুক করুন।", 12f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER).apply {
                            setPadding(0, dp(10), 0, dp(10))
                        }
                    )
                } else {
                    for (i in 0 until rows.length()) {
                        val obj = rows.getJSONObject(i)
                        val date = obj.optString("preferred_date")
                        val time = obj.optString("preferred_time")
                        val reason = obj.optString("reason")
                        val status = obj.optString("status")

                        appointmentsContainer.addView(appointmentCard(date, time, reason, status))
                        appointmentsContainer.addView(space(dp(10)))

                        if (i < 2) {
                            homeUpcomingContainer.addView(appointmentCard(date, time, reason, status))
                            if (i == 0 && rows.length() > 1) homeUpcomingContainer.addView(space(dp(10)))
                        }
                    }
                }
            }.onFailure {
                val errText = text("অ্যাপয়েন্টমেন্ট লোড করা যায়নি", 12.5f, Typeface.NORMAL, colorDanger, Gravity.CENTER)
                appointmentsContainer.addView(errText)
                homeUpcomingContainer.addView(
                    text("অ্যাপয়েন্টমেন্ট লোড করা যায়নি", 12.5f, Typeface.NORMAL, colorDanger, Gravity.CENTER)
                )
            }
        }
    }

    /** ছবির মতো: তারিখ ব্যাজ + শিরোনাম/সময় + স্ট্যাটাস লাইন + রঙিন ডট */
    private fun appointmentCard(date: String, time: String, reason: String, status: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardStroke(colorBorder, 16f)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        val (monthAbbr, dayNum) = formatDateBadge(date)
        val dateBadge = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBg(colorChipBg, 12f)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(text(monthAbbr, 10.5f, Typeface.BOLD, colorPrimary, Gravity.CENTER))
            addView(text(dayNum, 15f, Typeface.BOLD, colorPrimaryDark, Gravity.CENTER))
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
        }
        val titleLine = if (time.isNotBlank()) "${reason.ifBlank { "সাধারণ পরামর্শ" }} • $time" else reason.ifBlank { "সাধারণ পরামর্শ" }
        col.addView(text(titleLine, 13f, Typeface.BOLD, colorDark, Gravity.START))
        col.addView(text(statusText(status), 11f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
            setPadding(0, dp(4), 0, 0)
        })

        val dot = View(this).apply {
            background = roundedBg(statusColor(status), 10f)
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10))
        }

        row.addView(dateBadge)
        row.addView(col)
        row.addView(dot)
        return row
    }

    private fun statusText(status: String): String = when (status) {
        "confirmed" -> "কনফার্মড করা হয়েছে"
        "completed" -> "সম্পন্ন হয়েছে"
        "cancelled" -> "বাতিল করা হয়েছে"
        else -> "পেমেন্ট যাচাই চলছে"
    }

    private fun statusColor(status: String): Int = when (status) {
        "confirmed" -> colorSuccess
        "completed" -> Color.parseColor("#2563EB")
        "cancelled" -> colorDanger
        else -> colorAccent
    }

    /** "yyyy-MM-dd" ফরম্যাটের তারিখ থেকে বাংলা মাস-সংক্ষেপ ও দিনসংখ্যা বের করে, ব্যর্থ হলে কাঁচা টেক্সট রিটার্ন করে */
    private fun formatDateBadge(dateStr: String): Pair<String, String> {
        val monthAbbrevs = arrayOf("জানু", "ফেব্রু", "মার্চ", "এপ্রি", "মে", "জুন", "জুলা", "আগ", "সেপ্টে", "অক্টো", "নভে", "ডিসে")
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            val d = sdf.parse(dateStr)
            val cal = Calendar.getInstance()
            cal.time = d!!
            val month = monthAbbrevs[cal.get(Calendar.MONTH)]
            val day = cal.get(Calendar.DAY_OF_MONTH).toString()
            Pair(month, day)
        } catch (e: Exception) {
            Pair("তারিখ", dateStr.takeLast(2))
        }
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

    /** সাদা কার্ড + পাতলা বর্ডার — নতুন ডিজাইন সিস্টেমের মূল কার্ড টোকেন */
    private fun cardStroke(borderColor: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(colorCard)
        setStroke(dp(1), borderColor)
    }

    /** বৃত্তাকার ইনিশিয়াল অ্যাভাটার (রোগী/হেডার অ্যাভাটারের জন্য) */
    private fun circleAvatar(initial: String, sizeDp: Int, bg: Int, textSizeSp: Float = sizeDp * 0.36f): TextView = TextView(this).apply {
        text = initial.uppercase()
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        textSize = textSizeSp
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    /** স্কয়ার-রাউন্ডেড ইনিশিয়াল অ্যাভাটার (ডাক্তারের অ্যাভাটারের জন্য, ছবির "MS" ব্যাজের মতো) */
    private fun squareAvatar(initial: String, sizeDp: Int, bg: Int, radiusDp: Float): TextView = TextView(this).apply {
        text = initial.uppercase()
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        textSize = sizeDp * 0.30f
        background = roundedBg(bg, radiusDp)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }
}
