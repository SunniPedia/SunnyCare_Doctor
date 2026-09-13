package com.konasl.nagad

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    // ---------------------------------------------------------------
    // Palette
    // ---------------------------------------------------------------
    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryLight = Color.parseColor("#16897A")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorAccent = Color.parseColor("#F59E0B")
    private val colorFieldBg = Color.parseColor("#F1F5F4")
    private val colorFieldBorder = Color.parseColor("#E2E8E6")
    private val colorTextMuted = Color.parseColor("#6B7280")
    private val colorDark = Color.parseColor("#111827")
    private val colorError = Color.parseColor("#D32F2F")

    // পাসওয়ার্ড ভুলে গেলে এই WhatsApp নাম্বারে (অ্যাডমিন/ক্লিনিক) যোগাযোগ করতে বলা হবে
    private val ADMIN_WHATSAPP_NUMBER = "8801714656343"

    // ---------------------------------------------------------------
    // Views
    // ---------------------------------------------------------------
    private lateinit var phoneDisplay: PhoneDisplayView
    private lateinit var otpPin: OtpPinView
    private lateinit var keypad: NumericKeypadView
    private lateinit var otpSection: LinearLayout
    private lateinit var sendOtpBtn: Button
    private lateinit var verifyBtn: Button
    private lateinit var resendText: TextView
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var card: LinearLayout

    // --- পাসওয়ার্ড সেকশন (পুরাতন ইউজার লগইন / নতুন পাসওয়ার্ড সেট) ---
    private lateinit var passwordSection: LinearLayout
    private lateinit var passwordSectionLabel: TextView
    private lateinit var passwordInput: EditText
    private lateinit var passwordConfirmInput: EditText
    private lateinit var passwordActionBtn: Button
    private lateinit var forgotPasswordText: TextView
    private lateinit var changeNumberText: TextView

    // ---------------------------------------------------------------
    // State
    // ---------------------------------------------------------------
    private enum class Field { PHONE, OTP }

    /** পাসওয়ার্ড সেকশন তিনটা আলাদা কনটেক্সটে ব্যবহার হয়:
     *  LOGIN          - বিদ্যমান পাসওয়ার্ড দিয়ে লগইন (রেজিস্টার্ড ইউজার, পাসওয়ার্ড সেট আছে)
     *  SETUP_EXISTING - রেজিস্টার্ড কিন্তু পুরাতন/পাসওয়ার্ড-বিহীন অ্যাকাউন্টের জন্য নতুন পাসওয়ার্ড সেট
     *  SETUP_NEW      - একদম নতুন (এখনো তৈরি হয়নি এমন) পেশেন্টের জন্য পাসওয়ার্ড সেট, যেটা সাইনআপ ফর্মের সাথে পাঠানো হবে
     */
    private enum class PasswordMode { LOGIN, SETUP_EXISTING, SETUP_NEW }

    private val phoneDigits = StringBuilder()
    private val otpDigits = StringBuilder()
    private var activeField = Field.PHONE

    private var currentPhone: String = ""
    private var currentPatient: JSONObject? = null
    private var passwordMode: PasswordMode = PasswordMode.LOGIN
    private var canResend = false
    private var resendTimer: CountDownTimer? = null

    private val OTP_VALIDITY_MS = 2 * 60 * 1000L // ২ মিনিট

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark)
            )
        }

        val decor = BackgroundDecorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val scroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(52), dp(24), dp(36))
        }

        val icon = AuthIconView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(78), dp(78)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }

        val title = text("SunnyCare ☀", 26f, Typeface.BOLD, Color.WHITE).apply { letterSpacing = 0.01f }
        val subtitle = text("মোবাইল নাম্বার দিয়ে লগইন করুন", 13.5f, Typeface.NORMAL, Color.argb(220, 255, 255, 255))

        // ---- Premium Card ----
        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.WHITE, 26f)
            setPadding(dp(22), dp(26), dp(22), dp(22))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(30) }
            elevation = dp(18).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(26).toFloat())
                }
            }
            clipToOutline = true
        }

        val phoneLabel = text("ফোন নাম্বার", 12.5f, Typeface.BOLD, colorTextMuted).apply {
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        phoneDisplay = PhoneDisplayView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
            ).apply { topMargin = dp(8) }
            isActive = true
            onTap = { setActiveField(Field.PHONE) }
        }

        sendOtpBtn = premiumButton("পরবর্তী") { onPhoneNext() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        }

        // ---- OTP section (হাইড থাকে, OTP পাঠানোর পর দেখায়) ----
        otpSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }
        }
        val otpLabel = text("৬ ডিজিটের OTP কোড দিন", 12.5f, Typeface.BOLD, colorTextMuted).apply { gravity = Gravity.START }

        otpPin = OtpPinView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
            ).apply { topMargin = dp(10) }
            onTap = { setActiveField(Field.OTP) }
        }

        verifyBtn = premiumButton("ভেরিফাই করুন") { onVerifyOtp() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        }

        resendText = text("OTP আবার পাঠান", 12.5f, Typeface.BOLD, colorPrimary).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
            setOnClickListener { if (canResend) startOtpFlow(currentPhone) }
        }

        otpSection.addView(otpLabel)
        otpSection.addView(otpPin)
        otpSection.addView(verifyBtn)
        otpSection.addView(resendText)

        // ---- Password section (হাইড থাকে; পুরাতন ইউজারের লগইন বা নতুন পাসওয়ার্ড সেট করার জন্য দেখায়) ----
        passwordSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }
        }

        passwordSectionLabel = text("পাসওয়ার্ড দিন", 12.5f, Typeface.BOLD, colorTextMuted).apply {
            gravity = Gravity.START
        }

        passwordInput = passwordField("পাসওয়ার্ড").apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(8) }
        }

        passwordConfirmInput = passwordField("পাসওয়ার্ড আবার লিখুন").apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(10) }
        }

        passwordActionBtn = premiumButton("লগইন করুন") { onPasswordAction() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        }

        forgotPasswordText = text("পাসওয়ার্ড ভুলে গেছেন? Admin-কে WhatsApp এ জানান", 12.5f, Typeface.BOLD, colorPrimary).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
            setOnClickListener { openForgotPasswordOnWhatsApp() }
        }

        changeNumberText = text("অন্য নাম্বার ব্যবহার করবেন?", 12f, Typeface.NORMAL, colorTextMuted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
            setOnClickListener { resetToPhoneEntry() }
        }

        passwordSection.addView(passwordSectionLabel)
        passwordSection.addView(passwordInput)
        passwordSection.addView(passwordConfirmInput)
        passwordSection.addView(passwordActionBtn)
        passwordSection.addView(forgotPasswordText)
        passwordSection.addView(changeNumberText)

        progress = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14); gravity = Gravity.CENTER_HORIZONTAL }
        }

        statusText = text("", 12.5f, Typeface.NORMAL, colorError).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }

        // ---- Built-in numeric keypad (fully canvas drawn) ----
        keypad = NumericKeypadView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(232)
            ).apply { topMargin = dp(20) }
            onKey = { key -> handleKey(key) }
        }

        card.addView(phoneLabel)
        card.addView(phoneDisplay)
        card.addView(sendOtpBtn)
        card.addView(otpSection)
        card.addView(passwordSection)
        card.addView(progress)
        card.addView(statusText)
        card.addView(keypad)

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
        root.addView(decor)
        root.addView(scroll)
        setContentView(root)
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }

    // ------------------------------------------------------------------
    // Keypad routing
    // ------------------------------------------------------------------
    private fun setActiveField(field: Field) {
        activeField = field
        phoneDisplay.isActive = field == Field.PHONE
        otpPin.isActive = field == Field.OTP
    }

    private fun handleKey(key: String) {
        if (!keypad.isEnabled) return
        when {
            key == "back" -> {
                if (activeField == Field.PHONE) {
                    if (phoneDigits.isNotEmpty()) phoneDigits.deleteCharAt(phoneDigits.length - 1)
                } else {
                    if (otpDigits.isNotEmpty()) otpDigits.deleteCharAt(otpDigits.length - 1)
                }
            }
            key.isNotEmpty() -> {
                if (activeField == Field.PHONE) {
                    if (phoneDigits.length < 11) phoneDigits.append(key)
                } else {
                    if (otpDigits.length < 6) otpDigits.append(key)
                }
            }
        }
        refreshFields()
    }

    private fun refreshFields() {
        phoneDisplay.digits = phoneDigits.toString()
        otpPin.digits = otpDigits.toString()
    }

    // ------------------------------------------------------------------
    // ফোন নাম্বার দেওয়ার পর "পরবর্তী" চাপলে প্রথমে চেক করা হয় নাম্বারটা রেজিস্টার্ড কিনা।
    // রেজিস্টার্ড হলে পাসওয়ার্ড চাওয়া হয় (OTP ছাড়াই — শুধু ফোন নাম্বার দিয়ে যেন কেউ ঢুকতে না পারে)।
    // নতুন নাম্বার হলে আগের মতোই OTP ভেরিফিকেশনের মাধ্যমে সাইনআপে পাঠানো হয়।
    // ------------------------------------------------------------------
    private fun onPhoneNext() {
        val phone = phoneDigits.toString().trim()
        if (phone.length < 11) {
            statusText.text = "সঠিক ফোন নাম্বার দিন"
            return
        }
        currentPhone = phone
        setLoading(true)
        lifecycleScope.launch {
            val result = SupabaseClient.findPatientByPhone(phone)
            setLoading(false)
            result.onSuccess { patient ->
                if (patient != null) {
                    currentPatient = patient
                    val hasPassword = patient.optString("password_hash").isNotEmpty() &&
                        patient.optString("password_salt").isNotEmpty()
                    // আগে থেকে রেজিস্টার্ড কিন্তু কোনো কারণে পাসওয়ার্ড সেট নেই এমন (পুরাতন) অ্যাকাউন্টকে
                    // প্রথমবার একটা পাসওয়ার্ড সেট করতে বলা হয়, তারপর সরাসরি লগইন হয়ে যায়।
                    showPasswordSection(if (hasPassword) PasswordMode.LOGIN else PasswordMode.SETUP_EXISTING)
                } else {
                    startOtpFlow(phone)
                }
            }.onFailure {
                statusText.text = it.message ?: "সমস্যা হয়েছে, আবার চেষ্টা করুন"
            }
        }
    }

    /** নতুন (অ-রেজিস্টার্ড) নাম্বারের জন্য OTP পাঠায়; ভেরিফাই হলে প্রোফাইল তৈরির (সাইনআপ) পেইজে যায়, যেখানে পাসওয়ার্ডও সেট করতে হবে। */
    private fun startOtpFlow(phone: String) {
        setLoading(true)
        lifecycleScope.launch {
            val result = SupabaseClient.requestOtp(phone)
            setLoading(false)
            result.onSuccess { code ->
                sendOtpBtn.visibility = View.GONE
                otpSection.visibility = View.VISIBLE
                otpDigits.clear()
                refreshFields()
                statusText.text = ""
                setActiveField(Field.OTP)
                showOtpSentDialog(code)
                startResendTimer()
            }.onFailure {
                statusText.text = it.message ?: "OTP পাঠাতে সমস্যা হয়েছে"
            }
        }
    }

    private fun onVerifyOtp() {
        val code = otpDigits.toString().trim()
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
                    statusText.text = "দয়া করে সঠিক ওটিপি লিখুন"
                    return@onSuccess
                }
                setLoading(false)
                // এই নাম্বারটা নতুন (findPatientByPhone আগেই null দিয়েছিল) — তাই এখনই এখানে পাসওয়ার্ড সেট করতে বলা হচ্ছে।
                // এই পাসওয়ার্ডটাই একটু পর সাইনআপ ফর্মের সাথে পাঠিয়ে দেওয়া হবে এবং প্রোফাইল তৈরির সময় সেভ হবে।
                showPasswordSection(PasswordMode.SETUP_NEW)
            }.onFailure {
                setLoading(false)
                statusText.text = "দয়া করে সঠিক ওটিপি লিখুন"
            }
        }
    }

    // ------------------------------------------------------------------
    // পাসওয়ার্ড সেকশন — লগইন (বিদ্যমান পাসওয়ার্ড দিয়ে) অথবা সেটআপ (প্রথমবার পাসওয়ার্ড তৈরি)
    // ------------------------------------------------------------------
    private fun showPasswordSection(mode: PasswordMode) {
        passwordMode = mode
        sendOtpBtn.visibility = View.GONE
        otpSection.visibility = View.GONE
        passwordSection.visibility = View.VISIBLE
        passwordInput.text?.clear()
        passwordConfirmInput.text?.clear()
        statusText.text = ""

        when (mode) {
            PasswordMode.LOGIN -> {
                passwordSectionLabel.text = "পাসওয়ার্ড দিন"
                passwordConfirmInput.visibility = View.GONE
                passwordActionBtn.text = "লগইন করুন"
                forgotPasswordText.visibility = View.VISIBLE
            }
            PasswordMode.SETUP_EXISTING -> {
                passwordSectionLabel.text = "আপনার অ্যাকাউন্টের জন্য একটি নতুন পাসওয়ার্ড সেট করুন"
                passwordConfirmInput.visibility = View.VISIBLE
                passwordActionBtn.text = "পাসওয়ার্ড সেট করুন"
                forgotPasswordText.visibility = View.GONE
            }
            PasswordMode.SETUP_NEW -> {
                passwordSectionLabel.text = "আপনার নতুন একাউন্টের জন্য একটি পাসওয়ার্ড সেট করুন"
                passwordConfirmInput.visibility = View.VISIBLE
                passwordActionBtn.text = "পরবর্তী ধাপ"
                forgotPasswordText.visibility = View.GONE
            }
        }
        passwordInput.requestFocus()
    }

    /** "অন্য নাম্বার ব্যবহার করবেন?" চাপলে পুরো ফর্ম রিসেট হয়ে আবার ফোন নাম্বার চাওয়া হয় */
    private fun resetToPhoneEntry() {
        passwordSection.visibility = View.GONE
        otpSection.visibility = View.GONE
        sendOtpBtn.visibility = View.VISIBLE
        phoneDigits.clear()
        otpDigits.clear()
        refreshFields()
        currentPatient = null
        passwordMode = PasswordMode.LOGIN
        statusText.text = ""
        setActiveField(Field.PHONE)
    }

    private fun onPasswordAction() {
        val pwd = passwordInput.text?.toString().orEmpty()
        if (pwd.length < 4) {
            statusText.text = "কমপক্ষে ৪ ক্যারেক্টারের পাসওয়ার্ড দিন"
            return
        }

        when (passwordMode) {
            PasswordMode.LOGIN -> {
                val patient = currentPatient ?: return
                setLoading(true)
                lifecycleScope.launch {
                    val result = SupabaseClient.verifyPatientPassword(patient, pwd)
                    setLoading(false)
                    result.onSuccess { matched ->
                        if (matched) {
                            SupabaseClient.saveSession(
                                this@LoginActivity, patient.getString("id"), currentPhone, patient.getString("full_name")
                            )
                            startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                            finish()
                        } else {
                            statusText.text = "পাসওয়ার্ড সঠিক নয়"
                        }
                    }.onFailure {
                        statusText.text = it.message ?: "সমস্যা হয়েছে"
                    }
                }
            }

            PasswordMode.SETUP_EXISTING -> {
                val confirm = passwordConfirmInput.text?.toString().orEmpty()
                if (pwd != confirm) {
                    statusText.text = "দুটি পাসওয়ার্ড মিলছে না"
                    return
                }
                val patient = currentPatient ?: return
                setLoading(true)
                lifecycleScope.launch {
                    val result = SupabaseClient.setPatientPassword(patient.getString("id"), pwd)
                    setLoading(false)
                    result.onSuccess {
                        SupabaseClient.saveSession(
                            this@LoginActivity, patient.getString("id"), currentPhone, patient.getString("full_name")
                        )
                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                        finish()
                    }.onFailure {
                        statusText.text = it.message ?: "পাসওয়ার্ড সেট করা যায়নি"
                    }
                }
            }

            PasswordMode.SETUP_NEW -> {
                val confirm = passwordConfirmInput.text?.toString().orEmpty()
                if (pwd != confirm) {
                    statusText.text = "দুটি পাসওয়ার্ড মিলছে না"
                    return
                }
                // এই ফোন নাম্বারের patient row এখনো তৈরি হয়নি (id নেই), তাই এখান থেকে সরাসরি
                // Supabase-এ পাসওয়ার্ড সেভ করা সম্ভব না। পাসওয়ার্ডটা সাইনআপ ফর্মের সাথে পাঠিয়ে দেওয়া
                // হচ্ছে — প্রোফাইল সাবমিট করার সময় SignupActivity, SupabaseClient.registerPatient()-কে
                // এই পাসওয়ার্ডসহ কল করবে, তখনই hash হয়ে সেভ হবে।
                val intent = Intent(this@LoginActivity, SignupActivity::class.java)
                intent.putExtra("phone", currentPhone)
                intent.putExtra("password", pwd)
                startActivity(intent)
                finish()
            }
        }
    }

    /** পাসওয়ার্ড ভুলে গেলে সরাসরি অ্যাডমিনের WhatsApp নাম্বারে চ্যাট ওপেন করে, ফোন নাম্বারসহ একটা রেডি মেসেজ বসিয়ে দেয় */
    private fun openForgotPasswordOnWhatsApp() {
        val message = "আসসালামু আলাইকুম, আমি SunnyCare অ্যাপে আমার পাসওয়ার্ড ভুলে গিয়েছি। আমার ফোন নাম্বার: $currentPhone । দয়া করে সাহায্য করুন।"
        val url = "https://wa.me/$ADMIN_WHATSAPP_NUMBER?text=" + Uri.encode(message)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            statusText.text = "WhatsApp খুলতে সমস্যা হয়েছে, সরাসরি $ADMIN_WHATSAPP_NUMBER নাম্বারে যোগাযোগ করুন"
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        sendOtpBtn.isEnabled = !loading
        verifyBtn.isEnabled = !loading
        keypad.isEnabled = !loading
        keypad.alpha = if (loading) 0.5f else 1f
        passwordInput.isEnabled = !loading
        passwordConfirmInput.isEnabled = !loading
        passwordActionBtn.isEnabled = !loading
    }

    // ------------------------------------------------------------------
    // ২ মিনিটের রিসেন্ড টাইমার
    // ------------------------------------------------------------------
    private fun startResendTimer() {
        canResend = false
        resendText.alpha = 0.55f
        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(OTP_VALIDITY_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSec = millisUntilFinished / 1000
                val m = totalSec / 60
                val s = totalSec % 60
                resendText.text = "OTP আবার পাঠান (%02d:%02d)".format(m, s)
            }

            override fun onFinish() {
                canResend = true
                resendText.alpha = 1f
                resendText.text = "OTP আবার পাঠান"
            }
        }.start()
    }

    // ------------------------------------------------------------------
    // কাস্টম OTP ডায়ালগ (কোনো টেকনিক্যাল ব্যাখ্যা ছাড়া, শুধু কোড দেখানো)
    // ------------------------------------------------------------------
    private fun showOtpSentDialog(code: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(Color.WHITE, 26f)
            setPadding(dp(28), dp(30), dp(28), dp(26))
            elevation = dp(20).toFloat()
        }

        val badge = OtpBadgeView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(66), dp(66)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }

        val title = text("আপনার OTP কোড", 15.5f, Typeface.BOLD, colorDark).apply {
            setPadding(0, dp(16), 0, 0)
        }

        val codeText = text(code, 32f, Typeface.BOLD, colorPrimary).apply {
            letterSpacing = 0.3f
            setPadding(0, dp(10), 0, 0)
        }

        val note = text("২ মিনিটের মধ্যে কোডটি লিখুন", 12f, Typeface.NORMAL, colorTextMuted).apply {
            setPadding(0, dp(10), 0, 0)
        }

        val okBtn = premiumButton("ঠিক আছে") { dialog.dismiss() }.apply {
            setPadding(dp(40), dp(12), dp(40), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }
        }

        layout.addView(badge)
        layout.addView(title)
        layout.addView(codeText)
        layout.addView(note)
        layout.addView(okBtn)

        dialog.setContentView(layout)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    // ------------------------------------------------------------------
    // Small UI helpers
    // ------------------------------------------------------------------
    private fun text(t: String, sizeSp: Float, style: Int, color: Int): TextView = TextView(this).apply {
        text = t; textSize = sizeSp; setTypeface(null, style); setTextColor(color); gravity = Gravity.CENTER
    }

    private fun space(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, h) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** স্ট্যান্ডার্ড সিস্টেম কীবোর্ড ব্যবহার করে এমন পাসওয়ার্ড ফিল্ড (অক্ষর + সংখ্যা দুটোই সাপোর্ট করে) */
    private fun passwordField(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        setHintTextColor(colorTextMuted)
        setTextColor(colorDark)
        textSize = 15.5f
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        isSingleLine = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(colorFieldBg)
            setStroke(dp(2), colorFieldBorder)
        }
        setPadding(dp(16), dp(10), dp(16), dp(10))
    }

    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(color)
    }

    /** Premium gradient button with a soft press-scale animation (no XML drawables/resources used). */
    private fun premiumButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 15f
        isAllCaps = false
        setTypeface(null, Typeface.BOLD)
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(colorPrimaryLight, colorPrimaryDark)
        ).apply { cornerRadius = dp(15).toFloat() }
        elevation = dp(3).toFloat()
        setPadding(0, dp(14), 0, dp(14))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
        setOnClickListener { onClick() }
    }

    // ==================================================================
    // Canvas-drawn custom views (no drawable resources used anywhere)
    // ==================================================================

    /** Soft translucent circles floating over the header gradient, for depth. */
    class BackgroundDecorView(context: Context) : View(context) {
        private val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(26, 255, 255, 255) }
        private val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(16, 255, 255, 255) }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawCircle(w * 0.85f, h * 0.07f, w * 0.38f, p1)
            canvas.drawCircle(w * 0.10f, h * 0.20f, w * 0.24f, p2)
            canvas.drawCircle(w * 0.92f, h * 0.30f, w * 0.15f, p2)
        }
    }

    /** সূর্য ভিতরে মেডিকেল ক্রস - প্রোগ্রামেটিক্যালি আঁকা, soft shadow সহ */
    class AuthIconView(context: Context) : View(context) {
        private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val paintOrange = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            paintWhite.setShadowLayer(14f, 0f, 6f, Color.argb(60, 0, 0, 0))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            val cx = w / 2; val cy = h / 2
            canvas.drawCircle(cx, cy, w / 2 - 6f, paintWhite)
            paintOrange.shader = RadialGradient(
                cx, cy, w * 0.26f,
                Color.parseColor("#FBBF24"), Color.parseColor("#F59E0B"),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, w * 0.24f, paintOrange)
            val crossW = w * 0.22f; val crossH = w * 0.075f
            canvas.drawRect(cx - crossW / 2, cy - crossH / 2, cx + crossW / 2, cy + crossH / 2, paintWhite)
            canvas.drawRect(cx - crossH / 2, cy - crossW / 2, cx + crossH / 2, cy + crossW / 2, paintWhite)
        }
    }

    /** ফোন নাম্বার দেখানোর কাস্টম ফিল্ড - সিস্টেম কীবোর্ড ছাড়া, নিচের কীপ্যাড থেকে ইনপুট নেয়। */
    class PhoneDisplayView(context: Context) : View(context) {

        var onTap: (() -> Unit)? = null
        var digits: String = ""
            set(value) { field = value; invalidate() }
        var isActive: Boolean = false
            set(value) {
                field = value
                if (value) startBlink() else { stopBlink(); cursorVisible = false }
                invalidate()
            }

        private var cursorVisible = true
        private val handler = Handler(Looper.getMainLooper())
        private val blinkRunnable = object : Runnable {
            override fun run() {
                cursorVisible = !cursorVisible
                invalidate()
                handler.postDelayed(this, 500)
            }
        }

        private val colorPrimary = Color.parseColor("#0F6C61")
        private val colorFieldBg = Color.parseColor("#F1F5F4")
        private val colorFieldBorder = Color.parseColor("#E2E8E6")
        private val colorTextMuted = Color.parseColor("#9CA3AF")
        private val colorDark = Color.parseColor("#111827")

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorFieldBg; style = Paint.Style.FILL }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(1.5f)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT; typeface = Typeface.DEFAULT_BOLD; color = colorDark
            letterSpacing = 0.12f
        }
        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT; color = colorTextMuted
        }

        init {
            textPaint.textSize = spToPx(17f)
            hintPaint.textSize = spToPx(15f)
        }

        private fun dp(v: Float): Float = v * resources.displayMetrics.density
        private fun spToPx(v: Float): Float = v * resources.displayMetrics.scaledDensity

        private fun startBlink() {
            handler.removeCallbacks(blinkRunnable)
            cursorVisible = true
            handler.postDelayed(blinkRunnable, 500)
        }

        private fun stopBlink() = handler.removeCallbacks(blinkRunnable)

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopBlink()
        }

        private fun formatted(): String {
            if (digits.isEmpty()) return ""
            return digits.chunked(4).joinToString("  ")
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            val r = dp(14f)
            borderPaint.color = if (isActive) colorPrimary else colorFieldBorder
            canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, bgPaint)
            canvas.drawRoundRect(RectF(dp(0.75f), dp(0.75f), w - dp(0.75f), h - dp(0.75f)), r, r, borderPaint)

            val padding = dp(16f)
            val cy = h / 2

            if (digits.isEmpty()) {
                val hy = cy - (hintPaint.descent() + hintPaint.ascent()) / 2
                canvas.drawText("01XXXXXXXXX", padding, hy, hintPaint)
                if (isActive && cursorVisible) drawCursor(canvas, padding, cy)
            } else {
                val display = formatted()
                val ty = cy - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(display, padding, ty, textPaint)
                if (isActive && cursorVisible) {
                    val textW = textPaint.measureText(display)
                    drawCursor(canvas, padding + textW + dp(4f), cy)
                }
            }
        }

        private fun drawCursor(canvas: Canvas, x: Float, cy: Float) {
            val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorPrimary; strokeWidth = dp(2f); strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(x, cy - dp(11f), x, cy + dp(11f), cursorPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                onTap?.invoke()
                performClick()
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    /** ৬-বক্সের OTP পিন ভিউ - সিস্টেম কীবোর্ড ছাড়া, নিচের কীপ্যাড থেকে ইনপুট নেয়। */
    class OtpPinView(context: Context) : View(context) {

        var onTap: (() -> Unit)? = null
        var digits: String = ""
            set(value) { field = value.take(6); invalidate() }
        var isActive: Boolean = false
            set(value) {
                field = value
                if (value) startBlink() else { stopBlink(); cursorVisible = false }
                invalidate()
            }

        private var cursorVisible = true
        private val handler = Handler(Looper.getMainLooper())
        private val blinkRunnable = object : Runnable {
            override fun run() {
                cursorVisible = !cursorVisible
                invalidate()
                handler.postDelayed(this, 500)
            }
        }

        private val colorPrimary = Color.parseColor("#0F6C61")
        private val colorFieldBg = Color.parseColor("#F1F5F4")
        private val colorFieldBorder = Color.parseColor("#E2E8E6")
        private val colorDark = Color.parseColor("#111827")

        private val filledBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E4F3F1") }
        private val emptyBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorFieldBg }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.6f) }
        private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = colorDark
        }

        init { digitPaint.textSize = spToPx(20f) }

        private fun dp(v: Float): Float = v * resources.displayMetrics.density
        private fun spToPx(v: Float): Float = v * resources.displayMetrics.scaledDensity

        private fun startBlink() {
            handler.removeCallbacks(blinkRunnable)
            cursorVisible = true
            handler.postDelayed(blinkRunnable, 500)
        }

        private fun stopBlink() = handler.removeCallbacks(blinkRunnable)

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopBlink()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val boxCount = 6
            val gap = dp(8f)
            val totalGap = gap * (boxCount - 1)
            val boxW = (width - totalGap) / boxCount
            val boxH = height.toFloat()
            val r = dp(12f)

            for (i in 0 until boxCount) {
                val left = i * (boxW + gap)
                val right = left + boxW
                val filled = i < digits.length
                val isCursorHere = isActive && i == digits.length && cursorVisible

                borderPaint.color = if (filled || isCursorHere) colorPrimary else colorFieldBorder
                val rect = RectF(left, 0f, right, boxH)
                canvas.drawRoundRect(rect, r, r, if (filled) filledBg else emptyBg)
                canvas.drawRoundRect(rect, r, r, borderPaint)

                if (filled) {
                    val cx = left + boxW / 2
                    val cy = boxH / 2 - (digitPaint.descent() + digitPaint.ascent()) / 2
                    canvas.drawText(digits[i].toString(), cx, cy, digitPaint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                onTap?.invoke()
                performClick()
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    /** ইন-বিল্ট নাম্বার কীপ্যাড - সম্পূর্ণ Canvas দিয়ে আঁকা, কোনো resource/drawable ছাড়া। */
    class NumericKeypadView(context: Context) : View(context) {

        var onKey: ((String) -> Unit)? = null

        private val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "back")
        private var pressedIndex = -1

        private val colorDark = Color.parseColor("#111827")
        private val colorMuted = Color.parseColor("#6B7280")
        private val keyBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FAFBFB") }
        private val keyPressedBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E4F3F1") }
        private val keyBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(1f); color = Color.parseColor("#E5E9E8")
        }
        private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = colorDark
        }
        private val backspacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(2f); strokeCap = Paint.Cap.ROUND; color = colorMuted
        }
        private val backspaceFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(1.4f); color = colorMuted
        }

        init { digitPaint.textSize = spToPx(22f) }

        private fun dp(v: Float): Float = v * resources.displayMetrics.density
        private fun spToPx(v: Float): Float = v * resources.displayMetrics.scaledDensity

        private fun indexAt(x: Float, y: Float): Int {
            if (x < 0 || y < 0 || x > width || y > height) return -1
            val cols = 3; val rows = 4
            val col = (x / (width / cols.toFloat())).toInt().coerceIn(0, cols - 1)
            val row = (y / (height / rows.toFloat())).toInt().coerceIn(0, rows - 1)
            val idx = row * cols + col
            if (idx !in keys.indices) return -1
            if (keys[idx].isEmpty()) return -1
            return idx
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cols = 3; val rows = 4
            val keyW = width / cols.toFloat()
            val keyH = height / rows.toFloat()
            val gap = dp(9f)
            val r = dp(16f)
            val alphaMul = if (isEnabled) 255 else 130

            for (i in keys.indices) {
                val label = keys[i]
                if (label.isEmpty()) continue
                val row = i / cols; val col = i % cols
                val left = col * keyW + gap / 2
                val top = row * keyH + gap / 2
                val right = (col + 1) * keyW - gap / 2
                val bottom = (row + 1) * keyH - gap / 2
                val rect = RectF(left, top, right, bottom)

                val bg = if (i == pressedIndex) keyPressedBg else keyBg
                bg.alpha = alphaMul
                canvas.drawRoundRect(rect, r, r, bg)
                keyBorder.alpha = alphaMul
                canvas.drawRoundRect(rect, r, r, keyBorder)

                val cx = (left + right) / 2
                val cy = (top + bottom) / 2

                if (label == "back") {
                    backspacePaint.alpha = alphaMul
                    backspaceFill.alpha = alphaMul
                    drawBackspaceIcon(canvas, cx, cy, keyH * 0.34f)
                } else {
                    digitPaint.alpha = alphaMul
                    val ty = cy - (digitPaint.descent() + digitPaint.ascent()) / 2
                    canvas.drawText(label, cx, ty, digitPaint)
                }
            }
        }

        private fun drawBackspaceIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            val w = size * 1.6f; val h = size
            val path = Path().apply {
                moveTo(cx - w / 2, cy)
                lineTo(cx - w / 2 + h / 2, cy - h / 2)
                lineTo(cx + w / 2, cy - h / 2)
                lineTo(cx + w / 2, cy + h / 2)
                lineTo(cx - w / 2 + h / 2, cy + h / 2)
                close()
            }
            canvas.drawPath(path, backspaceFill)
            val xLeft = cx - w * 0.08f; val xRight = cx + w * 0.30f
            canvas.drawLine(xLeft, cy - h * 0.22f, xRight, cy + h * 0.22f, backspacePaint)
            canvas.drawLine(xLeft, cy + h * 0.22f, xRight, cy - h * 0.22f, backspacePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled) return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pressedIndex = indexAt(event.x, event.y)
                    invalidate()
                    return pressedIndex != -1
                }
                MotionEvent.ACTION_MOVE -> {
                    val idx = indexAt(event.x, event.y)
                    if (idx != pressedIndex) {
                        pressedIndex = -1
                        invalidate()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val idx = pressedIndex
                    pressedIndex = -1
                    invalidate()
                    if (idx != -1) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onKey?.invoke(keys[idx])
                        performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    pressedIndex = -1
                    invalidate()
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    /** কাস্টম ডায়ালগের ভেতরের ব্যাজ আইকন - বৃত্তের ভিতরে একটা মেসেজ/চিঠি চিহ্ন (drawable ছাড়া আঁকা) */
    class OtpBadgeView(context: Context) : View(context) {
        private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E4F3F1"); style = Paint.Style.FILL }
        private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F6C61"); style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            val cx = w / 2; val cy = h / 2
            canvas.drawCircle(cx, cy, w / 2, paintBg)
            val boxW = w * 0.42f; val boxH = h * 0.28f
            val left = cx - boxW / 2; val top = cy - boxH / 2
            val right = cx + boxW / 2; val bottom = cy + boxH / 2
            canvas.drawRect(left, top, right, bottom, paintStroke)
            canvas.drawLine(left, top, cx, cy + boxH * 0.12f, paintStroke)
            canvas.drawLine(right, top, cx, cy + boxH * 0.12f, paintStroke)
        }
    }
}
