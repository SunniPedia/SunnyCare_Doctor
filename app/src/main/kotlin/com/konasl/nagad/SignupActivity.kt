package com.konasl.nagad

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryLight = Color.parseColor("#16897A")
    private val colorPrimaryDark = Color.parseColor("#0A2A26")
    private val colorScreenBg = Color.parseColor("#F8FFFE")
    private val colorCardBg = Color.WHITE
    private val colorBorder = Color.parseColor("#E6EFED")
    private val colorFieldBg = Color.parseColor("#F8FFFE")
    private val colorFieldBorderActive = colorPrimary
    private val colorTextMuted = Color.parseColor("#6B7C7A")
    private val colorDark = Color.parseColor("#0A2A26")
    private val colorError = Color.parseColor("#D32F2F")

    private var appFont: Typeface? = null

    private lateinit var avatarImage: ImageView
    private lateinit var avatarHint: TextView
    private var selectedImageUri: Uri? = null
    private var skipPhotoUpload = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            skipPhotoUpload = false
            avatarImage.setImageURI(uri)
            avatarHint.text = "ছবি পরিবর্তন করুন"
        }
    }

    private lateinit var nameInput: EditText
    private lateinit var ageInput: EditText
    private lateinit var addressInput: EditText
    private lateinit var emergencyInput: EditText
    private lateinit var historyInput: EditText
    private lateinit var genderGroup: RadioGroup
    private lateinit var bloodGroupField: TextView
    private lateinit var bloodGroupRow: LinearLayout
    private var selectedBloodGroup: String = ""
    private var isBloodGroupSelected = false
    private val bloodGroups = arrayOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "জানা নেই")
    private lateinit var statusText: TextView
    private lateinit var submitBtn: Button
    private lateinit var progress: ProgressBar

    private lateinit var rootView: FrameLayout
    private lateinit var scrollView: NestedScrollView
    private lateinit var containerView: LinearLayout

    private var normalContainerBottomPadding = 0
    private var phone: String = ""
    private var pin: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        phone = intent.getStringExtra("phone") ?: ""
        pin = intent.getStringExtra("pin") ?: ""

        // ফোনটা নরমালাইজ করে নিচ্ছি (BD হলে 11 ডিজিট, বিদেশি হলে + সহ)
        val phoneValidation = SupabaseClient.PhoneValidator.validate(phone)
        if (phoneValidation.isValid) phone = phoneValidation.normalizedPhone

        appFont = try {
            Typeface.createFromAsset(assets, "fonts/SolaimanLipi.ttf")
        } catch (e: Exception) { null }

        WindowCompat.setDecorFitsSystemWindows(window, false)
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
            setPadding(dp(18), dp(28), dp(18), dp(24))
        }
        containerView = container
        normalContainerBottomPadding = dp(24)

        val heroCard = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(colorPrimaryLight, colorPrimaryDark)).apply { cornerRadius = dp(24).toFloat() }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
                }
            }
            elevation = dp(10).toFloat()
        }
        val heroDecor = SignupDecorView(this).apply { layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        val heroContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(30), dp(24), dp(28))
        }
        val icon = SignupIconView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL } }
        val title = text("প্রোফাইল তৈরি করুন", 20f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply { letterSpacing = 0.01f }
        val subtitle = text("চিকিৎসা সেবা পেতে আপনার তথ্য দিন", 12.5f, Typeface.NORMAL, Color.argb(215, 255, 255, 255), Gravity.CENTER)
        val phoneBadge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(30).toFloat()
                setColor(Color.argb(40, 255, 255, 255))
                setStroke(dp(1), Color.argb(70, 255, 255, 255))
            }
            setPadding(dp(14), dp(7), dp(14), dp(7))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) }
            addView(checkBadgeView(this@SignupActivity).apply { layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(6) } })
            addView(text("যাচাইকৃত নাম্বার: $phone", 11.5f, Typeface.BOLD, Color.WHITE, Gravity.CENTER))
        }
        heroContent.addView(icon)
        heroContent.addView(space(dp(10)))
        heroContent.addView(title)
        heroContent.addView(space(dp(4)))
        heroContent.addView(subtitle)
        heroContent.addView(phoneBadge)
        heroCard.addView(heroDecor)
        heroCard.addView(heroContent)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
            setPadding(dp(20), dp(22), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) }
            elevation = dp(6).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
                }
            }
            clipToOutline = true
        }

        val avatarSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(18) }
        }
        val avatarFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(92), dp(92)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        avatarImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(92), dp(92))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E4F3F1"))
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            setImageDrawable(personPlaceholderDrawable())
            isClickable = true
            isFocusable = true
            setOnClickListener { pickImageLauncher.launch("image/*") }
        }
        val cameraBadge = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28)).apply { gravity = Gravity.BOTTOM or Gravity.END }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorPrimary)
                setStroke(dp(2), Color.WHITE)
            }
            isClickable = true
            isFocusable = true
            addView(cameraIconView(this@SignupActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dp(14), dp(14)).apply { gravity = Gravity.CENTER }
            })
            setOnClickListener { pickImageLauncher.launch("image/*") }
        }
        avatarFrame.addView(avatarImage)
        avatarFrame.addView(cameraBadge)
        avatarHint = text("প্রোফাইল ছবি যুক্ত করুন (ঐচ্ছিক)", 11.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER).apply {
            setPadding(0, dp(8), 0, 0)
        }
        avatarSection.addView(avatarFrame)
        avatarSection.addView(avatarHint)

        val formHeading = text("ব্যক্তিগত তথ্য", 13.5f, Typeface.BOLD, colorDark, Gravity.START).apply { setPadding(0, 0, 0, dp(4)) }
        nameInput = fieldInput("পূর্ণ নাম *", InputType.TYPE_CLASS_TEXT, imeAction = EditorInfo.IME_ACTION_NEXT)
        ageInput = fieldInput("বয়স *", InputType.TYPE_CLASS_NUMBER, imeAction = EditorInfo.IME_ACTION_NEXT)
        val genderLabel = label("লিঙ্গ *")
        genderGroup = RadioGroup(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(styledRadio("পুরুষ"))
            addView(styledRadio("মহিলা").apply { (layoutParams as? LinearLayout.LayoutParams)?.leftMargin = dp(20) })
            addView(styledRadio("অন্যান্য"))
            (getChildAt(0).layoutParams as LinearLayout.LayoutParams).rightMargin = dp(20)
            (getChildAt(1).layoutParams as LinearLayout.LayoutParams).rightMargin = dp(20)
            check(getChildAt(0).id)
        }
        val bloodLabel = label("রক্তের গ্রুপ *")
        isBloodGroupSelected = false
        selectedBloodGroup = ""
        bloodGroupField = text("রক্তের গ্রুপ নির্বাচন করুন", 14f, Typeface.NORMAL, Color.parseColor("#9AA8A5"), Gravity.START).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val bloodGroupArrow = text("▾", 15f, Typeface.BOLD, colorPrimary, Gravity.END)
        bloodGroupRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = fieldBg(false)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
            addView(bloodGroupField)
            addView(bloodGroupArrow)
            setOnClickListener {
                hideKeyboard()
                root.requestFocus()
                showBloodGroupDialog()
            }
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.background = fieldBg(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.background = if (v.background == fieldBgError()) fieldBgError() else fieldBg(false)
                    }
                }
                false
            }
        }
        addressInput = fieldInput("ঠিকানা *", InputType.TYPE_CLASS_TEXT, multiLine = true, imeAction = EditorInfo.IME_ACTION_NEXT)
        emergencyInput = fieldInput("জরুরি যোগাযোগ নাম্বার * (11 ডিজিট / বিদেশি +)", InputType.TYPE_CLASS_PHONE, imeAction = EditorInfo.IME_ACTION_NEXT)
        historyInput = fieldInput("পূর্ববর্তী রোগ/অ্যালার্জি (যদি থাকে)", InputType.TYPE_CLASS_TEXT, multiLine = true, imeAction = EditorInfo.IME_ACTION_DONE)

        nameInput.setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_NEXT) { ageInput.requestFocus(); true } else false }
        ageInput.setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_NEXT) { addressInput.requestFocus(); true } else false }
        addressInput.setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_NEXT) { emergencyInput.requestFocus(); true } else false }
        emergencyInput.setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_NEXT) { historyInput.requestFocus(); true } else false }
        historyInput.setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_DONE) { hideKeyboard(); historyInput.clearFocus(); true } else false }

        val smartFocusListener = View.OnFocusChangeListener { v, hasFocus ->
            val et = v as? EditText
            if (et != null) {
                if (et.background.constantState != fieldBgError().constantState) {
                    et.background = fieldBg(hasFocus)
                }
            }
            if (hasFocus) {
                v.postDelayed({ ensureVisible(v) }, 150)
            } else {
                et?.let { if (it.text.toString().trim().isNotEmpty()) it.background = fieldBg(false) }
            }
        }
        nameInput.onFocusChangeListener = smartFocusListener
        ageInput.onFocusChangeListener = smartFocusListener
        addressInput.onFocusChangeListener = smartFocusListener
        emergencyInput.onFocusChangeListener = smartFocusListener
        historyInput.onFocusChangeListener = smartFocusListener

        submitBtn = premiumButton("প্রোফাইল সাবমিট করুন") {
            hideKeyboard()
            onSubmit()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22) }
        }
        progress = ProgressBar(this).apply {
            visibility = View.GONE
            indeterminateTintList = ColorStateList.valueOf(colorPrimary)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12) }
        }
        statusText = text("", 12.5f, Typeface.NORMAL, colorError, Gravity.CENTER).apply { setPadding(0, dp(10), 0, 0) }

        card.addView(avatarSection)
        card.addView(formHeading)
        card.addView(space(dp(10)))
        card.addView(label("পূর্ণ নাম *"))
        card.addView(nameInput)
        card.addView(space(dp(14)))
        card.addView(label("বয়স *"))
        card.addView(ageInput)
        card.addView(space(dp(14)))
        card.addView(genderLabel)
        card.addView(genderGroup)
        card.addView(space(dp(14)))
        card.addView(bloodLabel)
        card.addView(bloodGroupRow)
        card.addView(space(dp(14)))
        card.addView(label("ঠিকানা *"))
        card.addView(addressInput)
        card.addView(space(dp(14)))
        card.addView(label("জরুরি যোগাযোগ নাম্বার *"))
        card.addView(emergencyInput)
        card.addView(space(dp(14)))
        card.addView(label("পূর্ববর্তী রোগ/অ্যালার্জি (ঐচ্ছিক)"))
        card.addView(historyInput)
        card.addView(submitBtn)
        card.addView(progress)
        card.addView(statusText)
        card.addView(space(dp(120)))

        val footNote = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
            addView(dotView(this@SignupActivity, Color.parseColor("#10B981")).apply { layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply { rightMargin = dp(6) } })
            addView(text("আপনার তথ্য নিরাপদে সংরক্ষিত থাকবে", 10.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER))
        }

        container.addView(heroCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(card)
        container.addView(footNote)
        scroll.addView(container)
        root.addView(scroll)
        setContentView(root)
        applyFontRecursively(root)
        root.requestFocus()

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBarsBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom

            if (imeVisible) {
                containerView.updatePadding(bottom = imeHeight + dp(24))
                currentFocus?.let { ensureVisible(it) }
            } else {
                containerView.updatePadding(bottom = normalContainerBottomPadding + systemBarsBottom)
            }
            insets
        }
    }

    private fun ensureVisible(focusedView: View) {
        val rect = Rect()
        focusedView.getGlobalVisibleRect(rect)
        val visibleFrame = Rect()
        rootView.getWindowVisibleDisplayFrame(visibleFrame)
        if (rect.bottom > visibleFrame.bottom) {
            val scrollDelta = rect.bottom - visibleFrame.bottom + dp(24)
            scrollView.smoothScrollBy(0, scrollDelta)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(currentFocus?.windowToken ?: rootView.windowToken, 0)
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
        val dialogTitle = text("রক্তের গ্রুপ নির্বাচন করুন", 15.5f, Typeface.BOLD, colorPrimaryDark, Gravity.START).apply { setPadding(0, 0, 0, dp(14)) }
        dialogRoot.addView(dialogTitle)
        bloodGroups.forEach { group ->
            val isSelected = group == selectedBloodGroup && isBloodGroupSelected
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = if (isSelected) GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(14).toFloat()
                    setColor(Color.argb(24, 15, 108, 97))
                    setStroke(dp(1), colorPrimary)
                } else roundedBg(colorFieldBg, 14f)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
            }
            val rowLabel = text(group, 14.5f, if (isSelected) Typeface.BOLD else Typeface.NORMAL, if (isSelected) colorPrimary else Color.parseColor("#1F2937"), Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(rowLabel)
            if (isSelected) { row.addView(text("✓", 15f, Typeface.BOLD, colorPrimary, Gravity.END)) }
            row.setOnClickListener {
                selectedBloodGroup = group
                isBloodGroupSelected = true
                bloodGroupField.text = group
                bloodGroupField.setTextColor(colorDark)
                bloodGroupField.setTypeface(appFont, Typeface.BOLD)
                bloodGroupRow.background = fieldBg(false)
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

    private fun showPhotoReminderDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.WHITE, 20f)
            setPadding(dp(24), dp(22), dp(24), dp(18))
            elevation = dp(14).toFloat()
        }
        val dialogTitle = text("প্রোফাইল ছবি যুক্ত করেননি", 15.5f, Typeface.BOLD, colorPrimaryDark, Gravity.CENTER)
        val msg = text(
            "প্রোফাইল ছবি থাকলে আপনার তথ্য সহজে সনাক্ত করা যায়। আপনি চাইলে এখনই একটি ছবি যুক্ত করতে পারেন, অথবা ছবি ছাড়াই চালিয়ে যেতে পারেন।",
            12.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER
        ).apply { setPadding(0, dp(10), 0, dp(18)) }
        val addBtn = premiumButton("ছবি যুক্ত করুন") {
            dialog.dismiss()
            pickImageLauncher.launch("image/*")
        }
        val skipText = text("ছবি ছাড়াই চালিয়ে যান", 13f, Typeface.BOLD, colorTextMuted, Gravity.CENTER).apply {
            setPadding(0, dp(14), 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                skipPhotoUpload = true
                dialog.dismiss()
                onSubmit()
            }
        }
        layout.addView(dialogTitle)
        layout.addView(msg)
        layout.addView(addBtn)
        layout.addView(skipText)
        applyFontRecursively(layout)
        dialog.setContentView(layout)
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
        var isValid = true
        var firstErrorView: View? = null

        fun setError(input: EditText) {
            input.background = fieldBgError()
            if (firstErrorView == null) firstErrorView = input
            isValid = false
        }
        fun setNormal(input: EditText) {
            input.background = fieldBg(false)
        }

        val name = nameInput.text.toString().trim()
        val ageStr = ageInput.text.toString().trim()
        val address = addressInput.text.toString().trim()
        val emergencyRaw = emergencyInput.text.toString().trim()
        val history = historyInput.text.toString().trim()

        setNormal(nameInput); setNormal(ageInput); setNormal(addressInput); setNormal(emergencyInput); setNormal(historyInput)
        bloodGroupRow.background = fieldBg(false)

        if (name.isEmpty()) { setError(nameInput) }
        if (ageStr.isEmpty()) { setError(ageInput) }
        if (address.isEmpty()) { setError(addressInput) }
        if (emergencyRaw.isEmpty()) { setError(emergencyInput) }
        if (!isBloodGroupSelected) {
            bloodGroupRow.background = fieldBgError()
            if (firstErrorView == null) firstErrorView = bloodGroupRow
            isValid = false
        }

        // Emergency Contact Smart Validation - BD 11 digit / Foreign
        if (emergencyRaw.isNotEmpty()) {
            val eValid = SupabaseClient.PhoneValidator.validate(emergencyRaw)
            if (!eValid.isValid) {
                statusText.text = "জরুরি নাম্বার: ${eValid.message}"
                setError(emergencyInput)
                isValid = false
            }
        }

        if (!isValid) {
            if (statusText.text.isEmpty()) statusText.text = "অনুগ্রহ করে * চিহ্নিত সব ফিল্ড পূরণ করুন"
            firstErrorView?.let { ensureVisible(it) }
            return
        }

        if (pin.isBlank()) {
            statusText.text = "PIN পাওয়া যায়নি, দয়া করে আবার শুরু থেকে চেষ্টা করুন"
            return
        }

        if (selectedImageUri == null && !skipPhotoUpload) {
            showPhotoReminderDialog()
            return
        }

        val age = ageStr.toIntOrNull()
        val gender = when (genderGroup.indexOfChild(findViewById(genderGroup.checkedRadioButtonId))) {
            0 -> "পুরুষ"; 1 -> "মহিলা"; else -> "অন্যান্য"
        }

        val emergencyNormalized = SupabaseClient.PhoneValidator.validate(emergencyRaw).normalizedPhone

        proceedWithSubmit(name, age, gender, address, emergencyNormalized, history)
    }

    private fun proceedWithSubmit(
        name: String,
        age: Int?,
        gender: String,
        address: String,
        emergency: String,
        history: String
    ) {
        submitBtn.isEnabled = false
        progress.visibility = View.VISIBLE
        statusText.text = ""

        lifecycleScope.launch {
            var profileUrl = ""
            val uri = selectedImageUri
            if (uri != null) {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val mime = contentResolver.getType(uri) ?: "image/jpeg"
                        val uploadResult = SupabaseClient.uploadProfilePicture("profile.jpg", mime, bytes)
                        if (uploadResult.isFailure) {
                            progress.visibility = View.GONE
                            submitBtn.isEnabled = true
                            statusText.text = uploadResult.exceptionOrNull()?.message ?: "প্রোফাইল ছবি আপলোড করতে সমস্যা হয়েছে"
                            return@launch
                        }
                        profileUrl = uploadResult.getOrDefault("")
                    }
                } catch (e: Exception) {
                    progress.visibility = View.GONE
                    submitBtn.isEnabled = true
                    statusText.text = "প্রোফাইল ছবি পড়তে সমস্যা হয়েছে"
                    return@launch
                }
            }

            val deviceId = DeviceUtils.getDeviceId(this@SignupActivity)
            val conflictResult = SupabaseClient.findPatientByDeviceId(deviceId)
            if (conflictResult.getOrNull() != null) {
                progress.visibility = View.GONE
                submitBtn.isEnabled = true
                statusText.text = "এই ডিভাইসে ইতিমধ্যে একটি একাউন্ট যুক্ত আছে। সাহায্যের জন্য অ্যাডমিনের সাথে যোগাযোগ করুন। Doctor: 01710355342 Admin: 01632336631"
                return@launch
            }

            val result = SupabaseClient.registerPatient(
                SupabaseClient.NewPatient(
                    phone = phone,
                    fullName = name,
                    age = age,
                    gender = gender,
                    bloodGroup = selectedBloodGroup,
                    address = address,
                    emergencyContact = emergency,
                    medicalHistory = history,
                    pin = pin,
                    deviceId = deviceId,
                    profilePictureUrl = profileUrl
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

    private fun fieldInput(hintText: String, type: Int, multiLine: Boolean = false, imeAction: Int = EditorInfo.IME_ACTION_NEXT): EditText = EditText(this).apply {
        hint = hintText
        setHintTextColor(Color.parseColor("#9AA8A5"))
        inputType = if (multiLine) type or InputType.TYPE_TEXT_FLAG_MULTI_LINE else type
        if (multiLine) minLines = 2
        imeOptions = imeAction or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        if (multiLine) setSingleLine(false) else setSingleLine(true)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = fieldBg(false)
        textSize = 14.5f
        setTextColor(colorDark)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
    }

    private fun fieldBg(active: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        setColor(colorFieldBg)
        setStroke((1.6f * resources.displayMetrics.density).toInt(), if (active) colorFieldBorderActive else colorBorder)
    }

    private fun fieldBgError(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        setColor(Color.parseColor("#FFF5F5"))
        setStroke((1.6f * resources.displayMetrics.density).toInt(), colorError)
    }

    private fun cardBg(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(20).toFloat()
        setColor(colorCardBg)
        setStroke(dp(1), colorBorder)
    }

    private fun styledRadio(labelText: String): RadioButton = RadioButton(this).apply {
        text = labelText; id = View.generateViewId(); textSize = 14f; setTextColor(colorDark); setPadding(dp(6), 0, 0, 0)
        buttonTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)), intArrayOf(colorPrimary, Color.parseColor("#B9C7C4")))
    }

    private fun premiumButton(labelText: String, onClick: () -> Unit): Button = Button(this).apply {
        text = labelText; setTextColor(Color.WHITE); isAllCaps = false; setTypeface(null, Typeface.BOLD); textSize = 15f
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(colorPrimaryLight, colorPrimaryDark)).apply { cornerRadius = dp(16).toFloat() }
        elevation = dp(3).toFloat(); setPadding(0, dp(14), 0, dp(14))
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
        setOnClickListener { onClick() }
    }

    private fun label(t: String): TextView = text(t, 12.5f, Typeface.BOLD, colorTextMuted, Gravity.START)
    private fun text(t: String, sizeSp: Float, style: Int, color: Int, gravity: Int): TextView = TextView(this).apply {
        text = t; textSize = sizeSp; setTypeface(appFont, style); setTextColor(color); this.gravity = gravity
    }
    private fun space(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, h) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = radiusDp * resources.displayMetrics.density; setColor(color) }
    private fun applyFontRecursively(view: View) { val font = appFont ?: return; when (view) { is TextView -> view.setTypeface(font, view.typeface?.style ?: Typeface.NORMAL); is ViewGroup -> for (i in 0 until view.childCount) applyFontRecursively(view.getChildAt(i)) } }
    private fun dotView(context: Context, color: Int): View = View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } }

    private fun personPlaceholderDrawable(): android.graphics.drawable.Drawable {
        val size = dp(92)
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B9C7C4") }
        val cx = size / 2f
        canvas.drawCircle(cx, size * 0.38f, size * 0.16f, paint)
        val bodyPath = android.graphics.Path().apply {
            moveTo(size * 0.22f, size * 0.92f)
            quadTo(cx, size * 0.60f, size * 0.78f, size * 0.92f)
            lineTo(size * 0.22f, size * 0.92f)
            close()
        }
        canvas.drawPath(bodyPath, paint)
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    private fun cameraIconView(context: Context): View = object : View(context) {
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorPrimary; style = Paint.Style.FILL }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRoundRect(android.graphics.RectF(w * 0.05f, h * 0.28f, w * 0.95f, h * 0.9f), 2f, 2f, bodyPaint)
            canvas.drawCircle(w / 2, h * 0.6f, h * 0.2f, lensPaint)
        }
    }

    private fun checkBadgeView(context: Context): View = object : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.4f; strokeCap = Paint.Cap.ROUND }
        override fun onDraw(canvas: Canvas) { super.onDraw(canvas); val w = width.toFloat(); val h = height.toFloat(); val path = android.graphics.Path().apply { moveTo(w * 0.18f, h * 0.52f); lineTo(w * 0.42f, h * 0.76f); lineTo(w * 0.85f, h * 0.24f) }; canvas.drawPath(path, p) }
    }
    class SignupDecorView(context: Context) : View(context) {
        private val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(22, 255, 255, 255) }
        private val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(14, 255, 255, 255) }
        override fun onDraw(canvas: Canvas) { super.onDraw(canvas); val w = width.toFloat(); val h = height.toFloat(); canvas.drawCircle(w * 0.90f, h * 0.05f, w * 0.32f, p1); canvas.drawCircle(w * 0.06f, h * 0.90f, w * 0.22f, p2) }
    }
    class SignupIconView(context: Context) : View(context) {
        private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val paintOrange = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        init { setLayerType(LAYER_TYPE_SOFTWARE, null); paintWhite.setShadowLayer(12f, 0f, 5f, Color.argb(55, 0, 0, 0)) }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas); val w = width.toFloat(); val h = height.toFloat(); canvas.drawCircle(w / 2, h / 2, w / 2 - 5f, paintWhite)
            paintOrange.shader = RadialGradient(w / 2, h * 0.38f, w * 0.20f, Color.parseColor("#FBBF24"), Color.parseColor("#F59E0B"), Shader.TileMode.CLAMP)
            canvas.drawCircle(w / 2, h * 0.38f, w * 0.16f, paintOrange)
            val path = android.graphics.Path().apply { moveTo(w * 0.22f, h * 0.82f); quadTo(w * 0.5f, h * 0.55f, w * 0.78f, h * 0.82f); lineTo(w * 0.22f, h * 0.82f); close() }; canvas.drawPath(path, paintOrange)
        }
    }
}
