package com.konasl.nagad

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AdminPaymentVerificationActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorAccent = Color.parseColor("#F59E0B")
    private val colorBg = Color.parseColor("#F4F7F6")
    private val colorTextMuted = Color.parseColor("#6B7280")
    private val colorCard = Color.WHITE

    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // নিরাপত্তা চেক — অ্যাডমিন না হলে এই স্ক্রিনে ঢুকতে পারবে না
        if (!SupabaseClient.isAdmin(this)) {
            Toast.makeText(this, "অনুমতি নেই", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(colorBg)
        }
        val scroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(30))
        }

        // ---------------- HEADER ----------------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(colorPrimary, colorPrimaryDark))
            setPadding(dp(22), dp(40), dp(22), dp(26))
        }
        val backRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backBtn = TextView(this).apply {
            text = "←"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, dp(14), 0)
            setOnClickListener { finish() }
        }
        val headerTitle = text("পেমেন্ট ভেরিফিকেশন", 18f, Typeface.BOLD, Color.WHITE, Gravity.START)
        backRow.addView(backBtn)
        backRow.addView(headerTitle)
        val headerSub = text(
            "bKash/Nagad Transaction ID যাচাই করে কনফার্ম করুন",
            12f, Typeface.NORMAL, Color.argb(215, 255, 255, 255), Gravity.START
        ).apply { setPadding(dp(34), dp(6), 0, 0) }
        header.addView(backRow)
        header.addView(headerSub)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), 0)
        }

        page.addView(header)
        page.addView(listContainer)

        scroll.addView(page)
        root.addView(scroll)
        setContentView(root)

        loadPendingPayments()
    }

    override fun onResume() {
        super.onResume()
        loadPendingPayments()
    }

    // ------------------------------------------------------------------
    private fun loadPendingPayments() {
        listContainer.removeAllViews()
        listContainer.addView(
            text("লোড হচ্ছে...", 12.5f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER).apply {
                setPadding(0, dp(20), 0, dp(20))
            }
        )

        lifecycleScope.launch {
            val result = SupabaseClient.getPendingVerificationAppointments()
            listContainer.removeAllViews()
            result.onSuccess { rows ->
                if (rows.length() == 0) {
                    listContainer.addView(
                        text("যাচাইয়ের জন্য কোনো পেমেন্ট নেই ✅", 13f, Typeface.NORMAL, colorTextMuted, Gravity.CENTER).apply {
                            setPadding(0, dp(40), 0, dp(40))
                        }
                    )
                } else {
                    for (i in 0 until rows.length()) {
                        val obj = rows.getJSONObject(i)
                        listContainer.addView(paymentCard(
                            appointmentId = obj.optString("id"),
                            patientName = obj.optString("patient_name"),
                            phone = obj.optString("phone"),
                            date = obj.optString("preferred_date"),
                            time = obj.optString("preferred_time"),
                            paymentMethod = obj.optString("payment_method"),
                            fee = obj.optInt("fee", 800),
                            transactionId = obj.optString("transaction_id")
                        ))
                        listContainer.addView(space(dp(12)))
                    }
                }
            }.onFailure {
                listContainer.addView(
                    text("লোড করা যায়নি: ${it.message}", 12.5f, Typeface.NORMAL, Color.parseColor("#D32F2F"), Gravity.CENTER)
                )
            }
        }
    }

    private fun paymentCard(
        appointmentId: String,
        patientName: String,
        phone: String,
        date: String,
        time: String,
        paymentMethod: String,
        fee: Int,
        transactionId: String
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(colorCard, 18f)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            elevation = dp(3).toFloat()
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nameCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameCol.addView(text(patientName.ifEmpty { "নাম নেই" }, 14.5f, Typeface.BOLD, Color.parseColor("#111827"), Gravity.START))
        nameCol.addView(text(phone, 12f, Typeface.NORMAL, colorTextMuted, Gravity.START).apply {
            setPadding(0, dp(2), 0, 0)
        })
        val methodBadge = text(
            if (paymentMethod == "বিকাশ") "📱 $paymentMethod" else "💳 $paymentMethod",
            10.5f, Typeface.BOLD, colorPrimary, Gravity.CENTER
        ).apply {
            background = roundedBg(Color.parseColor("#E4F3F1"), 30f)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        topRow.addView(nameCol)
        topRow.addView(methodBadge)

        val detailsText = text(
            "$date • $time   •   ফি: ৳ $fee",
            12f, Typeface.NORMAL, colorTextMuted, Gravity.START
        ).apply { setPadding(0, dp(10), 0, 0) }

        val trxRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(Color.parseColor("#F1F5F4"), 12f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
        }
        trxRow.addView(text("TrxID: ", 12.5f, Typeface.BOLD, colorTextMuted, Gravity.START))
        trxRow.addView(
            text(transactionId.ifEmpty { "—" }, 13f, Typeface.BOLD, Color.parseColor("#111827"), Gravity.START).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        val rejectBtn = text("বাতিল করুন", 12.5f, Typeface.BOLD, Color.parseColor("#DC2626"), Gravity.CENTER).apply {
            background = roundedBg(Color.parseColor("#FEE2E2"), 12f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener { confirmReject(appointmentId, patientName) }
        }
        val verifyBtn = text("ভেরিফাই করুন ✓", 12.5f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
            background = roundedBg(Color.parseColor("#059669"), 12f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
            setOnClickListener { verifyThisPayment(appointmentId) }
        }
        actionRow.addView(rejectBtn)
        actionRow.addView(verifyBtn)

        card.addView(topRow)
        card.addView(detailsText)
        card.addView(trxRow)
        card.addView(actionRow)
        return card
    }

    // ------------------------------------------------------------------
    private fun verifyThisPayment(appointmentId: String) {
        lifecycleScope.launch {
            val result = SupabaseClient.verifyPayment(appointmentId)
            result.onSuccess {
                Toast.makeText(this@AdminPaymentVerificationActivity, "পেমেন্ট ভেরিফাই হয়েছে", Toast.LENGTH_SHORT).show()
                loadPendingPayments()
            }.onFailure {
                Toast.makeText(this@AdminPaymentVerificationActivity, it.message ?: "ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmReject(appointmentId: String, patientName: String) {
        AlertDialog.Builder(this)
            .setTitle("পেমেন্ট বাতিল করুন")
            .setMessage("$patientName এর অ্যাপয়েন্টমেন্ট বাতিল করতে চান? (ভুল/জাল TrxID এর ক্ষেত্রে)")
            .setPositiveButton("হ্যাঁ, বাতিল করুন") { _, _ ->
                lifecycleScope.launch {
                    val result = SupabaseClient.rejectPayment(appointmentId)
                    result.onSuccess {
                        Toast.makeText(this@AdminPaymentVerificationActivity, "বাতিল করা হয়েছে", Toast.LENGTH_SHORT).show()
                        loadPendingPayments()
                    }.onFailure {
                        Toast.makeText(this@AdminPaymentVerificationActivity, it.message ?: "ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("না", null)
            .show()
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------
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
}
