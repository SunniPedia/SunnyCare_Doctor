package com.konasl.nagad

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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

    private lateinit var rootView: FrameLayout
    private lateinit var scrollView: NestedScrollView
    private lateinit var containerView: LinearLayout

    // কীবোর্ড শো/হাইড ট্র্যাক করার জন্য এবং কনটেইনারের স্বাভাবিক (কীবোর্ড ছাড়া) বটম প্যাডিং মনে রাখার জন্য
    private var isKeyboardShowing = false
    private var normalContainerBottomPadding = 0

    private var phone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        phone = intent.getStringExtra("phone") ?: ""

        // কীবোর্ড ওপেন হলে স্ক্রিন রিসাইজ হয়ে ফর্মটা স্ক্রল-এবল থাকবে, কোনো ফিল্ড কীবোর্ডের নিচে চাপা পড়বে না।
        // SOFT_INPUT_STATE_HIDDEN দিয়ে নিশ্চিত করা হচ্ছে Activity ওপেন হওয়ার সাথে সাথেই যেন
        // প্রথম EditText অটো-ফোকাস হয়ে কীবোর্ড নিজে থেকে পপ-আপ না করে।
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(colorPrimary, colorPrimaryDark))
            // রুট ভিউকে ফোকাসেবল করা হলো যাতে এটিই ডিফল্ট ফোকাস নেয়, কোনো EditText না।
            // এতে Activity চালু হওয়ার মুহূর্তে অনাকাঙ্ক্ষিতভাবে কীবোর্ড উঠবে না।
            isFocusableInTouchMode = true
            isFocusable = true
        }
        rootView = root

        val scroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
            clipToPadding = false
            // ফর্মের যেকোনো ফাঁকা জায়গায় ট্যাপ করলে কীবোর্ড বন্ধ হয়ে যাবে এবং ফোকাস সরে যাবে,
            // যাতে কীবোর্ড অযথা খোলা থেকে বিরত থাকে।
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    hideKeyboard()
                    root.requestFocus()
                }
                false
            }
        }
        scrollView = scroll

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(40))
        }
        containerView = container
        normalContainerBottomPadding = dp(40)

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

        nameInput = fieldInput("পূর্ণ নাম *", InputType.TYPE_CLASS_TEXT, imeAction = EditorInfo.IME_ACTION_NEXT)
        ageInput = fieldInput("বয়স", InputType.TYPE_CLASS_NUMBER, imeAction = EditorInfo.IME_ACTION_NEXT)

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

        addressInput = fieldInput("ঠিকানা", InputType.TYPE_CLASS_TEXT, multiLine = true, imeAction = EditorInfo.IME_ACTION_NEXT)
        emergencyInput = fieldInput("জরুরি যোগাযোগ নাম্বার", InputType.TYPE_CLASS_PHONE, imeAction = EditorInfo.IME_ACTION_NEXT)
        historyInput = fieldInput("পূর্ববর্তী রোগ/অ্যালার্জি (যদি থাকে)", InputType.TYPE_CLASS_TEXT, multiLine = true, imeAction = EditorInfo.IME_ACTION_DONE)

        // এন্টার/নেক্সট চাপলে ফোকাস পরের ফিল্ডে চলে যাবে, শেষ ফিল্ডে "Done" চাপলে কীবোর্ড বন্ধ হয়ে যাবে
        nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) { ageInput.requestFocus(); true } else false
        }
        ageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) { addressInput.requestFocus(); true } else false
        }
        addressInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) { emergencyInput.requestFocus(); true } else false
        }
        emergencyInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) { historyInput.requestFocus(); true } else false
        }
        historyInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { hideKeyboard(); historyInput.clearFocus(); true } else false
        }

        // ফোকাস পাওয়ার সাথে সাথে সেই ফিল্ডটি কীবোর্ডের ওপরে স্বয়ংক্রিয়ভাবে স্ক্রল করে দৃশ্যমান করা হচ্ছে,
        // যাতে ইউজারকে নিজে থেকে স্ক্রল করে ফিল্ড খুঁজতে না হয়
        val scrollToViewOnFocus = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // কীবোর্ড আসার অ্যানিমেশন/রিসাইজ শেষ হওয়ার জন্য সামান্য অপেক্ষা করে তারপর স্ক্রল করা হচ্ছে,
                // নাহলে রিসাইজ হওয়ার আগেই ভুল পজিশনে স্ক্রল হয়ে যেতে পারে
                v.postDelayed({ scrollFieldIntoView(v) }, 120)
            }
        }
        nameInput.onFocusChangeListener = scrollToViewOnFocus
        ageInput.onFocusChangeListener = scrollToViewOnFocus
        addressInput.onFocusChangeListener = scrollToViewOnFocus
        emergencyInput.onFocusChangeListener = scrollToViewOnFocus
        historyInput.onFocusChangeListener = scrollToViewOnFocus

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
            setOnClickListener {
                hideKeyboard()
                onSubmit()
            }
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

        // Activity তৈরি হওয়ার সময় রুট ভিউ ফোকাস নিয়ে নেয়, ফলে কোনো EditText অটো-ফোকাসড না হয়ে
        // কীবোর্ড নিজে থেকে খুলে যায় না — ইউজার নিজে ট্যাপ করলে তবেই কীবোর্ড আসবে
        root.requestFocus()

        // কীবোর্ড ওপেন/ক্লোজ হওয়া ডিটেক্ট করার জন্য গ্লোবাল লেআউট লিসেনার।
        // কীবোর্ড ওপেন থাকলে কনটেইনারের নিচে কীবোর্ডের উচ্চতার সমান অতিরিক্ত জায়গা যোগ করা হয়,
        // যাতে "জরুরি যোগাযোগ নাম্বার"-এর পরের ফিল্ড, সাবমিট বাটন — সবকিছু স্ক্রল করে কীবোর্ডের
        // উপরে সম্পূর্ণ দৃশ্যমান জায়গায় আনা যায়, কোনো কিছু কীবোর্ডের নিচে লুকিয়ে না থাকে।
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val visibleFrame = Rect()
            root.getWindowVisibleDisplayFrame(visibleFrame)
            val screenHeight = root.rootView.height
            val keypadHeight = screenHeight - visibleFrame.bottom

            val keyboardVisibleNow = keypadHeight > screenHeight * 0.15

            if (keyboardVisibleNow) {
                isKeyboardShowing = true
                // কনটেইনারের বটম প্যাডিং = কীবোর্ডের উচ্চতা + কিছুটা বাড়তি জায়গা,
                // যাতে সবচেয়ে নিচের ফিল্ড/বাটনও কীবোর্ডের উপরে স্ক্রল করে আনা যায়
                containerView.setPadding(
                    containerView.paddingLeft,
                    containerView.paddingTop,
                    containerView.paddingRight,
                    keypadHeight + dp(24)
                )
                // যে ফিল্ডে বর্তমানে ফোকাস আছে সেটিকে আবার নতুন প্যাডিং অনুযায়ী দৃশ্যমান জায়গায় স্ক্রল করা হচ্ছে
                currentFocus?.let { focused ->
                    scroll.post { scrollFieldIntoView(focused) }
                }
            } else if (isKeyboardShowing) {
                isKeyboardShowing = false
                containerView.setPadding(
                    containerView.paddingLeft,
                    containerView.paddingTop,
                    containerView.paddingRight,
                    normalContainerBottomPadding
                )
            }
        }
    }

    // ফোকাসড ভিউটির scroll-কনটেন্টের মধ্যে absolute Y পজিশন বের করে সেই পর্যন্ত স্ক্রল করা হয়,
    // যাতে ভিউটি (এবং তার আশেপাশের কনটেন্ট, যেমন সাবমিট বাটন) কীবোর্ডের উপরে দৃশ্যমান জায়গায় থাকে
    private fun scrollFieldIntoView(v: View) {
        var offsetY = 0
        var current: View = v
        while (current !== scrollView) {
            offsetY += current.top
            val parent = current.parent as? View ?: return
            current = parent
        }
        val extraPadding = dp(16)
        scrollView.smoothScrollTo(0, (offsetY - extraPadding).coerceAtLeast(0))
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let { imm?.hideSoftInputFromWindow(it.windowToken, 0) }
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
    private fun fieldInput(
        hintText: String,
        type: Int,
        multiLine: Boolean = false,
        imeAction: Int = EditorInfo.IME_ACTION_NEXT
    ): EditText = EditText(this).apply {
        hint = hintText
        inputType = if (multiLine) type or InputType.TYPE_TEXT_FLAG_MULTI_LINE else type
        if (multiLine) minLines = 2
        // মাল্টি-লাইন ফিল্ডে "Enter"-কে newline হিসেবে ব্যবহার না করে কাস্টম ime action ব্যবহার করা হচ্ছে,
        // যাতে ফোকাস-চেইন ঠিকভাবে কাজ করে
        imeOptions = imeAction or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        if (multiLine) setSingleLine(false) else setSingleLine(true)
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
