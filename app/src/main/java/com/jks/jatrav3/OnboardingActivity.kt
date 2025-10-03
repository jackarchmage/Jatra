package com.jks.jatrav3

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.core.content.edit
import androidx.core.view.marginBottom

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: Button
    private lateinit var tabLayout: TabLayout
    private lateinit var prefs: SharedPreferences
    private lateinit var tvExtra: TextView


    private val pages = listOf(
        OnboardPage.OnboardPage(
            imageRes = R.drawable.onboarding1bground,
            title = "Bring Your Ideas to\nLife with AR",
            indicatorRes = R.drawable.firststep
        ),
        OnboardPage.OnboardPage(
            imageRes = R.drawable.onboarding2bground,
            title = "Preview 3D Models\nin Your Space",
            indicatorRes = R.drawable.secondstep
        ),
        OnboardPage.OnboardPage(
            imageRes = R.drawable.onboarding3bground,
            title = "Customize & Share\nwith Friends",
            indicatorRes = R.drawable.thirdstep
        ),
        OnboardPage.OnboardPage(
            imageRes = R.drawable.onboarding4bground,
            title = "Ready to build\nsomething amazing?",
            indicatorRes = R.drawable.fourthstep
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
//        if (prefs.getBoolean("onboarding_complete", false)) {
//            startHomeAndFinish()
//            return
//        }

        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        btnNext = findViewById(R.id.btnNext)
        tabLayout = findViewById(R.id.tabDots)
        tvExtra = findViewById(R.id.tv_create)

        val adapter = OnboardAdapter(pages)
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 1

        // TabLayout dots
        TabLayoutMediator(tabLayout, viewPager) { tab, _ ->
            // leave tabs empty; we'll style the tabs via tabIndicatorHeight = 0 and custom tab view if needed
        }.attach()

        // update button label depending on current page
        fun updateButton(pos: Int) {
            btnNext.text = if (pos == pages.lastIndex) "GET STARTED" else "NEXT"
//            tvExtra.visibility = if (pos == pages.lastIndex) View.VISIBLE else View.GONE
            // set bottom margin of tvExtra to 12dp (adjust parent-specific cast if needed)
//            val params = tvExtra.layoutParams as? ViewGroup.MarginLayoutParams
//            val params1 = tvExtra.layoutParams as? ViewGroup.MarginLayoutParams
//            params?.bottomMargin = dpToPx(120, this)
//            params1?.bottomMargin = dpToPx(0, this)
//            tvExtra.layoutParams = params
//            if (pos==pages.lastIndex)
//            {
//                tvExtra.layoutParams = params
//                btnNext.layoutParams = params1
//            }
        }
        tvExtra.setOnClickListener {
            startActivity(Intent(this, Signup::class.java))
            finish()
        }

        updateButton(0)

        btnNext.setOnClickListener {
            val current = viewPager.currentItem
            if (current < pages.lastIndex) {
                viewPager.currentItem = current + 1
            } else {
                // finish onboarding
                prefs.edit { putBoolean("onboarding_complete", true) }
                startHomeAndFinish()
            }
        }

        // listen for page changes
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateButton(position)
            }
        })
    }
    private fun dpToPx(dp: Int, context: Context): Int =
        (dp * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun startHomeAndFinish() {
        startActivity(Intent(this, Homescreen::class.java))
        finish()
    }

    override fun onBackPressed() {
        if (::viewPager.isInitialized && viewPager.currentItem > 0) {
            viewPager.currentItem = viewPager.currentItem - 1
        } else {
            super.onBackPressed()
        }
    }
}
