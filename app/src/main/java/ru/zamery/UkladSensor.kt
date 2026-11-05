package ru.zamery

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Датчик для пульта «Уклада» (сфера «Здоровье»).
 * «Уклад» шлёт ru.uklad.SENSOR и забирает сводку из resultData.
 * Только локальные данные, без сети, быстро. Договор — docs/sensors.md в репозитории uklad.
 */
class UkladSensor : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val json = try { summary(context) } catch (e: Exception) {
            JSONObject().put("app", "zamery").put("title", "Замеры").put("sphere", "Здоровье")
                .put("updated", System.currentTimeMillis()).put("status", "yellow")
                .put("headline", "Не удалось прочитать замеры").put("items", JSONArray()).put("tasks", JSONArray())
        }
        resultCode = Activity.RESULT_OK
        resultData = json.toString()
    }

    companion object {
        fun summary(context: Context): JSONObject {
            val entries = Store(context).load()
            val goal = Settings(context).goal
            val today = LocalDate.now()
            val weights = entries.filter { it.value(Metric.WEIGHT) != null }
            val sizesAt = entries.lastOrNull { e -> Metric.entries.drop(1).any { e.value(it) != null } }?.date

            val items = JSONArray()
            val tasks = JSONArray()
            fun item(label: String, value: String, status: String? = null) {
                if (items.length() < 6) items.put(JSONObject().put("label", label).put("value", value).apply { status?.let { put("status", it) } })
            }

            var status = "green"
            val headline: String

            if (weights.isEmpty()) {
                status = "yellow"
                headline = "Замеров пока нет"
                tasks.put(JSONObject().put("id", "weigh").put("text", "Взвеситься и записать первый замер").put("due", today.toString()).put("priority", 1))
            } else {
                val last = weights.last()
                val now = last.value(Metric.WEIGHT)!!
                val days = ChronoUnit.DAYS.between(last.date, today).coerceAtLeast(0)
                val month = valueAtOrBefore(weights, Metric.WEIGHT, last.date.minusDays(30))
                val dMonth = month?.let { now - it }
                val dStart = now - weights.first().value(Metric.WEIGHT)!!

                // Лампочка: давно не взвешивался или вес за месяц растёт
                val ageStatus = when { days <= 3 -> "green"; days <= 7 -> "yellow"; else -> "red" }
                val trendStatus = if (dMonth != null && dMonth > 0.5) "yellow" else "green"
                status = listOf(ageStatus, trendStatus).maxBy { listOf("green", "yellow", "red").indexOf(it) }

                headline = when {
                    days > 7 -> "Не взвешивался $days ${dn(days)}"
                    dMonth != null -> "Вес ${fmtDelta(dMonth)} кг за месяц"
                    weights.size > 1 -> "Вес ${fmtDelta(dStart)} кг с ${dateFrom(weights.first().date)}"
                    else -> "Вес ${fmt(now)} кг"
                }

                item("Вес", "${fmt(now)} кг")
                if (dMonth != null) item("За месяц", "${fmtDelta(dMonth)} кг", trendStatus)
                if (weights.size > 1) item("С начала", "${fmtDelta(dStart)} кг")
                if (goal != null) {
                    val left = now - goal
                    item("До цели ${fmt(goal)} кг", if (left > 0.04) "${fmt(left)} кг" else "достигнута", if (left > 0.04) null else "green")
                }
                item("Последний замер", ago(days), ageStatus)
                valueAtOrBefore(entries, Metric.WAIST, today)?.let { item("Талия", "${fmt(it)} см") }

                if (days >= 1) tasks.put(
                    JSONObject().put("id", "weigh").put("text", "Взвеситься").put("due", today.toString())
                        .put("priority", if (days > 7) 2 else if (days > 3) 1 else 0)
                )
            }

            // Раз в неделю — объёмы и фото
            val sizesDays = sizesAt?.let { ChronoUnit.DAYS.between(it, today) }
            if (weights.isNotEmpty() && (sizesDays == null || sizesDays >= 7)) {
                val due = sizesAt?.plusDays(7)?.takeIf { it.isAfter(today) } ?: today
                tasks.put(JSONObject().put("id", "sizes").put("text", "Замерить объёмы и сделать фото").put("due", due.toString()).put("priority", 0))
            }

            return JSONObject()
                .put("app", "zamery")
                .put("title", "Замеры")
                .put("sphere", "Здоровье")
                .put("updated", System.currentTimeMillis())
                .put("status", status)
                .put("headline", headline)
                .put("items", items)
                .put("tasks", tasks)
        }

        private fun dn(n: Long): String {
            val a = n % 100
            val b = n % 10
            return when {
                a in 11..14 -> "дней"
                b == 1L -> "день"
                b in 2..4 -> "дня"
                else -> "дней"
            }
        }

        private fun ago(days: Long) = when (days) {
            0L -> "сегодня"
            1L -> "вчера"
            else -> "$days ${dn(days)} назад"
        }
    }
}
