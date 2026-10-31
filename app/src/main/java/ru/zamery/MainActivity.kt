package ru.zamery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(applicationContext)
        val settings = Settings(applicationContext)
        setContent { ZameryTheme { App(store, settings) } }
    }
}

/** Что открыто поверх главного экрана. */
private sealed class Overlay {
    data object None : Overlay()
    data class Edit(val entry: Entry?) : Overlay()   // null — новый замер
}

private data class NavTab(val title: String, val icon: ImageVector)

@Composable
fun App(store: Store, settings: Settings) {
    val t = LocalTokens.current
    var entries by remember { mutableStateOf(store.load()) }
    var goal by remember { mutableStateOf(settings.goal) }
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
    var viewer by remember { mutableStateOf<Pair<Entry, String>?>(null) }
    var askGoal by remember { mutableStateOf(false) }
    var chartMetric by rememberSaveable { mutableStateOf(Metric.WEIGHT) }

    fun commit(list: List<Entry>) {
        entries = list.sortedWith(compareBy({ it.date }, { it.id }))
        store.save(entries)
    }

    val o = overlay
    if (o is Overlay.Edit) {
        EditorScreen(
            store = store,
            original = o.entry,
            history = entries.filter { it.id != o.entry?.id },
            onClose = { overlay = Overlay.None },
            onSave = { e ->
                commit(entries.filter { it.id != e.id } + e)
                overlay = Overlay.None
            },
            onDelete = { e ->
                e.photos.forEach { store.photoFile(it).delete() }
                commit(entries.filter { it.id != e.id })
                overlay = Overlay.None
            },
        )
        return
    }

    val tabs = listOf(NavTab("Сводка", Ic.home), NavTab("Графики", Ic.chart), NavTab("Фото", Ic.photo))
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = t.bg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (pager.currentPage == 0) "Замеры" else tabs[pager.currentPage].title,
                    color = t.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { askGoal = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Ic.settings, contentDescription = "Цель по весу", tint = t.text)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = t.surface, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = pager.currentPage == i,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title, fontSize = 13.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = t.primary, selectedTextColor = t.primary,
                            indicatorColor = t.primarySoft,
                            unselectedIconColor = t.muted, unselectedTextColor = t.muted,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().padding(inner)) { page ->
            when (page) {
                0 -> SummaryTab(
                    entries = entries, goal = goal,
                    onAdd = { overlay = Overlay.Edit(null) },
                    onOpen = { overlay = Overlay.Edit(it) },
                    onGoal = { askGoal = true },
                    onMetric = { m -> chartMetric = m; scope.launch { pager.animateScrollToPage(1) } },
                )
                1 -> ChartsTab(entries, goal, chartMetric, { chartMetric = it }) { askGoal = true }
                else -> PhotosTab(entries, store, onAdd = { overlay = Overlay.Edit(null) }) { e, name -> viewer = e to name }
            }
        }
    }

    viewer?.let { (e, name) -> PhotoViewer(store, e, name) { viewer = null } }

    if (askGoal) GoalDialog(goal, onDismiss = { askGoal = false }) { g ->
        goal = g
        settings.goal = g
        askGoal = false
    }
}

@Composable
private fun GoalDialog(current: Double?, onDismiss: () -> Unit, onSave: (Double?) -> Unit) {
    val t = LocalTokens.current
    var text by remember { mutableStateOf(current?.let(::fmt) ?: "") }
    val parsed = parseNum(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        title = { Text("Цель по весу") },
        text = {
            Column {
                Text("К какому весу идём? Покажу прогресс и сколько осталось.", color = t.muted, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                    label = { Text("Цель, кг") },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, color = t.text, fontWeight = FontWeight.SemiBold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = t.primary, focusedLabelColor = t.primary, cursorColor = t.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(parsed) }, enabled = parsed != null) { Text("Сохранить", fontSize = 16.sp) }
        },
        dismissButton = {
            if (current != null) TextButton(onClick = { onSave(null) }) { Text("Убрать цель", fontSize = 16.sp, color = t.danger) }
            else TextButton(onClick = onDismiss) { Text("Отмена", fontSize = 16.sp) }
        },
    )
}
