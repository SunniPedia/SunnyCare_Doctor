package com.konasl.nagad

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ============================================================================
 * CRASH REPORTING SYSTEM — একটাই ফাইলে সবকিছু (CrashStore + CrashHandler +
 * NagadApplication + DebugActivity)
 * ============================================================================
 * উদ্দেশ্য: কোনো Activity-তে ঢোকার সময় বা অ্যাপের যেকোনো জায়গায় unhandled crash হলে,
 * Android ডিফল্টভাবে "App has stopped" দেখিয়ে বন্ধ হয়ে যায় — আসল কারণ শুধু Logcat-এ
 * থাকে, যা কম্পিউটার ছাড়া দেখা যায় না। এই সিস্টেম ক্র্যাশ ধরে DebugActivity খুলে দেয়,
 * যেখানে পুরো স্ট্যাক-ট্রেস কপি/শেয়ার করা যায় — কোনো Logcat বা কম্পিউটার ছাড়াই।
 *
 * ⚠️ MANIFEST SETUP (একবারই করতে হবে) — AndroidManifest.xml-এর <application> ট্যাগে:
 *
 * <application
 *     android:name=".NagadApplication"
 *     ... (বাকি সব যা আগে থেকে আছে তাই থাকবে) ... >
 *
 *     <activity
 *         android:name=".DebugActivity"
 *         android:exported="false"
 *         android:theme="@style/Theme.AppCompat.Light.NoActionBar" />
 *
 *     <!-- বাকি সব <activity> ট্যাগ যেমন আগে ছিল তেমনই থাকবে -->
 * </application>
 *
 * যদি ইতিমধ্যে আপনার নিজের কোনো Application সাবক্লাস থাকে, NagadApplication আলাদা করে
 * লাগবে না — শুধু সেই ক্লাসের onCreate()-এ CrashHandler.install(this) লাইনটা যোগ করলেই হবে।
 * ============================================================================
 */

// ============================================================================
// CrashStore — শেষ ক্র্যাশ রিপোর্ট SharedPreferences-এ সেভ/লোড করার হেল্পার
// ============================================================================
// কেন দরকার: uncaughtException() ধরা পড়ার পরপরই অ্যাপ প্রসেস কিল করে দেওয়া হয়
// (স্থিতিশীলতার জন্য জরুরি — করাপ্টেড স্টেটে অ্যাপ চলতে থাকলে আরও সমস্যা হতে পারে)।
// DebugActivity সাধারণত Intent-এর extra থেকেই রিপোর্ট পায়, কিন্তু কোনো কারণে সেটা মিস
// হলে (যেমন OS নিজে থেকে অ্যাপ রিস্টার্ট করে দিলে) SharedPreferences থেকে শেষ ক্র্যাশটা
// আবার দেখানো যায়।
object CrashStore {
    private const val PREFS = "sunnycare_crash_prefs"
    private const val KEY_LAST_CRASH = "last_crash_report"
    private const val KEY_LAST_CRASH_TIME = "last_crash_time"

    fun saveLastCrash(context: Context, report: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_CRASH, report)
            .putLong(KEY_LAST_CRASH_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getLastCrash(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_CRASH, null)

    fun getLastCrashTime(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_CRASH_TIME, 0L)

    fun clearLastCrash(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_LAST_CRASH)
            .remove(KEY_LAST_CRASH_TIME)
            .apply()
    }
}

// ============================================================================
// CrashHandler — পুরো অ্যাপের জন্য গ্লোবাল ক্র্যাশ হ্যান্ডলার
// ============================================================================
// Thread.setDefaultUncaughtExceptionHandler() দিয়ে নিজেকে বসিয়ে দেয়, তাই যেকোনো
// ক্র্যাশ ধরা পড়লেই:
//   ১) সম্পূর্ণ স্ট্যাক-ট্রেস + ডিভাইস/অ্যাপ তথ্য একসাথে সাজিয়ে একটা রিপোর্ট বানায়
//   ২) সেটা SharedPreferences-এ ব্যাকআপ হিসেবে সেভ করে (CrashStore)
//   ৩) DebugActivity খুলে সরাসরি রিপোর্টটা Intent extra দিয়ে পাঠিয়ে দেয়
//   ৪) ক্র্যাশড প্রসেসটা পুরোপুরি বন্ধ করে দেয়, যাতে করাপ্টেড স্টেটে অ্যাপ আর না চলে
//
// ব্যবহার (Application ক্লাসের onCreate()-এ একবারই): CrashHandler.install(this)
class CrashHandler private constructor(
    private val appContext: Context
) : Thread.UncaughtExceptionHandler {

    // সিস্টেমের নিজস্ব ডিফল্ট হ্যান্ডলার সরিয়ে রাখা হয় — যদি আমাদের রিপোর্ট বানানোর সময়ই
    // আরেকটা এরর হয় (rare edge case), তখন অন্তত সিস্টেম ডিফল্ট আচরণে ফলব্যাক করা যায়
    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val report = buildCrashReport(thread, throwable)
            CrashStore.saveLastCrash(appContext, report)

            val intent = Intent(appContext, DebugActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(DebugActivity.EXTRA_CRASH_REPORT, report)
            }
            appContext.startActivity(intent)
        } catch (inner: Throwable) {
            // রিপোর্ট বানাতে/DebugActivity খুলতে গিয়েও সমস্যা হলে, সিস্টেমের ডিফল্ট হ্যান্ডলারকে
            // কাজ করতে দেওয়া হয়, যাতে অন্তত কোনো ক্র্যাশ সম্পূর্ণ অদৃশ্যভাবে সাইলেন্ট না থাকে
            defaultHandler?.uncaughtException(thread, throwable)
        } finally {
            Process.killProcess(Process.myPid())
            System.exit(1)
        }
    }

    /** এক্সসেপশনের পুরো স্ট্যাক-ট্রেস + সময়, অ্যাপ ভার্সন, ডিভাইস মডেল, Android ভার্সন ইত্যাদি জুড়ে একটা পাঠযোগ্য রিপোর্ট বানায় */
    private fun buildCrashReport(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        val timeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.ENGLISH)
        val versionInfo = try {
            val pkgInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                pkgInfo.longVersionCode
            else
                @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
            "${pkgInfo.versionName} ($code)"
        } catch (e: Exception) {
            "N/A"
        }

        return buildString {
            appendLine("========== CRASH REPORT ==========")
            appendLine("সময়: ${timeFormat.format(Date())}")
            appendLine("অ্যাপ ভার্সন: $versionInfo")
            appendLine("প্যাকেজ: ${appContext.packageName}")
            appendLine("ডিভাইস: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android ভার্সন: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("ক্র্যাশ থ্রেড: ${thread.name}")
            appendLine("এক্সসেপশন: ${throwable.javaClass.name}")
            appendLine("মেসেজ: ${throwable.message ?: "(কোনো মেসেজ নেই)"}")
            appendLine("===================================")
            appendLine()
            append(sw.toString())
        }
    }

    companion object {
        /** Application.onCreate() থেকে একবার কল করলেই পুরো অ্যাপের জন্য গ্লোবাল ক্র্যাশ হ্যান্ডলার সেট হয়ে যায় */
        fun install(context: Context) {
            val appContext = context.applicationContext
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(appContext))
        }
    }
}

// ============================================================================
// NagadApplication — অ্যাপ চালু হওয়ার সাথে সাথে গ্লোবাল ক্র্যাশ হ্যান্ডলার ইনস্টল করে
// ============================================================================
class NagadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // এখান থেকে অ্যাপের যেকোনো Activity/থ্রেডে unhandled crash হলেই DebugActivity খুলবে
        CrashHandler.install(this)
    }
}

// ============================================================================
// DebugActivity — ক্র্যাশ হলে যা কারণে হয়েছে সেটা স্ক্রিনে দেখায়, এক ট্যাপে কপি/শেয়ার করা যায়
// ============================================================================
// CrashHandler.install() (NagadApplication-এ কল করা) অ্যাপের যেকোনো জায়গায় unhandled
// exception হলেই এই Activity-টা Intent extra হিসেবে পুরো স্ট্যাক-ট্রেস পাঠিয়ে খুলে দেয়।
// এখান থেকে ইউজার/ডেভেলপার পুরো রিপোর্ট এক ট্যাপে কপি করে ডেভেলপারকে পাঠাতে পারবেন —
// কোনো Logcat বা কম্পিউটার ছাড়াই।
class DebugActivity : AppCompatActivity() {

    private val colorPrimary = Color.parseColor("#0F6C61")
    private val colorPrimaryLight = Color.parseColor("#16897A")
    private val colorPrimaryDark = Color.parseColor("#0A4A42")
    private val colorAccent = Color.parseColor("#F59E0B")
    private val colorDanger = Color.parseColor("#DC2626")
    private val colorBg = Color.parseColor("#F4F7F6")
    private val colorTextMuted = Color.parseColor("#6B7280")
    private val colorDark = Color.parseColor("#111827")
    private val colorCard = Color.WHITE
    private val colorFieldBorder = Color.parseColor("#E7ECEA")

    private lateinit var reportText: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reportText = intent.getStringExtra(EXTRA_CRASH_REPORT)
            ?: CrashStore.getLastCrash(this)
            ?: "কোনো ক্র্যাশ রিপোর্ট পাওয়া যায়নি।"

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F8FBFA"), colorBg)
            )
        }
        val scroll = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(40))
        }

        // ---------------- HEADER ----------------
        val header = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#B91C1C"), colorDanger, Color.parseColor("#7F1D1D"))
            ).apply {
                setCornerRadii(floatArrayOf(0f, 0f, 0f, 0f, dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat()))
            }
            clipToPadding = false
        }
        header.addView(glowCircle(dp(160), Color.argb(24, 255, 255, 255), Gravity.TOP or Gravity.END, dp(-55), dp(-50)))
        header.addView(glowCircle(dp(110), Color.argb(22, Color.red(colorAccent), Color.green(colorAccent), Color.blue(colorAccent)), Gravity.BOTTOM or Gravity.START, dp(-35), dp(-30)))

        val headerInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(44), dp(22), dp(30))
        }
        val warningWrap = ImageView(this).apply {
            setImageDrawable(WarningIconDrawable(Color.WHITE, dp(2.2f)))
            background = roundedBg(Color.argb(46, 255, 255, 255), 20f)
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            val pad = dp(14)
            setPadding(pad, pad, pad, pad)
        }
        val headerTitle = text("অ্যাপ একটি সমস্যায় পড়েছিল", 17f, Typeface.BOLD, Color.WHITE, Gravity.CENTER).apply {
            setPadding(0, dp(14), 0, 0)
        }
        val headerSub = text(
            "নিচে ক্র্যাশের বিস্তারিত কারণ দেখানো হয়েছে — কপি করে ডেভেলপারকে পাঠিয়ে দিন, সমস্যাটা দ্রুত ঠিক করা যাবে",
            11.5f, Typeface.NORMAL, Color.argb(225, 255, 255, 255), Gravity.CENTER
        ).apply {
            setPadding(dp(10), dp(8), dp(10), 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        headerInner.addView(warningWrap)
        headerInner.addView(headerTitle)
        headerInner.addView(headerSub)
        header.addView(headerInner)

        // ---------------- ACTION BUTTONS (কপি / শেয়ার) ----------------
        val actionCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg(colorCard, 20f)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(-26), dp(18), 0)
            }
            elevation = dp(6).toFloat()
            outlineProvider = roundOutline(20)
            clipToOutline = true
        }
        val copyBtn = actionButton(
            "কপি করুন",
            CopyIconDrawable(Color.WHITE, dp(1.6f)),
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(colorPrimaryLight, colorPrimary, colorPrimaryDark)).apply {
                cornerRadius = dp(14).toFloat()
            },
            Color.WHITE
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
            setOnClickListener { copyReportToClipboard() }
        }
        val shareBtn = actionButton(
            "শেয়ার করুন",
            ShareIconDrawable(colorPrimary, dp(1.6f)),
            roundedBgStroke(Color.parseColor("#E4F3F1"), colorFieldBorder, 14f, 1),
            colorPrimary
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) }
            setOnClickListener { shareReport() }
        }
        actionCard.addView(copyBtn)
        actionCard.addView(shareBtn)

        // ---------------- CRASH LOG CARD ----------------
        val logSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(24), dp(18), 0)
            addView(ImageView(this@DebugActivity).apply {
                setImageDrawable(CopyIconDrawable(colorPrimary, dp(1.4f)))
                background = roundedBg(Color.parseColor("#E4F3F1"), 8f)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
                setPadding(dp(6), dp(6), dp(6), dp(6))
            })
            addView(text("ক্র্যাশ লগ (বিস্তারিত)", 14.5f, Typeface.BOLD, colorDark, Gravity.START).apply {
                setPadding(dp(10), 0, 0, 0)
            })
        }

        val logCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBgStroke(Color.parseColor("#0B1220"), colorFieldBorder, 16f, 1)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(18), dp(10), dp(18), 0)
            }
            elevation = dp(1.5f)
        }
        val logTextView = TextView(this).apply {
            text = reportText
            textSize = 11.5f
            setTextColor(Color.parseColor("#D1FAE5"))
            typeface = Typeface.MONOSPACE
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextIsSelectable(true)
            setPadding(0, 0, 0, 0)
        }
        logCard.addView(logTextView)

        val logHint = text(
            "টিপ: উপরের লেখা থেকে অংশবিশেষ সিলেক্ট করেও কপি করা যাবে, অথবা উপরের \"কপি করুন\" বাটনে পুরো রিপোর্ট একসাথে কপি হবে",
            10.5f, Typeface.NORMAL, colorTextMuted, Gravity.START
        ).apply {
            setPadding(dp(18), dp(10), dp(18), 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        }

        // ---------------- RESTART BUTTON ----------------
        val restartWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), 0)
        }
        val restartBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(colorAccent, Color.parseColor("#D97706"))).apply {
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            elevation = dp(3).toFloat()
            outlineProvider = roundOutline(16)
            clipToOutline = true
            setOnClickListener { restartApp() }
        }
        restartBtn.addView(ImageView(this).apply {
            setImageDrawable(RestartIconDrawable(Color.WHITE, dp(1.8f)))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) }
        })
        restartBtn.addView(text("অ্যাপ আবার চালু করুন", 15f, Typeface.BOLD, Color.WHITE, Gravity.CENTER))
        restartWrap.addView(restartBtn)

        page.addView(header)
        page.addView(actionCard)
        page.addView(logSection)
        page.addView(logCard)
        page.addView(logHint)
        page.addView(restartWrap)

        scroll.addView(page)
        root.addView(scroll)
        setContentView(root)
    }

    // ------------------------------------------------------------------
    private fun copyReportToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Crash Report", reportText))
        Toast.makeText(this, "পুরো ক্র্যাশ রিপোর্ট কপি হয়েছে", Toast.LENGTH_SHORT).show()
    }

    private fun shareReport() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "অ্যাপ ক্র্যাশ রিপোর্ট")
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        startActivity(Intent.createChooser(shareIntent, "রিপোর্ট শেয়ার করুন"))
    }

    private fun restartApp() {
        CrashStore.clearLastCrash(this)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(launchIntent)
        }
        finish()
        Process.killProcess(Process.myPid())
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------
    private fun actionButton(label: String, icon: Drawable, bg: GradientDrawable, textColor: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = bg
            setPadding(dp(12), dp(13), dp(12), dp(13))
            isClickable = true
            isFocusable = true
            outlineProvider = roundOutline(14)
            clipToOutline = true
            addView(ImageView(this@DebugActivity).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginEnd = dp(7) }
            })
            addView(text(label, 12.5f, Typeface.BOLD, textColor, Gravity.CENTER))
        }

    private fun text(t: String, sizeSp: Float, style: Int, color: Int, gravity: Int): TextView = TextView(this).apply {
        text = t; textSize = sizeSp; setTypeface(null, style); setTextColor(color); this.gravity = gravity
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun glowCircle(size: Int, color: Int, gravityVal: Int, marginTopOrBottom: Int, marginSideVal: Int): View {
        return View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = gravityVal
                if (gravityVal and Gravity.TOP == Gravity.TOP) topMargin = marginTopOrBottom else bottomMargin = marginTopOrBottom
                if (gravityVal and Gravity.END == Gravity.END) rightMargin = marginSideVal else leftMargin = marginSideVal
            }
        }
    }

    private fun roundOutline(radiusDp: Int): ViewOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, dp(radiusDp).toFloat())
        }
    }

    private fun roundedBg(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(color)
    }

    private fun roundedBgStroke(fillColor: Int, strokeColor: Int, radiusDp: Float, strokeWidthDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(fillColor)
        setStroke(dp(strokeWidthDp), strokeColor)
    }

    // ------------------------------------------------------------------
    // সব আইকনই সম্পূর্ণ ভেক্টর-আঁকা (Canvas/Paint/Path দিয়ে) — কোনো ইমুজি বা ফন্ট-ক্যারেক্টার নয়,
    // তাই যেকোনো ডিভাইস/ফন্টে একইরকম শার্প ও প্রফেশনাল দেখায়
    // ------------------------------------------------------------------

    /** সতর্কতা-ত্রিভুজ + বিস্ময়সূচক চিহ্ন */
    private class WarningIconDrawable(iconColor: Int, private val strokePx: Float) : Drawable() {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = iconColor
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = iconColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return

            val path = Path().apply {
                moveTo(b.left + w * 0.5f, b.top + h * 0.12f)
                lineTo(b.left + w * 0.92f, b.top + h * 0.85f)
                lineTo(b.left + w * 0.08f, b.top + h * 0.85f)
                close()
            }
            canvas.drawPath(path, strokePaint)

            // বিস্ময়সূচক রেখা
            canvas.drawLine(b.left + w * 0.5f, b.top + h * 0.38f, b.left + w * 0.5f, b.top + h * 0.62f, strokePaint)
            // বিস্ময়সূচক বিন্দু
            canvas.drawCircle(b.left + w * 0.5f, b.top + h * 0.72f, strokePx * 0.55f, fillPaint)
        }

        override fun setAlpha(alpha: Int) { strokePaint.alpha = alpha; fillPaint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { strokePaint.colorFilter = colorFilter; fillPaint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /** দুইটা ওভারল্যাপ করা রাউন্ডেড-রেক্ট — "কপি" আইকন */
    private class CopyIconDrawable(iconColor: Int, private val strokePx: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeJoin = Paint.Join.ROUND
            color = iconColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val r = w * 0.12f

            // পেছনের রেক্ট
            canvas.drawRoundRect(b.left + w * 0.06f, b.top + h * 0.06f, b.left + w * 0.68f, b.top + h * 0.68f, r, r, paint)
            // সামনের রেক্ট
            canvas.drawRoundRect(b.left + w * 0.32f, b.top + h * 0.32f, b.left + w * 0.94f, b.top + h * 0.94f, r, r, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /** তিনটা বিন্দু দুই রেখায় সংযুক্ত — "শেয়ার" আইকন */
    private class ShareIconDrawable(iconColor: Int, private val strokePx: Float) : Drawable() {
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            color = iconColor
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = iconColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val dotR = w * 0.11f

            val topX = b.left + w * 0.82f; val topY = b.top + h * 0.18f
            val botX = b.left + w * 0.82f; val botY = b.top + h * 0.82f
            val leftX = b.left + w * 0.18f; val leftY = b.top + h * 0.5f

            canvas.drawLine(leftX, leftY, topX, topY, linePaint)
            canvas.drawLine(leftX, leftY, botX, botY, linePaint)

            canvas.drawCircle(topX, topY, dotR, dotPaint)
            canvas.drawCircle(botX, botY, dotR, dotPaint)
            canvas.drawCircle(leftX, leftY, dotR, dotPaint)
        }

        override fun setAlpha(alpha: Int) { linePaint.alpha = alpha; dotPaint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { linePaint.colorFilter = colorFilter; dotPaint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /** বৃত্তাকার আর্ক + তীরচিহ্ন — "রিস্টার্ট/রিফ্রেশ" আইকন */
    private class RestartIconDrawable(iconColor: Int, private val strokePx: Float) : Drawable() {
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            color = iconColor
        }
        private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = iconColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return

            val pad = w * 0.14f
            val rectF = android.graphics.RectF(b.left + pad, b.top + pad, b.right - pad, b.bottom - pad)
            // প্রায় সম্পূর্ণ বৃত্ত, উপরের ডানদিকে একটু ফাঁক রাখা (তীরচিহ্নের জন্য জায়গা)
            canvas.drawArc(rectF, -30f, 280f, false, arcPaint)

            // তীরচিহ্নের হেড, আর্কের শুরুর বিন্দুতে
            val cx = b.left + w / 2f
            val cy = b.top + h / 2f
            val radius = (rectF.width()) / 2f
            val angleRad = Math.toRadians(-30.0)
            val tipX = (cx + radius * Math.cos(angleRad)).toFloat()
            val tipY = (cy + radius * Math.sin(angleRad)).toFloat()

            val arrowSize = w * 0.16f
            val headPath = Path().apply {
                moveTo(tipX, tipY - arrowSize)
                lineTo(tipX + arrowSize, tipY)
                lineTo(tipX - arrowSize * 0.15f, tipY + arrowSize * 0.35f)
                close()
            }
            canvas.drawPath(headPath, headPaint)
        }

        override fun setAlpha(alpha: Int) { arcPaint.alpha = alpha; headPaint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { arcPaint.colorFilter = colorFilter; headPaint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        const val EXTRA_CRASH_REPORT = "extra_crash_report"
    }
}
