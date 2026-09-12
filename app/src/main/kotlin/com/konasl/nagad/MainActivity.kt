package com.konasl.nagad

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. BACKGROUND - Programmatically Gradient Drawable
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#0F6C61"), Color.parseColor("#0A4A42"))
        )

        // 2. ROOT LAYOUT
        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = gradientDrawable
        }

        // 3. CENTER CONTAINER
        val centerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            setPadding(40)
        }

        // 4. ICON - Programmatically Custom View (No drawable)
        val iconView = SunnyCareIconView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(112), dp(112)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        // 5. APP NAME
        val appName = createText("SunnyCare ☀", 34f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
            letterSpacing = 0.02f
        }
        val subName = createText("DOCTOR", 13f, Typeface.BOLD, Color.parseColor("#F59E0B"), Gravity.CENTER).apply {
            letterSpacing = 0.3f
        }

        // 6. DOCTOR DETAILS - Keep All Details
        val drName = createText("ডা. মাসুম বিল্লাহ সানি", 19f, Typeface.BOLD, Color.WHITE, Gravity.CENTER)

        val degrees = createText(
            "এম.বি.বি.এস (সি.ইউ), ডি.এম.ইউ (আল্ট্রা),\nপিজিটি, এম.সি.জি.পি (মেডিসিন ও শিশু),\nসি.সি.ডি (ডায়াবেটিস- বারডেম, ঢাকা)",
            11.5f, Typeface.NORMAL, Color.argb(230, 255, 255, 255), Gravity.CENTER
        )

        val designation = createText(
            "এক্স মেডিকেল অফিসার:\nপার্কভিউ মেডিকেল কলেজ হাসপাতাল, সিলেট।",
            11f, Typeface.NORMAL, Color.argb(190, 255, 255, 255), Gravity.CENTER
        )

        // 7. BMDC BADGE - Programmatically background
        val bmdcBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(50).toFloat()
            setColor(Color.argb(45, 255, 255, 255))
        }
        val bmdcBadge = createText("বি.এম.ডি.সি এ-১৭৬৩০  •  Verified", 10.5f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
            background = bmdcBg
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }

        // 8. LOADING DOTS - Programmatically
        val dotsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(36) }
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

        // 9. FOOTER
        val footer = createText(
            "মেডিসিন-শিশু, ডায়াবেটিস, উচ্চ রক্তচাপ, বাত ব্যাথা,\nনাক-কান-গলা, এলার্জি, শ্বাসকষ্ট ও চর্মরোগে অভিজ্ঞ।",
            9f, Typeface.NORMAL, Color.argb(115, 255, 255, 255), Gravity.CENTER
        ).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(28)
            }
            setPadding(dp(16), 0, dp(16), 0)
        }

        // ADD ALL VIEWS
        centerLayout.apply {
            addView(iconView)
            addView(space(dp(22)))
            addView(appName)
            addView(subName)
            addView(space(dp(28)))
            addView(drName)
            addView(space(dp(8)))
            addView(degrees)
            addView(space(dp(10)))
            addView(designation)
            addView(space(dp(12)))
            addView(bmdcBadge)
            addView(dotsLayout)
        }

        root.addView(centerLayout)
        root.addView(footer)

        setContentView(root)

        // Auto navigate after 2.6s - WITH LOGIN CHECK
        Handler(Looper.getMainLooper()).postDelayed({
            if (SupabaseClient.isLoggedIn(this)) {
                startActivity(Intent(this, HomeActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 2600)
    }

    // Helper: Custom Icon View - Draw Sun + Medical Cross
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
