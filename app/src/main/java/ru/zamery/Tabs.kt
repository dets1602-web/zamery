package ru.zamery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

// ---------- общие кусочки ----------

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTokens.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.surface).padding(16.dp),
        content = content,
    )
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

// ---------- Сводка ----------

@Composable
fun SummaryTab(entries: List<Entry>, onOpen: (Entry) -> Unit) {
    val t = LocalTokens.current
    if (entries.isEmpty()) {
        EmptyState(
            "Пока нет замеров",
            "Нажми «Записать замер» внизу: вес, объёмы и фото. Первый замер станет точкой отсчёта.",
        )
        return
    }
    val weights = entries.filter { it.value(Metric.WEIGHT) != null }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CardBox {
                Text("Вес", color = t.muted, fontSize = 16.sp)
                if (weights.isEmpty()) {
                    Text("ещё не записан", color = t.text, fontSize = 20.sp)
                } else {
                    val last = weights.last()
                    val now = last.value(Metric.WEIGHT)!!
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(fmt(now), color = t.text, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
                        Text(" кг", color = t.muted, fontSize = 20.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Text(dateLong(last.date), color = t.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (weights.size > 1) DeltaStat("С начала", now - weights.first().value(Metric.WEIGHT)!!)
                        valueAtOrBefore(weights, Metric.WEIGHT, last.date.minusDays(7))?.let { DeltaStat("За 7 дней", now - it) }
                        valueAtOrBefore(weights, Metric.WEIGHT, last.date.minusDays(30))?.let { DeltaStat("За 30 дней", now - it) }
                    }
                }
            }
        }

        val sizes = Metric.entries.drop(1).filter { m -> entries.any { it.value(m) != null } }
        if (sizes.isNotEmpty()) item {
            CardBox {
                Text("Объёмы, см", color = t.muted, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                sizes.forEach { m ->
                    val list = entries.filter { it.value(m) != null }
                    val now = list.last().value(m)!!
                    val d = now - list.first().value(m)!!
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(m.title, color = t.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text(fmt(now), color = t.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (list.size > 1) fmtDelta(d) else "",
                            color = deltaColor(d), fontSize = 15.sp, textAlign = TextAlign.End,
                            modifier = Modifier.width(64.dp),
                        )
                    }
                }
            }
        }

        item {
            Text(
                "История", color = t.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        val rev = entries.reversed()
        items(rev, key = { it.id }) { e ->
            // Прошлый вес — из ближайшего более раннего замера, где он есть
            val idx = entries.indexOfFirst { it.id == e.id }
            val prevW = entries.subList(0, maxOf(idx, 0)).lastOrNull { it.value(Metric.WEIGHT) != null }?.value(Metric.WEIGHT)
            HistoryRow(e, prevW) { onOpen(e) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun DeltaStat(label: String, d: Double) {
    val t = LocalTokens.current
    Column {
        Text(label, color = t.muted, fontSize = 13.sp)
        Text(fmtDelta(d) + " кг", color = deltaColor(d), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HistoryRow(e: Entry, prevWeight: Double?, onClick: () -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp))
            .background(t.surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(dateLong(e.date), color = t.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            val sizes = Metric.entries.drop(1).mapNotNull { m -> e.value(m)?.let { "${m.title} ${fmt(it)}" } }
            val extra = (sizes + listOfNotNull(if (e.photos.isNotEmpty()) "фото: ${e.photos.size}" else null))
            if (extra.isNotEmpty()) Text(extra.joinToString(" · "), color = t.muted, fontSize = 14.sp)
        }
        e.value(Metric.WEIGHT)?.let { w ->
            Column(horizontalAlignment = Alignment.End) {
                Text("${fmt(w)} кг", color = t.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                prevWeight?.let { Text(fmtDelta(w - it), color = deltaColor(w - it), fontSize = 14.sp) }
            }
        }
    }
}

// ---------- Графики ----------

private enum class Period(val title: String, val days: Long?) {
    MONTH("Месяц", 30), QUARTER("3 месяца", 91), YEAR("Год", 365), ALL("Всё", null)
}

@Composable
fun ChartsTab(entries: List<Entry>) {
    val t = LocalTokens.current
    var metric by rememberSaveable { mutableStateOf(Metric.WEIGHT) }
    var period by rememberSaveable { mutableStateOf(Period.ALL) }

    if (entries.isEmpty()) {
        EmptyState("Графиков пока нет", "Запиши хотя бы пару замеров — здесь появятся линии по весу и объёмам.")
        return
    }

    val from = period.days?.let { LocalDate.now().minusDays(it) }
    val pts = entries.filter { it.value(metric) != null && (from == null || it.date >= from) }
        .map { it.date to it.value(metric)!! }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChipRow(Metric.entries, metric, { it.title }) { metric = it }
        ChipRow(Period.entries, period, { it.title }) { period = it }
        CardBox {
            Text("${metric.title}, ${metric.unit}", color = t.muted, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            if (pts.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    Text("За этот период замеров нет", color = t.muted, fontSize = 16.sp)
                }
            } else {
                LineChart(pts, Modifier.fillMaxWidth().height(240.dp))
            }
        }
        if (pts.isNotEmpty()) CardBox {
            val first = pts.first().second
            val last = pts.last().second
            StatLine("Было", "${fmt(first)} ${metric.unit}", t.text)
            StatLine("Сейчас", "${fmt(last)} ${metric.unit}", t.text)
            if (pts.size > 1) StatLine("Изменение", "${fmtDelta(last - first)} ${metric.unit}", deltaColor(last - first))
            StatLine("Меньше всего", "${fmt(pts.minOf { it.second })} ${metric.unit}", t.text)
            StatLine("Больше всего", "${fmt(pts.maxOf { it.second })} ${metric.unit}", t.text)
            StatLine("Замеров", pts.size.toString(), t.text)
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, color: Color) {
    val t = LocalTokens.current
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.muted, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun <T> ChipRow(items: List<T>, selected: T, title: (T) -> String, onSelect: (T) -> Unit) {
    val t = LocalTokens.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(title(item), fontSize = 16.sp) },
                modifier = Modifier.height(48.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = t.surface, labelColor = t.text,
                    selectedContainerColor = t.primary, selectedLabelColor = t.onPrimary,
                ),
                border = null,
            )
        }
    }
}

// ---------- Фото ----------

data class PhotoRef(val entry: Entry, val name: String)

@Composable
fun PhotosTab(entries: List<Entry>, store: Store, onView: (Entry, String) -> Unit) {
    val t = LocalTokens.current
    val all = entries.flatMap { e -> e.photos.map { PhotoRef(e, it) } }
    if (all.isEmpty()) {
        EmptyState(
            "Фото пока нет",
            "Добавляй фото при записи замера — здесь можно будет сравнить «было» и «стало».",
        )
        return
    }
    // Выбор для сравнения: по умолчанию самое первое и самое последнее фото
    var leftName by rememberSaveable { mutableStateOf<String?>(null) }
    var rightName by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(0) } // 0 — нет, 1 — левое, 2 — правое
    val left = all.firstOrNull { it.name == leftName } ?: all.first()
    val right = all.firstOrNull { it.name == rightName } ?: all.last()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            CardBox(Modifier.padding(bottom = 10.dp)) {
                Text("Было и стало", color = t.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Нажми на фото, чтобы выбрать другое", color = t.muted, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompareSlot(left, store, Modifier.weight(1f)) { picking = 1 }
                    CompareSlot(right, store, Modifier.weight(1f)) { picking = 2 }
                }
                val lw = valueAtOrBefore(entries, Metric.WEIGHT, left.entry.date)
                val rw = valueAtOrBefore(entries, Metric.WEIGHT, right.entry.date)
                if (lw != null && rw != null && left.entry.date != right.entry.date) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Разница по весу: ${fmtDelta(rw - lw)} кг",
                        color = deltaColor(rw - lw), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        entries.filter { it.photos.isNotEmpty() }.reversed().forEach { e ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "h${e.id}") {
                Text(
                    dateLong(e.date) + (e.value(Metric.WEIGHT)?.let { " · ${fmt(it)} кг" } ?: ""),
                    color = t.text, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }
            items(e.photos, key = { it }) { name ->
                PhotoImage(
                    store.photoFile(name),
                    Modifier.aspectRatio(0.75f).clip(RoundedCornerShape(10.dp)).clickable { onView(e, name) },
                    maxPx = 400,
                )
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
private fun CompareSlot(p: PhotoRef, store: Store, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalTokens.current
    Column(modifier) {
        PhotoImage(
            store.photoFile(p.name),
            Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
            maxPx = 900,
        )
        Text(dateLong(p.entry.date), color = t.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
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
