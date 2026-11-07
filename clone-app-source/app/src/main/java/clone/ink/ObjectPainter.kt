package clone.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import clone.data.PageObject
import clone.data.TextProps

/**
 * Draws text boxes and pictures in page points: the caller sets the canvas scale. Used by the editor and by
 * the PDF export, so what is on screen is what is written to the file.
 */
object ObjectPainter {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private fun layoutFor(props: TextProps, widthPt: Float): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = props.colorArgb; textSize = props.sizePt }
        val width = widthPt.toInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(props.text, 0, props.text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
    }

    /** Height in points that the text needs when wrapped to [widthPt]. */
    fun measureTextHeight(props: TextProps, widthPt: Float): Float = layoutFor(props, widthPt).height.toFloat().coerceAtLeast(props.sizePt)

    fun draw(canvas: Canvas, o: PageObject, bitmap: Bitmap?) {
        when (o.kind) {
            "text" -> {
                val props = try { TextProps.fromJson(o.props) } catch (e: Exception) { return }
                if (props.text.isEmpty()) return
                canvas.save()
                canvas.translate(o.x, o.y)
                layoutFor(props, o.w).draw(canvas)
                canvas.restore()
            }
            "image" -> bitmap?.let { canvas.drawBitmap(it, null, RectF(o.x, o.y, o.x + o.w, o.y + o.h), bitmapPaint) }
        }
    }
}
