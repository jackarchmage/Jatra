package com.jks.jatrav3

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.view.animation.DecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.core.view.get
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding

// ADD THIS IMPORT
import com.razorpay.PaymentResultListener
import androidx.core.view.size

class Homescreen : AppCompatActivity(), PaymentResultListener {
    private val originalTitles = mutableMapOf<Int, CharSequence>()

    private val defaultTitles = mapOf(
        R.id.nav_home to "Home",
        R.id.nav_design to "Design",
        R.id.nav_source to "Source",
        R.id.nav_hire to "Hire",
        R.id.nav_account to "Account"
    )


    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_homescreen)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val frame = findViewById<FrameLayout>(R.id.frame1)
        val root = findViewById<View>(R.id.main_root)

// apply the selectors programmatically to be safe on older platforms
        bottomNav.itemIconTintList = ContextCompat.getColorStateList(this, R.color.nav_item_color)
        bottomNav.itemTextColor = ContextCompat.getColorStateList(this, R.color.nav_item_color)
//        bottomNav.itemBackground = ContextCompat.getDrawable(this, R.drawable.nav_item_background)



        for (i in 0 until bottomNav.menu.size) {
            val it = bottomNav.menu[i]
                originalTitles[it.itemId] = it.title ?: ""
            }
        bottomNav.post {
            updateMenuTitlesForSelected(bottomNav, bottomNav.selectedItemId)
            // now safe to open initial fragment (only on fresh start)
            if (savedInstanceState == null) {
                openFragmentFromIntent(intent)
            }
        }


// ensure default selection (so selected circle & color show initially)
        bottomNav.selectedItemId = bottomNav.selectedItemId.takeIf { it != 0 } ?: R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment())
                R.id.nav_design -> loadFragment(DesignFragment())
                R.id.nav_source -> loadFragment(MaterialsFragment())
                R.id.nav_hire -> loadFragment(WorkersFragment())
                R.id.nav_account-> loadFragment(ProfileFragment())
            }
            updateLabelVisibilityWithAnimation(bottomNav,item.itemId,animate = true)
//            updateMenuTitlesForSelected(bottomNav, item.itemId)
//            scaleSelectedIcon(bottomNav, item.itemId)
            true
        }
        setupBottomNav(bottomNav)



        val baseMarginPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics
        ).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sysInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = bottomNav.layoutParams as ViewGroup.MarginLayoutParams
            lp.bottomMargin = sysInsets.bottom + baseMarginPx
            bottomNav.layoutParams = lp
            WindowInsetsCompat.CONSUMED
        }
        bottomNav.doOnLayout {
            ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
                val sysInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val navHeight = bottomNav.height
                // ensure content ends above bottom nav + gesture bar
                view.updatePadding(bottom = navHeight + sysInsets.bottom)
                insets
            }
        }
//        ViewCompat.setWindowInsetsAnimationCallback(root,
//            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
//
//                // track last applied bottom padding so we can interpolate
//                private var lastBottom = frame.paddingBottom
//
//                override fun onProgress(
//                    insets: WindowInsetsCompat,
//                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
//                ): WindowInsetsCompat {
//                    // system bottom inset (gesture nav / IME)
//                    val sysBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
//
//                    // if bottom nav height is known, include it; otherwise fallback to stored lastBottom minus sysBottom
//                    val navHeight = bottomNav.height
//                    val targetBottom = navHeight + sysBottom
//
//                    // update padding immediately to follow animation progress
//                    frame.updatePadding(bottom = targetBottom)
//                    lastBottom = targetBottom
//                    return insets
//                }
//
//                // optional: handle start/end if you want to animate something else
//                override fun onStart(
//                    animation: WindowInsetsAnimationCompat,
//                    bounds: WindowInsetsAnimationCompat.BoundsCompat
//                ): WindowInsetsAnimationCompat.BoundsCompat {
//                    return bounds
//                }
//            }
//        )

        // keep bottom nav label visibility correct on layout changes too (e.g rotating device)
        bottomNav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateLabelVisibilityWithAnimation(bottomNav, bottomNav.selectedItemId, animate = false)
            // ensure frame padding remains correct after layout changes
            val rootInsets = ViewCompat.getRootWindowInsets(root)
            val sysBottom = rootInsets?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
            frame.updatePadding(bottom = bottomNav.height + sysBottom)
        }

        // initialize your custom bottom nav label visibility state

    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // update stored intent
        openFragmentFromIntent(intent)
    }

    private fun openFragmentFromIntent(intent: Intent?) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val fragmentToOpen = intent?.getStringExtra("openFragment")

        val (fragment, menuId) = when (fragmentToOpen) {
            "login" -> AuthenticationFragment() to R.id.nav_account
            "design" -> DesignFragment() to R.id.nav_design
            "source" -> MaterialsFragment() to R.id.nav_source
            "hire" -> WorkersFragment() to R.id.nav_hire
            "account" -> ProfileFragment() to R.id.nav_account
            else -> HomeFragment() to R.id.nav_home
        }

        // switch fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame1, fragment)
            .commit()

        // update bottom nav selection
        bottomNav.selectedItemId = menuId
        updateMenuTitlesForSelected(bottomNav, menuId)
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame1, fragment)
            .commit()
    }

    private fun setupBottomNav(bottomNav: BottomNavigationView) {
        // apply initial state after layout
        bottomNav.post {
            updateLabelVisibilityWithAnimation(bottomNav, bottomNav.selectedItemId, animate = false)
        }
    }

    private fun getMaterialLabelId(name: String): Int {
        // look in the Material library package first
        val libPackage = "com.google.android.material"
        val idInLib = resources.getIdentifier(name, "id", libPackage)
        if (idInLib != 0) return idInLib

        // fallback to app package (very unlikely but safe)
        val idInApp = resources.getIdentifier(name, "id", packageName)
        return idInApp // may be 0 -> caller must handle null
    }

    @SuppressLint("RestrictedApi")
    private fun scaleSelectedIcon(bottomNav: BottomNavigationView, selectedItemId: Int) {
        val menuView = bottomNav.getChildAt(0) as? BottomNavigationMenuView ?: return
        for (i in 0 until menuView.childCount) {
            val itemView = menuView.getChildAt(i) as? BottomNavigationItemView ?: continue
            val iconView = itemView.findViewById<View>(com.google.android.material.R.id.icon)
            if (bottomNav.menu[i].itemId == selectedItemId) {
                iconView?.animate()?.scaleX(1.12f)?.scaleY(1.12f)?.setDuration(120)?.start()
            } else {
                iconView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun updateLabelVisibilityWithAnimation(
        bottomNav: BottomNavigationView,
        selectedId: Int,
        animate: Boolean = true
    ) {
        val menuView = bottomNav.getChildAt(0) as? BottomNavigationMenuView ?: return
        val smallLabelId = getMaterialLabelId("smallLabel")
        val largeLabelId = getMaterialLabelId("largeLabel")


        for (i in 0 until menuView.childCount) {
            val itemView = menuView.getChildAt(i) as? BottomNavigationItemView ?: continue

            val smallLabel = if (smallLabelId != 0) itemView.findViewById<TextView?>(smallLabelId) else null
            val largeLabel = if (largeLabelId != 0) itemView.findViewById<TextView?>(largeLabelId) else null

            val isSelected = bottomNav.menu[i].itemId == selectedId

            val isHome = bottomNav.menu[0].itemId == R.id.nav_home

            // Home should always show its labels; others hide when selected
            val showLabels = isHome || !isSelected


//            if (animate) {
//                animateLabelVisibility(smallLabel, !isSelected)
//                animateLabelVisibility(largeLabel, !isSelected)
//            } else {
//                applyLabelVisibilityInstant(smallLabel, !isSelected)
//                applyLabelVisibilityInstant(largeLabel, !isSelected)
//            }
            if (animate) {
                // for selected -> HIDE labels; for unselected -> SHOW labels
//                if (isSelected) {
//                    animateLabelVisibility(smallLabel, show = false)
//                    animateLabelVisibility(largeLabel, show = false)
//                } else {
//                    animateLabelVisibility(smallLabel, show = true)
//                    animateLabelVisibility(largeLabel, show = true)
//                }
                animateLabelVisibility(smallLabel, show = showLabels)
                animateLabelVisibility(largeLabel, show = showLabels)
            } else {
//                if (isSelected) {
//                    applyLabelVisibilityInstant(smallLabel, show = false)
//                    applyLabelVisibilityInstant(largeLabel, show = false)
//                } else {
//                    applyLabelVisibilityInstant(smallLabel, show = true)
//                    applyLabelVisibilityInstant(largeLabel, show = true)
//                }
                applyLabelVisibilityInstant(smallLabel, show = showLabels)
                applyLabelVisibilityInstant(largeLabel, show = showLabels)
            }

        }
    }

    /**
     * Hides the selected menu item's title (sets it to empty string)
     * and restores original titles for other items.
     * Also sets contentDescription on the item view for accessibility.
     */
    @SuppressLint("RestrictedApi")
    private fun updateMenuTitlesForSelected(bottomNav: BottomNavigationView, selectedId: Int) {
        val menuView = bottomNav.getChildAt(0) as? BottomNavigationMenuView ?: return

        for (i in 0 until bottomNav.menu.size) {
            val menuItem = bottomNav.menu[i]
            val orig = defaultTitles[menuItem.itemId] ?: ""

            val isSelected = menuItem.itemId == selectedId
            val isHome = menuItem.itemId == R.id.nav_home

            // Home -> always show title. Others -> hide title when selected.
            menuItem.title = when {
                isHome -> orig
                isSelected -> ""
                else -> orig
            }
//            // selected -> hide title, unselected -> restore default
//            menuItem.title = if (menuItem.itemId == selectedId) "" else orig

            // accessibility: set original text on the item view's contentDescription
            val itemView = menuView.getChildAt(i) as? BottomNavigationItemView
            itemView?.contentDescription = orig
        }
        // force a layout pass so the BottomNavigationView updates immediately
        bottomNav.requestLayout()
        bottomNav.invalidate()
    }


    private fun applyLabelVisibilityInstant(label: TextView?, show: Boolean) {
        label ?: return
        label.visibility = if (show) View.VISIBLE else View.GONE
        label.alpha = if (show) 1f else 0f
        label.scaleX = if (show) 1f else 0.9f
        label.scaleY = if (show) 1f else 0.9f
    }

    private fun animateLabelVisibility(label: TextView?, show: Boolean) {
        label ?: return
        val duration = 160L
        val interpolator = DecelerateInterpolator()

        if (show) {
            label.visibility = View.VISIBLE
            label.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction(null)
                .start()
        } else {
            label.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction { label.visibility = View.GONE }
                .start()
        }
    }

    // ----------------------------
    // Razorpay Payment callbacks
    // ----------------------------

    /**
     * Called by Razorpay SDK when payment succeeds.
     * Forwards the payment ID to UploadFragment if it's present.
     */
    override fun onPaymentSuccess(razorpayPaymentID: String?) {
        try {
            val uploadFragment = supportFragmentManager
                .fragments
                .firstOrNull { it is UploadFragment } as? UploadFragment

            if (uploadFragment != null && razorpayPaymentID != null) {
                uploadFragment.onRazorpayPaymentSuccess(razorpayPaymentID)
            } else {
                Toast.makeText(this, "Payment success: $razorpayPaymentID", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Payment success (handler error): ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPaymentError(code: Int, response: String?) {
        try {
            val uploadFragment = supportFragmentManager
                .fragments
                .firstOrNull { it is UploadFragment } as? UploadFragment

            if (uploadFragment != null) {
                uploadFragment.onRazorpayPaymentError(code, response)
            } else {
                Toast.makeText(this, "Payment failed: $response", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Payment failed (handler error): ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
    override fun onResume() {
        super.onResume()
        // re-apply menu titles in case something changed while the activity was paused
        try {
            val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
            updateMenuTitlesForSelected(bottomNav, bottomNav.selectedItemId)
        } catch (e: Exception) {
            // defensive: ignore if view not ready
            e.printStackTrace()
        }
    }


}
