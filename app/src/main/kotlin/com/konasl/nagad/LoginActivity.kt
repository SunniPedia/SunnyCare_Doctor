package com.konasl.nagad

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FDFCF8"))
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }

        // Icon - Same as MainActivity but smaller
        val iconView = MainActivity.SunnyCareIconView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(88), dp(88)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(24) }
        }

        val title = TextView(this).apply {
            text = "SunnyCare এ স্বাগতম"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F6C61"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }

        val sub = TextView(this).apply {
            text = "আপনার মোবাইল নাম্বার দিন, OTP Supabase থেকে আসবে (ফ্রি)"
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), 0)
        }

        val inputBg = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.WHITE)
            setStroke(dp(1), Color.parseColor("#D1D5DB"))
        }

        val phoneLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = inputBg
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) }
        }

        val countryCode = TextView(this).apply {
            text = "🇧🇩 +88"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F6C61"))
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor("#E6F4F2")) }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val phoneInput = EditText(this).apply {
            hint = "01XXXXXXXXX"
            inputType = InputType.TYPE_CLASS_PHONE
            background = null
            setPadding(dp(12), dp(14), dp(12), dp(14))
            textSize = 17f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        phoneLayout.addView(countryCode)
        phoneLayout.addView(phoneInput)

        // OTP Info Banner - Programmatically
        val otpInfoBg = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.parseColor("#FFFBEB"))
            setStroke(dp(1), Color.parseColor("#F59E0B"))
        }
        val otpInfo = TextView(this).apply {
            text = "💡 Demo: Supabase otps table এ 10k OTP আগে থেকেই আছে। কোড অ্যাপেই দেখাবে, SMS লাগবে না।"
            textSize = 10.5f
            setTextColor(Color.parseColor("#92400E"))
            background = otpInfoBg
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
        }

        val btnBg = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            colors = intArrayOf(Color.parseColor("#0F6C61"), Color.parseColor("#0A4A42"))
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
        }

        val btn = TextView(this).apply {
            text = "OTP পাঠান  →"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = btnBg
            setPadding(0, dp(18), 0, dp(18))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) }
            isClickable = true
            isFocusable = true
        }

        val loadingDots = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(16) }
        }

        btn.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            if (phone.length != 11 || !phone.startsWith("01")) {
                Toast.makeText(this, "সঠিক 11 ডিজিট নাম্বার দিন", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btn.isEnabled = false
            loadingDots.visibility = View.VISIBLE

            lifecycleScope.launch {
                try {
                    // Supabase থেকে OTP fetch
                    val otpRow = SupabaseClient.client.from("otps").select {
                        filter { eq("phone", phone) }
                    }.decodeList<Map<String, String>>().firstOrNull()

                    val code = otpRow?.get("code") ?: run {
                        // যদি না থাকে, নতুন generate করে insert
                        val newCode = (100000..999999).random().toString()
                        SupabaseClient.client.from("otps").insert(mapOf("phone" to phone, "code" to newCode))
                        newCode
                    }

                    // Show OTP in-app banner (No SMS cost)
                    Toast.makeText(this@LoginActivity, "🔐 আপনার OTP: $code", Toast.LENGTH_LONG).show()

                    // Check if profile exists
                    val profileExists = SupabaseClient.client.from("profiles").select {
                        filter { eq("phone", phone) }
                    }.decodeList<Map<String, String>>().isNotEmpty()

                    val intent = if (profileExists) {
                        Intent(this@LoginActivity, OtpVerifyActivity::class.java).apply {
                            putExtra("phone", phone)
                            putExtra("code", code)
                            putExtra("isLogin", true)
                        }
                    } else {
                        Intent(this@LoginActivity, OtpVerifyActivity::class.java).apply {
                            putExtra("phone", phone)
                            putExtra("code", code)
                            putExtra("isLogin", false)
                        }
                    }
                    startActivity(intent)

                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    btn.isEnabled = true
                    loadingDots.visibility = View.GONE
                }
            }
        }

        root.addView(iconView)
        root.addView(title)
        root.addView(sub)
        root.addView(phoneLayout)
        root.addView(otpInfo)
        root.addView(btn)
        root.addView(loadingDots)

        setContentView(root)
    }

    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

// Temporary OtpVerifyActivity inside same package for flow
class OtpVerifyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val phone = intent.getStringExtra("phone") ?: ""
        val realCode = intent.getStringExtra("code") ?: ""
        val isLogin = intent.getBooleanExtra("isLogin", false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(24), dp(60), dp(24), dp(24))
        }

        val title = TextView(this).apply { text = "OTP যাচাই করুন"; textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#0F6C61")) }
        val sub = TextView(this).apply { text = "$phone নাম্বারে পাঠানো কোডটি দিন\n(Demo কোড: $realCode)"; textSize = 13f; setTextColor(Color.GRAY) }

        val otpInput = EditText(this).apply {
            hint = "6 ডিজিট কোড"
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 20f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#F3F4F6")); setStroke(dp(1), Color.parseColor("#0F6C61")) }
            setPadding(dp(16), dp(18), dp(16), dp(18))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) }
        }

        val btn = TextView(this).apply {
            text = "যাচাই করুন"
            textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#0F6C61")) }
            setPadding(0, dp(18), 0, dp(18))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) }
            isClickable = true
            setOnClickListener {
                if (otpInput.text.toString() == realCode) {
                    if (isLogin) {
                        SupabaseClient.saveLogin(this@OtpVerifyActivity, phone)
                        startActivity(Intent(this@OtpVerifyActivity, HomeActivity::class.java))
                        finishAffinity()
                    } else {
                        startActivity(Intent(this@OtpVerifyActivity, SignupActivity::class.java).apply { putExtra("phone", phone) })
                    }
                } else {
                    Toast.makeText(this@OtpVerifyActivity, "ভুল OTP", Toast.LENGTH_SHORT).show()
                }
            }
        }

        root.addView(title)
        root.addView(sub)
        root.addView(otpInput)
        root.addView(btn)
        setContentView(root)
    }
    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
