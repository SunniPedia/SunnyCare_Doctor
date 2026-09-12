package com.konasl.nagad

import android.app.Activity
import android.app.Application
import android.graphics.Typeface
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
                activity.window.decorView.post {
                    val root = activity.window.decorView.rootView as ViewGroup
                    applyFontToAllViews(root)
                    watchForNewViews(root)
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
                // মিক্সড ফন্ট = SolaimanLipi, কিন্তু ইংরেজি না থাকলে আমরা Apply ই করবো না
                // তাই ইংরেজি আগের মতোই সুন্দর থাকবে
                mixedTypeface = banglaTypeface
            }
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
