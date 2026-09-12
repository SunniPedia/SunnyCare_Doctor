package com.konasl.nagad

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorAccent = Color.parseColor("#F59E0B")
    private val colorBg = Color.parseColor("#F4F7F6")
    private val colorTextMuted = Color.parseColor("#6B7280")
    private val colorCard = Color.WHITE

    // ডাক্তারের তথ্য
    private val doctorPhoneForCall = "+8801XXXXXXXXX" // TODO: বসান

    private lateinit var appointmentsContainer: LinearLayout
    private lateinit var greetingText: TextView

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

        val scroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(30))
        }

        // ---------------- HEADER ----------------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(colorPrimary, colorPrimaryDark))
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

        // ---------------- DOCTOR CARD ----------------
        val doctorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(colorCard, 20f)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(-18), dp(18), 0)
            }
            elevation = dp(6).toFloat()
        }
        val doctorTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = DoctorAvatarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
        }
        val doctorTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(14) }
        }
        doctorTextCol.addView(text("ডা. মাসুম বিল্লাহ সানি", 16f, Typeface.BOLD, Color.parseColor("#111827"), Gravity.START))
        doctorTextCol.addView(text("মেডিসিন, শিশু ও ডায়াবেটিস বিশেষজ্ঞ", 12f, Typeface.NORMAL, colorTextMuted, Gravity.START))
        doctorTopRow.addView(avatar)
        doctorTopRow.addView(doctorTextCol)

        val degreesText = text(
            "এম.বি.বি.এস (সি.ইউ), ডি.এম.ইউ (আল্ট্রা), পিজিটি,\nএম.সি.জি.পি (মেডিসিন ও শিশু), সি.সি.ডি (ডায়াবেটিস- বারডেম, ঢাকা)",
            11f, Typeface.NORMAL, colorTextMuted, Gravity.START
        ).apply { setPadding(0, dp(12), 0, 0) }

        val bmdcBadge = text("বি.এম.ডি.সি এ-১৭৬৩০ • Verified", 10.5f, Typeface.BOLD, colorPrimary, Gravity.START).apply {
            background = roundedBg(Color.parseColor("#E4F3F1"), 30f)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
        }

        val expertiseText = text(
            "নাক-কান-গলা, এলার্জি, শ্বাসকষ্ট, চর্মরোগ, উচ্চ রক্তচাপ ও বাত ব্যাথায় অভিজ্ঞ",
            11f, Typeface.NORMAL, colorTextMuted, Gravity.START
        ).apply { setPadding(0, dp(10), 0, 0) }

        doctorCard.addView(doctorTopRow)
        doctorCard.addView(degreesText)
        doctorCard.addView(bmdcBadge)
        doctorCard.addView(expertiseText)

        // ---------------- QUICK ACTIONS ----------------
        val actionsGrid = GridLayout(this).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(14), dp(18), dp(14), 0)
            }
        }
        actionsGrid.addView(actionCard("📅", "অ্যাপয়েন্টমেন্ট বুক", colorPrimary) { showBookAppointmentDialog() })
        actionsGrid.addView(actionCard("📞", "ডাক্তারকে কল করুন", Color.parseColor("#2563EB")) { callDoctor() })
        actionsGrid.addView(actionCard("💊", "প্রেসক্রিপশন", Color.parseColor("#7C3AED")) { showComingSoon("প্রেসক্রিপশন হিস্ট্রি") })
        actionsGrid.addView(actionCard("👤", "আমার প্রোফাইল", Color.parseColor("#DB2777")) { showProfileDialog() })

        // ---------------- APPOINTMENTS LIST ----------------
        val appointmentsHeader = text("আমার অ্যাপয়েন্টমেন্টসমূহ", 15f, Typeface.BOLD, Color.parseColor("#111827"), Gravity.START).apply {
            setPadding(dp(18), dp(24), dp(18), dp(10))
        }
        appointmentsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
        }

        page.addView(header)
        page.addView(doctorCard)
        page.addView(actionsGrid)
        page.addView(appointmentsHeader)
        page.addView(appointmentsContainer)

        scroll.addView(page)
        root.addView(scroll)
        setContentView(root)

        loadAppointments()
    }

    override fun onResume() {
        super.onResume()
        loadAppointments()
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
        col.addView(text("$date • $time", 13f, Typeface.BOLD, Color.parseColor("#111827"), Gravity.START))
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
    private fun showBookAppointmentDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(4))
        }
        val reasonInput = EditText(this).apply {
            hint = "সমস্যার সংক্ষিপ্ত বিবরণ"
            inputType = InputType.TYPE_CLASS_TEXT
            background = roundedBg(Color.parseColor("#F1F5F4"), 12f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val dateBtn = Button(this).apply {
            text = "তারিখ নির্বাচন করুন"
            isAllCaps = false
            setOnClickListener { pickDate(this) }
        }
        val timeBtn = Button(this).apply {
            text = "সময় নির্বাচন করুন"
            isAllCaps = false
            setOnClickListener { pickTime(this) }
        }
        layout.addView(reasonInput)
        layout.addView(space(dp(12)))
        layout.addView(dateBtn)
        layout.addView(space(dp(8)))
        layout.addView(timeBtn)

        AlertDialog.Builder(this)
            .setTitle("অ্যাপয়েন্টমেন্ট বুক করুন")
            .setView(layout)
            .setPositiveButton("বুক করুন") { _, _ ->
                val reason = reasonInput.text.toString().trim()
                val date = dateBtn.text.toString()
                val time = timeBtn.text.toString()
                if (date == "তারিখ নির্বাচন করুন" || time == "সময় নির্বাচন করুন") {
                    Toast.makeText(this, "তারিখ ও সময় নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                bookAppointment(reason, date, time)
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun pickDate(target: Button) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            target.text = "%04d-%02d-%02d".format(y, m + 1, d)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickTime(target: Button) {
        val cal = Calendar.getInstance()
        TimePickerDialog(this, { _, h, min ->
            target.text = "%02d:%02d".format(h, min)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun bookAppointment(reason: String, date: String, time: String) {
        val patientId = SupabaseClient.getPatientId(this) ?: return
        val phone = SupabaseClient.getPhone(this) ?: ""
        val name = SupabaseClient.getName(this) ?: ""
        lifecycleScope.launch {
            val result = SupabaseClient.createAppointment(patientId, name, phone, reason, date, time)
            result.onSuccess {
                Toast.makeText(this@HomeActivity, "অ্যাপয়েন্টমেন্ট বুক হয়েছে", Toast.LENGTH_SHORT).show()
                loadAppointments()
            }.onFailure {
                Toast.makeText(this@HomeActivity, it.message ?: "বুক করা যায়নি", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun callDoctor() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$doctorPhoneForCall"))
        startActivity(intent)
    }

    private fun showProfileDialog() {
        val msg = "নাম: ${SupabaseClient.getName(this)}\nফোন: ${SupabaseClient.getPhone(this)}"
        AlertDialog.Builder(this)
            .setTitle("আমার প্রোফাইল")
            .setMessage(msg)
            .setPositiveButton("ঠিক আছে", null)
            .show()
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
            setOnClickListener { onClick() }
        }
        val iconCircle = TextView(this).apply {
            text = emoji
            textSize = 22f
            gravity = Gravity.CENTER
            background = roundedBg(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)), 40f)
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        }
        val labelView = text(label, 12f, Typeface.BOLD, Color.parseColor("#111827"), Gravity.CENTER).apply {
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

    /** ডাক্তারের অ্যাভাটার - সূর্য + মেডিকেল ক্রস আইকন প্রোগ্রামেটিক্যালি আঁকা (drawable ছাড়া) */
    class DoctorAvatarView(context: android.content.Context) : View(context) {
        private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F6C61"); style = Paint.Style.FILL }
        private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val paintOrange = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B"); style = Paint.Style.FILL }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawCircle(w / 2, h / 2, w / 2, paintBg)
            canvas.drawCircle(w / 2, h / 2, w * 0.30f, paintOrange)
            val crossW = w * 0.22f; val crossH = w * 0.07f
            canvas.drawRect(w / 2 - crossW / 2, h / 2 - crossH / 2, w / 2 + crossW / 2, h / 2 + crossH / 2, paintWhite)
            canvas.drawRect(w / 2 - crossH / 2, h / 2 - crossW / 2, w / 2 + crossH / 2, h / 2 + crossW / 2, paintWhite)
        }
    }
}
