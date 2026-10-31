package ru.zamery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

// ---------- общие кусочки ----------

@Composable
fun CardBox(modifier: Modifier = Modifier, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTokens.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(t.surface).padding(padding.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Text(text, color = t.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
fun EmptyState(title: String, text: String) {
    val t = LocalTokens.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = t.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, color = t.muted, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

/** Цвет изменения: меньше — хорошо (зелёный), больше — терракотовый. */
@Composable
fun deltaColor(d: Double): Color {
    val t = LocalTokens.current
    return when {
        d < -0.04 -> t.down
        d > 0.04 -> t.up
        else -> t.muted
    }
}

/** Последнее значение метрики на дату или раньше. */
fun valueAtOrBefore(entries: List<Entry>, m: Metric, date: LocalDate): Double? =
    entries.lastOrNull { it.date <= date && it.value(m) != null }?.value(m)

fun Metric.picture(): Int = when (this) {
    Metric.WAIST -> R.drawable.anat_waist
    Metric.BELLY -> R.drawable.anat_belly
    Metric.CHEST -> R.drawable.anat_chest
    Metric.HIPS -> R.drawable.anat_hips
    Metric.WEIGHT -> R.drawable.anat_belly
}

/** Картинка-анатомия; в тёмной теме приглушаем, чтобы не слепила. */
@Composable
fun Anatomy(m: Metric, modifier: Modifier) {
    val t = LocalTokens.current
    Image(
        painterResource(m.picture()), contentDescription = null, contentScale = ContentScale.Crop,
        colorFilter = if (t.dark) ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.62f, 0.66f, 0.68f, 1f) }) else null,
        modifier = modifier,
    )
}

@Composable
fun PrimaryButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalTokens.current
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = t.primary, contentColor = t.onPrimary),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- Сводка ----------

@Composable
fun SummaryTab(
    entries: List<Entry>,
    goal: Double?,
    onAdd: () -> Unit,
    onOpen: (Entry) -> Unit,
    onGoal: () -> Unit,
    onMetric: (Metric) -> Unit,
) {
    val t = LocalTokens.current
    var showAll by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (entries.isEmpty()) {
            Box(Modifier.weight(1f)) {
                EmptyState(
                    "Пока нет замеров",
                    "Нажми «Записать замер» внизу: вес, объёмы и фото. Первый замер станет точкой отсчёта.",
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { WeightHero(entries, goal, onGoal) }

                val sizes = Metric.entries.drop(1)
                if (entries.any { e -> sizes.any { e.value(it) != null } }) {
                    item { SectionTitle("Объёмы", Modifier.padding(top = 6.dp)) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sizes.forEach { m -> VolumeTile(entries, m, Modifier.weight(1f)) { onMetric(m) } }
                        }
                    }
                }

                item { SectionTitle("Последние замеры", Modifier.padding(top = 6.dp)) }
                item {
                    val rev = entries.reversed()
                    val shown = if (showAll) rev else rev.take(5)
                    CardBox(padding = 0) {
                        shown.forEachIndexed { i, e ->
                            if (i > 0) HorizontalDivider(color = t.line, modifier = Modifier.padding(horizontal = 16.dp))
                            HistoryRow(e) { onOpen(e) }
                        }
                        if (rev.size > 5) {
                            HorizontalDivider(color = t.line)
                            TextButton(
                                onClick = { showAll = !showAll },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                Text(if (showAll) "Свернуть" else "Показать все (${rev.size})", fontSize = 16.sp, color = t.primary)
                            }
                        }
                    }
                }
            }
        }
        PrimaryButton("Записать замер", Ic.plus, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), onClick = onAdd)
    }
}

@Composable
private fun WeightHero(entries: List<Entry>, goal: Double?, onGoal: () -> Unit) {
    val t = LocalTokens.current
    val weights = entries.filter { it.value(Metric.WEIGHT) != null }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.surface)) {
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            Image(
                painterResource(if (t.dark) R.drawable.hero_dark else R.drawable.hero_light),
                contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Подложка слева, чтобы цифры читались поверх гор
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to t.surface.copy(alpha = 0.92f), 0.55f to t.surface.copy(alpha = 0.55f), 1f to Color.Transparent)
                )
            )
            Column(Modifier.padding(18.dp)) {
                Text("Текущий вес", color = t.muted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (weights.isEmpty()) {
                    Text("—", color = t.text, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                    Text("Вес ещё не записан", color = t.muted, fontSize = 15.sp)
                } else {
                    val now = weights.last().value(Metric.WEIGHT)!!
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(fmt(now), color = t.text, fontSize = 60.sp, fontWeight = FontWeight.Bold, lineHeight = 64.sp)
                        Text(" кг", color = t.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    if (weights.size > 1) {
                        val d = now - weights.first().value(Metric.WEIGHT)!!
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (d <= 0) "▼ " else "▲ ", color = deltaColor(d), fontSize = 18.sp)
                            Text("${fmtDelta(d)} кг", color = deltaColor(d), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("  с ${dateFrom(weights.first().date)}", color = t.muted, fontSize = 15.sp)
                        }
                    } else {
                        Text(dateLong(weights.last().date), color = t.muted, fontSize = 15.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val last = weights.lastOrNull()
            val now = last?.value(Metric.WEIGHT)
            val d7 = if (last != null) valueAtOrBefore(weights, Metric.WEIGHT, last.date.minusDays(7))?.let { now!! - it } else null
            val d30 = if (last != null) valueAtOrBefore(weights, Metric.WEIGHT, last.date.minusDays(30))?.let { now!! - it } else null
            HeroStat(d7?.let { "${fmtDelta(it)} кг" } ?: "—", "за 7 дней", d7?.let { deltaColor(it) } ?: t.muted, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(36.dp).background(t.line))
            HeroStat(d30?.let { "${fmtDelta(it)} кг" } ?: "—", "за 30 дней", d30?.let { deltaColor(it) } ?: t.muted, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(36.dp).background(t.line))
            Box(Modifier.weight(1f).clickable(onClick = onGoal)) {
                if (goal == null) {
                    HeroStat("Цель", "задать", t.primary, Modifier.fillMaxWidth())
                } else {
                    val left = now?.let { it - goal }
                    HeroStat(
                        if (left == null) "${fmt(goal)} кг" else if (left <= 0.04) "Есть!" else "${fmt(left)} кг",
                        if (left != null && left > 0.04) "до цели ${fmt(goal)}" else "цель ${fmt(goal)} кг",
                        t.text, Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, color: Color, modifier: Modifier) {
    val t = LocalTokens.current
    Column(modifier.heightIn(min = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = t.muted, fontSize = 13.sp)
    }
}

@Composable
private fun VolumeTile(entries: List<Entry>, m: Metric, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalTokens.current
    val list = entries.filter { it.value(m) != null }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(t.surface).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Anatomy(m, Modifier.fillMaxWidth().aspectRatio(1.05f))
        Spacer(Modifier.height(6.dp))
        Text(m.title, color = t.muted, fontSize = 13.sp)
        Text(
            list.lastOrNull()?.value(m)?.let { "${fmt(it)} см" } ?: "—",
            color = t.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        )
        val d = if (list.size > 1) list.last().value(m)!! - list.first().value(m)!! else null
        Text(d?.let { "${fmtDelta(it)} см" } ?: " ", color = d?.let { deltaColor(it) } ?: t.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HistoryRow(e: Entry, onClick: () -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dateShort(e.date), color = t.text, fontSize = 16.sp, modifier = Modifier.width(78.dp))
        Text(
            e.value(Metric.WEIGHT)?.let { "${fmt(it)} кг" } ?: "—",
            color = t.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp),
        )
        val sizes = Metric.entries.drop(1).map { m -> e.value(m)?.let(::fmt) ?: "—" }
        Text(
            if (sizes.all { it == "—" }) (if (e.photos.isNotEmpty()) "фото: ${e.photos.size}" else "") else sizes.joinToString(" / "),
            color = t.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Icon(Ic.next, contentDescription = null, tint = t.muted, modifier = Modifier.size(20.dp))
    }
}

// ---------- Графики ----------

private enum class Period(val title: String, val days: Long?) {
    MONTH("Месяц", 30), QUARTER("3 месяца", 91), YEAR("Год", 365), ALL("Всё время", null)
}

@Composable
fun ChartsTab(entries: List<Entry>, goal: Double?, metric: Metric, onMetric: (Metric) -> Unit, onGoal: () -> Unit) {
    val t = LocalTokens.current
    var period by rememberSaveable { mutableStateOf(Period.ALL) }

    if (entries.isEmpty()) {
        EmptyState("Графиков пока нет", "Запиши хотя бы пару замеров — здесь появятся линии по весу и объёмам.")
        return
    }

    val from = period.days?.let { LocalDate.now().minusDays(it) }
    val pts = entries.filter { it.value(metric) != null && (from == null || it.date >= from) }
        .map { it.date to it.value(metric)!! }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Segmented(Metric.entries, metric, { it.title }, onMetric)
        Segmented(Period.entries, period, { it.title }) { period = it }

        CardBox(padding = 12) {
            if (pts.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                    Text("За этот период замеров нет", color = t.muted, fontSize = 16.sp)
                }
            } else {
                LineChart(pts, metric.unit, Modifier.fillMaxWidth().height(260.dp))
            }
        }

        if (pts.isNotEmpty()) {
            val first = pts.first()
            val last = pts.last()
            val min = pts.minBy { it.second }
            val max = pts.maxBy { it.second }
            val u = metric.unit
            CardBox {
                Row {
                    Stat("Было", "${fmt(first.second)} $u", dateShort(first.first), t.text, Modifier.weight(1f))
                    Stat("Сейчас", "${fmt(last.second)} $u", dateShort(last.first), t.text, Modifier.weight(1f))
                    val d = last.second - first.second
                    Stat("Изменение", "${fmtDelta(d)} $u", fmtPercent(d / first.second * 100), deltaColor(d), Modifier.weight(1f))
                }
                HorizontalDivider(color = t.line, modifier = Modifier.padding(vertical = 12.dp))
                Row {
                    Stat("Минимум", "${fmt(min.second)} $u", dateShort(min.first), t.text, Modifier.weight(1f))
                    Stat("Максимум", "${fmt(max.second)} $u", dateShort(max.first), t.text, Modifier.weight(1f))
                    Stat("Замеров", pts.size.toString(), " ", t.text, Modifier.weight(1f))
                }
            }
        }

        if (metric == Metric.WEIGHT) GoalCard(entries, goal, onGoal)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun GoalCard(entries: List<Entry>, goal: Double?, onGoal: () -> Unit) {
    val t = LocalTokens.current
    val w = entries.filter { it.value(Metric.WEIGHT) != null }
    Box(Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onGoal)) {
        CardBox {
            if (goal == null || w.isEmpty()) {
                Text("Цель не задана", color = t.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Нажми, чтобы указать вес, к которому идём", color = t.muted, fontSize = 15.sp)
            } else {
                val start = w.first().value(Metric.WEIGHT)!!
                val now = w.last().value(Metric.WEIGHT)!!
                val left = now - goal
                val progress = if (start - goal <= 0.01) 1f else ((start - now) / (start - goal)).toFloat().coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Цель: ${fmt(goal)} кг", color = t.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (left > 0.04) "Осталось" else "Цель достигнута", color = t.muted, fontSize = 13.sp)
                        if (left > 0.04) Text("−${fmt(left)} кг", color = t.down, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = t.primary, trackColor = t.chip, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
                )
                Spacer(Modifier.height(6.dp))
                Text("Пройдено ${(progress * 100).toInt()}% пути от ${fmt(start)} кг", color = t.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String, color: Color, modifier: Modifier) {
    val t = LocalTokens.current
    Column(modifier) {
        Text(label, color = t.muted, fontSize = 13.sp)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(sub, color = if (label == "Изменение") color else t.muted, fontSize = 13.sp)
    }
}

/** Переключатель «таблетками» как в макете: серая подложка, выбранное — белая плашка. */
@Composable
private fun <T> Segmented(items: List<T>, selected: T, title: (T) -> String, onSelect: (T) -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(t.chip).padding(4.dp),
    ) {
        items.forEach { item ->
            val sel = item == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                    .background(if (sel) (if (t.dark) t.primarySoft else t.surface) else Color.Transparent)
                    .clickable { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title(item), fontSize = 14.sp, maxLines = 1,
                    color = if (sel) (if (t.dark) t.primary else t.text) else t.muted,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// ---------- Фото ----------

data class PhotoRef(val entry: Entry, val name: String)

@Composable
fun PhotosTab(entries: List<Entry>, store: Store, onAdd: () -> Unit, onView: (Entry, String) -> Unit) {
    val t = LocalTokens.current
    val all = entries.flatMap { e -> e.photos.map { PhotoRef(e, it) } }
    if (all.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                EmptyState("Фото пока нет", "Добавляй фото при записи замера — здесь можно будет сравнить «было» и «стало».")
            }
            PrimaryButton("Добавить фото", Ic.camera, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), onClick = onAdd)
        }
        return
    }
    var leftName by rememberSaveable { mutableStateOf<String?>(null) }
    var rightName by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(0) } // 0 — нет, 1 — левое, 2 — правое
    val left = all.firstOrNull { it.name == leftName } ?: all.first()
    val right = all.firstOrNull { it.name == rightName } ?: all.last()

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            CardBox(Modifier.padding(bottom = 8.dp), padding = 14) {
                Text("Было и стало", color = t.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Нажми на фото, чтобы выбрать другое", color = t.muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                val lw = valueAtOrBefore(entries, Metric.WEIGHT, left.entry.date)
                val rw = valueAtOrBefore(entries, Metric.WEIGHT, right.entry.date)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompareSlot(left, lw, store, Modifier.weight(1f)) { picking = 1 }
                    Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (lw != null && rw != null) {
                            Text("${fmtDelta(rw - lw)} кг", color = deltaColor(rw - lw), fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        Icon(Ic.arrowRight, contentDescription = null, tint = t.primary, modifier = Modifier.size(32.dp).padding(top = 6.dp))
                    }
                    CompareSlot(right, rw, store, Modifier.weight(1f)) { picking = 2 }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Все фото") }
        item {
            Column(
                Modifier.aspectRatio(0.72f).clip(RoundedCornerShape(12.dp)).background(t.surface).clickable(onClick = onAdd),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(t.primary), contentAlignment = Alignment.Center) {
                    Icon(Ic.plus, contentDescription = null, tint = t.onPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text("Добавить\nфото", color = t.text, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 16.sp)
            }
        }
        items(all.reversed(), key = { it.name }) { p ->
            Box(Modifier.aspectRatio(0.72f).clip(RoundedCornerShape(12.dp)).clickable { onView(p.entry, p.name) }) {
                PhotoImage(store.photoFile(p.name), Modifier.fillMaxSize(), maxPx = 400)
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .padding(start = 6.dp, end = 4.dp, top = 14.dp, bottom = 5.dp),
                ) {
                    Text(dateShort(p.entry.date), color = Color.White, fontSize = 13.sp, maxLines = 1)
                    valueAtOrBefore(entries, Metric.WEIGHT, p.entry.date)?.let {
                        Text("${fmt(it)} кг", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }

    if (picking != 0) {
        PhotoPicker(all, store, onDismiss = { picking = 0 }) { p ->
            if (picking == 1) leftName = p.name else rightName = p.name
            picking = 0
        }
    }
}

@Composable
private fun CompareSlot(p: PhotoRef, weight: Double?, store: Store, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalTokens.current
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(t.chip).clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(dateLong(p.entry.date), color = t.muted, fontSize = 13.sp, maxLines = 1)
            Text(weight?.let { "${fmt(it)} кг" } ?: "—", color = t.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        PhotoImage(store.photoFile(p.name), Modifier.fillMaxWidth().aspectRatio(0.75f), maxPx = 900)
    }
}

@Composable
private fun PhotoPicker(all: List<PhotoRef>, store: Store, onDismiss: () -> Unit, onPick: (PhotoRef) -> Unit) {
    val t = LocalTokens.current
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(t.surface).padding(16.dp)) {
            Text("Выбери фото", color = t.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.heightIn(max = 480.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(all.reversed(), key = { it.name }) { p ->
                    Column(Modifier.clickable { onPick(p) }) {
                        PhotoImage(store.photoFile(p.name), Modifier.aspectRatio(0.75f).clip(RoundedCornerShape(8.dp)), maxPx = 300)
                        Text(dateShort(p.entry.date), color = t.muted, fontSize = 13.sp)
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).height(48.dp)) {
                Text("Закрыть", fontSize = 16.sp, color = t.primary)
            }
        }
    }
}

@Composable
fun PhotoViewer(store: Store, e: Entry, name: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose)) {
            PhotoImage(store.photoFile(name), Modifier.fillMaxSize(), maxPx = 2000, crop = false)
            Text(
                dateLong(e.date) + (e.value(Metric.WEIGHT)?.let { " · ${fmt(it)} кг" } ?: ""),
                color = Color.White, fontSize = 17.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(20.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                "Нажми, чтобы закрыть", color = Color(0xCCFFFFFF), fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            )
        }
    }
}
