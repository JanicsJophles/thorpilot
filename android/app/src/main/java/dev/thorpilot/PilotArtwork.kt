package dev.thorpilot

import android.graphics.*
import android.graphics.drawable.Drawable

/** Original vector landscape; no launcher wallpaper or third-party art. */
class PilotWallpaper : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scene: Picture? = null
    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onBoundsChange(bounds: Rect) {
        scene = if (bounds.isEmpty) null else Picture().apply {
            val recording = beginRecording(bounds.width(), bounds.height())
            paintScene(recording)
            endRecording()
        }
    }
    override fun draw(canvas: Canvas) {
        val cached = scene ?: return
        val save = if (layerPaint.alpha == 255 && layerPaint.colorFilter == null) canvas.save() else
            canvas.saveLayer(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), layerPaint)
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.drawPicture(cached)
        canvas.restoreToCount(save)
    }
    private fun paintScene(canvas: Canvas) {

        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        paint.alpha = 255
        paint.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xff020609.toInt(), 0xff061922.toInt(), 0xff100d29.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w*.75f, h*.15f, w*.55f, intArrayOf(0x4027e4c5, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w*.1f, h*.9f, w*.55f, intArrayOf(0x503c38ce, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null
        // A quiet aurora on the horizon leaves the content area near-black.
        val aurora = Path().apply {
            moveTo(0f,h*.75f); cubicTo(w*.27f,h*.28f,w*.62f,h*1.12f,w,h*.53f)
            lineTo(w,h*.68f); cubicTo(w*.62f,h*1.15f,w*.27f,h*.4f,0f,h*.88f); close()
        }
        paint.shader = LinearGradient(0f,h*.6f,w,h*.8f,intArrayOf(0x0544bbff,0x242ce6c8,0x306e47ef,0x057e44ef),null,Shader.TileMode.CLAMP)
        canvas.drawPath(aurora,paint);paint.shader=null
        paint.color = 0x7ac9fff2
        for (i in 0..38) {
            val x = ((i * 173 + 43) % 997) / 997f * w
            val y = ((i * 71 + 11) % 503) / 503f * h * .7f
            canvas.drawCircle(x, y, if (i % 4 == 0) 1.7f else .8f, paint)
        }
        fun ridge(color: Int, base: Float, amplitude: Float, shift: Float) {
            val path = Path().apply {
                moveTo(0f,h); lineTo(0f,h*base)
                for (i in 0..14) {
                    val x=w*i/14f
                    val y=h*(base-amplitude*(.5f+.5f*kotlin.math.sin(i*1.3f+shift)))
                    lineTo(x,y)
                }
                lineTo(w,h); close()
            }
            paint.color=color; canvas.drawPath(path,paint)
        }
        ridge(0x57266372, .85f,.22f,0f)
        ridge(0x782c2456, .98f,.26f,1.5f)
        ridge(0xe0020810.toInt(),1.05f,.18f,3f)
    }
    override fun setAlpha(alpha: Int) { layerPaint.alpha=alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { layerPaint.colorFilter=filter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A friendly compass-shaped pilot emblem, drawn for this project. */
class PilotEmblem : Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val cx=bounds.exactCenterX(); val cy=bounds.exactCenterY(); val r=minOf(bounds.width(),bounds.height())*.4f
        paint.alpha=255;paint.shader=LinearGradient(cx-r,cy-r,cx+r,cy+r,0xff81ffe0.toInt(),0xff467aff.toInt(),Shader.TileMode.CLAMP)
        canvas.drawCircle(cx,cy,r,paint);paint.shader=null
        paint.color=0x55ffffff;paint.style=Paint.Style.STROKE;paint.strokeWidth=r*.04f
        canvas.drawCircle(cx,cy,r*.88f,paint);paint.style=Paint.Style.FILL
        paint.color=Color.WHITE
        val arrow=Path().apply {moveTo(cx,cy-r*.6f);lineTo(cx+r*.45f,cy+r*.42f);quadTo(cx,cy+r*.12f,cx-r*.45f,cy+r*.42f);close()}
        canvas.drawPath(arrow,paint)
        paint.color=0xff386788.toInt();canvas.drawCircle(cx,cy+r*.06f,r*.1f,paint)
    }
    override fun setAlpha(alpha:Int) {paint.alpha=alpha}
    override fun setColorFilter(filter:ColorFilter?) {paint.colorFilter=filter}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
