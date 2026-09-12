package com.konasl.nagad

import android.app.Activity
import android.app.Application
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.children
import java.io.File

class MyApp : Application() {

    companion object {
        var banglaTypeface: Typeface? = null
        var mixedTypeface: Typeface? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        loadFont()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                
                // DecorView পাওয়ার পর Auto Watcher চালু করো
                activity.window.decorView.post {
                    val root = activity.window.decorView.rootView as ViewGroup
                    applyFontToAllViews(root)
                    watchForNewViews(root) // নতুন Programmatically View Add হলেও ধরবে
                }
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    fun loadFont() {
        try {
            val fontFile = File(filesDir, "fonts/SolaimanLipi.ttf")
            if (fontFile.exists()) {
                banglaTypeface = Typeface.createFromFile(fontFile)
                mixedTypeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val systemFamily = FontFamily.Builder(Font.Builder(Typeface.DEFAULT).build()).build()
                    Typeface.CustomFallbackBuilder(systemFamily)
                        .addCustomFallback(
                            FontFamily.Builder(Font.Builder(banglaTypeface!!).build()).build()
                        ).build()
                } else {
                    banglaTypeface
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // নতুন View Add হলে অটো ধরার জন্য Watcher
    private fun watchForNewViews(viewGroup: ViewGroup) {
        viewGroup.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) {
                applyFontToAllViews(child)
                if (child is ViewGroup) {
                    watchForNewViews(child) // ভেতরে আরও ViewGroup থাকলে সেটাও Watch করো
                }
            }
            override fun onChildViewRemoved(parent: View, child: View) {}
        })
        // আগে থেকে থাকা ViewGroup গুলোতেও Watcher লাগাও
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

                if (isInput || hasBangla) {
                    // EditText এর hint সহ ফন্ট Apply করার গ্যারান্টি
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
