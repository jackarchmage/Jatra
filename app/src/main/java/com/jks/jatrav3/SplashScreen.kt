package com.jks.jatrav3

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import android.animation.AnimatorListenerAdapter
import android.net.Uri
import android.widget.VideoView
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@SuppressLint("CustomSplashScreen")
class SplashScreen : AppCompatActivity() {

    // If you prefer a fixed timeout instead of waiting for animation end, set this to a positive value (ms).
    // If 0L, navigation will happen when the animation ends.
    private val fallbackDelayMs: Long = 13000L
    private var player: ExoPlayer? = null


    @SuppressLint("ClickableViewAccessibility")
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash_screen)

        // Keep inset padding so content doesn't collide with status/nav bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val logo = findViewById<ImageView>(R.id.logoImage)
        val bg = findViewById<ImageView>(R.id.bgImage)
        val playerView = findViewById<androidx.media3.ui.PlayerView>(R.id.playerView)
        playerView.controllerAutoShow = false   // prevent auto-show
        playerView.hideController()             // hide immediately
        playerView.setOnTouchListener { _, _ -> true } // disables showing controller on tap
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM

        initPlayer(playerView)


        // Wait until views are measured so we can set translation correctly
//        findViewById<android.view.View>(R.id.main).doOnPreDraw {
//            startEntranceAnimation(logo, bg)
//        }
    }

    @SuppressLint("Recycle")
    private fun startEntranceAnimation(logo: ImageView, bg: ImageView) {
        val screenHeight = resources.displayMetrics.heightPixels
        // Prefer real measured bg height; fall back to ~65% of screen if not measured
        val measuredBgHeight = if (bg.height > 0) bg.height else (screenHeight * 0.65).toInt()

        // Place bg off-screen (below)
        bg.translationY = measuredBgHeight.toFloat()

        // ensure logo initial state: invisible and normal scale
        logo.alpha = 0f
        logo.translationY = 0f
        logo.scaleX = 1f
        logo.scaleY = 1f
        // Ensure logo starts at natural position


        logo.animate()
            .alpha(1f)
            .setDuration(500L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animation parameters (tweak duration & logoShift to taste)
        val duration = 2000L
        val logoShift = - (measuredBgHeight * 0.26f) // negative = move logo up
        val interpolator = AccelerateDecelerateInterpolator()

        // Animate background up into view
        val bgAnim = bg.animate()
            .translationY(0f)
            .setDuration(duration)
            .setInterpolator(interpolator)
//
//        // Animate logo up (pushed) and slightly scale for a nicer finish
//        logo.animate()
//            .translationY(logoShift)
//            .scaleX(0.995f)
//            .scaleY(0.995f)
//            .setDuration(duration)
//            .setInterpolator(interpolator)
//            .start()

        // When bg animation ends, navigate to Onboarding (or fallback timer)
        bgAnim.withEndAction {
            if (fallbackDelayMs > 0L) {
                bg.postDelayed({ goToOnboarding() }, fallbackDelayMs)
            } else {
                goToOnboarding()
            }
        }.start()


    }

    private fun goToOnboarding() {
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }
    private fun initPlayer(playerView: PlayerView) {
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo

            // Load video from raw folder
            val uri = "android.resource://com.jks.jatrav3/${R.raw.logovideo}".toUri()
            val mediaItem = MediaItem.fromUri(uri)

            exo.setMediaItem(mediaItem)
            exo.prepare()
            exo.playWhenReady = true
            exo.seekTo(0)


            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        goToOnboarding()
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    goToOnboarding()
                }
            })
        }

        // Safety fallback
        Handler(Looper.getMainLooper()).postDelayed({
            if (player?.playbackState != Player.STATE_ENDED) {
                goToOnboarding()
            }
        }, fallbackDelayMs)
    }
}
