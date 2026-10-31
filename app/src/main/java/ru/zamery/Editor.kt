package ru.zamery

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    store: Store,
    original: Entry?,
    history: List<Entry>,
    onClose: () -> Unit,
    onSave: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf(original?.date ?: LocalDate.now()) }
    val fields = remember {
        mutableStateMapOf<Metric, String>().apply {
            Metric.entries.forEach { put(it, original?.value(it)?.let(::fmt) ?: "") }
        }
    }
    val photos = remember { mutableStateListOf<String>().apply { original?.photos?.let { addAll(it) } } }
    val added = remember { mutableSetOf<String>() }      // фото, добавленные в этот раз
    var busy by remember { mutableStateOf(false) }
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    fun cancel() {
        added.forEach { store.photoFile(it).delete() }
        onClose()
    }
    BackHandler { cancel() }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val name = pendingCamera
        pendingCamera = null
        if (name != null) {
            val f = store.photoFile(name)
            if (ok && f.length() > 0) {
                scope.launch {
                    busy = true
                    normalizePhoto(f)
                    photos.add(name); added.add(name)
                    busy = false
                }
            } else f.delete()
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            uris.forEachIndexed { i, u ->
                val name = "p_${System.currentTimeMillis()}_$i.jpg"
                if (storePhoto(context, u, store.photoFile(name))) { photos.add(name); added.add(name) }
            }
            busy = false
        }
    }

    fun takePhoto() {
        val name = "p_${System.currentTimeMillis()}.jpg"
        val f = store.photoFile(name)
        f.createNewFile()
        val uri = FileProvider.getUriForFile(context, "ru.zamery.app.files", f)
        pendingCamera = name
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pendingCamera = null; f.delete()
            Toast.makeText(context, "Не нашлось приложения камеры", Toast.LENGTH_LONG).show()
        }
    }

    fun prev(m: Metric) = history.lastOrNull { it.value(m) != null && it.date <= date }?.value(m)
        ?: history.lastOrNull { it.value(m) != null }?.value(m)

    val parsed = Metric.entries.associateWith { parseNum(fields[it] ?: "") }
    val bad = Metric.entries.filter { (fields[it] ?: "").isNotBlank() && parsed[it] == null }
    val hasSomething = parsed.values.any { it != null } || photos.isNotEmpty()
    val canSave = hasSomething && bad.isEmpty() && !busy

    Column(Modifier.fillMaxSize().background(t.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { cancel() }, modifier = Modifier.size(48.dp)) {
                Icon(Ic.back, contentDescription = "Назад без сохранения", tint = t.text)
            }
            Text(
                if (original == null) "Новый замер" else "Замер",
                color = t.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Дата
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(t.surface)
                    .clickable { showDate = true }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dateFull(date), color = t.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Icon(Ic.calendar, contentDescription = "Выбрать дату", tint = t.text, modifier = Modifier.size(22.dp))
            }

            // Вес — главное поле
            val wPrev = prev(Metric.WEIGHT)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(t.surface).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 10.dp)) {
                Text("Вес, кг", color = t.muted, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumField(
                        value = fields[Metric.WEIGHT] ?: "",
                        onChange = { fields[Metric.WEIGHT] = it },
                        hint = wPrev?.let(::fmt) ?: "0,0",
                        style = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold, color = t.text, textAlign = TextAlign.Center),
                        isLast = false,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { fields[Metric.WEIGHT] = "" },
                        enabled = !fields[Metric.WEIGHT].isNullOrEmpty(),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(t.chip), contentAlignment = Alignment.Center) {
                            Icon(Ic.close, contentDescription = "Очистить", tint = t.muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                when {
                    Metric.WEIGHT in bad -> Text("Число, например 94,6", color = t.danger, fontSize = 14.sp)
                    wPrev != null -> Text("прошлый: ${fmt(wPrev)} кг", color = t.muted, fontSize = 14.sp)
                }
            }

            // Объёмы 2×2
            Text("Объёмы, см", color = t.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            val sizes = Metric.entries.drop(1)
            sizes.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { m ->
                        VolumeField(
                            m, fields[m] ?: "", { fields[m] = it }, prev(m), m in bad,
                            isLast = m == sizes.last(), modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Фото
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                Text("Фото", color = t.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("  (необязательно)", color = t.muted, fontSize = 15.sp)
            }
            if (photos.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    photos.toList().forEach { name ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PhotoImage(store.photoFile(name), Modifier.width(96.dp).height(128.dp).clip(RoundedCornerShape(12.dp)), maxPx = 300)
                            TextButton(onClick = {
                                photos.remove(name)
                                if (name in added) { store.photoFile(name).delete(); added.remove(name) }
                            }, modifier = Modifier.height(48.dp)) { Text("Убрать", color = t.danger, fontSize = 15.sp) }
                        }
                    }
                }
            }
            if (busy) Text("Обрабатываю фото…", color = t.muted, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PhotoButton("Сфотографировать", Ic.camera, !busy, Modifier.weight(1f)) { takePhoto() }
                PhotoButton("Выбрать из галереи", Ic.gallery, !busy, Modifier.weight(1f)) {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }

            if (original != null) {
                TextButton(onClick = { askDelete = true }, modifier = Modifier.padding(top = 12.dp).height(48.dp)) {
                    Text("Удалить замер", color = t.danger, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.size(8.dp))
        }

        PrimaryButton(
            "Сохранить замер", Ic.check, enabled = canSave,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            val keep = photos.toList()
            original?.photos?.filter { it !in keep }?.forEach { store.photoFile(it).delete() }
            onSave(
                Entry(
                    id = original?.id ?: System.currentTimeMillis(),
                    date = date,
                    values = parsed.filterValues { it != null }.mapValues { it.value!! },
                    photos = keep,
                )
            )
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("Выбрать", fontSize = 16.sp) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Отмена", fontSize = 16.sp) } },
        ) { DatePicker(state = state) }
    }

    if (askDelete && original != null) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            containerColor = t.surface,
            title = { Text("Удалить замер?") },
            text = { Text("Замер за ${dateLong(original.date).lowercase()} и его фото удалятся без возврата.") },
            confirmButton = {
                TextButton(onClick = {
                    added.forEach { store.photoFile(it).delete() }
                    onDelete(original)
                }) { Text("Удалить", color = t.danger, fontSize = 16.sp) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Оставить", fontSize = 16.sp) } },
        )
    }
}

/** Поле числа без рамки: подсказка серым, запятая и точка. */
@Composable
private fun NumField(value: String, onChange: (String) -> Unit, hint: String, style: TextStyle, isLast: Boolean, modifier: Modifier) {
    val t = LocalTokens.current
    BasicTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }.take(6)) },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(t.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
        ),
        modifier = modifier,
        decorationBox = { inner ->
            Box(contentAlignment = when (style.textAlign) { TextAlign.Center -> Alignment.Center; else -> Alignment.CenterStart }) {
                if (value.isEmpty()) Text(hint, style = style.copy(color = t.muted.copy(alpha = 0.45f)))
                inner()
            }
        },
    )
}

@Composable
private fun VolumeField(
    m: Metric, value: String, onChange: (String) -> Unit, prev: Double?, isBad: Boolean, isLast: Boolean, modifier: Modifier,
) {
    val t = LocalTokens.current
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(t.surface).height(112.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Anatomy(m, Modifier.width(64.dp).fillMaxHeight())
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(m.title, color = t.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            NumField(
                value = value, onChange = onChange, hint = prev?.let(::fmt) ?: "—",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = t.text),
                isLast = isLast,
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .border(1.5.dp, if (isBad) t.danger else t.line, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
            Text(
                if (isBad) "нужно число" else prev?.let { "прошлый: ${fmt(it)}" } ?: " ",
                color = if (isBad) t.danger else t.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun PhotoButton(text: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalTokens.current
    Column(
        modifier.height(84.dp).clip(RoundedCornerShape(16.dp)).background(t.surface)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) t.text else t.muted, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(text, color = if (enabled) t.text else t.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}
