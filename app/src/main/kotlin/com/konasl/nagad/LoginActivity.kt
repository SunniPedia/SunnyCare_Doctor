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
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryLight = Color.parseColor("#16897A")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorAccent = Color.parseColor("#F59E0B")
    private val colorFieldBg = Color.parseColor("#F1F5F4")
    private val colorFieldBorder = Color.parseColor("#E2E8E6")
    private val colorTextMuted = Color.parseColor("#6B7280")
    private val colorDark = Color.parseColor("#111827")
    private val colorError = Color.parseColor("#D32F2F")

    // দুইটা নাম্বারেই WhatsApp যাবে
    private val ADMIN_WHATSAPP_NUMBER = "8801632336631" // এডমিন
    private val DOCTOR_WHATSAPP_NUMBER = "8801710355342" // ডাক্তার

    private val PIN_LENGTH = 4

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

    private lateinit var pinSection: LinearLayout
    private lateinit var pinSectionLabel: TextView
    private lateinit var pinView: OtpPinView
    private lateinit var pinConfirmLabel: TextView
    private lateinit var pinConfirmView: OtpPinView
    private lateinit var pinActionBtn: Button
    private lateinit var forgotPinText: TextView
    private lateinit var changeNumberText: TextView

    private lateinit var deviceBlockedSection: LinearLayout
    private lateinit var deviceBlockedMessage: TextView
    private lateinit var deviceRetryBtn: Button
    private lateinit var deviceHelpBtn: Button

    private enum class Field { PHONE, OTP, PIN, PIN_CONFIRM }
    private enum class PinMode { LOGIN, SETUP_EXISTING, SETUP_NEW }
    private enum class DeviceBlockReason { ACCOUNT_BOUND_ELSEWHERE, DEVICE_ALREADY_USED }

    private val phoneDigits = StringBuilder()
    private val otpDigits = StringBuilder()
    private val pinDigits = StringBuilder()
    private val pinConfirmDigits = StringBuilder()
    private var activeField = Field.PHONE

    private var currentPhone: String = ""
    private var currentPatient: JSONObject? = null
    private var pinMode: PinMode = PinMode.LOGIN
    private var deviceBlockReason: DeviceBlockReason = DeviceBlockReason.ACCOUNT_BOUND_ELSEWHERE
    private var canResend = false
    private var resendTimer: CountDownTimer? = null

    private val OTP_VALIDITY_MS = 2 * 60 * 1000L

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

        otpSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }
        }
        val otpLabel = text("৬ ডিজিটের OTP কোড দিন", 12.5f, Typeface.BOLD, colorTextMuted).apply { gravity = Gravity.START }

        otpPin = OtpPinView(this, 6).apply {
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

        pinSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }
        }

        pinSectionLabel = text("PIN দিন", 12.5f, Typeface.BOLD, colorTextMuted).apply {
            gravity = Gravity.START
        }

        pinView = OtpPinView(this, PIN_LENGTH).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
            ).apply { topMargin = dp(8) }
            onTap = { setActiveField(Field.PIN) }
        }

        pinConfirmLabel = text("PIN আবার দিন", 12.5f, Typeface.BOLD, colorTextMuted).apply {
            visibility = View.GONE
            gravity = Gravity.START
            setPadding(0, dp(14), 0, 0)
        }

        pinConfirmView = OtpPinView(this, PIN_LENGTH).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
            ).apply { topMargin = dp(8) }
            onTap = { setActiveField(Field.PIN_CONFIRM) }
        }

        pinActionBtn = premiumButton("লগইন করুন") { onPinAction() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        }

        forgotPinText = text("PIN ভুলে গেছেন? Admin/Doctor কে WhatsApp করুন", 12.5f, Typeface.BOLD, colorPrimary).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
            setOnClickListener { openForgotPinOnWhatsApp() }
        }

        changeNumberText = text("অন্য নাম্বার ব্যবহার করবেন?", 12f, Typeface.NORMAL, colorTextMuted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
            setOnClickListener { resetToPhoneEntry() }
        }

        pinSection.addView(pinSectionLabel)
        pinSection.addView(pinView)
        pinSection.addView(pinConfirmLabel)
        pinSection.addView(pinConfirmView)
        pinSection.addView(pinActionBtn)
        pinSection.addView(forgotPinText)
        pinSection.addView(changeNumberText)

        deviceBlockedSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }
        }
        deviceBlockedMessage = text(
            "এই একাউন্টটি অন্য একটি ডিভাইসে যুক্ত আছে।",
            13f, Typeface.NORMAL, colorDark
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        deviceRetryBtn = secondaryButton("🔄 আবার চেষ্টা করুন") { retryDeviceCheck() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
        }
        deviceHelpBtn = premiumButton("Admin কে WhatsApp এ জানান") { openDeviceChangeOnWhatsApp() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        val deviceChangeNumberText = text("অন্য নাম্বার ব্যবহার করবেন?", 12f, Typeface.NORMAL, colorTextMuted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
            setOnClickListener { resetToPhoneEntry() }
        }
        deviceBlockedSection.addView(deviceBlockedMessage)
        deviceBlockedSection.addView(deviceRetryBtn)
        deviceBlockedSection.addView(deviceHelpBtn)
        deviceBlockedSection.addView(deviceChangeNumberText)

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
        card.addView(pinSection)
        card.addView(deviceBlockedSection)
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

    private fun setActiveField(field: Field) {
        activeField = field
        phoneDisplay.isActive = field == Field.PHONE
        otpPin.isActive = field == Field.OTP
        pinView.isActive = field == Field.PIN
        pinConfirmView.isActive = field == Field.PIN_CONFIRM
    }

    private fun handleKey(key: String) {
        if (!keypad.isEnabled) return
        when {
            key == "back" -> {
                when (activeField) {
                    Field.PHONE -> if (phoneDigits.isNotEmpty()) phoneDigits.deleteCharAt(phoneDigits.length - 1)
                    Field.OTP -> if (otpDigits.isNotEmpty()) otpDigits.deleteCharAt(otpDigits.length - 1)
                    Field.PIN -> if (pinDigits.isNotEmpty()) pinDigits.deleteCharAt(pinDigits.length - 1)
                    Field.PIN_CONFIRM -> if (pinConfirmDigits.isNotEmpty()) pinConfirmDigits.deleteCharAt(pinConfirmDigits.length - 1)
                }
            }
            key.isNotEmpty() -> {
                when (activeField) {
                    Field.PHONE -> if (phoneDigits.length < 15) phoneDigits.append(key) // বিদেশির জন্য 15 পর্যন্ত
                    Field.OTP -> if (otpDigits.length < 6) otpDigits.append(key)
                    Field.PIN -> {
                        if (pinDigits.length < PIN_LENGTH) pinDigits.append(key)
                        if (pinDigits.length == PIN_LENGTH && pinConfirmView.visibility == View.VISIBLE) {
                            setActiveField(Field.PIN_CONFIRM)
                        }
                    }
                    Field.PIN_CONFIRM -> if (pinConfirmDigits.length < PIN_LENGTH) pinConfirmDigits.append(key)
                }
            }
        }
        refreshFields()
    }

    private fun refreshFields() {
        phoneDisplay.digits = phoneDigits.toString()
        otpPin.digits = otpDigits.toString()
        pinView.digits = pinDigits.toString()
        pinConfirmView.digits = pinConfirmDigits.toString()
    }

    // UPDATED: Smart Phone Validation
    private fun onPhoneNext() {
        val rawPhone = phoneDigits.toString().trim()
        val validation = SupabaseClient.PhoneValidator.validate(rawPhone)

        if (!validation.isValid) {
            statusText.text = validation.message
            return
        }

        // যদি বাংলাদেশি হয়, তাহলে ১১ ডিজিট মাস্ট - যেভাবে আছে সেভাবেই রাখা হলো
        if (!validation.isForeign && validation.normalizedPhone.length!= 11) {
            statusText.text = "বাংলাদেশি নাম্বার ১১ ডিজিটের হতে হবে"
            return
        }

        currentPhone = validation.normalizedPhone
        if (validation.isForeign) {
            statusText.text = "বিদেশি নাম্বার ডিটেক্ট করা হয়েছে: ${validation.normalizedPhone}"
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = SupabaseClient.findPatientByPhone(currentPhone)
            setLoading(false)
            result.onSuccess { patient ->
                if (patient!= null) {
                    currentPatient = patient
                    val deviceId = DeviceUtils.getDeviceId(this@LoginActivity)
                    val storedDeviceId = SupabaseClient.getDeviceIdFromPatient(patient)

                    // SMART FIX: device_id null/empty হলে অন্য ডিভাইস দিয়ে লগইন করতে পারবে
                    if (SupabaseClient.isDeviceIdEmpty(storedDeviceId)) {
                        // Device reset করা আছে, তাই সরাসরি PIN চাইবে এবং নতুন ডিভাইস বাইন্ড করবে
                        val hasPin = patient.optString("password_hash").isNotEmpty() &&
                                patient.optString("password_salt").isNotEmpty()
                        showPinSection(if (hasPin) PinMode.LOGIN else PinMode.SETUP_EXISTING)
                    } else if (storedDeviceId!= deviceId) {
                        // অন্য ডিভাইসে বাঁধা আছে - ব্লক
                        showDeviceBlocked(DeviceBlockReason.ACCOUNT_BOUND_ELSEWHERE)
                    } else {
                        val hasPin = patient.optString("password_hash").isNotEmpty() &&
                                patient.optString("password_salt").isNotEmpty()
                        showPinSection(if (hasPin) PinMode.LOGIN else PinMode.SETUP_EXISTING)
                    }
                } else {
                    startOtpFlow(currentPhone)
                }
            }.onFailure {
                statusText.text = it.message?: "সমস্যা হয়েছে, আবার চেষ্টা করুন"
            }
        }
    }

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
                statusText.text = it.message?: "OTP পাঠাতে সমস্যা হয়েছে"
            }
        }
    }

    private fun onVerifyOtp() {
        val code = otpDigits.toString().trim()
        if (code.length!= 6) {
            statusText.text = "৬ ডিজিটের কোড দিন"
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            val verifyResult = SupabaseClient.verifyOtp(currentPhone, code)
            val matched = verifyResult.getOrElse {
                setLoading(false)
                statusText.text = "দয়া করে সঠিক ওটিপি লিখুন"
                return@launch
            }
            if (!matched) {
                setLoading(false)
                statusText.text = "দয়া করে সঠিক ওটিপি লিখুন"
                return@launch
            }
            val deviceId = DeviceUtils.getDeviceId(this@LoginActivity)
            val conflictResult = SupabaseClient.findPatientByDeviceId(deviceId)
            if (conflictResult.getOrNull()!= null) {
                setLoading(false)
                showDeviceBlocked(DeviceBlockReason.DEVICE_ALREADY_USED)
                return@launch
            }
            setLoading(false)
            showPinSection(PinMode.SETUP_NEW)
        }
    }

    private fun showPinSection(mode: PinMode) {
        pinMode = mode
        sendOtpBtn.visibility = View.GONE
        otpSection.visibility = View.GONE
        deviceBlockedSection.visibility = View.GONE
        pinSection.visibility = View.VISIBLE
        keypad.visibility = View.VISIBLE
        pinDigits.clear()
        pinConfirmDigits.clear()
        refreshFields()
        statusText.text = ""

        when (mode) {
            PinMode.LOGIN -> {
                pinSectionLabel.text = "$PIN_LENGTH ডিজিটের PIN দিন"
                pinConfirmLabel.visibility = View.GONE
                pinConfirmView.visibility = View.GONE
                pinActionBtn.text = "লগইন করুন"
                forgotPinText.visibility = View.VISIBLE
            }
            PinMode.SETUP_EXISTING -> {
                pinSectionLabel.text = "আপনার অ্যাকাউন্টের জন্য একটি নতুন $PIN_LENGTH ডিজিটের PIN সেট করুন"
                pinConfirmLabel.visibility = View.VISIBLE
                pinConfirmView.visibility = View.VISIBLE
                pinActionBtn.text = "PIN সেট করুন"
                forgotPinText.visibility = View.GONE
            }
            PinMode.SETUP_NEW -> {
                pinSectionLabel.text = "আপনার নতুন একাউন্টের জন্য একটি $PIN_LENGTH ডিজিটের PIN সেট করুন"
                pinConfirmLabel.visibility = View.VISIBLE
                pinConfirmView.visibility = View.VISIBLE
                pinActionBtn.text = "পরবর্তী ধাপ"
                forgotPinText.visibility = View.GONE
            }
        }
        setActiveField(Field.PIN)
    }

    private fun showDeviceBlocked(reason: DeviceBlockReason = DeviceBlockReason.ACCOUNT_BOUND_ELSEWHERE) {
        deviceBlockReason = reason
        sendOtpBtn.visibility = View.GONE
        otpSection.visibility = View.GONE
        pinSection.visibility = View.GONE
        deviceBlockedSection.visibility = View.VISIBLE
        keypad.visibility = View.GONE
        statusText.text = ""
        deviceBlockedMessage.text = when (reason) {
            DeviceBlockReason.ACCOUNT_BOUND_ELSEWHERE ->
                "এই একাউন্টটি অন্য একটি ডিভাইসে যুক্ত আছে। Doctor/Admin ($ADMIN_WHATSAPP_NUMBER) ডিভাইস রিসেট করে দিলে নিচের 'আবার চেষ্টা করুন' বাটনে চাপ দিন।"
            DeviceBlockReason.DEVICE_ALREADY_USED ->
                "এই মোবাইল ফোনে ইতিমধ্যে অন্য একটি একাউন্ট ব্যবহার করা হচ্ছে। একটি ডিভাইসে একটি মাত্র একাউন্ট চালানো যায়।"
        }
    }

    private fun retryDeviceCheck() {
        setLoading(true)
        statusText.text = ""
        when (deviceBlockReason) {
            DeviceBlockReason.ACCOUNT_BOUND_ELSEWHERE -> {
                lifecycleScope.launch {
                    val result = SupabaseClient.findPatientByPhone(currentPhone)
                    setLoading(false)
                    result.onSuccess { patient ->
                        if (patient == null) {
                            statusText.text = "একাউন্ট খুঁজে পাওয়া যায়নি"
                            return@onSuccess
                        }
                        currentPatient = patient
                        val deviceId = DeviceUtils.getDeviceId(this@LoginActivity)
                        val storedDeviceId = SupabaseClient.getDeviceIdFromPatient(patient)

                        // SMART CHECK: এখন null কিনা চেক করছে
                        if (SupabaseClient.isDeviceIdEmpty(storedDeviceId)) {
                            Toast.makeText(this@LoginActivity, "ডিভাইস রিসেট হয়ে গেছে! এখন লগইন করুন", Toast.LENGTH_SHORT).show()
                            val hasPin = patient.optString("password_hash").isNotEmpty() &&
                                    patient.optString("password_salt").isNotEmpty()
                            showPinSection(if (hasPin) PinMode.LOGIN else PinMode.SETUP_EXISTING)
                        } else if (storedDeviceId == deviceId) {
                            // একই ডিভাইস
                            val hasPin = patient.optString("password_hash").isNotEmpty() &&
                                    patient.optString("password_salt").isNotEmpty()
                            showPinSection(if (hasPin) PinMode.LOGIN else PinMode.SETUP_EXISTING)
                        } else {
                            statusText.text = "এখনো অ্যাডমিন ডিভাইস রিসেট করেননি, একটু পর আবার চেষ্টা করুন"
                        }
                    }.onFailure {
                        statusText.text = it.message?: "সমস্যা হয়েছে"
                    }
                }
            }
            DeviceBlockReason.DEVICE_ALREADY_USED -> {
                val deviceId = DeviceUtils.getDeviceId(this@LoginActivity)
                lifecycleScope.launch {
                    val result = SupabaseClient.findPatientByDeviceId(deviceId)
                    setLoading(false)
                    result.onSuccess { conflict ->
                        if (conflict == null) {
                            Toast.makeText(this@LoginActivity, "ডিভাইস এখন খালি আছে! এগিয়ে যান", Toast.LENGTH_SHORT).show()
                            showPinSection(PinMode.SETUP_NEW)
                        } else {
                            statusText.text = "এই ডিভাইসে এখনো অন্য একাউন্ট বাঁধা আছে"
                        }
                    }.onFailure {
                        statusText.text = it.message?: "সমস্যা হয়েছে"
                    }
                }
            }
        }
    }

    private fun resetToPhoneEntry() {
        pinSection.visibility = View.GONE
        otpSection.visibility = View.GONE
        deviceBlockedSection.visibility = View.GONE
        keypad.visibility = View.VISIBLE
        sendOtpBtn.visibility = View.VISIBLE
        phoneDigits.clear()
        otpDigits.clear()
        pinDigits.clear()
        pinConfirmDigits.clear()
        refreshFields()
        currentPatient = null
        pinMode = PinMode.LOGIN
        deviceBlockReason = DeviceBlockReason.ACCOUNT_BOUND_ELSEWHERE
        statusText.text = ""
        setActiveField(Field.PHONE)
    }

    private fun finishLogin(patient: JSONObject) {
        SupabaseClient.saveSession(
            this@LoginActivity, patient.getString("id"), currentPhone, patient.getString("full_name")
        )
        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
        finish()
    }

    private fun onPinAction() {
        val pin = pinDigits.toString()
        if (pin.length!= PIN_LENGTH) {
            statusText.text = "$PIN_LENGTH ডিজিটের PIN দিন"
            return
        }

        when (pinMode) {
            PinMode.LOGIN -> {
                val patient = currentPatient?: return
                setLoading(true)
                lifecycleScope.launch {
                    val verifyResult = SupabaseClient.verifyPatientPassword(patient, pin)
                    val matched = verifyResult.getOrElse {
                        setLoading(false)
                        statusText.text = it.message?: "সমস্যা হয়েছে"
                        return@launch
                    }
                    if (!matched) {
                        setLoading(false)
                        statusText.text = "PIN সঠিক নয়"
                        pinDigits.clear()
                        refreshFields()
                        setActiveField(Field.PIN)
                        return@launch
                    }
                    val storedDeviceId = SupabaseClient.getDeviceIdFromPatient(patient)
                    if (SupabaseClient.isDeviceIdEmpty(storedDeviceId)) {
                        val deviceId = DeviceUtils.getDeviceId(this@LoginActivity)
                        val conflictResult = SupabaseClient.findPatientByDeviceId(deviceId, patient.getString("id"))
                        val conflict = conflictResult.getOrElse {
                            setLoading(false)
                            statusText.text = it.message?: "সমস্যা হয়েছে"
                            return@launch
                        }
                        if (conflict!= null) {
                            setLoading(false)
                            showDeviceBlocked(DeviceBlockReason.DEVICE_ALREADY_USED)
                            return@launch
                        }
                        SupabaseClient.bindDeviceToPatient(patient.getString("id"), deviceId)
                    }
                    setLoading(false)
                    finishLogin(patient)
                }
            }

            PinMode.SETUP_EXISTING -> {
                val confirm = pinConfirmDigits.toString()
                if (pin!= confirm) {
                    statusText.text = "দুটি PIN মিলছে না"
                    pinConfirmDigits.clear()
                    refreshFields()
                    return
                }
                val patient = currentPatient?: return
                val deviceId = DeviceUtils.getDeviceId(this)
                setLoading(true)
                lifecycleScope.launch {
                    val conflictResult = SupabaseClient.findPatientByDeviceId(deviceId, patient.getString("id"))
                    val conflict = conflictResult.getOrElse {
                        setLoading(false)
                        statusText.text = it.message?: "সমস্যা হয়েছে"
                        return@launch
                    }
                    if (conflict!= null) {
                        setLoading(false)
                        showDeviceBlocked(DeviceBlockReason.DEVICE_ALREADY_USED)
                        return@launch
                    }
                    val setResult = SupabaseClient.setPatientPassword(patient.getString("id"), pin)
                    if (setResult.isFailure) {
                        setLoading(false)
                        statusText.text = setResult.exceptionOrNull()?.message?: "PIN সেট করা যায়নি"
                        return@launch
                    }
                    SupabaseClient.bindDeviceToPatient(patient.getString("id"), deviceId)
                    setLoading(false)
                    finishLogin(patient)
                }
            }

            PinMode.SETUP_NEW -> {
                val confirm = pinConfirmDigits.toString()
                if (pin!= confirm) {
                    statusText.text = "দুটি PIN মিলছে না"
                    pinConfirmDigits.clear()
                    refreshFields()
                    return
                }
                val intent = Intent(this@LoginActivity, SignupActivity::class.java)
                intent.putExtra("phone", currentPhone)
                intent.putExtra("pin", pin)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun openForgotPinOnWhatsApp() {
        val message = "আসসালামু আলাইকুম, আমি SunnyCare অ্যাপে আমার PIN ভুলে গিয়েছি। আমার ফোন নাম্বার: $currentPhone । দয়া করে সাহায্য করুন।"
        val url = "https://wa.me/$ADMIN_WHATSAPP_NUMBER?text=" + Uri.encode(message)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            statusText.text = "WhatsApp খুলতে সমস্যা হয়েছে"
        }
    }

    private fun openDeviceChangeOnWhatsApp() {
        val message = "আসসালামু আলাইকুম, আমি SunnyCare অ্যাপে অন্য একটি ডিভাইস ব্যবহার করতে চাই। আমার ফোন নাম্বার: $currentPhone । দয়া করে আমার একাউন্টের ডিভাইস রিসেট করে দিন। Doctor: 01710355342 Admin: 01632336631"
        val url = "https://wa.me/$ADMIN_WHATSAPP_NUMBER?text=" + Uri.encode(message)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            deviceBlockedMessage.text = "WhatsApp খুলতে সমস্যা হয়েছে"
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        sendOtpBtn.isEnabled =!loading
        verifyBtn.isEnabled =!loading
        keypad.isEnabled =!loading
        keypad.alpha = if (loading) 0.5f else 1f
        pinActionBtn.isEnabled =!loading
        if (::deviceRetryBtn.isInitialized) {
            deviceRetryBtn.isEnabled =!loading
            deviceRetryBtn.alpha = if (loading) 0.6f else 1f
        }
        if (::deviceHelpBtn.isInitialized) deviceHelpBtn.isEnabled =!loading
    }

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

    private fun secondaryButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(colorPrimary)
        textSize = 14.5f
        isAllCaps = false
        setTypeface(null, Typeface.BOLD)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(15).toFloat()
            setColor(Color.WHITE)
            setStroke(dp(2), colorPrimary)
        }
        elevation = dp(1).toFloat()
        setPadding(0, dp(13), 0, dp(13))
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
                cursorVisible =!cursorVisible
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
            return digits.chunked(4).joinToString(" ")
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
                canvas.drawText("01XXXXXXXXX / + বিদেশি", padding, hy, hintPaint)
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

    class OtpPinView @JvmOverloads constructor(
        context: Context,
        private val boxCount: Int = 6
    ) : View(context) {

        var onTap: (() -> Unit)? = null
        var digits: String = ""
            set(value) { field = value.take(boxCount); invalidate() }
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
                cursorVisible =!cursorVisible
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
            if (idx!in keys.indices) return -1
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
                    return pressedIndex!= -1
                }
                MotionEvent.ACTION_MOVE -> {
                    val idx = indexAt(event.x, event.y)
                    if (idx!= pressedIndex) {
                        pressedIndex = -1
                        invalidate()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val idx = pressedIndex
                    pressedIndex = -1
                    invalidate()
                    if (idx!= -1) {
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
