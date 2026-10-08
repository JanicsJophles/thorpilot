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
        fill.shader = LinearGradient(surface.left, surface.top, surface.right, surface.bottom,
            if (selected) intArrayOf(0xf0524940.toInt(), 0xf027292f.toInt(), 0xfa13151b.toInt())
            else intArrayOf(0xf0333842.toInt(), 0xf01b1e26.toInt(), 0xfa101218.toInt()),
            floatArrayOf(0f, .38f, 1f), Shader.TileMode.CLAMP)
        rim.shader = LinearGradient(surface.left, surface.top, surface.right, surface.bottom,
            if (selected) intArrayOf(0xffffe5bc.toInt(), 0xffd69d52.toInt(), 0x88726960.toInt())
            else intArrayOf(0x99e5eaf2.toInt(), 0x304c5361, 0x66727b8c), null, Shader.TileMode.CLAMP)

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

