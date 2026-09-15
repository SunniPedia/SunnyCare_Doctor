package com.konasl.nagad

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding

class MainActivity : AppCompatActivity() {

    private var customTypeface: Typeface? = null
    private val allTextViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#0F6C61"), Color.parseColor("#0A4A42"))
        )

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = gradientDrawable
        }

        val centerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            setPadding(dp(28), dp(20), dp(28), dp(20))
        }

        // ===== App icon (unchanged, as requested) =====
        val iconView = SunnyCareIconView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(100), dp(100)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        val appName = createText("SunnyCare ☀", 30f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
            letterSpacing = 0.02f
        }

        // ===== Doctor details card =====
        val drName = createText("ডা. মাসুম বিল্লাহ সানি", 19f, Typeface.BOLD, Color.WHITE, Gravity.CENTER)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(3)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
                bottomMargin = dp(2)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(Color.parseColor("#F59E0B"))
            }
        }

        val degreesText = createRowText(
            "এম.বি.বি.এস (সিইউ), ডি.এম.ইউ (আল্ট্রা),\nপিজিটি (পিএমসি), এম.সি.জি.পি (মেডিসিন ও শিশু),\nসি.সি.ডি (ডায়াবেটিস-বারডেম, ঢাকা)",
            10.5f, Typeface.NORMAL, Color.argb(235, 255, 255, 255)
        )

        val designationText = createRowText(
            "প্রাক্তন মেডিকেল অফিসার:\nপার্কভিউ মেডিকেল কলেজ হাসপাতাল, সিলেট",
            10f, Typeface.NORMAL, Color.argb(195, 255, 255, 255)
        )

        val specialtiesText = createRowText(
            "মেডিসিন-শিশু, ডায়াবেটিস, উচ্চ রক্তচাপ, নাক-কান-গলা, বাত ব্যাথা, এলার্জি, শ্বাসকষ্ট, চর্ম ও যৌনরোগে অভিজ্ঞ।",
            10f, Typeface.NORMAL, Color.argb(205, 255, 255, 255)
        )

        val chamberText = createRowText(
            "ঢাকা ডায়গনস্টিক সেন্টার, মাধবপুর পশ্চিম বাজার (সোনালী ব্যাংকের নিচ তলা), কাজী আক্তার ম্যানশন, মাধবপুর, হবিগঞ্জ।",
            9.5f, Typeface.NORMAL, Color.argb(180, 255, 255, 255)
        )

        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(Color.argb(26, 255, 255, 255))
            setStroke(dp(1), Color.argb(55, 255, 255, 255))
        }

        val doctorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(dp(18), dp(18), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(22)
            }
            addView(drName)
            addView(divider)
            addView(iconTextRow(VectorIconView.IconType.CAP, degreesText))
            addView(iconTextRow(VectorIconView.IconType.HOSPITAL, designationText))
            addView(iconTextRow(VectorIconView.IconType.HEART, specialtiesText))
            addView(iconTextRow(VectorIconView.IconType.PIN, chamberText))
        }

        // ===== BMDC verified badge (with shield vector icon) =====
        val bmdcBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(50).toFloat()
            setColor(Color.argb(45, 255, 255, 255))
        }
        val bmdcText = createText("বি.এম.ডি.সি নং – এ-১১৭৪৬৩  •  Verified", 10f, Typeface.BOLD, Color.WHITE, Gravity.CENTER)
        val bmdcRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bmdcBg
            setPadding(dp(14), dp(7), dp(16), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(18)
            }
            val shieldIcon = VectorIconView(this@MainActivity, VectorIconView.IconType.SHIELD).apply {
                layoutParams = LinearLayout.LayoutParams(dp(15), dp(15)).apply { rightMargin = dp(8) }
            }
            addView(shieldIcon)
            addView(bmdcText)
        }

        // ===== Loading dots =====
        val dotsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(26) }
        }
        val dots = mutableListOf<View>()
        repeat(3) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                    leftMargin = dp(4); rightMargin = dp(4)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                alpha = 0.3f
            }
            dots.add(dot)
            dotsLayout.addView(dot)
        }
        animateDots(dots)

        val footer = createText(
            "আপনার সুস্থতাই আমাদের অঙ্গীকার",
            9.5f, Typeface.NORMAL, Color.argb(120, 255, 255, 255), Gravity.CENTER
        ).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(22)
            }
            setPadding(dp(16), 0, dp(16), 0)
        }

        centerLayout.apply {
            addView(iconView)
            addView(space(dp(16)))
            addView(appName)
            addView(doctorCard)
            addView(bmdcRow)
            addView(dotsLayout)
        }

        root.addView(centerLayout)
        root.addView(footer)
        setContentView(root)

        // === ফন্ট লোডিং লজিক ===
        // আগে ইন্টারনেট থেকে ফন্ট ডাউনলোড করে internal storage-এ সেভ করে তারপর লোড করা হতো।
        // এখন ফন্ট ফাইলটা সরাসরি প্রজেক্টের res/font/solaimanlipi.ttf-এ বান্ডেল করা আছে, তাই
        // কোনো নেটওয়ার্ক কল বা ব্যাকগ্রাউন্ড থ্রেড ছাড়াই ResourcesCompat.getFont() দিয়ে সরাসরি লোড হয়।
        loadFontAndApply()

        // Auto navigate
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (SupabaseClient.isLoggedIn(this)) {
                startActivity(android.content.Intent(this, HomeActivity::class.java))
            } else {
                startActivity(android.content.Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 2600)
    }

    /**
     * res/font/solaimanlipi.ttf থেকে সরাসরি টাইপফেস লোড করে — এটা একটা লোকাল রিসোর্স ফাইল
     * (কোনো ডাউনলোড/নেটওয়ার্ক কল নেই), তাই কোনো ব্যাকগ্রাউন্ড থ্রেডের প্রয়োজন নেই, মূল থ্রেডেই
     * তাৎক্ষণিকভাবে লোড হয়ে যায়।
     */
    private fun loadFontAndApply() {
        try {
            customTypeface = ResourcesCompat.getFont(this, R.font.solaimanlipi)

            // MyApp কে জানিয়ে দাও ফন্ট রেডি - Auto System এর জন্য
            (application as MyApp).loadFont()

            customTypeface?.let { tf ->
                allTextViews.forEach { tv ->
                    if (tv.text.any { c -> c.code in 2432..2559 }) {
                        val oldStyle = tv.typeface?.style ?: Typeface.NORMAL
                        tv.typeface = Typeface.create(tf, oldStyle)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ===== Main circular sun/medical-cross logo (kept exactly as before) =====
    class SunnyCareIconView(context: Context) : View(context) {
        private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val paintOrange = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B"); style = Paint.Style.FILL; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND }
        private val paintOrangeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B"); style = Paint.Style.STROKE; strokeWidth = 7f; strokeCap = Paint.Cap.ROUND }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2
            val cy = h / 2
            canvas.drawCircle(cx, cy, w / 2, paintWhite)
            canvas.drawCircle(cx, cy, w * 0.22f, paintOrange)
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45).toDouble())
                val r1 = w * 0.30f
                val r2 = w * 0.38f
                val sx = cx + r1 * Math.cos(angle).toFloat()
                val sy = cy + r1 * Math.sin(angle).toFloat()
                val ex = cx + r2 * Math.cos(angle).toFloat()
                val ey = cy + r2 * Math.sin(angle).toFloat()
                canvas.drawLine(sx, sy, ex, ey, paintOrangeStroke)
            }
            val crossW = w * 0.24f
            val crossH = w * 0.08f
            canvas.drawRect(cx - crossW / 2, cy - crossH / 2, cx + crossW / 2, cy + crossH / 2, paintWhite)
            canvas.drawRect(cx - crossH / 2, cy - crossW / 2, cx + crossH / 2, cy + crossW / 2, paintWhite)
        }
    }

    // ===== Small vector icons used inside the doctor details card =====
    class VectorIconView(context: Context, private val iconType: IconType) : View(context) {

        enum class IconType { CAP, HOSPITAL, HEART, PIN, SHIELD }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#F59E0B") }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            when (iconType) {
                IconType.CAP -> drawCap(canvas, w, h)
                IconType.HOSPITAL -> drawHospital(canvas, w, h)
                IconType.HEART -> drawHeart(canvas, w, h)
                IconType.PIN -> drawPin(canvas, w, h)
                IconType.SHIELD -> drawShield(canvas, w, h)
            }
        }

        // গ্র্যাজুয়েশন ক্যাপ — শিক্ষাগত যোগ্যতা (ডিগ্রি) বোঝাতে
        private fun drawCap(canvas: Canvas, w: Float, h: Float) {
            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.12f)
                lineTo(w * 0.95f, h * 0.40f)
                lineTo(w * 0.5f, h * 0.66f)
                lineTo(w * 0.05f, h * 0.40f)
                close()
            }
            canvas.drawPath(path, fillPaint)
            val band = RectF(w * 0.27f, h * 0.54f, w * 0.73f, h * 0.76f)
            canvas.drawRoundRect(band, w * 0.05f, w * 0.05f, accentPaint)
            canvas.drawLine(w * 0.87f, h * 0.42f, w * 0.87f, h * 0.82f, strokePaint)
            canvas.drawCircle(w * 0.87f, h * 0.86f, w * 0.05f, accentPaint)
        }

        // হাসপাতাল ভবন + ক্রস — কর্মস্থল/মেডিকেল অফিসার বোঝাতে
        private fun drawHospital(canvas: Canvas, w: Float, h: Float) {
            val rect = RectF(w * 0.12f, h * 0.14f, w * 0.88f, h * 0.90f)
            canvas.drawRoundRect(rect, w * 0.10f, w * 0.10f, fillPaint)
            val crossW = w * 0.14f
            val crossH = w * 0.36f
            val cx = w / 2
            val cy = h * 0.52f
            canvas.drawRect(cx - crossW / 2, cy - crossH / 2, cx + crossW / 2, cy + crossH / 2, accentPaint)
            canvas.drawRect(cx - crossH / 2, cy - crossW / 2, cx + crossH / 2, cy + crossW / 2, accentPaint)
        }

        // হার্টবিট/পালস লাইন — চিকিৎসার বিভিন্ন বিভাগ (ডিজিজ স্পেশালিটি) বোঝাতে
        private fun drawHeart(canvas: Canvas, w: Float, h: Float) {
            val path = Path().apply {
                moveTo(0f, h * 0.55f)
                lineTo(w * 0.22f, h * 0.55f)
                lineTo(w * 0.34f, h * 0.18f)
                lineTo(w * 0.47f, h * 0.88f)
                lineTo(w * 0.58f, h * 0.38f)
                lineTo(w * 0.68f, h * 0.55f)
                lineTo(w, h * 0.55f)
            }
            canvas.drawPath(path, strokePaint)
            canvas.drawCircle(w * 0.47f, h * 0.88f, w * 0.045f, accentPaint)
        }

        // ম্যাপ পিন — চেম্বার/ঠিকানা বোঝাতে
        private fun drawPin(canvas: Canvas, w: Float, h: Float) {
            val cx = w / 2
            val topCy = h * 0.34f
            val r = w * 0.32f
            canvas.drawCircle(cx, topCy, r, fillPaint)
            val tri = Path().apply {
                moveTo(cx - r * 0.78f, topCy + r * 0.55f)
                lineTo(cx + r * 0.78f, topCy + r * 0.55f)
                lineTo(cx, h * 0.95f)
                close()
            }
            canvas.drawPath(tri, fillPaint)
            canvas.drawCircle(cx, topCy, r * 0.42f, accentPaint)
        }

        // শিল্ড + চেকমার্ক — বি.এম.ডি.সি ভেরিফায়েড বোঝাতে
        private fun drawShield(canvas: Canvas, w: Float, h: Float) {
            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.04f)
                lineTo(w * 0.92f, h * 0.20f)
                lineTo(w * 0.92f, h * 0.54f)
                cubicTo(w * 0.92f, h * 0.80f, w * 0.72f, h * 0.95f, w * 0.5f, h * 0.98f)
                cubicTo(w * 0.28f, h * 0.95f, w * 0.08f, h * 0.80f, w * 0.08f, h * 0.54f)
                lineTo(w * 0.08f, h * 0.20f)
                close()
            }
            canvas.drawPath(path, fillPaint)
            val check = Path().apply {
                moveTo(w * 0.30f, h * 0.50f)
                lineTo(w * 0.45f, h * 0.65f)
                lineTo(w * 0.72f, h * 0.35f)
            }
            val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.parseColor("#0A4A42")
                strokeWidth = 5f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(check, checkPaint)
        }
    }

    // কেন্দ্রীয় (centered) টেক্সট তৈরি করে — টাইটেল/নাম/ব্যাজের জন্য
    private fun createText(text: String, sizeSp: Float, style: Int, color: Int, gravity: Int): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTypeface(null, style)
            setTextColor(color)
            this.gravity = gravity
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { this.gravity = Gravity.CENTER_HORIZONTAL }
            MyApp.banglaTypeface?.let {
                if (text.any { c -> c.code in 2432..2559 }) {
                    typeface = Typeface.create(it, style)
                }
            }
            allTextViews.add(this)
        }
    }

    // আইকন-রো'র পাশে বসানোর জন্য বাম-সারিবদ্ধ (left aligned), width-flexible টেক্সট
    private fun createRowText(text: String, sizeSp: Float, style: Int, color: Int): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTypeface(null, style)
            setTextColor(color)
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            MyApp.banglaTypeface?.let {
                if (text.any { c -> c.code in 2432..2559 }) {
                    typeface = Typeface.create(it, style)
                }
            }
            allTextViews.add(this)
        }
    }

    // ভেক্টর আইকন + টেক্সট নিয়ে একটি হরাইজন্টাল সারি (row) তৈরি করে
    private fun iconTextRow(iconType: VectorIconView.IconType, textView: TextView, iconSizeDp: Int = 18): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) }
            val icon = VectorIconView(this@MainActivity, iconType).apply {
                layoutParams = LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp)).apply {
                    rightMargin = dp(9)
                    topMargin = dp(2)
                }
            }
            addView(icon)
            addView(textView)
        }
    }

    private fun space(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, h) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun animateDots(dots: List<View>) {
        val animator = ValueAnimator.ofFloat(0.3f, 1f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val v = it.animatedValue as Float
                dots.forEachIndexed { index, dot ->
                    dot.alpha = v * (1f - index * 0.15f)
                }
            }
        }
        animator.start()
    }
}
