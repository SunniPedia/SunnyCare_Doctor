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

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Activity এর View রেডি হলে অটো ফন্ট Apply হবে
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

    fun loadFont() {
        try {
            val fontFile = File(filesDir, "fonts/SolaimanLipi.ttf")
            if (fontFile.exists()) {
                solaimanLipi = Typeface.createFromFile(fontFile)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun applyFontToAllViews(view: View) {
        if (view is ViewGroup) {
            view.children.forEach { applyFontToAllViews(it) }
        } else if (view is TextView) {
            solaimanLipi?.let {
                // শুধু বাংলা থাকলেই Apply হবে, ইংরেজি ভাঙবে না
                if (view.text.any { c -> c.code in 2432..2559 }) {
                    val oldStyle = view.typeface?.style ?: Typeface.NORMAL
                    view.typeface = Typeface.create(it, oldStyle)
                }
            }
        }
    }
}
