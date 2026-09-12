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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * ---------------------------------------------------------------------
 * SignupActivity — SunnyCare Prototype Design System-এর সাথে মিলিয়ে রিস্কিন করা হয়েছে:
 *   • Primary: #0F6C61 (teal) / Dark: #0A2A26
 *   • Screen bg: #F8FFFE (mint) | Card bg: #FFFFFF | Border: #E6EFED
 *   • Corner radius: বড় কার্ড 22-24dp, কম্পোনেন্ট 12-16dp (প্রোটোটাইপের মতো)
 *   • Glass/verified badge স্টাইল, soft shadow 0_8px_24px
 *   • SolaimanLipi বাংলা ফন্ট (assets/fonts/SolaimanLipi.ttf থাকলে অটো-লোড হবে, না থাকলে system font)
 *
 * সমস্ত আগের ফিচার/লজিক অপরিবর্তিত রাখা হয়েছে: ফর্ম ফিল্ড, কীবোর্ড হ্যান্ডলিং,
 * ফোকাস-চেইন, ব্লাড গ্রুপ ডায়ালগ, ভ্যালিডেশন, SupabaseClient কল, সেশন সেভ ইত্যাদি।
 * ---------------------------------------------------------------------
 */
class SignupActivity : AppCompatActivity() {

    // ---------------------------------------------------------------
    // Palette — প্রোটোটাইপ ডিজাইন সিস্টেম অনুযায়ী
    // ---------------------------------------------------------------
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
    private val colorSunFrom = Color.parseColor("#FBBF24")
    private val colorSunTo = Color.parseColor("#F59E0B")
    private val colorError = Color.parseColor("#D32F2F")

    // SolaimanLipi ফন্ট — assets/fonts/SolaimanLipi.ttf পাওয়া গেলে ব্যবহার হবে, নাহলে system default
    private var appFont: Typeface? = null

    private lateinit var nameInput: EditText
    private lateinit var ageInput: EditText
    private lateinit var addressInput: EditText
    private lateinit var emergencyInput: EditText
    private lateinit var historyInput: EditText
    private lateinit var genderGroup: RadioGroup
    private lateinit var bloodGroupField: TextView
    private lateinit var bloodGroupRow: LinearLayout
    private var selectedBloodGroup: String = ""
    private val bloodGroups = arrayOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "জানা নেই")
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

        appFont = try {
            Typeface.createFromAsset(assets, "fonts/SolaimanLipi.ttf")
        } catch (e: Exception) {
            null
        }

        // কীবোর্ড ওপেন হলে স্ক্রিন রিসাইজ হয়ে ফর্মটা স্ক্রল-এবল থাকবে, কোনো ফিল্ড কীবোর্ডের নিচে চাপা পড়বে না।
        // SOFT_INPUT_STATE_HIDDEN দিয়ে নিশ্চিত করা হচ্ছে Activity ওপেন হওয়ার সাথে সাথেই যেন
        // প্রথম EditText অটো-ফোকাস হয়ে কীবোর্ড নিজে থেকে পপ-আপ না করে।
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )

        // রুট ব্যাকগ্রাউন্ড এখন প্রোটোটাইপের মতো হালকা মিন্ট ফ্ল্যাট বেজ (ফুল-স্ক্রিন গ্র্যাডিয়েন্টের বদলে)
        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(colorScreenBg)
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
            overScrollMode = View.OVER_SCROLL_NEVER
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
            setPadding(dp(18), dp(28), dp(18), dp(24))
        }
        containerView = container
        normalContainerBottomPadding = dp(24)

        // --------------------------------------------------------
        // হিরো হেডার কার্ড — প্রোটোটাইপের rounded-[24px] গ্র্যাডিয়েন্ট হিরো স্টাইল
        // --------------------------------------------------------
        val heroCard = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(colorPrimaryLight, colorPrimaryDark)
            ).apply { cornerRadius = dp(24).toFloat() }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
                }
            }
            elevation = dp(10).toFloat()
        }
        val heroDecor = SignupDecorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val heroContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(30), dp(24), dp(28))
        }

        val icon = SignupIconView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        val title = text("প্রোফাইল তৈরি করুন", 20f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
            letterSpacing = 0.01f
        }
        val subtitle = text("চিকিৎসা সেবা পেতে আপনার তথ্য দিন", 12.5f, Typeface.NORMAL, Color.argb(215, 255, 255, 255), Gravity.CENTER)

        // প্রোটোটাইপের "Glass BMDC badge" কম্পোনেন্ট স্টাইলে ভেরিফায়েড নাম্বার ব্যাজ
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
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
            addView(checkBadgeView(this@SignupActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(6) }
            })
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

        // --------------------------------------------------------
        // ফর্ম কার্ড — প্রোটোটাইপের "rounded-[20px] bg-white border border-[#E6EFED]" স্টাইল
        // --------------------------------------------------------
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
            setPadding(dp(20), dp(22), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18)
            }
            elevation = dp(6).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
                }
            }
            clipToOutline = true
        }

        val formHeading = text("ব্যক্তিগত তথ্য", 13.5f, Typeface.BOLD, colorDark, Gravity.START).apply {
            setPadding(0, 0, 0, dp(4))
        }

        nameInput = fieldInput("পূর্ণ নাম *", InputType.TYPE_CLASS_TEXT, imeAction = EditorInfo.IME_ACTION_NEXT)
        ageInput = fieldInput("বয়স", InputType.TYPE_CLASS_NUMBER, imeAction = EditorInfo.IME_ACTION_NEXT)

        val genderLabel = label("লিঙ্গ")
        genderGroup = RadioGroup(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(styledRadio("পুরুষ"))
            addView(styledRadio("মহিলা").apply { (layoutParams as? LinearLayout.LayoutParams)?.leftMargin = dp(20) })
            addView(styledRadio("অন্যান্য"))
            (getChildAt(0).layoutParams as LinearLayout.LayoutParams).rightMargin = dp(20)
            (getChildAt(1).layoutParams as LinearLayout.LayoutParams).rightMargin = dp(20)
            check(getChildAt(0).id)
        }

        val bloodLabel = label("রক্তের গ্রুপ")

        // Spinner-এর বদলে অ্যাপের কালার থিম মেনে তৈরি একটি কাস্টম ক্লিকেবল ফিল্ড,
        // যাতে ট্যাপ করলে অ্যাপের কালারে স্টাইল করা কাস্টম ডায়ালগ ওপেন হয়
        selectedBloodGroup = bloodGroups[0]
        bloodGroupField = text(selectedBloodGroup, 14f, Typeface.BOLD, colorDark, Gravity.START).apply {
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
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
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
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.background = fieldBg(false)
                }
                false
            }
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
        // যাতে ইউজারকে নিজে থেকে স্ক্রল করে ফিল্ড খুঁজতে না হয়। একইসাথে ফোকাস অনুযায়ী ফিল্ডের বর্ডার
        // হাইলাইট করে প্রিমিয়াম ইনপুট-স্টেট দেখানো হচ্ছে।
        val scrollToViewOnFocus = View.OnFocusChangeListener { v, hasFocus ->
            (v as? EditText)?.background = fieldBg(hasFocus)
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

        submitBtn = premiumButton("প্রোফাইল সাবমিট করুন") {
            hideKeyboard()
            onSubmit()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(22)
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

        card.addView(formHeading)
        card.addView(space(dp(10)))
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
        card.addView(bloodGroupRow)
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
        card.addView(space(dp(30))) // কীবোর্ড খোলা অবস্থায় সাবমিট বাটন যেন নিচে চাপা না পড়ে

        // প্রোটোটাইপের "Components" চিপ স্টাইলে ছোট ফুটার ইঙ্গিত (ডিজাইন সিস্টেম কনসিস্টেন্সি)
        val footNote = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            }
            addView(dotView(this@SignupActivity, Color.parseColor("#10B981")).apply {
                layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply { rightMargin = dp(6) }
            })
            addView(text("আপনার তথ্য নিরাপদে সংরক্ষিত থাকবে", 10.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER))
        }

        container.addView(heroCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(card)
        container.addView(footNote)

        scroll.addView(container)
        root.addView(scroll)
        setContentView(root)

        applyFontRecursively(root)

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

    // অ্যাপের কালার থিম (colorPrimary/colorFieldBg) অনুসরণ করে রক্তের গ্রুপ নির্বাচনের কাস্টম ডায়ালগ
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
                        setColor(Color.argb(24, 15, 108, 97))
                        setStroke(dp(1), colorPrimary)
                    }
                else roundedBg(colorFieldBg, 14f)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                }
            }
            val rowLabel = text(group, 14.5f, if (isSelected) Typeface.BOLD else Typeface.NORMAL, if (isSelected) colorPrimary else Color.parseColor("#1F2937"), Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(rowLabel)
            if (isSelected) {
                row.addView(text("✓", 15f, Typeface.BOLD, colorPrimary, Gravity.END))
            }
            row.setOnClickListener {
                selectedBloodGroup = group
                bloodGroupField.text = group
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
        val gender = when (genderGroup.indexOfChild(findViewById(genderGroup.checkedRadioButtonId))) {
            0 -> "পুরুষ"; 1 -> "মহিলা"; else -> "অন্যান্য"
        }
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

    // ------------------------------------------------------------------
    private fun fieldInput(
        hintText: String,
        type: Int,
        multiLine: Boolean = false,
        imeAction: Int = EditorInfo.IME_ACTION_NEXT
    ): EditText = EditText(this).apply {
        hint = hintText
        setHintTextColor(Color.parseColor("#9AA8A5"))
        inputType = if (multiLine) type or InputType.TYPE_TEXT_FLAG_MULTI_LINE else type
        if (multiLine) minLines = 2
        // মাল্টি-লাইন ফিল্ডে "Enter"-কে newline হিসেবে ব্যবহার না করে কাস্টম ime action ব্যবহার করা হচ্ছে,
        // যাতে ফোকাস-চেইন ঠিকভাবে কাজ করে
        imeOptions = imeAction or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        if (multiLine) setSingleLine(false) else setSingleLine(true)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = fieldBg(false)
        textSize = 14.5f
        setTextColor(colorDark)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        }
    }

    /** ফোকাস/আনফোকাস অনুযায়ী বর্ডার-কালার বদলায় এমন ইনপুট-ফিল্ড ব্যাকগ্রাউন্ড — প্রোটোটাইপের #E6EFED বর্ডার টোকেন অনুযায়ী */
    private fun fieldBg(active: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        setColor(colorFieldBg)
        setStroke((1.6f * resources.displayMetrics.density).toInt(), if (active) colorFieldBorderActive else colorBorder)
    }

    /** কার্ডের ব্যাকগ্রাউন্ড — সাদা + #E6EFED বর্ডার, প্রোটোটাইপের rounded-[20px] কার্ড টোকেন */
    private fun cardBg(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(20).toFloat()
        setColor(colorCardBg)
        setStroke(dp(1), colorBorder)
    }

    /** থিম কালারে ট্রাই-স্টেট রেডিও বাটন, বড় টাচ-এরিয়া ও প্রিমিয়াম টাইপোগ্রাফি সহ */
    private fun styledRadio(labelText: String): RadioButton = RadioButton(this).apply {
        text = labelText
        id = View.generateViewId()
        textSize = 14f
        setTextColor(colorDark)
        setPadding(dp(6), 0, 0, 0)
        buttonTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(colorPrimary, Color.parseColor("#B9C7C4"))
        )
    }

    /** প্রিমিয়াম গ্রেডিয়েন্ট বাটন - প্রেস করলে হালকা স্কেল-অ্যানিমেশন, কোনো XML drawable ছাড়া */
    private fun premiumButton(labelText: String, onClick: () -> Unit): Button = Button(this).apply {
        text = labelText
        setTextColor(Color.WHITE)
        isAllCaps = false
        setTypeface(null, Typeface.BOLD)
        textSize = 15f
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(colorPrimaryLight, colorPrimaryDark)
        ).apply { cornerRadius = dp(16).toFloat() }
        elevation = dp(3).toFloat()
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

    private fun label(t: String): TextView = text(t, 12.5f, Typeface.BOLD, colorTextMuted, Gravity.START)

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

    /** ছোট সলিড ডট ইন্ডিকেটর (প্রোটোটাইপের emerald status dot-এর মতো) */
    private fun dotView(context: Context, color: Int): View = View(context).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    }

    /** ছোট চেক-ব্যাজ আইকন — Lucide-চেক আইকনের বিকল্প, ভেরিফায়েড ব্যাজের ভেতরে ব্যবহৃত */
    private fun checkBadgeView(context: Context): View = object : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            strokeCap = Paint.Cap.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            val path = android.graphics.Path().apply {
                moveTo(w * 0.18f, h * 0.52f)
                lineTo(w * 0.42f, h * 0.76f)
                lineTo(w * 0.85f, h * 0.24f)
            }
            canvas.drawPath(path, p)
        }
    }

    /** ব্যাকগ্রাউন্ড গ্রেডিয়েন্টের উপর হালকা translucent বৃত্ত - depth যোগ করার জন্য (কোনো resource ছাড়া) */
    class SignupDecorView(context: Context) : View(context) {
        private val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(22, 255, 255, 255) }
        private val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(14, 255, 255, 255) }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawCircle(w * 0.90f, h * 0.05f, w * 0.32f, p1)
            canvas.drawCircle(w * 0.06f, h * 0.90f, w * 0.22f, p2)
        }
    }

    /** SunnyCare আইকন — সাদা বৃত্ত + কমলা সূর্য + মেডিকেল ক্রস (প্রোটোটাইপের কম্পোনেন্ট টোকেন অনুযায়ী) */
    class SignupIconView(context: Context) : View(context) {
        private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val paintOrange = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            paintWhite.setShadowLayer(12f, 0f, 5f, Color.argb(55, 0, 0, 0))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawCircle(w / 2, h / 2, w / 2 - 5f, paintWhite)
            paintOrange.shader = RadialGradient(
                w / 2, h * 0.38f, w * 0.20f,
                Color.parseColor("#FBBF24"), Color.parseColor("#F59E0B"),
                Shader.TileMode.CLAMP
            )
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
