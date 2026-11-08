package com.example.ui.leanback

import android.view.View
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridView

object LeanbackUtils {

    /**
     * Configures a Leanback VerticalGridView for smooth Android TV scrolling,
     * consistent 35% keyline alignment, and focus retention.
     */
    fun setupVerticalGrid(grid: VerticalGridView, numColumns: Int = 1) {
        grid.setNumColumns(numColumns)
        grid.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE)
        grid.setWindowAlignmentOffsetPercent(35f)
        grid.setItemAlignmentOffsetPercent(0f)
        grid.setFocusScrollStrategy(BaseGridView.FOCUS_SCROLL_ALIGNED)
        grid.clipToPadding = false
        grid.clipChildren = false
        grid.setHasFixedSize(true)
    }

    /**
     * Applies the official Leanback card focus scale and elevation effect.
     */
    fun applyFocusHighlight(view: View, scale: Float = 1.025f, elevationDp: Float = 8f) {
        val density = view.resources.displayMetrics.density
        val elevationPx = elevationDp * density

        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .translationZ(elevationPx)
                    .setDuration(140)
                    .start()
            } else {
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(140)
                    .start()
            }
        }
    }
}
