package com.konasl.nagad

import android.app.Activity
import android.app.Application
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import java.io.File

class MyApp : Application() {

    companion object {
        var solaimanLipi: Typeface? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        loadFont()

        // এইটা হলো ম্যাজিক - প্রতিটা Activity কে অটো Track করবে
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Activity এর View লোড হওয়ার পর ফন্ট Apply করো
                activity.window.decorView.post {
                    applyFontToAllViews(activity.window.decorView.rootView)
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

    private fun loadFont() {
        try {
            val fontFile = File(filesDir, "fonts/SolaimanLipi.ttf")
            if (fontFile.exists()) {
                solaimanLipi = Typeface.createFromFile(fontFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // এই ফাংশনটা ডাউনলোড শেষ হলে কল করবে, তাহলে সাথে সাথেই পুরো অ্যাপে ফন্ট চেঞ্জ হবে
    fun refreshFont() {
        loadFont()
    }

    private fun applyFontToAllViews(view: View) {
        if (view is ViewGroup) {
            view.children.forEach { child ->
                applyFontToAllViews(child)
            }
        } else if (view is TextView) {
            // শুধু বাংলায় SolaimanLipi, ইংরেজি আগের মতোই থাকবে
            if (isBangla(view.text.toString())) {
                solaimanLipi?.let {
                    val oldStyle = view.typeface?.style ?: Typeface.NORMAL
                    view.typeface = Typeface.create(it, oldStyle)
                }
            }
        }
    }

    private fun isBangla(text: String): Boolean {
        return text.any { it.code in 2432..2559 }
    }
}
