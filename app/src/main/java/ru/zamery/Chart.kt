package ru.zamery

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

/** Линия с заливкой по датам. Ось X — настоящие дни, пропуски видно. У последней точки — плашка со значением. */
@Composable
fun LineChart(points: List<Pair<LocalDate, Double>>, unit: String, modifier: Modifier) {
    val t = LocalTokens.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = t.muted, fontSize = 13.sp)
    val tipStyle = TextStyle(color = t.bg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val tipSmall = TextStyle(color = t.bg, fontSize = 13.sp)

    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val left = 40.dp.toPx()
        val right = 16.dp.toPx()
        val top = 56.dp.toPx()     // место под плашку
        val bottom = 28.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom

        // Целые деления по вертикали, шаг подбираем под размах
        val rawLo = points.minOf { it.second }
        val rawHi = points.maxOf { it.second }
        val span = (rawHi - rawLo).coerceAtLeast(2.0)
        val step = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0).first { span / it <= 4 }
        val lo = floor((rawLo - span * 0.1) / step) * step
        val hi = ceil((rawHi + span * 0.1) / step) * step

        val d0 = points.first().first.toEpochDay()
        val d1 = points.last().first.toEpochDay()
        fun x(d: LocalDate) = if (d1 == d0) left + w / 2 else left + w * (d.toEpochDay() - d0).toFloat() / (d1 - d0)
        fun y(v: Double) = top + h * (1 - ((v - lo) / (hi - lo))).toFloat()

        var v = lo
        while (v <= hi + 1e-6) {
            val yy = y(v)
            drawLine(t.line, Offset(left, yy), Offset(left + w, yy), strokeWidth = 1.dp.toPx())
            val tl = measurer.measure(fmt(v), labelStyle)
            drawText(tl, topLeft = Offset(left - tl.size.width - 8.dp.toPx(), yy - tl.size.height / 2))
            v += step
        }

        // До 5 подписей дат по низу
        val n = if (d1 == d0) 1 else 5
        for (i in 0 until n) {
            val day = if (n == 1) d0 else d0 + (d1 - d0) * i / (n - 1)
            val tl = measurer.measure(dateShort(LocalDate.ofEpochDay(day)), labelStyle)
            val cx = if (n == 1) left + w / 2 else left + w * i / (n - 1)
            val tx = (cx - tl.size.width / 2).coerceIn(left - 8.dp.toPx(), size.width - tl.size.width.toFloat())
            drawText(tl, topLeft = Offset(tx, top + h + 8.dp.toPx()))
        }

        if (points.size > 1) {
            val line = Path()
            points.forEachIndexed { i, (d, value) -> if (i == 0) line.moveTo(x(d), y(value)) else line.lineTo(x(d), y(value)) }
            val area = Path().apply {
                addPath(line)
                lineTo(x(points.last().first), top + h)
                lineTo(x(points.first().first), top + h)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(t.primary.copy(alpha = 0.28f), t.primary.copy(alpha = 0f)), startY = top, endY = top + h))
            drawPath(line, t.primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (points.size <= 40) points.forEach { (d, value) -> drawCircle(t.primary, 4.dp.toPx(), Offset(x(d), y(value))) }

        // Последняя точка: пунктир вниз, кружок и плашка
        val (ld, lv) = points.last()
        val px = x(ld)
        val py = y(lv)
        drawLine(
            t.primary.copy(alpha = 0.6f), Offset(px, py), Offset(px, top + h), strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
        drawCircle(t.surface, 8.dp.toPx(), Offset(px, py))
        drawCircle(t.primary, 6.dp.toPx(), Offset(px, py))

        val l1 = measurer.measure("${fmt(lv)} $unit", tipStyle)
        val l2 = measurer.measure(dateShort(ld), tipSmall)
        val pad = 8.dp.toPx()
        val bw = maxOf(l1.size.width, l2.size.width) + pad * 2
        val bh = l1.size.height + l2.size.height + pad * 1.5f
        val bx = (px - bw / 2).coerceIn(0f, size.width - bw)
        val by = (py - bh - 12.dp.toPx()).coerceAtLeast(0f)
        drawRoundRect(t.text, Offset(bx, by), Size(bw, bh), CornerRadius(8.dp.toPx()))
        drawText(l1, topLeft = Offset(bx + pad, by + pad * 0.6f))
        drawText(l2, topLeft = Offset(bx + pad, by + pad * 0.6f + l1.size.height))
    }
}
