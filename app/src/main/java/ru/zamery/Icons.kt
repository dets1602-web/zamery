package ru.zamery

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Линейные иконки из набора ChatGPT (24×24, обводка 1,8). Цвет задаётся через tint. */
object Ic {
    private fun icon(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { p ->
            b.addPath(
                pathData = addPathNodes(p),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val back = icon("back", "M19 12H5m7-7-7 7 7 7")
    val next = icon("next", "m9 5 7 7-7 7")
    val calendar = icon("calendar", "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M7 3v5M17 3v5M3 10h18")
    val camera = icon("camera", "M4 7h4l2-3h4l2 3h4v13H4Z", "M8 13.5a4 4 0 1 0 8 0a4 4 0 1 0-8 0")
    val chart = icon("chart", "M3 20V12M9 20V7M15 20V10M21 20V3")
    val gallery = icon(
        "gallery",
        "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M6.5 9a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0",
        "m4 18 6-6 3 3 3-3 4 5",
    )
    val home = icon("home", "M3 10.5 12 3l9 7.5V21h-6v-7H9v7H3Z")
    val photo = icon(
        "photo",
        "M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3z",
        "M6.4 8a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0",
        "m3 17 6-6 4 4 3-3 5 6",
    )
    val plus = icon("plus", "M12 4v16M4 12h16")
    val settings = icon(
        "settings",
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
        "M10 2h4l.6 2.3 1.9.8 2.1-1.2 2.8 2.8-1.2 2.1.8 1.9L23 11v3l-2.3.6-.8 1.9 1.2 2.1-2.8 2.8-2.1-1.2-1.9.8L14 23h-4l-.6-2.3-1.9-.8-2.1 1.2-2.8-2.8 1.2-2.1-.8-1.9L1 14v-3l2.3-.6.8-1.9-1.2-2.1 2.8-2.8 2.1 1.2 1.9-.8Z",
    )
    val check = icon("check", "M5 12.5l4.5 4.5L19 7")
    val close = icon("close", "M7 7l10 10M17 7L7 17")
    val arrowRight = icon("arrowRight", "M5 12h14m-7-7 7 7-7 7")
}
