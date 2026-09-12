package com.konasl.nagad

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val phone = intent.getStringExtra("phone") ?: ""

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FDFCF8"))
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }

        val icon = MainActivity.SunnyCareIconView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(16) }
        }

        val title = TextView(this).apply {
            text = "রোগীর তথ্য দিন"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F6C61"))
        }
        val sub = TextView(this).apply {
            text = "মোবাইল: $phone"
            textSize = 12f
            setTextColor(Color.GRAY)
        }

        // Helper to create input field programmatically
        fun createInput(hint: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText {
            val bg = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.parseColor("#E5E7EB"))
            }
            return EditText(this).apply {
                this.hint = hint
                this.inputType = inputType
                background = bg
                setPadding(dp(14), dp(16), dp(14), dp(16))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            }
        }

        val nameInput = createInput("পূর্ণ নাম (বাংলা/English)")
        val ageInput = createInput("বয়স", InputType.TYPE_CLASS_NUMBER)
        val weightInput = createInput("ওজন (kg)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val addressInput = createInput("ঠিকানা - গ্রাম/থানা")

        // Gender chips programmatically
        val genderLabel = TextView(this).apply { text = "লিঙ্গ"; textSize = 12f; setTextColor(Color.GRAY); setPadding(0, dp(16), 0, dp(4)) }
        val genderLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        var selectedGender = "পুরুষ"
        val genders = listOf("পুরুষ", "মহিলা", "অন্যান্য")
        val genderViews = mutableListOf<TextView>()
        genders.forEach { g ->
            val chipBg = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(if (g == selectedGender) Color.parseColor("#0F6C61") else Color.WHITE); setStroke(dp(1), Color.parseColor("#0F6C61")) }
            val tv = TextView(this).apply {
                text = g
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (g == selectedGender) Color.WHITE else Color.parseColor("#0F6C61"))
                background = chipBg
                setPadding(dp(16), dp(8), dp(16), dp(8))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) }
                isClickable = true
                setOnClickListener {
                    selectedGender = g
                    genderViews.forEach {
                        val isSel = it.text == g
                        it.background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(if (isSel) Color.parseColor("#0F6C61") else Color.WHITE); setStroke(dp(1), Color.parseColor("#0F6C61")) }
                        it.setTextColor(if (isSel) Color.WHITE else Color.parseColor("#0F6C61"))
                    }
                }
            }
            genderViews.add(tv)
            genderLayout.addView(tv)
        }

        // Chronic diseases chips
        val diseaseLabel = TextView(this).apply { text = "পুরাতন রোগ (থাকলে সিলেক্ট করুন)"; textSize = 12f; setTextColor(Color.GRAY); setPadding(0, dp(16), 0, dp(4)) }
        val diseaseLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2) }
        val diseases = listOf("ডায়াবেটিস", "উচ্চ রক্তচাপ", "এলার্জি", "হাঁপানি")
        val selectedDiseases = mutableSetOf<String>()
        diseases.forEach { d ->
            val tv = TextView(this).apply {
                text = d
                textSize = 11f
                setTextColor(Color.parseColor("#6B7280"))
                background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.parseColor("#F3F4F6")) }
                setPadding(dp(12), dp(6), dp(12), dp(6))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) }
                isClickable = true
                setOnClickListener {
                    if (selectedDiseases.contains(d)) {
                        selectedDiseases.remove(d)
                        setBackgroundColor(Color.parseColor("#F3F4F6"))
                    } else {
                        selectedDiseases.add(d)
                        background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.parseColor("#FEF3C7")); setStroke(dp(1), Color.parseColor("#F59E0B")) }
                    }
                }
            }
            diseaseLayout.addView(tv)
        }

        val btn = TextView(this).apply {
            text = "রেজিস্ট্রেশন সম্পন্ন করুন  ✓"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); colors = intArrayOf(Color.parseColor("#0F6C61"), Color.parseColor("#0A4A42")); orientation = GradientDrawable.Orientation.LEFT_RIGHT }
            setPadding(0, dp(18), 0, dp(18))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) }
            isClickable = true
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) { Toast.makeText(this@SignupActivity, "নাম দিন", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

                lifecycleScope.launch {
                    try {
                        SupabaseClient.client.from("profiles").insert(
                            mapOf(
                                "phone" to phone,
                                "name" to name,
                                "age" to (ageInput.text.toString().toIntOrNull() ?: 0),
                                "gender" to selectedGender,
                                "weight" to (weightInput.text.toString().toFloatOrNull() ?: 0f),
                                "address" to addressInput.text.toString(),
                                "chronic_diseases" to selectedDiseases.toList()
                            )
                        )
                        SupabaseClient.saveLogin(this@SignupActivity, phone, name)
                        startActivity(Intent(this@SignupActivity, HomeActivity::class.java))
                        finishAffinity()
                    } catch (e: Exception) {
                        Toast.makeText(this@SignupActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        root.addView(icon)
        root.addView(title)
        root.addView(sub)
        root.addView(nameInput)
        root.addView(ageInput)
        root.addView(genderLabel)
        root.addView(genderLayout)
        root.addView(weightInput)
        root.addView(addressInput)
        root.addView(diseaseLabel)
        root.addView(diseaseLayout)
        root.addView(btn)

        scroll.addView(root)
        setContentView(scroll)
    }
    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
