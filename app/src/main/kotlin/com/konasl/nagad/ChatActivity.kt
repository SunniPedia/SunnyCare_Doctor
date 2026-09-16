package com.konasl.nagad

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ChatActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorBg = Color.parseColor("#F4F7F6")
    private val colorTextMuted = Color.parseColor("#6B7280")
    private val colorDark = Color.parseColor("#111827")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appointmentId = intent.getStringExtra("appointment_id") ?: ""
        val patientId = intent.getStringExtra("patient_id") ?: ""
        val patientName = intent.getStringExtra("patient_name") ?: ""
        val patientPhone = intent.getStringExtra("patient_phone") ?: ""

        val root = FrameLayout(this).apply {
            setBackgroundColor(colorBg)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(16))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
            }
        }
        val backBtn = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(colorDark)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        topBar.addView(backBtn)

        content.addView(text("চ্যাট", 20f, Typeface.BOLD, colorPrimaryDark))
        content.addView(text(patientName.ifEmpty { "রোগী" }, 14f, Typeface.BOLD, colorDark).apply {
            setPadding(0, dp(6), 0, 0)
        })
        if (patientPhone.isNotBlank()) {
            content.addView(text(patientPhone, 12f, Typeface.NORMAL, colorTextMuted).apply {
                setPadding(0, dp(2), 0, 0)
            })
        }
        content.addView(text("চ্যাট ফিচারটি শীঘ্রই যুক্ত করা হবে।", 12.5f, Typeface.NORMAL, colorTextMuted).apply {
            setPadding(0, dp(20), 0, 0)
        })

        root.addView(content)
        root.addView(topBar)
        setContentView(root)
    }

    private fun text(t: String, sizeSp: Float, style: Int, color: Int): TextView = TextView(this).apply {
        text = t; textSize = sizeSp; setTypeface(null, style); setTextColor(color); gravity = Gravity.CENTER
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
