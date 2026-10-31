package ru.zamery

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.Locale

/** Что меряем. Порядок здесь = порядок в форме и на графиках. */
enum class Metric(val key: String, val title: String, val unit: String) {
    WEIGHT("weight", "Вес", "кг"),
    WAIST("waist", "Талия", "см"),
    BELLY("belly", "Живот", "см"),
    CHEST("chest", "Грудь", "см"),
    HIPS("hips", "Бёдра", "см"),
}

data class Entry(
    val id: Long,
    val date: LocalDate,
    val values: Map<Metric, Double>,
    val photos: List<String>, // имена файлов в папке photos/
) {
    fun value(m: Metric): Double? = values[m]
}

/** Хранилище: один JSON-файл в памяти приложения, фото — рядом в папке photos/. */
class Store(private val context: Context) {
    private val file = File(context.filesDir, "entries.json")
    val photoDir: File = File(context.filesDir, "photos").apply { mkdirs() }

    fun load(): List<Entry> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val vals = mutableMapOf<Metric, Double>()
                Metric.entries.forEach { m -> if (o.has(m.key)) vals[m] = o.getDouble(m.key) }
                val ph = o.optJSONArray("photos")
                Entry(
                    id = o.getLong("id"),
                    date = LocalDate.parse(o.getString("date")),
                    values = vals,
                    photos = if (ph == null) emptyList() else (0 until ph.length()).map { ph.getString(it) },
                )
            }.sortedWith(compareBy({ it.date }, { it.id }))
        } catch (e: Exception) {
            // Битый файл не затираем — откладываем копию, чтобы данные можно было спасти
            file.copyTo(File(context.filesDir, "entries-broken-${System.currentTimeMillis()}.json"), overwrite = true)
            emptyList()
        }
    }

    fun save(list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            val o = JSONObject()
            o.put("id", e.id)
            o.put("date", e.date.toString())
            e.values.forEach { (m, v) -> o.put(m.key, v) }
            o.put("photos", JSONArray(e.photos))
            arr.put(o)
        }
        // Пишем во временный файл и подменяем — чтобы не потерять всё при обрыве
        val tmp = File(context.filesDir, "entries.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    fun photoFile(name: String) = File(photoDir, name)
}

/** «82,4» и «82.4» — оба понимаем. */
fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

fun fmt(v: Double): String {
    val s = String.format(Locale.US, "%.1f", v)
    return (if (s.endsWith(".0")) s.dropLast(2) else s).replace('.', ',')
}

fun fmtDelta(v: Double): String = when {
    v > 0.04 -> "+" + fmt(v)
    v < -0.04 -> "−" + fmt(-v)
    else -> "0"
}

private val months = listOf("янв.", "февр.", "мар.", "апр.", "мая", "июн.", "июл.", "авг.", "сент.", "окт.", "нояб.", "дек.")
private val monthsFull = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
private val weekdays = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

fun dateShort(d: LocalDate) = "${d.dayOfMonth} ${months[d.monthValue - 1]}"

/** «1 июля» (+ год, если не текущий) — для подписи «с 1 июля». */
fun dateFrom(d: LocalDate) =
    "${d.dayOfMonth} ${monthsFull[d.monthValue - 1]}" + if (d.year != LocalDate.now().year) " ${d.year}" else ""

/** «12 ноября 2026, вт» — для поля даты в форме. */
fun dateFull(d: LocalDate) =
    "${d.dayOfMonth} ${monthsFull[d.monthValue - 1]} ${d.year}, ${weekdays[d.dayOfWeek.value - 1]}"

fun fmtPercent(v: Double): String = fmtDelta(v) + "%"

/** Цель по весу (0 — не задана). Храним в настройках приложения. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var goal: Double?
        get() = prefs.getFloat("goal", 0f).takeIf { it > 0f }?.toDouble()
        set(v) { prefs.edit().putFloat("goal", (v ?: 0.0).toFloat()).apply() }
}
fun dateLong(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> "${d.dayOfMonth} ${monthsFull[d.monthValue - 1]}" + if (d.year != today.year) " ${d.year}" else ""
    }
}
