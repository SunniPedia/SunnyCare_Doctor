package com.konasl.nagad

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PrescriptionActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorBg = Color.parseColor("#F4F7F6")
    private val colorDark = Color.parseColor("#111827")
    private val colorMuted = Color.parseColor("#6B7280")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F8FBFA"), colorBg)
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#16897A"), colorPrimary, colorPrimaryDark)
            ).apply {
                cornerRadii = floatArrayOf(
                    0f, 0f, 0f, 0f,
                    dp(26).toFloat(), dp(26).toFloat(),
                    dp(26).toFloat(), dp(26).toFloat()
                )
            }
            setPadding(dp(18), dp(38), dp(18), dp(22))
        }

        header.addView(ImageView(this).apply {
            setImageDrawable(HomeActivity.VectorIconDrawable(
                HomeActivity.VectorIconDrawable.IconType.ARROW_RIGHT,
                Color.WHITE,
                dp(20)
            ))
            rotation = 180f
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            background = rounded(Color.argb(40, 255, 255, 255), 12)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { finish() }
        })

        header.addView(text("আমার প্রেসক্রিপশন", 18f, Typeface.BOLD, Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(12) }
        })

        root.addView(header)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(110))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(Color.WHITE, 22)
            elevation = dp(2).toFloat()
            setPadding(dp(28), dp(30), dp(28), dp(30))
        }

        card.addView(ImageView(this).apply {
            setImageDrawable(HomeActivity.VectorIconDrawable(
                HomeActivity.VectorIconDrawable.IconType.PRESCRIPTION,
                colorPrimary,
                dp(30)
            ))
            background = rounded(Color.parseColor("#E7F4F1"), 18)
            layoutParams = LinearLayout.LayoutParams(dp(70), dp(70))
            setPadding(dp(19), dp(19), dp(19), dp(19))
        })

        card.addView(text(
            "প্রেসক্রিপশন",
            16f,
            Typeface.BOLD,
            colorDark,
            Gravity.CENTER
        ).apply { setPadding(0, dp(18), 0, 0) })

        card.addView(text(
            "ডাক্তারের প্রেসক্রিপশন এখানে দেখা যাবে।",
            12f,
            Typeface.NORMAL,
            colorMuted,
            Gravity.CENTER
        ).apply {
            setPadding(dp(8), dp(7), dp(8), 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        content.addView(card)
        root.addView(content)
        setContentView(root)
    }

    private fun text(
        value: String,
        size: Float,
        style: Int,
        color: Int,
        gravity: Int = Gravity.START
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        this.gravity = gravity
        setTypeface(null, style)
    }

    private fun rounded(color: Int, radiusDp: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
