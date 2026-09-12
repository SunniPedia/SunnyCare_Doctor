package com.konasl.nagad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

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

        // FIXED: Crash-proof OTP logic + Auto Copy
        btn.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            if (phone.length != 11 || !phone.startsWith("01")) {
                Toast.makeText(this, "সঠিক 11 ডিজিট নাম্বার দিন", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Prevent double click
            btn.isEnabled = false
            btn.alpha = 0.6f
            btn.text = "পাঠানো হচ্ছে..."
            loadingDots.visibility = View.VISIBLE

            lifecycleScope.launch {
                try {
                    // SAFE: Use JsonObject instead of Map<String,String> to avoid crash
                    var code: String? = null

                    try {
                        val result = SupabaseClient.client.from("otps")
                            .select(columns = Columns.list("code")) {
                                filter { eq("phone", phone) }
                            }.decodeSingleOrNull<JsonObject>()

                        code = result?.get("code")?.jsonPrimitive?.content
                        Log.d("SunnyCare", "Found OTP: $code for $phone")
                    } catch (e: Exception) {
                        Log.e("SunnyCare", "OTP fetch error: ${e.message}")
                    }

                    // যদি না থাকে, নতুন generate
                    if (code.isNullOrEmpty()) {
                        code = (100000..999999).random().toString()
                        try {
                            SupabaseClient.client.from("otps").insert(
                                buildJsonObject {
                                    put("phone", phone)
                                    put("code", code)
                                }
                            )
                        } catch (e: Exception) {
                            Log.e("SunnyCare", "Insert error: ${e.message}")
                            // Insert fail হলেও code টা use করবো
                        }
                    }

                    // FIX: Copy to clipboard (যাতে কপি হয়ে যায়)
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("SunnyCare OTP", code)
                    clipboard.setPrimaryClip(clip)

                    // Show OTP - In-app notification
                    Toast.makeText(this@LoginActivity, "🔐 OTP: $code (কপি হয়েছে)", Toast.LENGTH_LONG).show()

                    // Check if profile exists - SAFE way
                    var profileExists = false
                    try {
                        val profileResult = SupabaseClient.client.from("profiles")
                            .select(columns = Columns.list("phone")) {
                                filter { eq("phone", phone) }
                            }.decodeList<JsonObject>()
                        profileExists = profileResult.isNotEmpty()
                    } catch (e: Exception) {
                        Log.e("SunnyCare", "Profile check error: ${e.message}")
                        profileExists = false
                    }

                    val intent = Intent(this@LoginActivity, OtpVerifyActivity::class.java).apply {
                        putExtra("phone", phone)
                        putExtra("code", code)
                        putExtra("isLogin", profileExists)
                    }
                    startActivity(intent)

                } catch (e: Exception) {
                    Log.e("SunnyCare", "Main crash: ${e.message}", e)
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    // FIX: Always re-enable button, else app looks frozen
                    btn.isEnabled = true
                    btn.alpha = 1f
                    btn.text = "OTP পাঠান  →"
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

// OtpVerifyActivity - Also fixed with copy-paste support
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
        val sub = TextView(this).apply { text = "$phone নাম্বারে কোড পাঠানো হয়েছে\n(Demo কোড: $realCode - কপি হয়েছে)"; textSize = 13f; setTextColor(Color.GRAY) }

        // Banner showing OTP with copy button programmatically
        val otpBannerBg = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#E6F4F2")); setStroke(dp(1), Color.parseColor("#0F6C61")) }
        val otpBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = otpBannerBg
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
        }
        val otpText = TextView(this).apply { text = "🔐 $realCode"; textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#0F6C61")); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val copyBtn = TextView(this).apply {
            text = "Copy"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(Color.parseColor("#0F6C61")) }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            isClickable = true
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OTP", realCode))
                Toast.makeText(this@OtpVerifyActivity, "কপি হয়েছে!", Toast.LENGTH_SHORT).show()
            }
        }
        otpBanner.addView(otpText)
        otpBanner.addView(copyBtn)

        val otpInput = EditText(this).apply {
            hint = "6 ডিজিট কোড paste করুন"
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 20f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#F3F4F6")); setStroke(dp(1), Color.parseColor("#0F6C61")) }
            setPadding(dp(16), dp(18), dp(16), dp(18))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) }
            // Auto-paste if clipboard has 6 digit code
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    try {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (clipText != null && clipText.length == 6 && clipText.all { it.isDigit() }) {
                            setText(clipText)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        val btn = TextView(this).apply {
            text = "যাচাই করুন"
            textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#0F6C61")) }
            setPadding(0, dp(18), 0, dp(18))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) }
            isClickable = true
            setOnClickListener {
                if (otpInput.text.toString().trim() == realCode) {
                    if (isLogin) {
                        SupabaseClient.saveLogin(this@OtpVerifyActivity, phone)
                        startActivity(Intent(this@OtpVerifyActivity, HomeActivity::class.java))
                        finishAffinity()
                    } else {
                        startActivity(Intent(this@OtpVerifyActivity, SignupActivity::class.java).apply { putExtra("phone", phone) })
                    }
                } else {
                    Toast.makeText(this@OtpVerifyActivity, "ভুল OTP, সঠিক কোড: $realCode", Toast.LENGTH_SHORT).show()
                }
            }
        }

        root.addView(title)
        root.addView(sub)
        root.addView(otpBanner)
        root.addView(otpInput)
        root.addView(btn)
        setContentView(root)
    }
    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
