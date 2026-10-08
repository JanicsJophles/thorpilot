package dev.thorpilot

import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewTreeObserver

/** A static, inset glass rim: no blur, animation loop or layout changes. */
class PilotFocusRing(private val density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        rect.inset(5f * density, 5f * density)
    }
    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        val radius = minOf(22f * density, rect.height() / 2f)
        paint.color = 0x50ffb547
        paint.strokeWidth = 9f * density
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.color = 0xffffb547.toInt()
        paint.strokeWidth = 3f * density
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.color = 0xffffe2af.toInt()
        paint.strokeWidth = 1f * density
        canvas.drawRoundRect(rect, radius, radius, paint)
    }
    override fun setAlpha(alpha: Int) { /* Focus contrast is fixed. */ }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Tracks focus for dynamic child controls while preserving their own foregrounds. */
object PilotFocus {
    fun install(root: View) {
        var focused: View? = null
        var original: Drawable? = null
        var decoration: Drawable? = null
        var observer: ViewTreeObserver? = null
        fun clear() {
            focused?.let { if (it.foreground === decoration) it.foreground = original }
            focused = null; original = null; decoration = null
        }
        fun update(view: View?) {
            clear()
            if (view == null || !view.isEnabled || !(view.isClickable || view is android.widget.EditText)) return
            focused = view
            original = view.foreground
            val ring = PilotFocusRing(view.resources.displayMetrics.density)
            decoration = original?.let { LayerDrawable(arrayOf(it, ring)) } ?: ring
            view.foreground = decoration
        }
        val listener = ViewTreeObserver.OnGlobalFocusChangeListener { _, next -> update(next) }
        fun attach() {
            observer = root.viewTreeObserver.also { it.addOnGlobalFocusChangeListener(listener) }
            update(root.findFocus())
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { attach() }
            override fun onViewDetachedFromWindow(view: View) {
                observer?.takeIf { it.isAlive }?.removeOnGlobalFocusChangeListener(listener)
                observer = null
                clear()
            }
        })
        if (root.isAttachedToWindow) attach()
    }
}
