package com.jks.jatrav3.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import kotlin.math.roundToInt

class OnboardingDots @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var count = 0
    private var selected = 0
    private val gap = dp(6)
    private val height = dp(6)
    private val widthInactive = dp(6)
    private val widthActive = dp(18)

    init {
        orientation = HORIZONTAL
    }

    fun attach(count: Int) {
        this.count = count
        removeAllViews()
        repeat(count) { index ->
            addView(makeDot(index == selected))
            if (index < count - 1) addView(space())
        }
        refresh()
    }

    fun select(index: Int) {
        if (index == selected) return
        selected = index
        refresh()
    }

    private fun refresh() {
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val isDot = v.tag == "dot"
            if (!isDot) continue
            val idx = i / 2 // because spaces are between
            val active = idx == selected
            v.background = pill(active)
            v.updateLayoutParams<LayoutParams> {
                width = if (active) widthActive else widthInactive
                height = this@OnboardingDots.height
            }
        }
    }

    private fun makeDot(active: Boolean): View = View(context).apply {
        tag = "dot"
        background = pill(active)
        layoutParams = LayoutParams(
            if (active) widthActive else widthInactive, height
        )
    }

    private fun space(): View = View(context).apply {
        layoutParams = LayoutParams(gap, height)
    }

    private fun pill(active: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = height / 2f
        setColor(if (active) 0xFF333333.toInt() else 0xFFBDBDBD.toInt()) // tweak to theme
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}