package dev.nekuz.gamemath

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator

open class ChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.brand_soft)
    }
    private val grid = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = context.getColor(R.color.outline)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        color = context.getColor(R.color.text_secondary)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.on_brand)
    }
    private val path = Path()
    private val margin = 12f * density
    private val labelBand = 18f * density
    private val dotRadius = 6f * density
    private var values = DoubleArray(0)
    private var bounds = ChartBounds(0.0, 1.0)
    private var topLabel = ""
    private var bottomLabel = ""
    private var dotX = Float.NaN
    private var dotY = Float.NaN

    fun setValues(next: DoubleArray) {
        values = next
        bounds = ChartMath.bounds(next)
        topLabel = Fmt.number(bounds.max)
        bottomLabel = Fmt.number(bounds.min)
        rebuild()
        invalidate()
    }

    protected fun setDot(fractionX: Double, value: Double) {
        dotX = fractionX.toFloat()
        dotY = ChartMath.fractionY(value, bounds).toFloat()
        invalidate()
    }

    protected fun clearDot() {
        dotX = Float.NaN
        dotY = Float.NaN
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun plotWidth() = (width - 2f * margin).coerceAtLeast(1f)

    private fun plotHeight() = (height - 2f * labelBand).coerceAtLeast(1f)

    private fun pixelX(fraction: Float) = margin + fraction * plotWidth()

    private fun pixelY(fraction: Float) = height - labelBand - fraction * plotHeight()

    private fun rebuild() {
        path.reset()
        val count = values.size
        if (count == 0 || width <= 0 || height <= 0) return
        val stride = ChartMath.stride(count, width)
        var index = 0
        var first = true
        while (index < count) {
            addPoint(index, count, first)
            first = false
            index += stride
        }
        if ((count - 1) % stride != 0) addPoint(count - 1, count, first)
    }

    private fun addPoint(index: Int, count: Int, first: Boolean) {
        val x = pixelX(ChartMath.fractionX(index, count).toFloat())
        val y = pixelY(ChartMath.fractionY(values[index], bounds).toFloat())
        if (first) path.moveTo(x, y) else path.lineTo(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val top = pixelY(1f)
        val bottom = pixelY(0f)
        canvas.drawLine(margin, top, width - margin, top, grid)
        canvas.drawLine(margin, bottom, width - margin, bottom, grid)
        canvas.drawText(topLabel, margin, top - 4f * density, label)
        canvas.drawText(bottomLabel, margin, bottom + 14f * density, label)
        if (values.size == 1) {
            canvas.drawCircle(pixelX(0.5f), pixelY(ChartMath.fractionY(values[0], bounds).toFloat()), dotRadius, line)
        } else {
            canvas.drawPath(path, line)
        }
        if (!dotX.isNaN() && !dotY.isNaN()) canvas.drawCircle(pixelX(dotX), pixelY(dotY), dotRadius, dotPaint)
    }
}

class EasingChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ChartView(context, attrs) {
    private var easing = Easing.LINEAR
    private var wanted = false
    private var animator: ValueAnimator? = null

    fun setEasing(next: Easing) {
        easing = next
        setValues(next.samples(SAMPLES))
    }

    fun setRunning(running: Boolean) {
        wanted = running
        sync()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        sync()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        sync()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sync()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun sync() {
        if (wanted && isAttachedToWindow && windowVisibility == VISIBLE && isShown) start() else stop()
    }

    private fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = LOOP_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val progress = (it.animatedFraction / TRAVEL_SHARE).toDouble().coerceIn(0.0, 1.0)
                setDot(progress, easing.eval(progress))
            }
            start()
        }
    }

    private fun stop() {
        val running = animator ?: return
        running.removeAllUpdateListeners()
        running.cancel()
        animator = null
        clearDot()
    }

    private companion object {
        const val SAMPLES = 256
        const val LOOP_MS = 2000L
        const val TRAVEL_SHARE = 0.8f
    }
}
