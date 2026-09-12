package com.konasl.nagad

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#FDFCF8")) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val phone = SupabaseClient.getLoggedPhone(this) ?: ""

        // Top Bar programmatically
        val topBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val avatarBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#0F6C61")) }
        val avatar = TextView(this).apply {
            text = "☀"
            textSize = 18f
            gravity = Gravity.CENTER
            background = avatarBg
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
        val greetLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        val greet = TextView(this).apply { text = "আসসালামু আলাইকুম"; textSize = 12f; setTextColor(Color.GRAY) }
        val nameView = TextView(this).apply {
            text = "রোগী - $phone"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#111827"))
            // Fetch name from Supabase
            lifecycleScope.launch {
                try {
                    val p = SupabaseClient.client.from("profiles").select { filter { eq("phone", phone) } }.decodeList<Map<String, String>>().firstOrNull()
                    text = p?.get("name") ?: "রোগী"
                } catch (_: Exception) {}
            }
        }
        greetLayout.addView(greet)
        greetLayout.addView(nameView)

        // Logout button programmatically icon
        val logoutBtn = TextView(this).apply {
            text = "⎋"
            textSize = 20f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.WHITE); setStroke(dp(1), Color.parseColor("#E5E7EB")) }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) }
            isClickable = true
            setOnClickListener {
                SupabaseClient.logout(this@HomeActivity)
                startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
                finishAffinity()
            }
        }

        topBar.addView(avatar)
        topBar.addView(greetLayout)
        topBar.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        topBar.addView(logoutBtn)

        // Doctor Card - Programmatically Gradient + all details
        val doctorCardBg = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            colors = intArrayOf(Color.parseColor("#0F6C61"), Color.parseColor("#0A4A42"))
            orientation = GradientDrawable.Orientation.TL_BR
        }
        val doctorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = doctorCardBg
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) }
            elevation = dp(6).toFloat()
        }

        val doctorTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val doctorIcon = SunnyIconSmall(this).apply { layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)) }
        val doctorInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        val drName = TextView(this).apply { text = "ডা. মাসুম বিল্লাহ সানি"; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) }
        val drDegree = TextView(this).apply { text = "এম.বি.বি.এস, ডি.এম.ইউ, পিজিটি\nবি.এম.ডি.সি: এ-১৭৬৩০"; textSize = 10f; setTextColor(Color.argb(200, 255, 255, 255)) }
        doctorInfo.addView(drName)
        doctorInfo.addView(drDegree)

        val availabilityDot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#10B981")) }
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { leftMargin = dp(8) }
        }
        val availText = TextView(this).apply { text = "Available"; textSize = 10f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#6EE7B7")); setPadding(dp(6), 0, 0, 0) }

        doctorTop.addView(doctorIcon)
        doctorTop.addView(doctorInfo)
        doctorTop.addView(availabilityDot)
        doctorTop.addView(availText)

        val expertise = TextView(this).apply {
            text = "মেডিসিন-শিশু, ডায়াবেটিস, উচ্চ রক্তচাপ, বাত ব্যথা, নাক-কান-গলা, এলার্জি, শ্বাসকষ্ট ও চর্মরোগে অভিজ্ঞ।"
            textSize = 10f
            setTextColor(Color.argb(160, 255, 255, 255))
            setPadding(0, dp(14), 0, 0)
        }

        doctorCard.addView(doctorTop)
        doctorCard.addView(expertise)

        // Quick Actions - 2x2 Grid programmatically
        val gridLabel = TextView(this).apply { text = "সেবা সমূহ"; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#111827")); setPadding(0, dp(24), 0, dp(12)) }

        val gridLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun createActionRow(leftTitle: String, leftIcon: String, leftColor: String, rightTitle: String, rightIcon: String, rightColor: String): LinearLayout {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) } }
            fun createCard(title: String, icon: String, color: String): LinearLayout {
                val bg = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE); setStroke(dp(1), Color.parseColor("#F3F4F6")) }
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = bg
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    layoutParams = LinearLayout.LayoutParams(0, dp(110), 1f).apply { setMargins(dp(5), 0, dp(5), 0) }
                    isClickable = true
                    elevation = dp(2).toFloat()
                }
                val iconBg = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor(color)) }
                val iconView = TextView(this).apply { text = icon; textSize = 18f; gravity = Gravity.CENTER; background = iconBg; setPadding(dp(10), dp(10), dp(10), dp(10)); layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)) }
                val titleView = TextView(this).apply { text = title; textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#111827")); setPadding(0, dp(10), 0, 0) }
                card.addView(iconView)
                card.addView(titleView)
                return card
            }
            row.addView(createCard(leftTitle, leftIcon, leftColor))
            row.addView(createCard(rightTitle, rightIcon, rightColor))
            return row
        }

        gridLayout.addView(createActionRow("অ্যাপয়েন্টমেন্ট\nবুক করুন", "📅", "#E6F4F2", "ডাক্তারের সাথে\nচ্যাট", "💬", "#FEF3C7"))
        gridLayout.addView(createActionRow("আমার\nপ্রেসক্রিপশন", "📄", "#DBEAFE", "আমার\nরিপোর্ট", "🖼️", "#FCE7F3"))

        root.addView(topBar)
        root.addView(doctorCard)
        root.addView(gridLabel)
        root.addView(gridLayout)

        scroll.addView(root)
        setContentView(scroll)
    }

    // Small Sun Icon for Home
    class SunnyIconSmall(context: Context) : View(context) {
        val pW = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val pO = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B") }
        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            c.drawCircle(cx, cy, width / 2f, pW)
            c.drawCircle(cx, cy, width * 0.22f, pO)
            val cw = width * 0.22f; val ch = width * 0.07f
            c.drawRect(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2, pW)
            c.drawRect(cx - ch / 2, cy - cw / 2, cx + ch / 2, cy + cw / 2, pW)
        }
    }

    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
