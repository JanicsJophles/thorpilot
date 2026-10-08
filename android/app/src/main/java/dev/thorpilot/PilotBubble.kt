package dev.thorpilot

import android.graphics.*
import android.graphics.drawable.Drawable

/** Lightweight rounded surface using the native workspace palette. */
class PilotBubble(private val radius: Float, private val selected: Boolean = false) : Drawable() {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val rect = RectF()
    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds); rect.inset(1f, 1f)
        if (rect.isEmpty) return
        body.color = if (selected) 0xff1c1f27.toInt() else 0xff13151b.toInt()
        rim.color = 0xff2a2e38.toInt()
    }
    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        canvas.drawRoundRect(rect, radius, radius, body)
        canvas.drawRoundRect(rect, radius, radius, rim)
    }
    override fun setAlpha(alpha: Int) { body.alpha = alpha; rim.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { body.colorFilter = filter; rim.colorFilter = filter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
