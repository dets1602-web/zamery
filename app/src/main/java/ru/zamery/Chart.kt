package ru.zamery

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Линейный график по датам. Ось X — настоящие дни, поэтому пропуски в замерах видно. */
@Composable
fun LineChart(points: List<Pair<LocalDate, Double>>, modifier: Modifier) {
    val t = LocalTokens.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = t.muted, fontSize = 13.sp)

    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val left = 48.dp.toPx()
        val right = 12.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 28.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom

        var lo = points.minOf { it.second }
        var hi = points.maxOf { it.second }
        if (hi - lo < 1.0) { val mid = (hi + lo) / 2; lo = mid - 1; hi = mid + 1 }
        val pad = (hi - lo) * 0.12
        lo -= pad; hi += pad

        val d0 = points.first().first.toEpochDay()
        val d1 = points.last().first.toEpochDay()
        fun x(d: LocalDate) = if (d1 == d0) left + w / 2 else left + w * (d.toEpochDay() - d0).toFloat() / (d1 - d0)
        fun y(v: Double) = top + h * (1 - ((v - lo) / (hi - lo))).toFloat()

        // Сетка и подписи значений
        for (i in 0..3) {
            val v = lo + (hi - lo) * i / 3
            val yy = y(v)
            drawLine(t.line, Offset(left, yy), Offset(left + w, yy), strokeWidth = 1.dp.toPx())
            val tl = measurer.measure(fmt(v), labelStyle)
            drawText(tl, topLeft = Offset(left - tl.size.width - 6.dp.toPx(), yy - tl.size.height / 2))
        }

        // Подписи дат: первая и последняя
        val first = measurer.measure(dateShort(points.first().first), labelStyle)
        drawText(first, topLeft = Offset(left, top + h + 6.dp.toPx()))
        if (d1 != d0) {
            val last = measurer.measure(dateShort(points.last().first), labelStyle)
            drawText(last, topLeft = Offset(left + w - last.size.width, top + h + 6.dp.toPx()))
        }

        // Линия
        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(x(d), y(v)) else path.lineTo(x(d), y(v)) }
            drawPath(path, t.primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Точки (если замеров немного — все, иначе только последняя)
        if (points.size <= 40) points.forEach { (d, v) -> drawCircle(t.primary, 4.dp.toPx(), Offset(x(d), y(v))) }
        val (ld, lv) = points.last()
        drawCircle(t.surface, 7.dp.toPx(), Offset(x(ld), y(lv)))
        drawCircle(t.primary, 5.dp.toPx(), Offset(x(ld), y(lv)))
    }
}
