package com.konasl.nagad

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * ---------------------------------------------------------------------
 * SignupActivity — আপলোড করা "SunnyCare" প্রোটোটাইপ স্ক্রিনশটের সাথে
 * হুবহু মিলিয়ে রিস্কিন করা হয়েছে: প্রতিটি ফিল্ড এখন আলাদা আলাদা
 * সাদা বর্ডারযুক্ত রাউন্ডেড কার্ডে, উপরে ছোট মিউটেড লেবেল এবং নিচে
 * বড় ভ্যালু/হিন্ট টেক্সট — ঠিক ছবির মতো। লিঙ্গ একটি ৩-অপশন সেগমেন্টেড
 * কন্ট্রোল, রক্তের গ্রুপ ও জরুরি যোগাযোগ পাশাপাশি দুইটি কার্ডে।
 *
 * কালার প্যালেট অপরিবর্তিত রাখা হয়েছে:
 *   • Primary: #0F6C61 (teal) / Dark: #0A2A26
 *   • Screen bg: #F8FFFE (mint) | Card bg: #FFFFFF | Border: #E6EFED
 *
 * সমস্ত আগের ফিচার/লজিক অপরিবর্তিত: ফর্ম ফিল্ড, কীবোর্ড হ্যান্ডলিং,
 * ফোকাস-চেইন, ব্লাড গ্রুপ ডায়ালগ, ভ্যালিডেশন, SupabaseClient কল, সেশন সেভ।
 * ---------------------------------------------------------------------
 */
class SignupActivity : AppCompatActivity() {

    // ---------------------------------------------------------------
    // Palette — অপরিবর্তিত
    // ---------------------------------------------------------------
    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#0A2A26")
    private val colorScreenBg = Color.parseColor("#F8FFFE")
    private val colorCardBg = Color.WHITE
    private val colorBorder = Color.parseColor("#E6EFED")
    private val colorTextMuted = Color.parseColor("#6B7C7A")
    private val colorDark = Color.parseColor("#0A2A26")
    private val colorHintMuted = Color.parseColor("#9AA8A5")
    private val colorError = Color.parseColor("#D32F2F")
    private val colorChipBg = Color.parseColor("#EAF6F3")

    private var appFont: Typeface? = null

    private lateinit var nameInput: EditText
    private lateinit var ageInput: EditText
    private lateinit var addressInput: EditText
    private lateinit var emergencyInput: EditText
    private lateinit var historyInput: EditText

    private var selectedGenderIndex: Int = 0
    private val genderOptions = arrayOf("পুরুষ", "মহিলা", "অন্যান্য")
    private var genderChips: MutableList<TextView> = mutableListOf()

    private lateinit var bloodGroupField: TextView
    private lateinit var bloodGroupRow: LinearLayout
    private var selectedBloodGroup: String = "B+"
    private val bloodGroups = arrayOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "জানা নেই")

    private lateinit var statusText: TextView
    private lateinit var submitBtn: Button
    private lateinit var progress: ProgressBar

    private lateinit var rootView: FrameLayout
    private lateinit var scrollView: NestedScrollView
    private lateinit var containerView: LinearLayout

    private var isKeyboardShowing = false
    private var normalContainerBottomPadding = 0

    private var phone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        phone = intent.getStringExtra("phone") ?: ""

        appFont = try {
            Typeface.createFromAsset(assets, "fonts/SolaimanLipi.ttf")
        } catch (e: Exception) {
            null
        }

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(colorScreenBg)
            isFocusableInTouchMode = true
            isFocusable = true
        }
        rootView = root

        val scroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
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
            setPadding(dp(18), dp(22), dp(18), dp(20))
        }
        containerView = container
        normalContainerBottomPadding = dp(20)

        // --------------------------------------------------------
        // হেডার — ছবির মতো প্লেইন টাইটেল + সাবটাইটেল (কোনো গ্র্যাডিয়েন্ট হিরো নেই)
        // --------------------------------------------------------
        val headerTitle = text("নতুন রোগী নিবন্ধন", 21f, Typeface.BOLD, colorPrimaryDark, Gravity.START)
        val headerSubtitle = text(
            "অনুগ্রহ করে সঠিক তথ্য দিয়ে ফর্ম পূরণ করুন",
            12.5f, Typeface.NORMAL, colorTextMuted, Gravity.START
        ).apply { setPadding(0, dp(6), 0, 0) }

        val verifiedChip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(30).toFloat()
                setColor(colorChipBg)
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
            if (phone.isNotEmpty()) {
                addView(text("যাচাইকৃত নাম্বার: $phone", 11f, Typeface.BOLD, colorPrimary, Gravity.CENTER))
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        // --------------------------------------------------------
        // ফিল্ড কার্ডসমূহ — ছবির প্রতিটি ফিল্ডের মতো আলাদা বর্ডারযুক্ত কার্ড
        // --------------------------------------------------------
        val (nameCard, nameEdit) = fieldCard(
            "পূর্ণ নাম / FULL NAME *", "মোঃ রাহাত হোসেন", InputType.TYPE_CLASS_TEXT
        )
        nameInput = nameEdit

        val (ageCard, ageEdit) = fieldCard(
            "বয়স / AGE", "৩২", InputType.TYPE_CLASS_NUMBER
        )
        ageInput = ageEdit

        val genderCard = buildGenderCard()

        val bloodCard = buildBloodGroupCard()
        val (emergencyCard, emergencyEdit) = fieldCard(
            "জরুরি যোগাযোগ", "017XXXXXXXXX", InputType.TYPE_CLASS_PHONE
        )
        emergencyInput = emergencyEdit
        // পাশাপাশি রো-তে বসানোর জন্য টপ-মার্জিন রিসেট করে হাফ-উইথ লেআউট দেওয়া হচ্ছে
        (bloodCard.layoutParams as LinearLayout.LayoutParams).apply { topMargin = 0; marginEnd = dp(12) }
        (emergencyCard.layoutParams as LinearLayout.LayoutParams).apply { topMargin = 0 }
        val bloodEmergencyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
            addView(bloodCard)
            addView(emergencyCard)
        }

        val (addressCardView, addressEdit) = fieldCard(
            "ঠিকানা / ADDRESS", "সিলেট, আশ্বরখানা, ...", InputType.TYPE_CLASS_TEXT, multiLine = true
        )
        addressInput = addressEdit

        val (historyCardView, historyEdit) = fieldCard(
            "চিকিৎসা ইতিহাস (ঐচ্ছিক)", "ডায়াবেটিস, উচ্চ রক্তচাপ, এলার্জি ইত্যাদি...",
            InputType.TYPE_CLASS_TEXT, multiLine = true, imeAction = EditorInfo.IME_ACTION_DONE
        )
        historyInput = historyEdit

        // এন্টার/নেক্সট চাপলে ফোকাস পরের ফিল্ডে চলে যাবে
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

        submitBtn = premiumButton("প্রোফাইল সাবমিট করুন") {
            hideKeyboard()
            onSubmit()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20)
            }
        }

        progress = ProgressBar(this).apply {
            visibility = View.GONE
            indeterminateTintList = ColorStateList.valueOf(colorPrimary)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12)
            }
        }

        statusText = text("", 12.5f, Typeface.NORMAL, colorError, Gravity.CENTER).apply {
            setPadding(0, dp(10), 0, 0)
        }

        container.addView(headerTitle)
        container.addView(headerSubtitle)
        container.addView(verifiedChip)
        container.addView(nameCard)
        container.addView(ageCard)
        container.addView(genderCard)
        container.addView(bloodEmergencyRow)
        container.addView(addressCardView)
        container.addView(historyCardView)
        container.addView(submitBtn)
        container.addView(progress)
        container.addView(statusText)
        container.addView(space(dp(30)))

        scroll.addView(container)
        root.addView(scroll)
        setContentView(root)

        applyFontRecursively(root)
        root.requestFocus()

        root.viewTreeObserver.addOnGlobalLayoutListener {
            val visibleFrame = Rect()
            root.getWindowVisibleDisplayFrame(visibleFrame)
            val screenHeight = root.rootView.height
            val keypadHeight = screenHeight - visibleFrame.bottom
            val keyboardVisibleNow = keypadHeight > screenHeight * 0.15

            if (keyboardVisibleNow) {
                isKeyboardShowing = true
                containerView.setPadding(
                    containerView.paddingLeft,
                    containerView.paddingTop,
                    containerView.paddingRight,
                    keypadHeight + dp(24)
                )
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

    // ------------------------------------------------------------------
    // ছবির মতো: প্রতিটি ফিল্ড = সাদা কার্ড, বর্ডার #E6EFED, ভেতরে ছোট মিউটেড
    // লেবেল উপরে এবং বড় ভ্যালু/হিন্ট টেক্সট নিচে
    // ------------------------------------------------------------------
    private fun fieldCard(
        labelText: String,
        hintText: String,
        inputType: Int,
        multiLine: Boolean = false,
        imeAction: Int = EditorInfo.IME_ACTION_NEXT
    ): Pair<LinearLayout, EditText> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardStroke(colorBorder)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
        }
        val label = text(labelText, 11.5f, Typeface.BOLD, colorTextMuted, Gravity.START)
        val edit = EditText(this).apply {
            hint = hintText
            setHintTextColor(colorHintMuted)
            this.inputType = if (multiLine) inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE else inputType
            if (multiLine) { minLines = 2; setSingleLine(false) } else setSingleLine(true)
            imeOptions = imeAction or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, dp(4), 0, 0)
            textSize = 14.5f
            setTextColor(colorDark)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        edit.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            card.background = cardStroke(if (hasFocus) colorPrimary else colorBorder)
            if (hasFocus) v.postDelayed({ scrollFieldIntoView(v) }, 120)
        }
        card.addView(label)
        card.addView(edit)
        return Pair(card, edit)
    }

    /** ছবির মতো ৩-অপশন সেগমেন্টেড লিঙ্গ নির্বাচন কার্ড (পুরুষ/মহিলা/অন্যান্য) */
    private fun buildGenderCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardStroke(colorBorder)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
        }
        card.addView(text("লিঙ্গ / GENDER", 11.5f, Typeface.BOLD, colorTextMuted, Gravity.START))
        card.addView(space(dp(10)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        genderChips = mutableListOf()
        genderOptions.forEachIndexed { idx, label ->
            val chip = text(label, 13.5f, Typeface.BOLD, colorDark, Gravity.CENTER).apply {
                setPadding(dp(6), dp(11), dp(6), dp(11))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx > 0) marginStart = dp(8)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedGenderIndex = idx
                    updateGenderChips()
                }
            }
            genderChips.add(chip)
            row.addView(chip)
        }
        card.addView(row)
        updateGenderChips()
        return card
    }

    private fun updateGenderChips() {
        genderChips.forEachIndexed { idx, chip ->
            if (idx == selectedGenderIndex) {
                chip.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(colorPrimaryDark)
                }
                chip.setTextColor(Color.WHITE)
            } else {
                chip.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.WHITE)
                    setStroke(dp(1), colorBorder)
                }
                chip.setTextColor(colorDark)
            }
        }
    }

    /** ছবির মতো "B+ (Positive)" ফরম্যাটে রক্তের গ্রুপ কার্ড, চেভরন সহ, ট্যাপ করলে ডায়ালগ খোলে */
    private fun buildBloodGroupCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardStroke(colorBorder)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                hideKeyboard()
                rootView.requestFocus()
                showBloodGroupDialog()
            }
        }
        card.addView(text("রক্তের গ্রুপ", 11.5f, Typeface.BOLD, colorTextMuted, Gravity.START))
        card.addView(space(dp(6)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bloodGroupField = text(bloodGroupDisplay(selectedBloodGroup), 14f, Typeface.BOLD, colorDark, Gravity.START).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(bloodGroupField)
        row.addView(text("›", 17f, Typeface.BOLD, colorTextMuted, Gravity.END))
        card.addView(row)
        bloodGroupRow = card
        return card
    }

    private fun bloodGroupDisplay(code: String): String = when (code) {
        "A+" -> "A+ (Positive)"
        "A-" -> "A- (Negative)"
        "B+" -> "B+ (Positive)"
        "B-" -> "B- (Negative)"
        "AB+" -> "AB+ (Positive)"
        "AB-" -> "AB- (Negative)"
        "O+" -> "O+ (Positive)"
        "O-" -> "O- (Negative)"
        else -> "জানা নেই"
    }

    /** কার্ডের বর্ডার/ব্যাকগ্রাউন্ড — সাদা + কনফিগারযোগ্য বর্ডার কালার (ফোকাস হাইলাইটের জন্য) */
    private fun cardStroke(borderColor: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        setColor(colorCardBg)
        setStroke(dp(1), borderColor)
    }

    private fun showBloodGroupDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dialogRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.WHITE, 20f)
            setPadding(dp(20), dp(20), dp(20), dp(10))
            elevation = dp(14).toFloat()
        }

        val dialogTitle = text("রক্তের গ্রুপ নির্বাচন করুন", 15.5f, Typeface.BOLD, colorPrimaryDark, Gravity.START).apply {
            setPadding(0, 0, 0, dp(14))
        }
        dialogRoot.addView(dialogTitle)

        bloodGroups.forEach { group ->
            val isSelected = group == selectedBloodGroup
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = if (isSelected)
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(14).toFloat()
                        setColor(colorChipBg)
                        setStroke(dp(1), colorPrimary)
                    }
                else roundedBg(colorScreenBg, 14f)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                }
            }
            val rowLabel = text(bloodGroupDisplay(group), 14.5f, if (isSelected) Typeface.BOLD else Typeface.NORMAL, if (isSelected) colorPrimary else Color.parseColor("#1F2937"), Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(rowLabel)
            if (isSelected) {
                row.addView(text("✓", 15f, Typeface.BOLD, colorPrimary, Gravity.END))
            }
            row.setOnClickListener {
                selectedBloodGroup = group
                bloodGroupField.text = bloodGroupDisplay(group)
                dialog.dismiss()
            }
            dialogRoot.addView(row)
        }

        val cancelText = text("বাতিল করুন", 14f, Typeface.BOLD, colorTextMuted, Gravity.CENTER).apply {
            setPadding(0, dp(16), 0, dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }
        dialogRoot.addView(cancelText)

        applyFontRecursively(dialogRoot)

        dialog.setContentView(dialogRoot)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            val params = attributes
            params.width = (resources.displayMetrics.widthPixels * 0.86).toInt()
            attributes = params
        }
        dialog.show()
    }

    private fun onSubmit() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            statusText.text = "নাম আবশ্যক"
            return
        }
        val age = ageInput.text.toString().trim().toIntOrNull()
        val gender = genderOptions[selectedGenderIndex]
        val bloodGroup = selectedBloodGroup
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

    /** প্রিমিয়াম সলিড বাটন (ছবির টিল-ডার্ক টোনের সাথে মিলিয়ে), প্রেস করলে হালকা স্কেল-অ্যানিমেশন */
    private fun premiumButton(labelText: String, onClick: () -> Unit): Button = Button(this).apply {
        text = labelText
        setTextColor(Color.WHITE)
        isAllCaps = false
        setTypeface(null, Typeface.BOLD)
        textSize = 15f
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(colorPrimaryDark)
        }
        elevation = dp(2).toFloat()
        setPadding(0, dp(14), 0, dp(14))
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

    private fun text(t: String, sizeSp: Float, style: Int, color: Int, gravity: Int): TextView = TextView(this).apply {
        text = t; textSize = sizeSp; setTypeface(appFont, style); setTextColor(color); this.gravity = gravity
    }

    private fun space(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, h) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(color)
    }

    /** পুরো ভিউ-ট্রি জুড়ে SolaimanLipi ফন্ট প্রয়োগ করা হয় (স্টাইল/বোল্ড অক্ষুণ্ণ রেখে) */
    private fun applyFontRecursively(view: View) {
        val font = appFont ?: return
        when (view) {
            is TextView -> view.setTypeface(font, view.typeface?.style ?: Typeface.NORMAL)
            is ViewGroup -> for (i in 0 until view.childCount) applyFontRecursively(view.getChildAt(i))
        }
    }
}
