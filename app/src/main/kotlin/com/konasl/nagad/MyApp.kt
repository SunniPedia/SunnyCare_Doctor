package com.konasl.nagad

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children

class MyApp : Application() {

    companion object {
        var banglaTypeface: Typeface? = null
        var mixedTypeface: Typeface? = null
            private set
    }

    // FIX: এই মুহূর্তে কোন Activity রিজিউমড (স্ক্রিনে দৃশ্যমান) আছে তা রাখা হয়,
    // যাতে DoctorAlertService থেকে ব্রডকাস্ট এলে সেই Activity-র উপরে কাস্টম
    // "ডাক্তার মেসেজ পাঠিয়েছেন" ডায়ালগ দেখানো যায় — অ্যাপ যেই স্ক্রিনেই থাকুক না কেন।
    private var resumedActivity: Activity? = null

    // FIX: DoctorAlertService মেসেজ পেলে এই ব্রডকাস্ট পাঠায়। অ্যাপ ফোরগ্রাউন্ডে
    // থাকলে (resumedActivity != null) এখানে ধরে কাস্টম ডায়ালগ দেখানো হয়;
    // ব্যাকগ্রাউন্ডে থাকলে সার্ভিস নিজেই সিস্টেম নোটিফিকেশন দেখিয়ে দেয়, তখন
    // এই রিসিভার কিছু করে না (কারণ অ্যাপ প্রসেস তখনো বেঁচে থাকলে receiver
    // এটা পাবে ঠিকই, কিন্তু resumedActivity null থাকায় দেখানো হয় না)।
    private val doctorMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val conversationId = intent.getStringExtra(DoctorAlertService.EXTRA_CONVERSATION_ID)
            val appointmentId = intent.getStringExtra(DoctorAlertService.EXTRA_APPOINTMENT_ID).orEmpty()
            val preview = intent.getStringExtra(DoctorAlertService.EXTRA_PREVIEW) ?: "নতুন মেসেজ এসেছে"

            if (conversationId.isNullOrBlank()) return

            // FIX: ইউজার যদি ইতিমধ্যে এই একই কনভারসেশনের ChatActivity তে থাকে,
            // তাহলে আলাদা করে পপ-আপ ডায়ালগ দেখানোর দরকার নেই — সে তো মেসেজটা
            // চ্যাটেই রিয়েলটাইমে দেখতে পাচ্ছে।
            if (ChatActivity.currentOpenConversationId == conversationId) return

            val activity = resumedActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                DoctorAlertDialogHelper.show(activity, preview, appointmentId)
            }
        }
    }

    override fun onCreate() {
        CrashHandler.install(this)
        super.onCreate()
        loadFont()
        registerDoctorMessageReceiver()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activity.window.decorView.post {
                    val root = activity.window.decorView.rootView as ViewGroup
                    applyFontToAllViews(root)
                    watchForNewViews(root)
                }
            }

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                resumedActivity = activity
                AppForegroundState.isAppInForeground = true
                // FIX: ইউজার লগইন করা রোগী হলে (এডমিন/ডাক্তার নয়) ডাক্তারের
                // মেসেজ শোনার জন্য ব্যাকগ্রাউন্ড-সক্ষম সার্ভিসটা চালু করা হয়।
                // ইতিমধ্যে চলমান থাকলে এই কল কোনো ক্ষতি করে না (onStartCommand
                // আবার কল হবে, socket ইতিমধ্যে থাকলে নতুন করে কানেক্ট হবে না)।
                maybeStartDoctorAlertService(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (resumedActivity == activity) {
                    resumedActivity = null
                }
                AppForegroundState.isAppInForeground = false
            }

            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun registerDoctorMessageReceiver() {
        val filter = IntentFilter(DoctorAlertService.ACTION_DOCTOR_MESSAGE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(doctorMessageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(doctorMessageReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun maybeStartDoctorAlertService(context: Context) {
        try {
            // এডমিন/ডাক্তার নিজেই এই এলার্ট পাওয়ার দরকার নেই — এটা শুধু রোগীর জন্য।
            if (!SupabaseClient.isLoggedIn(context) || SupabaseClient.isAdmin(context)) {
                return
            }

            val patientId = SupabaseClient.getPatientId(context)
            if (patientId.isNullOrBlank()) return

            val intent = Intent(context, DoctorAlertService::class.java).apply {
                putExtra(DoctorAlertService.EXTRA_PATIENT_ID, patientId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * আগে এই ফাংশন internal storage-এ ডাউনলোড করে রাখা ফন্ট ফাইল (filesDir/fonts/SolaimanLipi.ttf)
     * থেকে টাইপফেস লোড করত। এখন ফন্টটা সরাসরি প্রজেক্টের res/font/solaimanlipi.ttf রিসোর্স হিসেবে
     * বান্ডেল করা আছে, তাই ResourcesCompat.getFont() দিয়ে সরাসরি সেখান থেকেই লোড হয় —
     * কোনো ডাউনলোড, ফাইল-চেক বা ইন্টারনেট পারমিশনের প্রয়োজন নেই।
     */
    fun loadFont() {
        try {
            banglaTypeface = ResourcesCompat.getFont(this, R.font.solaimanlipi)
            // মিক্সড ফন্ট = SolaimanLipi, কিন্তু ইংরেজি না থাকলে আমরা Apply ই করবো না
            // তাই ইংরেজি আগের মতোই সুন্দর থাকবে
            mixedTypeface = banglaTypeface
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun watchForNewViews(viewGroup: ViewGroup) {
        viewGroup.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) {
                applyFontToAllViews(child)
                if (child is ViewGroup) watchForNewViews(child)
            }
            override fun onChildViewRemoved(parent: View, child: View) {}
        })
        viewGroup.children.forEach { child ->
            if (child is ViewGroup) watchForNewViews(child)
        }
    }

    private fun applyFontToAllViews(view: View) {
        if (view is TextView) {
            mixedTypeface?.let { tf ->
                val isInput = view is EditText
                val hasBangla = view.text.any { c -> c.code in 2432..2559 } ||
                        view.hint?.any { c -> c.code in 2432..2559 } == true

                // EditText হলে সবসময়, TextView হলে শুধু বাংলা থাকলে
                if (isInput || hasBangla) {
                    if (view.typeface != tf) {
                        val oldStyle = view.typeface?.style ?: Typeface.NORMAL
                        view.typeface = Typeface.create(tf, oldStyle)
                    }
                }
            }
        }
        if (view is ViewGroup) {
            view.children.forEach { applyFontToAllViews(it) }
        }
    }
}
