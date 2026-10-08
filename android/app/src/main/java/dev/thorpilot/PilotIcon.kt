package dev.thorpilot

import android.graphics.*
import android.graphics.drawable.Drawable

/** Offline vector action icons for the native companion. */
class PilotIcon(private val kind:String): Drawable() {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scene by lazy {
        Picture().apply {
            val recording = beginRecording(100, 100)
            paintScene(recording)
            endRecording()
        }
    }
    override fun draw(c: Canvas) {
        if (bounds.isEmpty) return
        val save = if (layerPaint.alpha == 255 && layerPaint.colorFilter == null) c.save() else
            c.saveLayer(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), layerPaint)
        c.translate(bounds.left.toFloat(), bounds.top.toFloat())
        c.scale(bounds.width() / 100f, bounds.height() / 100f)
        c.drawPicture(scene)
        c.restoreToCount(save)
    }
    private fun paintScene(c: Canvas) {
        val save=c.save()
        val colors = intArrayOf(0xffffb547.toInt(), 0xff13151b.toInt())
        p.style=Paint.Style.FILL; p.color=0xff1c1f27.toInt()
        c.drawRoundRect(4f,3f,96f,94f,18f,18f,p)
        p.style=Paint.Style.STROKE; p.strokeWidth=4f; p.strokeCap=Paint.Cap.ROUND; p.strokeJoin=Paint.Join.ROUND; p.color=0xffffb547.toInt()
        when(kind){
            "chat"->{
                val bubble=Path().apply{moveTo(31f,25f);lineTo(70f,25f);quadTo(79f,25f,79f,34f);lineTo(79f,57f);quadTo(79f,66f,70f,66f);lineTo(47f,66f);lineTo(31f,77f);lineTo(33f,66f);quadTo(23f,66f,23f,57f);lineTo(23f,34f);quadTo(23f,25f,31f,25f)};c.drawPath(bubble,p)
                p.style=Paint.Style.FILL;for(x in listOf(38f,51f,64f))c.drawCircle(x,46f,3.3f,p)
            }
            "sync"->{
                val upper=Path().apply{moveTo(27f,40f);cubicTo(31f,22f,61f,21f,72f,36f);lineTo(73f,26f);moveTo(72f,36f);lineTo(61f,35f)}
                val lower=Path().apply{moveTo(73f,59f);cubicTo(69f,77f,39f,78f,28f,63f);lineTo(27f,73f);moveTo(28f,63f);lineTo(39f,64f)}
                c.drawPath(upper,p);c.drawPath(lower,p)
            }
            "device"->{
                c.drawRoundRect(25f,20f,75f,48f,6f,6f,p);c.drawRoundRect(25f,54f,75f,79f,6f,6f,p)
                p.strokeWidth=3f;c.drawLine(33f,65f,41f,65f,p);c.drawLine(37f,61f,37f,69f,p)
                p.style=Paint.Style.FILL;c.drawCircle(64f,63f,2f,p);c.drawCircle(59f,68f,2f,p)
            }
            "requests"->{
                c.drawLine(50f,23f,50f,55f,p);c.drawLine(38f,43f,50f,55f,p);c.drawLine(62f,43f,50f,55f,p)
                val tray=Path().apply{moveTo(26f,54f);lineTo(26f,69f);quadTo(26f,76f,33f,76f);lineTo(67f,76f);quadTo(74f,76f,74f,69f);lineTo(74f,54f)};c.drawPath(tray,p)
            }
            else->{
                for(y in listOf(31f,49f,67f))c.drawLine(25f,y,75f,y,p)
                p.style=Paint.Style.FILL;listOf(39f to 31f,62f to 49f,44f to 67f).forEach{(x,y)->p.color=0xfffaffff.toInt();c.drawCircle(x,y,7f,p);p.color=colors[1];c.drawCircle(x,y,3f,p)}
            }
        }
        p.style=Paint.Style.FILL;p.alpha=255;p.shader=null;c.restoreToCount(save)
    }
    override fun setAlpha(a:Int){layerPaint.alpha=a;invalidateSelf()}
    override fun setColorFilter(f:ColorFilter?){layerPaint.colorFilter=f;invalidateSelf()}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

