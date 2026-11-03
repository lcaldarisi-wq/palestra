package it.palestra.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.palestra.app.ui.Fmt
import it.palestra.app.ui.theme.Pal
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

data class ChartPoint(val label: String, val value: Double)

private fun niceStep(range: Double): Double {
    if (range <= 0) return 1.0
    val raw = range / 4
    val mag = Math.pow(10.0, floor(Math.log10(raw)))
    val n = raw / mag
    val nice = when {
        n <= 1 -> 1.0
        n <= 2 -> 2.0
        n <= 2.5 -> 2.5
        n <= 5 -> 5.0
        else -> 10.0
    }
    return nice * mag
}

/** Grafico a linea semplice e leggibile, con griglia, etichette e ultimo valore evidenziato. */
@Composable
fun LineChart(points: List<ChartPoint>, modifier: Modifier = Modifier, unit: String = "") {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Pal.Muted, fontSize = 11.sp)
    val valueStyle = TextStyle(color = Pal.Text, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    Canvas(modifier.fillMaxWidth().height(190.dp)) {
        if (points.isEmpty()) return@Canvas
        val minV = points.minOf { it.value }
        val maxV = points.maxOf { it.value }
        val step = niceStep(if (maxV - minV < 1e-6) max(1.0, maxV * 0.2) else maxV - minV)
        val lo = floor((minV - step * 0.5) / step) * step
        val hi = ceil((maxV + step * 0.5) / step) * step
        val left = 40.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 24.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
        fun x(i: Int) = if (points.size == 1) (left + right) / 2 else left + (right - left) * i / (points.size - 1)

        var g = lo
        while (g <= hi + 1e-9) {
            val yy = y(g)
            drawLine(Pal.OutlineSoft, Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
            val t = measurer.measure(Fmt.num(g), labelStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), yy - t.size.height / 2))
            g += step
        }
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.value)) else path.lineTo(x(i), y(p.value)) }
        drawPath(path, Pal.Accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEachIndexed { i, p ->
            val c = Offset(x(i), y(p.value))
            if (i == points.lastIndex) {
                drawCircle(Pal.Accent, 6.dp.toPx(), c)
            } else {
                drawCircle(Pal.Secondary, 4.dp.toPx(), c)
                drawCircle(Pal.Accent, 4.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            }
        }
        // ultimo valore
        val last = points.last()
        val lt = measurer.measure("${Fmt.num(last.value)}$unit", valueStyle)
        val lx = (x(points.lastIndex) - lt.size.width).coerceAtLeast(left)
        drawText(lt, topLeft = Offset(lx, y(last.value) - lt.size.height - 8.dp.toPx()))
        // etichette asse x: prima e ultima
        val first = measurer.measure(points.first().label, labelStyle)
        drawText(first, topLeft = Offset(x(0) - if (points.size == 1) first.size.width / 2f else 0f, bottom + 6.dp.toPx()))
        if (points.size > 1) {
            val lastL = measurer.measure(last.label, labelStyle)
            drawText(lastL, topLeft = Offset(right - lastL.size.width, bottom + 6.dp.toPx()))
        }
    }
}

/** Istogramma verticale (es. allenamenti per settimana). */
@Composable
fun BarChart(points: List<ChartPoint>, modifier: Modifier = Modifier, highlightLast: Boolean = true) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Pal.Muted, fontSize = 11.sp)
    val valueStyle = TextStyle(color = Pal.Text, fontSize = 11.sp)
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        if (points.isEmpty()) return@Canvas
        val maxV = max(1.0, points.maxOf { it.value })
        val top = 18.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val slot = size.width / points.size
        val barW = slot * 0.56f
        points.forEachIndexed { i, p ->
            val h = ((p.value / maxV) * (bottom - top)).toFloat()
            val x = slot * i + (slot - barW) / 2
            val color = if (highlightLast && i == points.lastIndex) Pal.Accent else Pal.Accent.copy(alpha = 0.45f)
            if (p.value > 0) {
                drawRoundRect(color, Offset(x, bottom - h), Size(barW, h), CornerRadius(6.dp.toPx(), 6.dp.toPx()))
            } else {
                drawRoundRect(Pal.Secondary, Offset(x, bottom - 3.dp.toPx()), Size(barW, 3.dp.toPx()), CornerRadius(2f, 2f))
            }
            val v = measurer.measure(Fmt.num(p.value), valueStyle)
            drawText(v, topLeft = Offset(x + barW / 2 - v.size.width / 2, bottom - h - v.size.height - 2.dp.toPx()))
            val l = measurer.measure(p.label, labelStyle)
            drawText(l, topLeft = Offset(x + barW / 2 - l.size.width / 2, bottom + 4.dp.toPx()))
        }
    }
}
