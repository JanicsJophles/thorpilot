package dev.thorpilot

import android.graphics.*
import android.graphics.drawable.Drawable

/** Shared native control surface. There is no legacy theme fallback. */
class PilotSurface(private val radius: Float = 28f, private val selected: Boolean = false) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val surface = RectF()
    override fun onBoundsChange(bounds: Rect) {
        surface.set(bounds); surface.inset(1f, 1f)
        if (surface.isEmpty) return
        fill.color = if (selected) 0xff1c1f27.toInt() else 0xff13151b.toInt()
        rim.color = if (selected) 0xffffb547.toInt() else 0xff2a2e38.toInt()

    }
    override fun draw(c: Canvas) {
        if (surface.isEmpty) return
        c.drawRoundRect(surface, radius, radius, fill)
        c.drawRoundRect(surface, radius, radius, rim)
    }
    override fun setAlpha(a: Int) { fill.alpha = a; rim.alpha = a; invalidateSelf() }
    override fun setColorFilter(f: ColorFilter?) { fill.colorFilter = f; rim.colorFilter = f; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

