package com.jks.jatrav3

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat

class WebviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Get the URL
        val url = intent.getStringExtra("TEXT_LINK")
        if (url.isNullOrEmpty()) {
            finish()
            return
        }

        // 2. Configure the Custom Tab
        val builder = CustomTabsIntent.Builder()

        // Set the toolbar color to match your app (e.g., Purple or Black)
        builder.setToolbarColor(ContextCompat.getColor(this, R.color.black))

        // Show the title of the page (Optional)
        builder.setShowTitle(true)

        // Animation for opening/closing (Optional - makes it feel native)
        builder.setStartAnimations(this, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        builder.setExitAnimations(this, android.R.anim.slide_in_left, android.R.anim.slide_out_right)

        // 3. Build and Launch
        val customTabsIntent = builder.build()

        // This launches the browser overlay
        customTabsIntent.launchUrl(this, Uri.parse(url))

        // Since the browser covers the whole screen, we can finish this activity
        // so the user returns to the previous screen when they close the browser.
        finish()
    }
}