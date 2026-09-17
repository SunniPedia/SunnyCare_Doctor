package com.konasl.nagad

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * অ্যাপ ফোরগ্রাউন্ডে (যেকোনো একটিভিটিতে) থাকা অবস্থায় ডাক্তার/এডমিন
 * (01710355342, 01632336631) থেকে মেসেজ এলে দেখানোর জন্য একটা সুন্দর
 * কাস্টম ডায়ালগ। ট্যাপ করলে সরাসরি WaitingActivity তে নিয়ে যায়।
 *
 * এটা MyApp থেকে কল হয় (DoctorAlertService এর ব্রডকাস্ট রিসিভ করার পর)।
 */
object DoctorAlertDialogHelper {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#083F39")
    private val colorText = Color.parseColor("#111827")
    private val colorMuted = Color.parseColor("#6B7280")

    fun show(activity: Activity, preview: String, appointmentId: String) {
        if (activity.isFinishing || activity.isDestroyed) return

        // ডিফল্ট নোটিফিকেশন সাউন্ড — ফোরগ্রাউন্ডে থাকলেও ইউজার যেন বুঝতে পারে
        try {
            val ringtone = RingtoneManager.getRingtone(
                activity,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )
            ringtone?.play()
        } catch (_: Exception) {
        }

        val density = activity.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val dialog = Dialog(activity)

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.WHITE)
            }
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorPrimary, colorPrimaryDark)
            ).apply {
                cornerRadii = floatArrayOf(
                    dp(20).toFloat(), dp(20).toFloat(),
                    dp(20).toFloat(), dp(20).toFloat(),
                    0f, 0f,
                    0f, 0f
                )
            }
        }

        // ডাক্তারের ছোট্ট এভাটার — সাদা বৃত্তের উপর "ডা" লেখা
        val avatar = object : View(activity) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val r = minOf(width, height) / 2f
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy, r, paint)
                paint.color = colorPrimary
                paint.textSize = r * 0.8f
                val fm = paint.fontMetrics
                canvas.drawText("ডা", cx, cy - (fm.ascent + fm.descent) / 2f, paint)
            }
        }

        val titleColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        }

        val titleView = TextView(activity).apply {
            text = "ডাক্তার একটি মেসেজ পাঠিয়েছেন"
            setTextColor(Color.WHITE)
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitleView = TextView(activity).apply {
            text = "SunnyCare টেলিমেডিসিন"
            setTextColor(Color.argb(210, 255, 255, 255))
            textSize = 11f
        }

        titleColumn.addView(titleView)
        titleColumn.addView(subtitleView)

        header.addView(avatar, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(titleColumn)

        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(4))
        }

        val previewView = TextView(activity).apply {
            text = preview
            setTextColor(colorText)
            textSize = 14f
        }
        body.addView(previewView)

        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(18), dp(16), dp(18), dp(18))
        }

        val laterBtn = TextView(activity).apply {
            text = "পরে দেখব"
            textSize = 13.5f
            setTextColor(colorMuted)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }

        val viewBtn = TextView(activity).apply {
            text = "এখনই দেখুন"
            textSize = 13.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(colorPrimary)
            }
            setPadding(dp(20), dp(10), dp(20), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                try {
                    val intent = Intent(activity, WaitingActivity::class.java).apply {
                        putExtra("appointment_id", appointmentId)
                    }
                    activity.startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }

        buttonRow.addView(laterBtn)
        buttonRow.addView(
            viewBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
        )

        card.addView(header)
        card.addView(body)
        card.addView(buttonRow)

        dialog.setContentView(card)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.88).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.CENTER)
        }
        dialog.setCancelable(true)

        try {
            dialog.show()
        } catch (_: Exception) {
            // অ্যাক্টিভিটি ইতিমধ্যে ফিনিশ হয়ে থাকলে (রেস কন্ডিশন) ডায়লগ দেখানো এড়িয়ে যাওয়া হলো
        }
    }
}
