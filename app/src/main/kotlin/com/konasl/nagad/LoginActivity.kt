package com.konasl.nagad

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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

class LoginActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorAccent = Color.parseColor("#F59E0B")
    private val colorFieldBg = Color.parseColor("#F1F5F4")
    private val colorTextMuted = Color.parseColor("#6B7280")

    private lateinit var phoneInput: EditText
    private lateinit var otpInput: EditText
    private lateinit var otpSection: LinearLayout
    private lateinit var sendOtpBtn: Button
    private lateinit var verifyBtn: Button
    private lateinit var resendText: TextView
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar

    private var currentPhone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(colorPrimary, colorPrimaryDark)
            )
        }

        val scroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(56), dp(28), dp(40))
        }

        val icon = AuthIconView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }

        val title = text("SunnyCare ☀", 26f, Typeface.BOLD, Color.WHITE)
        val subtitle = text("মোবাইল নাম্বার দিয়ে লগইন করুন", 13.5f, Typeface.NORMAL, Color.argb(220, 255, 255, 255))

        // ---- Card ----
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.WHITE, 24f)
            setPadding(dp(24), dp(28), dp(24), dp(28))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(32) }
        }

        val phoneLabel = text("ফোন নাম্বার", 12.5f, Typeface.BOLD, colorTextMuted).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        phoneInput = EditText(this).apply {
            hint = "01XXXXXXXXX"
            inputType = InputType.TYPE_CLASS_PHONE
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBg(colorFieldBg, 14f)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        sendOtpBtn = primaryButton("OTP পাঠান") { onSendOtp() }

        // ---- OTP section (hidden initially) ----
        otpSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
        }
        val otpLabel = text("৬ ডিজিটের OTP কোড দিন", 12.5f, Typeface.BOLD, colorTextMuted)
        otpInput = EditText(this).apply {
            hint = "• • • • • •"
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBg(colorFieldBg, 14f)
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        verifyBtn = primaryButton("ভেরিফাই করুন") { onVerifyOtp() }
        resendText = text("OTP আবার পাঠান", 12.5f, Typeface.BOLD, colorPrimary).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
            setOnClickListener { onSendOtp() }
        }
        otpSection.addView(otpLabel)
        otpSection.addView(otpInput)
        otpSection.addView(space(dp(14)))
        otpSection.addView(verifyBtn)
        otpSection.addView(resendText)

        progress = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14); gravity = Gravity.CENTER_HORIZONTAL }
        }

        statusText = text("", 12.5f, Typeface.NORMAL, Color.parseColor("#D32F2F")).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }

        card.addView(phoneLabel)
        card.addView(phoneInput)
        card.addView(space(dp(14)))
        card.addView(sendOtpBtn)
        card.addView(otpSection)
        card.addView(progress)
        card.addView(statusText)

        val signupHint = text("নতুন ইউজার? OTP ভেরিফাই করার পর প্রোফাইল তৈরি করুন", 11.5f, Typeface.NORMAL, Color.argb(200, 255, 255, 255)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(20), dp(16), 0)
        }

        container.addView(icon)
        container.addView(space(dp(16)))
        container.addView(title)
        container.addView(subtitle)
        container.addView(card)
        container.addView(signupHint)

        scroll.addView(container)
        root.addView(scroll)
        setContentView(root)
    }

    // ------------------------------------------------------------------
    private fun onSendOtp() {
        val phone = phoneInput.text.toString().trim()
        if (phone.length < 11) {
            statusText.text = "সঠিক ফোন নাম্বার দিন"
            return
        }
        currentPhone = phone
        setLoading(true)
        lifecycleScope.launch {
            val result = SupabaseClient.requestOtp(phone)
            setLoading(false)
            result.onSuccess { code ->
                otpSection.visibility = View.VISIBLE
                statusText.text = ""
                // যেহেতু রিয়েল SMS গেটওয়ে নেই, ডেমো হিসেবে কোডটা ডায়ালগে দেখানো হচ্ছে
                AlertDialog.Builder(this@LoginActivity)
                    .setTitle("আপনার OTP কোড (Demo)")
                    .setMessage("কোড: $code\n\nবাস্তব SMS পাঠাতে Supabase-এ একটি SMS provider (Twilio/Vonage) যুক্ত করতে হবে।")
                    .setPositiveButton("ঠিক আছে", null)
                    .show()
            }.onFailure {
                statusText.text = it.message ?: "OTP পাঠাতে সমস্যা হয়েছে"
            }
        }
    }

    private fun onVerifyOtp() {
        val code = otpInput.text.toString().trim()
        if (code.length != 6) {
            statusText.text = "৬ ডিজিটের কোড দিন"
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            val verifyResult = SupabaseClient.verifyOtp(currentPhone, code)
            verifyResult.onSuccess { matched ->
                if (!matched) {
                    setLoading(false)
                    statusText.text = "কোড সঠিক নয় বা মেয়াদ শেষ"
                    return@onSuccess
                }
                val patientResult = SupabaseClient.findPatientByPhone(currentPhone)
                setLoading(false)
                patientResult.onSuccess { patient ->
                    if (patient != null) {
                        SupabaseClient.saveSession(
                            this@LoginActivity,
                            patient.getString("id"),
                            currentPhone,
                            patient.getString("full_name")
                        )
                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                        finish()
                    } else {
                        val intent = Intent(this@LoginActivity, SignupActivity::class.java)
                        intent.putExtra("phone", currentPhone)
                        startActivity(intent)
                        finish()
                    }
                }.onFailure {
                    statusText.text = it.message ?: "সমস্যা হয়েছে"
                }
            }.onFailure {
                setLoading(false)
                statusText.text = it.message ?: "ভেরিফিকেশনে সমস্যা হয়েছে"
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        sendOtpBtn.isEnabled = !loading
        verifyBtn.isEnabled = !loading
    }

    // ------------------------------------------------------------------
    // Small UI helpers
    // ------------------------------------------------------------------
    private fun text(t: String, sizeSp: Float, style: Int, color: Int): TextView = TextView(this).apply {
        text = t; textSize = sizeSp; setTypeface(null, style); setTextColor(color); gravity = Gravity.CENTER
    }

    private fun space(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, h) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(color)
    }

    private fun primaryButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 15f
        isAllCaps = false
        setTypeface(null, Typeface.BOLD)
        background = roundedBg(colorPrimary, 14f)
        setPadding(0, dp(14), 0, dp(14))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setOnClickListener { onClick() }
    }

    /** ছোট আইকন - সূর্য ভিতরে মেডিকেল ক্রস (drawable ছাড়া প্রোগ্রামেটিক্যালি আঁকা) */
    class AuthIconView(context: android.content.Context) : View(context) {
        private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val paintOrange = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B"); style = Paint.Style.FILL }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            val cx = w / 2; val cy = h / 2
            canvas.drawCircle(cx, cy, w / 2, paintWhite)
            canvas.drawCircle(cx, cy, w * 0.24f, paintOrange)
            val crossW = w * 0.22f; val crossH = w * 0.075f
            canvas.drawRect(cx - crossW / 2, cy - crossH / 2, cx + crossW / 2, cy + crossH / 2, paintWhite)
            canvas.drawRect(cx - crossH / 2, cy - crossW / 2, cx + crossH / 2, cy + crossW / 2, paintWhite)
        }
    }
}
