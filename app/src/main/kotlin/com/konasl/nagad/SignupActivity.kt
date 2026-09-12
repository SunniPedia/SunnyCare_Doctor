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
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorFieldBg = Color.parseColor("#F1F5F4")
    private val colorTextMuted = Color.parseColor("#6B7280")

    private lateinit var nameInput: EditText
    private lateinit var ageInput: EditText
    private lateinit var addressInput: EditText
    private lateinit var emergencyInput: EditText
    private lateinit var historyInput: EditText
    private lateinit var genderGroup: RadioGroup
    private lateinit var bloodGroupSpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var submitBtn: Button
    private lateinit var progress: ProgressBar

    private var phone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        phone = intent.getStringExtra("phone") ?: ""

        // কীবোর্ড ওপেন হলে স্ক্রিন রিসাইজ হয়ে ফর্মটা স্ক্রল-এবল থাকবে, কোনো ফিল্ড কীবোর্ডের নিচে চাপা পড়বে না
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(colorPrimary, colorPrimaryDark))
        }

        val scroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
            clipToPadding = false
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(40))
        }

        val icon = SignupIconView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        val title = text("প্রোফাইল তৈরি করুন", 22f, Typeface.BOLD, Color.WHITE, Gravity.CENTER)
        val subtitle = text("চিকিৎসা সেবা পেতে আপনার তথ্য দিন", 13f, Typeface.NORMAL, Color.argb(220, 255, 255, 255), Gravity.CENTER)
        val phoneBadge = text("যাচাইকৃত নাম্বার: $phone", 12f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
            background = roundedBg(Color.argb(45, 255, 255, 255), 30f)
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.WHITE, 24f)
            setPadding(dp(22), dp(24), dp(22), dp(24))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(24)
            }
        }

        nameInput = fieldInput("পূর্ণ নাম *", InputType.TYPE_CLASS_TEXT)
        ageInput = fieldInput("বয়স", InputType.TYPE_CLASS_NUMBER)

        val genderLabel = label("লিঙ্গ")
        genderGroup = RadioGroup(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(RadioButton(this@SignupActivity).apply { text = "পুরুষ"; id = View.generateViewId() })
            addView(RadioButton(this@SignupActivity).apply { text = "মহিলা"; id = View.generateViewId(); (layoutParams as? LinearLayout.LayoutParams)?.leftMargin = dp(20) })
            addView(RadioButton(this@SignupActivity).apply { text = "অন্যান্য"; id = View.generateViewId() })
            (getChildAt(0).layoutParams as LinearLayout.LayoutParams).rightMargin = dp(20)
            (getChildAt(1).layoutParams as LinearLayout.LayoutParams).rightMargin = dp(20)
            check(getChildAt(0).id)
        }

        val bloodLabel = label("রক্তের গ্রুপ")
        bloodGroupSpinner = Spinner(this).apply {
            val groups = arrayOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "জানা নেই")
            adapter = ArrayAdapter(this@SignupActivity, android.R.layout.simple_spinner_dropdown_item, groups)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
            background = roundedBg(colorFieldBg, 14f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        addressInput = fieldInput("ঠিকানা", InputType.TYPE_CLASS_TEXT, multiLine = true)
        emergencyInput = fieldInput("জরুরি যোগাযোগ নাম্বার", InputType.TYPE_CLASS_PHONE)
        historyInput = fieldInput("পূর্ববর্তী রোগ/অ্যালার্জি (যদি থাকে)", InputType.TYPE_CLASS_TEXT, multiLine = true)

        submitBtn = Button(this).apply {
            text = "প্রোফাইল সাবমিট করুন"
            setTextColor(Color.WHITE)
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            textSize = 15f
            background = roundedBg(colorPrimary, 14f)
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18)
            }
            setOnClickListener { onSubmit() }
        }

        progress = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12)
            }
        }

        statusText = text("", 12.5f, Typeface.NORMAL, Color.parseColor("#D32F2F"), Gravity.CENTER).apply {
            setPadding(0, dp(10), 0, 0)
        }

        card.addView(label("পূর্ণ নাম *"))
        card.addView(nameInput)
        card.addView(space(dp(14)))
        card.addView(label("বয়স"))
        card.addView(ageInput)
        card.addView(space(dp(14)))
        card.addView(genderLabel)
        card.addView(genderGroup)
        card.addView(space(dp(14)))
        card.addView(bloodLabel)
        card.addView(bloodGroupSpinner)
        card.addView(space(dp(14)))
        card.addView(label("ঠিকানা"))
        card.addView(addressInput)
        card.addView(space(dp(14)))
        card.addView(label("জরুরি যোগাযোগ নাম্বার"))
        card.addView(emergencyInput)
        card.addView(space(dp(14)))
        card.addView(label("পূর্ববর্তী রোগ/অ্যালার্জি"))
        card.addView(historyInput)
        card.addView(submitBtn)
        card.addView(progress)
        card.addView(statusText)
        card.addView(space(dp(40))) // কীবোর্ড খোলা অবস্থায় সাবমিট বাটন যেন নিচে চাপা না পড়ে

        container.addView(icon)
        container.addView(space(dp(12)))
        container.addView(title)
        container.addView(subtitle)
        container.addView(space(dp(14)))
        container.addView(phoneBadge)
        container.addView(card)

        scroll.addView(container)
        root.addView(scroll)
        setContentView(root)
    }

    private fun onSubmit() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            statusText.text = "নাম আবশ্যক"
            return
        }
        val age = ageInput.text.toString().trim().toIntOrNull()
        val gender = when (genderGroup.indexOfChild(findViewById(genderGroup.checkedRadioButtonId))) {
            0 -> "পুরুষ"; 1 -> "মহিলা"; else -> "অন্যান্য"
        }
        val bloodGroup = bloodGroupSpinner.selectedItem?.toString() ?: ""
        val address = addressInput.text.toString().trim()
        val emergency = emergencyInput.text.toString().trim()
        val history = historyInput.text.toString().trim()

        submitBtn.isEnabled = false
        progress.visibility = View.VISIBLE
        statusText.text = ""

        lifecycleScope.launch {
            val result = SupabaseClient.registerPatient(
                SupabaseClient.NewPatient(
                    phone = phone,
                    fullName = name,
                    age = age,
                    gender = gender,
                    bloodGroup = bloodGroup,
                    address = address,
                    emergencyContact = emergency,
                    medicalHistory = history
                )
            )
            progress.visibility = View.GONE
            submitBtn.isEnabled = true
            result.onSuccess { patient ->
                SupabaseClient.saveSession(this@SignupActivity, patient.getString("id"), phone, name)
                startActivity(Intent(this@SignupActivity, HomeActivity::class.java))
                finish()
            }.onFailure {
                statusText.text = it.message ?: "প্রোফাইল সেভ করতে সমস্যা হয়েছে"
            }
        }
    }

    // ------------------------------------------------------------------
    private fun fieldInput(hintText: String, type: Int, multiLine: Boolean = false): EditText = EditText(this).apply {
        hint = hintText
        inputType = if (multiLine) type or InputType.TYPE_TEXT_FLAG_MULTI_LINE else type
        if (multiLine) minLines = 2
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = roundedBg(colorFieldBg, 14f)
        textSize = 14.5f
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        }
    }

    private fun label(t: String): TextView = text(t, 12.5f, Typeface.BOLD, colorTextMuted, Gravity.START)

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

    class SignupIconView(context: android.content.Context) : View(context) {
        private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val paintOrange = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B"); style = Paint.Style.FILL }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawCircle(w / 2, h / 2, w / 2, paintWhite)
            canvas.drawCircle(w / 2, h * 0.38f, w * 0.16f, paintOrange)
            val path = android.graphics.Path().apply {
                moveTo(w * 0.22f, h * 0.82f)
                quadTo(w * 0.5f, h * 0.55f, w * 0.78f, h * 0.82f)
                lineTo(w * 0.22f, h * 0.82f)
                close()
            }
            canvas.drawPath(path, paintOrange)
        }
    }
}
