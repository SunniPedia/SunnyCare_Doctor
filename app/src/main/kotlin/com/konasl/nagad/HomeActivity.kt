package com.konasl.nagad

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.CornerPathEffect
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject

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
    private val doctorSerialPhone = "01660029028" // সিরিয়ালের জন্য

    private lateinit var appointmentsContainer: LinearLayout
    private lateinit var greetingText: TextView

    // ---------------- ন্যাভিগেশন ----------------
    private enum class Tab { HOME, APPOINTMENTS, PROFILE }
    private var currentTab = Tab.HOME

    private lateinit var homePanel: View
    private lateinit var appointmentsPanel: View
    private lateinit var profilePanel: View

    private data class NavItemViews(
        val root: LinearLayout,
        val pill: LinearLayout,
        val icon: ImageView,
        val iconDrawable: VectorIconDrawable,
        val label: TextView
    )

    private lateinit var navHome: NavItemViews
    private lateinit var navAppointments: NavItemViews
    private lateinit var navProfile: NavItemViews

    // ---------------- প্রোফাইল প্যানেল ----------------
    private lateinit var profileAvatar: PatientAvatarView
    private lateinit var profileNameText: TextView
    private lateinit var profilePhoneText: TextView
    private lateinit var profileDetailsContainer: LinearLayout

    // ---------------- নতুন: হোমে "আসন্ন অ্যাপয়েন্টমেন্ট" কার্ড ----------------
    private lateinit var homeUpcomingContainer: LinearLayout

    // ---------------- নতুন: প্রতি ১০ সেকেন্ড স্ট্যাটাস পোলিং ----------------
    private val pollHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private val POLL_INTERVAL_MS = 10_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SupabaseClient.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
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

        // ---------------- HOME PANEL ----------------
        val homeScroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val homeContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(100))
        }

        // ---------------- HEADER (গ্রেডিয়েন্ট + গ্লো ডেকোরেশন) ----------------
        val headerContainer = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark)
            ).apply {
                setCornerRadii(floatArrayOf(0f, 0f, 0f, 0f, dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat()))
            }
            clipToPadding = false
        }
        headerContainer.addView(glowCircle(dp(170), Color.argb(26, 255, 255, 255), Gravity.TOP or Gravity.END, dp(-60), dp(-55)))
        headerContainer.addView(glowCircle(dp(120), Color.argb(24, Color.red(colorAccent), Color.green(colorAccent), Color.blue(colorAccent)), Gravity.BOTTOM or Gravity.START, dp(-40), dp(-35)))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(44), dp(22), dp(30))
        }
        val headerTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        greetingText = text("স্বাগতম, ${SupabaseClient.getName(this) ?: "রোগী"}", 18f, Typeface.BOLD, Color.WHITE, Gravity.START)
        val appTitle = text("SunnyCare", 12f, Typeface.NORMAL, Color.argb(210, 255, 255, 255), Gravity.START).apply {
            compoundDrawablePadding = dp(5)
            setCompoundDrawablesWithIntrinsicBounds(
                VectorIconDrawable(VectorIconDrawable.IconType.SUN, Color.argb(230, 255, 255, 255), dp(13)), null, null, null
            )
        }
        headerTextCol.addView(greetingText)
        headerTextCol.addView(appTitle.apply { setPadding(0, dp(4), 0, 0) })

        val logoutBtn = TextView(this).apply {
            text = "লগ আউট"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            background = roundedBg(Color.argb(50, 255, 255, 255), 30f)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            compoundDrawablePadding = dp(6)
            setCompoundDrawablesWithIntrinsicBounds(null, null, VectorIconDrawable(VectorIconDrawable.IconType.LOGOUT, Color.WHITE, dp(16)), null)
            setOnClickListener { onLogout() }
        }
        header.addView(headerTextCol)
        header.addView(logoutBtn)
        headerContainer.addView(header)

        // ---------------- DOCTOR CARD (গ্রেডিয়েন্ট বর্ডার সহ) ----------------
        val doctorCard = buildDoctorCard()

        // ---------------- QUICK ACTIONS ----------------
        val quickActionsSection = buildQuickActionsSection()

        homeContent.addView(headerContainer)
        homeContent.addView(doctorCard)
        homeContent.addView(quickActionsSection)

        // ---------------- ADMIN-ONLY: পেমেন্ট ভেরিফিকেশন ----------------
        if (SupabaseClient.isAdmin(this)) {
            val adminRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(14), dp(10), dp(14), 0)
                }
            }
            adminRow.addView(adminActionCard(VectorIconDrawable.IconType.SHIELD, "পেমেন্ট ভেরিফিকেশন", Color.parseColor("#059669")) {
                startActivity(Intent(this, AdminPaymentVerificationActivity::class.java))
            })
            // নতুন: এডমিন প্যানেল (সব ডেটা পর্যবেক্ষণ ও ম্যানেজমেন্ট) — শুধু অ্যাডমিনের জন্য দৃশ্যমান
            adminRow.addView(space(dp(10)))
            adminRow.addView(adminActionCard(VectorIconDrawable.IconType.SHIELD, "এডমিন প্যানেল (সব ডেটা)", Color.parseColor("#0F6C61")) {
                startActivity(Intent(this, AdminActivity::class.java))
            })
            homeContent.addView(adminRow)
        }

        // ---------------- নতুন: "আসন্ন অ্যাপয়েন্টমেন্ট" কার্ডের কন্টেইনার ----------------
        homeUpcomingContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(14), dp(16), dp(14), 0)
            }
            visibility = View.GONE
        }
        homeContent.addView(homeUpcomingContainer)

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
        loadHomeUpcomingAppointment()
        updateNavStyle()
    }

    override fun onResume() {
        super.onResume()
        loadAppointments()
        loadHomeUpcomingAppointment()
        startStatusPolling()
    }

    override fun onPause() {
        super.onPause()
        stopStatusPolling()
    }

    // ------------------------------------------------------------------
    private fun startStatusPolling() {
        stopStatusPolling()
        val runnable = object : Runnable {
            override fun run() {
                loadAppointments()
                loadHomeUpcomingAppointment()
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

    private fun loadHomeUpcomingAppointment() {
        val patientId = SupabaseClient.getPatientId(this) ?: return
        lifecycleScope.launch {
            val result = SupabaseClient.getAppointments(patientId)
            result.onSuccess { rows ->
                var active: JSONObject? = null
                for (i in rows.length() - 1 downTo 0) {
                    val obj = rows.getJSONObject(i)
                    val status = obj.optString("status", "")
                    if (status == "pending" || status == "confirmed") {
                        active = obj
                        break
                    }
                }
                renderHomeUpcomingAppointment(active)
            }
        }
    }

    private fun renderHomeUpcomingAppointment(appt: JSONObject?) {
        homeUpcomingContainer.removeAllViews()
        if (appt == null) {
            homeUpcomingContainer.visibility = View.GONE
            return
        }
        homeUpcomingContainer.visibility = View.VISIBLE
        homeUpcomingContainer.addView(text("আসন্ন অ্যাপয়েন্টমেন্ট", 14.5f, Typeface.BOLD, colorDark, Gravity.START).apply {
            compoundDrawablePadding = dp(6)
            setCompoundDrawablesWithIntrinsicBounds(
                VectorIconDrawable(VectorIconDrawable.IconType.CALENDAR, colorPrimary, dp(15)), null, null, null
            )
            setPadding(dp(4), 0, 0, dp(10))
        })
        homeUpcomingContainer.addView(buildUpcomingAppointmentCard(appt))
    }

    private fun buildUpcomingAppointmentCard(appt: JSONObject): LinearLayout {
        val appointmentId = appt.optString("id", "")
        val date = appt.optString("preferred_date", "")
        val time = appt.optString("preferred_time", "")
        val reason = appt.optString("reason", "")
        val status = appt.optString("status", "pending")
        val slotOpen = appt.optBoolean("slot_open", false)
        val patientName = appt.optString("patient_name", SupabaseClient.getName(this) ?: "")
        val phone = appt.optString("phone", SupabaseClient.getPhone(this) ?: "")

        val canJoin = status == "confirmed" && slotOpen

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = if (canJoin) {
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark)
                ).apply { cornerRadius = dp(20).toFloat() }
            } else {
                roundedBg(colorCard, 20f)
            }
            setPadding(dp(18), dp(18), dp(18), dp(18))
            elevation = dp(2).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
                }
            }
            clipToOutline = true

            val titleColor = if (canJoin) Color.WHITE else colorDark
            val mutedColor = if (canJoin) Color.argb(220, 255, 255, 255) else colorTextMuted

            val topRow = LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val infoCol = LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoCol.addView(text("$date • $time", 14.5f, Typeface.BOLD, titleColor, Gravity.START))
            infoCol.addView(text(reason.ifEmpty { "সাধারণ পরামর্শ" }, 12f, Typeface.NORMAL, mutedColor, Gravity.START).apply {
                setPadding(0, dp(4), 0, 0)
            })
            topRow.addView(infoCol)

            val (statusLabel, statusColor) = when (status) {
                "confirmed" -> "কনফার্মড" to Color.parseColor("#16A34A")
                "completed" -> "সম্পন্ন" to Color.parseColor("#2563EB")
                "cancelled" -> "বাতিল" to Color.parseColor("#DC2626")
                else -> "পেন্ডিং" to colorAccent
            }
            topRow.addView(text(statusLabel, 10.5f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(lighten(statusColor, 0.12f), statusColor)).apply {
                    cornerRadius = dp(30).toFloat()
                }
                setPadding(dp(10), dp(5), dp(10), dp(5))
            })
            addView(topRow)

            addView(View(this@HomeActivity).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    topMargin = dp(14); bottomMargin = dp(14)
                }
                setBackgroundColor(if (canJoin) Color.argb(60, 255, 255, 255) else colorFieldBorder)
            })

            if (canJoin) {
                val ctaRow = LinearLayout(this@HomeActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = roundedBg(Color.argb(46, 255, 255, 255), 14f)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val intent = Intent(this@HomeActivity, WaitingActivity::class.java).apply {
                            putExtra("appointment_id", appointmentId)
                            putExtra("patient_name", patientName)
                            putExtra("phone", phone)
                            putExtra("preferred_date", date)
                            putExtra("preferred_time", time)
                            putExtra("reason", reason)
                        }
                        startActivity(intent)
                    }
                }
                ctaRow.addView(ImageView(this@HomeActivity).apply {
                    setImageDrawable(VectorIconDrawable(VectorIconDrawable.IconType.CHECK, Color.WHITE, dp(18)))
                    background = roundedBg(Color.argb(60, 255, 255, 255), 10f)
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                })
                val ctaTextCol = LinearLayout(this@HomeActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
                }
                ctaTextCol.addView(text("আপনার সিরিয়াল শুরু হয়েছে", 12.5f, Typeface.BOLD, Color.WHITE, Gravity.START))
                ctaTextCol.addView(text("এখনই যোগ দিন", 11f, Typeface.NORMAL, Color.argb(220, 255, 255, 255), Gravity.START).apply {
                    setPadding(0, dp(2), 0, 0)
                })
                ctaRow.addView(ctaTextCol)
                ctaRow.addView(ImageView(this@HomeActivity).apply {
                    setImageDrawable(VectorIconDrawable(VectorIconDrawable.IconType.ARROW_RIGHT, Color.WHITE, dp(16)))
                    layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
                })
                addView(ctaRow)
            } else {
                val waitRow = LinearLayout(this@HomeActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                waitRow.addView(ImageView(this@HomeActivity).apply {
                    setImageDrawable(VectorIconDrawable(VectorIconDrawable.IconType.CLOCK, colorPrimary, dp(16)))
                    background = roundedBg(Color.parseColor("#E4F3F1"), 10f)
                    layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                })
                val waitTextCol = LinearLayout(this@HomeActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
                }
                val waitMsg = if (status == "confirmed")
                    "কনফার্মড — সিরিয়াল ওপেন হলে এখানে জানানো হবে"
                else
                    "আপনার অ্যাপয়েন্টমেন্ট রিভিউ করা হচ্ছে"
                waitTextCol.addView(text(waitMsg, 12f, Typeface.BOLD, colorDark, Gravity.START))
                waitTextCol.addView(text("স্ট্যাটাস প্রতি ১০ সেকেন্ডে অটো-আপডেট হয়", 10.5f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
                    setPadding(0, dp(2), 0, 0)
                })
                waitRow.addView(waitTextCol)
                addView(waitRow)
            }
        }
    }

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
            val color = if (active) Color.WHITE else colorTextMuted
            item.iconDrawable.updateTint(color)
            item.label.setTextColor(color)
            item.label.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            item.pill.background = if (active) {
                GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(colorPrimaryLight, colorPrimaryDark)).apply {
                    cornerRadius = dp(18).toFloat()
                }
            } else {
                null
            }
        }
    }

    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg(Color.WHITE, 24f)
            setPadding(dp(6), dp(10), dp(6), dp(10))
            elevation = dp(8).toFloat()
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
        navHome = navItem(VectorIconDrawable.IconType.HOME, "হোম") { switchTab(Tab.HOME) }
        navAppointments = navItem(VectorIconDrawable.IconType.CALENDAR, "অ্যাপয়েন্টমেন্ট") { switchTab(Tab.APPOINTMENTS) }
        navProfile = navItem(VectorIconDrawable.IconType.PERSON, "প্রোফাইল") { switchTab(Tab.PROFILE) }
        nav.addView(navHome.root)
        nav.addView(navAppointments.root)
        nav.addView(navProfile.root)
        return nav
    }

    private fun navItem(iconType: VectorIconDrawable.IconType, labelText: String, onClick: () -> Unit): NavItemViews {
        val drawable = VectorIconDrawable(iconType, colorTextMuted, dp(22))
        val icon = ImageView(this).apply {
            setImageDrawable(drawable)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        }
        val labelView = text(labelText, 10.5f, Typeface.BOLD, colorTextMuted, Gravity.CENTER).apply {
            setPadding(0, dp(4), 0, 0)
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
            addView(icon)
            addView(labelView)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(4), dp(2), dp(4), dp(2))
            isClickable = true
            isFocusable = true
            addView(pill)
            setOnClickListener { onClick() }
        }
        return NavItemViews(root, pill, icon, drawable, labelView)
    }

    private fun panelTopBar(titleText: String): View {
        val container = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark)
            ).apply {
                setCornerRadii(floatArrayOf(0f, 0f, 0f, 0f, dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat()))
            }
        }
        container.addView(glowCircle(dp(150), Color.argb(24, 255, 255, 255), Gravity.TOP or Gravity.END, dp(-55), dp(-45)))
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(44), dp(22), dp(22))
        }
        inner.addView(text(titleText, 18f, Typeface.BOLD, Color.WHITE, Gravity.START))
        container.addView(inner)
        return container
    }

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

    // ------------------------------------------------------------------
    // ডাক্তারের কার্ড - গ্রেডিয়েন্ট বর্ডার সহ প্রিমিয়াম ডিজাইন
    // ------------------------------------------------------------------
    private fun buildDoctorCard(): View {
        val borderWrap = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorAccent, colorPrimary, colorPrimaryDark)
            ).apply { cornerRadius = dp(24).toFloat() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(-22), dp(18), 0)
            }
            setPadding(dp(2), dp(2), dp(2), dp(2))
            elevation = dp(6).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
                }
            }
            clipToOutline = true
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(colorCard, 22f)
            setPadding(dp(18), dp(20), dp(18), dp(18))
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
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#22C55E"))
                setStroke(dp(2), Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(dp(16), dp(16)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
        })
        // ------------------------------------------------------------------
        // নতুন: শুধুমাত্র অ্যাডমিনের জন্য — ডাক্তারের অ্যাভাটারে ক্লিক করলে
        // Admin.kt (AdminActivity) ওপেন হবে, যেখানে SupabaseClient এর সব
        // ডেটা (রোগী, অ্যাপয়েন্টমেন্ট, স্লট, OTP পুল) পর্যবেক্ষণ ও ম্যানেজ
        // করা যায়। সাধারণ রোগীর জন্য এই আইকন আগের মতোই অ-ক্লিকযোগ্য থাকবে।
        // ------------------------------------------------------------------
        if (SupabaseClient.isAdmin(this)) {
            avatarWrap.isClickable = true
            avatarWrap.isFocusable = true
            avatarWrap.foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(60, 0, 0, 0)),
                null,
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            )
            avatarWrap.setOnClickListener {
                startActivity(Intent(this, AdminActivity::class.java))
            }
        }
        val doctorTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(14) }
        }
        doctorTextCol.addView(text("ডা. মাসুম বিল্লাহ সানি", 16.5f, Typeface.BOLD, colorDark, Gravity.START))
        doctorTextCol.addView(text("মেডিসিন, শিশু, ডায়াবেটিস ও চর্ম-যৌনরোগ বিশেষজ্ঞ", 12f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
            setPadding(0, dp(2), 0, 0)
        })
        doctorTextCol.addView(text("প্রাক্তন মেডিকেল অফিসার: পার্কভিউ মেডিকেল কলেজ হাসপাতাল, সিলেট", 10.5f, Typeface.NORMAL, colorPrimaryDark, Gravity.START).apply {
            setPadding(0, dp(4), 0, 0)
            setLineSpacing(dp(1).toFloat(), 1f)
        })
        topRow.addView(avatarWrap)
        topRow.addView(doctorTextCol)
        inner.addView(topRow)

        // রেটিং রো (ক্যানভাস স্টার আইকন)
        val ratingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        val starsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(5) {
            starsRow.addView(ImageView(this).apply {
                setImageDrawable(VectorIconDrawable(VectorIconDrawable.IconType.STAR, colorAccent, dp(14)))
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(2) }
            })
        }
        ratingRow.addView(starsRow)
        ratingRow.addView(text(" ৫.০", 11.5f, Typeface.NORMAL, colorTextMuted, Gravity.START))
        inner.addView(ratingRow)

        // ডিগ্রি
        inner.addView(
            text(
                "এম.বি.বি.এস (সিইউ), ডি.এম.ইউ (আল্ট্রা), পিজিটি (পিএমসি),\nএম.সি.জি.পি (মেডিসিন ও শিশু), সি.সি.ডি (ডায়াবেটিস-বারডেম, ঢাকা)",
                11f, Typeface.NORMAL, colorTextMuted, Gravity.START
            ).apply {
                setPadding(0, dp(12), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            }
        )

        // ভেরিফাইড ব্যাজ (ক্যানভাস চেক আইকন)
        inner.addView(
            text("বি.এম.ডি.সি নং – এ-১৭৪৬৩ • Verified", 10.5f, Typeface.BOLD, colorPrimary, Gravity.START).apply {
                background = roundedBg(Color.parseColor("#E4F3F1"), 30f)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                compoundDrawablePadding = dp(6)
                setCompoundDrawablesWithIntrinsicBounds(
                    VectorIconDrawable(VectorIconDrawable.IconType.CHECK, colorPrimary, dp(13)), null, null, null
                )
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
        val expertise = listOf("মেডিসিন-শিশু", "ডায়াবেটিস", "উচ্চ রক্তচাপ", "নাক-কান-গলা", "বাত ব্যাথা", "এলার্জি", "শ্বাসকষ্ট", "চর্ম ও যৌনরোগ")
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

        // পাতলা বিভাজক
        inner.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(16) }
            setBackgroundColor(colorFieldBorder)
        })

        // চেম্বার, সিরিয়াল ও সময়সূচি তথ্য বক্স
        inner.addView(buildChamberInfoBox())

        borderWrap.addView(inner)
        return borderWrap
    }

    /** চেম্বারের ঠিকানা, সিরিয়াল নাম্বার ও রোগী দেখার সময় - প্রিমিয়াম ইনফো বক্স */
    private fun buildChamberInfoBox(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.parseColor("#F8FBFA"), 16f)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
        }
        box.addView(chamberInfoRow(
            VectorIconDrawable.IconType.LOCATION,
            "চেম্বার",
            "ঢাকা ডায়গনস্টিক সেন্টার, মাধবপুর পশ্চিম বাজার (সোনালী ব্যাংকের নিচ তলা), কাজী আক্তার ম্যানশন, মাধবপুর, হবিগঞ্জ"
        ))
        box.addView(rowDivider())
        box.addView(chamberInfoRow(
            VectorIconDrawable.IconType.CLOCK,
            "রোগী দেখার সময়",
            "প্রতিদিন সকাল ১০টা থেকে রাত ৮টা পর্যন্ত"
        ))
        box.addView(rowDivider())
        box.addView(chamberInfoRow(
            iconType = VectorIconDrawable.IconType.PHONE,
            label = "সিরিয়ালের জন্য কল করুন",
            value = doctorSerialPhone,
            clickable = true
        ) { callForSerial() })
        return box
    }

    private fun rowDivider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        setBackgroundColor(colorFieldBorder)
    }

    private fun chamberInfoRow(
        iconType: VectorIconDrawable.IconType,
        label: String,
        value: String,
        clickable: Boolean = false,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            if (clickable) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick?.invoke() }
            }
            addView(ImageView(this@HomeActivity).apply {
                setImageDrawable(VectorIconDrawable(iconType, colorPrimary, dp(14)))
                background = roundedBg(Color.parseColor("#E4F3F1"), 10f)
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
                setPadding(dp(8), dp(8), dp(8), dp(8))
            })
            val col = LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
            }
            col.addView(text(label, 10f, Typeface.BOLD, colorTextMuted, Gravity.START))
            col.addView(text(value, 11.5f, Typeface.NORMAL, colorDark, Gravity.START).apply {
                setPadding(0, dp(2), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            })
            addView(col)
            if (clickable) {
                addView(ImageView(this@HomeActivity).apply {
                    setImageDrawable(VectorIconDrawable(VectorIconDrawable.IconType.ARROW_RIGHT, colorTextMuted, dp(14)))
                    layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginStart = dp(6) }
                })
            }
        }
    }

    // ------------------------------------------------------------------
    // কুইক অ্যাকশন সেকশন
    // ------------------------------------------------------------------
    private fun buildQuickActionsSection(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(14), dp(18), dp(14), 0)
            }
        }

        row.addView(buildBookAppointmentHeroCard())
        row.addView(space(dp(12)).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), ViewGroup.LayoutParams.MATCH_PARENT)
        })
        row.addView(buildSideActionsColumn())

        return row
    }

    private fun buildBookAppointmentHeroCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark)
            ).apply { cornerRadius = dp(20).toFloat() }
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            elevation = dp(2).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
                }
            }
            clipToOutline = true
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(this@HomeActivity, BookAppointmentActivity::class.java)) }

            addView(ImageView(this@HomeActivity).apply {
                setImageDrawable(VectorIconDrawable(VectorIconDrawable.IconType.CALENDAR, Color.WHITE, dp(22)))
                background = roundedBg(Color.argb(46, 255, 255, 255), 14f)
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
                setPadding(dp(9), dp(9), dp(9), dp(9))
            })

            addView(text("অ্যাপয়েন্টমেন্ট বুক\nকরুন", 15f, Typeface.BOLD, Color.WHITE, Gravity.START).apply {
                setPadding(0, dp(16), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            })
        }
    }

    private fun buildSideActionsColumn(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)

            addView(sideActionCard(
                VectorIconDrawable.IconType.DOCUMENT,
                "আমার\nঅ্যাপয়েন্টমেন্ট",
                Color.parseColor("#2563EB")
            ) { switchTab(Tab.APPOINTMENTS) }.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            })

            addView(space(dp(10)))

            addView(sideActionCard(
                VectorIconDrawable.IconType.PHONE,
                "জরুরি\nযোগাযোগ",
                Color.parseColor("#DB2777")
            ) { callDoctor() }.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            })
        }
    }

    private fun sideActionCard(iconType: VectorIconDrawable.IconType, label: String, accentColor: Int, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(colorCard, 16f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            elevation = 1.5f * resources.displayMetrics.density
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
                }
            }
            clipToOutline = true
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(ImageView(this@HomeActivity).apply {
                setImageDrawable(VectorIconDrawable(iconType, accentColor, dp(16)))
                background = roundedBg(Color.argb(28, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)), 10f)
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                setPadding(dp(9), dp(9), dp(9), dp(9))
            })

            addView(text(label, 11.5f, Typeface.BOLD, colorDark, Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
                setLineSpacing(dp(1).toFloat(), 1f)
            })
        }
    }

    /** অ্যাডমিন-অনলি পূর্ণ-প্রস্থ অ্যাকশন কার্ড */
    private fun adminActionCard(iconType: VectorIconDrawable.IconType, label: String, color: Int, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.argb(20, Color.red(color), Color.green(color), Color.blue(color)))
            ).apply { cornerRadius = dp(18).toFloat() }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            elevation = dp(2).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(18).toFloat())
                }
            }
            clipToOutline = true
            setOnClickListener { onClick() }

            addView(ImageView(this@HomeActivity).apply {
                setImageDrawable(VectorIconDrawable(iconType, Color.WHITE, dp(22)))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setGradientType(GradientDrawable.RADIAL_GRADIENT)
                    setColors(intArrayOf(lighten(color, 0.28f), darken(color, 0.08f)))
                    setGradientRadius(dp(30).toFloat())
                    setGradientCenter(0.3f, 0.3f)
                }
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
                setPadding(dp(12), dp(12), dp(12), dp(12))
            })

            addView(text(label, 13f, Typeface.BOLD, colorDark, Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) }
            })

            addView(ImageView(this@HomeActivity).apply {
                setImageDrawable(VectorIconDrawable(VectorIconDrawable.IconType.ARROW_RIGHT, colorTextMuted, dp(18)))
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
            })
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
            background = roundedBg(colorCard, 22f)
            setPadding(dp(20), dp(24), dp(20), dp(24))
            elevation = dp(3).toFloat()
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
            elevation = dp(2).toFloat()
        }
        body.addView(profileDetailsContainer)

        val accountActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20)
            }
        }

        val logoutFromProfile = profileActionButton(
            label = "লগ আউট",
            iconType = VectorIconDrawable.IconType.LOGOUT,
            color = Color.parseColor("#DC2626"),
            bgColor = Color.parseColor("#FEE2E2")
        ) { onLogout() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val deleteProfileBtn = profileActionButton(
            label = "ডিলিট প্রোফাইল",
            iconType = VectorIconDrawable.IconType.TRASH,
            color = Color.WHITE,
            bgColor = Color.parseColor("#DC2626")
        ) { onDeleteProfile() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        }

        accountActionsRow.addView(logoutFromProfile)
        accountActionsRow.addView(deleteProfileBtn)
        body.addView(accountActionsRow)

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

                val photoUrl = patient.optString("profile_picture_url", "")
                if (photoUrl.isNotBlank()) {
                    loadProfileAvatarImage(photoUrl)
                } else {
                    profileAvatar.setPhoto(null)
                }
            }
        }
    }

    private fun loadProfileAvatarImage(url: String) {
        lifecycleScope.launch {
            val bitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
                var connection: java.net.HttpURLConnection? = null
                try {
                    connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 15000
                        doInput = true
                        instanceFollowRedirects = true
                    }
                    connection.connect()
                    connection.inputStream.use { input ->
                        android.graphics.BitmapFactory.decodeStream(input)
                    }
                } catch (e: Exception) {
                    null
                } finally {
                    connection?.disconnect()
                }
            }
            if (bitmap != null) {
                profileAvatar.setPhoto(bitmap)
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
                    appointmentsContainer.addView(buildEmptyAppointmentsCard())
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

    private fun buildEmptyAppointmentsCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(colorCard, 20f)
            setPadding(dp(26), dp(32), dp(26), dp(28))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            elevation = dp(2).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
                }
            }
            clipToOutline = true

            addView(ImageView(this@HomeActivity).apply {
                setImageDrawable(VectorIconDrawable(VectorIconDrawable.IconType.CALENDAR, colorPrimary, dp(28)))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setGradientType(GradientDrawable.RADIAL_GRADIENT)
                    setColors(intArrayOf(Color.parseColor("#E4F3F1"), Color.parseColor("#D3EAE6")))
                    setGradientRadius(dp(34).toFloat())
                    setGradientCenter(0.35f, 0.35f)
                }
                layoutParams = LinearLayout.LayoutParams(dp(68), dp(68))
                setPadding(dp(19), dp(19), dp(19), dp(19))
            })

            addView(text("কোনো অ্যাপয়েন্টমেন্ট নেই", 14.5f, Typeface.BOLD, colorDark, Gravity.CENTER).apply {
                setPadding(0, dp(16), 0, 0)
            })

            addView(text("নতুন অ্যাপয়েন্টমেন্ট বুক করে ডাক্তারের সাথে পরামর্শ নিন", 12f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER).apply {
                setPadding(dp(10), dp(6), dp(10), 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            })

            addView(buildBookAppointmentButton().apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(22)
                }
            })
        }
    }

    private fun buildBookAppointmentButton(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark)
            ).apply { cornerRadius = dp(30).toFloat() }
            setPadding(dp(22), dp(13), dp(26), dp(13))
            elevation = dp(3).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(30).toFloat())
                }
            }
            clipToOutline = true
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(this@HomeActivity, BookAppointmentActivity::class.java)) }

            addView(ImageView(this@HomeActivity).apply {
                setImageDrawable(VectorIconDrawable(VectorIconDrawable.IconType.PLUS, Color.WHITE, dp(15)))
                background = roundedBg(Color.argb(50, 255, 255, 255), 8f)
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                setPadding(dp(4), dp(4), dp(4), dp(4))
            })

            addView(text("অ্যাপয়েন্টমেন্ট বুক করুন", 13f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
                setPadding(dp(10), 0, 0, 0)
            })
        }
    }

    private fun appointmentRow(date: String, time: String, reason: String, status: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(colorCard, 16f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(1).toFloat()
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
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(lighten(statusColor, 0.12f), statusColor)).apply {
                cornerRadius = dp(30).toFloat()
            }
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

    private fun callForSerial() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$doctorSerialPhone"))
        startActivity(intent)
    }

    private fun showComingSoon(feature: String) {
        Toast.makeText(this, "$feature শীঘ্রই আসছে", Toast.LENGTH_SHORT).show()
    }

    private fun onLogout() {
        showCustomConfirmDialog(
            iconType = VectorIconDrawable.IconType.LOGOUT,
            iconBgColor = Color.parseColor("#DC2626"),
            title = "লগ আউট",
            message = "আপনি কি লগ আউট করতে চান?",
            positiveText = "হ্যাঁ, লগ আউট",
            positiveColor = Color.parseColor("#DC2626"),
            negativeText = "না"
        ) {
            SupabaseClient.logout(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun onDeleteProfile() {
        showCustomConfirmDialog(
            iconType = VectorIconDrawable.IconType.TRASH,
            iconBgColor = Color.parseColor("#DC2626"),
            title = "প্রোফাইল ডিলিট করুন",
            message = "আপনি কি নিশ্চিতভাবে আপনার প্রোফাইল স্থায়ীভাবে মুছে ফেলতে চান? এই কাজটি ফিরিয়ে আনা যাবে না।",
            positiveText = "হ্যাঁ, ডিলিট করুন",
            positiveColor = Color.parseColor("#DC2626"),
            negativeText = "বাতিল"
        ) {
            performAccountDeletion()
        }
    }

    private fun performAccountDeletion() {
        val patientId = SupabaseClient.getPatientId(this)
        if (patientId == null) {
            Toast.makeText(this, "প্রোফাইল খুঁজে পাওয়া যায়নি, আবার লগইন করুন", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "প্রোফাইল ডিলিট করা হচ্ছে...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = SupabaseClient.deleteAccount(patientId)
            result.onSuccess {
                Toast.makeText(this@HomeActivity, "আপনার প্রোফাইল ডিলিট করা হয়েছে", Toast.LENGTH_SHORT).show()
                SupabaseClient.logout(this@HomeActivity)
                startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
                finish()
            }.onFailure {
                Toast.makeText(this@HomeActivity, "প্রোফাইল ডিলিট করা যায়নি, আবার চেষ্টা করুন", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCustomConfirmDialog(
        iconType: VectorIconDrawable.IconType,
        iconBgColor: Int,
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
        dialog.setCancelable(true)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(Color.WHITE, 22f)
            setPadding(dp(24), dp(26), dp(24), dp(20))
            elevation = dp(6).toFloat()
        }

        card.addView(ImageView(this).apply {
            setImageDrawable(VectorIconDrawable(iconType, Color.WHITE, dp(24)))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(iconBgColor)
            }
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            setPadding(dp(15), dp(15), dp(15), dp(15))
        })

        card.addView(text(title, 16f, Typeface.BOLD, colorDark, Gravity.CENTER).apply {
            setPadding(0, dp(16), 0, 0)
        })

        card.addView(text(message, 12.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER).apply {
            setPadding(0, dp(8), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(22)
            }
        }

        val negativeBtn = text(negativeText, 13.5f, Typeface.BOLD, colorTextMuted, Gravity.CENTER).apply {
            background = roundedBg(Color.parseColor("#F1F3F2"), 14f)
            setPadding(0, dp(13), 0, dp(13))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }

        val positiveBtn = text(positiveText, 13.5f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
            background = roundedBg(positiveColor, 14f)
            setPadding(0, dp(13), 0, dp(13))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                onPositive()
            }
        }

        buttonsRow.addView(negativeBtn)
        buttonsRow.addView(positiveBtn)
        card.addView(buttonsRow)

        dialog.setContentView(card)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.86).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    private fun profileActionButton(
        label: String,
        iconType: VectorIconDrawable.IconType,
        color: Int,
        bgColor: Int,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedBg(bgColor, 14f)
            setPadding(dp(10), dp(14), dp(10), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(ImageView(this@HomeActivity).apply {
                setImageDrawable(VectorIconDrawable(iconType, color, dp(16)))
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
            })

            addView(text(label, 13.5f, Typeface.BOLD, color, Gravity.CENTER).apply {
                setPadding(dp(8), 0, 0, 0)
            })
        }
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

    private fun lighten(color: Int, amount: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * amount).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * amount).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun darken(color: Int, amount: Float): Int {
        val r = (Color.red(color) * (1 - amount)).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * (1 - amount)).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * (1 - amount)).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    // ------------------------------------------------------------------
    // ক্যানভাসে আঁকা ভেক্টর আইকন
    // ------------------------------------------------------------------
    class VectorIconDrawable(
        private val type: IconType,
        initialColor: Int,
        private val sizePx: Int = 96
    ) : Drawable() {

        enum class IconType { HOME, CALENDAR, PHONE, PILL, PERSON, CHECK, STAR, LOGOUT, SUN, DOT, SHIELD, ARROW_RIGHT, DOCUMENT, TRASH, LOCATION, CLOCK, PLUS }

        private var iconColor: Int = initialColor
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = iconColor }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = iconColor
        }

        fun updateTint(c: Int) {
            iconColor = c
            fillPaint.color = c
            strokePaint.color = c
            invalidateSelf()
        }

        override fun getIntrinsicWidth(): Int = sizePx
        override fun getIntrinsicHeight(): Int = sizePx

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            strokePaint.strokeWidth = w * 0.09f
            canvas.save()
            canvas.translate(b.left.toFloat(), b.top.toFloat())
            val pad = w * 0.1f
            canvas.translate(pad, pad)
            val s = w - pad * 2
            when (type) {
                IconType.HOME -> drawHome(canvas, s)
                IconType.CALENDAR -> drawCalendar(canvas, s)
                IconType.PHONE -> drawPhone(canvas, s)
                IconType.PILL -> drawPill(canvas, s)
                IconType.PERSON -> drawPerson(canvas, s)
                IconType.CHECK -> drawCheck(canvas, s)
                IconType.STAR -> drawStar(canvas, s)
                IconType.LOGOUT -> drawLogout(canvas, s)
                IconType.SUN -> drawSun(canvas, s)
                IconType.DOT -> drawDot(canvas, s)
                IconType.SHIELD -> drawShield(canvas, s)
                IconType.ARROW_RIGHT -> drawArrowRight(canvas, s)
                IconType.DOCUMENT -> drawDocument(canvas, s)
                IconType.TRASH -> drawTrash(canvas, s)
                IconType.LOCATION -> drawLocation(canvas, s)
                IconType.CLOCK -> drawClock(canvas, s)
                IconType.PLUS -> drawPlus(canvas, s)
            }
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {
            fillPaint.alpha = alpha
            strokePaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.colorFilter = colorFilter
            strokePaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        private fun drawHome(canvas: Canvas, s: Float) {
            val u = s / 24f
            val path = Path().apply {
                moveTo(10f * u, 20f * u)
                lineTo(10f * u, 14f * u)
                lineTo(14f * u, 14f * u)
                lineTo(14f * u, 20f * u)
                lineTo(19f * u, 20f * u)
                lineTo(19f * u, 12f * u)
                lineTo(22f * u, 12f * u)
                lineTo(12f * u, 3f * u)
                lineTo(2f * u, 12f * u)
                lineTo(5f * u, 12f * u)
                lineTo(5f * u, 20f * u)
                close()
            }
            val roundedFill = Paint(fillPaint).apply {
                pathEffect = CornerPathEffect(s * 0.05f)
            }
            canvas.drawPath(path, roundedFill)
        }

        private fun drawCalendar(canvas: Canvas, s: Float) {
            canvas.drawRoundRect(0f, s * 0.14f, s, s, s * 0.12f, s * 0.12f, strokePaint)
            canvas.drawLine(0f, s * 0.36f, s, s * 0.36f, strokePaint)
            canvas.drawLine(s * 0.26f, 0f, s * 0.26f, s * 0.26f, strokePaint)
            canvas.drawLine(s * 0.74f, 0f, s * 0.74f, s * 0.26f, strokePaint)
            canvas.drawCircle(s * 0.32f, s * 0.60f, s * 0.06f, fillPaint)
            canvas.drawCircle(s * 0.5f, s * 0.60f, s * 0.06f, fillPaint)
            canvas.drawCircle(s * 0.68f, s * 0.60f, s * 0.06f, fillPaint)
            canvas.drawCircle(s * 0.32f, s * 0.80f, s * 0.06f, fillPaint)
            canvas.drawCircle(s * 0.5f, s * 0.80f, s * 0.06f, fillPaint)
        }

        private fun drawPhone(canvas: Canvas, s: Float) {
            val path = Path()
            path.moveTo(s * 0.08f, s * 0.20f)
            path.cubicTo(s * 0.05f, s * 0.05f, s * 0.22f, -s * 0.02f, s * 0.30f, s * 0.14f)
            path.cubicTo(s * 0.36f, s * 0.26f, s * 0.30f, s * 0.30f, s * 0.26f, s * 0.36f)
            path.cubicTo(s * 0.32f, s * 0.52f, s * 0.46f, s * 0.66f, s * 0.62f, s * 0.72f)
            path.cubicTo(s * 0.68f, s * 0.68f, s * 0.72f, s * 0.62f, s * 0.84f, s * 0.68f)
            path.cubicTo(s * 1.0f, s * 0.76f, s * 0.94f, s * 0.94f, s * 0.80f, s * 0.96f)
            path.cubicTo(s * 0.48f, s * 1.0f, s * 0.02f, s * 0.54f, s * 0.08f, s * 0.20f)
            path.close()
            canvas.drawPath(path, fillPaint)
        }

        private fun drawPill(canvas: Canvas, s: Float) {
            canvas.save()
            canvas.rotate(-45f, s / 2, s / 2)
            val r = s * 0.22f
            canvas.drawRoundRect(s * 0.1f, s * 0.35f, s * 0.9f, s * 0.65f, r, r, strokePaint)
            canvas.drawLine(s * 0.5f, s * 0.35f, s * 0.5f, s * 0.65f, strokePaint)
            canvas.drawRoundRect(s * 0.1f, s * 0.35f, s * 0.5f, s * 0.65f, r, r, fillPaint)
            canvas.restore()
        }

        private fun drawPerson(canvas: Canvas, s: Float) {
            canvas.drawCircle(s * 0.5f, s * 0.28f, s * 0.22f, fillPaint)
            val path = Path()
            val rectF = RectF(s * 0.08f, s * 0.55f, s * 0.92f, s * 1.25f)
            path.addArc(rectF, 180f, 180f)
            canvas.drawPath(path, fillPaint)
        }

        private fun drawCheck(canvas: Canvas, s: Float) {
            canvas.drawCircle(s / 2, s / 2, s / 2, fillPaint)
            val checkPaint = Paint(strokePaint).apply {
                color = Color.WHITE
                strokeWidth = s * 0.1f
            }
            val path = Path()
            path.moveTo(s * 0.28f, s * 0.52f)
            path.lineTo(s * 0.44f, s * 0.68f)
            path.lineTo(s * 0.74f, s * 0.32f)
            canvas.drawPath(path, checkPaint)
        }

        private fun drawStar(canvas: Canvas, s: Float) {
            val path = Path()
            val cx = s / 2
            val cy = s / 2
            val outerR = s / 2
            val innerR = outerR * 0.42f
            for (i in 0 until 10) {
                val angle = Math.toRadians((i * 36 - 90).toDouble())
                val r = if (i % 2 == 0) outerR else innerR
                val x = cx + (r * Math.cos(angle)).toFloat()
                val y = cy + (r * Math.sin(angle)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, fillPaint)
        }

        private fun drawLogout(canvas: Canvas, s: Float) {
            canvas.drawRoundRect(0f, 0f, s * 0.55f, s, s * 0.08f, s * 0.08f, strokePaint)
            canvas.drawLine(s * 0.40f, s * 0.5f, s * 0.98f, s * 0.5f, strokePaint)
            val arrow = Path()
            arrow.moveTo(s * 0.78f, s * 0.30f)
            arrow.lineTo(s * 0.98f, s * 0.5f)
            arrow.lineTo(s * 0.78f, s * 0.70f)
            canvas.drawPath(arrow, strokePaint)
        }

        private fun drawSun(canvas: Canvas, s: Float) {
            canvas.drawCircle(s / 2, s / 2, s * 0.28f, fillPaint)
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45).toDouble())
                val x1 = s / 2 + (s * 0.36f * Math.cos(angle)).toFloat()
                val y1 = s / 2 + (s * 0.36f * Math.sin(angle)).toFloat()
                val x2 = s / 2 + (s * 0.48f * Math.cos(angle)).toFloat()
                val y2 = s / 2 + (s * 0.48f * Math.sin(angle)).toFloat()
                canvas.drawLine(x1, y1, x2, y2, strokePaint)
            }
        }

        private fun drawDot(canvas: Canvas, s: Float) {
            canvas.drawCircle(s / 2, s / 2, s / 2, fillPaint)
        }

        private fun drawShield(canvas: Canvas, s: Float) {
            val path = Path()
            path.moveTo(s * 0.5f, 0f)
            path.lineTo(s * 0.95f, s * 0.18f)
            path.lineTo(s * 0.95f, s * 0.55f)
            path.cubicTo(s * 0.95f, s * 0.82f, s * 0.75f, s * 0.96f, s * 0.5f, s)
            path.cubicTo(s * 0.25f, s * 0.96f, s * 0.05f, s * 0.82f, s * 0.05f, s * 0.55f)
            path.lineTo(s * 0.05f, s * 0.18f)
            path.close()
            canvas.drawPath(path, fillPaint)
            val check = Paint(strokePaint).apply {
                color = Color.WHITE
                strokeWidth = s * 0.09f
            }
            val cp = Path()
            cp.moveTo(s * 0.32f, s * 0.5f)
            cp.lineTo(s * 0.45f, s * 0.64f)
            cp.lineTo(s * 0.70f, s * 0.34f)
            canvas.drawPath(cp, check)
        }

        private fun drawArrowRight(canvas: Canvas, s: Float) {
            canvas.drawLine(0f, s / 2, s * 0.75f, s / 2, strokePaint)
            val arrow = Path()
            arrow.moveTo(s * 0.55f, s * 0.25f)
            arrow.lineTo(s * 0.85f, s * 0.5f)
            arrow.lineTo(s * 0.55f, s * 0.75f)
            canvas.drawPath(arrow, strokePaint)
        }

        private fun drawDocument(canvas: Canvas, s: Float) {
            canvas.drawRoundRect(s * 0.08f, 0f, s * 0.92f, s, s * 0.10f, s * 0.10f, strokePaint)
            canvas.drawLine(s * 0.24f, s * 0.30f, s * 0.76f, s * 0.30f, strokePaint)
            canvas.drawLine(s * 0.24f, s * 0.52f, s * 0.76f, s * 0.52f, strokePaint)
            canvas.drawLine(s * 0.24f, s * 0.74f, s * 0.58f, s * 0.74f, strokePaint)
        }

        private fun drawTrash(canvas: Canvas, s: Float) {
            canvas.drawLine(s * 0.16f, s * 0.22f, s * 0.84f, s * 0.22f, strokePaint)
            canvas.drawLine(s * 0.38f, s * 0.22f, s * 0.42f, s * 0.06f, strokePaint)
            canvas.drawLine(s * 0.42f, s * 0.06f, s * 0.58f, s * 0.06f, strokePaint)
            canvas.drawLine(s * 0.58f, s * 0.06f, s * 0.62f, s * 0.22f, strokePaint)
            canvas.drawRoundRect(s * 0.22f, s * 0.22f, s * 0.78f, s * 0.96f, s * 0.05f, s * 0.05f, strokePaint)
            canvas.drawLine(s * 0.40f, s * 0.36f, s * 0.40f, s * 0.82f, strokePaint)
            canvas.drawLine(s * 0.60f, s * 0.36f, s * 0.60f, s * 0.82f, strokePaint)
        }

        private fun drawLocation(canvas: Canvas, s: Float) {
            val path = Path()
            path.moveTo(s * 0.5f, s * 0.98f)
            path.cubicTo(s * 0.15f, s * 0.62f, s * 0.08f, s * 0.42f, s * 0.08f, s * 0.34f)
            path.cubicTo(s * 0.08f, s * 0.12f, s * 0.27f, 0f, s * 0.5f, 0f)
            path.cubicTo(s * 0.73f, 0f, s * 0.92f, s * 0.12f, s * 0.92f, s * 0.34f)
            path.cubicTo(s * 0.92f, s * 0.42f, s * 0.85f, s * 0.62f, s * 0.5f, s * 0.98f)
            path.close()
            canvas.drawPath(path, strokePaint)
            canvas.drawCircle(s * 0.5f, s * 0.34f, s * 0.12f, fillPaint)
        }

        private fun drawClock(canvas: Canvas, s: Float) {
            canvas.drawCircle(s / 2, s / 2, s / 2 - strokePaint.strokeWidth / 2, strokePaint)
            canvas.drawLine(s * 0.5f, s * 0.5f, s * 0.5f, s * 0.22f, strokePaint)
            canvas.drawLine(s * 0.5f, s * 0.5f, s * 0.70f, s * 0.58f, strokePaint)
        }

        private fun drawPlus(canvas: Canvas, s: Float) {
            val plusStroke = Paint(strokePaint).apply { strokeWidth = s * 0.16f }
            canvas.drawLine(s * 0.5f, s * 0.06f, s * 0.5f, s * 0.94f, plusStroke)
            canvas.drawLine(s * 0.06f, s * 0.5f, s * 0.94f, s * 0.5f, plusStroke)
        }
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

    class PatientAvatarView(context: Context) : View(context) {
        var initial: String = "র"
            set(value) { field = value; invalidate() }

        private var photoBitmap: android.graphics.Bitmap? = null
        private var bitmapShader: android.graphics.BitmapShader? = null
        private val photoMatrix = android.graphics.Matrix()

        fun setPhoto(bitmap: android.graphics.Bitmap?) {
            photoBitmap = bitmap
            bitmapShader = if (bitmap != null) {
                android.graphics.BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            } else {
                null
            }
            requestLayout()
            invalidate()
        }

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.WHITE
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            bgPaint.setShadowLayer(12f, 0f, 5f, Color.argb(60, 0, 0, 0))
            textPaint.textSize = 30f * resources.displayMetrics.scaledDensity / resources.displayMetrics.density
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val bmp = photoBitmap
            val shader = bitmapShader

            if (bmp != null && shader != null) {
                val scale: Float
                val dx: Float
                val dy: Float
                if (bmp.width.toFloat() * h > w * bmp.height.toFloat()) {
                    scale = h / bmp.height.toFloat()
                    dx = (w - bmp.width * scale) * 0.5f
                    dy = 0f
                } else {
                    scale = w / bmp.width.toFloat()
                    dx = 0f
                    dy = (h - bmp.height * scale) * 0.5f
                }
                photoMatrix.reset()
                photoMatrix.setScale(scale, scale)
                photoMatrix.postTranslate(dx, dy)
                shader.setLocalMatrix(photoMatrix)
                photoPaint.shader = shader
                canvas.drawCircle(w / 2, h / 2, w / 2 - 3f, photoPaint)
                canvas.drawCircle(w / 2, h / 2, w / 2 - 3f, ringPaint)
            } else {
                bgPaint.shader = RadialGradient(w / 2, h / 2, w / 2, Color.parseColor("#16897A"), Color.parseColor("#0A4A42"), Shader.TileMode.CLAMP)
                canvas.drawCircle(w / 2, h / 2, w / 2 - 3f, bgPaint)
                canvas.drawCircle(w / 2, h / 2, w / 2 - 3f, ringPaint)
                textPaint.textSize = h * 0.4f
                val cy = h / 2 - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(initial.uppercase(), w / 2, cy, textPaint)
            }
        }
    }
}
