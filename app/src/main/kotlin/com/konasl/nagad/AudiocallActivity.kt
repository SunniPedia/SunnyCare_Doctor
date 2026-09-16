package com.konasl.nagad

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AudiocallActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorTextMuted = Color.parseColor("#D1D5DB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val patientName = intent.getStringExtra("patient_name") ?: ""
        val patientPhone = intent.getStringExtra("patient_phone") ?: ""

        val root = FrameLayout(this).apply {
            setBackgroundColor(colorPrimaryDark)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        content.addView(text("অডিও কল", 20f, Typeface.BOLD, Color.WHITE))
        content.addView(text(patientName.ifEmpty { "রোগী" }, 14f, Typeface.BOLD, Color.WHITE).apply {
            setPadding(0, dp(8), 0, 0)
        })
        if (patientPhone.isNotBlank()) {
            content.addView(text(patientPhone, 12f, Typeface.NORMAL, colorTextMuted).apply {
                setPadding(0, dp(2), 0, 0)
            })
        }
        content.addView(text("অডিও কল ফিচারটি শীঘ্রই যুক্ত করা হবে।", 12.5f, Typeface.NORMAL, colorTextMuted).apply {
            setPadding(0, dp(20), 0, 0)
        })
        content.addView(text("বন্ধ করতে ট্যাপ করুন", 13f, Typeface.BOLD, Color.WHITE).apply {
            setPadding(dp(24), dp(12), dp(24), dp(12))
            setBackgroundColor(Color.parseColor("#DC2626"))
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(28)
            layoutParams = lp
        })

        root.addView(content)
        setContentView(root)
    }

    private fun text(t: String, sizeSp: Float, style: Int, color: Int): TextView = TextView(this).apply {
        text = t; textSize = sizeSp; setTypeface(null, style); setTextColor(color); gravity = Gravity.CENTER
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
