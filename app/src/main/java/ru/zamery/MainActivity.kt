package ru.zamery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(applicationContext)
        setContent { ZameryTheme { App(store) } }
    }
}

/** Что открыто поверх главного экрана. */
private sealed class Overlay {
    data object None : Overlay()
    data class Edit(val entry: Entry?) : Overlay()   // null — новый замер
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App(store: Store) {
    val t = LocalTokens.current
    var entries by remember { mutableStateOf(store.load()) }
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
    var viewer by remember { mutableStateOf<Pair<Entry, String>?>(null) }

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

    val tabs = listOf("Сводка", "Графики", "Фото")
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = t.bg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Text(
                    "Замеры", color = t.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                TabRow(
                    selectedTabIndex = pager.currentPage,
                    containerColor = t.bg, contentColor = t.primary,
                    indicator = { pos ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(pos[pager.currentPage]), color = t.primary, height = 3.dp,
                        )
                    },
                    divider = {},
                ) {
                    tabs.forEachIndexed { i, title ->
                        Tab(
                            selected = pager.currentPage == i,
                            onClick = { scope.launch { pager.animateScrollToPage(i) } },
                            modifier = Modifier.height(52.dp),
                            text = {
                                Text(
                                    title, fontSize = 16.sp,
                                    color = if (pager.currentPage == i) t.primary else t.muted,
                                    fontWeight = if (pager.currentPage == i) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        },
        bottomBar = {
            Box(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Button(
                    onClick = { overlay = Overlay.Edit(null) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = t.primary, contentColor = t.onPrimary),
                ) { Text("Записать замер", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            }
        },
    ) { inner ->
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().padding(inner)) { page ->
            when (page) {
                0 -> SummaryTab(entries) { overlay = Overlay.Edit(it) }
                1 -> ChartsTab(entries)
                else -> PhotosTab(entries, store) { e, name -> viewer = e to name }
            }
        }
    }

    viewer?.let { (e, name) -> PhotoViewer(store, e, name) { viewer = null } }
}
