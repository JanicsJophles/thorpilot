package dev.thorpilot

import android.graphics.*
import android.graphics.drawable.Drawable

/** Locally drawn glass: dark translucent body, silver rim and restrained upper reflection. */
class PilotBubble(private val radius: Float, private val selected: Boolean = false) : Drawable() {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()
    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds); rect.inset(1f, 1f)
        if (rect.isEmpty) return
        body.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            if (selected) intArrayOf(0xf0437771.toInt(), 0xf0204748.toInt(), 0xf0091d26.toInt())
            else intArrayOf(0xf039424d.toInt(), 0xf01a222d.toInt(), 0xf0060c16.toInt()),
            floatArrayOf(0f, .32f, 1f), Shader.TileMode.CLAMP)
        rim.shader = LinearGradient(0f, rect.top, rect.right, rect.bottom,
            intArrayOf(0x99e0f6f2.toInt(), 0x30495b70, 0x457d9a9d), null, Shader.TileMode.CLAMP)
        shine.shader = LinearGradient(0f, rect.top, 0f, rect.top + rect.height() * .35f,
            0x16ffffff, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        clip.reset(); clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
    }
    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        canvas.drawRoundRect(rect, radius, radius, body)
        val save = canvas.save(); canvas.clipPath(clip)
        canvas.drawOval(rect.left - rect.width() * .12f, rect.top - rect.height() * .45f,
            rect.right + rect.width() * .1f, rect.top + rect.height() * .3f, shine)
        canvas.restoreToCount(save)
        canvas.drawRoundRect(rect, radius, radius, rim)
    }
    override fun setAlpha(alpha: Int) { body.alpha = alpha; rim.alpha = alpha; shine.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { body.colorFilter = filter; rim.colorFilter = filter; shine.colorFilter = filter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
